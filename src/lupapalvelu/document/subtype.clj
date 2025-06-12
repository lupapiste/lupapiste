(ns lupapalvelu.document.subtype
  (:require [lupapalvelu.logging :refer [error]]
            [sade.strings :as ss :refer [blank?]]
            [sade.util :refer [->double ->int] :as util]
            [sade.validators :refer [valid-email?] :as va]))

(set! *warn-on-reflection* true)

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
    (let [min-int  (->int min Integer/MIN_VALUE)
          max-int  (->int max Integer/MAX_VALUE)
          number   (->int v nil)]
      (cond
        (not (integer? number)) [:warn "illegal-number"]
        (< number min-int) [:warn "illegal-number:too-small"]
        (> number max-int) [:warn "illegal-number:too-big"]))))

(defmethod subtype-validation :decimal [{:keys [min max]} v]
  (when-not (blank? v)
    (let [min-double (->double min Integer/MIN_VALUE)
          max-double (->double max Integer/MAX_VALUE)
          number (->double (ss/replace v "," ".") nil)]
      (cond
        (not (float? number)) [:warn "illegal-decimal"]
        (< number min-double) [:warn "illegal-decimal:too-small"]
        (> number max-double) [:warn "illegal-decimal:too-big"]))))

(defmethod subtype-validation :recent-year [{:keys [range]} v]
  (cond
    (blank? v) nil

    (re-matches #"^[1-9][0-9]*$" v) ;; n>0
    (let [given (util/->int v 0)
          current (.getValue (java.time.Year/now))]
      (cond
        (> given current)
        [:warn "illegal-recent-year:future"]
        (< given (- current range))
        [:warn "illegal-recent-year:too-past"]
        :else
        nil))
    :else
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

(defmethod subtype-validation :kiinteistotunnus [{:keys [transform]} v]
  (when (some-> (ss/blank-as-nil v)  va/kiinteistotunnus? not)
    [:warn (if (= transform :property-id)
             "invalid-property-id"
             "illegal-kiinteistotunnus")]))

(defmethod subtype-validation :y-tunnus [_ v]
  (cond
    (blank? v) nil
    (va/finnish-y? v) nil
    :else [:warn "illegal-y-tunnus"]))

;; This validator is only applicable to addresses without country
;; information. Finnish zips are validated by validate-element in
;; model.clj.
(defmethod subtype-validation :zip [_ v]
  (cond
    (blank? v) nil
    (va/finnish-zip? v) nil
    :else [:warn "illegal-zip"]))

(defmethod subtype-validation :rakennusnumero [_ v]
  (cond
    (blank? v) nil
    (va/rakennusnumero? v) nil
    :else [:warn "illegal-rakennusnumero"]))

(defmethod subtype-validation :rakennustunnus [_ v]
  (cond
    (blank? v) nil
    (va/rakennustunnus? v) nil
    :else [:warn "illegal-rakennustunnus"]))

(defmethod subtype-validation :vrk-name [_ v]
  (cond
    (blank? v) nil
    (re-matches #"^([\p{L}\-/ \.\*]+)$" v) nil
    :else [:warn "illegal-name"]))

(defmethod subtype-validation :vrk-address [_ _]
  nil)

(defmethod subtype-validation :maaraala-tunnus [_ v]
  (cond
    (blank? v) nil
    (re-matches #"^[0-9]{4}$" v) nil
    :else [:warn "illegal-maaraala-tunnus"]))

(defmethod subtype-validation :ovt [_ v]
  (cond
    (blank? v) nil
    (va/finnish-ovt? v) nil
    :else [:warn "illegal-ovt-tunnus"]))

(defmethod subtype-validation :zipcode [_ _]                ; passthrough, subtype is used only for schema data generation
  nil)

(defmethod subtype-validation nil [_ _]
  nil)

(defmethod subtype-validation :ryhti-address-name [_ v]
  ;; quality__req_address_addressName
  ;; \p{Lu} - all unicode uppercase letters
  ;; \p{Ll} - all unicode lowercase letters
  (cond
    (blank? v) nil
    ;; Rule: Katunumerossa ei saa esiintyä blankkoja alussa ja/tai lopussa.
    ;; Update-doc already calls trim (before value are saved to db), and additionally
    ;; datamodel code trims the unicode special spaces like non-breaking space.

    ;; Rule: Katunimessä pitää esiintyä vähintään yksi pieni tai iso kirjain
    (not (re-find #"(\p{Ll}|\p{Lu})" v))
    [:warn "illegal-address-name:capital-or-small"]

    ;; Rule: Yli yhden merkin mittaisessa katunimessä pitää esiintyä vähintään yksi pieni ja yksi iso kirjain
    (not (or (<= (count v) 1)
             (and (re-find #"\p{Lu}" v)
                  (re-find #"\p{Ll}" v))))
    [:warn "illegal-address-name:capital-and-small"]

    ;; Rule: Katunimessä ei saa esiintyä pelkkiä numeroita
    ;; NOTE: Can't ever trigger because one small or capital letter is checked first?
    ;; (re-matches #"[0-9]*" v)
    ;; [:warn "illegal-address-name:only-numbers"]

    :else nil))

(defmethod subtype-validation :default [elem _]
  (error "Unknown subtype:" elem)
  [:err "illegal-subtype"])
