(ns lupapalvelu.document.allu-schemas
  "Docgen schemas for Allu applications (promotio, lyhytaikainen maanvuokraus)."
  (:require [lupapalvelu.document.schemas :refer [defschemas]]
            [sade.util :as util]))

(def application-types
  {:area-rental                    "AREA_RENTAL"
   :cable-report                   "CABLE_REPORT"
   :event                          "EVENT"
   :excavation-announcement        "EXCAVATION_ANNOUNCEMENT"
   :note                           "NOTE"
   :placement-contract             "PLACEMENT_CONTRACT"
   :short-term-rental              "SHORT_TERM_RENTAL"
   :temporary-traffic-arrangements "TEMPORARY_TRAFFIC_ARRANGEMENTS"})

(def application-kinds
  {:agile-kiosk-area              "AGILE_KIOSK_AREA"
   :art                           "ART"
   :benji                         "BENJI"
   :bridge-banner                 "BRIDGE_BANNER"
   :christmas-tree-sales-area     "CHRISTMAS_TREE_SALES_AREA"
   :circus                        "CIRCUS"
   :city-cycling-area             "CITY_CYCLING_AREA"
   :construction                  "CONSTRUCTION"
   :container-barrack             "CONTAINER_BARRACK"
   :data-transfer                 "DATA_TRANSFER"
   :dog-training-event            "DOG_TRAINING_EVENT"
   :dog-training-field            "DOG_TRAINING_FIELD"
   :election-add-stand            "ELECTION_ADD_STAND"
   :electricity                   "ELECTRICITY"
   :geological-survey             "GEOLOGICAL_SURVEY"
   :heating-cooling               "HEATING_COOLING"
   :keskuskatu-sales              "KESKUSKATU_SALES"
   :lifting                       "LIFTING"
   :military-excercise            "MILITARY_EXCERCISE"
   :new-building-construction     "NEW_BUILDING_CONSTRUCTION"
   :other                         "OTHER"
   :other-subvision-of-state-area "OTHER_SUBVISION_OF_STATE_AREA"
   :outdoorevent                  "OUTDOOREVENT"
   :photo-shooting                "PHOTO_SHOOTING"
   :promotion                     "PROMOTION"
   :promotion-or-sales            "PROMOTION_OR_SALES"
   :property-renovation           "PROPERTY_RENOVATION"
   :public-event                  "PUBLIC_EVENT"
   :relocation                    "RELOCATION"
   :repaving                      "REPAVING"
   :roll-off                      "ROLL_OFF"
   :season-sale                   "SEASON_SALE"
   :small-art-and-culture         "SMALL_ART_AND_CULTURE"
   :snow-gather-area              "SNOW_GATHER_AREA"
   :snow-heap-area                "SNOW_HEAP_AREA"
   :snow-work                     "SNOW_WORK"
   :statement                     "STATEMENT"
   :storage-area                  "STORAGE_AREA"
   :street-and-green              "STREET_AND_GREEN"
   :summer-theater                "SUMMER_THEATER"
   :urban-farming                 "URBAN_FARMING"
   :water-and-sewage              "WATER_AND_SEWAGE"
   :winter-parking                "WINTER_PARKING"
   :yard                          "YARD"})

(def application-kinds-inverse (zipmap (vals application-kinds) (keys application-kinds)))

(defn allu-group [{group :name :keys [body rows css pdf-options]}]
  {:name             group
   :exclude-from-pdf {:title true}
   :type             :group
   :i18nkey          group
   :approvable       false
   :group-help       "help"
   :uicomponent      :docgenGroup
   :css              (cons :allu-group (flatten [(or css [])]))
   :template         "form-grid-docgen-group-template"
   :body             (mapv (fn [e]
                             (assoc e :i18nkey (name (util/kw-path group (:name e)))))
                           body)
   :rows             rows})

;; -------------------------
;; Promootio
;; -------------------------

(def promootio-description (allu-group {:name "promootio"
                                        :body [{:name        "promootio-name"
                                                :type        :string
                                                :placeholder "promootio.name.placeholder"
                                                :required    true
                                                :layout      :full-width
                                                :uicomponent :docgen-input
                                                :inputType   :string}
                                               {:name        "promootio-description"
                                                :type        :text
                                                :max-len     10000
                                                :placeholder "promootio.description.placeholder"
                                                :required    true
                                                :layout      :full-width}]
                                        :rows [["promootio-name::3"]
                                               ["promootio-description::3"]]}))



