(ns lupapalvelu.document.model
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error fatal]]
            [sade.strings :refer :all]
            [clojure.walk :refer [keywordize-keys]]
            [clojure.set :refer [union difference]]
            [clojure.string :as s]
            [clj-time.format :as timeformat]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.document.vrk]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [sade.env :as env]
            [lupapalvelu.document.validator :as validator]
            [lupapalvelu.document.subtype :as subtype]))

;;
;; Validation:
;;

;; if you changes these values, change it in docgen.js, too
(def default-max-len 255)
(def dd-mm-yyyy (timeformat/formatter "dd.MM.YYYY"))

(def ^:private latin1 (java.nio.charset.Charset/forName "ISO-8859-1"))

(defn- latin1-encoder
  "Creates a new ISO-8859-1 CharsetEncoder instance, which is not thread safe."
  [] (.newEncoder latin1))

;;
;; Field validation
;;

(defmulti validate-field (fn [elem _] (keyword (:type elem))))

(defmethod validate-field :group [_ v]
  (if (not (map? v)) [:err "illegal-value:not-a-map"]))

(defmethod validate-field :string [{:keys [max-len min-len] :as elem} v]
  (cond
    (not= (type v) String) [:err "illegal-value:not-a-string"]
    (not (.canEncode (latin1-encoder) v)) [:warn "illegal-value:not-latin1-string"]
    (> (.length v) (or max-len default-max-len)) [:err "illegal-value:too-long"]
    (< (.length v) (or min-len 0)) [:warn "illegal-value:too-short"]
    :else (subtype/subtype-validation elem v)))

(defmethod validate-field :text [elem v]
  (cond
    (not= (type v) String) [:err "illegal-value:not-a-string"]
    (> (.length v) (or (:max-len elem) default-max-len)) [:err "illegal-value:too-long"]
    (< (.length v) (or (:min-len elem) 0)) [:warn "illegal-value:too-short"]))

(defmethod validate-field :checkbox [_ v]
  (if (not= (type v) Boolean) [:err "illegal-value:not-a-boolean"]))

(defmethod validate-field :date [elem v]
  (try
    (or (s/blank? v) (timeformat/parse dd-mm-yyyy v))
    nil
    (catch Exception e [:warn "illegal-value:date"])))

(defmethod validate-field :select [{:keys [body other-key]} v]
  (let [accepted-values (set (map :name body))
        accepted-values (if other-key (conj accepted-values "other") accepted-values)]
    (when-not (or (s/blank? v) (contains? accepted-values v))
      [:warn "illegal-value:select"])))

;; FIXME implement validator, the same as :select?
(defmethod validate-field :radioGroup [elem v] nil)

(defmethod validate-field :buildingSelector [elem v] (subtype/subtype-validation {:subtype :rakennusnumero} v))
(defmethod validate-field :newBuildingSelector [elem v] (subtype/subtype-validation {:subtype :number} v))

;; FIXME implement validator (mongo id, check that user exists)
(defmethod validate-field :personSelector [elem v] nil)

(defmethod validate-field nil [_ _]
  [:err "illegal-key"])

(defmethod validate-field :default [elem _]
  (warn "Unknown schema type: elem=[%s]" elem)
  [:err "unknown-type"])

;;
;; Neue api:
;;

