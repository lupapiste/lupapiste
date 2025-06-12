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

(defn- filled-reviewer?
  "Returns true if reviewer should NOT be auto-filled. This is the case when:
  - Task has non-empty `pitaja` that is supported by the review officer list (if in use)"
  [{officers? :review-officers-list-enabled :as organization} task]
  (let [pitaja-field (some-> task :data :katselmus :pitaja)
        reviewer     (:value pitaja-field)]
    (boolean
     (cond
       (util/emptyish? reviewer) false
       officers?                 (util/find-by-key :code
                                                   (:code reviewer)
                                                   (:reviewOfficers organization))
       :else                     (string? reviewer)))))

(defn- reviewer-edited?
  "For ryhti-data-2026: Returns true if reviewer details should NOT be auto-filled.
  This is the case when:
  - Task has non-empty `pitajaDetails` with both etunimi and sukunimi, OR
  - User has touched the fields (they have a :modified timestamp), even if cleared"
  [task]
  (let [details        (some-> task :data :katselmus :pitajaDetails)
        etunimi-field  (:etunimi details)
        sukunimi-field (:sukunimi details)]
    (boolean
     (or
     ;; Fields are filled
      (and (not (util/emptyish? (:value etunimi-field)))
           (not (util/emptyish? (:value sukunimi-field))))
     ;; User has touched either field (has :modified timestamp)
      (:modified etunimi-field)
      (:modified sukunimi-field)))))

(defn- find-matching-officer
  "Finds the best matching officer from the review officers list using Levenshtein distance.
  Returns the officer if distance < 4, otherwise nil."
  [user organization]
  (let [lowname (ss/lower-case (usr/full-name user))
        revname (-> {:firstName (:lastName user)
                     :lastName  (:firstName user)}
                    usr/full-name
                    ss/lower-case)
        [distance officer] (some->> (:reviewOfficers organization)
                                    not-empty
                                    (map (fn [officer]
                                           (let [officer-name (-> officer :name ss/lower-case)]
                                             [(min (util/edit-distance officer-name lowname)
                                                   (util/edit-distance officer-name revname))
                                              officer])))
                                    (apply min-key first))]
    (when (some-> distance (< 4))
      officer)))

(defn- should-set-default-reviewer?
  "Common checks for whether default reviewer should be set."
  [{:keys [organization application]
    :as   command} task good-reviewer-fn]
  (let [organization (force organization)]
    (and (supported-application? application)
         (authorized? command)
         (task-is-review? task)
         (= (:state task) "requires_user_action")
         (not (good-reviewer-fn organization task)))))

(defn default-reviewer-value
  "Default reviewer for the given `task`. Returns nil, if task already has a suitable
  reviewer value. Otherwise, returns map with `:value` key. This is needed since the
  default value can also be nil. Note that `:value` can be either (current user's) name or
  a review officer list entry. "
  [{:keys [user organization]
    :as   command} task]
  (when (should-set-default-reviewer? command task filled-reviewer?)
    (let [organization (force organization)]
      (if (:review-officers-list-enabled organization)
        (let [old-value (some-> task :data :katselmus :pitaja :value)
              new-value (find-matching-officer user organization)]
          (when-not (= old-value new-value)
            {:value new-value}))
        {:value (usr/full-name user)}))))

(defn ryhti-default-reviewer-details
  "Default reviewer details (etunimi/sukunimi) for ryhti-data-2026 applications.
   Returns nil if task already has suitable reviewer details, otherwise returns
   map with `:etunimi`, `:sukunimi` and `:pitaja` keys.
   Also returns the pitäjä if the :review-officers-list-enabled is true"
  [{:keys [user organization]
    :as   command} task]
  (when (should-set-default-reviewer?
         command task
         (fn [_ task] (reviewer-edited? task)))
    (let [organization (force organization)]
      (if (:review-officers-list-enabled organization)
        (when-let [officer (find-matching-officer user organization)]
          {:etunimi  (-> officer
                         :firstName
                         ss/trim-or-nil)
           :sukunimi (-> officer
                         :lastName
                         ss/trim-or-nil)
           :pitaja   officer})
        {:etunimi  (-> user
                       :firstName
                       ss/trim-or-nil)
         :sukunimi (-> user
                       :lastName
                       ss/trim-or-nil)}))))

(defn enrich-default-task-reviewer
  [command task]
  (let [application (:application command)]
    (if (:ryhti-data-2026 application)
      (if-let [{:keys [etunimi sukunimi pitaja]} (ryhti-default-reviewer-details command task)]
        (cond-> task
          etunimi (assoc-in [:data :katselmus :pitajaDetails :etunimi :value] etunimi)
          sukunimi (assoc-in [:data :katselmus :pitajaDetails :sukunimi :value] sukunimi)
          pitaja (assoc-in [:data :katselmus :pitaja :value] pitaja))
        task)
      (if-let [value (default-reviewer-value command task)]
        (assoc-in task [:data :katselmus :pitaja :value] (:value value))
        task))))

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
