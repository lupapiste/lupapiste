(ns conversion.imports
  "Staging and template area for importing conversion schemas and sections. While the actual
  schemas/sections are stored in mongo, having a selection of templates makes it easier to
  create new definitions."
  (:require [lupapalvelu.conversion.section-schemas :as sec-sc]
            [sade.date :as date]
            [sade.util :as util]
            [schema.core :as sc]))

(def collateral-schema
  {:op         :replace
   :name       "collateral"
   :title      :pate-collateral
   :dictionary {:type        {:text {:i18nkey :pate.collateral-type}}
                :amount      {:text {:i18nkey :pate.collateral
                                     :css     :txt--right
                                     :after   :eur}}
                :date        {:date {:i18nkey :pate.collateral-date}}
                :section     {:text {:i18nkey :pate-verdict.section
                                     :before  :section
                                     :css     :txt--left}}
                :description {:text {:i18nkey :collateral.description
                                     :lines   5}}
                :givers      {:text {:i18nkey :collateral.givers
                                     :lines   5}}}
   :rows       [[{:box {:css   :flex--column.flex--g08
                        :items [{:box {:items [{:box {:css   :flex--column
                                                      :items [{:dict :type}
                                                              {:dict :section}]}}
                                               {:box {:css   :flex--column
                                                      :items [{:dict :amount}
                                                              {:dict :date}]}}
                                               {:box-css :flex--column.flex--g08.flex--self-stretch
                                                :dict    :givers
                                                :css     :full-width.flex--self-stretch.flex--g1}]}}
                                {:box {:items [{:box-css :flex--g08.flex--self-stretch
                                                :css     :full-width
                                                :dict    :description}]}}]}}]]})

(sc/validate sec-sc/SchemaDocumentImport collateral-schema)

(defn empty-section [backend-id section-name schema-name]
  {backend-id [{:op          :replace
                :name        section-name
                :schema-name schema-name}]})

(defn safe-long
  "Returns `n` as long, if its decimal part is zero."
  [n]
  (cond-> n
    (and (number? n) (zero? (mod n 1.0)))
    long))

(defn vakuus-section [m]
  {(:backend-id m) [{:op          :replace
                     :name        "vakuus"
                     :schema-name "collateral"
                     :data        (-> (dissoc m :backend-id)
                                      (update :date date/timestamp)
                                      util/strip-blanks)}]})
