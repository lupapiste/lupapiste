(ns lupapalvelu.invoices.integration.general.converter
  (:require [clojure.walk :as walk]
            [com.climate.claypoole :as cp]
            [iso-country-codes.core :refer [country-translate]]
            [lupapalvelu.invoices.integration.general.xml :as general-xml]
            [lupapalvelu.invoices.schemas :refer [GeneralApiXMLInput]]
            [lupapalvelu.invoices.util :as invoices-util]
            [lupapalvelu.mml.geocoding.core :as geocoding]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(def system-id "Lupapiste")

(defn- ->invoice-row [{:keys [code text unit units price-per-unit
                              discount-percent product-constants]}]
  (util/assoc-when {:code             (or code "")
                    :name             text
                    :unit             unit
                    :quantity         units
                    :discount-percent (or discount-percent 0)
                    :unitprice        price-per-unit}
                   :product-constants (->> product-constants
                                           (util/filter-map-by-val ss/not-blank?)
                                           not-empty)))

(defn- tidywalk
  "Walks `form`, trims string values and replaces nil values with empty strings. Converts
  booleans to strings."
  [form]
  (when-not (nil? form)
    (let [empty-string (constantly "")]
      (walk/postwalk (fn [v]
                       (util/pcond-> v
                         boolean? str
                         string?  ss/trim
                         nil?     empty-string))
                     form))))

(sc/defn ^:private ->invoice-data :- GeneralApiXMLInput
  "The general (non-SAP) invoice data for transferring to backend system (for most municipalities)"
  [{:keys [use-alpha-2-country-code? constants] :as invoicing-config} {:keys [enriched-data backend-id] :as invoice}] 
  (let [{:keys [myyntiorg jakelutie
                sektori]}   constants
        {:keys [address
                location
                property-id
                operation]} (:application enriched-data)
        protected?          (boolean (:protected? invoice))
        organization-payer? (invoices-util/company-payer? invoice)
        payer-base          {:streetaddress (:entity-address invoice)
                             :postalcode    (:postal-code invoice)
                             :city          (:city invoice)
                             :country       (if use-alpha-2-country-code? 
                                              (country-translate :alpha-3 :alpha-2 (:country invoice))
                                              (:country invoice))
                             :partner-code  (:partner-code invoice)}
        ;; zipcode (and municipality name) not stored on the app so fetch them
        mml-data            (if location
                              (apply geocoding/address-by-point! "fi" location)
                              (throw (ex-info "No location provided! Does the application exist?"
                                              {:app-id     (:application-id invoice)
                                               :invoice-id (:id invoice)})))
        data                {:invoice-type (if (:organization-internal-invoice? invoice) "INTERNAL" "EXTERNAL")
                             :reference    (:billing-reference invoice)
                             :case         (:case-reference invoice)
                             :letter       (:letter invoice)
                             :payer        (merge
                                             {:payer-type (if organization-payer? "ORGANIZATION" "PERSON")}
                                             (if organization-payer?
                                               {:organization (merge payer-base
                                                                     {:id                  (:company-id invoice)
                                                                      :name                (:entity-name invoice)
                                                                      :contact-firstname   (:first-name invoice)
                                                                      :contact-lastname    (:last-name invoice)
                                                                      :contact-turvakielto protected?
                                                                      :einvoice-address    (:netbill invoice)
                                                                      :edi                 (:ovt invoice)
                                                                      :operator            (:operator invoice)})}

                                               {:person (merge payer-base
                                                               {:id          (:person-id invoice)
                                                                :firstname   (:first-name invoice)
                                                                :lastname    (:last-name invoice)
                                                                :turvakielto protected?})}))
                             ;; from organization's invoicing-config
                             :payee        {:payee-organization-id myyntiorg
                                            :payee-group           jakelutie
                                            :payee-sector          sektori}
                             ;; from application
                             :target       {:street      address
                                            :postal-code (:postal-code mml-data)
                                            :city        (:fi (:name mml-data))}
                             :operation    operation
                             :property-id  property-id
                             ;; from invoice
                             :permit-id    (:application-id invoice)
                             :customer     {:client-number (:sap-number invoice)}
                             :invoice-rows (map ->invoice-row (:rows invoice))}]
    (cond-> (tidywalk data)
      (:backend-id? invoicing-config)
      (util/assoc-when :backend-id backend-id))))

(defn ->xml [invoicing-config timestamp invoices]
  (->> invoices
       ;; Mitigating the timeout risk caused by slow MML queries.
       (cp/pmap 8 (partial ->invoice-data invoicing-config))
       (general-xml/->xml timestamp system-id)))
