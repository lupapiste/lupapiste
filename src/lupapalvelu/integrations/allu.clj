(ns lupapalvelu.integrations.allu
  (:require [clojure.string :as s]
            [clojure.walk :refer [postwalk]]
            [mount.core :refer [defstate]]
            [schema.core :as sc]
            [cheshire.core :as json]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [iso-country-codes.core :refer [country-translate]]
            [sade.util :refer [assoc-when]]
            [sade.core :refer [def- fail!]]
            [sade.env :as env]
            [sade.http :as http]
            [lupapalvelu.i18n :refer [localize]]
            [lupapalvelu.document.tools :refer [doc-subtype]]
            [lupapalvelu.document.canonical-common :as doccc]
            [lupapalvelu.integrations.allu-schemas :refer [ValidPlacementApplication PlacementContract]]))

;;;; # Constants

(def- lang
  "The language to use when localizing output to ALLU"
  "fi")

(def- WGS84-URN "EPSG:4326")

;;;; # Cleaning up :value indirections

(def- flatten-values (partial postwalk (some-fn :value identity)))

;;;; # Application conversion

(defn- application-kind [app]
  (let [operation (-> app :primaryOperation :name keyword)
        kind (str (name (doccc/ya-operation-type-to-schema-name-key operation)) " / "
                  (doccc/ya-operation-type-to-usage-description operation))]
    (or (some->> (doccc/ya-operation-type-to-additional-usage-description operation)
                 (str kind " / "))
        kind)))

(defn- fullname [{:keys [etunimi sukunimi]}]
  (str etunimi " " sukunimi))

(defn- address-country [address]
  (country-translate :alpha-3 :alpha-2 (:maa address)))

(defn- convert-address [{:keys [postitoimipaikannimi postinumero katu]}]
  {:city          postitoimipaikannimi
   :postalCode    postinumero
   :streetAddress {:streetName katu}})

(defmulti ^:private doc->customer (fn [_payee doc] (-> doc :data :_selected)))

(defmethod doc->customer "henkilo" [_ {{{:keys [osoite], {:keys [hetu] :as person} :henkilotiedot} :henkilo} :data}]
  {:type          "PERSON"
   :registryKey   hetu
   :name          (fullname person)
   :country       (address-country osoite)
   :postalAddress (convert-address osoite)})

(defmethod doc->customer "yritys" [payee? {{company :yritys} :data}]
  (let [{:keys                                                 [osoite liikeJaYhteisoTunnus yritysnimi]
         {:keys [verkkolaskuTunnus ovtTunnus valittajaTunnus]} :verkkolaskutustieto} company
        customer {:type          "COMPANY"
                  :registryKey   liikeJaYhteisoTunnus
                  :name          yritysnimi
                  :country       (address-country osoite)
                  :postalAddress (convert-address osoite)}]
    (if payee?
      (assoc customer :invoicingOperator valittajaTunnus
                      ;; TODO: Why do we even have both ovtTunnus and verkkolaskuTunnus?
                      :ovt (if (seq ovtTunnus) ovtTunnus verkkolaskuTunnus))
      customer)))

(defn- person->contact [{:keys [henkilotiedot], {:keys [puhelin email]} :yhteystiedot}]
  {:name (fullname henkilotiedot), :phone puhelin, :email email})

(defmulti ^:private customer-contact (comp :_selected :data))

(defmethod customer-contact "henkilo" [{{person :henkilo} :data}]
  person)

(defmethod customer-contact "yritys" [{{company :yritys} :data}]
  (:yhteyshenkilo company))

(defn- convert-applicant [applicant-doc]
  {:customer (doc->customer false applicant-doc)
   :contacts [(person->contact (customer-contact applicant-doc))]})

(defn- convert-payee [payee-doc]
  (let [{:keys [email phone]} (person->contact (customer-contact payee-doc))]
    (assoc (doc->customer true payee-doc) :phone phone :email email)))

(defn- application-geometry [{:keys [drawings location-wgs84]}]
  (let [obj (if (seq drawings)
              {:type       "GeometryCollection"
               :geometries (mapv :geometry-wgs84 drawings)}
              {:type        "Point"
               :coordinates location-wgs84})]
    (assoc obj :crs {:type "name", :properties {:name WGS84-URN}})))

(defn- application-postal-address [{:keys [municipality address]}]
  ;; We don't have the postal code within easy reach so it is omitted here.
  {:city          (localize lang :municipality municipality)
   :streetAddress {:streetName address}})

(def- format-date-time (partial tf/unparse (tf/formatters :date-time-no-ms)))

(defn- convert-value-flattened-app
  [{:keys [id propertyId documents] :as app}]
  (let [applicant-doc (first (filter #(= (doc-subtype %) :hakija) documents))
        work-description (first (filter #(= (doc-subtype %) :hankkeen-kuvaus) documents))
        payee-doc (first (filter #(= (doc-subtype %) :maksaja) documents))
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
             :pendingOnClient              true
             :postalAddress                (application-postal-address app)
             :propertyIdentificationNumber propertyId
             :startTime                    (format-date-time start)
             :workDescription              (-> work-description :data :kayttotarkoitus)}]
    (assoc-when res :customerReference (not-empty (-> payee-doc :data :laskuviite)))))

;; Putting it all together
(sc/defn ^{:private true, :always-validate true} application->allu-placement-contract
  :- PlacementContract [app :- ValidPlacementApplication]
  (-> app flatten-values convert-value-flattened-app))

;;;; # Should you use this?

(defn- allu-organization? [dev-mode? org]
  (if dev-mode?
    (s/ends-with? org "-YA")
    (= org "091-YA")))

(def- allu-permit-subtypes #{"sijoituslupa" "sijoitussopimus"})

(defn allu-application?
  ([app] (allu-application? (env/dev-mode?) app))
  ([dev-mode? {:keys [permitSubtype organization]}]
   (and (allu-organization? dev-mode? organization)
        (contains? allu-permit-subtypes permitSubtype))))

;;;; # Effectful operations

(defprotocol ALLUPlacementContracts
  (create-contract! [self application]
    "Create placement contract in ALLU. Returns ALLU id for the contract.

    Can `sade.core/fail!` with
    * :error.allu.malformed-application - Application is malformed according to ALLU.
    * :error.allu.http :status _ :body _ - An HTTP error. Probably due to a bug or connection issues."))

(deftype RemoteALLU []
  ALLUPlacementContracts
  (create-contract! [_ app]
    (let [endpoint (str (env/value :allu :url) "/placementcontracts")
          request {:headers      {:authorization (str "Bearer " (env/value :allu :jwt))}
                   :content-type :json
                   :body         (json/encode (application->allu-placement-contract app))}
          {:keys [status body]} (http/post endpoint request)]
      ;; TODO: Propagate error descriptions from ALLU etc. when they provide documentation for those.
      (case status
        (200 201) body
        400 (fail! :error.allu.malformed-application)
        (fail! :error.allu.http :status status :body body)))))

(defstate allu-instance
  :start (->RemoteALLU))

(defn create-placement-contract! [app]
  (create-contract! allu-instance app))
