(ns lupapalvelu.ui.admin.import-export.phrase
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.admin.import-export.impl :as impl]
            [lupapalvelu.ui.admin.import-export.subs :as subs]
            [lupapalvelu.ui.components.reselect :as reselect]
            [re-frame.core :as rf]))

(rf/reg-sub ::phrase-items
  :<- [::subs/items]
  (fn [items]
    (->> items
         (map (fn [[id {:keys [phrase]}]]
                {:value id
                 :text  phrase}))
         (sort-by :text)
         (into []))))

(defn- phrase-selector
  []
  [:div.w--50
   [reselect/reselect
    (<sub [::subs/selected-items])
    {:id        "import-export-phrase-select"
     :multi?    true
     :clear?    true
     :disabled? (<sub [::subs/select-all?])
     :items     (<sub [::phrase-items])
     :callback  #(>evt [::subs/items-selected %])}]])

(defmethod impl/-select-items :phrase
  [_]
  [phrase-selector])

(defn- render-phrases
  [items]
  (if (seq items)
    [:table
     [:thead
      [:tr.strong
       [:td "Kategoria"]
       [:td "Tagi"]
       [:td.w--100 "Fraasi"]]]
     (->> items
          (map (fn [[_id {:keys [category tag phrase]}]]
                 [:tr
                  [:td category]
                  [:td tag]
                  [:td phrase]]))
          (into [:tbody]))]
    [:span "ei fraaseja"]))

(defmethod impl/-render-items :phrase
  [_ items]
  [render-phrases items])
