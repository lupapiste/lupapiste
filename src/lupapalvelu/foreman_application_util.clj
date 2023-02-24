(ns lupapalvelu.foreman-application-util
  (:require
    [lupapalvelu.document.schemas :as schemas]
    [lupapalvelu.document.tools :as tools]
    [lupapalvelu.domain :as domain]
    [lupapalvelu.mongo :as mongo]
    [lupapalvelu.pate.verdict-common :as vc]
    [lupapalvelu.pate.verdict-interface :as vif]
    [lupapalvelu.states :as states]
    [monger.operators :refer :all]
    [sade.date :as date]
    [sade.strings :as ss]
    [sade.util :as util]))

;; Moved into their own namespace due to dependency conflict with lupapalvelu.application-utils and
;; lupapalvelu.foreman

(defn foreman-app? [application]
  (util/=as-kw :tyonjohtajan-nimeaminen-v2
               (some-> application :primaryOperation :name)))

;; Foreman responsibility dates

(defn get-foreman-responsibility-timestamps
  "Returns a timestamp of the time interval when the foreman was responsible for the project.
   Project-app is the actual application we're currently considering (as foremen can be linked to multiple apps).
   Either/both the :started or :ended timestamp will be nil if not applicable.
     :started   is nil if foreman application is not accepted yet.
     :ended     is nil if foreman application is not accepted yet or responsibilities are still ongoing."
  [{:keys [permitSubtype foremanTermination state] :as foreman-app} {:keys [tasks] :as _project-app}]
  (let [verdict    (vif/latest-published-verdict {:application foreman-app})
        ilmoitus?  (= "tyonjohtaja-ilmoitus" permitSubtype)
        submitted? (states/post-submitted-states (keyword state))
        ;; Responsibilities started
        started
        (cond
          (some? foremanTermination) (:started foremanTermination)
          verdict                    (when-not (vif/verdict-negative? verdict)
                                       (or (vc/responsibilities-start-date verdict)
                                           (vc/verdict-date verdict)
                                           (vc/verdict-published verdict)))
          (and ilmoitus? submitted?) (:submitted foreman-app))
        ;; Responsibilities ended
        ended
        (when (some? started)
          (or (:ended foremanTermination)
              (->> tasks
                   (map tools/unwrapped)
                   (util/find-first #(and (= "loppukatselmus" (-> % :data :katselmuksenLaji))
                                          (= "lopullinen"     (-> % :data :katselmus :tila))
                                          (= "sent"           (-> % :state))))
                   :data
                   :katselmus
                   :pitoPvm
                   date/timestamp)))]
    {:started started
     :ended   ended}))

(defn foreman-termination-requested?
  "Returns true if a user has requested termination of this foreman.
   Returns false if foreman already terminated."
  [{:keys [foremanTermination]}]
  (= "requested" (:state foremanTermination)))

(defn select-latest-verdict-status
  "Verdict status is either ok, rejected or new."
  [{state :state :as application}]
  (if (util/=as-kw state :acknowledged)
    {:status "ok"}
    (let  [latest     (vif/latest-published-verdict {:application application})
           code       (vc/verdict-code latest)
           negative?  (vif/verdict-negative? latest)]
      (util/assoc-when {:status   (cond
                                    negative?           "rejected"
                                    (false? negative?)  "ok"
                                    :else               "new")}
                       :statusLoc (cond
                                    (ss/blank? code)        nil
                                    (re-find #"^\d+$" code) (util/kw-path :verdict.status code)
                                    :else                   (util/kw-path :pate-r.verdict-code code))))))

(defn- filter-foreman-doc-responsibilities
  "Removes `vastattavatTyotehtavat` that are not relevant to the currently selected foreman role.
   Uses document schemas which are used by the UI as well for consistency."
  [doc]
  (let [current-role  (-> doc :data :kuntaRoolikoodi :value)
        current-code  (->> schemas/kuntaroolikoodi-tyonjohtaja-v2
                           (mapcat :body)
                           (util/find-first #(= current-role (:name %)))
                           :code)
        resp-to-codes (->> schemas/vastattavat-tyotehtavat-tyonjohtaja-v2
                           (mapcat :body)
                           (reduce (fn [acc {:keys [name codes]}]
                                     (assoc acc (keyword name) (set codes)))
                                   {}))
        update-entry  (fn [[tyotehtava fields]]
                       (if (or (-> fields :value boolean? not)
                               (-> tyotehtava resp-to-codes (get current-code) some?))
                         [tyotehtava fields]
                         [tyotehtava (assoc fields :value false)]))]
    (update-in doc
               [:data :vastattavatTyotehtavat]
               #(->> %
                     (map update-entry)
                     (into {})))))

(defn foreman-application-info
  "Note that changing this might affect `lupapalvelu.foreman/add-required-roles-to-foreman-info` especially.
   The project-app is the currently considered point of view; the \"lopullinen loppukatselmus\" review
   for a project ends responsibilities for all foremen on that project but not for all projects
   the foreman applications are linked to."
  [project-app foreman-app]
  (-> (select-keys foreman-app [:id :state :auth :documents :organization :_non-listed-foreman])
      (merge {:latest-verdict-status  (select-latest-verdict-status foreman-app)
              :termination-reason     (get-in foreman-app [:foremanTermination :reason])
              :termination-requested  (foreman-termination-requested? foreman-app)
              :termination-request-ts (get-in foreman-app [:foremanTermination :request-ts])
              :termination-requester  (get-in foreman-app [:foremanTermination :requester :id])}
             (get-foreman-responsibility-timestamps foreman-app project-app))
      (update :documents #(->> (domain/get-documents-by-name % "tyonjohtaja-v2")
                               (map filter-foreman-doc-responsibilities)))))

(def foreman-info-projection
  [:id :state :created :submitted :auth :documents :organization
   :verdicts :pate-verdicts :foremanTermination :permitSubtype
   :_non-listed-foreman])

(defn get-linked-foreman-applications-by-id [id]
  (let [app-link-resp             (mongo/select :app-links {:link {$in [id]}})
        apps-linking-to-us        (filter #(= (:type ((keyword id) %)) "linkpermit") app-link-resp)
        foreman-application-links (filter #(= "tyonjohtajan-nimeaminen-v2"
                                              (get-in % [(keyword (first (:link %))) :apptype]))
                                    apps-linking-to-us)
        foreman-application-ids   (map (fn [link] (first (:link link))) foreman-application-links)
        applications              (mongo/select :applications
                                                {:_id {$in foreman-application-ids}}
                                                foreman-info-projection)
        project-app               (domain/get-application-no-access-checking id)
        mapped-applications       (map #(foreman-application-info project-app %) applications)]
    (sort-by :id mapped-applications)))

(defn get-linked-foreman-applications
  "Gets the foreman applications related to the given application.
   Note: Moved into own namespace from lupapalvelu.foreman due to the dependency cycle hell that is:
         (foreman > application > application-utils > application)"
  [{:keys [id] :as project-app}]
  (let [foreman-application-ids (->> (mongo/select :app-links {:link id})
                                     (filter (fn [{:keys [link] :as app-link}]
                                               (and (= (get-in app-link [(keyword id) :type])
                                                       "linkpermit")
                                                    (= (get-in app-link [(->> link first keyword) :apptype])
                                                       "tyonjohtajan-nimeaminen-v2"))))
                                     (map (comp first :link)))]
    (->> (mongo/select :applications
                       {:_id {$in foreman-application-ids}}
                       foreman-info-projection)
         (map #(foreman-application-info project-app %)))))
