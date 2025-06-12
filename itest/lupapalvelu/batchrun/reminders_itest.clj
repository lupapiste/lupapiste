(ns lupapalvelu.batchrun.reminders-itest
  (:require [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [lupapalvelu.action :refer [application->command update-application]]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.batchrun.reminders :as reminders]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer [contains-application-link? local-fixture-factory]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [monger.operators :refer [$elemMatch $push $set $unset]]
            [sade.coordinate :as coord]
            [sade.core :refer [def- now]]
            [sade.date :as date]
            [sade.dummy-email-server :as dummy-email-server]
            [sade.strings :as ss]
            [sade.util :as util]))

(set! *warn-on-reflection* true)

(use-fixtures :once (join-fixtures [(local-fixture-factory)
                                    (fn [run-test]
                                      (dummy-email-server/messages :reset true)
                                      (run-test))]))

(def- timestamp-the-beginning-of-time 0)
(def- timestamp-1-day-ago (-> (date/now) (date/minus :day) (date/timestamp)))
(def- timestamp-1-day-in-future (-> (date/now) (date/plus :day) (date/timestamp)))

(def- neighbor-non-matching
      {:id         "534bf825299508fb3618489v"
       :propertyId "p"
       :owner      {:type           nil
                    :name           "n"
                    :email          "e"
                    :businessID     nil
                    :nameOfDeceased nil
                    :address        {:street "s", :city "c", :zip "z"}}
       :status     [{:state   "open"
                     :created 1}
                    {:state   "email-sent"
                     :created timestamp-1-day-ago
                     :email   "abba@example.com"
                     :token   "Ww4yJgCmPyuqkWdQNiODsp1gHBsTCYHhfGaGaRDc5kMEP5Ar"
                     :user    {:enabled   true,
                               :lastName  "Panaani"
                               :firstName "Pena"
                               :city      "Piippola"
                               :username  "pena"
                               :street    "Paapankuja 12"
                               :phone     "0102030405"
                               :email     "pena@example.com"
                               :personId  "010203-040A"
                               :role      "applicant"
                               :zip       "92620"
                               :id        "777777777777777777000020"}}]})

(def- neighbor-matching
      (-> neighbor-non-matching
          (assoc :id "534bf825299508fb3618456c")
          (assoc-in [:status 1 :created] timestamp-the-beginning-of-time)))

(def- neighbor-non-matching-with-response-given ; reminders not sent if response has been given
      (-> neighbor-matching
          (assoc :id "534bf825299508fb3615223m")
          (update-in [:status] conj {:state   "response-given-ok"
                                     :message ""
                                     :user    nil
                                     :created timestamp-1-day-ago
                                     :vetuma  {:stamp     "70505470151426009182"
                                               :userid    "210281-9988"
                                               :city      nil
                                               :zip       nil
                                               :street    nil
                                               :lastName  "TESTAA"
                                               :firstName "PORTAALIA"}})))


(def- neighbor-non-matching-with-mark-done ; reminders not sent if authority has marked neighbor done
      (-> neighbor-matching
          (assoc :id "553649e9aa24e8d915b579da")
          (update-in [:status] conj {:state   "mark-done"
                                     :user    nil
                                     :created (+ 100 timestamp-1-day-ago)})))

(def- statement-non-matching
      {:id        "525533f7e4b0138a23d8r4b4"
       :given     nil
       :person    {:id    "5252ecdfe4b0138a23d8e386"
                   :text  "Palotarkastus"
                   :email "pekka.lupapiste@gmail.com"
                   :name  "Pekka Lupapiste"}
       :requested timestamp-1-day-ago
       :dueDate   timestamp-1-day-in-future
       :status    nil})

(def- statement-non-matching-no-dueDate
      (-> statement-non-matching
          (assoc :id "525533f7e4b0138a23d99994")
          (dissoc :dueDate)))

(def- statement-matching
      {:id        "525533f7e4b0138a23d8e4b5"
       :given     nil
       :person    {:id    "5252ecdfe4b0138a23d8e385"
                   :text  "Turvatarkastus"
                   :email "sakari.viranomainen@kuopio.fi"
                   :name  "Esa Lupapiste"}
       :requested timestamp-the-beginning-of-time
       :dueDate   timestamp-1-day-in-future
       :status    nil})

(def statement-matching-no-due-date
  (-> statement-matching
      (assoc :id "6305dd0ce3dbc91861d1f822")
      (dissoc :dueDate)))

(def- statement-matching-duedate-passed
      {:id        "525533f7e4b0138a23d81234"
       :given     nil
       :person    {:id    "525533f7e4b0138a23d8e333"
                   :text  "Erikoislausuja"
                   :email "sakari.viranomainen@kuopio.fi"
                   :name  "Aarni Lausuja"}
       :requested timestamp-the-beginning-of-time
       :dueDate   timestamp-1-day-ago
       :status    nil})

(def- reminder-application
      (merge
        domain/application-skeleton
        {:neighbors           [neighbor-non-matching
                               neighbor-matching
                               neighbor-non-matching-with-response-given
                               neighbor-non-matching-with-mark-done]
         :schema-version      1
         :auth                [{:lastName  "Panaani"
                                :firstName "Pena"
                                :username  "pena"
                                :role      "writer"
                                :id        "777777777777777777000020"}]
         :state               "open"
         :location            {:x 444444.0, :y 6666666.0}
         :statements          [statement-non-matching
                               statement-non-matching-no-dueDate
                               statement-matching
                               statement-matching-duedate-passed]
         :organization        "753-R"
         :title               "Naapurikuja 3"
         :address             "Naapurikuja 3"
         :primaryOperation    {:id      "534bf825299508fb3618455d"
                               :name    "kerrostalo-rivitalo"
                               :created 1397487653097}
         :secondaryOperations []
         :infoRequest         false
         :openInfoRequest     false
         :opened              1397487653750
         :created             1397487653097
         :propertyId          "75312312341234"
         :modified            (-> (date/now) (date/minus :months 2) (date/timestamp))
         :permitType          "R"
         :id                  "LP-753-2014-12345"
         :municipality        "753"}))

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

