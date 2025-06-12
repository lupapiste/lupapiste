(ns lupapalvelu.ui.admin.import-export.verdict-template-settings
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.admin.import-export.impl :as impl]
            [lupapalvelu.ui.admin.import-export.subs :as subs]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.reselect :as reselect]
            [re-frame.core :as rf]))

(rf/reg-sub ::settings-items
  :<- [::subs/items]
  (fn [items]
    (->> items
         (map (fn [[category _]]
                {:value category
                 :text  (common/loc (str "pate-" category))}))
         (sort-by :text)
         (into []))))

(defn- verdict-template-category-selector
  []
  [:div.w--50
   [reselect/reselect
    (<sub [::subs/selected-items])
    {:id        "import-export-verdict-template-category-select"
     :multi?    true
     :clear?    true
     :items     (<sub [::settings-items])
     :callback  #(>evt [::subs/items-selected %])}]])

(defmethod impl/-select-items :verdict-template-settings
  [_]
  [verdict-template-category-selector])

(defn- render-verdict-template-categories
  [items]
  (if (seq items)
    [:table
     [:thead
      [:tr.strong
       [:td "Päätöspohjien asetukset"]]]
     (->> items
          (map (fn [[category _]]
                 [:tr
                  [:td (common/loc (str "pate-" category))]]))
          (into [:tbody]))]
    [:span "ei valittavia päätöspohjien asetuksia"]))

(defmethod impl/-render-items :verdict-template-settings
  [_ items]
  [render-verdict-template-categories items])
