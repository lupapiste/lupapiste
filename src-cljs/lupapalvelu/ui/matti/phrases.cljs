(ns lupapalvelu.ui.matti.phrases
  (:require [clojure.set :as set]
            [clojure.string :as s]
            [lupapalvelu.matti.shared :as shared]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.matti.path :as path]
            [lupapalvelu.ui.matti.service :as service]
            [lupapalvelu.ui.matti.state :as state]
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
          shared/phrase-categories))

(defn- category-text [category]
  (path/loc [:phrase.category category]))

(rum/defcs phrase-category-select < rum/reactive
  (components/initial-value-mixin ::selected)
  [{selected* ::selected} _ callback & [include-empty?]]
  (let [items (->> (if include-empty?
                     shared/phrase-categories
                     (non-empty-categories))
                   (map name)
                   (map (fn [n] {:value n :text (category-text n)}))
                   (sort-by :text))
        select (fn [value]
                 (reset! selected* value)
                 (callback value))]
    (when-not (util/includes-as-kw? (map :value items) @selected*)
      (select (-> items first :value)))
    [:select.dropdown
     {:value  @selected*
      :on-change #(select (.. % -target -value))}
     (map (fn [{:keys [value text]}]
            [:option {:key value :value value} text])
          items)]))

(rum/defcs phrase-editor < rum/reactive
  (components/initial-value-mixin ::local)
  [{local* ::local} _ phrase*]
  [:div.matti-grid-8
   [:div.row
    [:div.col-2
     [:div.col--vertical
      [:label (common/loc :phrase.category)]
      (phrase-category-select (:category @local*)
                              (fn [selected]
                                (swap! local*
                                       #(assoc % :category selected)))
                              true)]]
    [:div.col-2
     [:div.col--vertical.col--full
      [:label.required (common/loc :phrase.tag)]
      (components/text-edit (:tag @local*)
                            {:callback   (fn [text]
                                           (swap! local* #(assoc % :tag text)))
                             :required?  true
                             :immediate? true})]]]
   [:div.row
    [:div.col-8
     [:div.col--vertical.col--full
      [:label.required (common/loc :phrase.phrase)]
      (components/textarea-edit (:phrase @local*)
                                {:callback   (fn [text]
                                               (swap! local* #(assoc % :phrase text)))
                                 :required?  true
                                 :immediate? true})]]]
   [:div.row
    [:div.col-2.inner-margins
     [:button.positive
      {:disabled (->> (rum/react local*)
                      vals
                      (some (comp s/blank? s/trim)))
       :on-click (fn []
                   (service/upsert-phrase (set/rename-keys @local* {:id :phrase-id})
                                          #(reset! phrase* nil)))}
      (common/loc :save)]
     [:button.primary.outline
      {:on-click #(reset! phrase* nil)}
      (common/loc :cancel)]]
    (when-let [phrase-id (:id @local*)]
      [:div.col-6.col--right
       [:button.secondary
        {:on-click (fn []
                     (components/confirm-dialog :phrase.remove
                                                :areyousure
                                                (fn []
                                                  (service/delete-phrase phrase-id
                                                                         #(reset! phrase* nil)))))}
        (common/loc :remove)]])]])

(rum/defc phrase-sorter < rum/reactive
  [column sort-column* descending?*]
  (let [sort-key? (util/=as-kw (rum/react sort-column*)
                               column)
        descending? (rum/react descending?*)]
    [:th {:on-click (fn []
                      (if sort-key?
                        (swap! descending?* not)
                        (do
                          (reset! sort-column* column)
                          (reset! descending?* false))))}
     [:div.like-btn [:span (path/loc [:phrase column])]
      [:i {:class (common/css-flags :lupicon-chevron-small-up (and sort-key?
                                                                   (not descending?))
                                    :lupicon-chevron-small-down (and sort-key?
                                                                     descending?)
                                    :icon-placeholder (not sort-key?))}]]]))

(rum/defcs phrases-table < rum/reactive
  (rum/local "" ::sort-column)
  (rum/local true ::descending?)
  (rum/local nil ::phrase)
  [{sort-column* ::sort-column
    descending?* ::descending?
    phrase* ::phrase}]
  [:div.matti-phrases
   [:h2 (common/loc :phrase.title)]
   (if (rum/react phrase*)
     (phrase-editor @phrase* phrase*)
     [:div
      (when (seq (rum/react state/phrases))
        [:table.matti-phrases-table
         [:thead
          [:tr
           (phrase-sorter :tag sort-column* descending?*)
           (phrase-sorter :category sort-column* descending?*)
           (phrase-sorter :phrase sort-column* descending?*)]]
         [:tbody
          (let [phrases (cond-> (sort-by (or (keyword (rum/react sort-column*)) identity)
                                         (rum/react state/phrases))
                          (rum/react descending?*) reverse)]
            (for [{:keys [tag category phrase id] :as phrase-map} phrases]
              [:tr {:key id}
               [:td tag]
               [:td (category-text category)]
               [:td.phrase [:a.link-btn
                            {:on-click #(reset! phrase* phrase-map)}
                            phrase]]]))]])
      [:button.positive
       {:on-click #(reset! phrase* {:tag "" :category "paatosteksti" :phrase ""})}
       [:i.lupicon-circle-plus]
       [:span (common/loc :phrase.add)]]])])
