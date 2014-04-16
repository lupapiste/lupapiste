(ns lupapalvelu.reminders-itest
  (:require [clojure.java.io :as io]
            [monger.operators :refer :all]
            [midje.sweet :refer :all]
            [lupapalvelu.core :refer [now]]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer [fact* facts*]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.action :refer :all]
            [sade.dummy-email-server :as dummy-email-server]
            [lupapalvelu.batchrun :as batchrun]))


(def ^:private timestamp-the-beginning-of-time 12345)

(def ^:private documents
  [{:id "534bf825299508fb3618455f"
    :schema-info {:approvable true
                  :subtype "hakija"
                  :name "hakija"
                  :removable true
                  :repeating true
                  :version 1
                  :type "party"
                  :order 3}
    :created 1397487653097
    :data {:_selected {:value "henkilo"}}}
   {:id "534bf825299508fb3618455e"
    :schema-info {:version 1
                  :name "uusiRakennus"
                  :approvable true
                  :op {:id "534bf825299508fb3618455d"
                       :name "asuinrakennus"
                       :created 1397487653097}
                  :removable true}
    :created 1397487653097
    :data {:huoneistot
           {:0 {:huoneistoTunnus {:huoneistonumero {:modified 1397487653097 :value "000"}}}},
           :kaytto {:kayttotarkoitus {:modified 1397487653097, :value "011 yhden asunnon talot"}}}}
   {:id "534bf825299508fb36184564"
    :schema-info
    {:approvable true, :name "hankkeen-kuvaus" :version 1 :order 1}
    :created 1397487653097
    :data {}}
   {:id "534bf825299508fb36184565"
    :schema-info {:approvable true
                  :name "maksaja"
                  :removable true
                  :repeating true
                  :version 1
                  :type "party"
                  :order 6}
    :created 1397487653097
    :data {}}
   {:id "534bf825299508fb36184566"
    :schema-info {:approvable true, :name "rakennuspaikka", :version 1, :order 2}
    :created 1397487653097
    :data {}}
   {:id "534bf825299508fb36184567"
    :schema-info {:approvable true
                  :name "paasuunnittelija"
                  :removable false
                  :version 1
                  :type "party"
                  :order 4}
    :created 1397487653097
    :data {}}
   {:id "534bf825299508fb36184568"
    :schema-info {:approvable true
                  :name "suunnittelija"
                  :removable true
                  :repeating true
                  :version 1
                  :type "party"
                  :order 5},
    :created 1397487653097
    :data {}}
   {:id
    "534bf825299508fb36184569"
    :schema-info {:approvable true
                  :name "tyonjohtaja"
                  :removable true
                  :repeating true
                  :version 1
                  :type "party"
                  :order 5}
    :created 1397487653097
    :data {}}])

(def ^:private attachments
  [{:state "requires_user_action"
    :target nil
    :op {:id "534bf825299508fb3618455d"
         :name "asuinrakennus"
         :created 1397487653097}
    :locked false
    :type {:type-group "paapiirustus", :type-id "asemapiirros"}
    :applicationState "open"
    :modified 1397487653097
    :versions []
    :id "534bf825299508fb36184560"}])

(def ^:private comments
  [{:text "foo"
    :type "applicant"
    :target {:type "application"}
    :created 1397487653750
    :to nil
    :user {:role "applicant"
           :lastName "Panaani"
           :firstName "Pena"
           :username "pena"
           :id "777777777777777777000020"}}])

(def ^:private neighbors
  [{:id "534bf825299508fb3618456c"
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
              :created timestamp-the-beginning-of-time
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
                     :personId "010203-0405"
                     :role "applicant"
                     :zip "10203"
                     :id "777777777777777777000020"}}]}])

(def ^:private statements
  [{:id "525533f7e4b0138a23d8e4b5"
    :given nil
    :person {:id "5252ecdfe4b0138a23d8e385"
             :text "Palotarkastus"
             :email "sito.lupapiste@gmail.com"
             :name "Sito Lupapiste"}
    :requested timestamp-the-beginning-of-time
    :status nil}])

(def ^:private app-id
  "LP-753-2014-12345")

(def ^:private reminder-application
  {:sent nil,
  :neighbors neighbors
  :schema-version 1
  :authority {}
  :auth [{:lastName "Panaani"
          :firstName "Pena"
          :username "pena"
          :type "owner"
          :role "owner"
          :id "777777777777777777000020"}]
  :drawings []
  :submitted nil
  :state "open"
  :permitSubtype nil
  :tasks []
  :closedBy {}
  :_verdicts-seen-by {}
  :location {:x 444444.0, :y 6666666.0}
  :attachments attachments
  :statements statements
  :organization "753-R"
  :buildings []
  :title "Naapurikuja 3"
  :started nil
  :closed nil
  :operations [{:id "534bf825299508fb3618455d"
                :name "asuinrakennus"
                :created 1397487653097}]
  :infoRequest false
  :openInfoRequest false
  :opened 1397487653750
  :created 1397487653097
  :_comments-seen-by {}
  :propertyId "75312312341234"
  :verdicts []
  :startedBy {}
  :documents documents
  :_statements-seen-by {}
  :modified timestamp-the-beginning-of-time
  :comments comments
  :address "Naapurikuja 3"
  :permitType "R"
  :id app-id
  :municipality "753"})

(def ^:private open-inforequest-id "0yqaV2vEcGDH9LYaLFOlxSTpLidKI7xWbuJ9IGGv0iPM0Rv2")

