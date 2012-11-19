(ns lupapalvelu.document.model
  (:require [clojure.string :as s]))

;;
;; Validation:
;;

(def default-max-len 64)

(defmulti validate (fn [elem v] (:type elem)))

(defmethod validate :group [elem v]
  (if (not (map? v)) [:err "illegal-value:not-a-map"]))

(defmethod validate :string [elem v]
  (cond
    (not= (type v) String) [:err "illegal-value:not-a-string"]
    (> (.length v) (or (:max-len elem) default-max-len)) [:err "illegal-value:too-long"]
    (< (.length v) (or (:min-len elem) 0)) [:warn "illegal-value:too-short"]))

(defmethod validate :text [elem v]
  (cond
    (not= (type v) String) [:err "illegal-value:not-a-string"]
    (> (.length v) (or (:max-len elem) default-max-len)) [:err "illegal-value:too-long"]
    (< (.length v) (or (:min-len elem) 0)) [:warn "illegal-value:too-short"]))

(defmethod validate :boolean [elem v]
  (if (not= (type v) Boolean) [:err "illegal-value:not-a-boolean"]))

(defmethod validate nil [elem v]
  [:err "illegal-key"])

(defmethod validate :default [elem v]
  [:err "unknown-type"])

;;
;; Neue api:
;;

(defn- find-by-name [body [k & ks]]
  (when-let [elem (some #(if (= (:name %) k) %) body)]
    (if (nil? ks)
      elem
      (find-by-name (:body elem) ks))))

(defn- validate-update [body results [k v]]
  (let [elem (find-by-name body (s/split k #"\."))
        result (validate elem v)]
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
