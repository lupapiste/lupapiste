(ns lupapalvelu.invoices.integration.sap-sbo.error-util
  "Utilities for producing localized invoice validation error messages"
  (:require [lupapalvelu.i18n :as i18n]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn loc-field
  "Return a translation key for a SAP field"
  [field-kw]
  (util/kw-path :sap.sbo.error.field field-kw))

(defn- localize-field-path
  "Represent a path to a field as a localized string.
  E.g., `[\"invoices\" 0 \"rows\" 0 \"code\"]` => \"Lasku: 1 > Rivi: 1 > Koodi\"."
  [field lang]
  (->> field
       (map (fn [part]
              (if (string? part)
                (i18n/localize lang (loc-field part))
                (inc part))))
       (partition-all 2 1)
       (reduce (fn [acc [current next]]
                 (cond
                   (number? next) (into acc [current ": "])
                   (some? next)   (into acc [current " > "])
                   :else          (into acc [current])))
               [])
       (apply str)))

(defn- with-field-location
  "Include a localized description of the problematic field (ie. where is the error)"
  [lang {:keys [field] :as error}]
  (assoc-in error [:loc :location] (localize-field-path field lang)))

(defn- with-expected-error
  "Include a localized error message (ie. what is the error)"
  [lang {:keys [expected] :as error}]
  (let [[expected-key & expected-args] (when (coll? expected)
                                         expected)
        ;; The `se/has-one-of` schema specifies a list of required keys
        ;; -> produce a string of field names
        expected-args                  (if (= :required-keys expected-key)
                                         (->> expected-args
                                              first
                                              (map (fn [field]
                                                     (i18n/localize lang (loc-field field))))
                                              sort
                                              (ss/join ", ")
                                              vector)
                                         expected-args)
        error-text                     (cond
                                         ;; Use the data from the schema to describe what is wrong
                                         ;; e.g., the value should be between X and Y characters long
                                         expected-key (apply i18n/localize-and-fill
                                                             lang
                                                             (util/kw-path :sap.sbo.error.expected expected-key)
                                                             expected-args)
                                         ;; Or give a more generic "value is invalid" error as a fallback
                                         :else        (i18n/localize lang (util/kw-path :sap.sbo.error (:error error))))]
    (assoc-in error [:loc :error] error-text)))

(defn with-localized-errors
  [lang errors]
  (->> errors
       (mapv (comp (partial with-field-location lang)
                   (partial with-expected-error lang)))))
