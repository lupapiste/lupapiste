(ns lupapalvelu.batchrun
  (:require [lupapalvelu.notifications :as notifications]
            [lupapalvelu.neighbors :as neighbors]
            [lupapalvelu.open-inforequest :as inforequest]
;            [clj-time.local :refer [local-now to-local-date-time]]
            [clj-time.core :refer [weeks months ago]]
            [clj-time.coerce :refer [to-long]]
            [monger.operators :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.user :as user]
            [lupapalvelu.notifications :as notifications]
            [sade.util :as util]
            [lupapalvelu.itest-util :refer [last-email] ] ;; TODO: Poista tama lopuksi!
            [sade.env :as env]))


; default pattern is daily at 2am
(def ^:dynamic ^:String *send-interval-pattern* "0 2") ; TODO: Joka yo backuppien jalkeen, selvita infralta, koska ne menevat

;  to-local-date-time
;  today-at-midnight
(defn- get-timestamp-from-now [time-key amount]
  {:pre [(#{:week :month} time-key)]}
  (let [time-fn (case time-key
                  :week weeks
                  :month months
                  )]
    (to-long (-> amount time-fn ago))))

;; TODO: Pitaisiko tama funktio olla jossain utils-namespacessa?
(defn- get-app-owner-email [application]
  (println "\n app-id: " (:id application))
  (let [owner (domain/get-auths-by-role application :owner)
;        owner-id (-> owner first :id)
;        user (user/get-user-by-id owner-id)
        ]
;    (:email user)
    (-> owner first :username)
    ))


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



(defn- send-reminder-emails []

  (let [timestamp-1-week-ago (get-timestamp-from-now :week 1)
        timestamp-1-month-ago (get-timestamp-from-now :month 1)]
    (println "\n timestamp-1-week-ago: " timestamp-1-week-ago "\n")
    (println "\n timestamp-1-month-ago: " timestamp-1-month-ago "\n")


    ;; * _Lausuntopyynto_:
    ;; Pyyntoon ei ole vastattu viikon kuluessa ja hakemuksen tila on valmisteilla tai vireilla. Lahetetaan viikoittain uudelleen.
    ;;
    ;; - kts. statement_api.clj
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
        apps)
      )


    ;; * _Neuvontapyynto_:
    ;; Neuvontapyyntoon ei ole vastattu viikon kuluessa eli neuvontapyynnon tila on avoin. Lahetetaan viikoittain uudelleen.
    ;;
    ;; *** TODO: Tarkoittaako se, että "last-used" on nil sitä, että oir:iin ei ole vastattu? ***
    ;;
    (let [oirs (mongo/select :open-inforequest-token {:created {$lt timestamp-1-week-ago}
                                                      :last-used nil})]
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
    ;; - kts. neighbors.clj:  defcommand neighbor-send-invite
    ;;   - olemassa oleva neighbor-config kelpaa
    ;;
    ;; **
    ;; Mahdoton tehdä mongossa wildcard-tyyppista kyselya, kuten ":neighbors.$.status". ->
    ;; Tarvii muuttaa kannassa neighborien rakenne arrayksi eli toisteiseksi.
    ;; **
    ;;
;    (let [apps (mongo/select :applications {:state {$in ["open" "submitted"]}
;                                            ;; TODO: KORJAA oheinen query:
;                                            ;;       wildcard-tyyppisia operaattoreita .$. tai .*. ei ole tahan tarkoitukseen olemassa mongossa.
;                                            :neighbors.$.status {:created {$lt timestamp-1-week-ago}
;                                                                 :state {$in ["email-sent"]}
;                                                                 }})]
;      (map
;        (fn [app]
;          (for [neighbor (:neighbors app)
;                :let [status (:status neighbor)]
;                :when (and
;                        (= "email-sent" (:state status))
;                        (< (:created status) timestamp-1-week-ago))]
;            (notifications/notify! :reminder-neighbor {:application app
;                                                       :data {:email (:email status)
;                                                              :token (:token status)
;                                                              ;; HUOM: Tama id neighborin paatasolla vasta kun neighbors on muutettu toisteiseksi kannassa!
;                                                              :neighborId (:id neighbor)
;                                                              }})
;            ;; TODO: Paivita kannassa kyseisen neighborin stateksi "reminder-sent".
;            (mongo/update-by-query :applications
;              {:_id (:id app)
;               :neighbors.$.id (:id neighbor) ;; HUOM: Tama id neighborin paatasolla vasta kun neighbors on muutettu toisteiseksi kannassa!
;               }
;              {$set {:neighbors.$.status.state "reminder-sent"}}  ;; TODO KORJAA oheinen query
;              )
;            ))
;        apps))


    ;; * _Hakemus_:
    ;; Hakemuksen tila on valmisteilla tai vireilla, mutta edellisesta paivityksesta on aikaa yli kuukausi. Lahetetaan kuukausittain uudelleen.
    ;;
    (let [apps (mongo/select :applications {:modified {$lt timestamp-1-month-ago}})]
      (println "\n apps: ")
      (clojure.pprint/pprint apps)
      (println "\n")

      ; TODO: Kaytetaan Owner-tyypin authin emailia - onko ok?
      (map
        (fn [app]
          (notifications/notify! :reminder-application-state {:application app
                                                              :data {:email (get-app-owner-email app)}}))
        apps)
      )
    )

  )

(defn start-reminder-email-scheduler []
  ;;
  ;; ** TODO: Testaa tama (kutsutaan serverin kaynnistyksessa) **
  ;;
;  (log/info "Starting reminder email scheduler with pattern" *send-interval-pattern*)
;  (cron/repeatedly-schedule *send-interval-pattern* send-reminder-emails)
  )

