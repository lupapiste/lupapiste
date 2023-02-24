(ns lupapalvelu.ui.property-list.property-table
  "A table where each row is a property and the columns are editable fields for those properties"
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [lupapalvelu.ui.components :as components]
            [reagent.core :as r]
            [sade.shared-util :as util]))

(defn error-message
  "A warning span that are collected into a list for the user to explain validation errors"
  [error-level error-code {:keys [loc aria-errormessage] {:keys [i18nkey]} :schema}]
  (when (and error-level
             error-code
             (not= error-level "tip"))
    (let [field-label (loc i18nkey)
          error-text  (loc (str "error." error-code))]
      [:div.warnings
       {:role       "alert"
        :aria-label (str field-label ": " error-text)}
       [:span.warning
        {:aria-hidden true
         :id          aria-errormessage}
        error-text]])))

(defn label
  "A per-input label"
  [{:keys [loc i18nkey required unit]}]
  [:div
   [:label.lux.form-label-string
    {:class (when required "required")}
    (loc i18nkey)
    (when unit (str " (" (->> unit (str "unit.") loc) ")"))]])

(defn date-field [{:keys [value schema] :as opts}]
  (components/day-edit
    value
    (-> opts
        (dissoc :value :loc)
        (assoc :required? (:required schema)
               :string? true))))

(defn- select-option-loc
  "Returns the localized label for any single option in a dropdown"
  [loc schema value]
  (if (or (nil? value)
          (= value ""))
    "-"
    (-> (util/kw-path (:i18nkey schema) value)
        (name)
        (loc))))

(defn select-field [{:keys [loc value schema] :as opts}]
  [components/select
   (or value "")
   (cons [""
          (loc "choose")]
         (->> (:body schema)
              (mapv (fn [{option-value :name}]
                      [option-value (select-option-loc loc schema option-value)]))))
   (dissoc opts :loc)])

(defn input-field [{{:keys [subtype]} :schema :as opts}]
  [components/text-edit
   (:value opts)
   (-> opts
       (dissoc :value :loc :schema)
       (update :callback comp util/tval)
       (set/rename-keys {:callback :on-blur})
       (merge {:type (if (= subtype "number") :number :text)}))])

(defn field-shown?
  "Returns true if the field either doesn't have a conditional show-when/hide-when option or it is met"
  [{:keys [show-when hide-when]} model]
  (let [;; Every odd step is the field name from the path and every even step is :model
        get-nested-path #(->> (repeat :model)
                              (mapcat vector (util/sequentialize %))
                              (map keyword))
        matches?        (fn [{:keys [path values]}]
                          (->> (get-nested-path path)
                               (get-in model)
                               (contains? (set values))))]
    (cond
      show-when (-> show-when matches?)
      hide-when (-> hide-when matches? not)
      :else     true)))

