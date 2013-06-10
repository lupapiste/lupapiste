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
            [sade.env :as env]
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

(defn- sub-schema-by-name [sub-schemas name]
  (some (fn [schema] (when (= (:name schema) name) schema)) sub-schemas))

(defn- one-of-many-selection [sub-schemas path data]
  (when-let [one-of (seq (map :name (:body (sub-schema-by-name sub-schemas "_selected"))))]
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
                      []))))]
    (if-let [selected (one-of-many-selection schema-body path data)]
      [(check (sub-schema-by-name schema-body selected))]
      (map check schema-body))))

(defn validate
  "Validates document against it's local schema and document level rules
   retuning list of validation errors."
  [{{schema-body :body} :schema data :data :as document}]
  (and data
    (flatten
      (concat
        (validate-fields schema-body nil data [])
        (validate-required-fields schema-body [] data [])
        (when (env/feature? :vrk) (validator/validate document))))))

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

;;
;; Approvals
;;

(defn approvable?
  ([document] (approvable? document nil))
  ([document path]
  (if (seq path)
    (let [schema-body (get-in document [:schema :body])
          str-path    (map #(if (keyword? %) (name %) %) path)
          element     (keywordize-keys (find-by-name schema-body str-path))]
      (true? (:approvable element)))
    (true? (get-in document [:schema :info :approvable])))))

(defn modifications-since-approvals
  ([{:keys [schema data meta]}]
    (modifications-since-approvals (:body schema) [] data meta (get-in schema [:info :approvable]) (get-in meta [:_approved :timestamp] 0)))
  ([schema-body path data meta approvable-parent timestamp]
    (letfn [(max-timestamp [p] (max timestamp (get-in meta (concat p [:_approved :timestamp]) 0)))
            (count-mods
              [{:keys [name approvable repeating body] :as element}]
              (let [current-path (conj path (keyword name))
                    current-approvable (or approvable-parent approvable)]

;(println current-path current-approvable timestamp)
                (if body
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
  {:id      (mongo/create-id)
   :created created
   :schema  schema
   :data    {}})
