(ns lupapalvelu.ui.docgen.field.foreman-history
  (:require ["react" :as react]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.pate.path :refer [loc]]
            [lupapalvelu.ui.docgen.component.toggle :as toggle]
            [lupapalvelu.ui.docgen.feature.foreman-history :as e]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [schema.core :as sc]))

(defn view [{:keys [app-id doc-id]}]
  (let [show-check (<sub [::e/show-check doc-id])
        show-all   (<sub [::e/show-all doc-id])
        title      (str "tyonjohtaja.historia.otsikko"
                        (when (or show-all (not show-check))
                          "-kaikki"))
        projects   (<sub [::e/projects doc-id])]
    (react/useEffect (fn []
                       (>evt [::e/query-foreman-history app-id doc-id show-all])
                       js/undefined)
                     #js [app-id doc-id show-all])
    [:div.form-table
     [:div.foreman-history-container
      [:label.form-label.form-label-table
       (loc title)]
      [:table.content
       [:thead
        [:tr
         [:th.first  (loc :tyonjohtaja.historia.kunta)]
         [:th.second (loc :tyonjohtaja.historia.vaativuus)]
         [:th.third  (loc :tyonjohtaja.historia.vainTamaHanke)]
         [:th.fourth (loc :tyonjohtaja.historia.vastuu)]
         [:th.fifth  (loc :tyonjohtaja.historia.hanke)]]]
       [:tbody
        (when (empty? projects)
          [:tr
           [:td {:col-span 5}
            (loc :tyonjohtaja.noProjects)]])

        (for [{:keys [foremanDocId foremanAppId linkedAppId
                      municipality difficulty limitedApproval jobDescription operation]}
              projects]
          [:tr
           {:key foremanDocId}
           [:td (loc :municipality municipality)]
           [:td (when difficulty
                  (loc :osapuoli.patevyysvaatimusluokka difficulty))]
           [:td (loc limitedApproval)]
           ;; NOTE: Depends on all :select option values being also i18n keys, currently OK
           [:td
            [:span (loc :osapuoli.tyonjohtaja.kuntaRoolikoodi jobDescription)]
            " (" [:span foremanAppId] ")"]
           [:td
            [:span (loc :operations operation)]
            " (" [:a {:href (str "#!/application/" linkedAppId)}
                  linkedAppId] ")"
            ]])]]
      (when show-check
        [toggle/toggle
         {:checked? show-all
          :on-change (fn [v] (>evt [::e/set-show-all doc-id v]))
          :prefix "blockbox"
          :text (loc :tyonjohtaja.history.showAllProjects)}])]]))

(sc/defn render
  "Native docgen CLJS component"
  [{:keys [app-id doc-id schema] :as args} :- schemas/DocgenFieldArguments]
  [:f> view args])