(def promootio-time (allu-group {:name "promootio-time"
                                 :body [{:name        "start-date"
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
                                         :show-when   {:path   "demolish-days-needed"
                                                       :values #{true}}}]
                                 :rows [["start-date" "start-time"]
                                        ["end-date" "end-time"]
                                        {:h3 "allu.build-and-demolish"}
                                        ["build-days-needed" "build-start-date" "build-start-time"]
                                        ["demolish-days-needed" "demolish-end-date" "demolish-end-time"]]}))

(defn- location [name & [kind]]
  {:name name
   :body [{:name             "location-type"
           :type             :radioGroup
           :uicomponent      :docgen-radio-group
           :required         true
           :default          "fixed"
           :exclude-from-pdf true
           :label            false
           :css              [:allu--radio :top-marginM]
           :body             [{:name    "fixed"
                               :i18nkey "allu.location-type.fixed"}
                              {:name    "custom"
                               :i18nkey "allu.location-type.custom"}]}
          (util/assoc-when {:name        "drawings"
                            :type        :allu-drawings
                            :pseudo?     true
                            :map         "allu-map"
                            :type-select "location-type"}
                           :kind kind)
          {:name    "allu-map"
           :type    :allu-map
           :pseudo? true
           :layout  :full-width}]
   :rows [["location-type::2"]
          ["drawings::2" "allu-map::2"]]})

(def promootio-location (allu-group (location "promootio-location" :promotion)))

(def promootio-structures (allu-group {:name "promootio-structures"
                                       :css  :allu-group--structures
                                       :body [{:name      "structures-needed"
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
                                               :inputType :checkbox-wrapper}
                                              {:name      "traffic-link"
                                               :pseudo?   true
                                               :type      :textlink
                                               :css       [:allu--link]
                                               :text      :promootio.traffic-link-text
                                               :url       :promootio.traffic-link-url
                                               :icon      [:lupicon-circle-info :primary]
                                               :show-when {:path   "traffic-needed"
                                                           :values #{true}}}]
                                       :rows            [{:css [:allu-row--spaced]
                                                          :row ["structures-needed::2"]}
                                                         ["structures::4"]
                                                         {:css [:allu-row--spaced]
                                                          :row ["traffic-needed::2" "traffic-link::2"]}]}))

(defn info-rows [info-keys]
  (reduce (fn [acc k]
            (let [info (str k "-info")
                  link (str k "-link")
                  title-key (str "promootio-info." k)]
              (-> acc
                  (update :body concat [{:name        k
                                         :type        :radioGroup
                                         :uicomponent :docgen-radio-group
                                         :required    true
                                         :label       false
                                         :labelled-by title-key
                                         :css         [:allu--radio]
                                         :body        [{:name    "no"
                                                        :i18nkey "no"}
                                                       {:name    "yes"
                                                        :i18nkey "yes"}]}
                                        {:name        info
                                         :type        :string
                                         :label       false
                                         :required    true
                                         :css         [:allu--description]
                                         :placeholder "placeholder.description"
                                         :show-when   {:path   k
                                                       :values #{"yes"}}}
                                        {:name      link
                                         :type      :textlink
                                         :pseudo?   true
                                         :text      (util/kw-path :promootio-info link :text)
                                         :url       (util/kw-path :promootio-info link :url)
                                         :icon      [:lupicon-circle-info :primary]
                                         :css       [:allu--link]
                                         :label     false
                                         :show-when {:path   k
                                                     :values #{"yes"}}}])
                  (update :rows concat [{:h4 title-key}
                                        [k (str info "::2") link]]))))
          {:body []
           :rows []}
          info-keys))

(def promootio-info (-> (info-rows ["food" "music" "alcohol" "games" "risks"])
                        (update :body concat [{:name        "other-info"
                                               :type        :text
                                               :max-len     10000
                                               :layout      :full-width
                                               :placeholder "placeholder.other-promotion-info"}])
                        (update :rows concat [{:row ["other-info::2"]
                                               :css [:allu-row--spaced]}])
                        (assoc :name "promootio-info"
                               :css :allu--info)
                        allu-group))

;; ----------------------------
;; Lyhytaikainen maanvuokraus
;; ----------------------------

(def lmv-description (allu-group {:name "lyhytaikainen-maanvuokraus"
                                  :body [{:name       "kind"
                                          :type       :allu-application-kind
                                          :required   true
                                          :applicationType :short-term-rental}
                                         {:name        "description"
                                          :type        :text
                                          :max-len     10000
                                          :placeholder "lmv.description.placeholder"
                                          :required    true
                                          :layout      :full-width}]
                                  :rows [["kind::3"]
                                         ["description::3"]]}))

(def lmv-location (allu-group (-> (location "lmv-location" {:path :lyhytaikainen-maanvuokraus.kind
                                                            :document "lyhytaikainen-maanvuokraus"})
                                  (update :body conj {:name        "area"
                                                      :type        :string
                                                      :subtype     :decimal
                                                      :unit        :m2
                                                      :min         0
                                                      :uicomponent :docgen-input
                                                      :inputType   :string
                                                      :placeholder "lmv.area.placeholder"
                                                      :hide-when   {:document "lyhytaikainen-maanvuokraus"
                                                                    :path     "lyhytaikainen-maanvuokraus/kind"
                                                                    :values   #{"bridge-banner"}}})
                                  (update :rows conj ["area"]))))

(def lmv-time (allu-group {:name "lmv-time"
                           :body [{:name        "start-date"
                                   :type        :date
                                   :layout      :initial-width
                                   :placeholder "placeholder.date"
                                   :required    true}
                                  {:name        "end-date"
                                   :type        :date
                                   :layout      :initial-width
                                   :placeholder "placeholder.date"
                                   :required    true}]
                           :rows [["start-date" "end-date"]]}))

;; The definition is used in pdf-export-test.
(def schema-definitions [{:info {:name       "promootio"
                                 :approvable true
                                 :order      11}
                          :body [promootio-description]}
                         {:info {:name       "promootio-time"
                                 :approvable true
                                 :order      12}
                          :body [promootio-time]}
                         {:info {:name       "promootio-location"
                                 :approvable true
                                 :order      13}
                          :body [promootio-location]}
                         {:info {:name       "promootio-structures"
                                 :approvable true
                                 :order      14}
                          :body [promootio-structures]}
                         {:info {:name       "promootio-info"
                                 :approvable true
                                 :order      15}
                          :body [promootio-info]}
                         {:info {:name       "lyhytaikainen-maanvuokraus"
                                 :approvable true
                                 :order      11}
                          :body [lmv-description]}
                         {:info {:name       "lmv-location"
                                 :approvable true
                                 :order      12}
                          :body [lmv-location]}
                         {:info {:name       "lmv-time"
                                 :approvable true
                                 :order      13}
                          :body [lmv-time]}])

(defschemas 1 schema-definitions)
