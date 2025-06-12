(ns lupapalvelu.ui.docgen.feature.value
  "Handle latest values of the form fields, updated from the UI
  as the user modifies the fields"
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.docgen.feature.committed :as committed]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [re-frame.core :as rf]))

(defn on-app-loaded
  [{:keys [db]} _]
  {:db (assoc db ::changes {})})

(defn on-unload
  [{:keys [db]}]
  {:db (dissoc db ::changes)})

(rf/reg-sub ::changes :-> ::changes)

(rf/reg-sub ::document-changes
  :<- [::changes]
  (fn [docs [_ doc-id]]
    (get docs doc-id)))

(rf/reg-sub ::field-changes
  (fn field-changes-inputfn [[_ doc-id field-path]]
    {:pre [doc-id (vector? field-path)]}
    (rf/subscribe [::document-changes doc-id]))
  (fn [doc-data [_ _doc-id field-path]]
    (get doc-data field-path)))

(defn db->field-changes
  "Like `::field-changes` but gets value from app-db."
  [{::keys [changes]} doc-id field-path]
  (get-in changes [doc-id field-path]))

(defn fields-edited-db
  [db {:keys [doc-id field-paths values]}]
  {:pre [doc-id (vector? field-paths) (vector? values) (= (count field-paths) (count values))]}
  (reduce (fn [db idx]
            (let [field-path (nth field-paths idx)
                  value      (nth values idx)]
              (assoc-in db [::changes doc-id field-path] value)))
          db
          (range (count field-paths))))

(defn fields-edited-fx
  [{:keys [db]} [_ {:keys [doc-id field-paths values] :as args}]]
  {:pre [doc-id (vector? field-paths) (vector? values) (= (count field-paths) (count values))]}
  {:db (fields-edited-db db args)})

(rf/reg-event-fx ::fields-edited
  fields-edited-fx)

;; UI values

(rf/reg-sub ::current-field-value
  (fn current-field-value-inputfn [[_ doc-id field-path]]
    {:pre [doc-id (vector? field-path)]}
    [(rf/subscribe [::field-changes doc-id field-path])
     (rf/subscribe [::committed/backend-submitted-value doc-id field-path])
     (rf/subscribe [::docs/field-data-value doc-id field-path])])
  (fn [[changed-value submitted-value original-value]]
    (docs/first-value [changed-value
                       ;; Some fields (e.g. checkboxes) don't invoke `::field-edited`
                       ;; and instead just save/commit their values.
                       submitted-value
                       original-value])))

(defn db->current-field-value
  "Like `::current-field-value` but gets value from app-db."
  [db doc-id field-path]
  (docs/first-value [(db->field-changes db doc-id field-path)
                     (committed/db->backend-submitted-value db doc-id field-path)
                     (docs/db->field-data-value db doc-id field-path)]))

(rf/reg-sub ::current-repeating-value
  (fn current-repeating-value-inputfn [[_ doc-id field-path column]]
    {:pre [(string? doc-id) (vector? field-path) column]}
    (rf/subscribe [::docs/field-child-keys doc-id field-path]))
  (fn [child-keys [_ doc-id path column]]
    (->> child-keys
         (map (fn [child-id]
                (<sub [::committed/backend-submitted-value doc-id (into path [child-id (keyword column)])])))
         (into []))))
