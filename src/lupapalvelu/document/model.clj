(ns lupapalvelu.document.model
  (:require [clojure.string :as s]))

;;
;; Document Model DSL
;;

(defn make-model   [name version & body] {:info {:name name :version version} :body body})
(defn make-elem    [name type & args]    (apply hash-map :name name :type type args))
(defn make-string  [name & args]         (merge (make-elem name :string :min-len 0 :max-len 32) (apply hash-map args)))
(defn make-text    [name & args]         (merge (make-elem name :text :min-len 0 :max-len 256) (apply hash-map args)))
(defn make-boolean [name & args]         (merge (make-elem name :boolean) (apply hash-map args)))
(defn make-group   [name & body]         {:type :group :name name :body (or body [])})

;;
;; Validation:
;;

(defmulti validate (fn [elem v] (:type elem)))

(defmethod validate :group [elem v]
  (if (not (map? v)) "illegal-value:not-a-map"))

(defmethod validate :string [elem v]
  (cond
    (not= (type v) String) "illegal-value:not-a-string"
    (and (:max-len elem) (> (.length v) (:max-len elem))) "illegal-value:too-long"
    (and (:min-len elem) (< (.length v) (:min-len elem))) "illegal-value:too-short"))

(defmethod validate :boolean [elem v]
  (if (not= (type v) Boolean) "illegal-value:not-a-boolean"))

;;
;; Processing
;;

(defn get-elem [schema n]
  (some #(if (= (:name %) n) %) schema))

(defn group? [elem]
  (= (:type elem) :group))

(defn xor [a b]
  (or (and a (not b)) (and (not a) b)))

(declare apply-update)

(defn apply-single-update [doc schema path changes k v elem]
  (let [name (s/join \. (reverse (cons k path)))
        error (if elem (validate elem v) "illegal-key")]
    (if error
      [doc (cons [name v false error] changes)]
      (if (group? elem)
        (let [[d c] (apply-update (get doc k {}) (:body elem) (cons k path) [] (seq v))]
          [(assoc doc k d) (concat changes c)])
        [(assoc doc k v) (cons [name v true] changes)]))))

(defn apply-update [doc schema path changes [[k v] & r]]
  (let [elem (get-elem schema k)
        result (apply-single-update doc schema path changes k v elem)]
    (if (nil? r)
      result
      (apply-update (first result) schema path (second result) r))))

(defn apply-updates
  "da public api."
  ([{:keys [body schema]} updates] (apply-updates body schema updates))
  ([doc schema updates]
  (apply-update
    doc              ; document to update
    (:body schema)    ; schema to confirm against
    []               ; path, for error reporting
    []               ; list of changes performed
    (seq updates))))  ; updates as a seq of key/value pairs
