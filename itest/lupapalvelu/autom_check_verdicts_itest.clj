(ns lupapalvelu.autom-check-verdicts-itest
  (:require [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.integrations-api]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.logging :as log]
            [lupapalvelu.minimal-util :as min-util]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [lupapalvelu.system.util-components :as components]
            [lupapalvelu.verdict :as verdict]
            [lupapalvelu.verdict-api]
            [midje.sweet :refer :all]
            [sade.core :refer [fail]]
            [sade.dummy-email-server :as dummy-email-server]
            [sade.util :as util]))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components
                      test-system/with-mock-laundry
                      (test-system/with-pubsub-publisher :batchrun/fetch-verdicts)
                      (test-system/with-pubsub-subscriber :batchrun/fetch-verdicts))
  (mongo/remove-many :organizations {})
  (mongo/remove-many :applications {})

  (let [krysp-url     (str (server-address) "/dev/krysp")
        organizations (map (fn [org] (update-in org [:krysp] #(assoc-in % [:R :url] krysp-url))) (minimal/organizations {:http-port (-> (test-system/get-common-components)
                                                                                                                                        components/system->http-port)}))]
    (dorun (map (partial mongo/insert :organizations) organizations)))

  (dummy-email-server/messages :reset true) ; Inbox zero

  (facts "Automatic checking for verdicts"
    (let [application-submitted        (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "Paatoskuja 17")
          application-id-submitted     (:id application-submitted)

          application-sent             (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "Paatoskuja 18")
          application-id-sent          (:id application-sent)

          application-verdict-given    (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "Paatoskuja 19")
          application-id-verdict-given (:id application-verdict-given)]

      (local-command sonja :update-app-bulletin-op-description :id application-id-sent :description "otsikko julkipanoon") => ok?
      (local-command sonja :update-app-bulletin-op-description :id application-id-verdict-given :description "otsikko julkipanoon") => ok?

      (local-command sonja :approve-application :id application-id-sent :lang "fi") => ok?
      (local-command sonja :approve-application :id application-id-verdict-given :lang "fi") => ok?
      (give-local-legacy-verdict sonja application-id-verdict-given :R true)

      (let [emails                    (dummy-email-server/messages :reset true)
            application-submitted     (query-application local-query sonja application-id-submitted)
            application-sent          (query-application local-query sonja application-id-sent)
            application-verdict-given (query-application local-query sonja application-id-verdict-given)]
        (fact "queries ok"
          application-submitted => truthy
          application-sent => truthy
          application-verdict-given => truthy)
        (fact "Six emails (3x submitted, 2x approved, 1x verdict)"
          (sleep-and-log 2000 "waiting for emails")
          (count emails) => 6)
        (get (last emails) :subject) => (contains "P\u00e4\u00e4t\u00f6s annettu")
        (:state application-submitted) => "submitted"
        (:verdictDate application-submitted) => nil
        (:state application-sent) => "sent"
        (:verdictDate application-sent) => nil
        (:state application-verdict-given) => "verdictGiven"
        (:verdictDate application-verdict-given) => pos?)

      (fact "checking verdicts and sending emails to the authorities related to the applications"
        (fetch-verdicts {:use-queue? true :wait-ms 3000}) => nil?
        (loop [retries 20
               state   (:state (mongo/select-one :applications {:_id application-id-sent} [:state]))]
          (if (zero? retries)
            :fail
            (when-not (or (zero? retries) (util/not=as-kw state :sent))
              (sleep-and-log 1000 "waiting for verdicts")
              (recur (dec retries) (:state (mongo/select-one :applications {:_id application-id-sent} [:state])))))) => #(not= :fail %))

      (fact "Verifying the sent emails"
        (sleep-and-log 500 "batchrun includes a parallel operation")
        (let [emails (dummy-email-server/messages :reset true)]
          (fact "email count" (count emails) => 1)
          (let [email (last emails)]
            (fact "email check"
              (:to email) => (contains (min-util/email-for-key sonja))
              (:subject email) => "Lupapiste: Paatoskuja 18, Sipoo - hankkeen tila on nyt P\u00e4\u00e4t\u00f6s annettu"
              email => (partial contains-application-link-with-tab? application-id-sent "verdict" "authority")
              (get-in email [:body :plain]) => (contains "tila on nyt P\u00e4\u00e4t\u00f6s annettu")))))

      (let [application-submitted     (query-application local-query sonja application-id-submitted)
            application-sent          (query-application local-query sonja application-id-sent)
            application-verdict-given (query-application local-query sonja application-id-verdict-given)]
        (fact "queries ok"
          application-submitted => truthy
          application-sent => truthy
          application-verdict-given => truthy)
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
          (verdict/do-check-for-verdict irrelevant anything) => irrelevant :times 0
          (log/log-event :warning irrelevant) => irrelevant :times 0
          (log/log-event :info anything) => nil))

      (fact "batchrun check-for-verdicts logs :error on exception"
        ;; make sure logging functions are called in expected ways
        (fetch-verdicts) => nil?
        (provided
          (mongo/select-ordered :applications anything anything) => [{:id "FOO-42", :permitType "foo", :organization "bar"}]
          (mongo/select :organizations anything anything) => [{:id "bar" :krysp {:foo {:url "http://test"}}}]
          (verdict/do-check-for-verdict anything anything) =throws=> (IllegalArgumentException.)
          (log/log-event :error anything) => nil
          (log/log-event :info anything) => nil))

      (fact "batchrun check-for-verdicts logs failure details"
        ;; make sure logging functions are called in expected ways
        (fetch-verdicts) => anything
        (provided
          (mongo/select-ordered :applications anything anything) => [{:id "FOO-42", :permitType "foo", :organization "bar"}]
          (mongo/select :organizations anything anything) => [{:id "bar" :krysp {:foo {:url "http://test"}}}]
          (log/log-event :error irrelevant) => nil :times 0
          (log/log-event :warning anything) => nil
          (log/log-event :info irrelevant) => irrelevant
          (verdict/do-check-for-verdict anything anything) => (fail :bar))))))
