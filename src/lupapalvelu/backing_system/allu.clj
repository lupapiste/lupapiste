(ns lupapalvelu.backing-system.allu
  "JSON REST API integration with ALLU as backing system. Used for Helsinki YA instead of SFTP/HTTP KRYSP XML
  integration."
  (:require [clojure.core.match :refer [match]]
            [clojure.string :as s]
            [clojure.walk :refer [postwalk]]
            [monger.operators :refer [$set]]
            [cheshire.core :as json]
            [mount.core :refer [defstate]]
            [schema.core :as sc :refer [defschema optional-key enum]]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [iso-country-codes.core :refer [country-translate]]
            [taoensso.timbre :refer [info]]
            [sade.util :refer [assoc-when]]
            [sade.core :refer [def- fail! now]]
            [sade.env :as env]
            [sade.http :as http]
            [sade.schemas :refer [NonBlankStr Email Zipcode Tel Hetu FinnishY FinnishOVTid Kiinteistotunnus
                                  ApplicationId ISO-3166-alpha-2 date-string]]
            [lupapalvelu.attachment :refer [get-attachment-file!]]
            [lupapalvelu.i18n :refer [localize]]
            [lupapalvelu.document.tools :refer [doc-name]]
            [lupapalvelu.document.canonical-common :as canonical-common]
            [lupapalvelu.integrations.geojson-2008-schemas :as geo]
            [lupapalvelu.integrations.messages :as imessages]
            [lupapalvelu.mongo :as mongo])
  (:import [java.io InputStream]))

;;;; Schemas
;;;; ===================================================================================================================

