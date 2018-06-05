(ns lupapalvelu.integrations.allu
  (:require [clojure.walk :refer [postwalk]]
            [schema.core :as sc]
            [cheshire.core :as json]
            [iso-country-codes.core :refer [country-translate]]
            [sade.util :refer [assoc-when]]
            [sade.core :refer [def-]]
            [sade.env :as env]
            [sade.http :as http]
            [lupapalvelu.i18n :refer [localize]]
            [lupapalvelu.document.tools :refer [doc-subtype]]
            [lupapalvelu.document.canonical-common :as doccc]
            [lupapalvelu.integrations.allu-schemas :refer [PlacementContract]]))

;;; FIXME: Avoid producing nil-valued fields.

;;;; Constants

(def- lang
  "The language to use when localizing output to ALLU"
  "fi")

(def- WGS84-URN "urn:ogc:def:crs:OGC:1.3:CRS84")

;;;; Cleaning up :value indirections

(def- flatten-values (partial postwalk (some-fn :value identity)))

;;;; Conversion details

(defn- application-kind [app]
  (let [operation (-> app :primaryOperation :name keyword)
        kind (str (name (doccc/ya-operation-type-to-schema-name-key operation)) " / "
                  (doccc/ya-operation-type-to-usage-description operation))]
    (or (some->> (doccc/ya-operation-type-to-additional-usage-description operation)
                 (str kind " / "))
        kind)))

(def- convert-customer-type
  {:henkilo "PERSON", :yritys "COMPANY"})

(defn- customer-registry-key [customer-type customer]
  (case customer-type
    :henkilo (-> customer :henkilotiedot :hetu)
    :yritys  (:liikeJaYhteisoTunnus customer)))

(defn- fullname [{:keys [etunimi sukunimi]}]
  (str etunimi " " sukunimi))

(defn- customer-name [customer-type customer]
  (case customer-type
    :henkilo (fullname (:henkilotiedot customer))
    :yritys  (:yritysnimi customer)))

(defn- address-country [address]
  (country-translate :alpha-3 :alpha-2 (:maa address)))

(defn- convert-address [{:keys [postitoimipaikannimi postinumero katu]}]
  {:city          postitoimipaikannimi
   :postalCode    postinumero
   :streetAddress {:streetName katu}})

(defn- company-invoicing
  [{{:keys [verkkolaskuTunnus ovtTunnus valittajaTunnus]} :verkkolaskutustieto}]
  {:invoicingOperator valittajaTunnus
   :ovt (if (seq ovtTunnus) ovtTunnus verkkolaskuTunnus)}) ; TODO: Why do we even have both fields?

(defn- doc->customer [payee? {{tag :_selected :as data} :data}]
  (let [tag (keyword tag)
        customer (get data tag)
        address (:osoite customer)
        allu-customer {:type          (convert-customer-type tag)
                       :registryKey   (customer-registry-key tag customer)
                       :name          (customer-name tag customer)
                       :country       (address-country address)
                       :postalAddress (convert-address address)}]
    (if (and payee? (= tag :yritys))
      (merge allu-customer (company-invoicing customer))
      allu-customer)))

(defn- person->contact [{:keys [henkilotiedot], {:keys [puhelin email]} :yhteystiedot}]
  {:name (fullname henkilotiedot), :phone puhelin, :email email})

(defn- customer-contact [{{tag :_selected :as data} :data :as doc}]
  (let [tag (keyword tag)]
    (case tag
      :henkilo (get data tag)
      :yritys  (:yhteyshenkilo (get data tag)))))

(defn- convert-applicant [applicant-doc]
  {:customer (doc->customer false applicant-doc)
   :contacts [(person->contact (customer-contact applicant-doc))]})

(defn- convert-payee [payee-doc]
  (let [{:keys [email phone]} (person->contact (customer-contact payee-doc))]
    (assoc (doc->customer true payee-doc) :phone phone :email email)))

(defn- drawing->GeoJSON-2008 [{:keys [geometry-wgs84] :as drawing}]
  {:type "Feature"
   :geometry geometry-wgs84
   :properties (dissoc drawing :geometry :geometry-wgs84)}) ; TODO: dissoc even more (?)

(defn- application-geometry [{:keys [drawings location-wgs84]}]
  (let [obj (if (seq drawings)
              {:type "FeatureCollection"
               :features (mapv drawing->GeoJSON-2008 drawings)}
              {:type "Point"
               :coordinates location-wgs84})]
    (assoc obj :crs {:type "name", :properties {:name WGS84-URN}})))

(defn- application-postal-address [{:keys [municipality address]}]
  ;; We don't have the postal code within easy reach so it is omitted here.
  {:city (localize lang :municipality municipality)
   :streetAddress {:streetName address}})

(defn- convert-value-flattened-app
  [{:keys [id propertyId drawings documents] :as app}]
  (let [applicant-doc    (first (filter #(= (doc-subtype %) :hakija) documents))
        work-description (first (filter #(= (doc-subtype %) :hankkeen-kuvaus) documents))
        payee-doc        (first (filter #(= (doc-subtype %) :maksaja) documents))
        res              {:clientApplicationKind (application-kind app)
                          :customerWithContacts  (convert-applicant applicant-doc)
                          :geometry              {:geometryOperations (application-geometry app)}
                          :identificationNumber  id
                          :invoicingCustomer     (convert-payee payee-doc)
                          :pendingOnClient       true
                          :postalAddress         (application-postal-address app)
                          :propertyIdentificationNumber propertyId
                          :workDescription (-> work-description :data :kayttotarkoitus)}]
    (assoc-when res :customerReference (not-empty (-> payee-doc :data :laskuviite)))))

;;;; Putting it all together

(sc/defn application->allu-placement-contract :- PlacementContract [app]
  (-> app flatten-values convert-value-flattened-app))

(defn create-placement-contract! [app]
  (http/post (str (env/value :allu :url) "/placementcontracts")
             {:headers {:authorization (str "Bearer " (env/value :allu :jwt))}
              :content-type :json
              :body (json/encode (application->allu-placement-contract app))}))
