(ns lupapalvelu.autom-check-verdicts-itest
  (:require [lupapalvelu.batchrun.fetch-verdict-consumer :refer [create-fetch-verdict-consumers!]]
            [lupapalvelu.factlet :refer [fact* facts*]]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.integrations-api]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [lupapalvelu.verdict-api]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.core :refer [now fail]]
            [sade.dummy-email-server :as dummy-email-server]
            [sade.env :as env]
            [sade.shared-util :as util]
            [taoensso.timbre :refer [warnf]]))

(if-not (env/feature? :jms)
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

(create-fetch-verdict-consumers! ["753-R"])

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
      (give-local-legacy-verdict sonja application-id-verdict-given)

      (let [emails (dummy-email-server/messages :reset true)
            application-submitted (query-application local-query sonja application-id-submitted) => truthy
            application-sent (query-application local-query sonja application-id-sent) => truthy
            application-verdict-given (query-application local-query sonja application-id-verdict-given) => truthy]
        (fact "Six emails (3x submitted, 2x approved, 1x verdict)"
          (Thread/sleep 2000)
          (count emails) => 6)
        (get (last emails) :subject) => (contains "P\u00e4\u00e4t\u00f6s annettu")
        (:state application-submitted) => "submitted"
        (:verdictDate application-submitted) => nil
        (:state application-sent) => "sent"
        (:verdictDate application-sent) => nil
        (:state application-verdict-given) => "verdictGiven"
        (:verdictDate application-verdict-given) => pos?)

      (fact "checking verdicts and sending emails to the authorities related to the applications"
        (fetch-verdicts {:jms? true :wait-ms 2000}) => nil?)
      (loop [retries 20
             state (:state (mongo/select-one :applications {:_id application-id-sent} [:state]))]
        (when-not (or (zero? retries) (util/not=as-kw state :sent))
          (Thread/sleep 1000)
          (recur (dec retries) (:state (mongo/select-one :applications {:_id application-id-sent} [:state])))))
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
        (:verdictDate application-submitted) => nil
        (:state application-sent) => "verdictGiven"
        (:verdictDate application-sent) => pos?
        (:state application-verdict-given) => "verdictGiven"
        (:verdictDate application-verdict-given) => pos?

        (fact "state history"
          (-> application-sent :history last :state) => "verdictGiven"
          (-> application-verdict-given :history last :state) => "verdictGiven"))


      ;; The following tests do not go through message queue

      (fact "batchrun verdicts not checked, if organization doesn't have url"
        (fetch-verdicts) => nil?
        (provided
          (mongo/select-ordered :applications anything anything) => [{:id "FOO-42", :permitType "foo", :organization "bar"}]
          (mongo/select :organizations anything anything) => [{:id "bar"}]
          (lupapalvelu.verdict/do-check-for-verdict irrelevant) => irrelevant :times 0
          (lupapalvelu.logging/log-event :error irrelevant) => irrelevant :times 0
          (lupapalvelu.logging/log-event :info anything) => nil))

      (fact "batchrun check-for-verdicts logs :error on exception"
        ;; make sure logging functions are called in expected ways
        (fetch-verdicts) => nil?
        (provided
          (mongo/select-ordered :applications anything anything) => [{:id "FOO-42", :permitType "foo", :organization "bar"}]
          (mongo/select :organizations anything anything) => [{:id "bar" :krysp {:foo {:url "http://test"}}}]
          (lupapalvelu.verdict/do-check-for-verdict anything) =throws=> (IllegalArgumentException.)
          (lupapalvelu.logging/log-event :error anything) => nil
          (lupapalvelu.logging/log-event :info anything) => nil))

      (fact "batchrun check-for-verdicts logs failure details"
        ;; make sure logging functions are called in expected ways
        (fetch-verdicts) => anything
        (provided
          (mongo/select-ordered :applications anything anything) => [{:id "FOO-42", :permitType "foo", :organization "bar"}]
          (mongo/select :organizations anything anything) => [{:id "bar" :krysp {:foo {:url "http://test"}}}]
          (lupapalvelu.logging/log-event :error anything) => nil
          (lupapalvelu.logging/log-event :info irrelevant) => irrelevant
          (lupapalvelu.verdict/do-check-for-verdict anything) => (fail :bar))))))))
