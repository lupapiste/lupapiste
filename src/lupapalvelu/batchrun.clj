(ns lupapalvelu.batchrun
  (:require [lupapalvelu.notifications :as notifications]
            [lupapalvelu.neighbors :as neighbors]
            [lupapalvelu.open-inforequest :as inforequest]
            [clj-time.core :refer [weeks months ago]]
            [clj-time.coerce :refer [to-long]]
            [monger.operators :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.core :refer [now]]
            [lupapalvelu.user :as user]
            [lupapalvelu.notifications :as notifications]
            [sade.util :as util]
            [sade.env :as env]))


(defn- get-timestamp-from-now [time-key amount]
  {:pre [(#{:week :month} time-key)]}
  (let [time-fn (case time-key
                  :week weeks
                  :month months
                  )]
    (to-long (-> amount time-fn ago))))

(defn- get-app-owner-email [application]
  (let [owner (domain/get-auths-by-role application :owner)
;        owner-id (-> owner first :id)
;        user (user/get-user-by-id owner-id)
        ]
;    (:email user)
    (-> owner first :username)))


;; For the "open info request reminder"

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

;; For the "Neighbor reminder"

(notifications/defemail :reminder-neighbor (assoc neighbors/email-conf :subject-key "neighbor-reminder"))



(defn send-reminder-emails [& args]
  (let [timestamp-1-week-ago (get-timestamp-from-now :week 1)
        timestamp-1-month-ago (get-timestamp-from-now :month 1)]

    ;; * _Lausuntopyynto_:
    ;; Pyyntoon ei ole vastattu viikon kuluessa ja hakemuksen tila on valmisteilla tai vireilla. Lahetetaan viikoittain uudelleen.
    ;;
    (let [apps (mongo/select :applications
                 {:state {$in ["open" "submitted"]}
                  :statements {$elemMatch {:requested {$lt timestamp-1-week-ago}
                                           :given nil}}})]
      (map
        (fn [app]
          (for [statement (:statements app)
                :let [requested (:requested statement)]
                :when (and
                        (nil? (:given statement))
                        (< requested timestamp-1-week-ago))]
            (notifications/notify! :reminder-request-statement {:application app
                                                                :data {:email (get-app-owner-email app)
                                                                       :created-date (util/to-local-date requested)}})))
        apps))


    ;; * _Neuvontapyynto_:
    ;; Neuvontapyyntoon ei ole vastattu viikon kuluessa eli neuvontapyynnon tila on avoin. Lahetetaan viikoittain uudelleen.
    ;;
    (let [oirs (mongo/select :open-inforequest-token {:created {$lt timestamp-1-week-ago}
                                                      :last-used nil})]  ;; TODO: Tarkoittaako "last-used" == nil sita, etta oir:iin ei ole vastattu?
      (map
        (fn [oir]
          (let [app (mongo/by-id :applications (:application-id oir))]
            (notifications/notify! :reminder-open-inforequest {:application app
                                                               :data {:email (:email oir)
                                                                      :token-id (:id oir)
                                                                      :created-date (util/to-local-date (:created oir))}})))
        oirs))


    ;; * _Naapurin kuuleminen_:
    ;; Kuulemisen tila on "Sahkoposti lahetetty", eika allekirjoitusta ole tehty viikon kuluessa ja hakemuksen tila on valmisteilla tai vireilla. Muistutus lahetetaan kerran.
    ;;
    (let [apps (mongo/select :applications
                 {:state {$in ["open" "submitted"]}
                  :neighbors {$elemMatch {:status.state {$in ["email-sent"]}
                                          :status.created {$lt timestamp-1-week-ago}}}})]
      (map
        (fn [app]
          (map
            (fn [neighbor]
              (let [statuses (:status neighbor)]

                (when (not-any? #(= "reminder-sent" (:state %)) statuses)
                  (map
                    (fn [status]
                      (when (and
                              (= "email-sent" (:state status))
                              (< (:created status) timestamp-1-week-ago))

                        (notifications/notify! :reminder-neighbor {:application app
                                                                   :data {:email (:email status)
                                                                          :token (:token status)
                                                                          :neighborId (:id neighbor)}})
                        (mongo/update :applications
                          {:_id (:id app) :neighbors {$elemMatch {:id (:id neighbor)}}}
                          {$push {:neighbors.$.status {:state    "reminder-sent"
                                                       :created  (now)}}})))
                    statuses
                    ))

                ))
            (:neighbors app)
            ))
        apps
        ))


    ;; * _Hakemus_:
    ;; Hakemuksen tila on valmisteilla tai vireilla, mutta edellisesta paivityksesta on aikaa yli kuukausi. Lahetetaan kuukausittain uudelleen.
    ;;
    (let [apps (mongo/select :applications {:modified {$lt timestamp-1-month-ago}})]
      (map
        (fn [app] (notifications/notify! :reminder-application-state {:application app
                                                                      :data {:email (get-app-owner-email app)}})) ; TODO: Kaytetaan Owner-tyypin authin emailia - onko ok?
        apps))
    ))

