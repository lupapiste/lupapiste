(ns lupapalvelu.reminders-itest
  (:require [clojure.java.io :as io]
            [monger.operators :refer :all]
            [midje.sweet :refer :all]
            [sade.dummy-email-server :as dummy-email-server]
            [sade.core :refer [def- now]]
            [sade.util :as util]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.application :as application]
            [lupapalvelu.factlet :refer [fact* facts*]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.action :refer :all]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.batchrun :as batchrun]))

(def db-name (str "test_reminders-itest_" (now)))

(mongo/connect!)
(mongo/with-db db-name
  (dummy-email-server/messages :reset true)  ;; clears inbox
  (fixture/apply-fixture "minimal"))

(def- timestamp-the-beginning-of-time 0)
(def- timestamp-1-day-ago (util/get-timestamp-ago :day 1))
(def- timestamp-1-day-in-future (util/get-timestamp-from-now :day 1))

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
             :type "owner"
             :role "owner"
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
     :modified timestamp-the-beginning-of-time
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
      :auth [{:lastName "Panaani"
              :firstName "Pena"
              :username "pena"
              :type "owner"
              :role "owner"
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
                                 :removable false,
                                 :type "group",
                                 :name "tyoaika"},
                   :data {:tyoaika-alkaa-ms {:value (util/get-timestamp-from-now :day 1), :modified 1443177749608},
                          :tyoaika-paattyy-ms {:value (util/get-timestamp-from-now :day 6), :modified 1443177751909}}}]}))

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
          email => (partial contains-application-link? application-id link-role))))))



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

          (batchrun/statement-request-reminder)

          (let [app (mongo/by-id :applications (:id reminder-application))
                reminder-sent-statements (filter :reminder-sent (:statements app))]
            (count reminder-sent-statements) => 1
            (-> reminder-sent-statements first :id) => (:id statement-matching)
            (>= (-> reminder-sent-statements first :reminder-sent) now-timestamp) => true?)

          (check-sent-reminder-email
            (-> statement-matching :person :email)
            "Lupapiste: Naapurikuja 3, Sipoo - muistutus lausuntopyynn\u00f6st\u00e4"
            ["muistuttelemme" "sinulta on pyydetty lausuntoa"]
            (:id reminder-application) "authority"))))

    (fact "the \"reminder-sent\" timestamp already exists but is over 1 week old -> reminder is sent"
      (mongo/with-db db-name

        (update-application
          (application->command reminder-application)
          {:statements {$elemMatch {:id (:id statement-matching)}}}
          {$set {:statements.$.reminder-sent timestamp-the-beginning-of-time}})

        (batchrun/statement-request-reminder)

        (let [app (mongo/by-id :applications (:id reminder-application))
              reminder-sent-statements (filter :reminder-sent (:statements app))]
          (count reminder-sent-statements) => 1
          (> (-> reminder-sent-statements first :reminder-sent) timestamp-the-beginning-of-time) => true?)

        ;; clears inbox
        (check-sent-reminder-email
          (-> statement-matching :person :email)
          "Lupapiste: Naapurikuja 3, Sipoo - muistutus lausuntopyynn\u00f6st\u00e4"
          ["muistuttelemme" "sinulta on pyydetty lausuntoa"]
          (:id reminder-application) "authority")))

    (fact "a fresh \"reminder-sent\" timestamp already exists -> no reminder is sent"
      (mongo/with-db db-name
        (dummy-email-server/messages :reset true)  ;; clears inbox
        (batchrun/statement-request-reminder)
        (dummy-email-server/messages :reset true) => empty?)))



  (facts "statement-reminder-due-date"

    ;; reset test conditions
    (mongo/with-db db-name
      (mongo/remove :applications (:id reminder-application))
      (mongo/insert :applications reminder-application))

    (fact "the \"duedate-reminder-sent\" timestamp does not pre-exist and one matching statement exists -> reminder is sent"
      (mongo/with-db db-name
        (let [now-timestamp (now)]

          (batchrun/statement-reminder-due-date)

          (let [app (mongo/by-id :applications (:id reminder-application))
                duedate-reminder-sent-statements (filter :duedate-reminder-sent (:statements app))]
            (count (:statements app)) => 4
            (count duedate-reminder-sent-statements) => 1
            (-> duedate-reminder-sent-statements first :id) => (:id statement-matching-duedate-passed)
            (>= (-> duedate-reminder-sent-statements first :duedate-reminder-sent) now-timestamp) => true?)

          (check-sent-reminder-email
            (-> statement-matching-duedate-passed :person :email)
            "Lupapiste: Naapurikuja 3, Sipoo - muistutus er\u00e4\u00e4ntyv\u00e4st\u00e4 lausuntopyynn\u00f6st\u00e4"
            ["muistuttelemme" "sinulta on pyydetty lausuntoa" "Lausunnolle asetettu m\u00e4\u00e4r\u00e4aika"]
            (:id reminder-application) "authority"))))

    (fact "the \"duedate-reminder-sent\" timestamp already exists but is over 1 week old -> reminder is sent"
      (mongo/with-db db-name

        (update-application
          (application->command reminder-application)
          {:statements {$elemMatch {:id (:id statement-matching-duedate-passed)}}}
          {$set {:statements.$.duedate-reminder-sent timestamp-the-beginning-of-time}})

        (batchrun/statement-reminder-due-date)

        (let [app (mongo/by-id :applications (:id reminder-application))
              duedate-reminder-sent-statements (filter :duedate-reminder-sent (:statements app))]
          (count duedate-reminder-sent-statements) => 1
          (> (-> duedate-reminder-sent-statements first :duedate-reminder-sent) timestamp-the-beginning-of-time) => true?)

        ;; clears inbox
        (check-sent-reminder-email
          (-> statement-matching-duedate-passed :person :email)
          "Lupapiste: Naapurikuja 3, Sipoo - muistutus er\u00e4\u00e4ntyv\u00e4st\u00e4 lausuntopyynn\u00f6st\u00e4"
          ["muistuttelemme" "sinulta on pyydetty lausuntoa" "Lausunnolle asetettu m\u00e4\u00e4r\u00e4aika"]
          (:id reminder-application) "authority")))

    (fact "a fresh \"duedate-reminder-sent\" timestamp already exists -> no reminder is sent"
      (mongo/with-db db-name
        (dummy-email-server/messages :reset true)  ;; clears inbox
        (batchrun/statement-reminder-due-date)
        (dummy-email-server/messages :reset true) => empty?)))



  (facts "open-inforequest-reminder"

    (fact "the \"reminder-sent\" timestamp does not pre-exist and one matching inforequest exists -> reminder is sent"
      (mongo/with-db db-name
        (let [now-timestamp (now)]

          (batchrun/open-inforequest-reminder)

          (let [oir-matching (mongo/by-id :open-inforequest-token (:_id open-inforequest-entry-matching))
                oir-non-matching (mongo/by-id :open-inforequest-token (:_id open-inforequest-entry-non-matching))]
            (>= (:reminder-sent oir-matching) now-timestamp) => true?
            (:reminder-sent oir-non-matching) => nil?)

          (check-sent-reminder-email
           (:email open-inforequest-entry-matching)
           "Lupapiste: Naapurikuja 3 - muistutus avoimesta neuvontapyynn\u00f6st\u00e4"
           ["on avattu neuvontapyynt\u00f6" "Vastaathan"]))))

    (fact "the \"reminder-sent\" timestamp already exists but is over 1 week old -> reminder is sent"
      (mongo/with-db db-name

        (mongo/update-by-id :open-inforequest-token (:_id open-inforequest-entry-matching)
                            {$set {:reminder-sent timestamp-the-beginning-of-time}})

        (batchrun/open-inforequest-reminder)

        (let [oir (mongo/by-id :open-inforequest-token (:_id open-inforequest-entry-matching))]
          (> (:reminder-sent oir) timestamp-the-beginning-of-time) => true?

          ;; clears inbox
          (check-sent-reminder-email
           (:email open-inforequest-entry-matching)
           "Lupapiste: Naapurikuja 3 - muistutus avoimesta neuvontapyynn\u00f6st\u00e4"
           ["on avattu neuvontapyynt\u00f6" "Vastaathan"]))))

    (fact "a fresh \"duedate-reminder-sent\" timestamp already exists -> no reminder is sent"
      (mongo/with-db db-name
        (dummy-email-server/messages :reset true)  ;; clears inbox
        (batchrun/statement-reminder-due-date)
        (dummy-email-server/messages :reset true) => empty?)))


  (facts "neighbor-reminder"
    (fact "the \"reminder-sent\" status does not pre-exist and one matching neighbor exists -> reminder is sent"
      (mongo/with-db db-name
        (let [now-timestamp (now)]

          (batchrun/neighbor-reminder)

          (let [app (mongo/by-id :applications (:id reminder-application))
                neighbors-with-reminder-sent-status (get-neighbors-with-reminder-sent-status app)]

            (count neighbors-with-reminder-sent-status) => 1
            (> (-> neighbors-with-reminder-sent-status first :timestamp-reminder-sent) now-timestamp) => true?

            ;; clears inbox
            (check-sent-reminder-email
              (->> neighbor-matching :status (filter #(= "email-sent" (:state %))) first :email)
              "Lupapiste: Naapurikuja 3, Sipoo - muistutus naapurin kuulemisesta"
              ["Hei n," "muistuttelemme" "rajanaapurina teille on ilmoitettu"])))))

    (fact "a recent \"reminder-sent\" status already exists - no reminder is sent"
      (mongo/with-db db-name
        (dummy-email-server/messages :reset true)  ;; clears inbox
        (batchrun/neighbor-reminder)
        (dummy-email-server/messages :reset true) => empty?))

    (fact "an very old \"reminder-sent\" status already exists -> reminder is not sent"
      (mongo/with-db db-name
        ;; in "neighbor-matching", set the :created timestamp of the "email-sent" status to "timestamp-the-beginning-of-time"
        (let [now-timestamp (now)
              app (mongo/by-id :applications (:id reminder-application))
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

          (batchrun/neighbor-reminder)

          (let [app (mongo/by-id :applications (:id reminder-application))
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

          (batchrun/application-state-reminder)

          (let [app (mongo/by-id :applications (:id reminder-application))]
            (>= (:reminder-sent app) now-timestamp) => true?

            (check-sent-reminder-email
             "pena@example.com"
             "Lupapiste: Naapurikuja 3, Sipoo - onko hanke yh\u00e4 aktiivinen?"
             ["sinulla on Lupapiste-palvelussa aktiivinen lupahakemus"])))))

    (fact "the 'reminder-sent' timestamp already exists but is over 1 week old -> reminder is sent"
      (mongo/with-db db-name
        (update-application (application->command reminder-application)
                            {$set {:reminder-sent timestamp-the-beginning-of-time}})

        (batchrun/application-state-reminder)

        (let [app (mongo/by-id :applications (:id reminder-application))]
          (> (:reminder-sent app) timestamp-the-beginning-of-time) => true?

          (check-sent-reminder-email
           "pena@example.com"
           "Lupapiste: Naapurikuja 3, Sipoo - onko hanke yh\u00e4 aktiivinen?"
           ["sinulla on Lupapiste-palvelussa aktiivinen lupahakemus"]))))

    (fact "the 'reminder-sent' timestamp already exists - no reminder is sent"
      (mongo/with-db db-name
        (dummy-email-server/messages :reset true)  ;; clears inbox
        (batchrun/application-state-reminder)
        (dummy-email-server/messages :reset true) => empty?)))



  (facts "ya-work-time-is-expiring-reminder"

    (fact "timestamp does not pre-exist -> reminder is sent"
      (mongo/with-db db-name
        (let [app (mongo/by-id :applications (:id ya-reminder-application))]

          (:work-time-expiring-reminder-sent app) => nil?

          (batchrun/ya-work-time-is-expiring-reminder)

          (let [app (mongo/by-id :applications (:id ya-reminder-application))
                tyoaika-paattyy (->> ya-reminder-application :documents
                                  (filter #(= "tyoaika" (-> % :schema-info :name)))
                                  first :data :tyoaika-paattyy-ms :value)]

            (:work-time-expiring-reminder-sent app) => number?

            (check-sent-reminder-email
              "pena@example.com"
              "Lupapiste: Latokuja 3, Sipoo - tarvitaanko jatkoaikaa?"
              ["Luvan p\u00e4\u00e4ttymisajankohdaksi on merkitty" (util/to-local-date tyoaika-paattyy) (:address app)])))))

    (fact "timestamp already exists -> no reminder is sent"
      (mongo/with-db db-name
        (update-application (application->command ya-reminder-application)
          {$set {:work-time-expiring-reminder-sent timestamp-the-beginning-of-time}})

        (batchrun/ya-work-time-is-expiring-reminder)

        (let [app (mongo/by-id :applications (:id ya-reminder-application))]
          (= (:work-time-expiring-reminder-sent app) timestamp-the-beginning-of-time) => true?
          (dummy-email-server/messages) => empty?)))

    (fact "reminder is sent also to applications in state 'construction-started'"
      (mongo/with-db db-name
        (update-application (application->command ya-reminder-application)
                            (merge (application/state-transition-update :constructionStarted 0 ya-reminder-application {})
                                   {$unset {:work-time-expiring-reminder-sent 1}}))
        (batchrun/ya-work-time-is-expiring-reminder)
        (check-sent-reminder-email
          "pena@example.com"
          "Lupapiste: Latokuja 3, Sipoo - tarvitaanko jatkoaikaa?"
          ["Luvan p\u00e4\u00e4ttymisajankohdaksi on merkitty"])))

    (fact "applications without 'tyoaika' document are not reacted to"
      (mongo/with-db db-name
        (update-application (application->command ya-reminder-application)
            {$unset {:work-time-expiring-reminder-sent 1}
             $set {:documents (remove
                                #(= "tyoaika" (-> % :schema-info :name))
                                (:documents ya-reminder-application))}})

        (batchrun/ya-work-time-is-expiring-reminder)
        (dummy-email-server/messages) => empty?))

    (fact "applications with :tyoaika-paattyy-pvm date too far in the future are not reacted to"
      (mongo/with-db db-name
        (let [date-str-8-days-in-future (util/to-local-date (util/get-timestamp-from-now :day 8))]

          (update-application
            (application->command ya-reminder-application)
            {:documents {$elemMatch {:schema-info (:name "tyoaika")}}}
            {$set {:documents.$.data.tyoaika-paattyy-pvm.value date-str-8-days-in-future}})  ;; 8 days -> will not trigger reminder

          (batchrun/ya-work-time-is-expiring-reminder)
          (dummy-email-server/messages) => empty?)))

    (fact "applications with :tyoaika-paattyy-pvm date in the past are not reacted to"
      (mongo/with-db db-name
        (let [date-str-1-day-ago (util/to-local-date (util/get-timestamp-ago :day 1))]

          (update-application
            (application->command ya-reminder-application)
            {:documents {$elemMatch {:schema-info (:name "tyoaika")}}}
            {$set {:documents.$.data.tyoaika-paattyy-pvm.value date-str-1-day-ago}})  ;; 1 day in the past -> will not trigger reminder

          (batchrun/ya-work-time-is-expiring-reminder)
          (dummy-email-server/messages) => empty?)))

    ))
