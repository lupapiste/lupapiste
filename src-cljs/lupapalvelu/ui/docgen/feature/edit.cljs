(ns lupapalvelu.ui.docgen.feature.edit
  "Handles document edit mode for documents that are marked with
  :post-verdict-editable flag.

  Accordion-toolbar has the controls to enable the edit state,
  the state will affect which command is used to send doc updates
  to the backend."
  (:require [re-frame.core :as rf]))

(defn on-app-loaded
  [{:keys [db]} [_ {:keys [_app]}]]
  {:db (update db ::data select-keys [:editing])})

(defn on-unload
  [{:keys [db]}]
  {:db (dissoc db ::data)})

;;; Events

(rf/reg-event-fx ::toggle-edit-mode
  (fn [{:keys [db]} [_ {:keys [doc-id enabled?]}]]
    {:pre [doc-id (boolean? enabled?)]}
    {:db (assoc-in db [::data :editing doc-id] enabled?)}))

;;; Subs

(rf/reg-sub ::data
  :-> ::data)

(rf/reg-sub ::editing
  :<- [::data]
  :-> :editing)

(rf/reg-sub ::editing-doc?
  (fn editing-doc-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    (rf/subscribe [::editing]))
  (fn [docs [_ doc-id]]
    (boolean (get docs doc-id))))

(defn editing-doc? [db doc-id]
  (boolean (get-in db [::data :editing doc-id])))

;; This is used in feature.documents in a subscription, and
;; a function that is used to retrieve doc commands from app-db,
;; and all other code, like feature.actions, should
;; use commands through those paths -> they will correctly
;; use this command when editing state is active.
(defn with-post-verdict-update-cmd [commands editing-doc?]
  (cond-> commands
    editing-doc? (assoc :update :update-post-verdict-doc)))
