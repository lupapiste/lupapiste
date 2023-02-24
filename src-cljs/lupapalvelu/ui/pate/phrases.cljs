(ns lupapalvelu.ui.pate.phrases
  (:require [clojure.set :as set]
            [clojure.string :as s]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.pate.service :as service]
            [lupapalvelu.ui.pate.state :as state]
            [rum.core :as rum]
            [sade.shared-util :as util]))

(defn phrase-list-for-autocomplete [category]
  (->> @state/phrases
       (filter #(util/=as-kw category (:category %)))
       (map (fn [{:keys [tag phrase]}]
              {:text (str tag " - " phrase)
               :value phrase}))))

(defn non-empty-categories []
  (filter #(util/find-by-key :category (name %)
                             @state/phrases)
          @state/phrase-categories))

(defn- custom-category? [category]
  (not (util/includes-as-kw? (rum/react state/phrase-categories) category)))

(defn- category-text [category]
  (if (custom-category? category)
    (:fi ((keyword category) @state/custom-phrase-categories))
    (path/loc [:phrase.category category])))

(defn- add-custom-categories [items]
  (concat items (map (fn [category] {:value (name (key category)) :text (:fi (val category))}) @state/custom-phrase-categories)))

(defn- filled? [value]
  (and (some? value)
       (not (s/blank? value))))

