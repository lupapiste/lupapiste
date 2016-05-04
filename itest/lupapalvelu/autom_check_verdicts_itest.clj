(ns lupapalvelu.autom-check-verdicts-itest
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer [fact* facts*]]
            [sade.core :refer [now fail]]
            [sade.dummy-email-server :as dummy-email-server]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.integrations-api]
            [lupapalvelu.verdict-api]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.batchrun :as batchrun]
            [lupapalvelu.xml.krysp.application-from-krysp :as app-from-krysp]))

(def db-name (str "test_autom-check-verdicts-itest_" (now)))

(mongo/connect!)
(mongo/with-db db-name
  (fixture/apply-fixture "minimal")
  (mongo/remove-many :organizations {})
  (mongo/remove-many :applications {}))

(mongo/with-db db-name
  (let [krysp-url (str (server-address) "/dev/krysp")
        organizations (map (fn [org] (update-in org [:krysp] #(assoc-in % [:R :url] krysp-url))) minimal/organizations)]
    (dorun (map (partial mongo/insert :organizations) organizations))))

(facts "Automatic checking for verdicts"
  (mongo/with-db db-name
    (let [

          application-submitted         (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "Paatoskuja 17")
          application-id-submitted      (:id application-submitted)

          application-sent              (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "Paatoskuja 18")
          application-id-sent           (:id application-sent)

          application-verdict-given     (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "Paatoskuja 19")
          application-id-verdict-given  (:id application-verdict-given)]

      (local-command sonja :approve-application :id application-id-sent :lang "fi") => ok?
      (local-command sonja :approve-application :id application-id-verdict-given :lang "fi") => ok?
      (give-local-verdict sonja application-id-verdict-given :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?

      (let [application-submitted (query-application local-query sonja application-id-submitted) => truthy
            application-sent (query-application local-query sonja application-id-sent) => truthy
            application-verdict-given (query-application local-query sonja application-id-verdict-given) => truthy]
        (:state application-submitted) => "submitted"
        (:state application-sent) => "sent"
        (:state application-verdict-given) => "verdictGiven")

      (dummy-email-server/messages :reset true) ; Inbox zero

      (fact "checking verdicts and sending emails to the authorities related to the applications"
        (count (batchrun/fetch-verdicts)) => pos?)

       (fact "Verifying the sent emails"
        (Thread/sleep 100) ; batchrun includes a parallel operation
        (let [emails (dummy-email-server/messages :reset true)]
          (fact "email count" (count emails) => 1)
          (let [email (last emails)]
            (fact "email check"
              (:to email) => (contains (email-for-key sonja))
              (:subject email) => "Lupapiste: Paatoskuja 18 - p\u00e4\u00e4t\u00f6s"
              email => (partial contains-application-link-with-tab? application-id-sent "verdict" "authority")
              (get-in email [:body :plain]) => (contains "Hakemukseesi on annettu p\u00e4\u00e4t\u00f6s")))))

      (let [application-submitted (query-application local-query sonja application-id-submitted) => truthy
            application-sent (query-application local-query sonja application-id-sent) => truthy
            application-verdict-given (query-application local-query sonja application-id-verdict-given) => truthy]
        (:state application-submitted) => "submitted"
        (:state application-sent) => "verdictGiven"
        (:state application-verdict-given) => "verdictGiven"

        (fact "state history"
          (-> application-sent :history last :state) => "verdictGiven"
          (-> application-verdict-given :history last :state) => "verdictGiven")))))


(testable-privates lupapalvelu.tasks-api task-is-review?)

(facts "Automatic checking for reviews"
  (mongo/with-db db-name
    (let [application-submitted        (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "Katselmuskuja 17")
          application-id-submitted     (:id application-submitted)
          application-verdict-given    (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "Katselmuskuja 18")
          application-id-verdict-given (:id application-verdict-given)
          has-empty-tasks #(some empty? (:tasks %))
          ]

      (facts "Initial state of reviews before krysp reading is sane"
        (local-command sonja :approve-application :id application-id-verdict-given :lang "fi") => ok?
        (give-local-verdict sonja application-id-verdict-given :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?
        (println "address & id for verdict-given" (:address application-verdict-given) (:id application-verdict-given))
        (println "address & id for submitted" (:address application-verdict-given) (:id application-submitted))
        (let [application-submitted (query-application local-query sonja application-id-submitted) => truthy
              application-verdict-given (query-application local-query sonja application-id-verdict-given) => truthy]

          (has-empty-tasks application-submitted) => false
          (has-empty-tasks application-verdict-given) => false

          (:state application-submitted) => "submitted"
          (:state application-verdict-given) => "verdictGiven")
        (count (:tasks application-verdict-given)) => 0)


      ;; (facts "Initial state of reviews before krysp reading is sane (minimized for debugging schema-info compile error)"
      ;;   (local-command sonja :approve-application :id application-id-verdict-given :lang "fi") => ok?
      ;;   (give-local-verdict sonja application-id-verdict-given :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?
      ;;   (count (:tasks application-verdict-given)) => 0)


      (against-background [(app-from-krysp/get-application-xml-by-application-id anything) => (sade.xml/parse-string
                                                                                               (slurp "resources/krysp/dev/r-verdict-review.xml")
                                                                                               ;;(slurp "dev-resources/krysp/verdict-r-buildings.xml")
                                                                                               "utf-8")]
        (fact "checking for reviews in correct states"

          (count (batchrun/poll-verdicts-for-reviews)) => pos?
          (println "batchrun returned happily")
          (let [query-tasks (fn [application-id] (:tasks (query-application local-query sonja application-id)))
                count-reviews #(count
                                (filter task-is-review? (query-tasks %)))]
            ;; (println "count-reviews after poll-verdicts-for-reviews" (count-reviews application-id-verdict-given))
            (println "task-count by count :tasks is" (count (query-tasks application-id-verdict-given)))
            (count-reviews application-id-verdict-given) => 10
            (count-reviews application-id-submitted) => 0)))



      (fact "existing tasks are (not) preserved"
        ;; tbd. check state vs before running poll, now just checking vs after running c-f-v
        ;; calling check-for-verdict results in query-application returning 9 tasks (of which 3 reviews).
        ;; otherwise there are 10 tasks, all of which are reviews.
        (has-empty-tasks (query-application local-query sonja application-id-verdict-given)) => nil
        (count  (:tasks (query-application local-query sonja application-id-verdict-given))) => 10
        (println "query")
        (local-command sonja :check-for-verdict :id application-id-verdict-given) => anything
        (has-empty-tasks (query-application local-query sonja application-id-verdict-given)) => nil
        (count  (:tasks (query-application local-query sonja application-id-verdict-given))) =not=> 10))))