(defn field
  "Render a single input field for the property"
  [{:keys [loc update-fn path model validation disabled? editing? hidden-label?] field-name :name :as opts}]
  (let [field-path    (concat path [field-name])
        field-model   (get model (keyword field-name))
        field-schema  (:schema field-model)
        schema-type   (:type field-schema)
        value         (get field-model :model "")
        element-id    (str/join "-" field-path)
        [error-level
         error-code]  (->> validation
                           (util/find-first #(-> % :path vec (= field-path)))
                           :result
                           (seq))
        input-opts    {:id                element-id
                       :key               element-id
                       :loc               loc
                       :name              field-name
                       :class             (str "lux " error-level)
                       :value             (or value "")
                       :schema            field-schema
                       :test-id           (-> field-path util/kw-path name)
                       :callback          (partial update-fn field-path)
                       :aria-invalid      (and (some? error-code)
                                               (not= error-level "tip"))
                       :aria-errormessage (str element-id "_error")}]
    (when (field-shown? field-schema model)
      [:div
       (when-not hidden-label?
         [label (merge opts field-schema)])
       (if (or (not editing?) disabled?)
         [:span (cond
                  (= schema-type "select") (select-option-loc loc field-schema value)
                  (some? value)            value
                  :else                    "")]
         (case schema-type
           "date"   [date-field input-opts]
           "select" [select-field input-opts]
           [input-field input-opts]))
       [error-message error-level error-code input-opts]])))

(defn item-buttons
  "The edit and remove buttons for a single item (property or field group row)"
  [{:keys [editing? focus? disabled? edit-fn remove-fn]}]
  (when (and (not disabled?)
             (or editing? focus?))
    [:<>
     [components/icon-button
      {:icon     (when-not editing? :lupicon-pen)
       :text-loc (if editing? :edit.stop :edit)
       :class    (if editing? :primary :tertiary)
       :on-click edit-fn}]
     [components/icon-button
      {:icon     :lupicon-trash
       :text-loc :remove
       :class    :secondary
       :on-click remove-fn}]]))

(defn field-group-row
  "Renders a single 'row' from a repeating field group (a subtable of the property)"
  [{:keys [model path delete-fn] :as opts}]
  (r/with-let [editing?* (r/atom false)
               focus?*   (r/atom false)]
    (let [opts (merge opts {:editing? @editing?* :focus? @focus?*})]
      [:div.form-card.flex--between.flex--gap2
       {:tab-index 0
        :class     (when (or @editing?* @focus?*) "focus")
        :on-focus  #(reset! focus?* true)
        :on-blur   #(reset! focus?* false)}
       (->> (vals model)
            (map #(vector field (merge opts
                                       (:schema %)
                                       {:hidden-label? (not @editing?*)})))
            (into [:div.flex--wrap.flex--gap1.flex--column-gap2]))
       [:div.button-panel.flex--right.flex--gap1
        [item-buttons (merge opts {:edit-fn   #(swap! editing?* not)
                                   :remove-fn #(delete-fn path)})]]])))

(defn field-group-buttons
  "The buttons for manually adding an entry to the field group or automatically fetching them"
  [{:keys [index path fetch-fns editing? focus? disabled? create-fn] group-name :name}]
  (when (and (not disabled?)
             (or editing? focus?))
    [:<>
     [components/icon-button
      {:icon     :lupicon-plus
       :text-loc (util/kw-path :kiinteistoLista group-name :_append_label)
       :class    :primary
       :on-click #(create-fn (concat path [group-name]) {})}]
     (let [{:keys [fetch-fn ok-fn]} (get fetch-fns (keyword group-name))]
       (when (and fetch-fn
                  ok-fn
                  (ok-fn index))
         [components/icon-button
          {:icon     :lupicon-refresh
           :text-loc (util/kw-path :kiinteistoLista group-name :_fetch_label)
           :class    :secondary
           :on-click #(fetch-fn index)}]))]))

(defn field-group
  "Render a field that is actually a group of other fields (e.g. a list of owners)"
  [{:keys [loc i18nkey model focus? editing?] group-name :name :as opts}]
  (let [field-group-model (get-in model [(keyword group-name) :model])]
    (when (or focus?
              editing?
              (not-empty field-group-model))
      [:div.flex--column.flex--gap1
       [:label.form-label (loc i18nkey)]
       (->> field-group-model
            (map #(vector field-group-row (merge opts %)))
            (into [:<>]))
       [:div.flex--wrap.flex--gap1
        [field-group-buttons opts]]])))

(defn property
  "Render a card that includes a single property's fields.
  The card itself can gain focus whereupon the edit button becomes available"
  [{:keys [path] :as model} {:keys [fields delete-fn] :as opts}]
  (r/with-let [editing?* (r/atom false)
               focus?*   (r/atom false)]
    (let [opts         (merge opts {:editing? @editing?* :focus? @focus?*})
          field-group? #(-> % :type #{"table"})]
      [:div.form-card.flex--column.flex--gap2
       {:tab-index 0
        :class     (when (or @editing?* @focus?*) "focus")
        :on-focus  #(reset! focus?* true)
        :on-blur   #(reset! focus?* false)}
       [:div.flex--between.flex--column-gap7
        ;; Simple fields that are just an input
        (->> fields
             (remove field-group?)
             (map #(vector field (merge opts model %)))
             (into [:div.flex--wrap.flex--gap1.flex--column-gap2]))
        ;; Button panel on the right side
        [:div.button-panel.flex--right.flex--gap1
         [item-buttons (merge opts {:edit-fn   #(swap! editing?* not)
                                    :remove-fn #(delete-fn path)})]]]
       ;; More complex fields that have child fields of their own
       (->> fields
            (filter field-group?)
            (map #(vector field-group (merge opts model %)))
            (into [:div.flex--column.flex--gap3]))])))

(defn table
  "A table (or list rather since it doesn't have columns) with a row for each selected property.
  The table model is an array of properties:
  [{index 0, model {kiinteistotunnus {model 1234567890} ...} ...}
   {index 2, model {kiinteistotunnus {model 1334434323} ...} ...}
   ...]
   Where the index is a side-effect of the way documents with repeating groups are handled"
  [{:keys [properties disabled? path create-fn] :as opts}]
  [:div.flex--column.flex--gap2
   (->> properties
        (sort-by #(get % "index")) ; ascending order of creation
        (mapv #(vector property % opts))
        (into [:<>]))
   (when-not disabled?
     [:div
      [components/icon-button
       {:class     :positive
        :text-loc  :kiinteistoLista._append_label
        :icon      :lupicon-plus
        :on-click  #(create-fn path {})}]])])
