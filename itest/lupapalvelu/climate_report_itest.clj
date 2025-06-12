(ns lupapalvelu.climate-report-itest
  (:require [clojure.test :refer :all]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-legacy-itest-util :refer [give-legacy-verdict]]
            [sade.core :refer [now]]
            [sade.env :as env]))

(use-fixtures :once
  (fn [run-tests]
    (with-test-system (-> {:fixture "minimal"}
                          test-system/with-remote-components
                          test-system/with-mock-laundry)
      (env/with-feature-value :building-product-catalog true
        (run-tests)))))

(defn- query-climate-reports [app-id]
  (-> (query-application sonja app-id)
      :climate-reports))

(deftest climate-reports-test
  (testing "Climate reports not created without ryhti-data-2026 set"
    (let [app       (create-and-submit-application mikko :operation "kerrostalo-rivitalo")
          app-id    (:id app)
          op-doc-id (:id (domain/get-document-by-name app "uusiRakennuskohde"))
          _         (is (nil? op-doc-id)
                        "uusiRakennuskohde not found")]
      (is (= (count (query-climate-reports app-id)) 0)
          "There should be 0 climate reports before verdict is given")
      (is (some? (give-legacy-verdict sonja app-id :R true)))
      (is (= (count (query-climate-reports app-id)) 0)
          "There should be 0 climate reports after verdict is given")))

  (is (ok? (command admin :set-organization-ryhti-data-2026-start-ts :organizationId "753-R" :ryhti-data-2026-start-ts (now)))
      "ryhti-data-2026 is set for organization")
  (testing "Climate reports will be generated when all prerequisites are met"
    (let [app       (create-and-submit-application mikko :operation "kerrostalo-rivitalo")
          app-id    (:id app)
          op-doc-id (:id (domain/get-document-by-name app "uusiRakennuskohde"))
          _         (is (some? op-doc-id)
                        "uusiRakennuskohde found")]

      ;; Climate reports should only be generated for buildings (kohteen-laji = "rakennus") that have some value set for
      ;; buildingPurposeForLowCarbonAssessment (rakennusluokka-vahahiilisyys)
      (is (ok? (command sonja :update-doc :id app-id :doc op-doc-id
                        :updates [["kaytto.kohteen-laji" "rakennus"]
                                  ["rakennusluokka-vahahiilisyys.rakennusluokka-vahahiilisyys", "0110 omakotitalot"]]
                        :collection "documents"))
          "Should update the required fields for climate reports to be generated")

      (is (= (count (query-climate-reports app-id)) 0)
          "There should be 0 climate reports before verdict is given")
      (is (some? (give-legacy-verdict sonja app-id :R true)))

      (let [climate-reports (query-climate-reports app-id)]
        (is (= (count climate-reports) 2)
            "There should be 2 climate reports after verdict is given")
        (testing "Climate report has correct structure"
          (let [climate-report (first climate-reports)]
            (is (some? (:id climate-report)))
            (is (some? (:created climate-report)))
            (is (some? (:schema-info climate-report)))
            (is (some? (:building-ref climate-report)))
            (is (= "climate-report" (:type climate-report)))))
        (testing "Climate reports are linked to correct documents"
          (let [location-doc  (:id (domain/get-document-by-name app "rakennuspaikka"))
                building-refs (set (map :building-ref climate-reports))]
            (is (contains? building-refs op-doc-id)
                "One climate report should reference the building doc")
            (is (contains? building-refs location-doc)
                "One climate reports should reference the location doc")))
        (testing "Climate reports can be updated"
          (let [climate-report (first climate-reports)]
            (is (ok? (command sonja :update-climate-report :id app-id
                              :doc (:id climate-report)
                              :updates [["laatimistiedot.laatija" "Ilmari Ilmastoselvittäjä"]
                                        ["laatimistiedot.pvm" "14.02.2026"]
                                        ["laatimistiedot.arvioinnissa-kaytetty-ohjelmisto" "MS-Climate-Reports"]]))))))))

  (testing "Climate reports will be generated for each building document that has rakennusluokka-vahahiilisyys set"
    (let [app           (create-and-submit-application mikko :operation "kerrostalo-rivitalo")
          app-id        (:id app)
          _             (command sonja :add-operation :id app-id :operation "kerrostalo-rivitalo")
          _             (command sonja :add-operation :id app-id :operation "pientalo")
          _             (command sonja :add-operation :id app-id :operation "pientalo")
          building-docs (domain/get-documents-by-name (query-application sonja app-id) "uusiRakennuskohde")]

      (is (= (count building-docs) 4)
          "there should be 4 building docs, one for each operation")

      ;; Set rakennusluokka-vahahiilisyys for all but one document
      (doseq [doc (drop-last building-docs)]
        (is (ok? (command sonja :update-doc :id app-id :doc (:id doc)
                          :updates [["kaytto.kohteen-laji" "rakennus"]
                                    ["rakennusluokka-vahahiilisyys.rakennusluokka-vahahiilisyys", "0110 omakotitalot"]]
                          :collection "documents"))
            "Should update the required fields for climate reports to be generated"))

      (is (= (count (query-climate-reports app-id)) 0)
          "There should be 0 climate reports before verdict is given")
      (is (some? (give-legacy-verdict sonja app-id :R true)))
      (is (= (count (query-climate-reports app-id)) 4)
          "There should be 4 climate reports after the verdict is given, one for the location and one for the 3 building-docs that have rakennusluokka-vahahiilisyys set")))

  (testing "Climate reports not created without rakennusluokka-vahahiilisyys"
    (let [app       (create-and-submit-application mikko :operation "kerrostalo-rivitalo")
          app-id    (:id app)
          op-doc-id (:id (domain/get-document-by-name app "uusiRakennuskohde"))
          _         (is (some? op-doc-id)
                        "uusiRakennuskohde found")]
      (is (ok? (command sonja :update-doc :id app-id :doc op-doc-id
                        :updates [["kaytto.kohteen-laji" "rakennus"]]
                        :collection "documents"))
          "Should update the kohteen-laji")
      (is (= (count (query-climate-reports app-id)) 0)
          "There should be 0 climate reports before verdict is given")
      (is (some? (give-legacy-verdict sonja app-id :R true)))
      (is (= (count (query-climate-reports app-id)) 0)
          "There should be 0 climate reports after verdict is given"))))
