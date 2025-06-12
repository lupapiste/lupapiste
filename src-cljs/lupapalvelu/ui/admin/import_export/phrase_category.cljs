(ns lupapalvelu.ui.admin.import-export.phrase-category
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.admin.import-export.impl :as impl]
            [lupapalvelu.ui.admin.import-export.subs :as subs]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.reselect :as reselect]
            [re-frame.core :as rf]))

(rf/reg-sub ::phrase-category-items
  :<- [::subs/items]
  (fn [items]
    (let [lang (keyword (common/get-current-language))]
      (->> items
           (map (fn [[id {:keys [fi sv en] :as item}]]
                  {:value id
                   :text  (or (get item lang)
                              fi sv en)}))
           (sort-by :text)
           (into [])))))

(defn- category-selector
  []
  [:div.w--50
   [reselect/reselect
    (<sub [::subs/selected-items])
    {:id        "import-export-phrase-category-select"
     :multi?    true
     :items     (<sub [::phrase-category-items])
     :callback  #(>evt [::subs/items-selected %])}]])

(defmethod impl/-select-items :phrase-category
  [_]
  [category-selector])

(defn- render-categories
  [items]
  (if (seq items)
    [:table
     [:thead
      [:tr.strong
       [:td "id"]
       [:td "FI"]
       [:td "SV"]
       [:td "EN"]]]
     (->> items
          (map (fn [[id {:keys [fi sv en]}]]
                 [:tr
                  [:td id]
                  [:td fi]
                  [:td sv]
                  [:td en]]))
          (into [:tbody]))]
    [:span "ei fraasikategorioita"]))

(defmethod impl/-render-items :phrase-category
  [_ items]
  [render-categories items])
