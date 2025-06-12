(ns lupapalvelu.ui.admin.import-export.verdict-template
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.admin.import-export.impl :as impl]
            [lupapalvelu.ui.admin.import-export.subs :as subs]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.reselect :as reselect]
            [re-frame.core :as rf]))

(defn unwrap
  [m k]
  (let [v (get m k)]
    (if (map? v)
      (:_value v)
      v)))

(defn- template->text
  [{:keys [category published] :as template}]
  (let [name     (unwrap template :name)
        deleted? (unwrap template :deleted)]
    (str name
         ", " (common/loc (str "pate-" category))
         (if (-> published :published)
           ", JULKAISTU"
           ", LUONNOS")
         (when deleted?
           ", POISTETTU"))))

(rf/reg-sub ::template-items
  :<- [::subs/items]
  (fn [items]
    (->> items
         (map (fn [[id template]]
                {:value id
                 :text  (template->text template)}))
         (sort-by :text)
         (into []))))

(defn- verdict-template-selector
  []
  [:div.w--50
   [reselect/reselect
    (<sub [::subs/selected-items])
    {:id        "import-export-verdict-template-category-select"
     :multi?    true
     :clear?    true
     :items     (<sub [::template-items])
     :callback  #(>evt [::subs/items-selected %])}]])

(defmethod impl/-select-items :verdict-template
  [_]
  [verdict-template-selector])

(defn- render-verdict-templates
  [items]
  (if (seq items)
    [:table
     [:thead
      [:tr.strong
       [:td.w--20 "Kategoria"]
       [:td.w--60 "Nimi"]
       [:td "Julkaistu"]
       [:td "Poistettu"]]]
     (->> items
          (sort-by :id)
          (map (fn [[id {:keys [category published] :as template}]]
                 (let [name     (unwrap template :name)
                       deleted? (unwrap template :deleted)]
                   ^{:key id}
                   [:tr
                    [:td (common/loc (str "pate-" category))]
                    [:td name]
                    [:td (if (:published published)
                           "Kyllä" "Ei")]
                    [:td (if deleted?
                           "Kyllä" "Ei")]])))
          (into [:tbody]))]
    [:span "ei valittavia päätöspohjia"]))

(defmethod impl/-render-items :verdict-template
  [_ items]
  [render-verdict-templates items])