(defn find-by-name [schema-body [k & ks]]
  (when-let [elem (some #(when (= (:name %) (name k)) %) schema-body)]
    (if (nil? ks)
      elem
      (if (:repeating elem)
        (when (numeric? (name (first ks)))
          (if (seq (rest ks))
            (find-by-name (:body elem) (rest ks))
            elem))
        (find-by-name (:body elem) ks)))))

(defn ->validation-result [data path element result]
  (when result
    {:data    data
     :path    (vec (map keyword path))
     :element element
     :result  result}))

(defn- validate-fields [schema-body k data path]
  (let [current-path (if k (conj path (name k)) path)]
    (if (contains? data :value)
      (let [element (keywordize-keys (find-by-name schema-body current-path))
            result  (validate-field element (:value data))]
        (->validation-result data current-path element result))
      (filter
        (comp not nil?)
        (map (fn [[k2 v2]]
               (validate-fields schema-body k2 v2 current-path)) data)))))

(defn- sub-schema-by-name [sub-schemas name]
  (some (fn [schema] (when (= (:name schema) name) schema)) sub-schemas))

(defn- one-of-many-options [sub-schemas]
  (map :name (:body (sub-schema-by-name sub-schemas schemas/select-one-of-key))))

(defn- one-of-many-selection [sub-schemas path data]
  (when-let [one-of (seq (one-of-many-options sub-schemas))]
    (or (get-in data (conj path :_selected :value)) (first one-of))))

(defn- validate-required-fields [schema-body path data validation-errors]
  (let [check
        (fn [{:keys [name required body repeating] :as element}]
          (let [kw (keyword name)
                current-path (conj path kw)
                validation-error (when (and required (s/blank? (get-in data (conj current-path :value))))
                                   (->validation-result nil current-path element [:tip "illegal-value:required"]))
                current-validation-errors (if validation-error (conj validation-errors validation-error) validation-errors)]
            (concat current-validation-errors
                    (if body
                      (if repeating
                        (map (fn [k] (validate-required-fields body (conj current-path k) data [])) (keys (get-in data current-path)))
                        (validate-required-fields body current-path data []))
                      []))))

        selected (one-of-many-selection schema-body path data)
        sub-schemas-to-validate (-> (set (map :name schema-body))
                                  (difference (set (one-of-many-options schema-body)) #{schemas/select-one-of-key})
                                  (union (when selected #{selected})))]

      (map #(check (sub-schema-by-name schema-body %)) sub-schemas-to-validate)))

(defn get-document-schema [{schema-info :schema-info}]
  (schemas/get-schema schema-info))

(defn validate
  "Validates document against schema and document level rules. Returns list of validation errors.
   If schema is not given, uses schema defined in document."
  ([document]
    (validate document nil))
  ([document schema]
    (let [data (:data document)
          schema (or schema (get-document-schema document))
          schema-body (:body schema)]
      (when data
        (flatten
          (concat
            (validate-fields schema-body nil data [])
            (validate-required-fields schema-body [] data [])
            (validator/validate document)))))))

(defn valid-document?
  "Checks weather document is valid."
  [document] (empty? (validate document)))

(defn has-errors?
  [results]
  (->>
    results
    (map :result)
    (map first)
    (some (partial = :err))
    true?))

;;
;; Updates
;;

(def ^:dynamic *timestamp* nil)
(defn current-timestamp
  "Returns the current timestamp to be used in document modifications."
  [] *timestamp*)

(defmacro with-timestamp [timestamp & body]
  `(binding [*timestamp* ~timestamp]
     ~@body))

(declare apply-updates)

(defn map2updates
  "Creates model-updates from map into path."
  [path m]
  (map (fn [[p v]] [(into path p) v]) (tools/path-vals m)))

(defn apply-update
  "Updates a document returning the modified document.
   Value defaults to \"\", e.g. unsetting the value.
   To be used within with-timestamp.
   Example: (apply-update document [:mitat :koko] 12)"
  ([document path]
    (apply-update document path ""))
  ([document path value]
    (if (map? value)
      (apply-updates document (map2updates path value))
      (let [data-path (vec (flatten [:data path]))]
        (-> document
          (assoc-in (conj data-path :value) value)
          (assoc-in (conj data-path :modified) (current-timestamp)))))))

(defn apply-updates
  "Updates a document returning the modified document.
   To be used within with-timestamp.
   Example: (apply-updates document [[:mitat :koko] 12])"
  [document updates]
  (reduce (fn [document [path value]] (apply-update document path value)) document updates))

;;
;; Approvals
;;

(defn ->approved [status user]
  "Approval meta data model. To be used within with-timestamp."
  {:value status
   :user (select-keys user [:id :firstName :lastName])
   :timestamp (current-timestamp)})


(defn apply-approval
  "Merges approval meta data into a map.
   To be used within with-timestamp or with a given timestamp."
  ([document path status user]
    (assoc-in document (filter (comp not nil?) (flatten [:meta path :_approved])) (->approved status user)))
  ([document path status user timestamp]
    (with-timestamp timestamp (apply-approval document path status user))))

(defn approvable?
  ([document] (approvable? document nil nil))
  ([document path] (approvable? document nil path))
  ([document schema path]
    (if (seq path)
      (let [schema      (or schema (get-document-schema document))
            schema-body (:body schema)
            str-path    (map #(if (keyword? %) (name %) %) path)
            element     (keywordize-keys (find-by-name schema-body str-path))]
        (true? (:approvable element)))
      (true? (get-in document [:schema-info :approvable])))))

(defn modifications-since-approvals
  ([{:keys [schema-info data meta]}]
    (let [schema (and schema-info (schemas/get-schema (:version schema-info) (:name schema-info)))]
      (modifications-since-approvals (:body schema) [] data meta (get-in schema [:info :approvable]) (get-in meta [:_approved :timestamp] 0))))
  ([schema-body path data meta approvable-parent timestamp]
    (letfn [(max-timestamp [p] (max timestamp (get-in meta (concat p [:_approved :timestamp]) 0)))
            (count-mods
              [{:keys [name approvable repeating body type] :as element}]
              (let [current-path (conj path (keyword name))
                    current-approvable (or approvable-parent approvable)]
                (if (= :group type)
                  (if repeating
                    (reduce + 0 (map (fn [k] (modifications-since-approvals body (conj current-path k) data meta current-approvable (max-timestamp (conj current-path k)))) (keys (get-in data current-path))))
                    (modifications-since-approvals body current-path data meta current-approvable (max-timestamp current-path)))
                  (if (and current-approvable (> (get-in data (conj current-path :modified) 0) (max-timestamp current-path))) 1 0))))]
      (reduce + 0 (map count-mods schema-body)))))

;;
;; Create
;;

(defn new-document
  "Creates an empty document out of schema"
  [schema created]
  {:id           (mongo/create-id)
   :created      created
   :schema-info  (:info schema)
   :data         {}})

;;
;; Blacklists
;;

(defn strip-blacklisted-data
  "Strips values from document data if blacklist in schema includes given blacklist-item."
  [{data :data :as document} blacklist-item & [initial-path]]
  (when data
    (letfn [(strip [schema-body path]
              (into {}
                (map
                  (fn [{:keys [name type body repeating blacklist] :as element}]
                    (let [k (keyword name)
                          current-path (conj path k)
                          v (get-in data current-path)]
                      (if ((set (map keyword blacklist)) (keyword blacklist-item))
                        [k nil]
                        (when v
                          (if (not= (keyword type) :group)
                            [k v]
                            [k (if repeating
                                 (into {} (map (fn [k2] [k2 (strip body (conj current-path k2))]) (keys v)))
                                 (strip body current-path))])))))
                  schema-body)))]
      (let [path (vec initial-path)
            schema (get-document-schema document)
            schema-body (:body (if (seq path) (find-by-name (:body schema) path) schema))]
        (assoc-in document (concat [:data] path)
          (strip schema-body path))))))


;;
;; Turvakielto
;;

(defn strip-turvakielto-data [{data :data :as document}]
  (reduce
    (fn [doc [path v]]
      (let [turvakielto-value (:value v)
            ; Strip data starting from one level up.
            ; Fragile, but currently schemas are modeled this way!
            strip-from (butlast path)]
        (if turvakielto-value
          (strip-blacklisted-data doc schemas/turvakielto strip-from)
          doc)))
    document
    (tools/deep-find data (keyword schemas/turvakielto))))