(def- open-inforequest-entry-non-matching {:_id             "0yqaV2vEcGDH9LYaLFOlxSTpLidKI7xWbuJ9IGGv0iPM0Rrd"
                                           :application-id  (:id reminder-application-matching-to-inforequest)
                                           :created         timestamp-1-day-ago
                                           :email           "reba.skebamies@example.com"
                                           :last-used       nil
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
        {:id                          "LP-753-2015-01234",
         :applicant                   "Panaani Pena",
         :propertyId                  "75341600550007",
         :location                    [404369.304 6693806.957],
         :address                     "Latokuja 3",
         :title                       "Latokuja 3",
         :primaryOperation            {:created     1443177714333,
                                       :description nil,
                                       :name        "ya-katulupa-vesi-ja-viemarityot",
                                       :id          "560524f2dacf322aecce862e"},
         :secondaryOperations         [],
         :authority                   {:id        "777777777777777777000023",
                                       :role      "authority",
                                       :lastName  "Sibbo",
                                       :firstName "Sonja",
                                       :username  "sonja"},
         :schema-version              1,
         :_applicantIndex             ["Panaani Pena"],
         :created                     1443177714333,
         :opened                      1443177714333,
         :sent                        1443177770260,
         :modified                    1443177860463,
         :submitted                   1443177768040,
         :_attachment_indicator_reset 1443177770260,
         :municipality                "753",
         :state                       "verdictGiven",
         :permitType                  "YA",
         :organization                "753-YA",
         :location-wgs84              (coord/convert "EPSG:3067" "WGS84" 5 [404369.304 6693806.957])
         :auth                        [{:lastName  "Panaani"
                                        :firstName "Pena"
                                        :username  "pena"
                                        :role      "writer"
                                        :id        "777777777777777777000020"}],
         :verdicts                    [{:id              "5605252edacf322aecce8636",
                                        :kuntalupatunnus "523",
                                        :timestamp       1443177774181,
                                        :paatokset       [{:poytakirjat
                                                           [{:urlHash "302aadf76080d26d6bc0b0ae7c1db81f87404dae"}],
                                                           :paivamaarat    {:paatosdokumentinPvm 1390953600000},
                                                           :lupamaaraykset {:muutMaaraykset
                                                                            ["Kaivu: Alkukatselmus pidett\u00e4v\u00e4."
                                                                             "Kaivu: Viheralueet rakennettava."],
                                                                            :takuuaikaPaivat "760"}}]}],
         :documents                   [{:id          "560524f2dacf322aecce8633",
                                        :created     1443177714333,
                                        :meta        {:_indicator_reset {:timestamp 1443177770260}},
                                        :schema-info {:version   1,
                                                      :order     63,
                                                      :repeating false,
                                                      :type      "group",
                                                      :name      "tyoaika"},
                                        :data        {:tyoaika-alkaa-ms   {:value    (-> (date/now) (date/plus :day) (date/timestamp))
                                                                           :modified 1443177749608},
                                                      :tyoaika-paattyy-ms {:value    (-> (date/now) (date/plus :days 6) (date/timestamp))
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
            (conj res2 {:neighbor-id             (:id neighbor)
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
    (testing "email count"
      (is (= 1 (count emails))))

    (let [email (last emails)]
      (testing "email check"
        (is (ss/includes? (:to email) to))
        (is (= subject (:subject email)))
        (doseq [bodypart bodyparts]
          (is (ss/includes? (get-in email [:body :plain]) bodypart)))
        (when link-role
          (is (contains-application-link? application-id link-role email))))
      email)))

(defn- no-emails []
  (Thread/sleep 100)
  (testing "No emails"
    (is (empty? (dummy-email-server/messages :reset true)))))


(deftest reminders-test
  ;; Clear any existing test data first to make test idempotent
  (mongo/remove :applications (:id reminder-application))
  (mongo/remove :applications (:id reminder-application-matching-to-inforequest))
  (mongo/remove :applications (:id reminder-application-non-matching-to-inforequest))
  (mongo/remove :applications (:id ya-reminder-application))
  (mongo/remove :open-inforequest-token (:_id open-inforequest-entry-non-matching))
  (mongo/remove :open-inforequest-token (:_id open-inforequest-entry-matching))
  (mongo/remove :open-inforequest-token (:_id open-inforequest-entry-with-application-with-non-matching-state))

  (mongo/insert :applications reminder-application)
  (mongo/insert :applications reminder-application-matching-to-inforequest)
  (mongo/insert :applications reminder-application-non-matching-to-inforequest)
  (mongo/insert :applications ya-reminder-application)
  (mongo/insert :open-inforequest-token open-inforequest-entry-non-matching)
  (mongo/insert :open-inforequest-token open-inforequest-entry-matching)
  (mongo/insert :open-inforequest-token open-inforequest-entry-with-application-with-non-matching-state)

  (dummy-email-server/messages :reset true) ;; clears inbox


  (testing "statement-request-reminder"

    (testing "the \"reminder-sent\" timestamp does not pre-exist and one matching statement exists -> reminder is sent"
      (let [now-timestamp (now)]

        (reminders/statement-request-reminder (test-system/get-common-components))

        (let [app                      (mongo/by-id :applications (:id reminder-application))
              reminder-sent-statements (filter :reminder-sent (:statements app))]
          (is (= 1 (count reminder-sent-statements)))
          (is (= (:id statement-matching) (-> reminder-sent-statements first :id)))
          (is (true? (>= (-> reminder-sent-statements first :reminder-sent) now-timestamp))))

        (check-sent-reminder-email
          (-> statement-matching :person :email)
          "Lupapiste: Naapurikuja 3, Sipoo - muistutus lausuntopyynnöstä"
          ["Muistutus" "sinulta on pyydetty lausuntoa"
           (str "Lausunnon määräajaksi on asetettu " (date/finnish-date timestamp-1-day-in-future))]
          (:id reminder-application) "authority")))

    (testing "the \"reminder-sent\" timestamp already exists but is over 1 week old -> reminder is sent"
      (update-application
        (application->command reminder-application)
        {:statements {$elemMatch {:id (:id statement-matching)}}}
        {$set {:statements.$.reminder-sent timestamp-the-beginning-of-time}})

      (reminders/statement-request-reminder (test-system/get-common-components))

      (let [app                      (mongo/by-id :applications (:id reminder-application))
            reminder-sent-statements (filter :reminder-sent (:statements app))]
        (is (= 1 (count reminder-sent-statements)))
        (is (true? (> (-> reminder-sent-statements first :reminder-sent) timestamp-the-beginning-of-time))))

      ;; clears inbox
      (check-sent-reminder-email
        (-> statement-matching :person :email)
        "Lupapiste: Naapurikuja 3, Sipoo - muistutus lausuntopyynnöstä"
        ["Muistutus" "sinulta on pyydetty lausuntoa"]
        (:id reminder-application) "authority"))

    (testing "a fresh \"reminder-sent\" timestamp already exists -> no reminder is sent"
      (dummy-email-server/messages :reset true) ;; clears inbox
      (reminders/statement-request-reminder (test-system/get-common-components))
      (is (empty? (dummy-email-server/messages :reset true))))

    (testing "Matching statement without due date"
      (dummy-email-server/messages :reset true) ;; clears inbox
      (mongo/update-by-id :applications (:id reminder-application)
                          {$push {:statements statement-matching-no-due-date}})
      (reminders/statement-request-reminder (test-system/get-common-components))
      (let [email (check-sent-reminder-email
                    (-> statement-matching :person :email)
                    "Lupapiste: Naapurikuja 3, Sipoo - muistutus lausuntopyynnöstä"
                    ["Muistutus" "sinulta on pyydetty lausuntoa"]
                    (:id reminder-application) "authority")]
        (testing "No due date information in mail"
          (let [plain (-> email :body :plain)]
            (is (ss/includes? plain "Lupaa haetaan toimenpiteelle Asuinkerrostalon tai rivitalon rakentaminen"))
            (is (not (ss/includes? plain "Lausunnon määräajaksi")))))))

    (testing "Statement without requested timestamp: no reminder"
      (dummy-email-server/messages :reset true) ;; clears inbox
      (mongo/update-by-id :applications (:id reminder-application)
                          {$push {:statements (dissoc statement-matching-no-due-date
                                                      :requested)}})
      (reminders/statement-request-reminder (test-system/get-common-components))
      (is (empty? (dummy-email-server/messages :reset true)))))

  (testing "statement-reminder-due-date"

    ;; reset test conditions
    (mongo/remove :applications (:id reminder-application))
    (mongo/insert :applications reminder-application)

    (testing "the \"duedate-reminder-sent\" timestamp does not pre-exist and one matching statement exists -> reminder is sent"
      (let [now-timestamp (now)]

        (reminders/statement-reminder-due-date (test-system/get-common-components))

        (let [app                              (mongo/by-id :applications (:id reminder-application))
              duedate-reminder-sent-statements (filter :duedate-reminder-sent (:statements app))]
          (is (= 4 (count (:statements app))))
          (is (= 1 (count duedate-reminder-sent-statements)))
          (is (= (:id statement-matching-duedate-passed) (-> duedate-reminder-sent-statements first :id)))
          (is (true? (>= (-> duedate-reminder-sent-statements first :duedate-reminder-sent) now-timestamp))))

        (check-sent-reminder-email
          (-> statement-matching-duedate-passed :person :email)
          "Lupapiste: Naapurikuja 3, Sipoo - muistutus erääntyvästä lausuntopyynnöstä"
          ["Muistutus" "sinulta on pyydetty lausuntoa" "Lausunnolle on asetettu määräaika"]
          (:id reminder-application) "authority")))

    (testing "the \"duedate-reminder-sent\" timestamp already exists and it is over 1 week old -> reminder is NOT sent"
      (update-application
        (application->command reminder-application)
        {:statements {$elemMatch {:id (:id statement-matching-duedate-passed)}}}
        {$set {:statements.$.duedate-reminder-sent timestamp-the-beginning-of-time}})

      (reminders/statement-reminder-due-date (test-system/get-common-components))

      (let [app                              (mongo/by-id :applications (:id reminder-application))
            duedate-reminder-sent-statements (filter :duedate-reminder-sent (:statements app))]
        (is (= 1 (count duedate-reminder-sent-statements))))

      (no-emails))

    (testing "a fresh \"duedate-reminder-sent\" timestamp already exists -> no reminder is sent"
      (dummy-email-server/messages :reset true) ;; clears inbox
      (reminders/statement-reminder-due-date (test-system/get-common-components))
      (is (empty? (dummy-email-server/messages :reset true))))

    (testing "Reminders not sent for inappropriate states"
      (letfn [(set-field [field value]
                (mongo/update-by-id :applications (:id reminder-application) {$set {field value}}))
              (send-reminders []
                (dummy-email-server/messages :reset true) ;; clears inbox
                (reminders/statement-reminder-due-date (test-system/get-common-components))
                (dummy-email-server/messages :reset true))]
        (mongo/remove :applications (:id reminder-application))
        (mongo/insert :applications reminder-application)
        (testing "R in draft state"
          (set-field :state :draft)
          (is (empty? (send-reminders))))
        (testing "R in sent state"
          (set-field :state :sent)
          (is (empty? (send-reminders)))
          (testing "YL in sent state"
            (set-field :permitType :YL)
            (is (not-empty (send-reminders))))))))

  (testing "open-inforequest-reminder"

    (testing "the \"reminder-sent\" timestamp does not pre-exist and one matching inforequest exists -> reminder is sent"
      (let [now-timestamp (now)]

        (reminders/open-inforequest-reminder (test-system/get-common-components))

        (let [oir-matching     (mongo/by-id :open-inforequest-token (:_id open-inforequest-entry-matching))
              oir-non-matching (mongo/by-id :open-inforequest-token (:_id open-inforequest-entry-non-matching))]
          (is (true? (>= (:reminder-sent oir-matching) now-timestamp)))
          (is (nil? (:reminder-sent oir-non-matching))))

        (check-sent-reminder-email
          (:email open-inforequest-entry-matching)
          "Lupapiste: Naapurikuja 3 - muistutus avoimesta neuvontapyynnöstä"
          ["on avattu neuvontapyynt\u00f6" "Vastaathan"])))

    (testing "the \"reminder-sent\" timestamp already exists but is over 1 week old -> reminder is sent"
      (mongo/update-by-id :open-inforequest-token (:_id open-inforequest-entry-matching)
                          {$set {:reminder-sent timestamp-the-beginning-of-time}})

      (reminders/open-inforequest-reminder (test-system/get-common-components))

      (let [oir (mongo/by-id :open-inforequest-token (:_id open-inforequest-entry-matching))]
        (is (true? (> (:reminder-sent oir) timestamp-the-beginning-of-time)))

        ;; clears inbox
        (check-sent-reminder-email
          (:email open-inforequest-entry-matching)
          "Lupapiste: Naapurikuja 3 - muistutus avoimesta neuvontapyynnöstä"
          ["on avattu neuvontapyynt\u00f6" "Vastaathan"])))

    (testing "a fresh \"duedate-reminder-sent\" timestamp already exists -> no reminder is sent"
      (dummy-email-server/messages :reset true) ;; clears inbox
      (reminders/statement-reminder-due-date (test-system/get-common-components))
      (is (empty? (dummy-email-server/messages :reset true)))))


  (testing "neighbor-reminder"
    (testing "the \"reminder-sent\" status does not pre-exist and one matching neighbor exists -> reminder is sent"
      (mongo/remove :applications (:id reminder-application))
      (mongo/insert :applications reminder-application)
      (let [now-timestamp (now)]

        (reminders/neighbor-reminder (test-system/get-common-components))

        (let [app                                 (mongo/by-id :applications (:id reminder-application))
              neighbors-with-reminder-sent-status (get-neighbors-with-reminder-sent-status app)]

          (is (= 1 (count neighbors-with-reminder-sent-status)))
          (is (true? (> (-> neighbors-with-reminder-sent-status first :timestamp-reminder-sent) now-timestamp)))

          ;; clears inbox
          (check-sent-reminder-email
            (->> neighbor-matching :status (filter #(= "email-sent" (:state %))) first :email)
            "Lupapiste: Naapurikuja 3, Sipoo - muistutus naapurin kuulemisesta"
            ["Hei n," "rajanaapurina sinulle on ilmoitettu"]))))

    (testing "a recent \"reminder-sent\" status already exists - no reminder is sent"
      (dummy-email-server/messages :reset true) ;; clears inbox
      (reminders/neighbor-reminder (test-system/get-common-components))
      (is (empty? (dummy-email-server/messages :reset true))))

    (testing "a very old \"reminder-sent\" status already exists -> reminder is not sent"
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

        (reminders/neighbor-reminder (test-system/get-common-components))

        (let [app                                 (mongo/by-id :applications (:id reminder-application))
              neighbors-with-reminder-sent-status (get-neighbors-with-reminder-sent-status app)]

          ;; New reminders have not been sent because neighbor reminders are sent only once.
          (is (= 1 (count neighbors-with-reminder-sent-status)))
          (is (= timestamp-the-beginning-of-time (-> neighbors-with-reminder-sent-status first :timestamp-reminder-sent)))

          (is (empty? (dummy-email-server/messages :reset true)))))))

  (testing "application-state-reminder"

    (testing "the 'reminder-sent' timestamp does not pre-exist -> reminder is sent"
      (let [now-timestamp (now)]

        (reminders/application-state-reminder (test-system/get-common-components))

        (let [app (mongo/by-id :applications (:id reminder-application))]
          (is (true? (>= (-> app :reminder-sent :application-state) now-timestamp)))

          (check-sent-reminder-email
            "pena@example.com"
            "Lupapiste: Naapurikuja 3, Sipoo - onko hanke yh\u00e4 aktiivinen?"
            ["sinulla on Lupapiste-palvelussa aktiivinen" "lupahakemus"]))))

    (testing "the 'reminder-sent' timestamp already exists but is over 1 week old -> reminder is sent"
      (update-application (application->command reminder-application)
                          {$set {:reminder-sent.application-state timestamp-the-beginning-of-time}})

      (reminders/application-state-reminder (test-system/get-common-components))

      (let [app (mongo/by-id :applications (:id reminder-application))]
        (is (true? (> (-> app :reminder-sent :application-state) timestamp-the-beginning-of-time)))

        (check-sent-reminder-email
          "pena@example.com"
          "Lupapiste: Naapurikuja 3, Sipoo - onko hanke yh\u00e4 aktiivinen?"
          ["sinulla on Lupapiste-palvelussa aktiivinen" "lupahakemus"])))

    (testing "the 'reminder-sent' timestamp already exists - no reminder is sent"
      (dummy-email-server/messages :reset true) ;; clears inbox
      (reminders/application-state-reminder (test-system/get-common-components))
      (is (empty? (dummy-email-server/messages :reset true)))))



  (testing "ya-work-time-is-expiring-reminder"

    (testing "timestamp does not pre-exist -> reminder is sent"
      (let [app (mongo/by-id :applications (:id ya-reminder-application))]

        (is (nil? (some-> app :reminder-sent :work-time-expiring)))

        (reminders/ya-work-time-is-expiring-reminder (test-system/get-common-components))

        (let [app             (mongo/by-id :applications (:id ya-reminder-application))
              tyoaika-paattyy (->> ya-reminder-application :documents
                                   (filter #(= "tyoaika" (-> % :schema-info :name)))
                                   first :data :tyoaika-paattyy-ms :value)]

          (is (number? (-> app :reminder-sent :work-time-expiring)))

          (check-sent-reminder-email
            "pena@example.com"
            "Lupapiste: Latokuja 3, Sipoo - tarvitaanko jatkoaikaa?"
            ["Luvan p\u00e4\u00e4ttymisajankohdaksi on merkitty"
             (date/finnish-date tyoaika-paattyy :zero-pad)
             (:address app)]))))

    (testing "timestamp already exists -> no reminder is sent"
      (update-application (application->command ya-reminder-application)
                          {$set {:reminder-sent.work-time-expiring timestamp-the-beginning-of-time}})

      (reminders/ya-work-time-is-expiring-reminder (test-system/get-common-components))

      (let [app (mongo/by-id :applications (:id ya-reminder-application))]
        (is (= timestamp-the-beginning-of-time (-> app :reminder-sent :work-time-expiring)))
        (is (empty? (dummy-email-server/messages)))))

    (testing "reminder is sent also to applications in state 'construction-started'"
      (update-application (assoc (application->command ya-reminder-application) :user usr/batchrun-user-data)
                          (merge (app-state/state-transition-update :constructionStarted 0 ya-reminder-application {})
                                 {$unset {:reminder-sent.work-time-expiring 1}}))
      (reminders/ya-work-time-is-expiring-reminder (test-system/get-common-components))
      (check-sent-reminder-email
        "pena@example.com"
        "Lupapiste: Latokuja 3, Sipoo - tarvitaanko jatkoaikaa?"
        ["Luvan p\u00e4\u00e4ttymisajankohdaksi on merkitty"]))

    (testing "applications without 'tyoaika' document are not reacted to"
      (update-application (application->command ya-reminder-application)
                          {$unset {:reminder-sent.work-time-expiring 1}
                           $set   {:documents (remove
                                                #(= "tyoaika" (-> % :schema-info :name))
                                                (:documents ya-reminder-application))}})

      (reminders/ya-work-time-is-expiring-reminder (test-system/get-common-components))
      (is (empty? (dummy-email-server/messages))))

    (testing "applications with :tyoaika-paattyy-pvm date too far in the future are not reacted to"
      (let [date-str-8-days-in-future (-> (date/now)
                                          (.plusDays 8)
                                          (date/finnish-date :zero-pad))]

        (update-application
          (application->command ya-reminder-application)
          {:documents {$elemMatch {:schema-info (:name "tyoaika")}}}
          {$set {:documents.$.data.tyoaika-paattyy-pvm.value date-str-8-days-in-future}}) ;; 8 days -> will not trigger reminder

        (reminders/ya-work-time-is-expiring-reminder (test-system/get-common-components))
        (is (empty? (dummy-email-server/messages)))))

    (testing "applications with :tyoaika-paattyy-pvm date in the past are not reacted to"
      (let [date-str-1-day-ago (-> (date/now)
                                   (.minusDays 1)
                                   (date/finnish-date :zero-pad))]

        (update-application
          (application->command ya-reminder-application)
          {:documents {$elemMatch {:schema-info (:name "tyoaika")}}}
          {$set {:documents.$.data.tyoaika-paattyy-pvm.value date-str-1-day-ago}}) ;; 1 day in the past -> will not trigger reminder

        (reminders/ya-work-time-is-expiring-reminder (test-system/get-common-components))
        (is (empty? (dummy-email-server/messages)))))))


(deftest no-reminders-for-read-only-applications-test
  (fixture/apply-fixture "minimal" (test-system/get-common-components))
  (mongo/insert :applications (assoc reminder-application :readOnly true))
  (mongo/insert :applications (assoc reminder-application-matching-to-inforequest :readOnly true))
  (mongo/insert :applications (assoc ya-reminder-application :readOnly true))
  (mongo/insert :open-inforequest-token (assoc open-inforequest-entry-matching :readOnly true))
  (dummy-email-server/messages :reset true) ;; clears inbox

  (reminders/statement-request-reminder (test-system/get-common-components))
  (reminders/statement-reminder-due-date (test-system/get-common-components))
  (reminders/open-inforequest-reminder (test-system/get-common-components))
  (reminders/neighbor-reminder (test-system/get-common-components))
  (reminders/application-state-reminder (test-system/get-common-components))
  (reminders/ya-work-time-is-expiring-reminder (test-system/get-common-components))

  (Thread/sleep 100)
  (testing "No emails sent"
    (is (empty? (dummy-email-server/messages :reset true)))))

(def future-deadline (-> (date/now) (date/plus :months 4) (date/timestamp)))
(def passed-deadline (-> (date/now) (date/minus :month) (date/timestamp)))
(def active-deadline (-> (date/now) (date/plus :months 2) (date/timestamp)))

(defn insert-app [& kvs]
  (->> (apply hash-map kvs)
       (reduce-kv (fn [acc k v]
                    (assoc-in acc (util/split-kw-path k) v))
                  {:permitType       "R"
                   :modified         (now)
                   :submitted        (now)
                   :municipality     "753"
                   :primaryOperation {:name "pientalo"}
                   :state            "verdictGiven"
                   :auth             [{:lastName  "Panaani"
                                       :firstName "Pena"
                                       :username  "pena"
                                       :role      "writer"
                                       :id        "777777777777777777000020"}]})
       (mongo/insert :applications)))

(defn make-task [laji tila state]
  (-> {:state state}
      (assoc-in [:data :katselmuksenLaji :value] laji)
      (assoc-in [:data :katselmus :tila :value] tila)))

(defn final-review-reminder-is-sent [task]
  (testing (str "Reminder will be sent for " task)
    (testing "Send reminder"
      (insert-app :state "constructionStarted" :address "Voimassa!"
                  :tasks [task]
                  :deadlines.voimassa active-deadline)
      (reminders/no-final-review-reminder (test-system/get-common-components))
      (let [emails (dummy-email-server/messages :reset true)]
        (is (= 1 (count emails)))
        (let [email (first emails)]
          (is (ss/includes? (:to email) "Pena Panaani <pena@example.com>"))
          (is (= "Lupapiste: Voimassa!, Sipoo - muistutus luvan vanhenemisesta" (:subject email)))
          (is (ss/includes? (get-in email [:body :plain]) "yseinen lupa on menossa vanhaksi, eikä siinä tietojemme mukaan ole tehty loppukatselmusta.")))))
    (testing "Sent reminder is marked"
      (is (pos? (:no-final-review (:reminder-sent (mongo/select-one :applications {:address "Voimassa!"}))))))
    (testing "Reminder is not resent"
      (reminders/no-final-review-reminder (test-system/get-common-components))
      (is (empty? (dummy-email-server/messages :reset true))))))

(deftest reminder-not-sent-for-applications-with-continuation-application-test
  (fixture/apply-fixture "minimal" (test-system/get-common-components))
  (testing "No final review reminder when continuation application exists"
    (let [original-id            "LP-753-2024-00001"
          continuation-id        "LP-753-2024-00002"
          original-submitted     (-> (date/now) (date/minus :months 6) (date/timestamp))
          continuation-submitted (-> (date/now) (date/minus :months 3) (date/timestamp))]
      (insert-app :id original-id
                  :submitted original-submitted
                  :state "constructionStarted" :address "Voimassa!"
                  :tasks [(make-task "loppukatselmus" "lopullinen" "requires_user_action")]
                  :deadlines.voimassa active-deadline)
      (insert-app :id continuation-id
                  :submitted continuation-submitted
                  :state "finished"
                  :primaryOperation {:name "raktyo-aloit-loppuunsaat"})
      (mongo/insert :app-links {:id                       (str original-id "|" continuation-id)
                                :link                     [continuation-id original-id]
                                (keyword continuation-id) {:type    "application"
                                                           :apptype "raktyo-aloit-loppuunsaat"}
                                (keyword original-id)     {:type "linkpermit"}})
      (reminders/no-final-review-reminder (test-system/get-common-components))
      (is (empty? (dummy-email-server/messages :reset true)))))

  (testing "No construction-not-started reminder when continuation app exists"
    (let [original-id            "LP-753-2024-00003"
          continuation-id        "LP-753-2024-00004"
          original-submitted     (-> (date/now) (date/minus :months 6) (date/timestamp))
          continuation-submitted (-> (date/now) (date/minus :months 3) (date/timestamp))]
      (insert-app :id original-id
                  :submitted original-submitted
                  :state "verdictGiven" :address "Aloitettava with jatkoaika!"
                  :deadlines.aloitettava active-deadline)
      (insert-app :id continuation-id
                  :submitted continuation-submitted
                  :state "verdictGiven"
                  :primaryOperation {:name "raktyo-aloit-loppuunsaat"})
      (mongo/insert :app-links {:id                       (str original-id "|" continuation-id)
                                :link                     [continuation-id original-id]
                                (keyword continuation-id) {:type    "application"
                                                           :apptype "raktyo-aloit-loppuunsaat"}
                                (keyword original-id)     {:type "linkpermit"}})
      (reminders/construction-not-started-reminder (test-system/get-common-components))
      (is (empty? (dummy-email-server/messages :reset true))))))

(deftest reminder-sent-for-continuation-app-with-deadline-test
  (fixture/apply-fixture "minimal" (test-system/get-common-components))
  (testing "Construction-not-started reminder IS sent for continuation app with active deadline"
    (let [original-id            "LP-753-2024-00005"
          continuation-id        "LP-753-2024-00006"
          original-submitted     (-> (date/now) (date/minus :months 6) (date/timestamp))
          continuation-submitted (-> (date/now) (date/minus :months 3) (date/timestamp))]
      (insert-app :id original-id
                  :submitted original-submitted
                  :state "verdictGiven" :address "Original app")
      (insert-app :id continuation-id
                  :submitted continuation-submitted
                  :state "verdictGiven"
                  :address "Continuation with deadline"
                  :primaryOperation {:name "raktyo-aloit-loppuunsaat"}
                  :deadlines.aloitettava active-deadline)
      (mongo/insert :app-links {:id                       (str original-id "|" continuation-id)
                                :link                     [continuation-id original-id]
                                (keyword continuation-id) {:type    "application"
                                                           :apptype "raktyo-aloit-loppuunsaat"}
                                (keyword original-id)     {:type "linkpermit"}})
      (reminders/construction-not-started-reminder (test-system/get-common-components))
      (let [emails (dummy-email-server/messages :reset true)]
        (is (= 1 (count emails)))
        (is (ss/includes? (:subject (first emails)) "Continuation with deadline"))))))

(deftest ya-work-time-reminder-not-sent-when-jatkoaika-exists-test
  (fixture/apply-fixture "minimal" (test-system/get-common-components))
  (dummy-email-server/messages :reset true)
  (testing "No reminder when ya-jatkoaika application exists with later submitted timestamp"
    (let [original-app-id     "LP-753-2015-01234"
          jatkoaika-app-id    "LP-753-2015-01235"
          original-submitted  (-> (date/now) (date/minus :months 6) (date/timestamp))
          jatkoaika-submitted (-> (date/now) (date/minus :months 3) (date/timestamp))]
      ;; Insert the original YA application that would trigger the reminder
      (mongo/insert :applications
                    (merge
                      domain/application-skeleton
                      {:id               original-app-id
                       :address          "Jatkoaika test"
                       :title            "Jatkoaika test"
                       :permitType       "YA"
                       :state            "verdictGiven"
                       :submitted        original-submitted
                       :municipality     "753"
                       :organization     "753-YA"
                       :primaryOperation {:name "ya-katulupa-vesi-ja-viemarityot"}
                       :auth             [{:lastName  "Panaani"
                                           :firstName "Pena"
                                           :username  "pena"
                                           :role      "writer"
                                           :id        "777777777777777777000020"}]
                       :documents        [{:schema-info {:name "tyoaika"}
                                           :data        {:tyoaika-paattyy-ms {:value (-> (date/now)
                                                                                         (date/plus :days 6)
                                                                                         (date/timestamp))}}}]}))
      ;; Insert the jatkoaika application with later submitted timestamp
      (mongo/insert :applications
                    (merge
                      domain/application-skeleton
                      {:id               jatkoaika-app-id
                       :address          "Jatkoaika test"
                       :title            "Jatkoaika test"
                       :permitType       "YA"
                       :state            "verdictGiven"
                       :submitted        jatkoaika-submitted
                       :municipality     "753"
                       :organization     "753-YA"
                       :primaryOperation {:name "ya-jatkoaika"}
                       :auth             [{:lastName  "Panaani"
                                           :firstName "Pena"
                                           :username  "pena"
                                           :role      "writer"
                                           :id        "777777777777777777000020"}]}))
      ;; Create app-link between the two applications
      (mongo/insert :app-links {:id                        (str original-app-id "|" jatkoaika-app-id)
                                :link                      [original-app-id jatkoaika-app-id]
                                (keyword jatkoaika-app-id) {:type    "application"
                                                            :apptype "ya-jatkoaika"}
                                (keyword original-app-id)  {:type "linkpermit"}})
      (reminders/ya-work-time-is-expiring-reminder (test-system/get-common-components))
      (is (empty? (dummy-email-server/messages :reset true)))))

  (testing "Reminder IS sent when no linked ya-jatkoaika exists"
    (let [app-id "LP-753-2015-99999"]
      (mongo/insert :applications
                    (merge
                      domain/application-skeleton
                      {:id               app-id
                       :address          "No jatkoaika test"
                       :title            "No jatkoaika test"
                       :permitType       "YA"
                       :state            "verdictGiven"
                       :submitted        (now)
                       :municipality     "753"
                       :organization     "753-YA"
                       :primaryOperation {:name "ya-katulupa-vesi-ja-viemarityot"}
                       :auth             [{:lastName  "Panaani"
                                           :firstName "Pena"
                                           :username  "pena"
                                           :role      "writer"
                                           :id        "777777777777777777000020"}]
                       :documents        [{:schema-info {:name "tyoaika"}
                                           :data        {:tyoaika-paattyy-ms {:value (-> (date/now)
                                                                                         (date/plus :days 6)
                                                                                         (date/timestamp))}}}]}))
      (reminders/ya-work-time-is-expiring-reminder (test-system/get-common-components))
      (let [emails (dummy-email-server/messages :reset true)]
        (is (= 1 (count emails)))
        (is (ss/includes? (:subject (first emails)) "No jatkoaika test")))))

  (testing "Reminder IS sent to only the ya-jatkoaika application with expiring work time"
    (let [original-app-id     "LP-753-2015-88880"
          jatkoaika-app-id    "LP-753-2015-88881"
          original-submitted  (-> (date/now) (date/minus :months 6) (date/timestamp))
          jatkoaika-submitted (-> (date/now) (date/minus :months 3) (date/timestamp))]
      ;; Insert an application which might require a reminder without jatkoaika, but is supressed.
      (mongo/insert :applications
                    (merge
                      domain/application-skeleton
                      {:id               original-app-id
                       :address          "Original for jatkoaika"
                       :title            "Original for jatkoaika"
                       :permitType       "YA"
                       :state            "verdictGiven"
                       :submitted        original-submitted
                       :municipality     "753"
                       :organization     "753-YA"
                       :primaryOperation {:name "ya-katulupa-vesi-ja-viemarityot"}
                       :auth             [{:lastName  "Panaani"
                                           :firstName "Pena"
                                           :username  "pena"
                                           :role      "writer"
                                           :id        "777777777777777777000020"}]
                       :documents        [{:schema-info {:name "tyoaika"}
                                           :data        {:tyoaika-paattyy-ms {:value (-> (date/now)
                                                                                         (date/plus :days 2)
                                                                                         (date/timestamp))}}}]}))
      ;; Insert the jatkoaika application with expiring work time
      (mongo/insert :applications
                    (merge
                      domain/application-skeleton
                      {:id               jatkoaika-app-id
                       :address          "Jatkoaika needs reminder"
                       :title            "Jatkoaika needs reminder"
                       :permitType       "YA"
                       :state            "verdictGiven"
                       :submitted        jatkoaika-submitted
                       :municipality     "753"
                       :organization     "753-YA"
                       :primaryOperation {:name "ya-jatkoaika"}
                       :auth             [{:lastName  "Panaani"
                                           :firstName "Pena"
                                           :username  "pena"
                                           :role      "writer"
                                           :id        "777777777777777777000020"}]
                       :documents        [{:schema-info {:name "tyoaika"}
                                           :data        {:tyoaika-paattyy-ms {:value (-> (date/now)
                                                                                         (date/plus :days 5)
                                                                                         (date/timestamp))}}}]}))
      ;; Create app-link between the two applications
      (mongo/insert :app-links {:id                        (str original-app-id "|" jatkoaika-app-id)
                                :link                      [original-app-id jatkoaika-app-id]
                                (keyword jatkoaika-app-id) {:type    "application"
                                                            :apptype "ya-jatkoaika"}
                                (keyword original-app-id)  {:type "linkpermit"}})
      (reminders/ya-work-time-is-expiring-reminder (test-system/get-common-components))
      (let [emails (dummy-email-server/messages :reset true)]
        (is (= 1 (count emails)))
        (is (ss/includes? (:subject (first emails)) "Jatkoaika needs reminder"))))))

(deftest deadlines-reminders-test
  (fixture/apply-fixture "minimal" (test-system/get-common-components))
  (testing "Construction not started reminder"
    (insert-app :address "No aloitettava deadline")
    (insert-app :address "Passed aloitettava deadline" :deadlines.aloitettava passed-deadline)
    (insert-app :address "Future aloitettava deadline" :deadlines.aloitettava future-deadline)
    (insert-app :address "Wrong permit type" :permitType "YA"
                :deadlines.aloitettava active-deadline)
    (insert-app :address "Wrong state" :state "appealed" :deadlines.aloitettava active-deadline)
    (insert-app :address "Read-only" :readOnly true :deadlines.aloitettava active-deadline)
    (testing "Applications inserted"
      (is (= 6 (mongo/count :applications {}))))
    (testing "No reminders sent"
      (reminders/construction-not-started-reminder (test-system/get-common-components))
      (is (empty? (dummy-email-server/messages :reset true))))
    (testing "Send reminder"
      (insert-app :address "Aloitettava!" :deadlines.aloitettava active-deadline)
      (reminders/construction-not-started-reminder (test-system/get-common-components))
      (let [emails (dummy-email-server/messages :reset true)]
        (is (= 1 (count emails)))
        (let [email (first emails)]
          (is (ss/includes? (:to email) "Pena Panaani <pena@example.com>"))
          (is (= "Lupapiste: Aloitettava!, Sipoo - muistutus luvan vanhenemisesta" (:subject email)))
          (is (ss/includes? (get-in email [:body :plain]) "Tietojemme mukaan rakennustyötä ei ole vielä aloitettu.")))))
    (testing "Sent reminder is marked"
      (is (pos? (:construction-not-started (:reminder-sent (mongo/select-one :applications {:address "Aloitettava!"}))))))
    (testing "Reminder is not resent"
      (reminders/construction-not-started-reminder (test-system/get-common-components))
      (is (empty? (dummy-email-server/messages :reset true)))))
  (testing "No final review reminder"
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
      (testing "No reminders sent"
        (reminders/no-final-review-reminder (test-system/get-common-components))
        (is (empty? (dummy-email-server/messages :reset true))))

      (doseq [task [lopullinen-luonnos-loppukatselmus osittainen-pidetty-loppukatselmus
                    osittainen-luonnos-loppukatselmus lopullinen-luonnos-osittainen
                    osittainen-luonnos-osittainen lopullinen-pidetty-aloituskokous
                    osittainen-pidetty-osittainen {}]]
        (final-review-reminder-is-sent task)))))

(def- supplement-request-reminder-sent
  {:id                     "525533f7e4b0138a23d8r4b4"
   :due-date               timestamp-1-day-ago
   :state                  "requested"
   :due-date-reminder-sent timestamp-1-day-ago})

(def- supplement-request-non-matching
      {:id       "525533f7e4b0138a23d8r4b4"
       :due-date timestamp-1-day-in-future
       :state    "replied"})

(def- supplement-request-matching
      {:id       "525533f7e4b0138a23d8e4b5"
       :due-date timestamp-1-day-in-future
       :state    "requested"})

(def- supplement-request-due-date-passed
      {:id       "525533f7e4b0138a23d81234"
       :due-date timestamp-1-day-ago
       :state    "requested"})

(def- supplement-request-reminder-application
      (merge
        domain/application-skeleton
        {:schema-version      1
         :auth                [{:lastName  "Panaani"
                                :firstName "Pena"
                                :username  "pena"
                                :role      "writer"
                                :id        "777777777777777777000020"}]
         :state               "submitted"
         :location            {:x 444444.0, :y 6666666.0}
         :supplement-requests [supplement-request-reminder-sent
                               supplement-request-non-matching
                               supplement-request-matching
                               supplement-request-due-date-passed]
         :organization        "564-YMP"
         :title               "Naapurikuja 3"
         :address             "Naapurikuja 3"
         :primaryOperation    {:id      "534bf825299508fb3618455d"
                               :name    "maa-aineslupa"
                               :created 1397487653097}
         :secondaryOperations []
         :infoRequest         false
         :openInfoRequest     false
         :opened              1397487653750
         :created             1397487653097
         :propertyId          "75312312341234"
         :modified            (-> (date/now) (date/minus :months 2) (date/timestamp))
         :permitType          "MAL"
         :id                  "LP-753-2014-12345"
         :municipality        "564"}))


(deftest supplement-request-reminder-due-date-test
  (mongo/remove :applications (:id supplement-request-reminder-application))
  (mongo/insert :applications supplement-request-reminder-application)

  (testing "due-date-reminder-sent timestamp does not pre-exist and one matching supplement request exists -> reminder is sent"
    (let [now-timestamp (now)]
      (reminders/supplement-request-reminder-due-date (test-system/get-common-components))
      (let [app                             (mongo/by-id :applications (:id supplement-request-reminder-application))
            due-date-reminder-sent-requests (filter :due-date-reminder-sent (:supplement-requests app))]
        (is (= 4 (count (:supplement-requests app))))
        (is (= 2 (count due-date-reminder-sent-requests)))
        (is (= (:id supplement-request-due-date-passed) (-> due-date-reminder-sent-requests second :id)))
        (is (true? (>= (-> due-date-reminder-sent-requests second :due-date-reminder-sent) now-timestamp))))

      (check-sent-reminder-email
        "pena@example.com"
        "Lupapiste: Naapurikuja 3, Oulu - muistutus erääntyvästä täydennyspyynnöstä"
        ["Muistutus:" "on täydennyspyyntö, jonka määräpäivä oli"]
        (:id supplement-request-reminder-application))))

  (testing "a fresh reminder-sent timestamp already exists -> no reminder is sent"
    (dummy-email-server/messages :reset true) ;; clears inbox
    (reminders/supplement-request-reminder-due-date (test-system/get-common-components))
    (is (empty? (dummy-email-server/messages :reset true)))))

(deftest no-linked-continuation-applications-with-verdict-test
  (fixture/apply-fixture "minimal" (test-system/get-common-components))

  (testing "Returns true when no links exist"
    (let [app-id "LP-753-2026-00001"]
      (insert-app :id app-id :submitted (now))
      (is (true? (reminders/no-linked-continuation-applications-with-verdict?
                   {:id app-id :submitted (now)})))))

  (testing "Returns true when submitted is nil"
    (let [app-id "LP-753-2026-00002"]
      (insert-app :id app-id)
      (is (true? (reminders/no-linked-continuation-applications-with-verdict?
                   {:id app-id :submitted nil})))))

  (testing "Returns true when linked app is not a jatkoaika operation"
    (let [original-id     "LP-753-2026-00010"
          linked-id       "LP-753-2026-00011"
          original-submit (-> (date/now) (date/minus :months 6) (date/timestamp))
          linked-submit   (-> (date/now) (date/minus :months 3) (date/timestamp))]
      (insert-app :id original-id :submitted original-submit)
      (insert-app :id linked-id
                  :submitted linked-submit
                  :state "verdictGiven"
                  :primaryOperation {:name "pientalo"}) ;; Not a jatkoaika
      (mongo/insert :app-links {:id                   (str original-id "|" linked-id)
                                :link                 [original-id linked-id]
                                (keyword linked-id)   {:type "application" :apptype "pientalo"}
                                (keyword original-id) {:type "linkpermit"}})
      (is (true? (reminders/no-linked-continuation-applications-with-verdict?
                   {:id original-id :submitted original-submit})))))

  (testing "Returns true when linked jatkoaika is not in post-verdict state"
    (let [original-id     "LP-753-2026-00020"
          linked-id       "LP-753-2026-00021"
          original-submit (-> (date/now) (date/minus :months 6) (date/timestamp))
          linked-submit   (-> (date/now) (date/minus :months 3) (date/timestamp))]
      (insert-app :id original-id :submitted original-submit)
      (insert-app :id linked-id
                  :submitted linked-submit
                  :state "submitted" ;; Not post-verdict
                  :primaryOperation {:name "raktyo-aloit-loppuunsaat"})
      (mongo/insert :app-links {:id                   (str original-id "|" linked-id)
                                :link                 [original-id linked-id]
                                (keyword linked-id)   {:type "application" :apptype "raktyo-aloit-loppuunsaat"}
                                (keyword original-id) {:type "linkpermit"}})
      (is (true? (reminders/no-linked-continuation-applications-with-verdict?
                   {:id original-id :submitted original-submit})))))

  (testing "Returns true when linked jatkoaika was submitted before original"
    (let [original-id     "LP-753-2026-00030"
          linked-id       "LP-753-2026-00031"
          original-submit (-> (date/now) (date/minus :months 3) (date/timestamp))
          linked-submit   (-> (date/now) (date/minus :months 6) (date/timestamp))] ;; Before original
      (insert-app :id original-id :submitted original-submit)
      (insert-app :id linked-id
                  :submitted linked-submit
                  :state "verdictGiven"
                  :primaryOperation {:name "raktyo-aloit-loppuunsaat"})
      (mongo/insert :app-links {:id                   (str original-id "|" linked-id)
                                :link                 [original-id linked-id]
                                (keyword linked-id)   {:type "application" :apptype "raktyo-aloit-loppuunsaat"}
                                (keyword original-id) {:type "linkpermit"}})
      (is (true? (reminders/no-linked-continuation-applications-with-verdict?
                   {:id original-id :submitted original-submit})))))

  (testing "Returns false when valid continuation application exists"
    (let [original-id     "LP-753-2026-00040"
          linked-id       "LP-753-2026-00041"
          original-submit (-> (date/now) (date/minus :months 6) (date/timestamp))
          linked-submit   (-> (date/now) (date/minus :months 3) (date/timestamp))]
      (insert-app :id original-id :submitted original-submit)
      (insert-app :id linked-id
                  :submitted linked-submit
                  :state "verdictGiven" ;; Post-verdict state
                  :primaryOperation {:name "raktyo-aloit-loppuunsaat"}) ;; Jatkoaika operation
      (mongo/insert :app-links {:id                   (str original-id "|" linked-id)
                                :link                 [original-id linked-id]
                                (keyword linked-id)   {:type "application" :apptype "raktyo-aloit-loppuunsaat"}
                                (keyword original-id) {:type "linkpermit"}})
      (is (false? (reminders/no-linked-continuation-applications-with-verdict?
                    {:id original-id :submitted original-submit})))))

  (testing "Returns false when YA jatkoaika with verdict exists"
    (let [original-id     "LP-753-2026-00050"
          linked-id       "LP-753-2026-00051"
          original-submit (-> (date/now) (date/minus :months 6) (date/timestamp))
          linked-submit   (-> (date/now) (date/minus :months 3) (date/timestamp))]
      (insert-app :id original-id
                  :submitted original-submit
                  :permitType "YA"
                  :primaryOperation {:name "ya-katulupa-vesi-ja-viemarityot"})
      (insert-app :id linked-id
                  :submitted linked-submit
                  :state "finished" ;; Post-verdict state
                  :permitType "YA"
                  :primaryOperation {:name "ya-jatkoaika"})
      (mongo/insert :app-links {:id                   (str original-id "|" linked-id)
                                :link                 [original-id linked-id]
                                (keyword linked-id)   {:type "application" :apptype "ya-jatkoaika"}
                                (keyword original-id) {:type "linkpermit"}})
      (is (false? (reminders/no-linked-continuation-applications-with-verdict?
                    {:id original-id :submitted original-submit}))))))
