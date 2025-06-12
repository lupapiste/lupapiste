(ns lupapalvelu.ui.admin.import-export.price-catalogue
  (:require [clojure.string :as ss]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.admin.import-export.impl :as impl]
            [lupapalvelu.ui.admin.import-export.subs :as subs]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.reselect :as reselect]
            [re-frame.core :as rf]))

(defn- validity->str
  [{:keys [valid-from valid-until]}]
  (ss/join " "
           (cond-> []
             valid-from  (conj (js/util.finnishDate valid-from))
             true        (conj "-")
             valid-until (conj (js/util.finnishDate valid-until)))))

(defn- catalogue->text
  [{:keys [name state valid-from valid-until rows] :as catalogue}]
  (let [validity (when (or valid-from valid-until)
                   (str ", " (validity->str catalogue)))
        draft    (when (= "draft" state)
                   (str " (" (common/loc :price-catalogue.draft) ")"))
        rows     (case (count rows)
                   0 ", ei rivejä"
                   1 ", 1 rivi"
                   (str ", " (count rows) " riviä"))]
    (str name validity draft rows)))

(rf/reg-sub ::price-catalogue-items
  :<- [::subs/items]
  (fn [items]
    (->> items
         (map (fn [[id item]]
                {:value id
                 :text  (catalogue->text item)}))
         (sort-by :text)
         (into []))))

(defn- catalogue-selector
  []
  [:div.w--50
   [reselect/reselect
    (<sub [::subs/selected-items])
    {:id        "import-export-price-catalogue-select"
     :multi?    true
     :items     (<sub [::price-catalogue-items])
     :callback  #(>evt [::subs/items-selected %])}]])

(defmethod impl/-select-items :price-catalogue
  [_]
  [catalogue-selector])

(defn- render-catalogues
  [items]
  (if (seq items)
    [:table
     [:thead
      [:tr.strong
       [:td "Nimi"]
       [:td "Voimassa"]
       [:td "Rivejä"]]]
     (->> items
          (map (fn [[_id {:keys [name rows] :as item}]]
                 [:tr
                  [:td name]
                  [:td (validity->str item)]
                  [:td (str (count rows))]]))
          (into [:tbody]))]
    [:span "ei taksoja"]))

(defmethod impl/-render-items :price-catalogue
  [_ items]
  [render-catalogues items])
