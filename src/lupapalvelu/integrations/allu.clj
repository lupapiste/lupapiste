(ns lupapalvelu.integrations.allu
  (:require [clojure.string :as s]
            [clojure.walk :refer [postwalk]]
            [mount.core :refer [defstate]]
            [schema.core :as sc]
            [schema-tools.core :refer [select-schema]]
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
            [lupapalvelu.document.canonical-common :as canonical-common]
            [lupapalvelu.integrations.allu-schemas :refer [ValidPlacementApplication PlacementContract]]))

;;; FIXME: use :schema-info :name instead of :subtype

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
        kind (str (name (canonical-common/ya-operation-type-to-schema-name-key operation)) " / "
                  (canonical-common/ya-operation-type-to-usage-description operation))]
    (or (some->> (canonical-common/ya-operation-type-to-additional-usage-description operation)
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
(sc/defn ^{:private true, :always-validate true} application->allu-placement-contract :- PlacementContract [app]
  ;; Using `select-schema` because `app` will have a lot more data than the schema covers, not ideal.
  (-> app (select-schema ValidPlacementApplication) flatten-values convert-value-flattened-app))

;; TODO: unit test this
(defn- placement-creation-request [allu-url allu-jwt app]
  [(str allu-url "/placementcontracts")
   {:headers      {:authorization (str "Bearer " allu-jwt)}
    :content-type :json
    :body         (json/encode (application->allu-placement-contract app))}])

;;;; # Should you use this?

(def- allu-organization? #{"091-YA"})

(def- allu-permit-subtype? #{"sijoituslupa" "sijoitussopimus"})

(defn allu-application? [{:keys [permitSubtype organization]}]
  (boolean (and (allu-organization? organization)
                (allu-permit-subtype? permitSubtype))))

;;;; # Effectful operations

;; TODO: unit test this
;; TODO: Propagate error descriptions from ALLU etc. when they provide documentation for those.
(defn- handle-placement-contract-response [{:keys [status body]}]
  (case status
    (200 201) body
    400 (fail! :error.allu.malformed-application :body body)
    (fail! :error.allu.http :status status :body body)))

(defprotocol ALLUPlacementContracts
  (create-contract! [self application]
    "Create placement contract in ALLU. Returns ALLU id for the contract.

    Can `sade.core/fail!` with
    * :error.allu.malformed-application - Application is malformed according to ALLU.
    * :error.allu.http :status _ :body _ - An HTTP error. Probably due to a bug or connection issues."))

(deftype ALLUService [http-post]
  ALLUPlacementContracts
  (create-contract! [_ app]
    ;; Essentially `placement-creation-request` wrapped into side effects (env reads, HTTP I/O, fail!).
    (let [[endpoint request] (placement-creation-request (env/value :allu :url) (env/value :allu :jwt) app)]
      (handle-placement-contract-response (http-post endpoint request)))))

(defstate allu-instance
  :start (->ALLUService http/post))

(defn create-placement-contract! [app]
  (create-contract! allu-instance app))
