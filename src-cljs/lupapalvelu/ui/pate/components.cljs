(ns lupapalvelu.ui.pate.components
  "More or less Pate-specific user interface components. See
  layout.cljs for documentation on the component conventions. Docgen
  support is in docgen.cljs."
  (:require [clojure.set :as set]
            [clojure.string :as s]
            [lupapalvelu.pate.shared :as shared]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.pate.docgen :as docgen]
            [lupapalvelu.ui.pate.path :as path]
            [lupapalvelu.ui.pate.phrases :as phrases]
            [lupapalvelu.ui.pate.service :as service]
            [lupapalvelu.ui.pate.state :as state]
            [rum.core :as rum]
            [sade.shared-util :as util]))

(defn show-label? [{label? :label?} wrap-label?]
  (and wrap-label? (not (false? label?))))

(rum/defc pate-date-delta < rum/reactive
  [{:keys [state path schema] :as options}  & [wrap-label?]]
  [:div.pate-date-delta
   (when (show-label? schema wrap-label?)
     [:div.delta-label (path/loc options)])
   [:div.delta-editor
    (docgen/text-edit (assoc options :path (path/extend path :delta))
                      :input.grid-style-input
                      {:type "number"
                       :disabled (path/disabled? options)})
    (common/loc (str "pate-date-delta." (-> schema :unit name)))]])


(rum/defc pate-multi-select < rum/reactive
  [{:keys [state path schema] :as options}  & [wrap-label?]]
  [:div.pate-multi-select
   (when (show-label? schema wrap-label?)
     [:h4.pate-label (path/loc options)])
   (let [state (path/state path state)
         items (cond->> (map (fn [item]
                               (if (:value item)
                                 item
                                 (let [item (keyword item)]
                                   {:value item
                                    :text  (if-let [item-loc (:item-loc-prefix schema)]
                                             (path/loc [item-loc item])
                                             (path/loc options item))})))
                             (:items schema))
                 (not (-> schema :sort? false? )) (sort-by :text))]

     (for [{:keys [value text]} items
           :let                 [item-id (path/unique-id "multi")
                                 checked (util/includes-as-kw? (set (rum/react state)) value)]]
       [:div.pate-checkbox-wrapper
        {:key item-id}
        [:input {:type    "checkbox"
                 :id      item-id
                 :disabled (path/disabled? options)
                 :checked checked}]
        [:label.pate-checkbox-label
         {:for      item-id
          :on-click (fn [_]
                      (swap! state
                             (fn [xs]
                               ;; Sanitation on the client side.
                               (util/intersection-as-kw (if checked
                                                          (remove #(util/=as-kw value %) xs)
                                                          (cons value xs))
                                                        (map :value items))))
                      (path/meta-updated options))}
         text]]))])

(defn- resolve-reference-list
  "List of :value, :text maps"
  [{:keys [path schema references] :as options}]
  (let [{:keys [item-key item-loc-prefix term]}        schema
        {:keys [extra-path match-key] term-path :path} term
        extra-path                                     (path/pathify extra-path)
        term-path                                      (path/pathify term-path)
        match-key                                      (or match-key item-key)]
    (->> (path/value (path/pathify (:path schema)) references )
         (remove :deleted) ; Safe for non-maps
         (map (fn [x]
                (let [v (if item-key (item-key x) x)]
                  {:value v
                   :text  (cond
                            (and match-key term-path) (-> (util/find-by-key match-key
                                                                            v
                                                                            (path/value term-path
                                                                                        references))
                                                          (get-in (cond->> [(keyword (common/get-current-language))]
                                                                    extra-path (concat extra-path))))
                            item-loc-prefix           (path/loc [item-loc-prefix v])
                            :else                     (path/loc options v))})))
         distinct)))

(rum/defc select-reference-list < rum/reactive
  [{:keys [schema references] :as options} & [wrap-label?]]
  (let [options   (assoc options
                         :schema (assoc schema
                                        :body [{:sortBy :displayName
                                                :body (map #(hash-map :name %)
                                                           (distinct (path/react (path/pathify (:path schema))
                                                                                 references)))}]))
        component (docgen/docgen-select options)]
    (if (show-label? schema wrap-label?)
      (docgen/docgen-label-wrap options component)
      component)))

(rum/defc multi-select-reference-list < rum/reactive
  [{:keys [schema] :as options} & [wrap-label?]]
  (pate-multi-select (assoc-in options [:schema :items] (resolve-reference-list options))
                      wrap-label?))

(rum/defc last-saved < rum/reactive
  [{info* :info}]
  [:span.saved-info
   (when-let [ts (path/react [:modified] info*)]
     (common/loc :pate.last-saved (js/util.finnishDateAndTime ts)))])

(rum/defcs pate-phrase-text < rum/reactive
  (rum/local nil ::category)
  (rum/local "" ::selected)
  (rum/local "" ::replaced)
  [{category* ::category
    selected* ::selected
    replaced* ::replaced :as local-state}
   {:keys [state path schema] :as options} & [wrap-label?]]
  (letfn [(set-category [category]
            (when (util/not=as-kw category @category*)
              (reset! category* (name (or category "")))
              (reset! selected* "")))
          (update-text [text]
            (reset! (path/state path state) text)
            (path/meta-updated options))]
    (when-not @category*
      (set-category (:category schema)))
    (let [ref-id    (path/unique-id "-ref")
          disabled? (path/disabled? options)]
      [:div.pate-grid-12
       (when (show-label? schema wrap-label?)
         [:h4.pate-label (path/loc options)])
       [:div.row
        [:div.col-3.col--full
         [:div.col--vertical
          [:label (common/loc :phrase.add)]
          (phrases/phrase-category-select @category*
                                          set-category
                                          {:disabled? disabled?})]]
        [:div.col-5
         [:div.col--vertical
          (common/empty-label)
          (components/autocomplete selected*
                                   {:items     (phrases/phrase-list-for-autocomplete (rum/react category*))
                                    :callback  #(let [text-node (.-firstChild (rum/ref local-state ref-id) )
                                                      sel-start (.-selectionStart text-node)
                                                      sel-end   (.-selectionEnd text-node)
                                                      old-text  (or (path/value path state) "")]
                                                  (reset! replaced* (subs old-text sel-start sel-end))
                                                  (update-text (s/join (concat (take sel-start old-text)
                                                                               %
                                                                               (drop sel-end old-text)))))
                                    :disabled? disabled?})]]
        [:div.col-4.col--right
         [:div.col--vertical
          (common/empty-label)
          [:div.inner-margins
           [:button.primary.outline
            {:on-click (fn []
                         (update-text "")
                         (reset! selected* ""))
             :disabled disabled?}
            (common/loc :pate.clear)]
           [:button.primary.outline
            {:disabled (let [phrase (rum/react selected*)]
                         (or disabled?
                             (s/blank? phrase)
                             (not (re-find (re-pattern (goog.string/regExpEscape phrase))
                                           (path/react path state)))))
             :on-click (fn []
                         (update-text (s/replace-first (path/value path state)
                                                       @selected*
                                                       @replaced*))
                         (reset! selected* ""))}
            (common/loc :phrase.undo)]]]]]
       [:div.row
        [:div.col-12.col--full
         {:ref ref-id}
         (components/textarea-edit (path/state path state)
                                   {:callback update-text
                                    :disabled disabled?})]]])))
