(ns lupapalvelu.batchrun
  (:require [taoensso.timbre :refer [debug debugf error errorf info]]
            [me.raynes.fs :as fs]
            [monger.operators :refer :all]
            [clojure.set :as set]
            [slingshot.slingshot :refer [try+]]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [monger.core :as monger]
            [monger.query :as monger-query]
            [monger.credentials :as monger-cred]
            [lupapalvelu.action :refer :all]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.integrations.matti :as matti]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.neighbors-api :as neighbors]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.prev-permit :as prev-permit]
            [lupapalvelu.review :as review]
            [lupapalvelu.states :as states]
            [lupapalvelu.tasks :as tasks]
            [lupapalvelu.ttl :as ttl]
            [lupapalvelu.user :as user]
            [lupapalvelu.verdict :as verdict]
            [lupapalvelu.xml.krysp.reader :as krysp-reader]
            [lupapalvelu.xml.krysp.application-from-krysp :as krysp-fetch]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.dummy-email-server]
            [sade.http :as http]
            [sade.strings :as ss]
            [sade.threads :as threads]
            [sade.util :refer [fn-> pcond->] :as util]
            [lupapalvelu.xml.asianhallinta.reader :as ah-reader]
            [monger.collection :as mc]
            [clojure.data :as cd]
            [clojure.pprint])
  (:import [org.xml.sax SAXParseException]))


(defn- older-than [timestamp] {$lt timestamp})

(defn- newer-than [timestamp] {$gt timestamp})

(defn- get-app-owner [application]
  (let [owner (auth/get-auths-by-role application :owner)]
    (user/get-user-by-id (-> owner first :id))))

(defn system-not-in-lockdown? []
  (-> (http/get "http://127.0.0.1:8000/system/status")
      http/decode-response
      :body :data :not-in-lockdown :data))

;; Email definition for the "open info request reminder"

(defn- oir-reminder-base-email-model [{{token :token-id created-date :created-date} :data :as command} _ recipient]
  (merge (notifications/create-app-model command nil recipient)
         {:link (fn [lang] (str (env/value :host) "/api/raw/openinforequest?token-id=" token "&lang=" (name lang)))
          :inforequest-created created-date}))

(def- oir-reminder-email-conf
  {:recipients-fn  notifications/from-data
   :subject-key    "open-inforequest-reminder"
   :model-fn       oir-reminder-base-email-model
   :application-fn (fn [{id :id}] (mongo/by-id :applications id))})

(notifications/defemail :reminder-open-inforequest oir-reminder-email-conf)

;; Email definition for the "Neighbor reminder"

(notifications/defemail :reminder-neighbor (assoc neighbors/email-conf :subject-key "neighbor-reminder"))

;; Email definition for the "Request statement reminder"

(defn- statement-reminders-email-model [{{:keys [created-date statement]} :data application :application :as command} _ recipient]
  (merge (notifications/create-app-model command nil recipient)
    {:link     #(notifications/get-application-link application "/statement" % recipient)
     :statement-request-created created-date
     :due-date (util/to-local-date (:dueDate statement))
     :message  (:saateText statement)}))

(notifications/defemail :reminder-request-statement
  {:recipients-fn  :recipients
   :subject-key    "statement-request-reminder"
   :model-fn       statement-reminders-email-model})

;; Email definition for the "Statement due date reminder"

(notifications/defemail :reminder-statement-due-date
  {:recipients-fn  :recipients
   :subject-key    "reminder-statement-due-date"
   :model-fn       statement-reminders-email-model})

;; Email definition for the "Application state reminder"

(notifications/defemail :reminder-application-state
  {:subject-key    "active-application-reminder"
   :recipients-fn  notifications/from-user})

;; Email definition for the "YA work time is expiring"

(defn- ya-work-time-is-expiring-reminder-email-model [{{work-time-expires-date :work-time-expires-date} :data :as command} _ recipient]
  (assoc
    (notifications/create-app-model command nil recipient)
    :work-time-expires-date work-time-expires-date))

(notifications/defemail :reminder-ya-work-time-is-expiring
  {:subject-key    "ya-work-time-is-expiring-reminder"
   :model-fn       ya-work-time-is-expiring-reminder-email-model})




;; "Lausuntopyynto: Pyyntoon ei ole vastattu viikon kuluessa ja hakemuksen tila on nakyy viranomaiselle tai hakemys jatetty. Lahetetaan viikoittain uudelleen."
(defn statement-request-reminder []
  (let [timestamp-now (now)
        timestamp-1-week-ago (util/get-timestamp-ago :week 1)
        apps (mongo/select :applications
                           {:state {$in ["open" "submitted"]}
                            :statements {$elemMatch {:requested (older-than timestamp-1-week-ago)
                                                     :given nil
                                                     $or [{:reminder-sent {$exists false}}
                                                          {:reminder-sent nil}
                                                          {:reminder-sent (older-than timestamp-1-week-ago)}]}}}
                           [:statements :state :modified :infoRequest :title :address :municipality :primaryOperation])]
    (doseq [app apps
            statement (:statements app)
            :let [requested (:requested statement)
                  due-date (:dueDate statement)
                  reminder-sent (:reminder-sent statement)]
            :when (and
                    (nil? (:given statement))
                    (< requested timestamp-1-week-ago)
                    (or (nil? reminder-sent) (< reminder-sent timestamp-1-week-ago))
                    (or (nil? due-date) (> due-date timestamp-now)))]
      (logging/with-logging-context {:applicationId (:id app)}
        (notifications/notify! :reminder-request-statement {:application app
                                                            :recipients [(user/get-user-by-email (get-in statement [:person :email]))]
                                                            :data {:created-date (util/to-local-date requested)
                                                                   :statement statement}})
        (update-application (application->command app)
          {:statements {$elemMatch {:id (:id statement)}}}
          {$set {:statements.$.reminder-sent timestamp-now}})))))



