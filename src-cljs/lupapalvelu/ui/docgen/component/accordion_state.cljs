(ns lupapalvelu.ui.docgen.component.accordion-state
  (:require [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.misc] ; :auth/*
            [re-frame.core :as rf]))

;;; Init

(defn on-app-loaded
  [{:keys [db]} [_ {:keys [app]}]]
  ;; Reset accordion open status only when the application changed
  ;; NOTE: Likely doesn't matter much on real use? But on development time
  ;; it is nice the accordions don't close after each application/load
  ;; with a authority user.
  (if (not= (:app-id (::data db)) (:id app))
    {:db (assoc db ::data {:app-id (:id app)})}
    {:db db}))

(defn on-unload
  [{:keys [db]}]
  {:db (dissoc db ::data)})

(rf/reg-sub ::data :-> ::data)

(rf/reg-sub ::accordions
  :<- [::data]
  :-> :accordions)

(rf/reg-sub ::accordion-state
  :<- [::accordions]
  (fn accordion-state-inputfn [accordions [_ doc-id]]
    {:pre [doc-id]}
    (get accordions doc-id)))

(rf/reg-sub ::open-all-accordions?
  :<- [:auth/application? :enable-accordions]
  :-> identity)

(rf/reg-sub ::accordion-open?
  (fn accordion-open-inputfn [[_ doc-id _]]
    {:pre [doc-id]}
    [(rf/subscribe [::accordion-state doc-id])
     (rf/subscribe [::open-all-accordions?])])
  (fn [[state open-all?] [_ _doc-id expand-accordions?]]
    (if-some [open? (:open? state)]
      open?
      (or open-all?
          expand-accordions?))))

(rf/reg-sub ::section-help
  (fn section-help-inputfn [[_ doc-id]]
    {:pre [(string? doc-id)]}
    (rf/subscribe [::docs/schema-info doc-id]))
  :-> :section-help)

;;; Events

(defn toggle-accordion
  [db doc-id open?]
  (assoc-in db [::data :accordions doc-id :open?] open?))

(rf/reg-event-fx ::toggle-accordion
  (fn [{:keys [db]} [_ {:keys [doc-id open?]}]]
    {:db (toggle-accordion db doc-id open?)}))
