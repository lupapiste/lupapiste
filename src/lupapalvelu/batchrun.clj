(ns lupapalvelu.batchrun
  (:require [lupapalvelu.notifications :as notifications]
            [lupapalvelu.neighbors :as neighbors]
            [lupapalvelu.open-inforequest :as inforequest]
            [clj-time.core :refer [days weeks months ago]]
            [clj-time.coerce :refer [to-long]]
            [monger.operators :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.core :refer [now]]
            [lupapalvelu.user :as user]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.verdict-api :as application]
            [lupapalvelu.action :refer :all]
            [sade.util :as util]
            [sade.env :as env]
            [sade.dummy-email-server]))


(defn get-timestamp-from-now [time-key amount]
  {:pre [(#{:day :week :month} time-key)]}
  (let [time-fn (case time-key
                  :day days
                  :week weeks
                  :month months)]
    (to-long (-> amount time-fn ago))))

(defn- older-than [timestamp] {$lt timestamp})

(defn- get-app-owner [application]
  (let [owner (domain/get-auths-by-role application :owner)]
    (user/get-user-by-id (-> owner first :id))))


;; Email definition for the "open info request reminder"

(defn- oir-reminder-base-email-model [{{token :token-id created-date :created-date} :data} _]
  (let  [link-fn (fn [lang] (str (env/value :host) "/api/raw/openinforequest?token-id=" token "&lang=" (name lang)))
         info-fn (fn [lang] (env/value :oir :wanna-join-url))]
    {:link-fi (link-fn :fi)
     :link-sv (link-fn :sv)
     :created-date created-date}))

(def ^:private oir-reminder-email-conf
  {:recipients-fn  notifications/from-data
   :subject-key    "open-inforequest-reminder"
   :model-fn       oir-reminder-base-email-model
   :application-fn (fn [{id :id}] (mongo/by-id :applications id))})

(notifications/defemail :reminder-open-inforequest oir-reminder-email-conf)

;; Email definition for the "Neighbor reminder"

(notifications/defemail :reminder-neighbor (assoc neighbors/email-conf :subject-key "neighbor-reminder"))



;; "Lausuntopyynto: Pyyntoon ei ole vastattu viikon kuluessa ja hakemuksen tila on valmisteilla tai vireilla. Lahetetaan viikoittain uudelleen."
(defn statement-request-reminder []
  (let [timestamp-1-week-ago (get-timestamp-from-now :week 1)
        apps (mongo/select :applications {:state {$in ["open" "submitted"]}
                                          :statements {$elemMatch {:requested (older-than timestamp-1-week-ago)
                                                                   :given nil
                                                                   $or [{:reminder-sent {$exists false}}
                                                                        {:reminder-sent nil}
                                                                        {:reminder-sent (older-than timestamp-1-week-ago)}]}}})]
    (doseq [app apps
            statement (:statements app)
            :let [email (-> statement :person :email)
                  requested (:requested statement)]
            :when (and
                    (nil? (:given statement))
                    (< requested timestamp-1-week-ago))]
      (notifications/notify! :reminder-request-statement {:application app
                                                          :data {:email email
                                                                 :created-date (util/to-local-date requested)}})
      (update-application (application->command app)
        {:statements {$elemMatch {:id (:id statement)}}}
        {$set {:statements.$.reminder-sent (now)}}))))


;; "Neuvontapyynto: Neuvontapyyntoon ei ole vastattu viikon kuluessa eli neuvontapyynnon tila on avoin. Lahetetaan viikoittain uudelleen."
(defn open-inforequest-reminder []
  (let [timestamp-1-week-ago (get-timestamp-from-now :week 1)
        oirs (mongo/select :open-inforequest-token {:created (older-than timestamp-1-week-ago)
                                                    :last-used nil
                                                    $or [{:reminder-sent {$exists false}}
                                                         {:reminder-sent nil}
                                                         {:reminder-sent (older-than timestamp-1-week-ago)}]})]
    (doseq [oir oirs]
      (let [application (mongo/by-id :applications (:application-id oir))]
        (when (= "info" (:state application))
          (notifications/notify! :reminder-open-inforequest {:application application
                                                             :data {:email (:email oir)
                                                                    :token-id (:id oir)
                                                                    :created-date (util/to-local-date (:created oir))}})
          (mongo/update-by-id :open-inforequest-token (:id oir) {$set {:reminder-sent (now)}})
          )))))


;; "Naapurin kuuleminen: Kuulemisen tila on "Sahkoposti lahetetty", eika allekirjoitusta ole tehty viikon kuluessa ja hakemuksen tila on valmisteilla tai vireilla. Muistutus lahetetaan kerran."
(defn neighbor-reminder []
  (let [timestamp-1-week-ago (get-timestamp-from-now :week 1)
        apps (mongo/select :applications {:state {$in ["open" "submitted"]}
                                          :neighbors.status {$elemMatch {$and [{:state {$in ["email-sent"]}}
                                                                               {:created (older-than timestamp-1-week-ago)}
                                                                               ]}}})]
    (doseq [app apps
            neighbor (:neighbors app)
            :let [statuses (:status neighbor)]]

      (when (not-any? #(or
                         (= "reminder-sent" (:state %))
                         (= "response-given-ok" (:state %))
                         (= "response-given-comments" (:state %))) statuses)

        (doseq [status statuses]

          (when (and
                  (= "email-sent" (:state status))
                  (< (:created status) timestamp-1-week-ago))
            (notifications/notify! :reminder-neighbor {:application app
                                                       :data {:email (:email status)
                                                              :token (:token status)
                                                              :neighborId (:id neighbor)}})
            (update-application (application->command app)
              {:neighbors {$elemMatch {:id (:id neighbor)}}}
              {$push {:neighbors.$.status {:state    "reminder-sent"
                                           :token    (:token status)
                                           :created  (now)}}})))))))


;; "Hakemus: Hakemuksen tila on valmisteilla tai vireilla, mutta edellisesta paivityksesta on aikaa yli kuukausi. Lahetetaan kuukausittain uudelleen."
(defn application-state-reminder []
  (let [timestamp-1-month-ago (get-timestamp-from-now :month 1)
        apps (mongo/select :applications {:state {$in ["open" "submitted"]}
                                          :modified (older-than timestamp-1-month-ago)
                                          $or [{:reminder-sent {$exists false}}
                                               {:reminder-sent nil}
                                               {:reminder-sent (older-than timestamp-1-month-ago)}]})]
    (doseq [app apps]
      (notifications/notify! :reminder-application-state {:application app
                                                          :data {:email (:email (get-app-owner app))}})
      (update-application (application->command app)
        {$set {:reminder-sent (now)}}))))


(defn send-reminder-emails [& args]
  (when (env/feature? :reminders)
    (mongo/connect!)
    (statement-request-reminder)
    (open-inforequest-reminder)
    (neighbor-reminder)
    (application-state-reminder)

    (mongo/disconnect!)))

(defn fetch-verdics []
  (let [apps (mongo/select :applications {:state {$in ["sent"]}})
        ids-of-all-orgs (map :id (mongo/select :organizations {} {:_id 1}))
        eraajo-user {:id "-"
                     :enabled true
                     :lastName "Er\u00e4ajo"
                     :firstName "Lupapiste"
                     :role "authority"
                     :organizations ids-of-all-orgs}]
    (doall
      (pmap
        (fn [app]
          (let [command (application->command app)
                verdicts-info (application/do-check-for-verdict command eraajo-user (now) (:application command))]
            (when (and verdicts-info (pos? (:verdictCount verdicts-info)))
              ;; Print manually to events.log, because "normal" prints would be sent as emails to us.
              (let [app-owner (get-app-owner app)]
                (logging/with-logging-context
                  {:applicationId (:id app)
                   :userId        (:id app-owner)}
                  (logging/log-event :info {:run-by "Automatic verdicts checking" :event "Found new verdict" :app-owner app-owner})))
              (notifications/notify! :application-verdict command))))
        apps))))

(defn check-for-verdicts [& args]
  (when (env/feature? :automatic-verdicts-checking)
    (mongo/connect!)
    (fetch-verdics)
    (mongo/disconnect!)))
