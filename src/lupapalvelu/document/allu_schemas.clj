(ns lupapalvelu.document.allu-schemas
  "Docgen schemas for Allu applications (promotio, lyhytaikainen maanvuokraus)."
  (:require [lupapalvelu.document.schemas :refer [defschemas]]))

(def promootio-description {:name        "promootio"
                            :type        :group
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
                                    :placeholder "placeholder.time"}]
                     :rows        [["start-date" "start-time"]
                                   ["end-date" "end-time"]]})

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
  [{:info {:name "promootio"
           :approvable true
           :order 1}
    :body [promootio-description]}
   {:info {:name "promootio-time"
           :approvable true
           :order 2}
    :body [promootio-time]}
   {:info {:name "lyhytaikainen-maanvuokraus"
           :approvable true
           :order 1}
    :body [lmv-description]}])
