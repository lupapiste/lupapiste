(ns lupapalvelu.ui.docgen.feature.allu-application-kind-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.ui.docgen.feature.allu-application-kind :as allu-application-kind]))

(def test-doc-schemas [{:schema {:group-help       "help"
                                 :approvable       false
                                 :i18nkey          "lmv-time"
                                 :uicomponent      "docgenGroup"
                                 :name             "lmv-time"
                                 :css              ["allu-group"]
                                 :type             "group"
                                 :template         "form-grid-docgen-group-template"
                                 :exclude-from-pdf {:title true}
                                 :rows             [["start-date" "end-date"]]
                                 :body             [{:name        "start-date"
                                                     :type        "date"
                                                     :layout      "initial-width"
                                                     :placeholder "placeholder.date"
                                                     :required    true
                                                     :i18nkey     "lmv-time.start-date"}
                                                    {:name        "end-date"
                                                     :type        "date"
                                                     :layout      "initial-width"
                                                     :placeholder "placeholder.date"
                                                     :required    true
                                                     :i18nkey     "lmv-time.end-date"}]}}
                       {:schema {:group-help       "help"
                                 :approvable       false
                                 :i18nkey          "lmv-location"
                                 :uicomponent      "docgenGroup"
                                 :name             "lmv-location"
                                 :css              ["allu-group"]
                                 :type             "group"
                                 :template         "form-grid-docgen-group-template"
                                 :exclude-from-pdf {:title true}
                                 :rows             [["location-type::2"] ["drawings::2" "allu-map::2"] ["area"]]
                                 :body             [{:i18nkey          "lmv-location.location-type"
                                                     :uicomponent      "docgen-radio-group"
                                                     :default          "fixed"
                                                     :name             "location-type"
                                                     :css              ["allu--radio" "top-marginM"]
                                                     :type             "radioGroup"
                                                     :exclude-from-pdf true
                                                     :label            false
                                                     :body             [{:name "fixed" :i18nkey "allu.location-type.fixed"}
                                                                        {:name "custom" :i18nkey "allu.location-type.custom"}]
                                                     :required         true}
                                                    {:name            "kind"
                                                     :type            "allu-application-kind"
                                                     :required        true
                                                     :applicationType "test-application-type"
                                                     :i18nkey         "lyhytaikainen-maanvuokraus.kind"}
                                                    {:name        "drawings"
                                                     :type        "allu-drawings"
                                                     :pseudo?     true
                                                     :map         "allu-map"
                                                     :type-select "location-type"
                                                     :kind        {:path     "lyhytaikainen-maanvuokraus.kind"
                                                                   :document "lyhytaikainen-maanvuokraus"}
                                                     :i18nkey     "lmv-location.drawings"}
                                                    {:name    "allu-map"
                                                     :type    "allu-map"
                                                     :pseudo? true
                                                     :layout  "full-width"
                                                     :i18nkey "lmv-location.allu-map"}
                                                    {:min         0
                                                     :i18nkey     "lmv-location.area"
                                                     :placeholder "lmv.area.placeholder"
                                                     :uicomponent "docgen-input"
                                                     :unit        "m2"
                                                     :name        "area"
                                                     :type        "string"
                                                     :inputType   "string"
                                                     :hide-when   {:document "lyhytaikainen-maanvuokraus"
                                                                   :path     "lyhytaikainen-maanvuokraus/kind"
                                                                   :values   ["bridge-banner"]}
                                                     :subtype     "decimal"}]}}
                       {:schema {:group-help       "help"
                                 :approvable       false
                                 :i18nkey          "lyhytaikainen-maanvuokraus"
                                 :uicomponent      "docgenGroup"
                                 :name             "lyhytaikainen-maanvuokraus"
                                 :css              ["allu-group"]
                                 :type             "group"
                                 :template         "form-grid-docgen-group-template"
                                 :exclude-from-pdf {:title true}
                                 :rows             [["kind::3"] ["description::3"]]
                                 :body             [{:name            "kind"
                                                     :type            "allu-application-kind"
                                                     :required        true
                                                     :applicationType "short-term-rental"
                                                     :i18nkey         "lyhytaikainen-maanvuokraus.kind"}
                                                    {:name        "description"
                                                     :type        "text"
                                                     :max-len     10000
                                                     :placeholder "lmv.description.placeholder"
                                                     :required    true
                                                     :layout      "full-width"
                                                     :i18nkey     "lyhytaikainen-maanvuokraus.description"}]}}])

(deftest field-type-matching-test
  (testing "All date fields found"
    (is (= [{:name        "start-date"
             :type        "date"
             :layout      "initial-width"
             :placeholder "placeholder.date"
             :required    true
             :i18nkey     "lmv-time.start-date"}
            {:name        "end-date"
             :type        "date"
             :layout      "initial-width"
             :placeholder "placeholder.date"
             :required    true
             :i18nkey     "lmv-time.end-date"}]
           (mapcat (partial allu-application-kind/find-fields-matching-type "date")
                   test-doc-schemas))))
  (testing "All allu-application-kind fields found"
    (is (= [{:name            "kind"
             :type            "allu-application-kind"
             :required        true
             :applicationType "test-application-type"
             :i18nkey         "lyhytaikainen-maanvuokraus.kind"}
            {:name            "kind"
             :type            "allu-application-kind"
             :required        true
             :applicationType "short-term-rental"
             :i18nkey         "lyhytaikainen-maanvuokraus.kind"}]
           (mapcat (partial allu-application-kind/find-fields-matching-type "allu-application-kind")
                   test-doc-schemas)))))

(deftest allu-application-types-test
  (testing "Found allu application types"
    (is (= #{"test-application-type" "short-term-rental"}
           (allu-application-kind/allu-application-types test-doc-schemas)))))
