(ns lupapalvelu.document.subtype
  (:use [clojure.string :only [blank?]] 
        [lupapalvelu.log]))

(defn- not-blank? [v]
  (not (blank? v)))

(defn- not-match? [re v]
  (nil? (re-matches #".+@.+\..+" v)))

(defmulti subtype-validation (fn [elem v] (keyword (:subtype elem))))

(defmethod subtype-validation :email [elem v]
  (cond
    (blank? v) nil
    (re-matches #".+@.+\..+" v) nil
    :else [:warn "illegal-email"]))

(defmethod subtype-validation :tel [elem v]
  (cond
    (blank? v) nil
    (re-matches #"^\+?[\d\s-]+" v) nil
    :else [:warn "illegal-tel"]))

(defmethod subtype-validation :number [elem v]
  (cond
    (blank? v) nil
    (re-matches #"\d+" v) nil
    :else [:warn "illegal-number"]))

(defmethod subtype-validation nil [elem v]
  nil)

(defmethod subtype-validation :default [elem v]
  (error "Unknown subtype: elem=[%s]" elem)
  [:err "illegal-subtype"])
