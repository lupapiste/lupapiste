(ns lupapalvelu.autom-check-verdicts-itest
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [taoensso.timbre :refer [warnf]]
            [lupapalvelu.batchrun :as batchrun]
            [lupapalvelu.batchrun.fetch-verdict]
            [lupapalvelu.factlet :refer [fact* facts*]]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.integrations-api]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.verdict-api]
            [lupapalvelu.xml.krysp.application-from-krysp :as app-from-krysp]
            [sade.core :refer [now fail]]
            [sade.env :as env]
            [sade.dummy-email-server :as dummy-email-server]))

(if-not (and (env/feature? :embedded-artemis) (env/feature? :jms))
(warnf "JMS not enabled for unit testing")

(do
(defonce db-name (str "test_autom-check-verdicts-itest_" (now)))

(mongo/connect!)
(mongo/with-db db-name
  (fixture/apply-fixture "minimal")
  (mongo/remove-many :organizations {})
  (mongo/remove-many :applications {}))

(mongo/with-db db-name
  (let [krysp-url (str (server-address) "/dev/krysp")
        organizations (map (fn [org] (update-in org [:krysp] #(assoc-in % [:R :url] krysp-url))) minimal/organizations)]
    (dorun (map (partial mongo/insert :organizations) organizations))))

(dummy-email-server/messages :reset true) ; Inbox zero

(facts "Automatic checking for verdicts"
  (mongo/with-db db-name
    (let [application-submitted         (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "Paatoskuja 17")
          application-id-submitted      (:id application-submitted)

          application-sent              (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "Paatoskuja 18")
          application-id-sent           (:id application-sent)

          application-verdict-given     (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "Paatoskuja 19")
          application-id-verdict-given  (:id application-verdict-given)]

      (local-command sonja :update-app-bulletin-op-description :id application-id-sent :description "otsikko julkipanoon") => ok?
      (local-command sonja :update-app-bulletin-op-description :id application-id-verdict-given :description "otsikko julkipanoon") => ok?

      (local-command sonja :approve-application :id application-id-sent :lang "fi") => ok?
      (local-command sonja :approve-application :id application-id-verdict-given :lang "fi") => ok?
      (give-local-verdict sonja application-id-verdict-given :verdictId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124) => ok?
      (Thread/sleep 100)                                    ; Wait for emails

      (let [emails (dummy-email-server/messages :reset true)
            application-submitted (query-application local-query sonja application-id-submitted) => truthy
            application-sent (query-application local-query sonja application-id-sent) => truthy
            application-verdict-given (query-application local-query sonja application-id-verdict-given) => truthy]
        (fact "Six emails (3x submitted, 2x approved, 1x verdict)" (count emails) => 6)
        (get (last emails) :subject) => (contains "P\u00e4\u00e4t\u00f6s annettu")
        (:state application-submitted) => "submitted"
        (:state application-sent) => "sent"
        (:state application-verdict-given) => "verdictGiven")

      (fact "checking verdicts and sending emails to the authorities related to the applications"
        (fetch-verdicts {:jms? true :wait-ms 2000}) => nil?)
      (fact "Verifying the sent emails"
        (Thread/sleep 500) ; batchrun includes a parallel operation
        (let [emails (dummy-email-server/messages :reset true)]
          (fact "email count" (count emails) => 1)
          (let [email (last emails)]
            (fact "email check"
              (:to email) => (contains (email-for-key sonja))
              (:subject email) => "Lupapiste: Paatoskuja 18, Sipoo - hankkeen tila on nyt P\u00e4\u00e4t\u00f6s annettu"
              email => (partial contains-application-link-with-tab? application-id-sent "verdict" "authority")
              (get-in email [:body :plain]) => (contains "tila on nyt P\u00e4\u00e4t\u00f6s annettu")))))

      (let [application-submitted (query-application local-query sonja application-id-submitted) => truthy
            application-sent (query-application local-query sonja application-id-sent) => truthy
            application-verdict-given (query-application local-query sonja application-id-verdict-given) => truthy]
        (:state application-submitted) => "submitted"
        (:state application-sent) => "verdictGiven"
        (:state application-verdict-given) => "verdictGiven"

        (fact "state history"
          (-> application-sent :history last :state) => "verdictGiven"
          (-> application-verdict-given :history last :state) => "verdictGiven"))

      (fact "batchrun verdicts not checked, if organization doesn't have url"
        (fetch-verdicts {:jms? true :wait-ms 2000}) => nil?
        (provided
          (mongo/select :applications anything) => [{:id "FOO-42", :permitType "foo", :organization "bar"}]
          (mongo/select :organizations anything anything) => [{:id "bar"}]
          (lupapalvelu.verdict/do-check-for-verdict irrelevant) => irrelevant :times 0
          (lupapalvelu.logging/log-event :error irrelevant) => irrelevant :times 0
          (lupapalvelu.logging/log-event :info anything) => nil))

      (fact "batchrun check-for-verdicts logs :error on exception"
        ;; make sure logging functions are called in expected ways
        (fetch-verdicts {:jms? true :wait-ms 2000}) => nil?
        (provided
          (mongo/select :applications anything) => [{:id "FOO-42", :permitType "foo", :organization "bar"}]
          (mongo/select :organizations anything anything) => [{:id "bar" :krysp {:foo {:url "http://test"}}}]
          (lupapalvelu.verdict/do-check-for-verdict anything) =throws=> (IllegalArgumentException.)
          (lupapalvelu.logging/log-event :error anything) => nil
          (lupapalvelu.logging/log-event :info anything) => nil))

      (fact "batchrun check-for-verdicts logs failure details"
        ;; make sure logging functions are called in expected ways
        (fetch-verdicts {:jms? true :wait-ms 2000}) => anything
        (provided
          (mongo/select :applications anything) => [{:id "FOO-42", :permitType "foo", :organization "bar"}]
          (mongo/select :organizations anything anything) => [{:id "bar" :krysp {:foo {:url "http://test"}}}]
          (lupapalvelu.logging/log-event :error anything) => nil
          (lupapalvelu.logging/log-event :info irrelevant) => irrelevant
          (lupapalvelu.verdict/do-check-for-verdict anything) => (fail :bar))))))
)
)
