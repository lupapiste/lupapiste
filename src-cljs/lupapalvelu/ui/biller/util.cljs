(ns lupapalvelu.ui.biller.util
  (:require [goog.date :as good]
            [goog.string :as goos]
            [lupapalvelu.pate.path :as path]
            [lupapalvelu.ui.common :as common]
            [sade.strings :as ss])
  (:import [goog.i18n DateTimeFormat NumberFormat]))

(def ^:private euro-fmt
  (NumberFormat. NumberFormat.Format.CURRENCY
                 "EUR"
                 NumberFormat.CurrencyStyle.LOCAL))

(def ^:private datetime-fmts
  {;; 18.8.2022 13.16
   :short-datetime  (DateTimeFormat. DateTimeFormat.Format.SHORT_DATETIME)
   ;; 18.8.2022 klo 13.16.51
   :medium-datetime (DateTimeFormat. DateTimeFormat.Format.MEDIUM_DATETIME)
   ;; 18.8.2022
   :short-date      (DateTimeFormat. DateTimeFormat.Format.SHORT_DATE)})

(defn euro-format
  "123.45   -> 123,45 €
   123      -> 123,00 (`:symbol?` false)
   123      -> 123 €  (`:zeros?` false)
   123.4567 -> 123,46 € (`:zeros?` false, but no zeros)"
  ([amount-in-euros {:keys [symbol? zeros?]}]
   (when amount-in-euros
     (cond-> (.format euro-fmt amount-in-euros)
       (false? symbol?) (ss/replace #"[^0-9]+$" "")
       (false? zeros?) (ss/replace #",00" ""))))
  ([amount-in-euros]
   (euro-format amount-in-euros nil)))

(defn cents->eur
  ([amount-in-cents opts]
   (euro-format (/ amount-in-cents 100.0) opts))
  ([amount-in-cents]
   (cents->eur amount-in-cents nil)))

(defn datetime-format
  ([timestamp-in-ms fmt-key]
   (when timestamp-in-ms
     (.format (get datetime-fmts fmt-key)
              (good/DateTime.fromTimestamp timestamp-in-ms))))
  ([timestamp-in-ms]
   (datetime-format timestamp-in-ms :short-date)))

(defn operator-string [operator]
  (some->> operator ss/blank-as-nil
           (path/loc :osapuoli.yritys.verkkolaskutustieto.valittajaTunnus)))

(defn country-string [country]
  (some->> country ss/blank-as-nil (path/loc :country)))

(defn backend-text
  ([{:keys [code text]} backend-id]
   (goos/format "%s (%s)" text (or backend-id code)))
  ([item]
   (backend-text item nil)))

(defn unknown-backend-code
  [backend-code]
  (common/loc :invoicing.backend-id.codes.unknown backend-code))

(defn id-string [id]
  (when-let [id (some-> id ss/trim ss/blank-as-nil)]
    (str "(" id ")")))
