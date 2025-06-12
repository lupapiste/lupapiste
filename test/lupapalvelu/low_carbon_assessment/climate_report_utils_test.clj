(ns lupapalvelu.low-carbon-assessment.climate-report-utils-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.low-carbon-assessment.climate-report-utils :as climate-report-utils]))

;; Test data - wrapped format (with {:value v} structure)
(def valid-climate-report-data
  "Climate report data with all required fields filled and positive carbon footprint"
  {:data {:hiilijalanjalki {:purkujatteen-kuljetukset     {:value "5.0"}
                            :purkujatteen-kasittely       {:value "1.0"}
                            :kuljetukset                  {:value "2.0"}
                            :purkaminen                   {:value "1.5"}
                            :tyomaatoiminnot              {:value "0.5"}
                            :purkujatteen-loppusijoitus   {:value "1.0"}
                            :rakennustuotteiden-vaihdot   {:value "1.0"}
                            :rakennustuotteiden-valmistus {:value "2.0"}}
          :laatimistiedot  {:pvm                              {:value "2025-01-15"}
                            :laatija                          {:value "Ilmari Ilmastoselvittäjä"}
                            :arvioinnissa-kaytetty-ohjelmisto {:value "MS Climate Report"}}}})

(def climate-report-data-missing-laatija
  "Climate report data with missing laatija field"
  (assoc-in valid-climate-report-data [:data :laatimistiedot :laatija :value] ""))

(def climate-report-data-missing-pvm
  "Climate report data with missing pvm field"
  (assoc-in valid-climate-report-data [:data :laatimistiedot :pvm :value] nil))

(def climate-report-data-zero-footprint
  "Climate report data with zero carbon footprint"
  (assoc valid-climate-report-data :data
         (assoc (:data valid-climate-report-data)
                :hiilijalanjalki {:purkujatteen-kuljetukset     {:value "0"}
                                  :purkujatteen-kasittely       {:value "0"}
                                  :kuljetukset                  {:value "0"}
                                  :purkaminen                   {:value "0"}
                                  :tyomaatoiminnot              {:value "0"}
                                  :purkujatteen-loppusijoitus   {:value "0"}
                                  :rakennustuotteiden-vaihdot   {:value "0"}
                                  :rakennustuotteiden-valmistus {:value "0"}})))

;; Timestamps for testing
(def submitted-before-2029 1735689600000) ;; 2025-01-01
(def submitted-after-2029 1893456000000)  ;; 2030-01-01

;; Tests

(deftest carbon-footprint-test
  (testing "carbon-footprint sums all wrapped values correctly"
    (is (= 14.0 (climate-report-utils/carbon-footprint (:data valid-climate-report-data)))
        "5.0 + 1.0 + 2.0 + 1.5 + 0.5 + 1.0 + 1.0 + 2.0 = 14.0"))

  (testing "carbon-footprint handles zero values"
    (is (= 0.0 (climate-report-utils/carbon-footprint (:data climate-report-data-zero-footprint)))))

  (testing "carbon-footprint handles nil hiilijalanjalki"
    (is (= 0 (climate-report-utils/carbon-footprint {})))))

(deftest valid-climate-report-test
  (testing "valid-climate-report? returns true for complete report"
    (is (true? (climate-report-utils/valid-climate-report? valid-climate-report-data))
        "Report with footprint > 0 and all laatimistiedot filled is valid"))

  (testing "valid-climate-report? returns false when laatija is empty"
    (is (false? (climate-report-utils/valid-climate-report? climate-report-data-missing-laatija))
        "Report with empty laatija field is invalid"))

  (testing "valid-climate-report? returns false when pvm is missing"
    (is (false? (climate-report-utils/valid-climate-report? climate-report-data-missing-pvm))
        "Report with missing pvm field is invalid"))

  (testing "valid-climate-report? returns false when carbon footprint is zero"
    (is (false? (climate-report-utils/valid-climate-report? climate-report-data-zero-footprint))
        "Report with carbon footprint = 0 is invalid")))

(deftest epbd-value-test
  (testing "epbd-value multiplies carbon footprint by 50"
    (is (= 700.0 (climate-report-utils/epbd-value (:data valid-climate-report-data)))
        "14.0 * 50 = 700.0")))

(deftest total-carbon-footprint-test
  (testing "total-carbon-footprint multiplies EPBD value by net heated area"
    (is (= 70000.0 (climate-report-utils/total-carbon-footprint (:data valid-climate-report-data) 100))
        "700.0 * 100 = 70000.0")))

(deftest low-carbon-assessment-limits-test
  (testing "uses 2026-2028 limits for applications submitted before 2029"
    (let [limits (climate-report-utils/low-carbon-assessment-limits :asuinkerrostalo submitted-before-2029)]
      (is (= 16.0 (:limit limits))
          "asuinkerrostalo limit before 2029 is 16.0")
      (is (= 800.0 (:epbd-limit limits))
          "epbd-limit is 16.0 * 50 = 800.0")))

  (testing "uses 2029 limits for applications submitted after 2029"
    (let [limits (climate-report-utils/low-carbon-assessment-limits :asuinkerrostalo submitted-after-2029)]
      (is (= 12.0 (:limit limits))
          "asuinkerrostalo limit after 2029 is 12.0")
      (is (= 600.0 (:epbd-limit limits))
          "epbd-limit is 12.0 * 50 = 600.0")))

  (testing "different building types have different limits"
    (let [sairaala-limits (climate-report-utils/low-carbon-assessment-limits :sairaala submitted-before-2029)]
      (is (= 29.0 (:limit sairaala-limits))
          "sairaala has a higher limit"))))

(deftest heated-net-area-total-sum-test
  (testing "heated-net-area-total-sum sums net heated areas from climate reports"
    (let [climate-reports {"report-1" {:data {:rakennuskohteen-osan-tiedot {:lammitetty-nettoala {:value "100"}}}}
                           "report-2" {:data {:rakennuskohteen-osan-tiedot {:lammitetty-nettoala {:value "200"}}}}}]
      (is (= 300.0 (climate-report-utils/heated-net-area-total-sum climate-reports)))))

  (testing "heated-net-area-total-sum handles empty reports"
    (is (= 0 (climate-report-utils/heated-net-area-total-sum {})))))
