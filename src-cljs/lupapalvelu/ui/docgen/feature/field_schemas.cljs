(ns lupapalvelu.ui.docgen.feature.field-schemas
  "Utilities for accessing a field schema based on path."
  (:require [lupapalvelu.document.shared-tools :as shared-tools]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.path :as path]
            [re-frame.core :as rf]
            [sade.util :as util]))

;; NOTE: If LPK-7725 is implemented, we can change this calculation
;; to work on the static schemas in the code (perhaps wrapped in a delay) instead
;; of parsing the schemas from the response

(defn- post-process-element
  [{:keys [type inputType default] :as element}]
  (cond-> element
    ;; Checkboxes act as if their default value is `false`
    (and (or (= "checkbox" type)
             (= "checkbox" inputType))
         (nil? default))
    (assoc :default false)))

(defn- resolve-i18nkey
  [context element]
  ;; Similar to `lupapalvelu.ui.docgen.loc/apply-parent-i18nkey`, inherit
  ;; `i18nkey` from parent if available and the field itself doesn't overwrite it
  (if (and (:i18nkey context)
           (not (:i18nkey element)))
    (str (:i18nkey context) "." (:name element))
    (:i18nkey element)))

(defn- collect-schemas
  [schema]
  (->> (shared-tools/walk-schema
         schema
         (fn [{:keys [path element context]}]
           (let [{:keys [body type] :as element} (post-process-element element)
                 i18nkey                         (resolve-i18nkey context element)
                 ;; Assign `i18nkey` to both this element and any children
                 context                         (util/assoc-when context :i18nkey i18nkey)
                 element                         (util/assoc-when element :i18nkey i18nkey)]
             (if (or (= "select" type)
                     (empty? body))
               {:result  [path element]
                :context context}
               {:result  [path (dissoc element :body)]
                :context context}))))
       (into {})))

(defn- doc->schemas
  [document]
  (collect-schemas (:schema document)))

(defn on-app-loaded
  [{:keys [db]} [_ {:keys [app]}]]
  {:db (assoc db ::schemas (->> (app/app->documents app)
                                (map (fn [{:keys [id]
                                           :as   doc}]
                                       ;; Produce a map of "doc-id" => "field path" => "field schema" for all fields
                                       [id (doc->schemas doc)]))
                                (into {})))})

(defn on-unload
  [{:keys [db]}]
  {:db (dissoc db ::schemas)})

(rf/reg-sub ::schemas :-> ::schemas)

(rf/reg-sub ::doc-field-schemas
  :<- [::schemas]
  (fn doc-field-schemas-inputfn [schemas [_ doc-id]]
    {:pre [doc-id]}
    (get schemas doc-id)))

(rf/reg-sub ::field-schema
  (fn field-schema-inputfn [[_ doc-id _path]]
    {:pre [doc-id]}
    (rf/subscribe [::doc-field-schemas doc-id]))
  (fn [schemas [_ _doc-id path]]
    ;; Ideally the caller has already applied `path/path-without-indexes`
    ;; to avoid duplicate subscriptions: schema for [foo 0 bar] is the same as [foo 1 bar].
    (get schemas (path/path-without-indexes path))))

(defn db->field-schema
  "Like `::field-schema` but gets value from app-db."
  [{::keys [schemas]} doc-id path]
  {:pre [(map? schemas) (string? doc-id) (vector? path)]}
  (let [doc-schemas (get schemas doc-id)]
    (get doc-schemas (path/path-without-indexes path))))

(defn db->field-schemas-by-pred
  "Like `db->field-schema` but returns a map (doc-id -> path -> schemas) of all schemas matching `pred`."
  [{::keys [schemas]} pred]
  {:pre [(map? schemas) (ifn? pred)]}
  (->> schemas
       (keep (fn [[doc-id by-path]]
               (when-let [schemas (->> by-path
                                       (filter (comp pred val))
                                       (into {})
                                       not-empty)]
                 [doc-id schemas])))
       (into {})))

(rf/reg-sub ::readonly-paths
  (fn [[_ doc-id path-prefix]]
    {:pre [(string? doc-id) (vector? path-prefix)]}
    (rf/subscribe [::doc-field-schemas doc-id]))
  (fn [schemas [_ _doc-id path-prefix]]
    ;; Find all readonly fields, which are under `path-prefix`, regardless of the depth
    (let [prefix (path/path-without-indexes path-prefix)]
      (->> schemas
           (filter (fn [[path schema]]
                     (and (:readonly schema)
                          (util/prefix-matches? path prefix))))
           (map key)
           (into #{})))))
