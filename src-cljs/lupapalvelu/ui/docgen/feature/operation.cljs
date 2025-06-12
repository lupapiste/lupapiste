(ns lupapalvelu.ui.docgen.feature.operation
  "State handling for the operation editor"
  (:require [lupapalvelu.ui.docgen.feature.documents :as docs]
            [re-frame.core :as rf]))

(defn on-app-loaded
  [{:keys [db]} _]
  {:db (assoc db ::descriptions {})})

(defn on-unload
  [{:keys [db]}]
  {:db (dissoc db ::descriptions)})

(rf/reg-sub ::descriptions :-> ::descriptions)

(rf/reg-sub ::description
  :<- [::descriptions]
  (fn [descriptions [_ doc-id]]
    (get descriptions doc-id)))

;; Assuming here that each operation is a separate document
(rf/reg-sub ::current-description
  (fn description-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    [(rf/subscribe [::docs/operation doc-id])
     (rf/subscribe [::description doc-id])])
  (fn [[op modified]]
    (or modified (:description op) "")))

(defn change-description-fx
  [{:keys [db]} [_ {:keys [doc-id value]}]]
  {:pre [doc-id]}
  {:db (assoc-in db [::descriptions doc-id] value)})
