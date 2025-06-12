(ns lupapalvelu.ui.dashboard.queries
  (:require [malli.core :as m]
            [malli.error :as me]
            [malli.util :as mu]))

(def FilterUiOptions
  [:multi
   {:dispatch :type}
   [:toggle
    [:map
     [:type [:= :toggle]]
     [:options [:vector [:map
                         [:value :any]
                         [:title :string]]]]]]
   [:date-range
    [:map
     [:type [:= :date-range]]]]])

(def FilterDefinition
  [:map
   [:filter/type :keyword]
   [:filter/label :string]
   ;; No UI flag -> hide in search filters panel
   [:filter/ui {:optional true} FilterUiOptions]
   [:filter/schema {:optional true} :any]])

(def QueryTypes
  [:vector
   [:map
    [:query/type :keyword]
    [:filters [:vector FilterDefinition]]]])

;; TODO: Labels using localization
;; TODO: Filters might be shown using autocomplete box, maybe with groups?
;; "Hakemuksen tila", "Työnjohtaja" etc. groups?

(def yes-no-toggle
  {:type :toggle
   :options [{:value true
              :title "yes"}
             {:value false
              :title "no"}]})

;; The map key here would be used as :name in the search filter model.
(def application-filters
  [{:filter/type :has-ifc-models
    :filter/label "dashboard.filters.has-ifc-models.label"
    :filter/ui yes-no-toggle
    :filter/schema [:boolean]}

   ;; TODO: Model each state as separate filter?
   {:filter/type :application-submitted
    :filter/label "dashboard.filters.application-submitted.label"
    :filter/ui yes-no-toggle
    :filter/schema [:boolean]}

   ;; FIXME: Hakemus vs hanke?
   {:filter/type :application-ready
    :filter/label "dashboard.filters.application-ready.label"
    :filter/ui yes-no-toggle
    :filter/schema [:boolean]}

   {:filter/type :decision-given
    :filter/label "dashboard.filters.decision-given.label"
    :filter/ui yes-no-toggle
    :filter/schema [:boolean]}

   (let [options [{:value :bim
                   :title "submit-data-source.option.bim"}
                  {:value :document
                   :title "submit-data-source.option.document"}]]
     {:filter/type :models-data-source
      :filter/label "dashboard.filters.models-data-source.label"
      :filter/ui {:type :toggle
                  :options options}
      :filter/schema (into [:enum] (map :value options))})

   {:filter/type :user-has-role
    :filter/label "dashboard.filters.user-has-role.label"
    :filter/ui yes-no-toggle
    :filter/schema [:boolean]}

   {:filter/type :ifc-upload-date
    :filter/label "dashboard.filters.ifc-upload-date.label"
    :filter/ui {:type :date-range}
    ;; TODO: :start/:end or something?
    :filter/schema :any}

   (let [options [{:value :before-decision
                   :title "dashboard.filters.model-delivered.before-decision"}
                  {:value :after-decision
                   :title "dashboard.filters.model-delivered.after-decision"}]]
     {:filter/type :model-delivered
      :filter/label "dashboard.filters.model-delivered.label"
      :filter/ui {:type :toggle
                  :options options}
      :filter/schema (into [:enum] (map :value options))})

   {:filter/type :ui/control-filter
    :filter/label "dashboard.filters.ui-control-filter.label"
    ;; :search false
    }])

(defn filters-to-schema [filters]
  (into [:multi
         {:dispatch :filter/type}]
        (map (fn [v]
               [(:filter/type v)
                (cond-> [:map
                         [:filter/type [:= (:filter/type v)]]]
                  (:filter/schema v) (mu/assoc :filter/value (:filter/schema v)))])
             filters)))

(def query-types
  [{:query/type :search/applications
    ;; :query/label "Hae hakemuksia, joissa"
    :filters application-filters
    :type->filter (into {} (map (juxt :filter/type identity) application-filters))
    :filter-schema (filters-to-schema application-filters)}])

(def type->query
  (into {} (map (juxt :query/type identity) query-types)))

(when-not (m/validate QueryTypes query-types)
  (throw (ex-info "Error on search types definition"
                  {:error (me/humanize (m/explain QueryTypes query-types))})))

;; TODO: Result types per search type maybe, like filters?
(def results
  {:applications/count {:label "Lukumäärä"}
   :ifc-models/count {:label "IFC-mallien lukumäärä"}})

(def SearchModel
  "Schema for the search query, i.e., what is send to the backend.

  Not a schema for the definitions in this file."
  (into [:multi
         {:dispatch :query/type}]
        (map (fn [v]
               [(:query/type v)
                [:map
                 [:query/type [:= (:query/type v)]]
                 [:query/filters [:vector (:filter-schema v)]]
                 [:query/results [:map
                                  [:id (into [:enum] (keys results))]]]]])
             query-types)))
