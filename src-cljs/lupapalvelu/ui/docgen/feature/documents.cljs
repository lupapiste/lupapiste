(ns lupapalvelu.ui.docgen.feature.documents
  "Store received documents"
  (:require [lupapalvelu.next.event :refer [<sub]]
            [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.feature.application :as app]
            [lupapalvelu.ui.docgen.feature.auth :as auth]
            [lupapalvelu.ui.docgen.feature.edit :as edit]
            [lupapalvelu.ui.docgen.feature.field-schemas :as field-schemas]
            [lupapalvelu.ui.docgen.path :as path]
            [re-frame.core :as rf]
            [sade.property :as property]
            [sade.strings :as ss]
            [sade.util :as util]))

;;; Initialization

(defn with-commands
  [document]
  (let [commands (case (-> document :schema :info :type)
                   "task"
                   {:update :update-task
                    :remove :remove-document-data
                    :collection :tasks}
                   "building-product-catalog"
                   {:update :update-building-product-catalog
                    :remove :remove-document-data}
                   "climate-report"
                   {:update :update-climate-report
                    :remove :remove-document-data}
                   {:update :update-doc
                    :remove :remove-document-data
                    :collection :documents})]
    (assoc document ::commands commands)))

(defn enrich-docs [docs]
  (->> docs
       (map with-commands)
       ;; `lupapalvelu.ui.docgen.feature.auth` handles auth
       (map (fn [doc] (dissoc doc :allowedActions)))
       (map (juxt :id identity))
       (into {})))

