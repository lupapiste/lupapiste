(ns lupapalvelu.task-util
  "Separate namespace in order to avoid cyclic dependencies."
  (:require [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [sade.strings :as ss]
            [sade.util :as util]))

(def task-schemas-version 1)

(def valid-application-states states/all-application-states-but-draft-or-terminal)

(defn task-is-review? [task]
  (some->> (get-in task [:schema-info :name])
           (schemas/get-schema task-schemas-version)
           (#(get-in % [:info :subtype]))
           (keyword)
           (contains? #{:review :review-backend})))

(defn- supported-application? [{:keys [state permitType tasks]}]
  (and (util/includes-as-kw? valid-application-states state)
       (util/includes-as-kw? [permit/R permit/YA] permitType)
       (some task-is-review? tasks)))

(defn- authorized? [{:keys [user application]}]
  (usr/user-is-authority-in-organization? (usr/with-org-auth user)
                                          (:organization application)))

(defn- good-reviewer?
  "Task has non-empty `pitaja` that is supported by the review officer list (if in use)."
  [{officers? :review-officers-list-enabled :as organization} task]
  (let [reviewer (some-> task :data :katselmus :pitaja :value)]
    (boolean (cond
               (util/emptyish? reviewer) false
               officers?                 (util/find-by-key :code
                                                           (:code reviewer)
                                                           (:reviewOfficers organization))
               :else                     (string? reviewer)))))

(defn default-reviewer-value
  "Default reviewer for the given `task`. Returns nil, if task already has a suitable
  reviewer value. Otherwise, returns map with `:value` key. This is needed since the
  default value can also be nil. Note that `:value` can be either (current user's) name or
  a review officer list entry. "
  [{:keys [user organization application] :as command} task]
  (let [username     (usr/full-name user)
        organization (force organization)]
    (when (and (supported-application? application)
               (authorized? command)
               (task-is-review? task)
               (= (:state task) "requires_user_action")
               (not (good-reviewer? organization task)))
      (if (:review-officers-list-enabled organization)
        (let [old-value (some-> task :data :katselmus :pitaja :value)
              lowname   (ss/lower-case username)
              revname   (-> {:firstName (:lastName user)
                             :lastName  (:firstName user)}
                            usr/full-name
                            ss/lower-case)
              ;; Levenshtein distance = number of edits needed to change string to another
              [distance
               officer] (some->> (:reviewOfficers organization)
                                 not-empty
                                 (map (fn [officer]
                                        (let [officer-name (-> officer :name ss/lower-case)]
                                          [(min (util/edit-distance officer-name lowname)
                                                (util/edit-distance officer-name revname))
                                           officer])))
                                 (apply min-key first))
              new-value (when (some-> distance (< 4))  ; More differences, no match
                          officer)]
          (when-not (= old-value new-value)
            {:value new-value}))
        {:value username}))))

(defn enrich-default-task-reviewer
  [command task]
  (if-let [value (default-reviewer-value command task)]
    (assoc-in task [:data :katselmus :pitaja :value] (:value value))
    task))

(defn with-review-officers
  "Enriches application with `:reviewOfficers`, (map of `:enabled` and `:officers`). Map is
  empty when officers are not supported for the application. Empty map is needed to make
  sure that the frontend application model initializes correctly.."
  [command application]
  (assoc application
         :reviewOfficers
         (or (when (and (supported-application? application)
                        (authorized? command))
               (let [{:keys [review-officers-list-enabled
                             reviewOfficers]} (some-> command
                                                   :organization
                                                   force)]
                 (when review-officers-list-enabled
                   (util/assoc-when {:enabled true}
                                    :officers (seq reviewOfficers)))))
             {})))

(defn faulty? [task]
  (util/=as-kw (:state task) :faulty_review_task))
