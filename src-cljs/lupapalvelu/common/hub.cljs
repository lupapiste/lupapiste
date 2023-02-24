(ns lupapalvelu.common.hub
  (:require [re-frame.core :as rf]))

(defn send
  ([event]
   (send event nil))
  ([event data]
   (.send js/hub (clj->js event) (clj->js data))))

(defn subscribe
  ([event listener-fn]
   (subscribe event listener-fn false))
  ([event listener-fn one-shot]
   (.subscribe js/hub
               (clj->js event)
               (comp listener-fn #(js->clj % :keywordize-keys true))
               one-shot)))

(defn unsubscribe [hub-id]
  (.unsubscribe js/hub hub-id))


(rf/reg-fx :hub/send
  (fn [{:keys [event data]}]
    (send event data)))

(rf/reg-fx
  :hub/send-n
  ;; Each event is an [event data] vector
  (fn [{:keys [events]}]
    (doseq [[event data] events]
      (send event data))))

;; Hubscribes and stores the hub-id under given in db.
;; Unsubsubscribes old handler, if needed.
(rf/reg-event-fx
  ::subscribe
  (fn [{db :db} [_ event fun k]]
    {:db (update-in db [::hubscriptions k] (fn [hub-id]
                                             (when hub-id
                                               (unsubscribe hub-id))
                                             (subscribe event fun)))}))

;; Unsubscribes the subscription(s) stored under ks
(rf/reg-event-fx
  ::unsubscribe
  (fn [{db :db} [_ & ks]]
    {:db (update db ::hubscriptions
                 (fn [hubs]
                   (reduce (fn [m k]
                             (when-let [hub-id (get m k)]
                               (unsubscribe hub-id))
                             (dissoc m k))
                           hubs
                           (flatten ks))))}))
