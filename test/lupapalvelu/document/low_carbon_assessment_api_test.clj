(ns lupapalvelu.document.low-carbon-assessment-api-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.document.low-carbon-assessment-api :as low-carbon-api]))

;; Test data helpers

(defn- make-document
  "Helper to create a document with specified fields"
  [kohteen-laji rakennusluokka-vahahiilisyys]
  {:data {:kaytto                       {:kohteen-laji {:value kohteen-laji}}
          :rakennusluokka-vahahiilisyys {:rakennusluokka-vahahiilisyys {:value rakennusluokka-vahahiilisyys}}}})

(defn- make-ryhti-application
  "Create a ryhti-2026 application with given documents"
  [documents]
  {:application {:ryhti-data-2026 true
                 :documents       documents}})

(defn- make-pre-ryhti-application
  "Create a pre-ryhti application with given building-product-catalogs"
  [building-product-catalogs]
  {:application {:ryhti-data-2026           false
                 :building-product-catalogs building-product-catalogs}})

;; Tests for ryhti-2026 applications

(deftest low-carbon-assessment-enabled-ryhti-2026-test
  (testing "ryhti-2026 applications"

    (testing "passes when document has both kohteen-laji='rakennus' and rakennusluokka-vahahiilisyys"
      (let [command (make-ryhti-application
                      [(make-document "rakennus" "0110 omakotitalot")])]
        (is (nil? (low-carbon-api/low-carbon-assessment-enabled command))
            "Should pass validation")))

    (testing "passes when multiple documents exist and at least one is valid"
      (let [command (make-ryhti-application
                      [(make-document "muu" nil)
                       (make-document "rakennus" "0110 omakotitalot")
                       (make-document "rakennus" nil)])]
        (is (nil? (low-carbon-api/low-carbon-assessment-enabled command))
            "Should pass when at least one document has both conditions")))

    (testing "fails when document has kohteen-laji='rakennus' but no rakennusluokka-vahahiilisyys"
      (let [command (make-ryhti-application
                      [(make-document "rakennus" nil)])
            result (low-carbon-api/low-carbon-assessment-enabled command)]
        (is (= result
               {:ok   false
                :text "error.no-building-document-for-low-carbon-assessment"}))))

    (testing "fails when document has kohteen-laji='rakennus' but blank rakennusluokka-vahahiilisyys"
      (let [command (make-ryhti-application
                      [(make-document "rakennus" "")])
            result (low-carbon-api/low-carbon-assessment-enabled command)]
        (is (= result
               {:ok   false
                :text "error.no-building-document-for-low-carbon-assessment"}))))

    (testing "fails when document has rakennusluokka-vahahiilisyys but kohteen-laji is not 'rakennus'"
      (let [command (make-ryhti-application
                      [(make-document "muu" "0110 omakotitalot")])
            result (low-carbon-api/low-carbon-assessment-enabled command)]
        (is (= result
               {:ok   false
                :text "error.no-building-document-for-low-carbon-assessment"}))))

    (testing "fails when both fields exist but on different documents"
      (let [command (make-ryhti-application
                      [(make-document "rakennus" nil)
                       (make-document "muu" "0110 omakotitalot")])
            result (low-carbon-api/low-carbon-assessment-enabled command)]
        (is (= result
               {:ok   false
                :text "error.no-building-document-for-low-carbon-assessment"}))))

    (testing "fails when no documents exist"
      (let [command (make-ryhti-application [])
            result (low-carbon-api/low-carbon-assessment-enabled command)]
        (is (= result
               {:ok   false
                :text "error.no-building-document-for-low-carbon-assessment"}))))

    (testing "fails when documents have neither condition"
      (let [command (make-ryhti-application
                      [(make-document "muu" nil)
                       (make-document "asuinrakennus" "")])
            result (low-carbon-api/low-carbon-assessment-enabled command)]
        (is (= result
               {:ok   false
                :text "error.no-building-document-for-low-carbon-assessment"}))))))

;; Tests for pre-ryhti applications

(deftest low-carbon-assessment-enabled-pre-ryhti-test
  (testing "pre-ryhti applications"

    (testing "passes when at least one building-product-catalog exists"
      (let [command (make-pre-ryhti-application
                      [{:id "catalog-1" :name "test-catalog"}])]
        (is (nil? (low-carbon-api/low-carbon-assessment-enabled command))
            "Should pass when building-product-catalogs exist")))

    (testing "passes when multiple building-product-catalogs exist"
      (let [command (make-pre-ryhti-application
                      [{:id "catalog-1" :name "test-catalog-1"}
                       {:id "catalog-2" :name "test-catalog-2"}])]
        (is (nil? (low-carbon-api/low-carbon-assessment-enabled command))
            "Should pass when multiple catalogs exist")))

    (testing "fails when building-product-catalogs is empty"
      (let [command (make-pre-ryhti-application [])
            result (low-carbon-api/low-carbon-assessment-enabled command)]
        (is (= result
               {:ok   false
                :text "error.building-product-catalogs-required-for-low-carbon-assessment"}))))

    (testing "fails when building-product-catalogs is nil"
      (let [command {:application {:ryhti-data-2026 false
                                   :building-product-catalogs nil}}
            result (low-carbon-api/low-carbon-assessment-enabled command)]
        (is (= result
               {:ok   false
                :text "error.building-product-catalogs-required-for-low-carbon-assessment"}))))))

;; Verify both application types use different logic

(deftest low-carbon-assessment-enabled-integration-test
  (testing "application type determines validation logic"

    (testing "pre-ryhti application ignores document fields"
      (let [command {:application
                     {:ryhti-data-2026           false
                      :building-product-catalogs [{:id "catalog-1"}]
                      :documents                 [(make-document "rakennus" "0110 omakotitalot")]}}]
        (is (nil? (low-carbon-api/low-carbon-assessment-enabled command))
            "Pre-ryhti should pass based on catalogs, ignoring documents")))

    (testing "ryhti-2026 application ignores building-product-catalogs"
      (let [command {:application
                     {:ryhti-data-2026           true
                      :building-product-catalogs [{:id "catalog-1"}]
                      :documents                 []}}
            result (low-carbon-api/low-carbon-assessment-enabled command)]
        (is (= result
               {:ok   false
                :text "error.no-building-document-for-low-carbon-assessment"}))))))
