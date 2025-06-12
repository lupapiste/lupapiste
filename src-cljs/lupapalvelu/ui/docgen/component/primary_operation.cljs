(ns lupapalvelu.ui.docgen.component.primary-operation
  (:require [lupapalvelu.next.event :refer [<sub >evt]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.feature.auth :as auth]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [re-frame.core :as rf]
            [sade.util :as util]))

(rf/reg-sub ::may-change-primary-operation?
  (fn may-change-primary-operation-inputfn [[_ doc-id override-auth-source]]
    {:pre [doc-id]}
    (rf/subscribe [::auth/auth? doc-id :change-primary-operation override-auth-source]))
  :-> identity)

(rf/reg-event-fx ::change-primary-operation.success
  (fn [_ [_ _resp app-id]]
    {:fx [[:application/load app-id]]}))

(rf/reg-event-fx ::primary-button-clicked
  (fn [_ [_ {:keys [app-id op-id]}]]
    {:pre [(string? app-id) (string? op-id)]}
    {:fx [[:action/command {:name    :change-primary-operation
                            :params  {:id                   app-id
                                      :secondaryOperationId op-id}
                            :success [::change-primary-operation.success app-id]}]]}))

(defn set-as-primary-button
  "Mark this operation as the primary operation."
  [{:keys [doc-id override-auth-source]}]
  (when (<sub [::may-change-primary-operation? doc-id override-auth-source])
    (let [primary? (<sub [::docs/primary-operation? doc-id])
          app-id   (<sub [::app/application-id])
          op-id    (:id (<sub [::docs/operation doc-id]))]
      [:button.secondary.gap--l1
       {:disabled primary?
        :on-click (fn [] (>evt [::primary-button-clicked (util/mmap app-id op-id)]))}
       [:i.lupicon-star]
       [:span
        (common/loc (if primary?
                      :operations.primary
                      :operations.primary.select))]])))
