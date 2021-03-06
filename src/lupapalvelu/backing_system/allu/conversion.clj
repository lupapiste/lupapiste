(ns lupapalvelu.backing-system.allu.conversion
  "Conversions from Lupapiste applications to ALLU contract formats."
  (:require [clojure.core.match :refer [match]]
            [clojure.walk :refer [postwalk]]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [iso-country-codes.core :refer [country-translate]]
            [schema.core :as sc :refer [optional-key]]
            [sade.core :refer [def-]]
            [sade.util :refer [assoc-when]]
            [lupapalvelu.backing-system.allu.schemas :refer [PlacementContract]]
            [lupapalvelu.document.canonical-common :as canonical-common]
            [lupapalvelu.document.tools :refer [doc-name]]
            [lupapalvelu.i18n :refer [localize]]
            [sade.util :as util]))

(def lang
  "The language to use when localizing output to ALLU. ALLU seems to operate in Finnish only so it is better to hardcode
  this here instead of using e.g. the applicant's language setting."
  "fi")

(def- WGS84-URN
  "Uniform Resource Name for the WGS 84 coordinate system famous for GPS."
  "EPSG:4326")

(def- flatten-values
  "Replace {:value 23 ...} subtrees with just the value (e.g. 23)."
  (partial postwalk (some-fn :value identity)))

(defn- application-kind
  "Concat a description string to be used in PlacementContract :clientApplicationKind and :name."
  [app]
  (let [operation (-> app :primaryOperation :name keyword)
        kind (str (name (canonical-common/ya-operation-type-to-schema-name-key operation)) " / "
                  (canonical-common/ya-operation-type-to-usage-description operation))]
    (if-let [usage-description (canonical-common/ya-operation-type-to-additional-usage-description operation)]
      (str kind " / " usage-description)
      kind)))

(defn- fullname
  "Compute 'Firstname Lastname' from a Lupapiste personal information subdocument."
  [{:keys [etunimi sukunimi]}]
  (str etunimi " " sukunimi))

(defn- address-country
  "The 2-letter country code of the given Lupapiste document address."
  [address]
  (country-translate :alpha-3 :alpha-2 (:maa address)))

(defn- convert-address
  "Convert a Lupapiste document address into an ALLU PlacementContract address."
  [{:keys [postitoimipaikannimi postinumero katu]}]
  {:city          postitoimipaikannimi
   :postalCode    postinumero
   :streetAddress {:streetName katu}})

(declare person->contact)

(defn- person->customer
  "Convert a Lupapiste person subdocument into an ALLU Customer."
  [{:keys [osoite], {:keys [hetu]} :henkilotiedot :as person}]
  (merge {:type        "PERSON"
          :registryKey hetu
          :country     (address-country osoite)}
         (person->contact person)))

(defn- company->customer
  "Convert a Lupapiste company subdocument into an ALLU Customer."
  [payee? company]
  (let [{:keys                                                 [osoite liikeJaYhteisoTunnus yritysnimi]
         {:keys [verkkolaskuTunnus ovtTunnus valittajaTunnus]} :verkkolaskutustieto} company
        customer {:type          "COMPANY"
                  :registryKey   liikeJaYhteisoTunnus
                  :name          yritysnimi
                  :country       (address-country osoite)
                  :postalAddress (convert-address osoite)}]
    (if payee?
      (let [customer (util/assoc-when-pred customer
                                           (fn [customer] (not (nil? (:value customer))))
                                           :invoicingOperator valittajaTunnus)]
        (if (or (and (seq ovtTunnus) (not-empty ovtTunnus))
                (and (seq verkkolaskuTunnus) (not-empty verkkolaskuTunnus)))
          (assoc customer :ovt (if (and (seq ovtTunnus) (not-empty ovtTunnus)) ovtTunnus verkkolaskuTunnus))
          customer))
      customer)))

(defn- doc->customer
  "Convert a Lupapiste document into an ALLU Customer."
  [payee? doc]
  (match (:data doc)
    {:_selected "henkilo", :henkilo person} (person->customer person)
    {:_selected "yritys", :yritys company} (company->customer payee? company)))

(defn- person->contact
  "Convert a Lupapiste person subdocument into an ALLU Contact."
  [{:keys [henkilotiedot osoite], {:keys [puhelin email]} :yhteystiedot}]
  (assoc-when {:name (fullname henkilotiedot), :phone puhelin, :email email}
              :postalAddress (some-> osoite convert-address)))

(defn- customer-contact
  "Get a contact person subdocument from a Lupapiste document."
  [customer-doc]
  (match (:data customer-doc)
    {:_selected "henkilo", :henkilo person} person
    {:_selected "yritys", :yritys {:yhteyshenkilo contact}} contact))

(defn- convert-applicant
  "Convert a Lupapiste applicant document into an ALLU CustomerWithContacts."
  [applicant-doc]
  {:customer (doc->customer false applicant-doc)
   :contacts [(person->contact (customer-contact applicant-doc))]})

(defn- convert-payee
  "Convert a Lupapiste payee document into an ALLU Customer."
  [payee-doc]
  (merge (doc->customer true payee-doc)
         (select-keys (person->contact (customer-contact payee-doc)) [:email :phone])))

(defn- application-geometry
  "Get the GeoJSON geometry data from the Lupapiste application."
  [{:keys [drawings location-wgs84]}]
  (assoc (if (seq drawings)
           {:type       "GeometryCollection"
            :geometries (mapv :geometry-wgs84 drawings)}
           {:type        "Point"
            :coordinates location-wgs84})
    :crs {:type "name", :properties {:name WGS84-URN}}))

(defn- application-postal-address
  "The postal address of a Lupapiste application."
  [{:keys [municipality address]}]
  ;; We don't have the postal code within easy reach so it is omitted here.
  {:city          (localize lang :municipality municipality)
   :streetAddress {:streetName address}})

(def format-date-time
  "Format a clj-time date-time into the string format expected by ALLU."
  (partial tf/unparse (tf/formatters :date-time-no-ms)))

(defn- convert-value-flattened-app
  "Convert a Lupapiste application that has gone through `flatten-values` into an ALLU PlacementContract."
  [pending-on-client {:keys [id propertyId documents] :as app}]
  (let [applicant-doc (first (filter #(= (doc-name %) "hakija-ya") documents))
        work-description (first (filter #(= (doc-name %) "yleiset-alueet-hankkeen-kuvaus-sijoituslupa") documents))
        payee-doc (first (filter #(= (doc-name %) "yleiset-alueet-maksaja") documents))
        kind (application-kind app)
        start (t/now)
        end (t/plus start (t/years 1))
        res {:clientApplicationKind        kind
             :customerWithContacts         (convert-applicant applicant-doc)
             :endTime                      (format-date-time end)
             :geometry                     (application-geometry app)
             :identificationNumber         id
             :invoicingCustomer            (convert-payee payee-doc)
             :name                         (str id " " kind)
             :pendingOnClient              pending-on-client
             :postalAddress                (application-postal-address app)
             :propertyIdentificationNumber propertyId
             :startTime                    (format-date-time start)
             :workDescription              (-> work-description :data :kayttotarkoitus)}]
    (assoc-when res :customerReference (let [cref (-> payee-doc :data :laskuviite)]
                                         (when-not (sc/check (get PlacementContract (optional-key :customerReference))
                                                             cref)
                                           cref)))))

(sc/defn application->allu-placement-contract :- PlacementContract
  "Convert a Lupapiste application into an ALLU PlacementContract."
  [pending-on-client app]
  (->> app flatten-values (convert-value-flattened-app pending-on-client)))
