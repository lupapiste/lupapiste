(ns lupapalvelu.document.subtype
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error fatal]]
            [sade.util :refer [->int ->double] :as util]
            [sade.validators :refer [valid-email?] :as v]
            [clojure.string :refer [blank?]]))

(defmulti subtype-validation (fn [elem _] (keyword (:subtype elem))))

(defmethod subtype-validation :email [_ v]
  (cond
    (blank? v) nil
    (valid-email? v) nil
    :else [:warn "illegal-email"]))

(defmethod subtype-validation :tel [_ v]
  (cond
    (blank? v) nil
    (re-matches #"^\+?[\d\s-]+" v) nil
    :else [:warn "illegal-tel"]))

(defmethod subtype-validation :number [{:keys [min max]} v]
  (when-not (blank? v)
    (let [min-int  (->int min (java.lang.Integer/MIN_VALUE))
          max-int  (->int max (java.lang.Integer/MAX_VALUE))
          number   (->int v nil)]
      (cond
        (not (integer? number)) [:warn "illegal-number"]
        (< number min-int) [:warn "illegal-number:too-small"]
        (> number max-int) [:warn "illegal-number:too-big"]))))

(defmethod subtype-validation :decimal [{:keys [min max]} v]
  (when-not (blank? v)
    (let [min-double (->double min (java.lang.Integer/MIN_VALUE))
          max-double (->double max (java.lang.Integer/MAX_VALUE))
          number (->double (clojure.string/replace v "," ".") nil)]
      (cond
        (not (float? number)) [:warn "illegal-decimal"]
        (< number min-double) [:warn "illegal-decimal:too-small"]
        (> number max-double) [:warn "illegal-decimal:too-big"]))))

(defmethod subtype-validation :recent-year [{:keys [range]} v]
  (if (re-matches #"^[1-9][0-9]*$" v) ;; > 0
    (let [given (Integer/parseInt v)
          current (+ 1900 (.getYear (java.util.Date.)))]
      (cond
        ((> given current)
          [:warn "illegal-recent-year:future"])
        ((< given (- current range))
          [:warn "illegal-recent-year:too-past"])
        :else
          nil))
     [:warn "illegal-recent-year:not-integer"]))

(defmethod subtype-validation :digit [_ v]
  (cond
    (blank? v) nil
    (re-matches #"^\d$" v) nil
    :else [:warn "illegal-number"]))

(defmethod subtype-validation :letter [{:keys [case]} v]
  (let [regexp (condp = (keyword case)
                 :lower #"^\p{Ll}$"
                 :upper #"^\p{Lu}$"
                 #"^\p{L}$")]
    (cond
      (blank? v) nil
      (re-matches regexp v) nil
      :else [:warn (str "illegal-letter:" (if case (name case) "any"))])))

(defmethod subtype-validation :kiinteistotunnus [_ v]
  (cond
    (blank? v) nil
    (re-matches #"^\d{14}$" v) nil
    :else [:warn "illegal-kiinteistotunnus"]))

(defmethod subtype-validation :y-tunnus [_ v]
  (cond
    (blank? v) nil
    (v/finnish-y? v) nil
    :else [:warn "illegal-y-tunnus"]))

;; Finnish zips are currently validated with defvalidators.
;; See lupapalvelu.document.document-field-validators for details.
;; This validator is only applicable to addresses without country
;; information.
(defmethod subtype-validation :zip [_ v]
  (cond
    (blank? v) nil
    (v/finnish-zip? v) nil
    :else [:warn "illegal-zip"]))

(defmethod subtype-validation :rakennusnumero [_ v]
  (cond
    (blank? v) nil
    (v/rakennusnumero? v) nil
    :else [:warn "illegal-rakennusnumero"]))

(defmethod subtype-validation :rakennustunnus [_ v]
  (cond
   (blank? v) nil
   (v/rakennustunnus? v) nil
   :else [:warn "illegal-rakennustunnus"])
 )

(defmethod subtype-validation :vrk-name [_ v]
  (cond
    (blank? v) nil
    (re-matches #"^([\p{L}\-/ \.\*]+)$" v) nil
    :else [:warn "illegal-name"]))

(defmethod subtype-validation :vrk-address [_ v]
  (cond
    (blank? v) nil
    (re-matches #"^([\p{L}\(\)\-/ &\.,:\*\d]+)$" v) nil
    :else [:warn "illegal-address"]))

(defmethod subtype-validation :maaraala-tunnus [_ v]
  (cond
    (blank? v) nil
    (re-matches #"^[0-9]{4}$" v) nil
    :else [:warn "illegal-maaraala-tunnus"]))

(defmethod subtype-validation :ovt [_ v]
  (cond
    (blank? v) nil
    v/finnish-ovt? nil
    :else [:warn "illegal-ovt-tunnus"]))

(defmethod subtype-validation nil [_ _]
  nil)

(defmethod subtype-validation :default [elem _]
  (error "Unknown subtype:" elem)
  [:err "illegal-subtype"])
