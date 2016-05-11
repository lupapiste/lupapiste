(ns lupapalvelu.autom-check-reviews-itest
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer [fact* facts*]]
            [sade.core :refer [now fail]]
            [sade.coordinate :as coordinate]
            [sade.dummy-email-server :as dummy-email-server]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.integrations-api]
            [lupapalvelu.verdict-api]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.batchrun :as batchrun]
            [lupapalvelu.xml.krysp.application-from-krysp :as app-from-krysp]))

(def db-name (str "test_autom-check-reviews-itest_" (now)))

(mongo/connect!)
(mongo/with-db db-name
  (fixture/apply-fixture "minimal")
  (mongo/remove-many :organizations {})
  (mongo/remove-many :applications {}))

(mongo/with-db db-name
  (let [krysp-url (str (server-address) "/dev/krysp")
        organizations (map (fn [org] (update-in org [:krysp] #(assoc-in % [:R :url] krysp-url))) minimal/organizations)]
    (dorun (map (partial mongo/insert :organizations) organizations))))

(testable-privates lupapalvelu.tasks-api task-is-review?)

(facts "Automatic checking for reviews"
  (mongo/with-db db-name
    (against-background [(coordinate/convert anything anything anything anything) => nil
                         ]
      (let [application-submitted        (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "Katselmuskuja 17")
            application-id-submitted     (:id application-submitted)
            application-verdict-given    (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "Katselmuskuja 18")
            application-id-verdict-given (:id application-verdict-given)
            has-empty-tasks #(some empty? (:tasks %))
            ]

        (facts "Initial state of reviews before krysp reading is sane"
          (local-command sonja :approve-application :id application-id-verdict-given :lang "fi") => ok?

          (give-local-verdict sonja application-id-verdict-given :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?
          ;; (give-local-verdict sonja application-id-verdict-given :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?
          (let [application-submitted (query-application local-query sonja application-id-submitted) => truthy
                application-verdict-given (query-application local-query sonja application-id-verdict-given) => truthy]

            (has-empty-tasks application-submitted) => falsey
            (has-empty-tasks application-verdict-given) => falsey

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
            (let [query-tasks (fn [application-id] (:tasks (query-application local-query sonja application-id)))
                  count-reviews #(count
                                  (filter task-is-review? (query-tasks %)))]
              (count-reviews application-id-verdict-given) => 2
              (count-reviews application-id-submitted) => 0)))

        (against-background [(app-from-krysp/get-application-xml-by-application-id anything) => (sade.xml/parse-string (slurp "dev-resources/krysp/verdict-r-buildings.xml") "utf-8")]
          (fact "buildings"
            ;;

            ))

        (fact "existing tasks are (not) preserved"
          ;; tbd. check state vs before running poll, now just checking vs after running c-f-v
          ;; calling check-for-verdict results in query-application returning 9 tasks (of which 3 reviews).
          ;; otherwise there are 10 tasks, all of which are reviews.
          (has-empty-tasks (query-application local-query sonja application-id-verdict-given)) => nil
          (count  (:tasks (query-application local-query sonja application-id-verdict-given))) => 2
          (local-command sonja :check-for-verdict :id application-id-verdict-given :lang "fi") => anything
          (has-empty-tasks (query-application local-query sonja application-id-verdict-given)) => nil
          (count  (:tasks (query-application local-query sonja application-id-verdict-given))) =not=> 2)))))
