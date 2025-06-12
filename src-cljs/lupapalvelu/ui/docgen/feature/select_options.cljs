(ns lupapalvelu.ui.docgen.feature.select-options
  "Answer the 'what translation key corresponds to this select option?' question."
  (:require [lupapalvelu.ui.docgen.feature.documents :as docs]
            [lupapalvelu.ui.docgen.feature.field-schemas :as field-schemas]
            [lupapalvelu.ui.docgen.path :as path]
            [re-frame.core :as rf]
            [sade.util :as util]))

(defn- option-loc-key
  [element schema-i18nkey doc-i18nkey path]
  (or (:i18nkey element)
      (and schema-i18nkey (path/path->str [schema-i18nkey (:name element)]))
      (path/path->str (concat [doc-i18nkey]
                              (path/path-without-indexes path)
                              [(:name element)]))))

(defn- make-options-map-entry
  "Construct a map entry of `value`->`loc-key` for a `select` option."
  [element {:keys [i18nkey doc-i18nkey path]}]
  [(:name element) (option-loc-key element i18nkey doc-i18nkey path)])

(defn make-options-map
  "Construct a map of `value`->`loc-key` for the possible `select` options."
  [{:keys [schema doc-i18nkey path]}]
  {:pre [(map? schema)
         (keyword? doc-i18nkey)
         (vector? path)]}
  (let [{:keys [body i18nkey other-key]} schema]
    (cond-> (->> body
                 (map (fn [element]
                        (make-options-map-entry element (util/mmap i18nkey doc-i18nkey path))))
                 (into {}))
      ;; Sometimes `select` elements contain a "Other" choice
      other-key (assoc "other" "select-other"))))

(rf/reg-sub ::options-map
  (fn options-map-inputfn [[_ doc-id path]]
    [(rf/subscribe [::docs/i18n-name doc-id])
     (rf/subscribe [::field-schemas/field-schema doc-id (path/path-without-indexes path)])])
  (fn [[doc-i18nkey schema] [_ _doc-id path]]
    (when (and schema (util/=as-kw :select (:type schema)))
      (make-options-map (util/mmap schema path doc-i18nkey)))))
