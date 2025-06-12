(ns lupapalvelu.ui.docgen.feature.maaraala
  (:require [re-frame.core :as rf]))

(defn on-app-loaded
  [{:keys [db]} _]
  {:db (assoc db ::maaraala-toggles {})})

(defn on-unload
  [{:keys [db]}]
  {:db (dissoc db ::maaraala-toggles)})

(rf/reg-sub ::maaraala-toggles :-> ::maaraala-toggles)

(rf/reg-sub ::enabled?
  :<- [::maaraala-toggles]
  (fn [toggles [_ doc-id field-path]]
    (get-in toggles [doc-id field-path])))

(defn toggle-fx
  [{:keys [db]} [_ {:keys [doc-id path enabled?]}]]
  {:pre [doc-id (vector? path) (boolean? enabled?)]}
  {:db (assoc-in db [::maaraala-toggles doc-id path] enabled?)})

(rf/reg-event-fx ::toggle
  toggle-fx)
