(ns lupapalvelu.ui.admin.import-export.operations-attachment
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.admin.import-export.impl :as impl]
            [lupapalvelu.ui.admin.import-export.subs :as subs]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.reselect :as reselect]
            [re-frame.core :as rf]
            [sade.util :as util]
            [clojure.string :as ss]))

(rf/reg-sub ::operations-attachment-items
  :<- [::subs/items]
  (fn [items]
    (->> items
         (map (fn [[id attachments]]
                {:value id
                 :text  (str (common/loc (util/kw-path :operations id))
                             " ("
                             (count attachments)
                             " kpl): "
                             (->> attachments
                                  (map (fn [[id group]]
                                         (common/loc (util/kw-path :attachmentType id group))))
                                  (ss/join ", ")))}))
         (sort-by :text)
         (into []))))

(defn- attachment-selector
  []
  [:div.w--50
   [reselect/reselect
    (<sub [::subs/selected-items])
    {:id        "import-export-phrase-category-select"
     :multi?    true
     :items     (<sub [::operations-attachment-items])
     :callback  #(>evt [::subs/items-selected %])}]])

(defmethod impl/-select-items :operations-attachment
  [_]
  [attachment-selector])

(defn- render-attachments
  [items]
  (if (seq items)
    [:table
     [:thead
      [:tr.strong
       [:td "Toimenpide"]
       [:td "Liitteet"]]]
     (->> items
          (map (fn [[id attachments]]
                 [:tr
                  [:td (common/loc (util/kw-path :operations id))]
                  [:td
                   (->> attachments
                        (map (fn [[id group]]
                               [:li (common/loc (util/kw-path :attachmentType id group))]))
                        (into [:ul]))]]))
          (into [:tbody]))]
    [:span "ei oletusliitteitä"]))

(defmethod impl/-render-items :operations-attachment
  [_ items]
  [render-attachments items])
