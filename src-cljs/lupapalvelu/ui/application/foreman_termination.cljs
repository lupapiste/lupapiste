(ns lupapalvelu.ui.application.foreman-termination
  (:require [clojure.string :as str]
            [lupapalvelu.application.foreman :as foreman]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.application.data :as data]
            [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.common :refer [loc]]
            [lupapalvelu.ui.components :as components]
            [lupapalvelu.ui.components.day-edit :as day-edit]
            [lupapalvelu.ui.date :as date]
            [lupapalvelu.ui.misc]
            [lupapalvelu.ui.user :as user]
            [sade.util :as util]))

(defn- inspection-summary-link []
  (let [app-id (<sub [::data/app-id])]
    [:a
     {:href     "#"
      :on-click #(do (>evt [:dialog/close])
                     (>evt [:application/open app-id :suffix :inspectionSummaries]))}
     (loc "foreman.terminate.reminder.inspection-document")]))

(defn- inspection-summary-reminder []
  (let [has-insp-summaries (auth/application-auth? :create-inspection-summary)]
    [:p
     [:b (loc "foreman.terminate.reminder.huom")]
     " "
     (loc "foreman.terminate.reminder.beginning")
     (if has-insp-summaries [inspection-summary-link] (loc "foreman.terminate.reminder.inspection-document"))
     (loc "foreman.terminate.reminder.end")]))

(defn termination-dialog []
  (let [tj-document            (<sub [::data/selected-foreman-document])
        tj-document-timestamps (<sub [::data/selected-foreman-document-timestamps])
        tj-app-id              (<sub [::data/app-id])
        responsibilities       (<sub [::data/selected-foreman-responsibilities])
        tj-v3?                 (-> tj-document :schema-info :name (util/=as-kw :tyonjohtaja-v3))
        tj-id                  (if tj-v3? (:id tj-document) tj-app-id)
        email                  (foreman/tj-document->email tj-document)]

    [:div#dialog-terminate-foreman
     [:div.dialog-content

      [:div.dialog-desc-wider
       [:p (loc :foreman.terminate.pending)]
       [inspection-summary-reminder]
       [:p (loc :foreman.terminate.tip)]]

      [:table
       [:tbody
        [:tr
         [:td.font-bold (str (loc :application.parties.foreman) ":")]
         [:td (or (foreman/tj-document->fullname tj-document) "-")]]
        [:tr
         [:td.font-bold (str (loc :osapuoli.tyonjohtaja.kuntaRoolikoodi._group_label) ":")]
         [:td (->> tj-document :data :kuntaRoolikoodi :value keyword (util/kw-path :osapuoli.tyonjohtaja.kuntaRoolikoodi) loc)]]
        [:tr
         [:td.font-bold (str (loc :osapuoli.patevyys-tyonjohtaja.patevyysvaatimusluokka._group_label) ":")]
         [:td (some->> tj-document :data :patevyysvaatimusluokka :value keyword (util/kw-path :osapuoli.patevyys-tyonjohtaja.patevyysvaatimusluokka) loc)]]
        [:tr
         [:td.font-bold (str (loc :foreman.tasks) ":")]
         [:td (str/join ", " responsibilities)]]
        [:tr
         [:td.font-bold (str (loc :foreman.termination.status.not-requested) ":")]
         [:td (or (some-> tj-document :started date/finnish-date)
                  (some-> tj-document-timestamps :timestamps :started date/finnish-date)
                  "-")]]
        (when (user/authority?)
          [:tr
           [:td.font-bold (str (loc :foreman.termination.date) ":")]
           [:td [day-edit/day-edit
                 (<sub [::data/termination-date tj-id])
                 {:id       "termination-date"
                  :callback #(>evt [::data/set-termination-date tj-id (some-> % (.valueOf))])}]]])]]

      [:span.mt-4 (loc :foreman.termination.reason)]
      [components/textarea-edit
       (<sub [::data/termination-reason tj-id])
       {:id       "termination-reason"
        :lines    3
        :callback #(>evt [::data/set-termination-reason tj-id %])}]
      [:button.primary.mr-2
       {:disabled (or (not (<sub [::data/termination-reason tj-id]))
                      (and (user/authority?)
                           (not (<sub [::data/termination-date tj-id]))))
        :on-click (fn []
                    (components/confirm-dialog
                      "areyousure"
                      (if (user/authority?)
                        "foreman.terminate.confirm"
                        "foreman.terminate.tip")
                      #(if tj-v3?
                         (if (user/authority?)
                           (>evt [::data/terminate-foreman-v3 tj-id])
                           (>evt [::data/request-foreman-termination-v3 email tj-id]))
                         (if (user/authority?)
                           (>evt [::data/terminate-foreman tj-id])
                           (>evt [::data/request-foreman-termination email tj-id])))))}
       (loc :foreman.terminate)]
      [:button.secondary.mr-2
       {:on-click #(>evt [:dialog/close])}
       (loc :cancel)]]]))


