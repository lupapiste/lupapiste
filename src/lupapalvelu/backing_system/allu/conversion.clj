(ns lupapalvelu.backing-system.allu.conversion
  "Conversions from Lupapiste applications to ALLU contract formats."
  (:require [clojure.core.match :refer [match]]
            [clojure.walk :refer [postwalk]]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clj-time.local :as tl]
            [iso-country-codes.core :refer [country-translate]]
            [schema.core :as sc :refer [optional-key]]
            [swiss.arrows :refer [-<>>]]
            [sade.coordinate :refer [WGS84-URN]]
            [sade.core :refer [def-]]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.backing-system.allu.schemas :refer [ContractBase PlacementContract ShortTermRental Promotion
                                                             LocationType]]
            [lupapalvelu.document.allu-schemas :refer [application-kinds]]
            [lupapalvelu.document.canonical-common :as canonical-common]
            [lupapalvelu.document.tools :as tools :refer [doc-name]]
            [lupapalvelu.i18n :refer [localize]]))

(def lang
  "The language to use when localizing output to ALLU. ALLU seems to operate in Finnish only so it is better to hardcode
  this here instead of using e.g. the applicant's language setting."
  "fi")

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
  [{:keys [osoite], {:keys [hetu ulkomainenHenkilotunnus not-finnish-hetu]} :henkilotiedot :as person}]
  (merge {:type        "PERSON"
          :registryKey (if not-finnish-hetu ulkomainenHenkilotunnus hetu)
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
      (util/assoc-when customer
                       :invoicingOperator (when-not (ss/blank? valittajaTunnus) valittajaTunnus)
                       :ovt (cond
                              (not (ss/blank? ovtTunnus)) ovtTunnus
                              (not (ss/blank? verkkolaskuTunnus)) verkkolaskuTunnus))
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
  (util/assoc-when {:name (fullname henkilotiedot), :phone puhelin, :email email}
              :postalAddress (some-> osoite convert-address)))

(defn- customer-contact
  "Get a contact person subdocument from a Lupapiste document."
  [customer-doc]
  (match (:data customer-doc)
    {:_selected "henkilo", :henkilo person} person
    {:_selected "yritys", :yritys {:yhteyshenkilo contact}} contact))

(defn- convert-customer
  "Convert a Lupapiste applicant or representative document into an ALLU CustomerWithContacts."
  [applicant-doc]
  {:customer (doc->customer false applicant-doc)
   :contacts [(person->contact (customer-contact applicant-doc))]})

(defn- convert-payee
  "Convert a Lupapiste payee document into an ALLU Customer."
  [payee-doc]
  (merge (doc->customer true payee-doc)
         (select-keys (person->contact (customer-contact payee-doc)) [:email :phone])))

(sc/defn ^:private application-geometry
  "Get the GeoJSON geometry data from the Lupapiste application."
  [location-type :- LocationType {:keys [drawings location-wgs84]}]
  (let [geometry (case location-type
                   "fixed" {:type       "GeometryCollection"
                            :geometries (into [] (comp (filter :allu-id)
                                                       (map :geometry-wgs84))
                                              drawings)}
                   "custom" (if (seq drawings)
                              {:type       "GeometryCollection"
                               :geometries (into [] (comp (remove :allu-id)
                                                          (map :geometry-wgs84))
                                                 drawings)}
                              {:type        "Point"
                               :coordinates location-wgs84}))]
    (assoc geometry :crs {:type "name", :properties {:name WGS84-URN}})))

(defn- application-postal-address
  "The postal address of a Lupapiste application."
  [{:keys [municipality address]}]
  ;; We don't have the postal code within easy reach so it is omitted here.
  {:city          (localize lang :municipality municipality)
   :streetAddress {:streetName address}})

(def format-date-time
  "Format a clj-time date-time into the string format expected by ALLU."
  (partial tf/unparse (tf/formatters :date-time-no-ms)))

(def- datetime-parser (tf/formatter "dd.MM.yyyy HH:mm:ss.SSS"))

(defn- convert-date-time
  "Convert an Allu-time document date and time string into a clj-time date-time."
  ([edge date]
   (convert-date-time edge date nil))
  ([edge date time]
   (let [time-fallback (case edge :start "3:00" :end "23:59")
         [_ hours minutes _ seconds _ tenths] (re-find util/time-pattern (or time time-fallback))
         seconds (or seconds "00")
         tenths (or tenths "0")
         millis (* (Integer/parseInt tenths) 100)]
     (->> (format "%s %s:%s:%s.%d" date hours minutes seconds millis)
          (tf/parse datetime-parser)
          tl/to-local-date-time))))

(defn- convert-short-term-rental-times
  "Compute short term rental (start|end)Time:s from lmv-time document."
  [{:keys [start-date end-date]}]
  {:startTime (convert-date-time :start start-date)
   :endTime   (convert-date-time :end end-date)})

(defn- convert-promotion-times
  "Compute :(event)?(start|end)Time:s as clj-time date-times from promootio-time document."
  [promootio-time]
  (let [event-start (convert-date-time :start (:start-date promootio-time) (:start-time promootio-time))
        event-end (convert-date-time :end (:end-date promootio-time) (:end-time promootio-time))]
    {:eventStartTime event-start
     :eventEndTime   event-end
     :startTime      (if (:build-days-needed promootio-time)
                       (convert-date-time :start (:build-start-date promootio-time) (:build-start-time promootio-time))
                       event-start)
     :endTime        (if (:demolish-days-needed promootio-time)
                       (convert-date-time :end (:demolish-end-date promootio-time) (:demolish-end-time promootio-time))
                       event-end)}))

(defn- join-structure-names
  "Joins the types of all the structures and also displays their areas for clarity"
  [structure-map]
  (ss/join ", "
            (for [[_ structure] structure-map
                  :let [area (if (or (nil? (:area structure)) (ss/blank? (:area structure)))
                               ""
                               (str ": " (:area structure) "m2"))
                        structure (:structure structure)]]
              (if (= "muu" (:structure-select structure))
                (str (:muu structure) area)
                (str (localize lang :promootio-structure (:structure-select structure)) area)))))

(defn- sum-structure-area
  "Sums the area of all the structures"
  [structure-map]
  (transduce (map (fn [[_ {:keys [area]}]] (util/->double area)))
             + 0.0 structure-map))

(defn- convert-value-flattened-app-to-partial-contract
  [pending-on-client {:keys [id] :as app} applicant-doc payee-doc rep-doc location-type]
  (let [res {:customerWithContacts (convert-customer applicant-doc)
             :geometry             (application-geometry location-type app)
             :identificationNumber id
             :invoicingCustomer    (convert-payee payee-doc)
             :pendingOnClient      pending-on-client
             :postalAddress        (application-postal-address app)}]
    (util/assoc-when res
                :customerReference (let [cref (-> payee-doc :data :laskuviite)]
                                     (when-not (sc/check (get ContractBase (optional-key :customerReference))
                                                         cref)
                                       cref))
                :representativeWithContacts (some-> rep-doc convert-customer))))

(defn- convert-value-flattened-app-to-placement-contract
  "Convert a Lupapiste application that has gone through [[tools/unwrap]] into an ALLU PlacementContract."
  [pending-on-client {:keys [id propertyId documents] :as app}]
  (let [applicant-doc (first (filter #(= (doc-name %) "hakija-ya") documents))
        work-description (first (filter #(= (doc-name %) "yleiset-alueet-hankkeen-kuvaus-sijoituslupa") documents))
        payee-doc (first (filter #(= (doc-name %) "yleiset-alueet-maksaja") documents))
        rep-doc (first (filter #(= (doc-name %) "hakijan-asiamies") documents))
        kind (application-kind app)
        start (t/now)
        end (t/plus start (t/years 1))]
    (assoc (convert-value-flattened-app-to-partial-contract pending-on-client app applicant-doc payee-doc rep-doc
                                                            "custom")
      :clientApplicationKind kind
      :endTime (format-date-time end)
      :name (str id " " kind)
      :propertyIdentificationNumber propertyId
      :startTime (format-date-time start)
      :workDescription (-> work-description :data :kayttotarkoitus))))

(defn- convert-value-flattened-app-to-short-term-rental
  "Convert a Lupapiste application that has gone through [[tools/unwrap]] into an ALLU PlacementContract."
  [pending-on-client {:keys [id documents] :as app}]
  (let [applicant-doc (first (filter #(= (doc-name %) "hakija-ya") documents))
        work-details (first (filter #(= (doc-name %) "lyhytaikainen-maanvuokraus") documents))
        location-doc (first (filter #(= (doc-name %) "lmv-location") documents))
        time-details (first (filter #(= (doc-name %) "lmv-time") documents))
        payee-doc (first (filter #(= (doc-name %) "yleiset-alueet-maksaja") documents))
        rep-doc (first (filter #(= (doc-name %) "hakijan-asiamies") documents))
        kind (-> work-details :data :lyhytaikainen-maanvuokraus :kind)
        kind (get application-kinds (keyword kind))
        location-type (-> location-doc :data :lmv-location :location-type)
        location-ids (filter #(not (nil? %)) (mapv :allu-id (:drawings app)))
        res (assoc (convert-value-flattened-app-to-partial-contract pending-on-client app applicant-doc payee-doc
                                                                    rep-doc location-type)
              :applicationKind kind
              :name (str id " " kind)
              :fixedLocationIds location-ids
              :description (-> work-details :data :lyhytaikainen-maanvuokraus :description))
        res (util/assoc-when res :area (-> location-doc :data :lmv-location :area (util/->double nil)))]
    (merge res (util/map-values format-date-time
                                (convert-short-term-rental-times (-> time-details :data :lmv-time))))))

(defn- convert-value-flattened-app-to-event
  "Convert a Lupapiste application that has gone through [[tools/unwrap]] into an ALLU Event (= Promotion)."
  [pending-on-client {:keys [documents] :as app}]
  (let [applicant-doc (first (filter #(= (doc-name %) "hakija-ya") documents))
        work-details (first (filter #(= (doc-name %) "promootio") documents))
        time-details (first (filter #(= (doc-name %) "promootio-time") documents))
        location-doc (first (filter #(= (doc-name %) "promootio-location") documents))
        structure-details (first (filter #(= (doc-name %) "promootio-structures") documents))
        structure-description (join-structure-names (-> structure-details :data :promootio-structures :structures))
        payee-doc (first (filter #(= (doc-name %) "yleiset-alueet-maksaja") documents))
        rep-doc (first (filter #(= (doc-name %) "hakijan-asiamies") documents))
        ;; If user draws custom geometries, :allu-id is nil and nils do not conform to spec:
        location-type (-> location-doc :data :promootio-location :location-type)
        location-ids (filter #(not (nil? %)) (mapv :allu-id (:drawings app)))
        res (assoc (convert-value-flattened-app-to-partial-contract pending-on-client app applicant-doc payee-doc
                                                                    rep-doc location-type)
              :name (-> work-details :data :promootio :promootio-name)
              :fixedLocationIds location-ids
              :description (-> work-details :data :promootio :promootio-description)
              :structureArea (sum-structure-area (-> structure-details :data :promootio-structures :structures)))
        res (util/assoc-when-pred res ss/not-blank? :structureDescription structure-description)]
    (merge res (util/map-values format-date-time
                                (convert-promotion-times (-> time-details :data :promootio-time))))))

(sc/defn application->allu-placement-contract :- PlacementContract
  "Convert a Lupapiste application into an ALLU PlacementContract."
  [pending-on-client app]
  (->> app tools/unwrapped (convert-value-flattened-app-to-placement-contract pending-on-client)))

(sc/defn application->allu-short-term-rental :- ShortTermRental
  "Convert a Lupapiste application into an ALLU ShortTermRental."
  [pending-on-client app]
  (->> app tools/unwrapped (convert-value-flattened-app-to-short-term-rental pending-on-client)))

(sc/defn application->allu-promotion :- Promotion
  "Convert a Lupapiste application into an ALLU Event (= Promotion)"
  [pending-on-client app]
  (->> app tools/unwrapped (convert-value-flattened-app-to-event pending-on-client)))