;; "Lausuntopyynnon maaraika umpeutunut, mutta lausuntoa ei ole annettu. Muistutus lahetetaan viikoittain uudelleen."
(defn statement-reminder-due-date []
  (let [timestamp-now (now)
        timestamp-1-week-ago (util/get-timestamp-ago :week 1)
        apps (mongo/select :applications
                           {:state {$nin (map name (clojure.set/union states/post-verdict-states states/terminal-states))}
                            :statements {$elemMatch {:given nil
                                                     $and [{:dueDate {$exists true}}
                                                           {:dueDate (older-than timestamp-now)}]
                                                     $or [{:duedate-reminder-sent {$exists false}}
                                                          {:duedate-reminder-sent nil}
                                                          {:duedate-reminder-sent (older-than timestamp-1-week-ago)}]}}}
                           [:statements :state :modified :infoRequest :title :address :municipality :primaryOperation])]
    (doseq [app apps
            statement (:statements app)
            :let [due-date (:dueDate statement)
                  duedate-reminder-sent (:duedate-reminder-sent statement)]
            :when (and
                    (nil? (:given statement))
                    (number? due-date)
                    (< due-date timestamp-now)
                    (or (nil? duedate-reminder-sent) (< duedate-reminder-sent timestamp-1-week-ago)))]
      (logging/with-logging-context {:applicationId (:id app)}
        (notifications/notify! :reminder-statement-due-date {:application app
                                                             :recipients [(user/get-user-by-email (get-in statement [:person :email]))]
                                                             :data {:due-date (util/to-local-date due-date)
                                                                    :statement statement}})
        (update-application (application->command app)
          {:statements {$elemMatch {:id (:id statement)}}}
          {$set {:statements.$.duedate-reminder-sent (now)}})))))



;; "Neuvontapyynto: Neuvontapyyntoon ei ole vastattu viikon kuluessa eli neuvontapyynnon tila on avoin. Lahetetaan viikoittain uudelleen."
(defn open-inforequest-reminder []
  (let [timestamp-1-week-ago (util/get-timestamp-ago :week 1)
        oirs (mongo/select :open-inforequest-token {:created (older-than timestamp-1-week-ago)
                                                    :last-used nil
                                                    $or [{:reminder-sent {$exists false}}
                                                         {:reminder-sent nil}
                                                         {:reminder-sent (older-than timestamp-1-week-ago)}]})]
    (doseq [oir oirs]
      (let [application (mongo/by-id :applications (:application-id oir) [:state :modified :title :address :municipality :primaryOperation])]
        (logging/with-logging-context {:applicationId (:id application)}
          (when (= "info" (:state application))
            (notifications/notify! :reminder-open-inforequest {:application application
                                                               :data {:email (:email oir)
                                                                      :token-id (:id oir)
                                                                      :created-date (util/to-local-date (:created oir))}})
            (mongo/update-by-id :open-inforequest-token (:id oir) {$set {:reminder-sent (now)}})
            ))))))


;; "Naapurin kuuleminen: Kuulemisen tila on "Sahkoposti lahetetty", eika allekirjoitusta ole tehty viikon kuluessa ja hakemuksen tila on nakyy viranomaiselle tai hakemus jatetty. Muistutus lahetetaan kerran."
(defn neighbor-reminder []
  (let [timestamp-1-week-ago (util/get-timestamp-ago :week 1)
        apps (mongo/select :applications
                           {:state {$in ["open" "submitted"]}
                            :neighbors.status {$elemMatch {$and [{:state {$in ["email-sent"]}}
                                                                 {:created (older-than timestamp-1-week-ago)}
                                                                 ]}}}
                           [:neighbors :state :modified :title :address :municipality :primaryOperation])]
    (doseq [app apps
            neighbor (:neighbors app)
            :let [statuses (:status neighbor)]]
      (logging/with-logging-context {:applicationId (:id app)}
        (when (not-any? #(or
                           (= "reminder-sent" (:state %))
                           (= "response-given-ok" (:state %))
                           (= "response-given-comments" (:state %))
                           (= "mark-done" (:state %))) statuses)

          (doseq [status statuses]

            (when (and
                    (= "email-sent" (:state status))
                    (< (:created status) timestamp-1-week-ago))
              (notifications/notify! :reminder-neighbor {:application app
                                                         :user        {:email (:email status)}
                                                         :data        {:token      (:token status)
                                                                       :expires    (util/to-local-datetime (+ ttl/neighbor-token-ttl (:created status)))
                                                                       :neighborId (:id neighbor)}})
              (update-application (application->command app)
                {:neighbors {$elemMatch {:id (:id neighbor)}}}
                {$push {:neighbors.$.status {:state    "reminder-sent"
                                             :token    (:token status)
                                             :created  (now)}}}))))))))



;; "YA hakemus: Hakemukselle merkitty tyoaika umpeutuu viikon kuluessa ja hakemuksen tila on valmisteilla tai vireilla. Lahetetaan viikoittain uudelleen."
(defn ya-work-time-is-expiring-reminder []
  (let [timestamp-1-week-in-future (util/get-timestamp-from-now :week 1)
        apps (mongo/select :applications
                           {:permitType "YA"
                            :state {$in ["verdictGiven" "constructionStarted"]}
                            ;; Cannot compare timestamp directly against date string here (e.g against "08.10.2015"). Must do it in function body.
                            :documents {$elemMatch {:schema-info.name "tyoaika"}}
                            :work-time-expiring-reminder-sent {$exists false}}
                           [:documents :auth :state :modified :title :address :municipality :infoRequest :primaryOperation])]
    (doseq [app apps
            :let [tyoaika-doc (some
                                (fn [doc]
                                  (when (= "tyoaika" (-> doc :schema-info :name)) doc))
                                (:documents app))
                  work-time-expires-timestamp (-> tyoaika-doc :data :tyoaika-paattyy-ms :value)]
            :when (and
                    work-time-expires-timestamp
                    (> work-time-expires-timestamp (now))
                    (< work-time-expires-timestamp timestamp-1-week-in-future))]
      (logging/with-logging-context {:applicationId (:id app)}
        (notifications/notify! :reminder-ya-work-time-is-expiring {:application app
                                                                   :user (get-app-owner app)
                                                                   :data {:work-time-expires-date (util/to-local-date work-time-expires-timestamp)}})
        (update-application (application->command app)
          {$set {:work-time-expiring-reminder-sent (now)}})))))

(defn application-state-reminder
  "Hakemus: Hakemuksen tila on luonnos tai nakyy viranomaiselle, mutta
  edellisesta paivityksesta on aikaa yli kuukausi ja alle puoli
  vuotta. Lahetetaan kuukausittain uudelleen. Ei laheteta
  digitoiduille luville."
  []
  (let [apps (mongo/select :applications
                           {:state {$in ["draft" "open"]}
                            :permitType {$ne "ARK"}
                            $and [{:modified (older-than (util/get-timestamp-ago :month 1))}
                                  {:modified (newer-than (util/get-timestamp-ago :month 6))}]
                            $or [{:reminder-sent {$exists false}}
                                 {:reminder-sent nil}
                                 {:reminder-sent (older-than (util/get-timestamp-ago :month 1))}]}
                           [:auth :state :modified :title :address :municipality :infoRequest :primaryOperation])]
    (doseq [app apps]
      (logging/with-logging-context {:applicationId (:id app)}
        (notifications/notify! :reminder-application-state {:application app
                                                            :user (get-app-owner app)})
        (update-application (application->command app)
          {$set {:reminder-sent (now)}})))))