(defn on-app-loaded
  [{:keys [db]} [_ {:keys [app]}]]
  ;; Note that we reinitialize the whole `::data` when a new app is loaded;
  ;; thereby discarding any transient state from any previous app.

  ;; TODO: LPK-7725 At the moment the JS code merges the document schemas with
  ;; the actual data of the documents (ie. what's written on the form).
  ;; However, we could just as well move the schemas to `.cljc` and read them
  ;; from there and not store them in the app-db.
  {:db (assoc db ::documents (->> app
                                  app/app->documents
                                  enrich-docs))})

(defn get-document-commands
  [db doc-id]
  (let [editing-doc? (edit/editing-doc? db doc-id)]
    (edit/with-post-verdict-update-cmd (get-in db [::documents doc-id ::commands]) editing-doc?)))

(defn on-unload
  [{:keys [db]}]
  {:db (dissoc db ::documents)})

;;; Subscriptions

(rf/reg-sub ::documents :-> ::documents)

(rf/reg-sub ::document-ids
  :<- [::documents]
  :-> (comp set keys))

(rf/reg-sub ::docs-by-op-id
  :<- [::documents]
  (fn [docs _]
    (->> docs
         (keep (fn [[_doc-id doc]]
                 (when-let [doc-op-id (-> doc
                                          (get-in [:schema-info :op :id])
                                          ss/blank-as-nil)]
                   [doc-op-id doc])))
         (into {}))))

(rf/reg-sub ::docs-by-nat-id
  :<- [::documents]
  (fn [docs _]
    (->> docs
         (keep
           (fn [[_doc-id doc]]
             (when-let [doc-nat-id (-> doc
                                       (get-in [:data :valtakunnallinenNumero :value])
                                       ss/blank-as-nil)]
               [doc-nat-id doc])))
         (into {}))))

(rf/reg-sub ::op-or-nat->related-document
  :<- [::docs-by-op-id]
  :<- [::docs-by-nat-id]
  (fn [[docs-by-op-id docs-by-nat-id] [_ op-id nat-id]]
    (or (and op-id (get docs-by-op-id op-id))
        (and nat-id (get docs-by-nat-id nat-id)))))

(rf/reg-sub ::op-documents
  :<- [::documents]
  (fn [docs]
    (->> docs
         (filter (comp #(get-in % [:schema-info :op]) second))
         (into {}))))

(rf/reg-sub ::op-name
  :<- [::documents]
  (fn [docs [_ doc-id]]
    (when-let [op-name (-> (get docs doc-id)
                           :schema-info :op :name)]
      (common/loc (util/kw-path op-name :_group_label)))))

(rf/reg-sub ::op-id->doc-id
  :<- [::op-documents]
  (fn [docs [_ op-id]]
    (some (fn [[id doc]]
            (when (= op-id (-> doc :schema-info :op :id))
              id))
          docs)))

(rf/reg-sub ::building-product-catalogs
  :<- [::documents]
  (fn [docs]
    (->> docs
         (filter (fn [[_k document]]
                   (= "building-product-catalog"
                      (-> document :schema :info :type))))
         (into {}))))

(rf/reg-sub ::climate-reports
  :<- [::documents]
  (fn [docs]
    (->> docs
         (filter (fn [[_k document]]
                   (= "climate-report"
                      (-> document :schema :info :type))))
         (into {}))))

(rf/reg-sub ::document
  :<- [::documents]
  (fn document-inputfn [docs [_ doc-id]]
    {:pre [doc-id]}
    (get docs doc-id)))

(rf/reg-sub ::building-document
  (fn building-document-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    (rf/subscribe [::document doc-id]))
  (fn [{:keys [building-ref]}]
    (when building-ref
      (<sub [::document building-ref]))))

;;  "Resolve the building document to use in climate calculations. When a climate-reports
;;  building-ref points to a location (rakennuspaikka) document, return the primary operation's building
;;  document instead, since additional data needed for climate calculations comes from the primary
;;  operations document data."
(rf/reg-sub ::resolve-building-document
  (fn resolve-building-document-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    [(rf/subscribe [::building-document doc-id])
     (rf/subscribe [::app/primary-operation-id])
     (rf/subscribe [::documents])])
  (fn [[building-doc primary-op-id docs]]
    (if (-> building-doc :schema-info :type (= "location"))
      (->> docs
           (util/find-first (fn [[_id v]] (= primary-op-id (-> v :schema-info :op :id))))
           second)
      building-doc)))

(rf/reg-sub ::doc-id-by-name
  :<- [::documents]
  (fn [docs [_ doc-name]]
    (:id (util/find-first (comp #{doc-name} :name :info :schema) (vals docs)))))

(defn db->document
  [db doc-id]
  (get-in db [::documents doc-id]))

(defn db->doc-schema-info
  [db doc-id]
  (-> (db->document db doc-id)
      :schema
      :info))

(defn db->document-referenced-by-low-carbon-assessment-document
  "Checks if some low-carbon assessment document has a :building-ref to given document-id"
  [db doc-id]
  (let [doc-references (->> (get db ::documents)
                            (filterv (fn [[_k {:keys [schema building-ref]}]]
                                       (when (some-> schema :info :type #{"climate-report" "building-product-catalog"})
                                         (= building-ref doc-id)))))]
    (boolean (seq doc-references))))

(rf/reg-sub ::disabled?
  (fn disabled-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    (rf/subscribe [::document doc-id]))
  :-> (comp boolean :disabled))

(rf/reg-sub ::enabled-doc-ids
  :<- [::documents]
  (fn [docs]
    (->> docs
         (remove (comp :disabled val))
         (map key)
         (into #{}))))

(rf/reg-sub ::document-data
  (fn document-data-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    (rf/subscribe [::document doc-id]))
  :-> :data)

(rf/reg-sub ::document-state
  (fn document-state-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    (rf/subscribe [::document doc-id]))
  :-> (comp keyword :state))

(rf/reg-sub ::schema
  (fn schema-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    (rf/subscribe [::document doc-id]))
  :-> :schema)

(rf/reg-sub ::schema-info
  (fn schema-info-inputfn [[_ doc-id]]
    {:pre [(string? doc-id)]}
    (rf/subscribe [::schema doc-id]))
  :-> :info)

(rf/reg-sub ::doc-name
  (fn doc-name-inputfn [[_ doc-id]]
    {:pre [(string? doc-id)]}
    (rf/subscribe [::schema-info doc-id]))
  :-> :name)

;; Same as `document.schemaI18name`
(rf/reg-sub ::i18n-name
  (fn i18n-name-inputfn [[_ doc-id]]
    {:pre [(string? doc-id)]}
    (rf/subscribe [::schema-info doc-id]))
  (fn [{:keys [i18name name]}]
    (some-> (or i18name name) keyword)))

(rf/reg-sub ::commands
  (fn commands-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    [(rf/subscribe [::document doc-id])
     (rf/subscribe [::edit/editing-doc? doc-id])])
  (fn [[document editing-doc?] _]
    (edit/with-post-verdict-update-cmd (::commands document) editing-doc?)))

(rf/reg-sub ::party-doc?
  (fn party-doc-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    (rf/subscribe [::schema-info doc-id]))
  (fn [info]
    (= "party" (:type info))))

(rf/reg-sub ::approvable?
  (fn approvable-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    (rf/subscribe [::schema-info doc-id]))
  :-> (comp boolean :approvable))

;;; Post-verdict editing and sending

(rf/reg-sub ::post-verdict-editable?
  (fn post-verdict-editable-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    (rf/subscribe [::schema-info doc-id]))
  :-> (comp boolean :post-verdict-editable))

(rf/reg-sub ::meta
  (fn meta-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    (rf/subscribe [::document doc-id]))
  :-> :meta)

(rf/reg-sub ::post-verdict-edit
  (fn post-verdict-edit-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    (rf/subscribe [::meta doc-id]))
  :-> :_post_verdict_edit)

(rf/reg-sub ::post-verdict-edited?
  (fn post-verdict-edited-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    (rf/subscribe [::post-verdict-edit doc-id]))
  (fn [edit]
    (-> edit
        :timestamp
        pos-int?)))

(defn- note-text
  [text-type]
  (fn [{:keys [user timestamp]}]
    (when (and user timestamp)
      (str (common/loc (util/kw-path :document text-type))
           " "
           (common/format-timestamp timestamp)
           ": "
           (:firstName user) " " (:lastName user)))))

(rf/reg-sub ::post-verdict-edited-note
  (fn post-verdict-edited-note-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    (rf/subscribe [::post-verdict-edit doc-id]))
  (note-text :edited))

(rf/reg-sub ::post-verdict-sent
  (fn post-verdict-sent-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    (rf/subscribe [::meta doc-id]))
  :-> :_post_verdict_sent)

(rf/reg-sub ::post-verdict-sent?
  (fn post-verdict-sent-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    (rf/subscribe [::post-verdict-sent doc-id]))
  (fn [sent]
    (-> sent
        :timestamp
        pos-int?)))

(rf/reg-sub ::post-verdict-sent-note
  (fn post-verdict-sent-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    (rf/subscribe [::post-verdict-sent doc-id]))
  (note-text :sent))

;;; Document operation

(rf/reg-sub ::operation
  (fn operation-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    (rf/subscribe [::schema-info doc-id]))
  :-> :op)

(rf/reg-sub ::schema-name
  (fn operation-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    (rf/subscribe [::schema-info doc-id]))
  :-> :name)

(rf/reg-sub ::primary-operation?
  (fn primary-operation-inputfn [[_ doc-id]]
    {:pre [doc-id]}
    [(rf/subscribe [::app/primary-operation-id])
     (rf/subscribe [::operation doc-id])])
  (fn [[primary-id {:keys [id]}]]
    (= primary-id id)))

;;; Document values

(rf/reg-sub ::field-data
  (fn field-data-inputfn [[_ doc-id field-path]]
    {:pre [doc-id (vector? field-path)]}
    (rf/subscribe [::document-data doc-id]))
  (fn field-data-fn [doc-data [_ _doc-id field-path]]
    ;; Hub uses `:keywordize-keys` to aggressively convert
    ;; all map keys to keywords. With repeating groups, the
    ;; keys should semantically be numbers.
    ;; NOTE: we only need to do this conversion here, the
    ;; other features store the paths as-is and thus can handle
    ;; the indexes.
    (get-in doc-data (path/keywordify-path field-path))))

(defn db->field-data
  "Like `::field-data` but gets value from app-db."
  [{::keys [documents]} doc-id field-path]
  (let [doc-data (:data (get documents doc-id))]
    (get-in doc-data (path/keywordify-path field-path))))

(defn post-process-value
  [value]
  (cond-> value
    (string? value) ss/blank-as-nil))

(defn first-value
  "Returns the first non-nil value. If the value is a blank string, nil is returned."
  [values]
  (-> (util/find-first some? values)
      post-process-value))

(defn get-transform-fn
  [transform]
  (case (keyword transform)
    :lower-case  ss/lower-case
    :property-id property/to-human-readable-property-id
    :upper-case  ss/upper-case
    :zero-pad-4  (fn [v] (.padStart (or v "") 4 "0"))
    identity))

;; Field value received when the app was originally loaded
;; Doesn't change even if the user modifies the field
(rf/reg-sub ::field-data-value
  (fn field-data-value-inputfn [[_ doc-id field-path]]
    {:pre [doc-id (vector? field-path)]}
    [(rf/subscribe [::field-data doc-id field-path])
     (rf/subscribe [::field-schemas/field-schema doc-id (path/path-without-indexes field-path)])])
  (fn [[data {:keys [transform default]}]]
    (let [transform-fn (get-transform-fn transform)]
      (some-> [(:value data)
               ;; Some schemas specify a `default` value
               default]
              first-value
              transform-fn))))

(defn db->field-data-value
  "Like `::field-data-value` but gets value from app-db."
  [db doc-id field-path]
  (first-value [(:value (db->field-data db doc-id field-path))
                (:default (field-schemas/db->field-schema db doc-id field-path))]))

(rf/reg-sub ::field-whitelist-action
  (fn field-whitelist-action-inputfn [[_ doc-id field-path]]
    {:pre [doc-id (vector? field-path)]}
    (rf/subscribe [::field-data doc-id field-path]))
  (comp keyword :whitelist-action))

(rf/reg-sub ::doc-disabled?
  (fn field-disabled-inputfn [[_ doc-id _override-auth-source]]
    {:pre [doc-id]}
    (rf/subscribe [::disabled? doc-id]))
  (fn [disabled? [_ doc-id override-auth-source]]
    (or disabled? ; whole document disabled
        (let [cmd (:update (<sub [::commands doc-id]))]
          (not (<sub [::auth/auth? doc-id cmd override-auth-source]))))))

;; For repeating groups (and tables?) retrieve the child
;; indexes.
(defn field-child-keys [field-data]
  ;; NOTE: Doesn't check schema for repeating-allow-empty
  (->> field-data keys path/integerize-coll sort))

(rf/reg-sub ::field-child-keys
  (fn field-child-keys-inputfn [[_ doc-id field-path]]
    {:pre [doc-id (vector? field-path)]}
    [(rf/subscribe [::field-data doc-id field-path])
     ;; field-path is likely keyword-path here,
     ;; for field-schema to work, it needs path-without-indexes,
     ;; and that util only works if indices are int in the path.
     (rf/subscribe [::field-schemas/field-schema doc-id (path/path-without-indexes (path/integerize-path field-path))])])
  (fn [[data {:keys [repeating-allow-empty]}] [_ _doc-id _field-path]]
    ;; The keys ought to be `:0`, `:1`, `:2`,...
    ;; NOTE: There are exceptions, sometimes (e.g. huoneistotaulu)
    ;;       there seems to also be a `:validationResults` key present.
    ;; NOTE: it's entirely possible that there are gaps
    (let [child-keys (field-child-keys data)]
      (cond
        (seq child-keys)            child-keys
        ;; Should we always show at least one child? By default yes,
        ;; but sometimes we allow having an empty group / table.
        ;; NOTE: we fake a child -> `::next-child-id` will return `1`.
        (not repeating-allow-empty) [0]))))

(defn next-child-id [child-indexes]
  (if (seq child-indexes)
    (inc (apply max child-indexes))
    0))

(rf/reg-sub ::next-child-id
  (fn [[_ doc-id field-path]]
    (rf/subscribe [::field-child-keys doc-id field-path]))
  (fn [child-indexes]
    (next-child-id child-indexes)))

(defn add-group-child
  "Add a new child element (e.g. a row) to a group or a table"
  [db {:keys [doc-id path next-idx ignore-missing-parent]}]
  {:pre [(string? doc-id) (vector? path) (int? next-idx)]}
  (let [parent-path    (into [::documents doc-id :data] path)
        get-child-path (fn [idx]
                         (-> (conj parent-path idx)
                             path/keywordify-path))
        child-path     (get-child-path next-idx)
        add-zeroth?    (and
                         (= 1 next-idx)
                         (let [parent-schema (field-schemas/db->field-schema db doc-id path)]
                           (not (:repeating-allow-empty parent-schema)))
                         (nil? (get-in db (get-child-path 0))))]
    (cond
      ;; Check we aren't overwriting anything
      (some? (get-in db child-path))
      (.warn js/console "Failed to add row, target exists" child-path)

      ;; Just in case verify that the parent table/group is found
      (or (map? (get-in db parent-path))
          ignore-missing-parent)
      ;; NOTE: this is one of the rare situations where we
      ;; modify the original application data. But we only add
      ;; an empty placeholder for the new child, the actual data goes
      ;; elsewhere.
      (cond-> (assoc-in db child-path {})
        ;; Did we previously fake a child and are now adding a new child?
        ;; => Add the previous child too. Otherwise the first "add child" effect
        ;; would do nothing to the UI as `[0]` would be replaced with `[1]`.
        ;; What we want is `[0 1]`.
        add-zeroth? (assoc-in (get-child-path 0) {}))

      :else
      (.warn js/console "Failed to add group child, no doc found" parent-path))))

(defn get-field-paths
  "Returns a map of paths to values"
  [db {:keys [doc-id path]}]
  (let [field-path (into [::documents doc-id :data] (path/keywordify-path path))]
    (when-let [field (get-in db field-path)]
      (->> (path/data->path-map field)
           ;; `field` is a subtree of the whole document data
           ;; and as such the paths we get are relative to it's root
           ;; and not the document root. Let's fix that by adding the root path back.
           (map (fn [[k v]]
                  ;; Also, return just the `:value` as value, not the whole value-map
                  [(into path k) (:value v)]))
           (into {})))))
