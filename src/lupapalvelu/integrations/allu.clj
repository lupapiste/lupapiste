(ns lupapalvelu.integrations.allu
  (:require [clojure.walk :refer [postwalk]]
            [schema.core :as sc]
            [cheshire.core :as json]
            [iso-country-codes.core :refer [country-translate]]
            [sade.core :refer [def-]]
            [sade.http :as http]
            [lupapalvelu.i18n :refer [localize]]
            [lupapalvelu.document.tools :refer [doc-subtype]]
            [lupapalvelu.document.canonical-common :as doccc]
            [lupapalvelu.integrations.allu-schemas :refer [PlacementContract]]))

;;; FIXME: Avoid producing nil-valued fields.

;;;; Cleaning up :value indirections

(defn- flatten-values [app]
  (letfn [(node-value [node]
            (if (and (map? node) (contains? node :value))
              (:value node)
              node))]
    (postwalk node-value app)))

;;;; Conversion details

(defn- application-kind [{{operation :name} :primaryOperation}]
  (let [operation (keyword operation)
        kind (str (name (doccc/ya-operation-type-to-schema-name-key operation)) " / "
                  (doccc/ya-operation-type-to-usage-description operation))]
    (if-let [additional (doccc/ya-operation-type-to-additional-usage-description operation)]
      (str kind " / " additional)
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

(defn- drawing->GeoJSON-2008 [{:keys [geometry geometry-wgs84] :as drawing}]
  {:type "Feature"
   :geometry geometry-wgs84
   :properties (dissoc drawing :geometry :geometry-wgs84)}) ; TODO: dissoc even more (?)

(def- WGS84-URN "urn:ogc:def:crs:OGC:1.3:CRS84")

(defn- application-geometry [{:keys [drawings location-wgs84]}]
  (let [obj (if (seq drawings)
              {:type "FeatureCollection"
               :features (mapv drawing->GeoJSON-2008 drawings)}
              {:type "Point"
               :coordinates location-wgs84})]
    (assoc obj :crs {:type "name", :properties {:name WGS84-URN}})))

;; TODO: postal code
(defn- application-postal-address [{:keys [municipality address]}]
  {:city (localize "fi" :municipality municipality) ; FIXME: hardcoded "fi"
   :streetAddress {:streetName address}})

(defn- convert-value-flattened-app
  [{:keys [id propertyId drawings documents] :as app}]
  (let [customer-doc     (first (filter #(= (doc-subtype %) :hakija) documents))
        work-description (first (filter #(= (doc-subtype %) :hankkeen-kuvaus) documents))
        payee-doc        (first (filter #(= (doc-subtype %) :maksaja) documents))]
    {:clientApplicationKind (application-kind app)
     :customerReference     (-> payee-doc :data :laskuviite)
     :customerWithContacts  {:customer (doc->customer false customer-doc)
                             :contacts [(person->contact (customer-contact customer-doc))]}
     :geometry              {:geometryOperations (application-geometry app)}
     :identificationNumber  id
     :invoicingCustomer     (doc->customer true payee-doc) ; TODO: contacts
     :pendingOnClient true
     :postalAddress   (application-postal-address app)
     :propertyIdentificationNumber propertyId
     :workDescription (-> work-description :data :kayttotarkoitus)}))

;;;; Putting it all together

(sc/defn application->allu-placement-contract :- PlacementContract [app]
  (-> app flatten-values convert-value-flattened-app))

;; TODO: Use property files to configure allu-api-* instead.
(defn create-placement-contract! [allu-api-url allu-api-jwt]
  (fn [app]
    (http/post (str allu-api-url "placementcontracts")
               {:headers {:authorization (str "Bearer " allu-api-jwt)}
                :content-type :json
                :body (json/encode (application->allu-placement-contract app))})))
