(ns lupapalvelu.document.subtype
  (:use [clojure.string :only [blank?]]
        [clojure.tools.logging]))

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

(defn- parse-number
  "Reads a number from input. Returns nil if not a number."
  ([x] (parse-number x nil))
  ([x default]
    (let [s (.replaceAll (str x) "0*(\\d+)" "$1")]
      (if (re-find #"^-?\d+\.?\d*([Ee]\+\d+|[Ee]-\d+|[Ee]\d+)?$" (.trim s))
        (read-string s)
        default))))

(defmethod subtype-validation :number [{:keys [min max]} v]
  (when-not (blank? v)
    (let [min-int  (parse-number min (java.lang.Integer/MIN_VALUE))
          max-int  (parse-number max (java.lang.Integer/MAX_VALUE))
          number   (parse-number v)]
      (when-not (and number (<= min-int number max-int))
        [:warn "illegal-number"]))))

(defmethod subtype-validation :digit [_ v]
  (cond
    (blank? v) nil
    (re-matches #"^\d$" v) nil
    :else [:warn "illegal-number"]))

(defmethod subtype-validation :letter [{:keys [case]} v]
  (let [regexp (condp = case
                 :lower #"^\p{Ll}$"
                 :upper #"^\p{Lu}$"
                 #"^\p{L}$")]
  (cond
    (blank? v) nil
    (re-matches regexp v) nil
    :else [:warn "illegal-letter"])))

(defmethod subtype-validation :kiinteistotunnus [_ v]
  (cond
    (blank? v) nil
    (re-matches #"^\d{14}$" v) nil
    :else [:warn "illegal-kiinteistotunnus"]))

(defmethod subtype-validation nil [_ _]
  nil)

(defmethod subtype-validation :default [elem _]
  (error "Unknown subtype:" elem)
  [:err "illegal-subtype"])
