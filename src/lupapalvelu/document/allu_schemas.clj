(ns lupapalvelu.document.allu-schemas
  "Docgen schemas for Allu applications (promotio, lyhytaikainen maanvuokraus)."
  (:require [lupapalvelu.document.schemas :refer [defschemas]]))

(def promootio-description {:name        "promootio"
                            :type        :group
                            :i18nkey     "promootio"
                            :approvable  false
                            :group-help  "help"
                            :uicomponent :docgenGroup
                            :css         [:allu-group]
                            :template    "form-grid-docgen-group-template"
                            :body        [{:name        "promootio-name"
                                           :type        :string
                                           :placeholder "promootio.name.placeholder"
                                           :required    true
                                           :layout      :full-width
                                           :uicomponent :docgen-input
                                           :inputType   :string}
                                          {:name        "promootio-description"
                                           :type        :text
                                           :placeholder "promootio.description.placeholder"
                                           :required    true
                                           :layout      :full-width}]
                            :rows        [["promootio-name::3"]
                                          ["promootio-description::3"]]})

(def promootio-time {:name        "promootio-time"
                     :type        :group
                     :i18nkey     "promootio-time"
                     :approvable  false
                     :group-help  "help"
                     :uicomponent :docgenGroup
                     :css         [:allu-group]
                     :template    "form-grid-docgen-group-template"
                     :body        [{:name        "start-date"
                                    :type        :date
                                    :layout      :initial-width
                                    :placeholder "placeholder.date"
                                    :required    true}
                                   {:name        "end-date"
                                    :type        :date
                                    :layout      :initial-width
                                    :placeholder "placeholder.date"
                                    :required    true}
                                   {:name        "start-time"
                                    :type        :time
                                    :placeholder "placeholder.time"}
                                   {:name        "end-time"
                                    :type        :time
                                    :placeholder "placeholder.time"}
                                   {:name      "build-days-needed"
                                    :type      :checkbox
                                    :inputType :checkbox-wrapper}
                                   {:name        "build-start-date"
                                    :type        :date
                                    :layout      :initial-width
                                    :placeholder "placeholder.date"
                                    :required    true
                                    :show-when   {:path   "build-days-needed"
                                                  :values #{true}}}
                                   {:name        "build-start-time"
                                    :type        :time
                                    :placeholder "placeholder.time"
                                    :required    true
                                    :show-when   {:path   "build-days-needed"
                                                  :values #{true}}}
                                   {:name      "demolish-days-needed"
                                    :type      :checkbox
                                    :inputType :checkbox-wrapper}
                                   {:name        "demolish-end-date"
                                    :type        :date
                                    :layout      :initial-width
                                    :placeholder "placeholder.date"
                                    :required    true
                                    :show-when   {:path   "demolish-days-needed"
                                                  :values #{true}}}
                                   {:name        "demolish-end-time"
                                    :type        :time
                                    :placeholder "placeholder.time"
                                    :required    true
                                    :show-when   {:path   "demolish-days-needed"
                                                  :values #{true}}}]
                     :rows        [["start-date" "start-time"]
                                   ["end-date" "end-time"]
                                   {:h3 "allu.build-and-demolish"}
                                   ["build-days-needed" "build-start-date" "build-start-time"]
                                   ["demolish-days-needed" "demolish-end-date" "demolish-end-time"]]})

(def promootio-location {:name        "promootio-location"
                         :type        :group
                         :i18nkey     "promootio-location"
                         :approvable  false
                         :group-help  "help"
                         :uicomponent :docgenGroup
                         :css         [:allu-group]
                         :template    "form-grid-docgen-group-template"
                         :body        [{:name        "place"
                                        :type        :group
                                        :uicomponent :docgen-group
                                        :css         [:allu-group :allu-group--place]
                                        :template    "form-grid-docgen-group-template"
                                        :body        [{:name   "select"
                                                       :type   :select
                                                       :css    [:dropdown]
                                                       :layout :full-width
                                                       :body   [{:name "foo"}
                                                                {:name "bar"}]}
                                                      {:name        "info"
                                                       :type        :string
                                                       :placeholder "placeholder.place-info"}]
                                        :rows        [["select::4"]
                                                      ["info::4"]]}
                                       {:name   "map"
                                        :type   :text
                                        :label  false
                                        :css    [:allu-map]
                                        :layout :full-width}]
                         :rows        [["place::2" "map::2"]]})

(def promootio-structures {:name        "promootio-structures"
                           :type        :group
                           :i18nkey     "promootio-structures"
                           :approvable  false
                           :group-help  "help"
                           :uicomponent :docgenGroup
                           :css         [:allu-group]
                           :template    "form-grid-docgen-group-template"
                           :body        [{:name      "structures-needed"
                                          :type      :checkbox
                                          :inputType :checkbox-wrapper}
                                         {:name        "structures"
                                          :type        :table
                                          :show-when   {:path   "structures-needed"
                                                        :values #{true}}
                                          :copybutton  true
                                          :repeating   true
                                          :css         [:allu-table]
                                          :columnCss   {"structure" [:column--80 :allu--structure]
                                                        "area"      [:column--20]}
                                          :body        [{:name        "structure"
                                                         :type        :group
                                                         :pdf-options {:other-select :structure-select}
                                                         :approvable  false
                                                         :template    "simple-docgen-group-template"
                                                         :body        [{:name        "structure-select"
                                                                        :type        :select
                                                                        :uicomponent :docgen-select
                                                                        :other-key   "muu"
                                                                        :sortBy      :displayname
                                                                        :i18nkey     "promootio-structure"
                                                                        :css         [:dropdown]
                                                                        :label       false
                                                                        :body        (conj (mapv #(hash-map :name %)
                                                                                                 ["tent" "stage" "coldtruck"])
                                                                                           {:name "muu" :i18nkey "select-other"})
                                                                        :hide-when   {:path "structure-select" :values #{"muu"}}}
                                                                       {:name        "muu"
                                                                        :type        :string
                                                                        :label       false
                                                                        :layout      :full-width
                                                                        :placeholder "placeholder.structure"
                                                                        :show-when   {:path "structure-select" :values #{"muu"}}}]}
                                                        {:name    "area"
                                                         :type    :string
                                                         :subtype :decimal
                                                         :unit    :m2
                                                         :min     0}]
                                          :footer-sums [{:unitKey :m2
                                                         :amount  "area"}]}
                                         {:name      "traffic-needed"
                                          :type      :checkbox
                                          :inputType :checkbox-wrapper}]
                           :rows        [["structures-needed::2"]
                                         ["structures::4"]
                                         ["traffic-needed::2"]]})


(def lmv-description {:name       "lyhytaikainen-maanvuokraus"
                      :type       :group
                      :approvable true
                      :body       [{:name     "lmv-type"
                                    :type     :select
                                    :layout   :full-width
                                    :required true
                                    :body     [{:name "one"}
                                               {:name "two"}]}
                                   {:name     "lmv-description"
                                    :type     :text
                                    :required true
                                    :layout   :full-width}]})

(defschemas
  1
  [{:info {:name       "promootio"
           :approvable true
           :order      1}
    :body [promootio-description]}
   {:info {:name       "promootio-time"
           :approvable true
           :order      2}
    :body [promootio-time]}
   {:info {:name       "promootio-location"
           :approvable true
           :order      3}
    :body [promootio-location]}
   {:info {:name       "promootio-structures"
           :approvable true
           :order      4}
    :body [promootio-structures]}
   {:info {:name       "lyhytaikainen-maanvuokraus"
           :approvable true
           :order      1}
    :body [lmv-description]}])
