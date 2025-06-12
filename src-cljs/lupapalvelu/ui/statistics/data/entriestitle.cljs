(ns lupapalvelu.ui.statistics.data.entriestitle
  "Sort of proxy for updating entries-title-service."
  (:require [lupapalvelu.ui.common :as common]
            [re-frame.core :as rf]))

;; -------------------------
;; Events
;; -------------------------

(rf/reg-event-fx ::update-service
  (fn [_ [_ {:keys [requests unseen?]}]]
    (js/lupapisteApp.services.entriesTitleService.setInfo requests unseen?)
    nil))

(rf/reg-event-fx ::fetch-requests-status
  (fn []
    {:action/query {:name    :processing-requests-status
                    :params  {:id (common/application-id)}
                    :success ::update-service
                    :error   ::update-service}}))

(rf/reg-event-fx ::toggle-visible
  (fn [_ [_ visible?]]
    (cond-> {:fx [[:dispatch [::update-service]]]}
      visible? (update :fx conj [:dispatch [::fetch-requests-status]]))))
