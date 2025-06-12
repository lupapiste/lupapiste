(ns lupapalvelu.ui.docgen.feature.committed
  "Once the changes are sent to the server and accepted/rejected, the changes
  are promoted to 'committed' state. Note that, similar to the original Docgen
  implementation, we do NOT read the whole (or parts of) document back when we
  update a single field *. Instead, we locally keep track of the changes and hope
  things are in sync.

  *) There are a few exceptions where we run the `:application/load` effect to
     reload the application state. E.g. removing a table row.

  Note that 'committed' doesn't necessarily mean that the changes were actually
  persisted. In case of invalid data, the backend might reject the changes and
  return an error but we still consider the value to be 'committed'."
  (:require [lupapalvelu.ui.docgen.feature.documents :as docs]
            [re-frame.core :as rf]
            [sade.util :as util]))

(defn on-app-loaded
  [{:keys [db]} _]
  {:db (assoc db ::committed {})})

(defn on-unload
  [{:keys [db]}]
  {:db (dissoc db ::committed)})

(defn commit-changed-fields
  "Promote the field(s) & value(s) we persisted as 'committed'."
  [{:keys [db]} [_ doc-id field-paths values]]
  {:pre [(string? doc-id) (vector? field-paths) (vector? values) (= (count field-paths) (count values))]}
  {:db (reduce (fn [db idx]
                 (let [field-path (nth field-paths idx)
                       value      (nth values idx)]
                   (assoc-in db [::committed doc-id field-path] value)))
               db
               (range (count field-paths)))})

(rf/reg-sub ::committed :-> ::committed)

(rf/reg-sub ::committed-changes
  :<- [::committed]
  (fn [docs [_ doc-id]]
    (get docs doc-id)))

(rf/reg-sub ::field-committed
  (fn field-committed-inputfn [[_ doc-id field-path]]
    {:pre [doc-id (vector? field-path)]}
    (rf/subscribe [::committed-changes doc-id]))
  (fn [doc-data [_ _doc-id field-path]]
    (get doc-data field-path)))

(defn db->field-committed
  "Like `::field-committed` but gets value from app-db."
  [{::keys [committed]} doc-id field-path]
  (get-in committed [doc-id field-path]))

;; Our best guess of the last field value submitted the backend:
;; - either we've made no changes and the value is what we read from the app, or
;; - we've tried to update the document with `:update-doc`, which may or may not have succeeded
(rf/reg-sub ::backend-submitted-value
  (fn backend-reported-value-inputfn [[_ doc-id field-path]]
    {:pre [doc-id (vector? field-path)]}
    [(rf/subscribe [::field-committed doc-id field-path])
     (rf/subscribe [::docs/field-data-value doc-id field-path])])
  (fn [[committed-value original-value]]
    (docs/first-value [committed-value
                       original-value])))

(defn db->backend-submitted-value
  "Like `::backend-submitted-value` but gets value from app-db."
  [db doc-id field-path]
  (docs/first-value [(db->field-committed db doc-id field-path)
                     (docs/db->field-data-value db doc-id field-path)]))

(defn get-committed
  "Return a map of committed values within a specific path."
  [{::keys [committed] :as _db} {:keys [doc-id path]}]
  (->> (get committed doc-id)
       (filter (fn [[k _v]]
                 (util/prefix-matches? k path)))
       (into {})))

(defn clean-committed-fields
  "Clean committed fields for document that match the given path prefix"
  [cofx doc-id prefix]
  (update-in cofx [:db ::committed doc-id]
             (fn [xs]
               (reduce-kv (fn [acc k v]
                            (if (util/prefix-matches? k prefix)
                              acc
                              (assoc acc k v)))
                          {}
                          xs))))
