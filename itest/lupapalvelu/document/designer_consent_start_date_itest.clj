(ns lupapalvelu.document.designer-consent-start-date-itest
  (:require [clojure.test :refer :all]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [sade.date :as date]
            [sade.env :as env]
            [sade.util :as util]))

(use-fixtures :once (local-fixture-factory (-> {:db-name "test_designer-consent-start-date-test"}
                                               test-system/with-remote-components)))

(deftest designer-consent-start-date-test
  (let [{app-id :id}    (create-and-submit-local-application pena :address "Designer Detour")
        {doc-id :doc}   (command pena :create-doc :id app-id :schemaName "suunnittelija")
        {blank-id :doc} (command pena :create-doc :id app-id :schemaName "suunnittelija")]
    (testing "Fill design document"
      (is (:ok (command pena :set-user-to-document :id app-id :documentId doc-id
                        :userId pena-id :path ""))
          "Set Pena as designer")
      (is (:ok (command pena :update-doc :id app-id :doc doc-id
                        :updates [["patevyys.koulutusvalinta" "arkkitehti"]
                                  ["patevyys.patevyysluokka" "AA"]
                                  ["patevyys.valmistumisvuosi" "2000"]
                                  ["kuntaRoolikoodi" "rakennesuunnittelija"]
                                  ["suunnittelutehtavanVaativuusluokka" "B"]]))))
    (is (:ok (command sonja :check-for-verdict :id app-id))
        "Check for verdict")

    (with-redefs [env/get-config (constantly {:consent {:start-date "2.11.2025"}})]
      (testing "Consent start date applies to the application."
        (testing "Consent is required for approval"
          (is (= {:reason :error.designer.consent-missing
                  :ok     false
                  :text   "error.designer.consent-missing"}
                 (command sonja :approve-doc :id app-id :doc doc-id
                          :collection "documents" :path ""))))
        (testing "Blank designer document cannot be approved"
          (is (= {:reason :error.missing-parameters
                  :ok     false
                  :text   "error.missing-parameters"}
                 (command sonja :approve-doc :id app-id :doc blank-id
                          :collection "documents" :path ""))))))

    (testing "Consent start date does not apply to the application."
      (doseq [cfg [{} {:consent {:start-date (date/finnish-date (date/plus (date/today) :day))}}]]
        (with-redefs [env/get-config (constantly cfg)]
          (let [{:keys [documents]} (query-application sonja app-id)
                approve-doc?        (util/fn-> (util/find-by-id documents) :allowedActions
                                         :approve-doc :ok boolean)]
            (is (approve-doc? doc-id) "Consent is not required for approval")
            (is (approve-doc? blank-id) "Blank designer document can be approved")))))

    (testing "Approved designer documents cannot be edited after verdict."
      (with-redefs [env/get-config (constantly {})]
        (let [{new-id :doc} (command pena :create-doc :id app-id :schemaName "suunnittelija")
              update-doc?   (fn [apikey doc-id]
                             (->>  (query-application apikey app-id)
                                   :documents
                                   (util/find-by-id doc-id)
                                   :allowedActions
                                   :update-doc :ok boolean))]
          (testing "Pre-verdict neutral documents cannot be updated"
            (doseq [doc-id [doc-id blank-id]
                    apikey [pena sonja]]
              (is (false? (update-doc? apikey doc-id)))))

          (testing "Post-verdict document can be updated"
            (is (true? (update-doc? pena new-id)))
            (is (true? (update-doc? sonja new-id))))

          (testing "Approved documents cannot be edited"
            (doseq [doc-id [doc-id blank-id new-id]]
              (is (:ok (command sonja :approve-doc :id app-id :doc doc-id
                                :collection "documents" :path "")))
              (is (false? (update-doc? pena doc-id)))
              (is (false? (update-doc? sonja doc-id)))))

          (testing "Rejected documents can be edited"
            (doseq [doc-id [doc-id blank-id new-id]]
              (is (:ok (command sonja :reject-doc :id app-id :doc doc-id
                                :collection "documents" :path "")))
              (is (true? (update-doc? pena doc-id)))
              (is (true? (update-doc? sonja doc-id))))))))))
