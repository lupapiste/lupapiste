(ns lupapalvelu.ui.pate.phrases
  (:require [clojure.set :as set]
            [clojure.string :as s]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.pate.shared-schemas :as shared-schemas]
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
          shared-schemas/phrase-categories))

(defn- category-text [category]
  (path/loc [:phrase.category category]))

(rum/defcs phrase-category-select < rum/reactive
  (components/initial-value-mixin ::selected)
  [{selected* ::selected} _ callback & [{:keys [include-empty? disabled?]}]]
  (let [items (->> (if include-empty?
                     shared-schemas/phrase-categories
                     (non-empty-categories))
                   (map name)
                   (map (fn [n] {:value n :text (category-text n)}))
                   (sort-by :text))
        select (fn [value]
                 ;; Nil and "" are treated as equal.
                 (when (common/reset-if-needed! selected* value not-empty)
                   (callback value)))]
    (when-not (util/includes-as-kw? (map :value items) @selected*)
      (select (-> items first :value)))
    [:select.dropdown
     (common/add-test-id {:value  @selected*
                          :disabled disabled?
                          :on-change #(select (.. % -target -value))}
                         :select-phrase-category)
     (map (fn [{:keys [value text]}]
            [:option {:key value :value value} text])
          items)]))

(rum/defcs phrase-editor < rum/reactive
  (components/initial-value-mixin ::local)
  (rum/local ::edit ::tab)
  [{local* ::local
    tab*   ::tab} _ phrase*]
  [:div.pate-grid-8
   [:div.row
    [:div.col-2
     [:div.col--vertical
      [:label (common/loc :phrase.category)]
      (phrase-category-select (:category @local*)
                              (fn [selected]
                                (swap! local*
                                       #(assoc % :category selected)))
                              {:include-empty? true})]]
    [:div.col-2
     [:div.col--vertical.col--full
      [:label.required (common/loc :phrase.tag)]
      (components/text-edit (:tag @local*)
                            {:callback   (fn [text]
                                           (swap! local* #(assoc % :tag text)))
                             :required?  true
                             :immediate? true
                             :test-id    :phrase-tag})]]]
   [:div.row
    [:div.col-8
     (let [phrase (:phrase @local*)]
       [:div.col--full
        (components/tabbar {:selected* tab*
                            :tabs      [{:text-loc :pate.edit-tab
                                         :id       ::edit
                                         :test-id  :edit-phrase-tab}
                                        {:text-loc :pdf.preview
                                         :id       ::preview
                                         :test-id  :preview-phrase-tab}]})
        [:div.phrase-edit
         (if (= (rum/react tab*) ::preview)
           (components/markup-span phrase :phrase-preview)
           (components/textarea-edit phrase
                                     {:callback   (fn [text]
                                                    (swap! local*
                                                           #(assoc % :phrase text)))
                                      :required?  true
                                      :immediate? true
                                      :test-id    :edit-phrase}))]])]]
   [:div.row
    [:div.col-2.inner-margins
     (components/icon-button {:icon      :lupicon-save
                              :text-loc  :save
                              :class     :positive
                              :disabled? (->> (rum/react local*)
                                              vals
                                              (some s/blank?))
                              :on-click  (fn []
                                           (service/upsert-phrase
                                            (set/rename-keys @local* {:id :phrase-id})
                                            #(reset! phrase* nil)))
                              :test-id   :save-phrase})
     [:button.primary.outline
      (common/add-test-id {:on-click #(reset! phrase* nil)} :cancel-phrase)
      (common/loc :cancel)]]
    (when-let [phrase-id (:id @local*)]
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
                        (service/delete-phrase phrase-id
                                               #(reset! phrase* nil)))))
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
   (if (rum/react phrase*)
     (phrase-editor @phrase* phrase*)
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
