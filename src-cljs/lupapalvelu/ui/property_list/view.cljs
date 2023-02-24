(ns lupapalvelu.ui.property-list.view
  "A Reagent component for selecting a collection of properties from a cloudpermit/map-component"
  (:require [clojure.data :as data]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [lupapalvelu.ui.property-list.property-table :as property-table]
            [reagent.core :as r]
            [sade.shared-util :as util]))

(defn application-map
  "Reagent wrapper for the cloudpermit/map-component JS React component"
  [& args]
  [:div.react-map-component.extended
   (apply conj [:> (.. js/window -MapLibrary -ApplicationMap)] args)])

(defn get-property-id
  "Returns the property id for a given property row.
  The structure comes from the docgen repeating group model"
  [property-id-field property-model]
  (get-in property-model [:model (keyword property-id-field) :model]))

(defn update-selected-properties
  "Removes or adds property rows to the model based on the updated list from the map-component.
  The table model is an array of properties:
  [{index 0, model {kiinteistotunnus {model 1234567890} ...} ...}
   {index 2, model {kiinteistotunnus {model 1334434323} ...} ...}
   ...]
   Where the index is a side-effect of the way documents with repeating groups are handled"
  [{:keys [properties path delete-fn create-fn property-id-field]} selected-property-ids]
  (let [get-id                    (partial get-property-id property-id-field)
        [removed-ids added-ids _] (data/diff (set (keep get-id properties))
                                             (set selected-property-ids))]
    (doseq [{:keys [index]} (filter #(contains? removed-ids (get-id %))
                                    properties)]
      (delete-fn (concat path [index])))
    (doseq [added-id added-ids]
      (create-fn path {property-id-field {:value added-id}}))))

(defn render
  "The Reagent render method for the property list, including
  - the map view where user can select/deselect properties
  - a table of selected properties
  - the fields for each selected property
    - including simple fields
    - and subtables (like property owners)

  This can be (in theory at least) used from Reagent directly;
  for use from KnockoutJS, check the property-list class below

  Note: 'index' below does not mean the property's location in the properties seq.
  In practice this means that when a row is removed, the subsequent rows' indexes are NOT updated

  Props [optional]:
  loc                Localization function; takes only localization key as param, not language
  map-props          A map of options to redirect to cloudpermit/map-component, some of which are overridden
  properties         A seq of property information, using the format of a repeating group document model
                     This is used directly; no separate internal state is maintained (except inside map-component)
                     See the docstring for `property-table/table` for specifics
  fields             A seq of field schemas, defining the list of fields inside a property
  path               An identifying seq that gives the 'address' of this component in the document
  create-fn          A callback fn, has params path ([..original-path... index field-name]) and data (a map of values)
  update-fn          A callback fn, has params path ([..original-path... index field-name]) and value
  delete-fn          A callback fn, has a single param: path to deleted item
  [fetch-fns]        A map from field-group name to a map with keys
                       fetch-fn (one param: index, updates the model outside the property-list)
                       ok-fn    (one param: index, returns true if the user is allowed to perform the fetch)
  property-id-field  Name of the field that contains the property id (used to sync map and list)
  [validation]       A seq of validation errors, with path and result keys
  [title]            The title localization key that is shown above the map
  [disabled?]        True if the list contents are not modifiable"
  [{:keys [map-props properties loc title disabled? property-id-field] :as opts}]
  [:div.flex--column.flex--gap3
   (when title [:div.group-title.strong (loc title)])
   [application-map
    (-> map-props
        (walk/keywordize-keys)
        (update :requestHeaders walk/stringify-keys) ; fixes React changing :x-anti-forgery-token to "xAntiForgeryToken"
        (merge {:locFn                       loc
                :hideDrawings                true
                :forcedInteractionState      (if disabled? "DEFAULT" "SELECT_PROPERTIES")
                :onSelectedPropertiesChanged #(update-selected-properties opts %)
                :selectedProperties          (->> properties
                                                  (keep (partial get-property-id property-id-field))
                                                  (map str/trim)
                                                  (distinct)
                                                  (clj->js))}))]
   [property-table/table opts]])

(def ^:export property-list
  "Defines a React class (component) that can be used from inside a KnockoutJS template"
  (r/create-class
    {:display-name   "property-list"
     :reagent-render (fn [_]
                       ;; This outer function is called once; inner one each time the opts are updated
                       (fn [opts]
                         (render
                           (-> (js->clj opts)
                               (set/rename-keys {:disabled :disabled?})
                               ;; Unwrap observables here since this class is used from docgen
                               (update :properties #(-> (%) js->clj walk/keywordize-keys))
                               (update :validation #(-> (%) js->clj walk/keywordize-keys))
                               (update :disabled? #(-> (%) boolean))
                               (update :fields walk/keywordize-keys)
                               (update :fetch-fns walk/keywordize-keys)
                               ;; Wrap the JS CRUD functions so they can be passed CLJS structures
                               (update :create-fn util/js->clj-fn)
                               (update :update-fn util/js->clj-fn)
                               (update :delete-fn util/js->clj-fn)))))}))
