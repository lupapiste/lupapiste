(ns lupapalvelu.dmcity.states-i-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]))

(use-fixtures :once (local-fixture-factory (-> {:db-name "test_dmcity-state-messages"}
                                               test-system/with-remote-components)))

(defn state-message [app-id]
  (when-let [m (mongo/select-one :integration-messages
                                 {:application.id app-id
                                  :messageType    :state-change})]
    {:from   (-> m :data :fromState :name keyword)
     :to     (-> m :data :toState :name keyword)
     :app-id (-> m :application :id)}))

(defn configure-dmcity [targets]
  (command admin :upsert-dmcity-configuration
                      :organizationId "753-R"
                      :configuration {:username "test-user"
                                      :password "test-secret"
                                      :vault    "test-vault"
                                      :url      (str (server-address) "/dev/dummy-dmcity")
                                      :targets  targets}))

(defn application-state [app-id]
  (:state (mongo/by-id :applications app-id [:state])))

(deftest dmcity-state-messages-test
  (testing "Enable dmcity integration for R and state."
    (is (ok? (configure-dmcity {:R true :state true}))))
  (let [app-id-r  (:id (create-local-app pena :operation "pientalo"
                                         :propertyId sipoo-property-id
                                         :address "Small Hut"))
        app-id-p  (:id (create-local-app pena :operation "poikkeamis"
                                         :propertyId sipoo-property-id
                                         :address "Discrepancy"))
        app-id-yi (:id (create-local-app pena :operation "meluilmoitus"
                                         :propertyId sipoo-property-id
                                         :address "Noise Notice"))
        app-id-mm (:id (create-local-app pena :operation "asemakaava"
                                         :propertyId sipoo-property-id
                                         :address "Station Formula"))]
    (testing "No state message for `open` state."
      (is (ok? (command pena :add-comment :id app-id-r
                        :roles ["applicant" "authority"]
                        :openApplication true
                        :target {:type "application"}
                        :text "Hello")))
      (is (= "open" (application-state app-id-r)))
      (is (nil? (state-message app-id-r))))

    (testing "State message for `submitted` R application."
      (is (ok? (command pena :submit-application :id app-id-r)))
      (is (= "submitted" (application-state app-id-r)))
      (is (= {:from :open :to :submitted :app-id app-id-r}
             (state-message app-id-r))))

    (testing "No state message for P application."
      (is (ok? (command pena :submit-application :id app-id-p)))
      (is (= "submitted" (application-state app-id-p)))
      (is (nil? (state-message app-id-p))))

    (testing "Enable YI target. State message for YI application."
      (is (ok? (configure-dmcity {:R true :state true :YI true})))
      (is (ok? (command pena :submit-application :id app-id-yi)))
      (is (= "submitted" (application-state app-id-yi)))
      (is (= {:from :draft :to :submitted :app-id app-id-yi}
             (state-message app-id-yi))))

    (testing "Enable MM target, but disable state. No state message for MM application."
      (is (ok? (configure-dmcity {:MM true :state false})))
      (is (ok? (command pena :submit-application :id app-id-mm)))
      (is (= "submitted" (application-state app-id-mm)))
      (is (nil? (state-message app-id-mm))))))
