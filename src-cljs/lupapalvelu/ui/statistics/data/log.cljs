(ns lupapalvelu.ui.statistics.data.log
  "Processing logdata is stored under datapath key `:logdata`."
  (:require [lupapalvelu.data :as data]
            [lupapalvelu.statistics.schemas :as stats-sc]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.statistics.data.summary :as data-summary]
            [re-frame.core :as rf]
            [sade.util :as util]))

(def set-d    (partial data/set-d ::data))
(def get-d    (partial data/get-d ::data))
(def unset-d  (partial data/unset-d ::data))

;; -------------------------
;; Events
;; -------------------------

(rf/reg-event-db ::clear
  (fn [db]
    (unset-d db :logdata)))

(rf/reg-event-fx ::fetch.complete
  (fn [{db :db} [_ {:keys [processing-logdata unseen?]}]]
    (if-let [{:keys [processing verdict-ts]} processing-logdata]
      {:db (set-d db :logdata (stats-sc/->logdata processing-logdata))
       :fx [[:dispatch [::data-summary/set-processing-days
                        (util/assoc-when processing
                                         :unseen? unseen?
                                         :verdict-ts verdict-ts)]]]}
      {:fx [[:dispatch [::clear]]]})))

(rf/reg-event-fx ::fetch-logdata
  (fn []
    {:action/query {:name    :processing-logdata
                    :params  {:id (common/application-id)}
                    :success ::fetch.complete
                    :error   ::fetch.complete}}))

;; -------------------------
;; Subs
;; -------------------------

(rf/reg-sub ::data :-> get-d)

(rf/reg-sub ::logdata
  :<- [::data]
  :-> :logdata)
