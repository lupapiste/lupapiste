(ns lupapalvelu.ui.components.reselect
  (:require [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.react-select :as re-select]
            [lupapalvelu.ui.misc]
            [rum.core :as rum]
            [sade.shared-schemas :as ssc]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :as sc]))

(sc/defschema Id "internal id" ssc/NonBlankStr)
(sc/defschema Value "external id/value" sc/Any)

(sc/defschema Option
  (st/open-schema
    {:label                     ssc/NonBlankStr
     (sc/optional-key :value)   Id
     (sc/optional-key :options) [(sc/recursive #'Option)]}))

(sc/defschema LookupTable
  "Translate internal ID to external value (and options)"
  {Id {:value  Value
       :option Option}})

(sc/defschema Options+LookupTable
  {:lut     LookupTable
   :options [Option]})

(sc/defn make-options-and-lut :- Options+LookupTable
  [items sort?]
  (cond-> (reduce
            (fn [acc {:keys [value text text-loc flags items]}]
              (let [;; Check if this item has sub-items
                    {:keys [options lut]} (when (seq items)
                                            (make-options-and-lut items sort?))
                    ;; Convert external value (clojure data) to a js-friendly internal id (= string)
                    id                    (when value ; Group items have no value themselves
                                            (pr-str value))
                    option                (cond-> {:label (or text (common/loc text-loc))}
                                            id          (assoc :value id)
                                            (seq flags) (merge (zipmap flags (cycle [true])))
                                            options     (assoc :options options))]
                (-> acc
                    (update :lut merge lut (when id {id (util/assoc-when {:option option}
                                                                         :value value)}))
                    (update :options conj option))))
            {:lut     {}
             :options []}
            items)
    sort? (update :options (fn [opts]
                             (->> opts
                                  (sort-by :label js/util.localeComparator)
                                  (into []))))))

(defn- id->option
  [id {:keys [lut] :as options}]
  (when id
    (or (get-in lut [id :option])
        (when (and ^boolean js/goog.DEBUG (seq lut))
          ;; Sometimes the `lut` might be stale for a single render cycle and
          ;; we might issue a false positive warning from these check(s).
          (.warn js/console "No option found!" {:id      id
                                                :options options})))))

(defn- id->value
  [id {:keys [lut] :as options}]
  (when id
    (or (get-in lut [id :value])
        (when (and ^boolean js/goog.DEBUG (seq lut))
          (.warn js/console "No value found!" {:id      id
                                               :options options})))))
(defn- value->id
  [value {:keys [lut] :as options}]
  (when value
    (or (some (fn [[id m]]
                ;; Unfortunately some callers might provide the current value
                ;; as a string even though the `:value`s in the selectable items
                ;; are keywords
                (when (or (= (:value m) value)
                          (= (:value m) (keyword value)))
                  id)) lut)
        (when (and ^boolean js/goog.DEBUG (seq lut))
          (.warn js/console "No id found!" {:value   value
                                            :options options})))))

(defn- grouped-items?
  "Check if any of the items requires grouping"
  [items]
  (boolean (some :group items)))

(defn- group-items
  "Group items according to `:group`."
  [items]
  (->> items
       (group-by :group)
       (reduce
         (fn [acc [group items]]
           (conj acc {:text  group
                      :items (mapv #(dissoc % :group) items)}))
         [])))

(rum/defc reselect
  "Controlled wrapper for `react-select`.

   Parameters: ctrl-value, options

   Options [optional]:

    `items` An item can have `:text/text-loc` key (option label) and either `:value` or
            `:items`, where the latter is the group contents. Only one-level groups are
            supported (`:group`). `:value` keys correspond to `ctrl-value` and `callback`
            argument. To avoid unnecessary calculations, try to make sure `items` remains
            `identical?` between renders (e.g. use subscriptions to return the same instance).

    [`callback`]   The function called on selection change.  Receives either a value or a
                   collection of values.
    [`required?`]  See `common/required-invalid-attributes`
    [`invalid?`]   See `common/required-invalid-attributes`
    [`disabled?`]  See `common/resolve-disabled`
    [`enabled?`]   See `common/resolve-disabled`
    [`sort?`]      Should items be sorted alphabetically, defaults to `true`.

    [`render?`] If false, the selected values are never rendered in the component. This is
    useful, when the selection results are rendered outside of the component (e.g.,
    separate tags view). Default is false.

    [`choose-loc`] Empty selection text-loc (default `:choose`)
    [`clear?`]     Show clear button (default false)

    [`multi?`] Multi-selection (default false). When true, `ctrl-value` is interpreted
               as a collection of values.

    [`id`]         Control id that can be used as a label `for` target.
    [`class`]      Additional classes for the wrapper div. Resolved with `common/css`."
  [ctrl-value {:keys [aria-label callback choose-loc clear? items multi? render? sort?]
               :or   {sort? true}
               :as   options}]
  (let [[id set-id!]         (rum/use-state (if multi? #{} nil))
        render?              (if (false? render?) false true)
        [opts+lut set-opts!] (rum/use-state {:options [] :lut {}})
        {:keys [aria-invalid
                required]}   (common/required-invalid-attributes options id)]
    (rum/use-effect! (fn []
                       (set-id! (if multi?
                                  (set (map #(value->id % opts+lut) ctrl-value))
                                  (value->id ctrl-value opts+lut)))
                       js/undefined)
                     [ctrl-value opts+lut multi?])
    (rum/use-effect! (fn []
                       ;; Calculating the options+lut might be expensive if the amount of
                       ;; items is large. Rum uses `identical?` to compare previous/current values
                       ;; so try to make sure to reuse the same instance of items between renders.
                       (set-opts! (cond-> items
                                    (grouped-items? items) (group-items)
                                    true                   (make-options-and-lut sort?)))
                       js/undefined)
                     [items])
    [:div
     {:class (common/css (:class options))}
     (re-select/select (-> {:aria-invalid             aria-invalid
                            :aria-label               aria-label
                            ;; In order to enforce component control (no local
                            ;; state), we do not render nil values.

                            :controlShouldRenderValue (and (some? id) render?)
                            :inputId                  (:id options)
                            :isClearable              clear?
                            :isDisabled               (common/resolve-disabled options)
                            :isMulti                  multi?
                            :isRequired               required
                            :noOptionsMessage         (constantly (common/loc :no-more-choices))
                            :onChange                 (fn [v]
                                                        (let [get-fn (comp #(id->value % opts+lut) #(common/oget % "value"))
                                                              v      (if multi?
                                                                       (set (map get-fn v))
                                                                       (get-fn v))]
                                                          (when callback
                                                            (callback v))))
                            :options                  (:options opts+lut)
                            :placeholder              (common/loc (or choose-loc :choose))
                            :value                    (if multi?
                                                        (vec (keep #(id->option % opts+lut) id))
                                                        (id->option id opts+lut))}
                           util/strip-nils))]))
