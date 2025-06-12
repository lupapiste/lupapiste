(ns lupapalvelu.ui.admin.import-export.inspection-summary
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.admin.import-export.impl :as impl]
            [lupapalvelu.ui.admin.import-export.subs :as subs]
            [lupapalvelu.ui.components.reselect :as reselect]
            [re-frame.core :as rf]))

(rf/reg-sub ::inspection-summary-items
  :<- [::subs/items]
  (fn [items]
    (->> items
         (map (fn [[id {:keys [name modified]}]]
                {:value id
                 :text (str name " (" (js/util.finnishDateAndTime modified) ")")}))
         (sort-by :text)
         (into []))))

(defn- inspection-summary-selector
  []
  [:div.w--50
   [reselect/reselect
    (<sub [::subs/selected-items])
    {:id        "import-export-inspection-summary-select"
     :multi?    true
     :clear?    true
     :disabled? (<sub [::subs/select-all?])
     :items     (<sub [::inspection-summary-items])
     :callback  #(>evt [::subs/items-selected %])}]])

(defmethod impl/-select-items :inspection-summary
  [_]
  [inspection-summary-selector])

(defn- render-inspection-summaries
  [items]
  (if (seq items)
    [:table
     [:thead
      [:tr.strong
       [:td "Nimi"]
       [:td "Sääntöjä"]
       [:td "Muokattu"]]]
     (->> items
          (map (fn [[_id {:keys [name items modified]}]]
                 [:tr
                  [:td name]
                  [:td (count items)]
                  [:td (js/util.finnishDateAndTime modified)]]))
          (into [:tbody]))]
    [:span "ei tarkastusasiakirjojen pohjia"]))

(defmethod impl/-render-items :inspection-summary
  [_ items]
  [render-inspection-summaries items])
