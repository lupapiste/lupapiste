(ns lupapalvelu.batchrun.reminders-itest
  (:require [lupapalvelu.action :refer :all]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.batchrun.reminders :as reminders]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.test-util :refer [in-text]]
            [lupapalvelu.user :as usr]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [mount.core :as mount]
            [sade.coordinate :as coord]
            [sade.core :refer [def- now]]
            [sade.date :as date]
            [sade.dummy-email-server :as dummy-email-server]
            [sade.util :as util]))

(def db-name (str "test_reminders-itest_" (now)))

(mount/start #'mongo/connection)
(mongo/with-db db-name
  (dummy-email-server/messages :reset true)  ;; clears inbox
  (fixture/apply-fixture "minimal"))

(def- timestamp-the-beginning-of-time 0)
(def- timestamp-1-day-ago (-> (date/now) (date/minus :day) (date/timestamp)))
(def- timestamp-1-day-in-future (-> (date/now) (date/plus :day) (date/timestamp)))

(def- neighbor-non-matching
  {:id "534bf825299508fb3618489v"
   :propertyId "p"
   :owner {:type nil
           :name "n"
           :email "e"
           :businessID nil
           :nameOfDeceased nil
           :address {:street "s", :city "c", :zip "z"}}
   :status [{:state "open"
             :created 1}
            {:state "email-sent"
             :created timestamp-1-day-ago
             :email "abba@example.com"
             :token "Ww4yJgCmPyuqkWdQNiODsp1gHBsTCYHhfGaGaRDc5kMEP5Ar"
             :user {:enabled true,
                    :lastName "Panaani"
                    :firstName "Pena"
                    :city "Piippola"
                    :username "pena"
                    :street "Paapankuja 12"
                    :phone "0102030405"
                    :email "pena@example.com"
                    :personId "010203-040A"
                    :role "applicant"
                    :zip "10203"
                    :id "777777777777777777000020"}}]})

(def- neighbor-matching
  (-> neighbor-non-matching
    (assoc :id "534bf825299508fb3618456c")
    (assoc-in [:status 1 :created] timestamp-the-beginning-of-time)))

(def- neighbor-non-matching-with-response-given   ; reminders not sent if response has been given
  (-> neighbor-matching
    (assoc :id "534bf825299508fb3615223m")
    (update-in [:status] conj {:state "response-given-ok"
                               :message ""
                               :user nil
                               :created timestamp-1-day-ago
                               :vetuma {:stamp "70505470151426009182"
                                        :userid "210281-9988"
                                        :city nil
                                        :zip nil
                                        :street nil
                                        :lastName "TESTAA"
                                        :firstName "PORTAALIA"}})))


(def- neighbor-non-matching-with-mark-done ; reminders not sent if authority has marked neighbor done
  (-> neighbor-matching
    (assoc :id "553649e9aa24e8d915b579da")
    (update-in [:status] conj {:state "mark-done"
                               :user nil
                               :created (+ 100 timestamp-1-day-ago)})))

(def- statement-non-matching
  {:id "525533f7e4b0138a23d8r4b4"
    :given nil
    :person {:id "5252ecdfe4b0138a23d8e386"
             :text "Palotarkastus"
             :email "pekka.lupapiste@gmail.com"
             :name "Pekka Lupapiste"}
    :requested timestamp-1-day-ago
    :dueDate timestamp-1-day-in-future
    :status nil})

(def- statement-non-matching-no-dueDate
  (-> statement-non-matching
    (assoc :id "525533f7e4b0138a23d99994")
    (dissoc :dueDate)))

(def- statement-matching
  {:id "525533f7e4b0138a23d8e4b5"
   :given nil
   :person {:id "5252ecdfe4b0138a23d8e385"
            :text "Turvatarkastus"
            :email "sakari.viranomainen@kuopio.fi"
            :name "Esa Lupapiste"}
   :requested timestamp-the-beginning-of-time
   :dueDate timestamp-1-day-in-future
   :status nil})

(def statement-matching-no-due-date
  (-> statement-matching
      (assoc :id "6305dd0ce3dbc91861d1f822")
      (dissoc :dueDate)))

(def- statement-matching-duedate-passed
  {:id "525533f7e4b0138a23d81234"
   :given nil
   :person {:id "525533f7e4b0138a23d8e333"
            :text "Erikoislausuja"
            :email "sakari.viranomainen@kuopio.fi"
            :name "Aarni Lausuja"}
   :requested timestamp-the-beginning-of-time
   :dueDate timestamp-1-day-ago
   :status nil})

(def- reminder-application
  (merge
    domain/application-skeleton
    {:neighbors [neighbor-non-matching
                 neighbor-matching
                 neighbor-non-matching-with-response-given
                 neighbor-non-matching-with-mark-done]
     :schema-version 1
     :auth [{:lastName "Panaani"
             :firstName "Pena"
             :username "pena"
             :role "writer"
             :id "777777777777777777000020"}]
     :state "open"
     :location {:x 444444.0, :y 6666666.0}
     :statements [statement-non-matching
                  statement-non-matching-no-dueDate
                  statement-matching
                  statement-matching-duedate-passed]
     :organization "753-R"
     :title "Naapurikuja 3"
     :address "Naapurikuja 3"
     :primaryOperation {:id "534bf825299508fb3618455d"
                        :name "kerrostalo-rivitalo"
                        :created 1397487653097}
     :secondaryOperations []
     :infoRequest false
     :openInfoRequest false
     :opened 1397487653750
     :created 1397487653097
     :propertyId "75312312341234"
     :modified (-> (date/now) (date/minus :months 2) (date/timestamp))
     :permitType "R"
     :id "LP-753-2014-12345"
     :municipality "753"}))

(def- reminder-application-matching-to-inforequest
  (assoc reminder-application
    :id "LP-732-2013-00006"
    :state "info"
    :infoRequest true
    :openInfoRequest true
    :modified timestamp-1-day-ago
    :statements []
    :neighbors []))

(def- reminder-application-non-matching-to-inforequest
  (assoc reminder-application
    :id "LP-732-2013-00007"
    :state "canceled"))

(def- open-inforequest-entry-non-matching {:_id "0yqaV2vEcGDH9LYaLFOlxSTpLidKI7xWbuJ9IGGv0iPM0Rrd"
                                           :application-id (:id reminder-application-matching-to-inforequest)
                                           :created timestamp-1-day-ago
                                           :email "reba.skebamies@example.com"
                                           :last-used nil
                                           :organization-id "732-R"})

(def- open-inforequest-entry-matching
  (-> open-inforequest-entry-non-matching
    (assoc
      :_id "0yqaV2vEcGDH9LYaLFOlxSTpLidKI7xWbuJ9IGGv0iPM0Rv2"
      :created timestamp-the-beginning-of-time
      :email "juba.skebamies@example.com")))

(def- open-inforequest-entry-with-application-with-non-matching-state
  (-> open-inforequest-entry-non-matching
    (assoc
      :_id "0yqaV2vEcGDH9LYaLFOlxSTpLidKI7xWbuJ9IGGv0iPM0As5"
      :email "jyba.skebamies@example.com"
      :application-id (:id reminder-application-non-matching-to-inforequest))))

(def- ya-reminder-application
   (merge
     domain/application-skeleton
     {:id "LP-753-2015-01234",
      :applicant "Panaani Pena",
      :propertyId "75341600550007",
      :location [404369.304 6693806.957],
      :address "Latokuja 3",
      :title "Latokuja 3",
      :primaryOperation {:created 1443177714333,
                         :description nil,
                         :name "ya-katulupa-vesi-ja-viemarityot",
                         :id "560524f2dacf322aecce862e"},
      :secondaryOperations [],
      :authority {:id "777777777777777777000023",
                  :role "authority",
                  :lastName "Sibbo",
                  :firstName "Sonja",
                  :username "sonja"},
      :schema-version 1,
      :_applicantIndex ["Panaani Pena"],
      :created 1443177714333,
      :opened 1443177714333,
      :sent 1443177770260,
      :modified 1443177860463,
      :submitted 1443177768040,
      :_attachment_indicator_reset 1443177770260,
      :municipality "753",
      :state "verdictGiven",
      :permitType "YA",
      :organization "753-YA",
      :location-wgs84 (coord/convert "EPSG:3067" "WGS84" 5 [404369.304 6693806.957])
      :auth [{:lastName "Panaani"
              :firstName "Pena"
              :username "pena"
              :role "writer"
              :id "777777777777777777000020"}],
      :verdicts [{:id "5605252edacf322aecce8636",
                  :kuntalupatunnus "523",
                  :timestamp 1443177774181,
                  :paatokset[{:poytakirjat
                              [{:urlHash "302aadf76080d26d6bc0b0ae7c1db81f87404dae"}],
                              :paivamaarat {:paatosdokumentinPvm 1390953600000},
                              :lupamaaraykset {:muutMaaraykset
                                               ["Kaivu: Alkukatselmus pidett\u00e4v\u00e4."
                                                "Kaivu: Viheralueet rakennettava."],
                                               :takuuaikaPaivat "760"}}]}],
      :documents [{:id "560524f2dacf322aecce8633",
                   :created 1443177714333,
                   :meta {:_indicator_reset {:timestamp 1443177770260}},
                   :schema-info {:version 1,
                                 :order 63,
                                 :repeating false,
                                 :type "group",
                                 :name "tyoaika"},
                   :data {:tyoaika-alkaa-ms {:value (-> (date/now) (date/plus :day) (date/timestamp))
                                             :modified 1443177749608},
                          :tyoaika-paattyy-ms {:value (-> (date/now) (date/plus :days 6) (date/timestamp))
                                               :modified 1443177751909}}}]}))

;;
;; Helper functions
;;

(defn get-neighbors-with-reminder-sent-status [app]
  (reduce
    (fn [res neighbor]
      (reduce
        (fn [res2 status]
          (if (= "reminder-sent" (:state status))
            (conj res2 {:neighbor-id (:id neighbor)
                        :timestamp-reminder-sent (:created status)})
            res2))
        res
        (:status neighbor)))
    []
    (:neighbors app)))

(defn- check-sent-reminder-email [to subject bodyparts & [application-id link-role]]
  {:pre (vector? bodyparts)}

  ;; dummy-email-server/messages sometimes returned nil for the email
  ;; (because the email sending is asynchronous). Thus applying sleep here.
  (Thread/sleep 100)

  (let [emails (dummy-email-server/messages :reset true)]
    (fact "email count"
      (count emails) => 1)

    (let [email (last emails)]
      (fact "email check"
        (:to email) => (contains to)
        (:subject email) => subject
        (doseq [bodypart bodyparts]
          (get-in email [:body :plain]) => (contains bodypart))
        (when link-role
          email => (partial contains-application-link? application-id link-role)))
      email)))

(defn- no-emails []
  (Thread/sleep 100)
  (fact "No emails"
    (dummy-email-server/messages :reset true) => empty?))


(facts "reminders"

  (mongo/with-db db-name
    (mongo/insert :applications reminder-application)
    (mongo/insert :applications reminder-application-matching-to-inforequest)
    (mongo/insert :applications reminder-application-non-matching-to-inforequest)
    (mongo/insert :applications ya-reminder-application)
    (mongo/insert :open-inforequest-token open-inforequest-entry-non-matching)
    (mongo/insert :open-inforequest-token open-inforequest-entry-matching)
    (mongo/insert :open-inforequest-token open-inforequest-entry-with-application-with-non-matching-state)

    (dummy-email-server/messages :reset true))  ;; clears inbox


  (facts "statement-request-reminder"

    (fact "the \"reminder-sent\" timestamp does not pre-exist and one matching statement exists -> reminder is sent"
      (mongo/with-db db-name
        (let [now-timestamp (now)]

          (reminders/statement-request-reminder)

          (let [app                      (mongo/by-id :applications (:id reminder-application))
                reminder-sent-statements (filter :reminder-sent (:statements app))]
            (count reminder-sent-statements) => 1
            (-> reminder-sent-statements first :id) => (:id statement-matching)
            (>= (-> reminder-sent-statements first :reminder-sent) now-timestamp) => true?)

          (check-sent-reminder-email
            (-> statement-matching :person :email)
            "Lupapiste: Naapurikuja 3, Sipoo - muistutus lausuntopyynnöstä"
            ["Muistutus" "sinulta on pyydetty lausuntoa"
             (str "Lausunnon määräajaksi on asetettu " (date/finnish-date timestamp-1-day-in-future))]
            (:id reminder-application) "authority"))))

    (fact "the \"reminder-sent\" timestamp already exists but is over 1 week old -> reminder is sent"
      (mongo/with-db db-name

        (update-application
          (application->command reminder-application)
          {:statements {$elemMatch {:id (:id statement-matching)}}}
          {$set {:statements.$.reminder-sent timestamp-the-beginning-of-time}})

        (reminders/statement-request-reminder)

        (let [app                      (mongo/by-id :applications (:id reminder-application))
              reminder-sent-statements (filter :reminder-sent (:statements app))]
          (count reminder-sent-statements) => 1
          (> (-> reminder-sent-statements first :reminder-sent) timestamp-the-beginning-of-time) => true?)

        ;; clears inbox
        (check-sent-reminder-email
          (-> statement-matching :person :email)
          "Lupapiste: Naapurikuja 3, Sipoo - muistutus lausuntopyynnöstä"
          ["Muistutus" "sinulta on pyydetty lausuntoa"]
          (:id reminder-application) "authority")))

    (fact "a fresh \"reminder-sent\" timestamp already exists -> no reminder is sent"
      (mongo/with-db db-name
        (dummy-email-server/messages :reset true)  ;; clears inbox
        (reminders/statement-request-reminder)
        (dummy-email-server/messages :reset true) => empty?))

    (fact "Matching statwment without due date"
      (mongo/with-db db-name
        (dummy-email-server/messages :reset true)  ;; clears inbox
        (mongo/update-by-id :applications (:id reminder-application)
                            {$push {:statements statement-matching-no-due-date}})
        (reminders/statement-request-reminder)
        (let [email (check-sent-reminder-email
                      (-> statement-matching :person :email)
                      "Lupapiste: Naapurikuja 3, Sipoo - muistutus lausuntopyynnöstä"
                      ["Muistutus" "sinulta on pyydetty lausuntoa"]
                      (:id reminder-application) "authority")]
          (fact "No due date information in mail"
            (in-text (-> email :body :plain)
                     "Lupaa haetaan toimenpiteelle Asuinkerrostalon tai rivitalon rakentaminen"
                     ["Lausunnon määräajaksi"]))))))

  (facts "statement-reminder-due-date"

    ;; reset test conditions
    (mongo/with-db db-name
      (mongo/remove :applications (:id reminder-application))
      (mongo/insert :applications reminder-application))

    (fact "the \"duedate-reminder-sent\" timestamp does not pre-exist and one matching statement exists -> reminder is sent"
      (mongo/with-db db-name
        (let [now-timestamp (now)]

          (reminders/statement-reminder-due-date)

          (let [app                              (mongo/by-id :applications (:id reminder-application))
                duedate-reminder-sent-statements (filter :duedate-reminder-sent (:statements app))]
            (count (:statements app)) => 4
            (count duedate-reminder-sent-statements) => 1
            (-> duedate-reminder-sent-statements first :id) => (:id statement-matching-duedate-passed)
            (>= (-> duedate-reminder-sent-statements first :duedate-reminder-sent) now-timestamp) => true?)

          (check-sent-reminder-email
            (-> statement-matching-duedate-passed :person :email)
            "Lupapiste: Naapurikuja 3, Sipoo - muistutus erääntyvästä lausuntopyynnöstä"
            ["Muistutus" "sinulta on pyydetty lausuntoa" "Lausunnolle on asetettu määräaika"]
            (:id reminder-application) "authority"))))

    (fact "the \"duedate-reminder-sent\" timestamp already exists and it is over 1 week old -> reminder is NOT sent"
      (mongo/with-db db-name

        (update-application
          (application->command reminder-application)
          {:statements {$elemMatch {:id (:id statement-matching-duedate-passed)}}}
          {$set {:statements.$.duedate-reminder-sent timestamp-the-beginning-of-time}})

        (reminders/statement-reminder-due-date)

        (let [app                              (mongo/by-id :applications (:id reminder-application))
              duedate-reminder-sent-statements (filter :duedate-reminder-sent (:statements app))]
          (count duedate-reminder-sent-statements) => 1)

        (no-emails)))

    (fact "a fresh \"duedate-reminder-sent\" timestamp already exists -> no reminder is sent"
      (mongo/with-db db-name
        (dummy-email-server/messages :reset true)  ;; clears inbox
        (reminders/statement-reminder-due-date)
        (dummy-email-server/messages :reset true) => empty?))

    (fact "Reminders not sent for inappropriate states"
      (mongo/with-db db-name
        (letfn [(set-field [field value]
                  (mongo/update-by-id :applications (:id reminder-application) {$set {field value}}))
                (send-reminders []
                  (dummy-email-server/messages :reset true)  ;; clears inbox
                  (reminders/statement-reminder-due-date)
                  (dummy-email-server/messages :reset true))]
          (mongo/remove :applications (:id reminder-application))
          (mongo/insert :applications reminder-application)
          (fact "R in draft state"
            (set-field :state :draft)
            (send-reminders) => empty?)
          (fact "R in sent state"
            (set-field :state :sent)
            (send-reminders) => empty?
            (fact "YL in sent state"
              (set-field :permitType :YL)
              (send-reminders) => not-empty))))))

  (facts "open-inforequest-reminder"

    (fact "the \"reminder-sent\" timestamp does not pre-exist and one matching inforequest exists -> reminder is sent"
      (mongo/with-db db-name
        (let [now-timestamp (now)]

          (reminders/open-inforequest-reminder)

          (let [oir-matching (mongo/by-id :open-inforequest-token (:_id open-inforequest-entry-matching))
                oir-non-matching (mongo/by-id :open-inforequest-token (:_id open-inforequest-entry-non-matching))]
            (>= (:reminder-sent oir-matching) now-timestamp) => true?
            (:reminder-sent oir-non-matching) => nil?)

          (check-sent-reminder-email
           (:email open-inforequest-entry-matching)
           "Lupapiste: Naapurikuja 3 - muistutus avoimesta neuvontapyynnöstä"
           ["on avattu neuvontapyynt\u00f6" "Vastaathan"]))))

    (fact "the \"reminder-sent\" timestamp already exists but is over 1 week old -> reminder is sent"
      (mongo/with-db db-name

        (mongo/update-by-id :open-inforequest-token (:_id open-inforequest-entry-matching)
                            {$set {:reminder-sent timestamp-the-beginning-of-time}})

        (reminders/open-inforequest-reminder)

        (let [oir (mongo/by-id :open-inforequest-token (:_id open-inforequest-entry-matching))]
          (> (:reminder-sent oir) timestamp-the-beginning-of-time) => true?

          ;; clears inbox
          (check-sent-reminder-email
           (:email open-inforequest-entry-matching)
           "Lupapiste: Naapurikuja 3 - muistutus avoimesta neuvontapyynnöstä"
           ["on avattu neuvontapyynt\u00f6" "Vastaathan"]))))

    (fact "a fresh \"duedate-reminder-sent\" timestamp already exists -> no reminder is sent"
      (mongo/with-db db-name
        (dummy-email-server/messages :reset true)  ;; clears inbox
        (reminders/statement-reminder-due-date)
        (dummy-email-server/messages :reset true) => empty?)))


  (facts "neighbor-reminder"
    (fact "the \"reminder-sent\" status does not pre-exist and one matching neighbor exists -> reminder is sent"
      (mongo/with-db db-name
        (mongo/remove :applications (:id reminder-application))
        (mongo/insert :applications reminder-application)
        (let [now-timestamp (now)]

          (reminders/neighbor-reminder)

          (let [app                                 (mongo/by-id :applications (:id reminder-application))
                neighbors-with-reminder-sent-status (get-neighbors-with-reminder-sent-status app)]

            (count neighbors-with-reminder-sent-status) => 1
            (> (-> neighbors-with-reminder-sent-status first :timestamp-reminder-sent) now-timestamp) => true?

            ;; clears inbox
            (check-sent-reminder-email
              (->> neighbor-matching :status (filter #(= "email-sent" (:state %))) first :email)
              "Lupapiste: Naapurikuja 3, Sipoo - muistutus naapurin kuulemisesta"
              ["Hei n," "rajanaapurina sinulle on ilmoitettu"])))))

    (fact "a recent \"reminder-sent\" status already exists - no reminder is sent"
      (mongo/with-db db-name
        (dummy-email-server/messages :reset true)  ;; clears inbox
        (reminders/neighbor-reminder)
        (dummy-email-server/messages :reset true) => empty?))

    (fact "an very old \"reminder-sent\" status already exists -> reminder is not sent"
      (mongo/with-db db-name
        ;; in "neighbor-matching", set the :created timestamp of the "email-sent" status to "timestamp-the-beginning-of-time"
        (let [app           (mongo/by-id :applications (:id reminder-application))
              new-neighbors (map
                              (fn [neighbor]
                                (if (= (:id neighbor-matching) (:id neighbor))
                                  (update-in neighbor [:status] (fn [statuses]
                                                                  (map
                                                                    (fn [status]
                                                                      (if (= "reminder-sent" (:state status))
                                                                        (assoc status :created timestamp-the-beginning-of-time)
                                                                        status))
                                                                    statuses)))
                                  neighbor))
                              (:neighbors app))]

          (update-application
            (application->command reminder-application)
            {$set {:neighbors new-neighbors}})

          (reminders/neighbor-reminder)

          (let [app                                 (mongo/by-id :applications (:id reminder-application))
                neighbors-with-reminder-sent-status (get-neighbors-with-reminder-sent-status app)]

            ;; New reminders have not been sent because neighbor reminders are sent only once.
            (count neighbors-with-reminder-sent-status) => 1
            (-> neighbors-with-reminder-sent-status first :timestamp-reminder-sent) => timestamp-the-beginning-of-time

            (mongo/with-db db-name
              (dummy-email-server/messages :reset true) => empty?))))))



  (facts "application-state-reminder"

    (fact "the 'reminder-sent' timestamp does not pre-exist -> reminder is sent"
      (mongo/with-db db-name
        (let [now-timestamp (now)]

          (reminders/application-state-reminder)

          (let [app (mongo/by-id :applications (:id reminder-application))]
            (-> app :reminder-sent :application-state (>= now-timestamp)) => true?

            (check-sent-reminder-email
             "pena@example.com"
             "Lupapiste: Naapurikuja 3, Sipoo - onko hanke yh\u00e4 aktiivinen?"
             ["sinulla on Lupapiste-palvelussa aktiivinen" "lupahakemus"])))))

    (fact "the 'reminder-sent' timestamp already exists but is over 1 week old -> reminder is sent"
      (mongo/with-db db-name
        (update-application (application->command reminder-application)
                            {$set {:reminder-sent.application-state timestamp-the-beginning-of-time}})

        (reminders/application-state-reminder)

        (let [app (mongo/by-id :applications (:id reminder-application))]
          (-> app :reminder-sent :application-state (> timestamp-the-beginning-of-time)) => true?

          (check-sent-reminder-email
           "pena@example.com"
           "Lupapiste: Naapurikuja 3, Sipoo - onko hanke yh\u00e4 aktiivinen?"
           ["sinulla on Lupapiste-palvelussa aktiivinen" "lupahakemus"]))))

    (fact "the 'reminder-sent' timestamp already exists - no reminder is sent"
      (mongo/with-db db-name
        (dummy-email-server/messages :reset true)  ;; clears inbox
        (reminders/application-state-reminder)
        (dummy-email-server/messages :reset true) => empty?)))



  (facts "ya-work-time-is-expiring-reminder"

    (fact "timestamp does not pre-exist -> reminder is sent"
      (mongo/with-db db-name
        (let [app (mongo/by-id :applications (:id ya-reminder-application))]

          (some-> app :reminder-sent :work-time-expiring) => nil?

          (reminders/ya-work-time-is-expiring-reminder)

          (let [app             (mongo/by-id :applications (:id ya-reminder-application))
                tyoaika-paattyy (->> ya-reminder-application :documents
                                     (filter #(= "tyoaika" (-> % :schema-info :name)))
                                     first :data :tyoaika-paattyy-ms :value)]

            (-> app :reminder-sent :work-time-expiring) => number?

            (check-sent-reminder-email
              "pena@example.com"
              "Lupapiste: Latokuja 3, Sipoo - tarvitaanko jatkoaikaa?"
              ["Luvan p\u00e4\u00e4ttymisajankohdaksi on merkitty"
               (date/finnish-date tyoaika-paattyy :zero-pad)
               (:address app)])))))

    (fact "timestamp already exists -> no reminder is sent"
      (mongo/with-db db-name
        (update-application (application->command ya-reminder-application)
                            {$set {:reminder-sent.work-time-expiring timestamp-the-beginning-of-time}})

        (reminders/ya-work-time-is-expiring-reminder)

        (let [app (mongo/by-id :applications (:id ya-reminder-application))]
          (-> app :reminder-sent :work-time-expiring) => timestamp-the-beginning-of-time
          (dummy-email-server/messages) => empty?)))

    (fact "reminder is sent also to applications in state 'construction-started'"
      (mongo/with-db db-name
        (update-application (assoc (application->command ya-reminder-application) :user usr/batchrun-user-data)
                            (merge (app-state/state-transition-update :constructionStarted 0 ya-reminder-application {})
                                   {$unset {:reminder-sent.work-time-expiring 1}}))
        (reminders/ya-work-time-is-expiring-reminder)
        (check-sent-reminder-email
          "pena@example.com"
          "Lupapiste: Latokuja 3, Sipoo - tarvitaanko jatkoaikaa?"
          ["Luvan p\u00e4\u00e4ttymisajankohdaksi on merkitty"])))

    (fact "applications without 'tyoaika' document are not reacted to"
      (mongo/with-db db-name
        (update-application (application->command ya-reminder-application)
                            {$unset {:reminder-sent.work-time-expiring 1}
                             $set   {:documents (remove
                                                  #(= "tyoaika" (-> % :schema-info :name))
                                                  (:documents ya-reminder-application))}})

        (reminders/ya-work-time-is-expiring-reminder)
        (dummy-email-server/messages) => empty?))

    (fact "applications with :tyoaika-paattyy-pvm date too far in the future are not reacted to"
      (mongo/with-db db-name
        (let [date-str-8-days-in-future (-> (date/now)
                                            (.plusDays 8)
                                            (date/finnish-date :zero-pad))]

          (update-application
            (application->command ya-reminder-application)
            {:documents {$elemMatch {:schema-info (:name "tyoaika")}}}
            {$set {:documents.$.data.tyoaika-paattyy-pvm.value date-str-8-days-in-future}})  ;; 8 days -> will not trigger reminder

          (reminders/ya-work-time-is-expiring-reminder)
          (dummy-email-server/messages) => empty?)))

    (fact "applications with :tyoaika-paattyy-pvm date in the past are not reacted to"
      (mongo/with-db db-name
        (let [date-str-1-day-ago (-> (date/now)
                                     (.minusDays 1)
                                     (date/finnish-date :zero-pad))]

          (update-application
            (application->command ya-reminder-application)
            {:documents {$elemMatch {:schema-info (:name "tyoaika")}}}
            {$set {:documents.$.data.tyoaika-paattyy-pvm.value date-str-1-day-ago}})  ;; 1 day in the past -> will not trigger reminder

          (reminders/ya-work-time-is-expiring-reminder)
          (dummy-email-server/messages) => empty?)))))


(facts "No reminders for read-only applications"

  (let [db-name (str db-name "_read_only")]
    (mongo/with-db db-name
      (fixture/apply-fixture "minimal")
      (mongo/insert :applications (assoc reminder-application :readOnly true))
      (mongo/insert :applications (assoc reminder-application-matching-to-inforequest :readOnly true))
      (mongo/insert :applications (assoc ya-reminder-application :readOnly true))
      (mongo/insert :open-inforequest-token (assoc open-inforequest-entry-matching :readOnly true))
      (dummy-email-server/messages :reset true)  ;; clears inbox

      (reminders/statement-request-reminder)
      (reminders/statement-reminder-due-date)
      (reminders/open-inforequest-reminder)
      (reminders/neighbor-reminder)
      (reminders/application-state-reminder)
      (reminders/ya-work-time-is-expiring-reminder)

      (Thread/sleep 100)
      (fact "No emails sent"
        (dummy-email-server/messages :reset true) => empty?))))

(def future-deadline (-> (date/now) (date/plus :months 4) (date/timestamp)))
(def passed-deadline (-> (date/now) (date/minus :month) (date/timestamp)))
(def active-deadline (-> (date/now) (date/plus :months 2) (date/timestamp)))

(defn insert-app [& kvs]
  (->> (apply hash-map kvs)
       (reduce-kv (fn [acc k v]
                    (assoc-in acc (util/split-kw-path k) v))
                  {:permitType       "R"
                   :modified (now)
                   :municipality "753"
                   :primaryOperation {:name "pientalo"}
                   :state            "verdictGiven"
                   :auth [{:lastName "Panaani"
                           :firstName "Pena"
                           :username "pena"
                           :role "writer"
                           :id "777777777777777777000020"}]})
       (mongo/insert :applications)))

(defn make-task [laji tila state]
  (-> {:state    state}
      (assoc-in [:data :katselmuksenLaji :value] laji)
      (assoc-in [:data :katselmus :tila :value] tila)))

(defn final-review-reminder-is-sent [task]
  (facts {:midje/description (str "Reminder will be sent for " task)}
    (fact "Send reminder"
      (insert-app :state "constructionStarted" :address "Voimassa!"
                  :tasks [task]
                  :deadlines.voimassa active-deadline)
      (reminders/no-final-review-reminder)
      (dummy-email-server/messages :reset true)
      => (just [(contains {:to      "Pena Panaani <pena@example.com>"
                           :subject "Lupapiste: Voimassa!, Sipoo - muistutus luvan vanhenemisesta"
                           :body    (contains {:plain (contains "yseinen lupa on menossa vanhaksi, eikä siinä tietojemme mukaan ole tehty loppukatselmusta.")})})]))
    (fact "Sent reminder is marked"
      (:reminder-sent (mongo/select-one :applications {:address "Voimassa!"}))
      => (contains {:no-final-review pos?}))
    (fact "Reminder is not resent"
      (reminders/no-final-review-reminder)
      (dummy-email-server/messages :reset true) => empty?)))

(facts "Deadlines reminders"
  (mongo/with-db (str db-name "_deadlines")
    (fixture/apply-fixture "minimal")
    (facts "Construction not started reminder"
      (insert-app :address "No aloitettava deadline")
      (insert-app :address "Passed aloitettava deadline" :deadlines.aloitettava passed-deadline)
      (insert-app :address "Future aloitettava deadline" :deadlines.aloitettava future-deadline)
      (insert-app :address "Wrong permit type" :permitType "YA"
                  :deadlines.aloitettava active-deadline)
      (insert-app :address "Wrong state" :state "appealed" :deadlines.aloitettava active-deadline)
      (insert-app :address "Read-only" :readOnly true :deadlines.aloitettava active-deadline)
      (fact "Applications inserted"
        (mongo/count :applications {}) => 6)
      (fact "No reminders sent"
        (reminders/construction-not-started-reminder)
        (dummy-email-server/messages :reset true) => empty?)
      (fact "Send reminder"
        (insert-app :address "Aloitettava!" :deadlines.aloitettava active-deadline)
        (reminders/construction-not-started-reminder)
        (dummy-email-server/messages :reset true)
        => (just [(contains {:to      "Pena Panaani <pena@example.com>"
                             :subject "Lupapiste: Aloitettava!, Sipoo - muistutus luvan vanhenemisesta"
                             :body    (contains {:plain (contains "Tietojemme mukaan rakennustyötä ei ole vielä aloitettu.")})})]))
      (fact "Sent reminder is marked"
        (:reminder-sent (mongo/select-one :applications {:address "Aloitettava!"}))
        => (contains {:construction-not-started pos?}))
      (fact "Reminder is not resent"
        (reminders/construction-not-started-reminder)
        (dummy-email-server/messages :reset true) => empty?))
    (facts "No final review reminder"
      (let [lopullinen-pidetty-loppukatselmus (make-task "loppukatselmus" "lopullinen" "sent")
            lopullinen-luonnos-loppukatselmus (make-task "loppukatselmus" "lopullinen" "requires_user_action")
            osittainen-pidetty-loppukatselmus (make-task "loppukatselmus" "osittainen" "sent")
            osittainen-luonnos-loppukatselmus (make-task "loppukatselmus" "osittainen" "requires")
            lopullinen-pidetty-osittainen     (make-task "osittainen loppukatselmus" "lopullinen" "sent")
            lopullinen-luonnos-osittainen     (make-task "osittainen loppukatselmus" "lopullinen" "requires_user_action")
            osittainen-pidetty-osittainen     (make-task "osittainen loppukatselmus" "osittainen" "sent")
            osittainen-luonnos-osittainen     (make-task "osittainen loppukatselmus" "osittainen" "requires_user_action")
            lopullinen-pidetty-aloituskokous  (make-task "aloituskokous" "lopullinen" "sent")]
        (insert-app :state "constructionStarted" :address "No voimassa deadline"
                    :tasks [lopullinen-luonnos-loppukatselmus])
        (insert-app :state "constructionStarted" :address "Passed voimassa deadline"
                    :tasks [lopullinen-luonnos-loppukatselmus]
                    :deadlines.voimassa passed-deadline)
        (insert-app :state "constructionStarted" :address "Future voimassa deadline"
                    :tasks [lopullinen-luonnos-loppukatselmus]
                    :deadlines.voimassa future-deadline)
        (insert-app :state "inUse" :address "Wrong state"
                    :tasks [lopullinen-luonnos-loppukatselmus]
                    :deadlines.voimassa active-deadline)
        (insert-app :state "constructionStarted" :address "Read-only" :readOnly true
                    :tasks [lopullinen-luonnos-loppukatselmus]
                    :deadlines.voimassa active-deadline)
        (insert-app :state "constructionStarted" :address "Wrong permit type" :permitType "YA"
                    :tasks [lopullinen-luonnos-loppukatselmus]
                    :deadlines.voimassa active-deadline)
        (insert-app :state "constructionStarted" :address "Final review done"
                    :tasks [lopullinen-pidetty-loppukatselmus]
                    :deadlines.voimassa active-deadline)
        (insert-app :state "constructionStarted" :address "Partial final review done"
                    :tasks [lopullinen-pidetty-osittainen]
                    :deadlines.voimassa active-deadline)
        (insert-app :state "constructionStarted" :address "Partial final review done among others"
                    :tasks [lopullinen-pidetty-osittainen lopullinen-luonnos-loppukatselmus
                            lopullinen-pidetty-aloituskokous]
                    :deadlines.voimassa active-deadline)
        (fact "No reminders sent"
          (reminders/no-final-review-reminder)
          (dummy-email-server/messages :reset true) => empty?)

        (doseq [task [lopullinen-luonnos-loppukatselmus osittainen-pidetty-loppukatselmus
                      osittainen-luonnos-loppukatselmus lopullinen-luonnos-osittainen
                      osittainen-luonnos-osittainen lopullinen-pidetty-aloituskokous
                      osittainen-pidetty-osittainen {}]]
          (final-review-reminder-is-sent task))))))