(defschema SijoituslupaOperation
  "An application primaryOperation.name that is classified as a :Sijoituslupa."
  (->> canonical-common/ya-operation-type-to-schema-name-key
       (filter (comp #(= % :Sijoituslupa) val))
       (map (comp name key))
       (apply sc/enum)))

(defschema RegistryKey
  "Henkil\u00f6- tai Y-tunnus"
  (sc/cond-pre Hetu FinnishY))

;; TODO: Improve this based on the standard.
(defschema JHS-106
  "Katuosoite.

  apartmentNumber: huoneiston numero
  divisionLetter: jakokirjainosa (soluasunnot tms.)
  entranceLetter: kirjainosa (yleens\u00e4 porras)
  premiseNumber: osoitenumero
  streetName: tien/kadun nimi

  Lupapisteess\u00e4 katuosoite on pelkk\u00e4 merkkijono. ALLUssa se voidaan vain laittaa suoraan
  streetName-kentt\u00e4\u00e4n ja muut kent\u00e4t j\u00e4tt\u00e4\u00e4 pois."
  {(optional-key :apartmentNumber) NonBlankStr
   (optional-key :divisionLetter)  NonBlankStr
   (optional-key :entranceLetter)  NonBlankStr
   (optional-key :premiseNumber)   NonBlankStr
   (optional-key :streetName)      NonBlankStr})

(defschema PostalAddress
  "Postiosoite.

  city: postitoimipaikan nimi
  postalCode: postinumero
  streetAddress: katuosoite"
  {(optional-key :city)          NonBlankStr
   (optional-key :postalCode)    Zipcode
   (optional-key :streetAddress) JHS-106})

(defschema Contact
  "Yhteystieto.

  email: s\u00e4hk\u00f6postiosoite
  id: Allun sis\u00e4inen id, voi j\u00e4tt\u00e4\u00e4 huomiotta, tulee poistumaan
  name: henkil\u00f6n tai yrityksen nimi
  phone: puhelinnumero
  postalAddress: postiosoite"
  {(optional-key :email)         Email
   (optional-key :id)            sc/Int
   :name                         NonBlankStr
   (optional-key :phone)         Tel
   (optional-key :postalAddress) PostalAddress})

(defschema CustomerType
  "Hakijan/maksajan tyyppi (henkil\u00f6/yritys/yhdistys/muu)."
  (enum "PERSON" "COMPANY" "ASSOCIATION" "OTHER"))

(defschema Customer
  "Hakijan/maksajan yleiset tiedot.

  country: kotimaa
  email: s\u00e4hk\u00f6postiosoite
  id: Allun sis\u00e4inen id, voi j\u00e4tt\u00e4\u00e4 huomiotta, tulee poistumaan
  invoicingOperator: laskutusoperaattori (pankkitunnus (OKOYFIHH, NDEAFIHH ...) tms.)
  name: henkil\u00f6n tai yrityksen nimi
  ovt: (yrityksen) OVT-tunnus
  phone: puhelinnumero
  postalAddress: postiosoite
  registryKey: henkil\u00f6- tai Y-tunnus
  type: Onko kyseess\u00e4 henkil\u00f6, yritys, yhdistys vai jokin muu."
  {:country                          ISO-3166-alpha-2
   (optional-key :email)             Email
   (optional-key :id)                sc/Int
   (optional-key :invoicingOperator) NonBlankStr            ; TODO: Is there some format for this?
   :name                             NonBlankStr
   (optional-key :ovt)               FinnishOVTid
   (optional-key :phone)             Tel
   (optional-key :postalAddress)     PostalAddress
   (optional-key :registryKey)       RegistryKey
   :type                             CustomerType})

(defschema FeaturelessGeoJSON-2008
  "A GeoJSON object that is not a Feature or a FeatureCollection."
  (sc/conditional
    geo/point? (geo/with-crs geo/Point)
    geo/multi-point? (geo/with-crs geo/MultiPoint)
    geo/line-string? (geo/with-crs geo/LineString)
    geo/multi-line-string? (geo/with-crs geo/MultiLineString)
    geo/polygon? (geo/with-crs geo/Polygon)
    geo/multi-polygon? (geo/with-crs geo/MultiPolygon)
    geo/geometry-collection? (geo/with-crs geo/GeometryCollection)
    'FeaturelessGeoJSONObject))

(defschema PlacementContract
  "Sijoitushakemuksen/sopimuksen tiedot.

  clientApplicationKind: Hakemuksen laji
  customerReference: viitenumero laskuun
  customerWithContacts: hakijan yleiset ja yhteystiedot
  endTime: alueen k\u00e4yt\u00f6n loppuaika (ei k\u00e4yt\u00f6ss\u00e4 Lupapisteess\u00e4)
  geometry: k\u00e4ytett\u00e4v\u00e4n alueen geometriat
  identificationNumber: (Lupapisteen) hakemustunnus
  invoicingCustomer: maksajan tiedot
  name: hakemuksen nimi / lyhyt kuvaus
  pendingOnClient: tehd\u00e4\u00e4nk\u00f6 Lupapisteess\u00e4 viel\u00e4 muutoksia
  postalAddress: varattavan alueen postiosoite
  propertyIdentificationNumber: varattavan alueen kiinteist\u00f6tunnus
  startTime: alueen k\u00e4yt\u00f6n alkuaika (ei k\u00e4yt\u00f6ss\u00e4 Lupapisteess\u00e4)
  workDescription: hankkeen (pidempi) kuvaus"
  {:clientApplicationKind                       NonBlankStr
   (optional-key :customerReference)            NonBlankStr
   :customerWithContacts                        {:contacts [(sc/one Contact "primary contact") Contact]
                                                 :customer Customer}
   :endTime                                     (date-string :date-time-no-ms)
   :geometry                                    FeaturelessGeoJSON-2008
   :identificationNumber                        ApplicationId
   (optional-key :invoicingCustomer)            Customer
   :name                                        NonBlankStr
   (optional-key :pendingOnClient)              sc/Bool
   (optional-key :postalAddress)                PostalAddress
   (optional-key :propertyIdentificationNumber) Kiinteistotunnus
   :startTime                                   (date-string :date-time-no-ms)
   (optional-key :workDescription)              NonBlankStr})

;;; TODO: Use these (?):

(defschema ALLUAuthHeaders
  {:authorization (sc/pred #(s/starts-with? % "Bearer ") 'JWTBearer)}) ; TODO: Also match JWT

(defschema ALLUPlacementContractRequest
  {:headers      ALLUAuthHeaders
   :content-type (sc/eq :json)
   :form-params  PlacementContract})

(defschema AttachmentRequest
  {:headers   ALLUAuthHeaders
   :multipart [{:name      (sc/eq "metadata")
                :mime-type (sc/eq "application/json")
                :encoding  (sc/eq "UTF-8")
                :content   sc/Str}                          ; TODO: Improve this (without using json/decode!)
               {:name      (sc/eq "file")
                :mime-type sc/Str
                :content   InputStream}]})

;;;; Constants
;;;; ===================================================================================================================

;; TODO: Should this be injected from commands instead?
(def- lang
  "The language to use when localizing output to ALLU"
  "fi")

(def- WGS84-URN "EPSG:4326")

;;;; Cleaning up :value indirections
;;;; ===================================================================================================================

(def- flatten-values (partial postwalk (some-fn :value identity)))

;;;; Application conversion
;;;; ===================================================================================================================

(defn- application-kind [app]
  (let [operation (-> app :primaryOperation :name keyword)
        kind (str (name (canonical-common/ya-operation-type-to-schema-name-key operation)) " / "
                  (canonical-common/ya-operation-type-to-usage-description operation))]
    (if-let [usage-description (canonical-common/ya-operation-type-to-additional-usage-description operation)]
      (str kind " / " usage-description)
      kind)))

(defn- fullname [{:keys [etunimi sukunimi]}]
  (str etunimi " " sukunimi))

(defn- address-country [address]
  (country-translate :alpha-3 :alpha-2 (:maa address)))

(defn- convert-address [{:keys [postitoimipaikannimi postinumero katu]}]
  {:city          postitoimipaikannimi
   :postalCode    postinumero
   :streetAddress {:streetName katu}})

(defn- person->customer [{:keys [osoite], {:keys [hetu] :as person} :henkilotiedot}]
  {:type          "PERSON"
   :registryKey   hetu
   :name          (fullname person)
   :country       (address-country osoite)
   :postalAddress (convert-address osoite)})

(defn- company->customer [payee? company]
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

(defn- doc->customer [payee? doc]
  (match (:data doc)
    {:_selected "henkilo", :henkilo person} (person->customer person)
    {:_selected "yritys", :yritys company} (company->customer payee? company)))

(defn- person->contact [{:keys [henkilotiedot], {:keys [puhelin email]} :yhteystiedot}]
  {:name (fullname henkilotiedot), :phone puhelin, :email email})

(defn- customer-contact [customer-doc]
  (match (:data customer-doc)
    {:_selected "henkilo", :henkilo person} person
    {:_selected "yritys", :yritys {:yhteyshenkilo contact}} contact))

(defn- convert-applicant [applicant-doc]
  {:customer (doc->customer false applicant-doc)
   :contacts [(person->contact (customer-contact applicant-doc))]})

(defn- convert-payee [payee-doc]
  (merge (doc->customer true payee-doc)
         (select-keys (person->contact (customer-contact payee-doc)) [:email :phone])))

(defn- application-geometry [{:keys [drawings location-wgs84]}]
  (assoc (if (seq drawings)
           {:type       "GeometryCollection"
            :geometries (mapv :geometry-wgs84 drawings)}
           {:type        "Point"
            :coordinates location-wgs84})
    :crs {:type "name", :properties {:name WGS84-URN}}))

(defn- application-postal-address [{:keys [municipality address]}]
  ;; We don't have the postal code within easy reach so it is omitted here.
  {:city          (localize lang :municipality municipality)
   :streetAddress {:streetName address}})

(def- format-date-time (partial tf/unparse (tf/formatters :date-time-no-ms)))

(defn- convert-value-flattened-app [pending-on-client {:keys [id propertyId documents] :as app}]
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
    (assoc-when res :customerReference (not-empty (-> payee-doc :data :laskuviite)))))

(sc/defn ^{:private true} application->allu-placement-contract :- PlacementContract [pending-on-client app]
  (->> app flatten-values (convert-value-flattened-app pending-on-client)))

;;;; Request construction
;;;; ===================================================================================================================

(defn- application-cancel-request [allu-url allu-jwt app]
  (let [allu-id (-> app :integrationKeys :ALLU :id)]
    (assert allu-id (str (:id app) " does not contain an ALLU id"))
    [(str allu-url "/applications/" allu-id "/cancelled")
     {:headers {:authorization (str "Bearer " allu-jwt)}}]))

(defn- attachment-send [allu-url allu-jwt app
                        {{:keys [type-group type-id]} :type :keys [latestVersion] :as attachment} file-contents]
  {:pre [file-contents]}
  (let [allu-id (-> app :integrationKeys :ALLU :id)]
    (assert allu-id (str (:id app) " does not contain an ALLU id"))
    [(str allu-url "/applications/" allu-id "/attachments")
     {:headers   {:authorization (str "Bearer " allu-jwt)}
      :multipart [{:name      "metadata"
                   :content   {:name        (:contents attachment)
                               :description (localize lang :attachmentType type-group type-id)
                               :mimeType    (:contentType latestVersion)}
                   :mime-type "application/json"
                   :encoding  "UTF-8"}
                  {:name      "file"
                   :content   file-contents
                   :mime-type (:contentType latestVersion)}]}]))

(defn- placement-creation-request [allu-url allu-jwt app]
  [(str allu-url "/placementcontracts")
   {:headers      {:authorization (str "Bearer " allu-jwt)}
    :content-type :json
    :form-params  (application->allu-placement-contract true app)}])

(defn- placement-update-request [pending-on-client allu-url allu-jwt app]
  (let [allu-id (-> app :integrationKeys :ALLU :id)]
    (assert allu-id (str (:id app) " does not contain an ALLU id"))
    [(str allu-url "/placementcontracts/" allu-id)
     {:headers      {:authorization (str "Bearer " allu-jwt)}
      :content-type :json
      :form-params  (application->allu-placement-contract pending-on-client app)}]))

;;;; Should you use this?
;;;; ===================================================================================================================

(defn allu-application? [organization permit-type]
  (and (env/feature? :allu) (= (:id organization) "091-YA") (= permit-type "YA")))

;;;; ALLU Proxy
;;;; ===================================================================================================================

(defprotocol ALLUApplications
  (cancel-allu-application! [self command endpoint request]))

(defprotocol ALLUAttachments
  (send-allu-attachment! [self command endpoint request]))

(defprotocol ALLUPlacementContracts
  (create-contract! [self command endpoint request])
  (update-contract! [self command endpoint request]))

(deftype RemoteALLU []
  ALLUApplications
  (cancel-allu-application! [_ _ endpoint request] (http/put endpoint request))

  ALLUAttachments
  (send-allu-attachment! [_ _ endpoint request]
    (->> (update-in request [:multipart 0 :content] json/encode)
         (http/post endpoint request)))

  ALLUPlacementContracts
  (create-contract! [_ _ endpoint request] (http/post endpoint request))
  (update-contract! [_ _ endpoint request] (http/put endpoint request)))

(defn- creation-message-sent? [allu-id]
  (mongo/any? :integration-messages {:messageType "ALLU placementcontracts/create"
                                     :status "done"
                                     :application.id (str "LP-" allu-id)}))

;; This approximates the ALLU state with the `imessages` data:
(deftype IntegrationMessagesMockALLU []
  ALLUApplications
  (cancel-allu-application! [_ _ endpoint _]
    (let [allu-id (second (re-find #".*/([\d\-]+)/cancelled" endpoint))]
      (if (creation-message-sent? allu-id)
        {:status 200, :body ""}
        {:status 404, :body (str "Not Found: " allu-id)})))

  ALLUAttachments
  (send-allu-attachment! [_ _ endpoint _]
    (let [allu-id (second (re-find #".*/applications/([\d\-]+)/attachments" endpoint))]
      (if (creation-message-sent? allu-id)
        {:status 200, :body ""}
        {:status 404, :body (str "Not Found: " allu-id)})))

  ALLUPlacementContracts
  (create-contract! [_ _ _ {{:keys [identificationNumber] :as placement-contract} :form-params}]
    (if-let [validation-error (sc/check PlacementContract placement-contract)]
      {:status 400, :body validation-error}
      {:status 200, :body (subs identificationNumber 3)}))

  (update-contract! [_ _ endpoint {placement-contract :form-params}]
    (let [allu-id (second (re-find #".*/([\d\-]+)" endpoint))]
      (if-let [validation-error (sc/check PlacementContract placement-contract)]
        {:status 400, :body validation-error}
        (if (creation-message-sent? allu-id)
          {:status 200, :body allu-id}
          {:status 404, :body (str "Not Found: " allu-id)})))))

(defn- integration-message [{:keys [application user action]} endpoint request message-subtype payload-key]
  {:id           (mongo/create-id)
   :direction    "out"
   :messageType  (str "ALLU " message-subtype)
   :transferType "http"
   :partner      "allu"
   :format       "json"
   :created      (now)
   :status       "processing"
   :application  (select-keys application [:id :organization :state])
   :initator     (select-keys user [:id :username])
   :action       action
   :data         {:endpoint endpoint
                  :request  (if (= payload-key :multipart)  ; HACK
                              {:multipart [(get-in request [:multipart 0])]}
                              (select-keys request [payload-key]))}})

(defn- with-integration-message [command endpoint request message-subtype payload-key body]
  (let [{msg-id :id :as msg} (integration-message command endpoint request message-subtype payload-key)
        _ (imessages/save msg)
        res (body)]
    (imessages/update-message msg-id {$set {:status "done", :acknowledged (now)}})
    res))

(deftype MessageSavingALLU [inner]
  ALLUApplications
  (cancel-allu-application! [_ command endpoint request]
    (with-integration-message command endpoint request "applications/cancelled" :form-params
                              #(cancel-allu-application! inner command endpoint request)))

  ALLUAttachments
  (send-allu-attachment! [_ command endpoint request]
    (with-integration-message command endpoint request "attachments/create" :multipart
                              #(send-allu-attachment! inner command endpoint request)))

  ALLUPlacementContracts
  (create-contract! [_ command endpoint request]
    (with-integration-message command endpoint request "placementcontracts/create" :form-params
                              #(create-contract! inner command endpoint request)))
  (update-contract! [_ command endpoint request]
    (with-integration-message command endpoint request "placementcontracts/update" :form-params
                              #(update-contract! inner command endpoint request))))

(def ^:dynamic allu-fail! (fn [text info-map] (fail! text info-map)))

(defstate allu-instance
  :start (->MessageSavingALLU (if (env/dev-mode?) (->IntegrationMessagesMockALLU) (->RemoteALLU))))

(defn- allu-http-fail! [response]
  (allu-fail! :error.allu.http (select-keys response [:status :body])))

(defn- create-placement-contract!
  "Create placement contract in ALLU. Returns ALLU id for the contract."
  [{:keys [application] :as command}]
  (let [[endpoint request] (placement-creation-request (env/value :allu :url) (env/value :allu :jwt) application)]
    (match (create-contract! allu-instance command endpoint request)
      {:status (:or 200 201), :body allu-id} (do (info (:id application) "was created in ALLU as" allu-id)
                                                 allu-id)
      response (allu-http-fail! response))))

;; TODO: Will error if user changes the application to contain invalid data, is that what we want?
(defn- update-placement-contract!
  "Update application in ALLU (if it had been sent there)."
  [{:keys [application] :as command}]
  (when-let [allu-id (-> application :integrationKeys :ALLU :id)]
    (let [[endpoint request] (placement-update-request true (env/value :allu :url) (env/value :allu :jwt) application)]
      (match (update-contract! allu-instance command endpoint request)
        {:status (:or 200 201), :body allu-id} (info (:id application) "was updated in ALLU as" allu-id)
        response (allu-http-fail! response)))))

(defn- send-attachment!
  "Send `attachment` of `application to ALLU. Return the fileId of the file that was sent."
  [{:keys [application] :as command} {attachment-id :id {:keys [fileId]} :latestVersion :as attachment}]
  (let [file-contents (when-let [file-map (get-attachment-file! application fileId)]
                        ((:content file-map)))
        [endpoint request] (attachment-send (env/value :allu :url) (env/value :allu :jwt) application attachment file-contents)]
    (match (send-allu-attachment! allu-instance command endpoint request)
      {:status (:or 200 201)} (do (info "attachment" attachment-id "of" (:id application) "was sent to ALLU")
                                  fileId)
      response (allu-http-fail! response))))

;;;; Public API
;;;; ===================================================================================================================

;;; TODO: DRY these up:

(defn cancel-application!
  "Cancel application in ALLU (if it had been sent there)."
  [{:keys [application] :as command}]
  (when-let [allu-id (-> application :integrationKeys :ALLU :id)]
    (let [[endpoint request] (application-cancel-request (env/value :allu :url) (env/value :allu :jwt) application)]
      (match (cancel-allu-application! allu-instance command endpoint request)
        {:status (:or 200 201)} (info (:id application) "was canceled in ALLU as" allu-id)
        response (allu-http-fail! response)))))

(defn send-attachments!
  "Send the specified `attachments` of `(:application command)` to ALLU
  Returns a seq of attachment file IDs that were sent."
  [command attachments]
  (doall (for [attachment attachments]
           (send-attachment! command attachment))))

(defn submit-application!
  "Submit application to ALLU. Returns the value that should be saved to application.integrationKeys.ALLU."
  [command]
  ;; TODO: Use message queue to delay and retry interaction with ALLU.
  ;; TODO: Save messages for inter-system debugging etc.
  ;; TODO: Send errors to authority instead of applicant?
  ;; TODO: Non-placement-contract ALLU applications
  {:id (create-placement-contract! command)})

;; TODO: Non-placement-contract ALLU applications
(def update-application!
  "Update application in ALLU (if it had been sent there)."
  update-placement-contract!)

(defn lock-placement-contract!
  "Lock placement contract in ALLU for verdict evaluation."
  [{:keys [application] :as command}]
  (let [[endpoint request] (placement-update-request false (env/value :allu :url) (env/value :allu :jwt) application)]
    (match (update-contract! allu-instance command endpoint request)
      {:status (:or 200 201), :body allu-id} (info (:id application) "was locked in ALLU as" allu-id)
      response (allu-http-fail! response))))
