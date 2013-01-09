(ns lupapalvelu.document.model
  (:use [lupapalvelu.log]
        [lupapalvelu.strings]
        [clojure.walk :only [keywordize-keys]])
  (:require [clojure.string :as s]
            [lupapalvelu.document.subtype :as subtype]))

;;
;; Validation:
;;

(def default-max-len 64)

(defmulti validate (fn [elem _] (keyword (:type elem))))

(defmethod validate :group [_ v]
  (if (not (map? v)) [:err "illegal-value:not-a-map"]))

(defmethod validate :string [elem v]
  (cond
    (not= (type v) String) [:err "illegal-value:not-a-string"]
    (> (.length v) (or (:max-len elem) default-max-len)) [:err "illegal-value:too-long"]
    (< (.length v) (or (:min-len elem) 0)) [:warn "illegal-value:too-short"]
    :else (subtype/subtype-validation elem v)))

(defmethod validate :text [elem v]
  (cond
    (not= (type v) String) [:err "illegal-value:not-a-string"]
    (> (.length v) (or (:max-len elem) default-max-len)) [:err "illegal-value:too-long"]
    (< (.length v) (or (:min-len elem) 0)) [:warn "illegal-value:too-short"]))

(defmethod validate :boolean [_ v]
  (if (not= (type v) Boolean) [:err "illegal-value:not-a-boolean"]))

;; FIXME
(defmethod validate :checkbox [elem v]
  nil)

;; FIXME
(defmethod validate :select [elem v]
  nil)

(defmethod validate nil [_ _]
  [:err "illegal-key"])

(defmethod validate :default [elem _]
  (warn "Unknown schema type: elem=[%s]" elem)
  [:err "unknown-type"])

;;
;; Neue api:
;;
(defn- find-by-name [schema-body [k & ks]]
  (when-let [elem (some #(if (= (:name %) k) %) schema-body)]
    (if (nil? ks)
      elem
      (if (:repeating elem)
        (when (numeric? (first ks))
          (if (seq (rest ks))
            (find-by-name (:body elem) (rest ks))
            elem))
        (find-by-name (:body elem) ks)))))

(defn- validate-update [schema-body results [k v]]
  (let [elem (find-by-name schema-body (s/split k #"\."))
        result (validate (keywordize-keys elem) v)]
    (if (nil? result)
      results
      (conj results (cons k result)))))

(defn validate-updates
  "Validate updates against schema.

  Updates is expected to be a seq of updates, where each update is a key/value seq. Key is name of
  the element to update, and the value is a new value for element. Key should be dot separated path.

  Returns a seq of validation failures. Each failure is a seq of three elements. First element is the
  name of the element. Second element is either :warn or :err and finally, the last element is the
  warning or error message."
  [schema updates]
  (reduce (partial validate-update (:body schema)) [] updates))

(defn validation-status
  "Accepts validation results (as defined in 'validate-updates' function) and returns either :ok
  (when results is empty), :warn (when results contains only warnings) or :err (when results
  contains one or more errors)."
  [results]
  (cond
    (empty? results) :ok
    (some #(= (second %) :err) results) :err
    :else :warn))
