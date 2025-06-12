(ns lupapalvelu.ui.docgen.feature.identifiers
  "Utilities regarding document identifiers, which are just
   top-level schema fields with the special `identifier` tag."
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [re-frame.core :as rf]))

(defn on-app-loaded
  [{:keys [db]} _]
  {:db (assoc db ::identifier-toggles {})})

(defn on-unload
  [{:keys [db]}]
  {:db (dissoc db ::identifier-toggles)})

(rf/reg-sub ::identifier-toggles :-> ::identifier-toggles)

(rf/reg-sub ::enabled?
  :<- [::identifier-toggles]
  (fn [toggles [_ doc-id]]
    (get toggles doc-id)))

(defn toggle-fx
  "Hide or show the editor toolbar."
  [{:keys [db]} [_ {:keys [doc-id enabled?]}]]
  {:pre [doc-id (boolean? enabled?)]}
  {:db (assoc-in db [::identifier-toggles doc-id] enabled?)})

(rf/reg-event-fx ::toggle
  toggle-fx)

(rf/reg-sub ::identifiers
  :<- [::docs/documents]
  (fn [docs]
    (->> docs
         vals
         (keep (fn [doc]
                 (when-let [subschema (->> doc
                                           :schema
                                           :body
                                           (some (fn [{:keys [identifier] :as subschema}]
                                                   (when identifier
                                                     subschema))))]
                   [(:id doc) {:path       [(keyword (:name subschema))]
                               :required?  (boolean (:required subschema))
                               :max-length (or (:max-len subschema) 255)}])))
         (into {}))))

(rf/reg-sub ::identifier
  :<- [::identifiers]
  (fn [identifiers [_ doc-id]]
    (get identifiers doc-id)))

(rf/reg-sub ::identifier?
  (fn identifierp-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    (rf/subscribe [::identifier doc-id]))
  :-> some?)

(rf/reg-sub ::identifier-path
  (fn identifier-path-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    (rf/subscribe [::identifier doc-id]))
  :-> :path)

(rf/reg-sub ::identifier-value
  (fn identifier-value-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    (rf/subscribe [::identifier-path doc-id]))
  (fn [path [_ doc-id]]
    (when path
      (<sub [::value/current-field-value doc-id path]))))

(rf/reg-sub ::show-identifier-editor?
  (fn show-identifier-editor-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    [(rf/subscribe [::identifier? doc-id])
     (rf/subscribe [::identifier-value doc-id])
     (rf/subscribe [::enabled? doc-id])])
  (fn [[identifier? value toggle-enabled?]]
    (if (some? toggle-enabled?)
      toggle-enabled?
      (and identifier? (nil? value)))))
