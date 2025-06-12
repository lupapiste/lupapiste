(ns lupapalvelu.ui.application.designer-view
  (:require ["react" :as react]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.application.data :as data]
            [lupapalvelu.ui.common :refer [loc]]
            [lupapalvelu.ui.dom :as ui-dom]))

(defn view
  []
  (let [suunnittelijat (<sub [::data/designer-applications])]
    (react/useEffect (fn []
                       (>evt [::data/fetch-designer-applications])
                       js/undefined)
                     #js [])
    [:<>
     [:div
      [:h2 (loc :suunnittelijat)]]
     [:table
      [:thead
       [:tr
        [:th (loc :foreman.status)]
        [:th (loc :name)]
        [:th (loc :osapuoli.suunnittelija.kuntaRoolikoodi._group_label)]]]
      [:tbody
       (for [{:keys [id state henkilotiedot kuntaRoolikoodi muuSuunnittelijaRooli]} suunnittelijat]
         [:tr
          {:key id}
          [:td (loc (str "osapuoli.suunnittelija.approval." state))]
          [:td (str (-> henkilotiedot :etunimi :value)
                    " "
                    (-> henkilotiedot :sukunimi :value))]
          [:td (case (:value kuntaRoolikoodi)
                 "other" (str (loc "select-other") ": " (:value muuSuunnittelijaRooli))

                 (when-let [v (:i18nkey kuntaRoolikoodi)]
                   (loc v)))]])]]]))

(defn view* []
  [:f> view])

(defonce mounter (ui-dom/init :reagent (fn [] view*)))

(defn ^:export start [dom-id]
  (ui-dom/mount! mounter dom-id))