(defn send-reminder-emails [& args]
  (when (env/feature? :reminders)
    (mongo/connect!)
    (statement-request-reminder)
    (statement-reminder-due-date)
    (open-inforequest-reminder)
    (neighbor-reminder)
    (application-state-reminder)
    (ya-work-time-is-expiring-reminder)

    (mongo/disconnect!)))

(defn fetch-verdicts []
  (let [orgs-with-wfs-url-defined-for-some-scope (organization/get-organizations
                                                   {$or [{:krysp.R.url {$exists true}}
                                                         {:krysp.YA.url {$exists true}}
                                                         {:krysp.P.url {$exists true}}
                                                         {:krysp.MAL.url {$exists true}}
                                                         {:krysp.VVVL.url {$exists true}}
                                                         {:krysp.YI.url {$exists true}}
                                                         {:krysp.YL.url {$exists true}}
                                                         {:krysp.KT.url {$exists true}}]}
                                                   {:krysp 1})
        orgs-by-id (util/key-by :id orgs-with-wfs-url-defined-for-some-scope)
        org-ids (keys orgs-by-id)
        apps (mongo/select :applications {:state {$in ["sent"]} :organization {$in org-ids}})
        eraajo-user (user/batchrun-user org-ids)]
    (doall
      (pmap
        (fn [{:keys [id permitType organization] :as app}]
          (logging/with-logging-context {:applicationId id, :userId (:id eraajo-user)}
            (let [url (get-in orgs-by-id [organization :krysp (keyword permitType) :url])]
              (try
                (if-not (ss/blank? url)
                  (let [command (assoc (application->command app) :user eraajo-user :created (now) :action "fetch-verdicts")
                        result (verdict/do-check-for-verdict command)]
                    (when (-> result :verdicts count pos?)
                      ;; Print manually to events.log, because "normal" prints would be sent as emails to us.
                      (logging/log-event :info {:run-by "Automatic verdicts checking" :event "Found new verdict"})
                      (notifications/notify! :application-state-change command))
                    (when (or (nil? result) (fail? result))
                      (logging/log-event :error {:run-by "Automatic verdicts checking"
                                                 :event "Failed to check verdict"
                                                 :failure (if (nil? result) :error.no-app-xml result)
                                                 :organization {:id organization :permit-type permitType}
                                                 })))

                  (logging/log-event :info {:run-by "Automatic verdicts checking"
                                            :event "No Krysp WFS url defined for organization"
                                            :organization {:id organization :permit-type permitType}}))
                (catch Throwable t
                  (logging/log-event :error {:run-by "Automatic verdicts checking"
                                             :event "Unable to get verdict from backend"
                                             :exception-message (.getMessage t)
                                             :application-id id
                                             :organization {:id organization :permit-type permitType}}))))))
          apps))))

(defn check-for-verdicts [& args]
  (when-not (system-not-in-lockdown?)
    (logging/log-event :info {:run-by "Automatic verdict checking" :event "Not run - system in lockdown"})
    (fail! :system-in-lockdown))
  (mongo/connect!)
  (fetch-verdicts))

(defn- get-asianhallinta-ftp-users [organizations]
  (->> (for [org organizations
             scope (:scope org)]
         (get-in scope [:caseManagement :ftpUser]))
    (remove nil?)
    distinct))

