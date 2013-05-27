(ns lupapalvelu.document.model
  (:use [clojure.tools.logging]
        [sade.strings]
        [lupapalvelu.document.schemas :only [schemas]]
        [clojure.walk :only [keywordize-keys]])
  (:require [clojure.string :as s]
            [clj-time.format :as timeformat]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.document.vrk]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.validator :as validator]
            [lupapalvelu.document.subtype :as subtype]))

;;
;; Validation:
;;

;; if you changes these values, change it in docgen.js, too
(def default-max-len 255)
(def dd-mm-yyyy (timeformat/formatter "dd.MM.YYYY"))

(def latin1-encoder (.newEncoder (java.nio.charset.Charset/forName "ISO-8859-1")))

;;
;; Field validation
;;

(defmulti validate-field (fn [elem _] (keyword (:type elem))))

(defmethod validate-field :group [_ v]
  (if (not (map? v)) [:err "illegal-value:not-a-map"]))

(defmethod validate-field :string [{:keys [max-len min-len] :as elem} v]
  (cond
    (not= (type v) String) [:err "illegal-value:not-a-string"]
    (not (.canEncode latin1-encoder v)) [:warn "illegal-value:not-latin1-string"]
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

(defmethod validate-field :select [{:keys [body]} v]
  (when-not (or (s/blank? v) (some #{v} (map :name body)))
    [:warn "illegal-value:select"]))

(defmethod validate-field :radioGroup [elem v] nil)
(defmethod validate-field :buildingSelector [elem v] nil)
(defmethod validate-field :personSelector [elem v] nil)

(defmethod validate-field nil [_ _]
  [:err "illegal-key"])

(defmethod validate-field :default [elem _]
  (warn "Unknown schema type: elem=[%s]" elem)
  [:err "unknown-type"])

;;
;; Neue api:
;;

(defn- find-by-name [schema-body [k & ks]]
  (when-let [elem (some #(when (= (:name %) k) %) schema-body)]
    (if (nil? ks)
      elem
      (if (:repeating elem)
        (when (numeric? (first ks))
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

(defn- validate-required-fields [schema-body path data validation-errors]
  (map
    (fn [{:keys [name required body repeating] :as element}]
      (let [kw (keyword name)
            current-path (if (empty? path) [kw] (conj path kw))
            validation-error (when
                               (and required
                                    (s/blank? (get-in data (conj current-path :value))))
                               (->validation-result nil current-path element [:warn "illegal-value:required"]))
            current-validation-errors (if validation-error (conj validation-errors validation-error) validation-errors)]
        (concat current-validation-errors
          (if body
            (if repeating
              (map (fn [k] (validate-required-fields body (conj current-path k) data [])) (keys (get-in data current-path)))
              (validate-required-fields body current-path data []))
            []))))
    schema-body))

(defn validate
  "Validates document against it's local schema and document level rules
   retuning list of validation errors."
  [{{schema-body :body} :schema data :data :as document}]
  (and data
    (flatten
      (concat
        (validate-fields schema-body nil data [])
        (validate-required-fields schema-body nil data [])
        (validator/validate document)))))

(defn valid-document?
  "Checks weather document is valid."
  [document] (empty? (validate document)))

(defn validate-against-current-schema
  "Validates document against the latest schema and returns list of errors."
  [{{{schema-name :name} :info} :schema document-data :data :as document}]
  (let [latest-schema (get schemas schema-name)
        pimped-doc    (assoc document :schema latest-schema)]
    (validate pimped-doc)))

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

(declare apply-updates)

(defn map2updates
  "Creates model-updates from map into path."
  [path m]
  (map (fn [[p v]] [(into path p) v]) (tools/path-vals m)))

(defn apply-update
  "Updates a document returning the modified document.
   Value defaults to \"\", e.g. unsetting the value.
   Example: (apply-update document [:mitat :koko] 12)"
  ([document path]
    (apply-update document path ""))
  ([document path value]
    (if (map? value)
      (apply-updates document (map2updates path value))
      (assoc-in document (flatten [:data path :value]) value))))

(defn apply-updates
  "Updates a document returning the modified document.
   Example: (apply-updates document [[:mitat :koko] 12])"
  [document updates]
  (reduce (fn [document [path value]] (apply-update document path value)) document updates))

(defn new-document
  "Creates an empty document out of schema"
  [schema created]
  {:id      (mongo/create-id)
   :created created
   :schema  schema
   :data    {}})
