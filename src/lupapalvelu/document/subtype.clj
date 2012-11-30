(ns lupapalvelu.document.subtype
  (:use [clojure.string :only [blank?]] 
        [lupapalvelu.log]))

(defmulti subtype-validation (fn [elem _] (keyword (:subtype elem))))

(defmethod subtype-validation :email [_ v]
  (cond
    (blank? v) nil
    (re-matches #".+@.+\..+" v) nil
    :else [:warn "illegal-email"]))

(defmethod subtype-validation :tel [_ v]
  (cond
    (blank? v) nil
    (re-matches #"^\+?[\d\s-]+" v) nil
    :else [:warn "illegal-tel"]))

(defmethod subtype-validation :number [_ v]
  (cond
    (blank? v) nil
    (re-matches #"\d+" v) nil
    :else [:warn "illegal-number"]))

(defmethod subtype-validation nil [_ _]
  nil)

(defmethod subtype-validation :default [elem _]
  (error "Unknown subtype: elem=[%s]" elem)
  [:err "illegal-subtype"])