(defn fetch-asianhallinta-messages []
  (let [ah-organizations (mongo/select :organizations
                                       {"scope.caseManagement.ftpUser" {$exists true}}
                                       {"scope.caseManagement.ftpUser" 1})
        ftp-users (if (string? (env/value :ely :sftp-user))
                    (conj (get-asianhallinta-ftp-users ah-organizations) (env/value :ely :sftp-user))
                    (get-asianhallinta-ftp-users ah-organizations))
        eraajo-user (user/batchrun-user (map :id ah-organizations))]
    (logging/log-event :info {:run-by "Asianhallinta reader"
                              :event (format "Reader process start - %d ftp users to be checked" (count ftp-users))})
    (doseq [ftp-user ftp-users
            :let [path (str
                         (env/value :outgoing-directory) "/"
                         ftp-user "/"
                         "asianhallinta/to_lupapiste/")]
            zip (util/get-files-by-regex path #".+\.zip$")]
      (fs/mkdirs (str path "archive"))
      (fs/mkdirs (str path "error"))
      (let [zip-path (.getPath zip)
            result (try
                     (ah-reader/process-message zip-path ftp-user eraajo-user)
                     (catch Throwable e
                       (logging/log-event :error {:run-by "Asianhallinta reader"
                                                  :event "Unable to process ah zip file"
                                                  :exception-message (.getMessage e)})
                       ;; (error e "Error processing zip-file in asianhallinta verdict batchrun")
                       (fail :error.unknown)))
            target (str path (if (ok? result) "archive" "error") "/" (.getName zip))]
        (logging/log-event (if (ok? result) :info :error)
                           (util/assoc-when {:run-by "Asianhallinta reader"
                                             :event (if (ok? result)  "Succesfully processed message" "Failed to process message")
                                             :zip-path zip-path}
                                            :text (:text result)))
        (when-not (fs/rename zip target)
          (errorf "Failed to rename %s to %s" zip-path target))))
    (logging/log-event :info {:run-by "Asianhallinta reader" :event "Reader process finished"})))

(defn check-for-asianhallinta-messages [& args]
  (when-not (system-not-in-lockdown?)
    (logging/log-event :info {:run-by "Asianhallinta reader" :event "Not run - system in lockdown"})
    (fail! :system-in-lockdown))
  (mongo/connect!)
  (fetch-asianhallinta-messages))

(defn orgs-for-review-fetch [& organization-ids]
  (mongo/select :organizations
                (merge {:krysp.R.url {$exists true},
                        :krysp.R.version {$gte "2.1.5"}}
                       (when-not (seq organization-ids) {:automatic-review-fetch-enabled true})
                       (when (seq organization-ids) {:_id {$in organization-ids}}))
                {:krysp true}))

(defn- save-reviews-for-application [user application {:keys [updates added-tasks-with-updated-buildings attachments-by-task-id] :as result}]
  (logging/with-logging-context {:applicationId (:id application) :userId (:id user)}
    (when (ok? result)
      (try
        (review/save-review-updates (assoc (application->command application) :user user)
                                    updates
                                    added-tasks-with-updated-buildings
                                    attachments-by-task-id)
        (catch Throwable t
          {:ok false :desc (.getMessage t)})))))

(defn- read-reviews-for-application
  [user created application app-xml & [overwrite-background-reviews?]]
  (try
    (when (and application app-xml)
      (logging/with-logging-context {:applicationId (:id application) :userId (:id user)}
        (let [{:keys [review-count updated-tasks validation-errors] :as result} (review/read-reviews-from-xml user created application app-xml overwrite-background-reviews?)]
          (cond
            (and (ok? result) (pos? review-count)) (logging/log-event :info {:run-by "Automatic review checking"
                                                                             :event "Reviews found"
                                                                             :updated-tasks updated-tasks})
            (ok? result)                           (logging/log-event :info {:run-by "Automatic review checking"
                                                                             :event "No reviews"})
            (fail? result)                         (logging/log-event :error {:run-by "Automatic review checking"
                                                                              :event "Failed to read reviews"
                                                                              :validation-errors validation-errors}))
          result)))
    (catch Throwable t
      (errorf "error.integration - Could not read reviews for %s" (:id application)))))

(defn fetch-reviews-for-organization-permit-type-consecutively [organization permit-type applications]
  (logging/log-event :info {:run-by "Automatic review checking"
                            :event "Fetch consecutively"
                            :organization-id (:id organization)
                            :application-count (count applications)
                            :applications (map :id applications)})
  (->> (map (fn [app]
              (try
                (krysp-fetch/fetch-xmls-for-applications organization permit-type [app])
                (catch Throwable t
                  (logging/log-event :error {:run-by "Automatic review checking"
                                             :application-id (:id app)
                                             :organization-id (:id organization)
                                             :exception (.getName (class t))
                                             :message (.getMessage t)
                                             :event (format "Unable to get reviews for %s from %s backend"  permit-type (:id organization))})
                  nil)))
            applications)
       (apply concat)
       (remove nil?)))

(defn- fetch-reviews-for-organization-permit-type [eraajo-user organization permit-type applications]
  (try+

   (logging/log-event :info {:run-by "Automatic review checking"
                             :event "Start fetching xmls"
                             :organization-id (:id organization)
                             :application-count (count applications)
                             :applications (map :id applications)})

   (krysp-fetch/fetch-xmls-for-applications organization permit-type applications)

   (catch SAXParseException e
     (logging/log-event :error {:run-by "Automatic review checking"
                                :organization-id (:id organization)
                                :event (format "Could not understand response when getting reviews in chunks from %s backend" (:id organization))})
     ;; Fallback into fetching xmls consecutively
     (fetch-reviews-for-organization-permit-type-consecutively organization permit-type applications))

   (catch [:sade.core/type :sade.core/fail
           :status         404] _
     (logging/log-event :error {:run-by "Automatic review checking"
                                :organization-id (:id organization)
                                :event (format "Unable to get reviews in chunks from %s backend: Got HTTP status 404" (:id organization))})
     ;; Fallback into fetching xmls consecutively
     (fetch-reviews-for-organization-permit-type-consecutively organization permit-type applications))


   (catch [:sade.core/type :sade.core/fail] t
     (logging/log-event :error {:run-by "Automatic review checking"
                                :organization-id (:id organization)
                                :event (format "Unable to get reviews from %s backend: %s" (:id organization) (select-keys t [:status :text]))}))

   (catch Object o
     (logging/log-event :error {:run-by "Automatic review checking"
                                :organization-id (:id organization)
                                :exception (.getName (class o))
                                :message (get &throw-context :message "")
                                :event (format "Unable to get reviews in chunks from %s backend: %s - %s"
                                               (:id organization) (.getName (class o)) (get &throw-context :message ""))}))))

(defn- organization-applications-for-review-fetching
  [organization-id permit-type projection & application-ids]
  (let [eligible-application-states (set/difference states/post-verdict-but-terminal #{:foremanVerdictGiven})]
    (mongo/select :applications (merge {:state {$in eligible-application-states}
                                        :permitType permit-type
                                        :organization organization-id
                                        :primaryOperation.name {$nin ["tyonjohtajan-nimeaminen-v2" "suunnittelijan-nimeaminen"]}}
                                       (when (not-empty application-ids)
                                         {:_id {$in application-ids}}))
                  (merge app-state/timestamp-key
                         (pcond-> projection
                                  sequential? (zipmap (repeat true)))))))

(defn mark-reviews-faulty-for-application [application {:keys [new-faulty-tasks]}]
  (when (not (empty? new-faulty-tasks))
    (let [timestamp (now)]
      (doseq [task-id new-faulty-tasks]
        (tasks/task->faulty (assoc (application->command application)
                                   :created timestamp)
                            task-id)))))

(defn- log-review-results-for-organization [organization-id applications-with-results]
  (logging/log-event :info {:run-by "Automatic review checking"
                            :event "Review checking finished for organization"
                            :organization-id organization-id
                            :application-count (count applications-with-results)
                            :faulty-tasks (->> applications-with-results
                                               (map (juxt (comp :id first)
                                                          (comp :new-faulty-tasks
                                                                second)))
                                               (into {}))}))

(defn- fetch-reviews-for-organization
  [eraajo-user created {org-krysp :krysp :as organization} permit-types applications {:keys [overwrite-background-reviews?]}]
  (let [fields [:address :primaryOperation :permitSubtype :history :municipality :state :permitType :organization :tasks :verdicts :modified :documents]
        projection (cond-> (distinct (concat fields matti/base-keys))
                     overwrite-background-reviews? (conj :attachments))
        permit-types (remove (fn-> keyword org-krysp :url ss/blank?) permit-types)
        grouped-apps (if (seq applications)
                       (group-by :permitType applications)
                       (->> (map #(organization-applications-for-review-fetching (:id organization) % projection) permit-types)
                            (zipmap permit-types)))]
    (->> (mapcat (partial apply fetch-reviews-for-organization-permit-type eraajo-user organization) grouped-apps)
         (map (fn [[{app-id :id permit-type :permitType} app-xml]]
                (let [app    (first (organization-applications-for-review-fetching (:id organization) permit-type projection app-id))
                      result (read-reviews-for-application eraajo-user created app app-xml overwrite-background-reviews?)
                      save-result (save-reviews-for-application eraajo-user app result)]
                  ;; save-result is nil when application query fails (application became irrelevant for review fetching) -> no actions taken
                  (when (fail? save-result)
                    (logging/log-event :info {:run-by "Automatic review checking"
                                              :event  "Failed to save review updates for application"
                                              :reason (:desc save-result)
                                              :application-id app-id
                                              :result result}))
                  (when (ok? save-result)
                    (mark-reviews-faulty-for-application app result)
                    [app result]))))
         (remove nil?)
         (log-review-results-for-organization (:id organization)))))

(defn poll-verdicts-for-reviews
  [& {:keys [application-ids organization-ids overwrite-background-reviews?] :as options}]
  (let [applications  (when (seq application-ids)
                        (mongo/select :applications {:_id {$in application-ids}}))
        permit-types  (-> (map (comp keyword :permitType) applications) distinct not-empty (or [:R]))
        organizations (->> (map :organization applications) distinct (concat organization-ids) (apply orgs-for-review-fetch))
        eraajo-user   (user/batchrun-user (map :id organizations))
        threadpool    (threads/threadpool (util/->int (env/value :batchrun :review-check-threadpool-size) 8) "review checking worker")
        threads       (mapv (fn [org]
                              (threads/submit
                               threadpool
                               (try
                                 (fetch-reviews-for-organization eraajo-user (now) org permit-types (filter (comp #{(:id org)} :organization) applications) options)
                                 (catch Throwable t
                                   (logging/log-event :info {:run-by "Automatic review checking"
                                                             :event  "Failed to check reviews for organization"
                                                             :organization-id (:id org)
                                                             :reason (.getMessage t)})))))
                            organizations)]
    (threads/wait-for-threads threads)))

(defn check-for-reviews [& args]
  (when-not (system-not-in-lockdown?)
    (logging/log-event :info {:run-by "Automatic review checking" :event "Not run - system in lockdown"})
    (fail! :system-in-lockdown))
  (logging/log-event :info {:run-by "Automatic review checking" :event "Started"})
  (mongo/connect!)
  (poll-verdicts-for-reviews)
  (logging/log-event :info {:run-by "Automatic review checking" :event "Finished"}))

(defn check-reviews-for-orgs [& args]
  (when-not (system-not-in-lockdown?)
    (logging/log-event :info {:run-by "Automatic review checking" :event "Not run - system in lockdown"})
    (fail! :system-in-lockdown))
  (logging/log-event :info {:run-by "Automatic review checking" :event "Started" :organizations args})
  (mongo/connect!)
  (poll-verdicts-for-reviews :organization-ids args)
  (logging/log-event :info {:run-by "Automatic review checking" :event "Finished" :organizations args}))

(defn overwrite-reviews-for-orgs [& args]
  (when-not (system-not-in-lockdown?)
    (logging/log-event :info {:run-by "Review checking with overwrite" :event "Not run - system in lockdown"})
    (fail! :system-in-lockdown))
  (when (empty? args)
    (logging/log-event :info {:run-by "Review checking with overwrite" :event "Not run - no organizations specified"})
    (fail! :no-organizations))
  (logging/log-event :info {:run-by "Review checking with overwrite" :event "Started" :organizations args})
  (mongo/connect!)
  (poll-verdicts-for-reviews :organization-ids args
                             :overwrite-background-reviews? true)
  (logging/log-event :info {:run-by "Review checking with overwrite" :event "Finished" :organizations args}))

(defn check-reviews-for-ids [& args]
  (when-not (system-not-in-lockdown?)
    (logging/log-event :info {:run-by "Automatic review checking" :event "Not run - system in lockdown"})
    (fail! :system-in-lockdown))
  (logging/log-event :info {:run-by "Automatic review checking" :event "Started" :applications args})
  (mongo/connect!)
  (poll-verdicts-for-reviews :application-ids args)
  (logging/log-event :info {:run-by "Automatic review checking" :event "Finished" :applications args}))

(defn extend-previous-permit [& args]
  (mongo/connect!)
  (if (= (count args) 1)
    (if-let [application (domain/get-application-no-access-checking (first args))]
      (let [kuntalupatunnus (get-in application [:verdicts 0 :kuntalupatunnus])
            app-xml  (krysp-fetch/get-application-xml-by-backend-id application kuntalupatunnus)
                    #_(sade.xml/parse (slurp "verdict-r-extend-prev-permit.xml"))
            app-info (krysp-reader/get-app-info-from-message app-xml kuntalupatunnus)]
        (prev-permit/extend-prev-permit-with-all-parties application app-xml app-info)
        0)
      (do
        (println "Cannot find application")
        2))
    (do
      (println "No application id given.")
      1)))

(defn pdfa-convert-review-pdfs [& args]
  (mongo/connect!)
  (debug "# of applications with background generated tasks:"
           (mongo/count :applications {:tasks.source.type "background"}))
  (let [eraajo-user (user/batchrun-user (map :id (orgs-for-review-fetch)))]
    (doseq [application (mongo/select :applications {:tasks.source.type "background"})]
      (let [command (assoc (application->command application) :user eraajo-user :created (now))]
        (doseq [task (:tasks application)]
          (if (= "background" (:type (:source task)))
            (do
              (doseq [att (:attachments application)]
                (if (= (:id task) (:id (:source att)))
                  (do
                    (debug "application" (:id (:application command)) "- converting task" (:id task) "-> attachment" (:id att) )
                    (attachment/convert-existing-to-pdfa! (:application command) (:user command) att)))))))))))

(defn pdf-to-pdfa-conversion [& args]
  (info "Starting pdf to pdf/a conversion")
  (mongo/connect!)
  (let [organization (first args)
        start-ts (c/to-long (c/from-string (second args)))
        end-ts (c/to-long (c/from-string (second (next args))))]
  (doseq [application (mongo/select :applications {:organization organization :state :verdictGiven})]
    (let [command (application->command application)
          last-verdict-given-date (:ts (last (sort-by :ts (filter #(= (:state % ) "verdictGiven") (:history application)))))]
      (logging/with-logging-context {:applicationId (:id application)}
        (when (and (= (:state application) "verdictGiven") (< start-ts last-verdict-given-date end-ts))
          (info "Converting attachments of application" (:id application))
          (doseq [attachment (:attachments application)]
            (when (:latestVersion attachment)
              (when-not (get-in attachment [:latestVersion :archivable])
                  (do
                    (info "Trying to convert attachment" (get-in attachment [:latestVersion :filename]))
                    (let [result (attachment/convert-existing-to-pdfa! (:application command) nil attachment)]
                      (if (:archivabilityError result)
                        (error "Conversion failed to" (:id application) "/" (:id attachment) "/" (get-in attachment [:latestVersion :filename]) "with error:" (:archivabilityError result))
                        (info "Conversion succeed to" (get-in attachment [:latestVersion :filename]) "/" (:id application))))))))))))))


(defn fetch-verdict-attachments
  [start-timestamp end-timestamp organizations]
  {:pre [(number? start-timestamp)
         (number? end-timestamp)
         (< start-timestamp end-timestamp)
         (vector? organizations)]}
  (let [app-infos (verdict/applications-with-missing-verdict-attachments
                   {:start         start-timestamp
                    :end           end-timestamp
                    :organizations organizations})
        eraajo-user (user/batchrun-user (map :organization app-infos))]
    (->> (doall
          (map
           (fn [{:keys [id organization permitType]}]
             (logging/with-logging-context {:applicationId id, :userId (:id eraajo-user)}
               (try
                 (logging/log-event :info {:run-by "Automatic verdict attachments checking"
                                           :event "Fetching verdict attachments"
                                           :application-id id})
                 (let [app (domain/get-application-no-access-checking id [:state :municipality
                                                                          :address :permitType
                                                                          :permitSubtype :organization
                                                                          :primaryOperation :verdicts
                                                                          :attachments])
                       command (assoc (application->command app) :user eraajo-user :created (now) :action :fetch-verdict-attachments)
                       app-xml (krysp-fetch/get-application-xml-by-application-id app)
                       result  (verdict/update-verdict-attachments-from-xml! command app-xml)]
                   (when (-> result :updated-verdicts count pos?)
                     ;; Print manually to events.log, because "normal" prints would be sent as emails to us.
                     (logging/log-event :info {:run-by "Automatic verdict attachments checking"
                                               :event "Found new verdict attachments"
                                               :updated-verdicts (-> result :updated-verdicts)}))
                   (when (or (nil? result) (fail? result))
                     (logging/log-event :error {:run-by "Automatic verdict attachment checking"
                                                :event "Failed to check verdict attachments"
                                                :failure (if (nil? result) :error.no-app-xml result)
                                                :organization {:id organization :permit-type permitType}
                                                }))

                   ;; Return result for testing purposes
                   result)
                 (catch Throwable t
                   (logging/log-event :error {:run-by "Automatic verdict attachments checking"
                                              :event "Unable to get verdict from backend"
                                              :exception-message (.getMessage t)
                                              :application-id id})))))
           app-infos))
         (remove #(empty? (:updated-verdicts %)))
         (hash-map :updated-applications)
         (merge {:start start-timestamp
                 :end   end-timestamp
                 :organizations organizations
                 :applications (map :id app-infos)}))))

(defn check-for-verdict-attachments
  "Fetch missing verdict attachments for verdicts given in the time
  interval between start-timestamp and end-timestamp (last 3 months by
  default), for the organizations whose id's are provided as
  arguments (all organizations by default)."
  [& [start-timestamp end-timestamp & organizations]]
  (when-not (system-not-in-lockdown?)
    (logging/log-event :info {:run-by "Automatic verdict attachment checking" :event "Not run - system in lockdown"})
    (fail! :system-in-lockdown))
  (mongo/connect!)
  (fetch-verdict-attachments (or (when start-timestamp
                                   (util/to-millis-from-local-date-string start-timestamp))
                                 (-> 3 t/months t/ago c/to-long))
                             (or (when end-timestamp
                                   (util/to-millis-from-local-date-string end-timestamp))
                                 (now))
                             (or (vec organizations) [])))

(defn review-empty-muutunnus? [task]
  (= "" (get-in task [:data :muuTunnus :value])))

(defn restore-tasks-and-attachments! [app-id restored-tasks restored-attachments]
  (when (not-empty restored-tasks)
    (logging/log-event :info {:app-id app-id :event "restoring tasks and attachments" :tasks (mapv :id restored-tasks) :attachemtns (mapv :id restored-attachments)})
    (mongo/update :applications {:_id app-id} {$push {:tasks {$each restored-tasks}
                                                      :attachments {$each restored-attachments}}})))

(defn copy-files-from-backup! [backup-db attachments]
  (let [file-ids (->> (mapcat :versions attachments)
                      (mapcat (juxt :originalFileId :fileId))
                      (remove nil?)
                      (mapcat (juxt identity #(str % "-preview"))))
        fs-files  (when (not-empty file-ids)
                    (monger-query/with-collection backup-db "fs.files"
                      (monger-query/find {:_id {$in file-ids}})
                      (monger-query/snapshot)))
        fs-chunks (when (not-empty file-ids)
                    (monger-query/with-collection backup-db "fs.chunks"
                      (monger-query/find {:files_id {$in file-ids}})
                      (monger-query/snapshot)))]

    (when (and (not-empty fs-files) (not-empty fs-chunks))
      (mongo/insert-batch :fs.files fs-files)
      (mongo/insert-batch :fs.chunks fs-chunks))))

(defn restore-missing-files! [backup-db attachments]
  (when-let [file-ids (->> (mapcat :versions attachments)
                           (mapcat (juxt :originalFileId :fileId))
                           (remove nil?)
                           (mapcat (juxt identity #(str % "-preview")))
                           distinct
                           not-empty)]

    (let [existing-ids    (set (->> (monger-query/with-collection (mongo/get-db) "fs.files"
                                      (monger-query/find {:_id {$in file-ids}})
                                      (monger-query/fields [:_id]))
                                    (map :_id)))
          existing-chunks (set (->> (monger-query/with-collection (mongo/get-db) "fs.chunks"
                                      (monger-query/find {:files_id {$in file-ids}})
                                      (monger-query/fields [:_id]))
                                    (map :_id)))]

      (logging/log-event :info {:event "restoring files" :file-ids file-ids})
      (->> file-ids
           (remove existing-ids)
           (run! (fn [file-id] (some->> (monger-query/with-collection backup-db "fs.files"
                                          (monger-query/find {:_id file-id}))
                                        not-empty
                                        (mongo/insert-batch :fs.files )))))

      (logging/log-event :info {:event "restoring chunks" :file-ids file-ids})
      (->> file-ids
           (run! (fn [file-id] (some->> (monger-query/with-collection backup-db "fs.chunks"
                                          (monger-query/find {:files_id file-id}))
                                        (remove (comp existing-chunks :_id))
                                        not-empty
                                        (mongo/insert-batch :fs.chunks))))))))

(defn restore-removed-tasks [backup-host backup-port & orgs]
  (mongo/connect!)
  (let [conf     (env/value :mongodb)
        dbname   (:dbname conf)
        username (-> conf :credentials :username)
        password (-> conf :credentials :password)
        server   (monger/server-address backup-host (util/->long backup-port))
        options  (monger/mongo-options {:write-concern mongo/default-write-concern})
        backup-connection (if (and username password)
                            (monger/connect server options (monger-cred/create username dbname password))
                            (monger/connect server options))
        backup-db (monger/get-db backup-connection dbname)]

    (->> (monger-query/with-collection backup-db "applications"
           (monger-query/find (cond-> {:tasks.data.muuTunnus.value ""}
                                (not-empty orgs) (assoc :organization {$in orgs})))
           (monger-query/fields [:_id]))

         (map mongo/with-id)

         (reduce (fn [counter {app-id :id}]
                   (let [{backup-tasks :tasks backup-attachments :attachments :as backup-app} (first (monger-query/with-collection backup-db "applications"
                                                                                                       (monger-query/find {:_id app-id})
                                                                                                       (monger-query/fields [:tasks :attachments])))
                         app (mongo/by-id :applications app-id [:tasks])
                         existing-task-ids (set (map :id (:tasks app)))
                         restored-tasks (->> (filter review-empty-muutunnus? backup-tasks)
                                             (remove (comp existing-task-ids :id)))
                         restored-task-ids (set (map :id restored-tasks))
                         restored-attachments (filter (comp restored-task-ids :id :target) backup-attachments)

                         task-ids-for-file-restore (set (->> (filter review-empty-muutunnus? backup-tasks)
                                                             (map :id)))
                         attachments-for-file-restore (filter (comp task-ids-for-file-restore :id :target) backup-attachments)]

                     (logging/log-event :info {:event "restoring tasks" :app-id app-id})

                     (when app
                       (do
                         (restore-tasks-and-attachments! app-id restored-tasks restored-attachments)
                         #_(copy-files-from-backup! backup-db restored-attachments)
                         (restore-missing-files! backup-db attachments-for-file-restore)))

                     (Thread/sleep 50)

                     (cond-> counter (not-empty restored-tasks) inc)))
                 0))))

(defn generate-missing-task-pdf [application task user lang]
  (logging/log-event :info {:event "Generating a new PDF version of approved task"
                            :task-id (:id task)
                            :taskname (:taskname task)})
  (tasks/generate-task-pdfa application task user lang))

(def katselmus-types #{{:type-group "katselmukset_ja_tarkastukset" :type-id "aloituskokouksen_poytakirja"}
                       {:type-group "katselmukset_ja_tarkastukset" :type-id "katselmuksen_tai_tarkastuksen_poytakirja"}})

(defn restore-removed-tasks-v2 [& orgs]
  (mongo/connect!)
  (let [dbname   "lupapiste-20171127"
        ; 2017-11-27 00:00 GMT+2
        start-ts 1511733600001]

        (doseq [{app-id :id
                 backup-tasks :tasks
                 backup-attachments :attachments} (->> (mongo/with-db dbname
                                                                         (mongo/find-maps "applications"
                                                                                          (cond-> {:tasks {$elemMatch {:data.muuTunnus.value ""
                                                                                                                       :created {$gte start-ts}}}}
                                                                                                  (not-empty orgs) (assoc :organization {$in orgs}))
                                                                                          [:tasks :_id :attachments]))
                                                       (map mongo/with-id))]

          (info "Checking restorable reviews for application" app-id)

          (let [app (mongo/by-id :applications app-id)
                existing-task-ids (set (map :id (:tasks app)))
                restored-tasks (->> (filter review-empty-muutunnus? backup-tasks)
                                    (remove (comp existing-task-ids :id)))
                restored-task-ids (set (map :id restored-tasks))
                missing-attachments (filter (comp restored-task-ids :id :target) backup-attachments)]

            (logging/log-event :info {:event "restoring tasks from application" :app-id app-id})

            (when (and app (seq restored-tasks))
              (logging/log-event :info {:app-id app-id :event "restoring tasks" :tasks (mapv :id restored-tasks)})
              (mongo/update :applications {:_id app-id} {$push {:tasks {$each restored-tasks}}})

              (doseq [attachment missing-attachments]
                (let [task (first (filter #(= (:id %) (-> attachment :target :id)) restored-tasks))
                      user (get-in attachment [:latestVersion :user])
                      attachment-lang (case (get-in attachment [:latestVersion :filename])
                                        "Katselmus.pdf" :fi
                                        "Syn.pdf" :sv
                                        nil)]
                  (if (and (katselmus-types (:type attachment)) attachment-lang (#{"ok" "sent"} (:state task)))
                    (generate-missing-task-pdf app task user attachment-lang)
                    (let [log-data {:event "Attachment file is lost, will not be restored"
                                    :app-id app-id
                                    :attachment-id (:id attachment)
                                    :attachment-type (:type attachment)
                                    :attachment-file (-> attachment :latestVersion :filename)
                                    :latest-version-uploader user
                                    :task-id (:id task)
                                    :taskname (:taskname task)
                                    :task-state (:state task)}]
                      (println log-data)
                      (logging/log-event :warn log-data))))))
            (Thread/sleep 50)))))

(def suspect-review-ids ["551635d1e4b0432f111f25f7"
                         "584252e928e06f09d203fee4"
                         "589e9bae28e06f1f5c65a9bf"
                         "58afc916edf02d2e601b6afd"
                         "58dc9a6aedf02d13c352159a"
                         "58e5af2a28e06f32fa34bb25"
                         "58f5f6ebedf02d4491e6f7cd"
                         "591c006dedf02d0d78a2a4f0"
                         "59279a9f28e06f6a55bd8a5f"
                         "5937c8dd28e06f2898f46469"
                         "5954ad0dedf02d51f12801fc"
                         "5955bfc528e06f415198ce64"
                         "5955bfc528e06f415198ce65"
                         "597ae4cb28e06f49579b6ab7"
                         "599e514128e06f508366157e"
                         "59aec834edf02d14b24b52c7"
                         "59e976a928e06f6c3f1efdf2"
                         "59fbfa0528e06f04c79335fa"
                         "59fbfa0528e06f04c79335fb"
                         "5a02918c28e06f07573ba7cf"
                         "5a02918c28e06f07573ba7d1"
                         "5a04268fedf02d75e59b1715"
                         "5a0a8971edf02d630dbd89c3"
                         "5a12632928e06f574ffdd64f"
                         "5a12635a28e06f574ffdd704"
                         "5a12635a28e06f574ffdd705"
                         "5a12707dedf02d6fa2bd9717"
                         "5a14216a28e06f5e176e4d45"
                         "5a15061228e06f44552879e0"
                         "5a16800028e06f55733d6a29"
                         "5a17e37a59412f64606243e3"
                         "5a18fa9528e06f52467a1c33"
                         "5a1b9db028e06f7d335e8d98"
                         "5a1bc4de59412f3ee91eb4e9"
                         "5a1bfcf659412f3ee91ed675"
                         "5a1c0e4c59412f3ee91ede6d"
                         "5a1c12ad59412f3ee91ee08a"
                         "573db893edf02d75bed063c9"
                         "5780778328e06f2fcbd91454"
                         "578ef93028e06f72b427f575"
                         "578ef93028e06f72b427f576"
                         "578ef93028e06f72b427f577"
                         "578ef93028e06f72b427f578"
                         "578ef93028e06f72b427f579"
                         "5837c6d728e06f32ba262ee3"
                         "5850d21dedf02d3952f51189"
                         "58c234ca28e06f7f9d006550"
                         "58fef24a28e06f398d1797d4"
                         "5915682128e06f1e87c57f6b"
                         "5915682128e06f1e87c57f6c"
                         "5915682128e06f1e87c57f6d"
                         "5915682128e06f1e87c57f6e"
                         "5915682128e06f1e87c57f6f"
                         "591a6f1528e06f7b37f3e1a0"
                         "591a6f1528e06f7b37f3e1a1"
                         "5932289e28e06f6b6c2fa5b0"
                         "5932289e28e06f6b6c2fa5b1"
                         "593689d828e06f2898f36dfb"
                         "593a0fed28e06f2b91f85c12"
                         "593a8da528e06f67a6728698"
                         "593a8da528e06f67a6728699"
                         "593a8da528e06f67a672869a"
                         "5943c9f8edf02d7894fa4a69"
                         "59531cd028e06f3f1bf091f0"
                         "59531cd028e06f3f1bf091f2"
                         "5955fae728e06f0224c35d70"
                         "5955fae728e06f0224c35d71"
                         "595ef98e28e06f3e4991adf9"
                         "599e510f28e06f5083661443"
                         "599e510f28e06f5083661444"
                         "59a639db28e06f7e71ce44a8"
                         "59ae233528e06f2c6690da32"
                         "59c8855f28e06f334566f60f"
                         "59c8855f28e06f334566f610"
                         "59c8855f28e06f334566f611"
                         "59c9d67f28e06f6e4147bae3"
                         "59d1ca9128e06f55c43124ac"
                         "59d45e7b28e06f64dc4d5cb2"
                         "59d45e7b28e06f64dc4d5cb8"
                         "59e582d528e06f2d2eed8b92"
                         "59f2b0dd28e06f5c5902bb1e"
                         "5a05350c28e06f0241735dd6"
                         "5a13b4ac28e06f077b7955e4"
                         "5a15061228e06f44552879de"
                         "5a1678dd59412f7e35b9c99c"
                         "5a1baf1728e06f30d12c76f0"
                         "5a1bc571edf02d13cfb76fdf"
                         "5a1c067128e06f30d12caa4b"])

(defn- update-review-data [{:keys [id attachments handlers] task :tasks}]
  (let [handler (mongo/by-id :users (-> handlers first :userId))
        assignee (mongo/by-id :tasks (-> task :assignee :id))]

    (logging/log-event :info {:event "Restoring task data"
                              :app-id id
                              :task-id (:id task)
                              :taskname (:taskname task)
                              :katselmuksenLaji (-> task :data :katselmuksenLaji :value)
                              :task-state (:state task)
                              :handler-email (:email handler)
                              :assignee-email (:email assignee)})

    (mongo/update "applications"
                  {:_id id :tasks.id (:id task)}
                  {$set {"tasks.$" task}})

    (when-let [attachment (first (filter #(= (-> % :target :id) (:id task)) attachments))]
      (let [user (get-in attachment [:latestVersion :user])
            attachment-lang (case (get-in attachment [:latestVersion :filename])
                              "Katselmus.pdf" :fi
                              "Syn.pdf" :sv
                              nil)]
        (if (and (katselmus-types (:type attachment)) attachment-lang (#{"ok" "sent"} (:state task)))
          (generate-missing-task-pdf (mongo/by-id :applications id) task user attachment-lang)
          (let [log-data {:event "Attachment file is lost, will not be restored"
                          :app-id id
                          :attachment-id (:id attachment)
                          :attachment-type (:type attachment)
                          :attachment-file (-> attachment :latestVersion :filename)
                          :latest-version-uploader user
                          :task-id (:id task)
                          :taskname (:taskname task)
                          :task-state (:state task)}]
            (println log-data)
            (logging/log-event :warn log-data)))))))

(defn restore-removed-tasks-v3 [& orgs]
  (mongo/connect!)
  (let [dbname   "lupapiste-20171127"
        stages    [{$match {:tasks.id {$in suspect-review-ids}}}
                   {$project {:tasks 1 :attachments 1 :handlers 1}}
                   {$unwind {:path "$tasks"}}
                   {$match {:tasks.data.muuTunnus.value ""
                            :tasks.id {$in suspect-review-ids}}}
                   {$sort {:_id 1
                           :tasks.id 1}}]
        _ (info "Fetching suspect reviews from backup database")
        backup-reviews (->> (mongo/with-db dbname
                              (mc/aggregate (mongo/get-db) "applications" stages))
                            (map mongo/with-id))
        _ (info "Fetching suspect reviews from production database")
        current-reviews (->> (mc/aggregate (mongo/get-db) "applications" stages)
                             (map mongo/with-id))
        find-current-review (fn [{:keys [id tasks]}]
                              (-> (filter #(and (= (:id %) id) (= (get-in % [:tasks :id]) (:id tasks))) current-reviews)
                                  first))]

    (doseq [{app-id :id backup-task :tasks :as backup-app} backup-reviews]

      (info "Checking restorable review" (:id backup-task) "for application" app-id)

      (let [{task :tasks :as current-app} (find-current-review backup-app)
            diff (cd/diff backup-task task)]

        (cond
          (empty? current-app) (error "No current application / review found for" app-id "/" (:id backup-task))

          (= (:state task) "sent") (info "Skipping because review" (:id backup-task) "is in sent state.")

          (first diff) (do (info "Overwriting review" (:id task) "data from backup.")
                           (clojure.pprint/pprint diff)
                           (update-review-data backup-app))

          :else (info "Skipping because backup review" (:id backup-task) "does not contain unique data."))))
    (info "Reviews updated.")))
