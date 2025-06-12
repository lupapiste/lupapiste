(ns lupapalvelu.ui.docgen.feature.climate-reports-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [day8.re-frame.test :as rf-test]
            [lupapalvelu.low-carbon-assessment.climate-report-utils :as climate-report-utils]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.docgen.feature.climate-reports :as climate-reports]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.test-utils :as test-utils]
            [lupapalvelu.ui.docgen.state :as docgen]
            [re-frame.core :as rf]))

(t/use-fixtures :once test-utils/js-services-fixture)

;; Test data

;; Minimal schema structure - only includes fields needed for testing.
;; In reality, this comes from the backend when the app is loaded.
(def building-doc-schema
  {:info {:type "building"
          :name "uusiRakennuskohde"}})

(def climate-report-schema
  {:info {:type "climate-report"
          :name "ilmastoselvitys"}})

(def building-doc
  "Building document (uusiRakennuskohde) that the climate-report references"
  {:id     "building-1"
   :schema building-doc-schema
   :data   {:rakennusluokka-vahahiilisyys {:rakennusluokka-vahahiilisyys {:value "asuinkerrostalo"}}
            :rakennuskohteen-osa          {:energiatiedot {:lammitetty-nettoala {:value "1000"}}}}})

(def climate-report-doc
  "Climate report document with carbon footprint data"
  {:id           "climate-report-1"
   :building-ref "building-1"
   :schema       climate-report-schema
   :data         {:hiilijalanjalki {:purkujatteen-kuljetukset     {:value "5.0"}
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

(def climate-report-doc-zero-footprint
  "Climate report with zero carbon footprint"
  (-> climate-report-doc
      (assoc :id "climate-report-zero")
      (assoc-in [:data :hiilijalanjalki] {:purkujatteen-kuljetukset     {:value "0"}
                                          :purkujatteen-kasittely       {:value "0"}
                                          :kuljetukset                  {:value "0"}
                                          :purkaminen                   {:value "0"}
                                          :tyomaatoiminnot              {:value "0"}
                                          :purkujatteen-loppusijoitus   {:value "0"}
                                          :rakennustuotteiden-vaihdot   {:value "0"}
                                          :rakennustuotteiden-valmistus {:value "0"}})))

;; Submitted before 2029 (limits-2026-2028 should apply)
(def submitted-before-2029 1735689600000) ;; 2025-01-01

;; Submitted after 2029 (limits-2029 should apply)
(def submitted-after-2029 1893456000000)  ;; 2030-01-01

(defn make-app [docs submitted-ts]
  (assoc test-utils/app
         :documents docs
         :submitted submitted-ts))

;; Tests

(deftest carbon-footprint-calculation-test
  (rf-test/run-test-sync
    (let [{:keys [handler]} (test-utils/make-mock-command)]
      (rf/reg-fx :action/command handler)

      (>evt [::docgen/app-loaded {:app     (make-app [building-doc climate-report-doc] submitted-before-2029)
                                  :details test-utils/details}])

      (testing "carbon footprint sums all values in hiilijalanjalki group"
        (let [climate-data (<sub [::climate-reports/climate-report-data "climate-report-1"])]
          (is (= 14.0 (climate-report-utils/carbon-footprint climate-data))
              "5.0 + 1.0 + 2.0 + 1.5 + 0.5 + 1.0 + 1.0 + 2.0 = 14.0"))))))

(deftest carbon-footprint-zero-values-test
  (rf-test/run-test-sync
    (let [{:keys [handler]} (test-utils/make-mock-command)]
      (rf/reg-fx :action/command handler)

      (>evt [::docgen/app-loaded {:app     (make-app [building-doc climate-report-doc-zero-footprint] submitted-before-2029)
                                  :details test-utils/details}])

      (testing "carbon footprint handles zero values"
        (let [climate-data (<sub [::climate-reports/climate-report-data "climate-report-zero"])]
          (is (= 0 (climate-report-utils/carbon-footprint climate-data))))))))

(deftest low-carbon-assessment-limits-before-2029-test
  (rf-test/run-test-sync
    (let [{:keys [handler]} (test-utils/make-mock-command)]
      (rf/reg-fx :action/command handler)

      (>evt [::docgen/app-loaded {:app     (make-app [building-doc climate-report-doc] submitted-before-2029)
                                  :details test-utils/details}])

      (testing "uses 2026-2028 limits for applications submitted before 2029"
        (let [limits (<sub [::climate-reports/low-carbon-assessment-limits "climate-report-1"])]
          (is (= 16.0 (:limit limits))
              "asuinkerrostalo limit before 2029 is 16.0")
          (is (= (* 50 (:limit limits)) (:epbd-limit limits))
              "epbd-limit is limit * 50"))))))

(deftest low-carbon-assessment-limits-after-2029-test
  (rf-test/run-test-sync
    (let [{:keys [handler]} (test-utils/make-mock-command)]
      (rf/reg-fx :action/command handler)

      (>evt [::docgen/app-loaded {:app (make-app [building-doc climate-report-doc] submitted-after-2029)
                                  :details test-utils/details}])

      (testing "uses 2029 limits for applications submitted after 2029"
        (let [limits (<sub [::climate-reports/low-carbon-assessment-limits "climate-report-1"])]
          (is (= 12.0 (:limit limits))
              "asuinkerrostalo limit after 2029 is 12.0")
          (is (= (* 50 (:limit limits)) (:epbd-limit limits))
              "epbd-limit is limit * 50"))))))

(deftest carbon-footprint-over-limit-test
  (rf-test/run-test-sync
    (let [{:keys [handler]} (test-utils/make-mock-command)]
      (rf/reg-fx :action/command handler)

      ;; Carbon footprint is 14.0, limit is 16.0 before 2029
      (>evt [::docgen/app-loaded {:app (make-app [building-doc climate-report-doc] submitted-before-2029)
                                  :details test-utils/details}])

      (testing "carbon footprint under limit returns false"
        (is (false? (<sub [::climate-reports/carbon-footprint-over-limit? "climate-report-1"]))
            "14.0 is not over 16.0 limit"))))

  (rf-test/run-test-sync
    (let [{:keys [handler]} (test-utils/make-mock-command)]
      (rf/reg-fx :action/command handler)

      ;; Carbon footprint is 14.0, limit is 12.0 after 2029
      (>evt [::docgen/app-loaded {:app     (make-app [building-doc climate-report-doc] submitted-after-2029)
                                  :details test-utils/details}])

      (testing "carbon footprint over limit returns true"
        (is (true? (<sub [::climate-reports/carbon-footprint-over-limit? "climate-report-1"]))
            "14.0 is > 12.0 limit")))))

(deftest climate-reports-subscription-test
  (rf-test/run-test-sync
    (let [{:keys [handler]} (test-utils/make-mock-command)]
      (rf/reg-fx :action/command handler)

      (>evt [::docgen/app-loaded {:app     (make-app [building-doc climate-report-doc] submitted-before-2029)
                                  :details test-utils/details}])

      (testing "climate-reports subscription filters only climate-report type documents"
        (let [climate-reports (<sub [::docs/climate-reports])]
          (is (= 1 (count climate-reports)))
          (is (= "climate-report-1" (-> climate-reports first first))))))))
