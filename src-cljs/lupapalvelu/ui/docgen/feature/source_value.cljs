(ns lupapalvelu.ui.docgen.feature.source-value
  "Subscriptions for accessing the `source*` data.

  The `source*` data represent data which Lupapiste received
  from elsewhere (e.g. data about buildings). Not all fields
  have the `source` fields.

  NOTE: The received `sourceValue` value may be an empty string or `nil`.
  You should use the other subscriptions to first check if the `source` data
  is present."
  (:require [lupapalvelu.ui.common :as common]
            [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.edit :as edit]
            [lupapalvelu.ui.docgen.feature.field-schemas :as field-schemas]
            [lupapalvelu.ui.docgen.feature.value :as value]
            [lupapalvelu.ui.docgen.path :as path]
            [re-frame.core :as rf]
            [sade.strings :as ss]))

(defn- source-value-state
  [source-value has-source? current-value]
  (cond
    (not has-source?)                   ::no-source-value
    (= (ss/blank-as-nil source-value)
       (ss/blank-as-nil current-value)) ::not-modified
    :else                               ::modified))

(rf/reg-sub ::has-source?
  (fn field-data-has-source-inputfn [[_ doc-id field-path]]
    {:pre [doc-id (vector? field-path)]}
    (rf/subscribe [::docs/field-data doc-id field-path]))
  (fn [{:keys [source] :as data}]
    (and (some? (ss/blank-as-nil source))
         ;; `sourceValue` might be "" or `nil`
         (contains? data :sourceValue))))

;; The raw `sourceValue` value. A `nil` result might indicate
;; that the value really is `nil` or the value does not exist.
(rf/reg-sub ::source-value
  (fn source-value-inputfn [[_ doc-id field-path]]
    {:pre [doc-id (vector? field-path)]}
    [(rf/subscribe [::docs/field-data doc-id field-path])
     (rf/subscribe [::field-schemas/field-schema doc-id (path/path-without-indexes field-path)])])
  (fn [[data {:keys [transform]}]]
    ;; Perform the same transformation as the actual value;
    ;; We don't want to highlight differences if they are caused
    ;; by the transformation itself.
    (let [transform-fn (docs/get-transform-fn transform)]
      (some-> (:sourceValue data)
              transform-fn))))

(defn db->source-value
  "Like `::source-value` but gets value from app-db."
  [db doc-id field-path]
  ;; This is only used in the huoneistot table and those fields
  ;; don't use `transform`. But at least in principle they might so
  ;; let's make sure we also support `transform` here.
  (let [{:keys [transform]} (field-schemas/db->field-schema db doc-id field-path)
        transform-fn        (docs/get-transform-fn transform)
        data                (docs/db->field-data db doc-id field-path)]
    (some-> (:sourceValue data)
            transform-fn)))

(rf/reg-sub ::source-value-modified?
  (fn source-value-modified-inputfn [[_ doc-id field-path]]
    {:pre [doc-id (vector? field-path)]}
    [(rf/subscribe [::docs/field-data doc-id field-path])
     (rf/subscribe [::source-value doc-id field-path])
     (rf/subscribe [::has-source? doc-id field-path])
     (rf/subscribe [::value/current-field-value doc-id field-path])
     (rf/subscribe [::edit/editing-doc? doc-id])])
  (fn [[data source-value has-source? current-value editing?]]
    (or (= ::modified (source-value-state source-value has-source? current-value))
        ;; Special case: also highlight field if we are
        ;; editing a form post-verdict
        (and editing?
             (not= (ss/blank-as-nil (:value data))
                   current-value)))))

;; Return the source value if it a) exists and b) does not match the current value
;; or the state keyword.
(rf/reg-sub ::source-value-or-state
  (fn field-data-source-value-inputfn [[_ doc-id field-path]]
    {:pre [doc-id (vector? field-path)]}
    [(rf/subscribe [::source-value doc-id field-path])
     (rf/subscribe [::has-source? doc-id field-path])
     (rf/subscribe [::value/current-field-value doc-id field-path])])
  (fn [[source-value has-source? current-value]]
    (let [state (source-value-state source-value has-source? current-value)]
      (if (= ::modified state)
        source-value
        ;; Use a special token to distinguish between the actual value
        state))))

(defn- title
  ([source-value-modified? source-value schema]
   (title source-value-modified? source-value schema nil))
  ([source-value-modified? source-value schema override-value-text]
   (when (and source-value-modified?
              (not (ss/ends-with-i? (:name schema) "hetu")))
     (str (common/loc :sourceValue)
          ": " (or override-value-text
                   source-value)))))

(rf/reg-sub ::title
  (fn source-value-title-inputfn [[_ doc-id path]]
    {:pre [doc-id (vector? path)]}
    [(rf/subscribe [::source-value-modified? doc-id path])
     (rf/subscribe [::source-value doc-id path])
     (rf/subscribe [::field-schemas/field-schema doc-id (path/path-without-indexes path)])])
  (fn [[modified? source-value schema] [_ _doc-id _path override-value]]
    (title modified? source-value schema override-value)))

(defn with-classes
  "Add class(es) indicating that the current value does not match the source value"
  [classes source-value-changed?]
  (cond-> (or classes [])
    source-value-changed? (conj "source-value-changed")))
