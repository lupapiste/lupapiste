(ns lupapalvelu.document.model
  (:use [clojure.tools.logging]
        [sade.strings]
        [lupapalvelu.document.schemas :only [schemas]]
        [lupapalvelu.clojure15]
        [clojure.walk :only [keywordize-keys]])
  (:require [clojure.string :as s]
            [clj-time.format :as timeformat]
            [sade.util :refer [safe-int]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.document.subtype :as subtype]))

;;
;; Validation:
;;

;; if you changes these values, change it in docgen.js, too
(def default-max-len 255)
(def dd-mm-yyyy (timeformat/formatter "dd.MM.YYYY"))

;;
;; Field validation
;;

(defmulti validate-field (fn [elem _] (keyword (:type elem))))

(defmethod validate-field :group [_ v]
  (if (not (map? v)) [:err "illegal-value:not-a-map"]))

(defmethod validate-field :string [{:keys [max-len min-len] :as elem} v]
  (cond
    (not= (type v) String) [:err "illegal-value:not-a-string"]
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
    (catch Exception e [:warn "invalid-date-format"])))

(defmethod validate-field :select [elem v] nil)
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

(defn validation-result [data path element result]
  {:data    data
   :path    (vec (map keyword path))
   :element element
   :result  result})

(defn- validate-fields [schema-body k data path]
  (let [current-path (if k (conj path (name k)) path)]
    (if (contains? data :value)
      (let [element (find-by-name schema-body current-path)
            result  (validate-field (keywordize-keys element) (:value data))]
        (and result (validation-result data current-path element result)))
      (filter
        (comp not nil?)
        (map (fn [[k2 v2]]
               (validate-fields schema-body k2 v2 current-path)) data)))))

;; TODO: separate namespace for these
(defn- validate-rules
  [{{{schema-name :name} :info} :schema data :data}]
  (when
    (and
      (= schema-name "uusiRakennus")
      (some-> data :rakenne :kantavaRakennusaine :value (= "puu"))
      (some-> data :mitat :kerrosluku :value safe-int (> 4)))
    [{:path    [:rakenne :kantavaRakennusaine]
      :result  [:warn "vrk:BR106"]}
     {:path    [:mitat :kerrosluku]
      :result  [:warn "vrk:BR106"]}]))

(defn validate
  "Validates document against it's local schema and document level rules
   retuning list of validation errors."
  [{{schema-body :body} :schema data :data :as document}]
  (and data
    (flatten
      (into
        (validate-fields schema-body nil data [])
        (validate-rules document)))))

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

(defn apply-update
  "Updates a document returning the modified document.
   Example: (apply-update document [:mitat :koko] 12)"
  [document path value]
  (assoc-in document (flatten [:data path :value]) value))

(defn apply-updates
  "Updates a document returning the modified document.
   Example: (apply-update document [:mitat :koko] 12)"
  [document updates]
  (reduce (fn [document [path value]] (apply-update document path value)) document updates))

(defn new-document
  "Creates an empty document out of schema"
  [schema created]
  {:id      (mongo/create-id)
   :created created
   :schema  schema
   :data    {}})
