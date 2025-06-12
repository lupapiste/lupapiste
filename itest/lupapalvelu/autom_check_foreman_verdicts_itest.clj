(ns lupapalvelu.autom-check-foreman-verdicts-itest
  (:require [lupapalvelu.domain :as domain]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.integrations-api]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.json :as json]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [lupapalvelu.system.util-components :as components]
            [lupapalvelu.verdict-api]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [sade.dummy-email-server :as dummy-email-server]
            [sade.env :as env]
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

    (facts "Automatic checking for foreman verdicts"
        (let [application-verdict-given    (create-and-submit-local-application sonja :propertyId sipoo-property-id :address "Paatoskuja 20")
              application-id-verdict-given (:id application-verdict-given)]

          (give-local-legacy-verdict sonja application-id-verdict-given :R true)

          (env/with-feature-value :new-foreman-invite true
            (facts "create-and-invite-foreman"
              (let [_           (fact (local-command sonja :create-and-invite-foreman-by-id :id application-id-verdict-given :foremanUser sonja-id :foremanRole "KVV-työnjohtaja") => ok?)
                    app         (query-application local-query sonja application-id-verdict-given)
                    foreman-doc (domain/get-document-by-name app "tyonjohtaja-v3")]
                (local-command sipoo :set-foreman-submit-setting :organizationId "753-R" :path {:application {:approve true}} :enabled true) => ok?
                (local-command sonja :set-user-to-document :id application-id-verdict-given :documentId (:id foreman-doc) :userId sonja-id :path "") => ok?
                (local-command sonja :update-doc :id application-id-verdict-given :doc (:id foreman-doc) :updates (default-foreman-updates)) => ok?
                (local-command sonja :foreman-consent :id application-id-verdict-given :foreman-doc-id (:id foreman-doc) :consent true) => ok?
                (local-command sonja :submit-foreman :id application-id-verdict-given :foreman-doc-id (:id foreman-doc) :with-lang "fi") => ok?)))

          (let [application-verdict-given (query-application local-query sonja application-id-verdict-given)
                foreman-doc               (domain/get-document-by-name application-verdict-given "tyonjohtaja-v3")]
            application-verdict-given => truthy

            (fact "foreman state submitted"
              (-> foreman-doc :state) => "submitted")

            (local-command sonja :approve-foreman :id application-id-verdict-given :foreman-doc-id (:id foreman-doc) :with-lang "fi"))

          (let [application-verdict-given (query-application local-query sonja application-id-verdict-given)
                foreman-doc               (domain/get-document-by-name application-verdict-given "tyonjohtaja-v3")]
            application-verdict-given => truthy
            (fact "foreman state approved"
              (-> foreman-doc :state) => "approved"))

          (fact "No verdict, no changes"
            (with-local-actions ;; Needed for override commands
              (let [old-app (mongo/by-id :applications application-id-verdict-given)
                    url     (str (server-address) "/dev/krysp")]
                (:verdicts old-app) => empty?
                (mongo/update-by-id :organizations "753-R"
                                    {$set {:krysp.R.url (str url "?overrides="
                                                             (json/encode [{:selector [:rakval:paatostieto]
                                                                            :value    nil}]))}})
                (fetch-foreman-verdicts {:use-queue? false :wait-ms 3000})
                (mongo/by-id :applications application-id-verdict-given) => old-app
                (mongo/update-by-id :organizations "753-R"
                                    {$set {:krysp.R.url url}}))))

          (fact "fetching foreman verdicts"
            (fetch-foreman-verdicts {:use-queue? true :wait-ms 3000}) => nil?
            (loop [retries     20
                   foreman-doc (-> (mongo/select-one :applications {:_id application-id-verdict-given} [:documents])
                                   (domain/get-document-by-name "tyonjohtaja-v3"))]
              (if (zero? retries)
                :fail
                (when-not (or (zero? retries) (util/not=as-kw (:state foreman-doc) :declared))
                  (sleep-and-log 1000 "waiting for foreman verdicts")
                  (recur (dec retries) (-> (mongo/select-one :applications {:_id application-id-verdict-given} [:documents])
                                           (domain/get-document-by-name "tyonjohtaja-v3")))))) => #(not= :fail %)

            (let [{:keys [documents verdicts]} (query-application local-query sonja application-id-verdict-given)
                  tj-doc                       (domain/get-document-by-name documents "tyonjohtaja-v3")]
              (fact "foreman state acknowledged"
                (:state tj-doc) => "acknowledged")
              (fact "Verdict created"
                verdicts => (just [(just {:document-id     (:id tj-doc)
                                          :id              truthy
                                          :kuntalupatunnus "25-0006-TJO"
                                          :paatokset       (just [(just {:id          truthy
                                                                         :poytakirjat [{:paatoksentekija "Työnjohtajailmoitus"
                                                                                        :paatoskoodi     "myönnetty"
                                                                                        :paatospvm       1748293200000
                                                                                        :status          "1"}]})])
                                          :timestamp       truthy
                                      :vrk-lupatunnus  "83700025-0006"})])))))))