(rum/defc phrase-category-select < rum/reactive
  [selected callback {:keys [include-empty? disabled? test-id]}]
  (let [items  (->> (if include-empty?
                     (rum/react state/phrase-categories)
                     (non-empty-categories))
                   (map name)
                   (map (fn [n] {:value n :text (category-text n)}))
                   (add-custom-categories)
                   (sort-by :text))
        select (fn [value]
                 ;; Nil and "" are treated as equal.
                 (when (not-empty value)
                   (callback value)))
        items  (if (contains? (set (map :value items)) selected)
                items
                ;; if selected is not one of the possible items, display placeholder option
                (conj items {:value selected :text (common/loc :selectone)}))]
    [:select.dropdown
     (common/add-test-id {:value      selected
                          :disabled   disabled?
                          :aria-label (common/loc :phrase.category)
                          :on-change  #(select (.. % -target -value))}
                         (or test-id :select-phrase-category))
     (for [{:keys [value text]} items]
       [:option {:key value :value value} text])]))

(rum/defcs phrase-editor < rum/reactive
  (rum/local ::edit ::tab)
  (rum/local false ::phrase-category?)
  (rum/local nil ::new-category)
  (rum/local "" ::selected-category)
  (rum/local {} ::phrase-draft)
  {:will-mount (fn [state] (assoc state ::reset-tab (fn [id] (reset! (::tab state) id))))}
  [{tab*              ::tab
    phrase-category?* ::phrase-category?
    new-category*     ::new-category
    phrase-draft*     ::phrase-draft
    switch-tab        ::reset-tab} phrase on-success]
  [:div.pate-grid-8
   [:div.row
    [:div.col-2
     [:div.col--vertical
      [:label (common/loc :phrase.category)]
      (phrase-category-select (or (:category @phrase-draft*) (:category phrase))
                              (fn [selected]
                                (swap! phrase-draft* assoc :category selected))
                              {:include-empty? true})]]
    [:div.col-2
     [:div.col--vertical.col--full
      [:label.required (common/loc :phrase.tag)]
      (components/simple-text-edit
        {:value     (or @(rum/cursor-in phrase-draft* [:tag]) (:tag phrase))
         :on-change #(swap! phrase-draft* assoc :tag (-> % .-target .-value))
         :required? true
         :test-id   :phrase-tag})]]]

    (if @phrase-category?*
      [:div.pate-grid-6
       [:div.row
        [:div.col-1
         [:div.col--vertical
          [:label.required (common/loc :pate-verdict.language.fi)]
          (components/text-edit (:fi @new-category*)
                                   {:callback   (fn [text]
                                                  (swap! new-category* #(assoc % :fi text)))
                                    :required?  true})]]
        [:div.col-1
         [:div.col--vertical
          [:label.required (common/loc :pate-verdict.language.sv)]
          (components/text-edit (:sv @new-category*)
                                   {:callback   (fn [text]
                                                  (swap! new-category* #(assoc % :sv text)))
                                    :required?  true})]]
        [:div.col-1
         [:div.col--vertical
          [:label.required (common/loc :pate-verdict.language.en)]
          (components/text-edit (:en @new-category*)
                                   {:callback   (fn [text]
                                                  (swap! new-category* #(assoc % :en text)))
                                    :required?  true})]]]
       [:div.row
        [:div.col-6
         [:div.col--vertical
          [:div.col-2.inner-margins
           (components/icon-button {:icon     :lupicon-save
                                    :text-loc :save
                                    :class    :positive
                                    :enabled? (and (filled? (:fi @new-category*))
                                                   (filled? (:en @new-category*))
                                                   (filled? (:sv @new-category*)))
                                    :on-click (fn [_]
                                                (service/save-phrase-category @new-category*)
                                                (on-success))
                                    :test-id  :save-phrase-category})
           [:button.primary.outline
            (common/add-test-id {:on-click #(swap! phrase-category?* not)} :cancel-phrase-category)
            (common/loc :cancel)]]]]]]

      [:div.row
       [:div.col-6
        [:div.col--vertical
          [:div.col-2.inner-margins
            (components/icon-button
              {:icon       :lupicon-circle-plus
               :text-loc   :pate.add-phrase-category
               :class      :positive
               :on-click   (fn [_] (swap! phrase-category?* not))})
            (when (custom-category? (or (:category @phrase-draft*) (:category phrase)))
              (components/icon-button
                {:icon       :lupicon-circle-plus
                 :text-loc   :pate.remove-phrase-category
                 :class      :negative
                 :on-click   (fn []
                               (components/confirm-dialog
                                 :phrase.category.remove
                                 :areyousure
                                 (fn []
                                   (service/delete-phrase-category (or (:category @phrase-draft*)
                                                                       (:category phrase)))
                                   (on-success)))
                               )}))]]]])
   [:div.row
    [:div.col-8
     [:div.col--full
      (components/tabbar {:selected @tab*
                          :on-click switch-tab
                          :tabs     [{:text-loc :pate.edit-tab
                                      :id       ::edit
                                      :test-id  :edit-phrase-tab}
                                     {:text-loc :pdf.preview
                                      :id       ::preview
                                      :test-id  :preview-phrase-tab}]})
      [:div.phrase-edit
       (if (= (rum/react tab*) ::preview)
         (components/markup-span (or (:phrase @phrase-draft*) (:phrase phrase)) :phrase-preview)
         (components/simple-textarea-edit
           {:value     (or @(rum/cursor-in phrase-draft* [:phrase]) (:phrase phrase))
            :on-change #(swap! phrase-draft* assoc :phrase (-> % .-target .-value))
            :required? true
            :test-id   :edit-phrase}))]]]]
   [:div.row
    [:div.col-2.inner-margins
     (components/icon-button {:icon      :lupicon-save
                              :text-loc  :save
                              :class     :positive
                              :disabled? (->> @phrase-draft*
                                              (merge phrase) ; merge data with initially selected phrase
                                              vals
                                              (some s/blank?))
                              :on-click  (fn []
                                           (service/upsert-phrase
                                             (set/rename-keys (merge phrase @phrase-draft*) {:id :phrase-id})
                                             on-success))
                              :test-id   :save-phrase})
     [:button.primary.outline
      (common/add-test-id {:on-click on-success} :cancel-phrase)
      (common/loc :cancel)]]
    (when-let [phrase-id (:id phrase)]
      [:div.col-6.col--right
       (components/icon-button
        {:icon     :lupicon-remove
         :text-loc :remove
         :class    :secondary
         :on-click (fn []
                     (components/confirm-dialog
                      :phrase.remove
                      :areyousure
                      (fn []
                        (service/delete-phrase phrase-id on-success))))
         :test-id  :delete-phrase})])]])

(rum/defc phrase-sorter < rum/reactive
  [column sort-column* descending?*]
  (let [sort-key? (util/=as-kw (rum/react sort-column*)
                               column)
        descending? (rum/react descending?*)]
    [:th (common/add-test-id {:on-click (fn []
                                          (if sort-key?
                                            (swap! descending?* not)
                                            (do
                                              (reset! sort-column* column)
                                              (reset! descending?* false))))}
                             (str "sort-by-" (name column)))
     [:div.like-btn [:span (path/loc [:phrase column])]
      [:i {:class (common/css-flags :lupicon-chevron-small-up (and sort-key?
                                                                   (not descending?))
                                    :lupicon-chevron-small-down (and sort-key?
                                                                     descending?)
                                    :icon-placeholder (not sort-key?))}]]]))

(defn- phrase-comparator [k a b]
  (js/util.localeComparator (-> a k s/lower-case)
                            (-> b k s/lower-case)))

(rum/defcs phrases-table < rum/reactive
  (rum/local nil ::sort-column)
  (rum/local false ::descending?)
  (rum/local nil ::phrase)
  [{sort-column* ::sort-column
    descending?* ::descending?
    phrase*      ::phrase}]
  [:div.pate-phrases
   [:h2 (common/loc :phrase.title)]
   (if-let [phrase @phrase*]
     (phrase-editor phrase #(reset! phrase* nil))
     [:div
      (when (seq (rum/react state/phrases))
        [:table.pate-phrases-table
         [:thead
          [:tr
           (phrase-sorter :tag sort-column* descending?*)
           (phrase-sorter :category sort-column* descending?*)
           (phrase-sorter :phrase sort-column* descending?*)]]
         [:tbody
          (let [phrases (cond->> (sort (partial phrase-comparator
                                                (keyword (or (rum/react sort-column*)
                                                             :tag)))
                                       (rum/react state/phrases))
                          (rum/react descending?*) reverse
                          true                     (map-indexed #(assoc %2 :index %1)))]
            (for [{:keys [tag category phrase id index] :as phrase-map} phrases]
              [:tr {:key id}
               (common/add-test-id [:td tag]
                                   (str "phrase-tag-" index))
               (common/add-test-id [:td.category (category-text category)]
                                   (str "phrase-category-" index))
               [:td.phrase [:a.link-btn
                            (common/add-test-id {:on-click #(reset! phrase* phrase-map)}
                                                (str "phrase-text-" index))
                            phrase]]]))]])
      (components/icon-button {:icon     :lupicon-circle-plus
                               :class    :positive
                               :text-loc :phrase.add
                               :enabled? (state/auth? :upsert-phrase)
                               :test-id  :add-phrase
                               :on-click #(reset! phrase* {:tag      ""
                                                           :category "paatosteksti"
                                                           :phrase   ""})})])])
