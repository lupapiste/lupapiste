(ns lupapalvelu.ui.docgen.field.link-permit-selector
  "Implements the link permit selector component.

  The link permit selector is a text field, that refers to a linked
  permit application. If there are no suitable link permits, the user
  may fill in the field manually by typing in e.g. an application id.

  However, if there _are_ suitable link permits, we will fill the field
  automatically and make it read only. The backend also duplicates this
  logic, see `lupapalvelu.document.canonical-common/link-permit-selector-value`,
  as otherwise the frontend and backend might not agree upon the value."
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.authorization :as auth]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.components.input :as ui-input]
            [lupapalvelu.ui.docgen.component.ping :as ping]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.feature.committed :as committed]
            [lupapalvelu.ui.docgen.feature.emit :as emit]
            [lupapalvelu.ui.docgen.feature.field-schemas :as field-schemas]
            [lupapalvelu.ui.docgen.feature.focus :as focus]
            [lupapalvelu.ui.docgen.feature.validation :as validation]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.field.docgen-input-common :as di]
            [lupapalvelu.ui.docgen.field.common :as docgen-common]
            [lupapalvelu.ui.docgen.path :as path]
            [lupapalvelu.ui.docgen.schemas :as schemas]
            [lupapalvelu.ui.misc]
            [sade.util :as util]
            [schema.core :as sc]
            [re-frame.core :as rf]))

(defn- link-permit-selector?
  [schema]
  (= :linkPermitSelector (keyword (:type schema))))

(defn on-app-loaded
  [{:keys [db]} _]
  ;; Check if there are any link-permit-selectors on any of the documents
  (let [schemas (field-schemas/db->field-schemas-by-pred db link-permit-selector?)]
    (when (and (seq schemas)
               ;; This mechanism seems broken;
               ;; if we don't have the proper permissions
               ;; -> we won't do the query
               ;; -> we can't classify the existing link permits
               ;; -> we can't choose & display the correct application id
               ;; -> we end up showing the raw field value, which might be whatever.
               ;; TODO: => should `:all-operations-in` be permitted for all users?
               (auth/application-auth? :all-operations-in))
      ;; In theory there might be more than one selector
      (let [paths-to-query (->> schemas
                                (mapcat (fn [[_doc-id by-path]]
                                          (->> by-path
                                               (map (fn [[_field-path {:keys [operationsPath]}]]
                                                      operationsPath)))))
                                (into #{}))
            app-id         (app/db->application-id db)]
        {:fx (->> paths-to-query
                  (mapv (fn [operations-path]
                          [:action/query {:name    :all-operations-in
                                          :params  {:id   app-id
                                                    :path (path/path->str operations-path)}
                                          :success [::all-operations-in.success app-id operations-path]}])))}))))

(defn on-unload
  [{:keys [db]}]
  {:db (dissoc db ::data)})

(rf/reg-event-fx ::all-operations-in.success
  (fn [{:keys [db]} [_ {:keys [operations]} app-id operations-path]]
    {:db (assoc-in db [::data app-id operations-path] (set operations))}))

(rf/reg-sub ::data :-> ::data)

(rf/reg-sub ::operations-for
  :<- [::data]
  (fn [data [_ app-id operations-path]]
    (get-in data [app-id operations-path] #{})))

(rf/reg-sub ::valid-link-permits
  (fn [[_ app-id operations-path]]
    [(rf/subscribe [::operations-for app-id operations-path])
     (rf/subscribe [::app/link-permit-data])])
  (fn [[operations link-permits]]
    (->> link-permits
         (filter (fn [{:keys [operation]}]
                   (contains? operations operation)))
         (mapv :id))))

(rf/reg-sub ::status
  (fn [[_ app-id operations-path]]
    (rf/subscribe [::valid-link-permits app-id operations-path]))
  (fn [link-permits]
    ;; If there's just a single choice => pick that one and make the field read-only
    (cond-> {}
      (seq link-permits)         (assoc :value (first link-permits) :read-only? true)
      (< 1 (count link-permits)) (assoc :validation-result-level :warn))))

(sc/defn render
  "See `link-permit-selector-[template.html|model.js]`."
  [{:keys [app-id doc-id schema path disabled?] :as args} :- schemas/DocgenFieldArguments]
  (let [
        {:keys [label aria-label]} (di/args->label args)
        status                     (<sub [::status app-id (:operationsPath schema)])
        read-only?                 (or (:read-only? status)
                                       (docgen-common/is-read-only? (util/mmap doc-id path schema)))
        value                      (or (:value status)
                                       (<sub [::value/current-field-value doc-id path]))
        validation-result-level    (or (:validation-result-level status)
                                       (<sub [::validation/field-data-validation-result-level doc-id path]))
        input-id                   (path/path->id path doc-id)
        flags                      (docgen-common/aria-flags validation-result-level)
        can-modify?                (and (not disabled?)
                                        (not read-only?))
        focus-ref                  (focus/use-focus doc-id path)]
    [di/container
     [:span.form-entry.link-permit-selector
      [di/docgen-label {:id                      input-id
                        :label                   label
                        :size                    (:size schema)
                        :validation-result-level validation-result-level}]
      [ui-input/text-input
       (cond-> (merge {:id          input-id
                       :class       (di/input-classes schema :link-permit-selector validation-result-level)
                       :debounce-ms docgen-common/default-input-debounce-ms
                       :value       value}
                      (-> (di/schema->input-options schema)
                          (select-keys [:max-length :placeholder])))
         (seq flags) (merge flags)
         focus-ref   (assoc :ref focus-ref)
         aria-label  (assoc :aria-label (common/loc aria-label))
         read-only?  (assoc :read-only? true)
         disabled?   (assoc :disabled? true)
         can-modify? (assoc :on-change (docgen-common/make-on-change (util/mmap doc-id path))
                            :on-blur (docgen-common/make-on-blur
                                       (util/mmap {:backend-value (<sub [::committed/backend-submitted-value doc-id path])
                                                   :emit-data     (emit/schema->emit-data (util/mmap app-id doc-id schema))}
                                                  app-id doc-id path))))]
      [ping/ping {:doc-id doc-id :field-path path :mode :icon}]]]))