(def ^:private open-inforequest-entry {"_id" open-inforequest-id
                                       :application-id "LP-732-2013-00006"
                                       :created 1382597181946
                                       :email "juba.skebamies@example.com"
                                       :last-used nil
                                       :organization-id "732-R"})

(defn- check-sent-reminder-email [to subject bodypart]

  ;; dummy-email-server/messages sometimes returned nil for the email
  ;; (because the email sending is asynchronous). Thus applying sleep here.
  (Thread/sleep 100)

  (let [email (last (dummy-email-server/messages :reset true))]
    (fact "email check"
      (:to email) => to
      (:subject email) => subject
      (get-in email [:body :plain]) => (contains bodypart))))


(facts "reminders"

 (apply-remote-minimal)
 (mongo/insert :applications reminder-application)
 (dummy-email-server/messages :reset true)  ;; clears inbox


 (facts "statement-request-reminder"

   (fact "the \"reminder-sent\" timestamp does not exist"
     (let [now-timestamp (now)]

       (batchrun/statement-request-reminder)

       (let [app (mongo/by-id :applications app-id)]
         (> (-> app :statements first :reminder-sent) now-timestamp) => true?)

       (check-sent-reminder-email
         "pena@example.com"
         "Lupapiste.fi: Naapurikuja 3 - Muistutus lausuntopyynn\u00f6st\u00e4"
         "Sinulta on pyydetty lausuntoa lupahakemukseen")
       ))

   (fact "the \"reminder-sent\" timestamp already exists"
     (update-application
       (application->command reminder-application)
       {:statements {$elemMatch {:id (-> statements first :id)}}}
       {$set {:statements.$.reminder-sent timestamp-the-beginning-of-time}})

     (batchrun/statement-request-reminder)

     (let [app (mongo/by-id :applications app-id)]
       (> (-> app :statements first :reminder-sent) timestamp-the-beginning-of-time) => true?)

     (check-sent-reminder-email
       "pena@example.com"
       "Lupapiste.fi: Naapurikuja 3 - Muistutus lausuntopyynn\u00f6st\u00e4"
       "Sinulta on pyydetty lausuntoa lupahakemukseen")
     ))


 (facts "open-inforequest-reminder"

   (mongo/insert :open-inforequest-token open-inforequest-entry)

   (fact "the \"reminder-sent\" timestamp does not exist"
     (let [now-timestamp (now)]

       (batchrun/open-inforequest-reminder)

       (let [oir (mongo/by-id :open-inforequest-token open-inforequest-id)]
         (> (:reminder-sent oir) now-timestamp) => true?

         (check-sent-reminder-email
           (:email oir)
           "Lupapiste.fi: Muistutus avoimesta neuvontapyynn\u00f6st\u00e4"
           "Organisaatiollasi on vastaamaton neuvontapyynt\u00f6")
         )))

   (fact "the \"reminder-sent\" timestamp already exists"
     (mongo/update-by-id :open-inforequest-token open-inforequest-id
       {$set {:reminder-sent timestamp-the-beginning-of-time}})

     (batchrun/open-inforequest-reminder)

     (let [oir (mongo/by-id :open-inforequest-token open-inforequest-id)]

       (> (:reminder-sent oir) timestamp-the-beginning-of-time) => true?

       (check-sent-reminder-email
         (:email oir)
         "Lupapiste.fi: Muistutus avoimesta neuvontapyynn\u00f6st\u00e4"
         "Organisaatiollasi on vastaamaton neuvontapyynt\u00f6")
       )))


 (facts "neighbor-reminder"

   (fact "the \"reminder-sent\" status does not exist"
     (let [now-timestamp (now)]
       (batchrun/neighbor-reminder)


       (let [app (mongo/by-id :applications app-id)
             status-reminder-sent (first (filter
                                           #(= "reminder-sent" (:state %))
                                           (-> app :neighbors first :status)))]

         status-reminder-sent =not=> nil?
         (> (:created status-reminder-sent) now-timestamp) => true?

         (check-sent-reminder-email
           (-> app :neighbors first :status second :email)
           "Lupapiste.fi: Naapurikuja 3 - Muistutus naapurin kuulemisesta"
           "T\u00e4m\u00e4 on muistutusviesti. Rakennuspaikan rajanaapurina Teille ilmoitetaan")
         )))

   (fact "the \"reminder-sent\" status already exists - no emails sent"
     (dummy-email-server/messages :reset true)  ;; clears inbox
     (batchrun/neighbor-reminder)
     (dummy-email-server/messages :reset true) => empty?))


 (facts "application-state-reminder"

   (fact "the \"reminder-sent\" timestamp does not exist"
     (let [now-timestamp (now)]

       (batchrun/application-state-reminder)

       (let [app (mongo/by-id :applications app-id)]
         (> (:reminder-sent app) now-timestamp) => true?

         (check-sent-reminder-email
           "pena@example.com"
           "Lupapiste.fi: Naapurikuja 3 - Muistutus aktiivisesta hakemuksesta"
           "Sinulla on Lupapiste.fi-palvelussa aktiivinen lupahakemus")
         )))

   (fact "the \"reminder-sent\" timestamp already exists"
     (update-application (application->command reminder-application)
       {$set {:reminder-sent timestamp-the-beginning-of-time}})

     (batchrun/application-state-reminder)

     (let [app (mongo/by-id :applications app-id)]
       (> (:reminder-sent app) timestamp-the-beginning-of-time) => true?

       (check-sent-reminder-email
         "pena@example.com"
         "Lupapiste.fi: Naapurikuja 3 - Muistutus aktiivisesta hakemuksesta"
         "Sinulla on Lupapiste.fi-palvelussa aktiivinen lupahakemus")
       )))

 )

