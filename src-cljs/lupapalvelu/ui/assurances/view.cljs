(ns lupapalvelu.ui.assurances.view
  "A view for displaying assurances that the applicant has accepted when submitting the application"
  (:require [lupapalvelu.common.hub :as hub]
            [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.assurances.data :as data]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.input :as components]
            [lupapalvelu.ui.dom :as ui-dom]))

(defn view []
  (when (<sub [::data/visible])
    (let [accordion-open? (<sub [::data/assurances-accordion])
          assurances-data (<sub [::data/assurances-data])
          assurances      (:assurances assurances-data)
          lang            (keyword (:language assurances-data))]
      (when (seq assurances)
        [:section.accordion {:id "accordion-application-summary-assurances"}
         [:button.secondary.accordion-toggle {:on-click #(>evt [::data/toggle-accordion])}
          (if accordion-open?
            [:i.lupicon-chevron-up]
            [:i.lupicon-chevron-down])
          [:span.bar-text {:style {:margin-right 0}}
           (common/loc :application.assurances.title)]]
         (when accordion-open?
           [:div.accordion_content.expanded
            [:div.accordion-fields.pt-4
             (for [assurance assurances
                   :let [text (-> assurance :text lang)
                         id   (:id assurance)]]
               [:div {:key text}
                [:p.mb-4
                 (components/checkbox {:id        id
                                       :checked?  true
                                       :disabled? true})
                 [:label {:for id} text]]])]])]))))

(defonce mounter (ui-dom/init :reagent (fn [] view)))

(defn ^:export start [dom-id]
  (hub/subscribe :reload-assurances #(>evt [::data/get-assurances]))
  (ui-dom/mount! mounter dom-id))
