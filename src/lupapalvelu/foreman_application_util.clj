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
  (let [op-name (some-> application :primaryOperation :name)]
    (or (util/=as-kw :tyonjohtajan-nimeaminen-v2 op-name)
        (util/=as-kw :tyonjohtajan-nimeaminen op-name))))

;; Foreman responsibility dates

(defn- substitute-timestamps
  "Returns [start end] timestamp list. Either/both can be nil. Since docgen allows
  nonsensical date range, the result is nil if the start is after end."
  [foreman-doc]
  (let [[start end
         :as result] (some->> foreman-doc
                              :data :sijaistus
                              tools/unwrapped
                              ((juxt :alkamisPvm :paattymisPvm))
                              (map date/timestamp))]
    (when-not (and start end (> start end))
      result)))

(defn get-foreman-responsibility-timestamps
  "Calculates the time period when a foreman was responsible for a project.

  Parameters:
    foreman-app-or-doc - Either a V2 foreman application or V3 foreman document (with :tj-document flag)
    project-app        - The actual project application

  Returns:
    {:started <timestamp>, :ended <timestamp>} - Either/both timestamps can be nil
    nil - When the calculated date range is invalid (start > end)

  Start Date Priority (first non-nil value wins):
    1. Explicit termination start (:started in :foremanTermination)
    2. Authority-set start date (:started in foreman-app-or-doc)
    3. Verdict timestamp (from :responsibilities-start-date, :verdict-date, or :verdict-published)
       - Only if verdict is not negative
       - For V3 documents: verdict must have matching :document-id
    4. Submission timestamp (:submitted)
       - For tyonjohtaja-ilmoitus: when state is post-submitted
       - For V3 documents: when state is 'declared' or 'acknowledged'

  End Date (first non-nil value wins):
    1. Explicit termination end (:ended in :foremanTermination)
    2. Final inspection completion date (loppukatselmus task with status 'lopullinen' and state 'sent')
    Note: End date is only calculated if start date exists

  Substitute Foreman Dates:
    If substitute dates exist (sijaistus.alkamisPvm/paattymisPvm in foreman document):
    - Start: MAX of substitute start and responsibility start (latest date)
    - End: MIN of substitute end and responsibility end (earliest date)
    - Invalid substitute ranges (start > end) are ignored

  Edge Cases:
    - Returns {:started nil, :ended nil} when no dates are available
    - Returns nil when final date range has start > end
    - Negative verdicts don't provide verdict timestamps but other dates still apply
    - V3 documents without state are considered 'new' and have no dates

  Compatibility:
    Works with both tyonjohtaja-v2 applications and tyonjohtaja-v3 documents."
  [{:keys [permitSubtype foremanTermination state] :as foreman-app-or-doc} {:keys [tasks] :as project-app}]
  (let [verdict                      (if (:tj-document foreman-app-or-doc)
                                       (vif/latest-published-foreman-verdict project-app foreman-app-or-doc)
                                       (vif/latest-published-verdict {:application foreman-app-or-doc}))
        foreman-doc                  (if (:tj-document foreman-app-or-doc)
                                       foreman-app-or-doc
                                       (domain/get-document-by-name foreman-app-or-doc
                                                                    :tyonjohtaja-v2))
        tj-verdict-ts                (when (some-> verdict vif/verdict-negative? not)
                                       (or (vc/responsibilities-start-date verdict)
                                           (vc/verdict-date verdict)
                                           (vc/verdict-published verdict)))
        ilmoitus?                    (= "tyonjohtaja-ilmoitus" permitSubtype)
        submitted?                   (states/post-submitted-states (keyword state))
        tj-doc-done?                 (and (:tj-document foreman-app-or-doc)
                                          (#{"declared" "acknowledged"} state))
        {tj-started-ts   :started
         tj-submitted-ts :submitted} (when (or (and ilmoitus? submitted?) tj-doc-done?)
                                       foreman-app-or-doc)
        ;; Responsibilities started
        started                      (or
                                       (:started foremanTermination)
                                       tj-started-ts ;; Explicitly set by the authority
                                       tj-verdict-ts
                                       tj-submitted-ts)
        ;; Responsibilities ended
        ended                        (when started
                                       (or (:ended foremanTermination)
                                           (->> tasks
                                                (map tools/unwrapped)
                                                (util/find-first #(and (= "loppukatselmus" (-> % :data :katselmuksenLaji))
                                                                       (= "lopullinen" (-> % :data :katselmus :tila))
                                                                       (= "sent" (-> % :state))))
                                                :data
                                                :katselmus
                                                :pitoPvm
                                                date/timestamp)))
        [sub-start sub-end]          (substitute-timestamps foreman-doc)
        started                      (some->> [sub-start started]
                                              (remove nil?)
                                              seq
                                              (apply max))
        ended                        (some->> [sub-end ended]
                                              (remove nil?)
                                              seq
                                              (apply min))]
    (when-not (and started ended (> started ended))
      {:started started :ended ended})))

(defn foreman-termination-requested?
  "Returns true if a user has requested termination of this foreman.
   Returns false if foreman already terminated."
  [{:keys [foremanTermination]}]
  (= "requested" (:state foremanTermination)))

(defn select-latest-verdict-status
  "Verdict status is either ok, rejected or new."
  [{state :state :as application} & [foreman-doc]]
  (let [state (if foreman-doc (:state foreman-doc) state)]
    ;; foreman-v3 is new when state is missing
    (cond (and foreman-doc (nil? state)) {:status "new"}
          (util/=as-kw state :acknowledged) {:status "ok"}
          :else (let [latest    (if foreman-doc
                                  (vif/latest-published-foreman-verdict application (:id foreman-doc))
                                  (vif/latest-published-verdict {:application application}))
                      code      (vc/verdict-code latest)
                      negative? (vif/verdict-negative? latest)]
                  (util/assoc-when {:status (cond
                                              negative? "rejected"
                                              (false? negative?) "ok"
                                              :else "new")}
                                   :statusLoc (cond
                                                (ss/blank? code) nil
                                                (re-find #"^\d+$" code) (util/kw-path :verdict.status code)
                                                :else (util/kw-path :pate.verdict-code code)))))))

(defn filter-foreman-doc-responsibilities
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
  (-> (select-keys foreman-app [:id :state :auth :documents :organization :_non-listed-foreman :started :submitted :changeLog :history])
      (merge {:latest-verdict-status  (select-latest-verdict-status foreman-app)
              :termination-reason     (get-in foreman-app [:foremanTermination :reason])
              :termination-requested  (foreman-termination-requested? foreman-app)
              :termination-request-ts (get-in foreman-app [:foremanTermination :request-ts])
              :termination-requester  (get-in foreman-app [:foremanTermination :requester :id])
              :latest-started-edit    (some->> foreman-app
                                               :changeLog
                                               (filter #(= (:field %) "started"))
                                               (sort-by :ts)
                                               last
                                               (#(select-keys % [:user :ts])))}
             (get-foreman-responsibility-timestamps foreman-app project-app))
      (update :documents #(->> (domain/get-documents-by-name % "tyonjohtaja-v2")
                               (map filter-foreman-doc-responsibilities)))))

(defn foreman-application-info-for-v3
  "Foreman info for V3. This is a separate function to avoid breaking the existing code.
  Gathers the data on the project application based on the foreman role"
  [project-app foreman-doc]
  (-> (select-keys project-app [:id :auth :organization :_non-listed-foreman :started :submitted :changeLog])
      (merge {:latest-verdict-status  (select-latest-verdict-status project-app foreman-doc)
              :termination-reason     (get-in foreman-doc [:foremanTermination :reason])
              :termination-requested  (foreman-termination-requested? foreman-doc)
              :termination-request-ts (get-in foreman-doc [:foremanTermination :request-ts])
              :termination-requester  (get-in foreman-doc [:foremanTermination :requester :id])
              :latest-started-edit    (some->> foreman-doc
                                               :changeLog
                                               (filter #(= (:field %) "started"))
                                               (sort-by :ts)
                                               last
                                               (#(select-keys % [:user :ts])))
              :state                  (:state foreman-doc)}
             (get-foreman-responsibility-timestamps foreman-doc project-app))
      (assoc :documents [(filter-foreman-doc-responsibilities foreman-doc)])))

(def foreman-info-projection
  [:id :state :created :submitted :started :auth :documents :organization
   :verdicts :pate-verdicts :foremanTermination :permitSubtype :history
   :_non-listed-foreman :changeLog])

(defn get-project-application-for-foreman
  "Given a foreman application, returns the linked project application.
   This is basically the inverse of get-linked-foreman-applications."
  [{foreman-app-id :id}]
  (when-let [project-app-id (->> (mongo/select :app-links {:link foreman-app-id})
                                 (some (fn [app-link]
                                         (let [link-app-id (first (remove #{foreman-app-id} (:link app-link)))
                                               link        ((keyword link-app-id) app-link)]
                                           (when (= (:linkpermittype link) "lupapistetunnus")
                                             link-app-id)))))]
    (domain/get-application-no-access-checking project-app-id)))

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

(defn get-foreman-v3-applications [application]
  (some->> (domain/get-documents-by-name application "tyonjohtaja-v3")
           (map #(foreman-application-info-for-v3 application %))))

(defn foreman-terminated? [foreman-doc]
  (-> foreman-doc :foremanTermination :state (= "confirmed")))

(defn foreman-termination-requested-phase?
  "If an authority terminates a foreman, skipping the request phase altogether,
  the confirmer and requester will be the same authority."
  [foreman-doc]
  (let [termination (-> foreman-doc :foremanTermination)]
    (not= (-> termination :confirmer :id)
          (-> termination :requester :id))))
