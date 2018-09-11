(ns lupapalvelu.backing-system.allu
  "JSON REST API integration with ALLU as backing system. Used for Helsinki YA instead of SFTP/HTTP KRYSP XML
  integration."
  (:require [clojure.string :as s]
            [clojure.core.match :refer [match]]
            [clojure.walk :refer [postwalk]]
            [monger.operators :refer [$set $in]]
            [cheshire.core :as json]
            [lupapiste-jms.client :as jms-client]
            [mount.core :refer [defstate]]
            [schema.core :as sc :refer [defschema optional-key enum]]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [iso-country-codes.core :refer [country-translate]]
            [taoensso.timbre :refer [info error]]
            [taoensso.nippy :as nippy]
            [sade.util :refer [dissoc-in assoc-when fn->]]
            [sade.core :refer [def- fail! now]]
            [sade.env :as env]
            [sade.http :as http]
            [sade.shared-schemas :as sssc]
            [sade.schemas :as ssc :refer [NonBlankStr Email Zipcode Tel Hetu FinnishY FinnishOVTid Kiinteistotunnus
                                          ApplicationId ISO-3166-alpha-2 date-string]]
            [lupapalvelu.application :as application]
            [lupapalvelu.attachment :refer [get-attachment-file!]]
            [lupapalvelu.document.tools :refer [doc-name]]
            [lupapalvelu.document.canonical-common :as canonical-common]
            [lupapalvelu.i18n :refer [localize]]
            [lupapalvelu.integrations.geojson-2008-schemas :as geo]
            [lupapalvelu.integrations.jms :as jms]
            [lupapalvelu.integrations.messages :as imessages :refer [IntegrationMessage]]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.states :as states])
  (:import [java.lang AutoCloseable]
           [java.io InputStream]))

;;; TODO: Sijoituslupa

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

(defschema ^:private MiniCommand
  {:application                               {:id           ssc/ApplicationId
                                               :organization sc/Str
                                               :state        (apply sc/enum (map name states/all-states))}
   :action                                    sc/Str
   :user                                      {:id       sc/Str
                                               :username sc/Str}
   (sc/optional-key :latestAttachmentVersion) {:fileId        sssc/FileId
                                               :storageSystem sssc/StorageSystem}})

(defschema ^:private FileMetadata
  {:name        sc/Str
   :description sc/Str
   :mimeType    (sc/maybe sc/Str)})

;;;; Application conversion
;;;; ===================================================================================================================

;; TODO: Should this be injected from commands instead?
(def- lang
  "The language to use when localizing output to ALLU"
  "fi")

(def- WGS84-URN "EPSG:4326")

(def- flatten-values (partial postwalk (some-fn :value identity)))

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

(declare person->contact)

(defn- person->customer [{:keys [osoite], {:keys [hetu]} :henkilotiedot :as person}]
  (merge {:type        "PERSON"
          :registryKey hetu
          :country     (address-country osoite)}
         (person->contact person)))

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

(defn- person->contact [{:keys [henkilotiedot osoite], {:keys [puhelin email]} :yhteystiedot}]
  (assoc-when {:name (fullname henkilotiedot), :phone puhelin, :email email}
              :postalAddress (some-> osoite convert-address)))

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

;;;; Initial request construction
;;;; ===================================================================================================================

(sc/defn ^{:private true, :always-validate true} minimize-command :- MiniCommand
  ([{:keys [application action user]}]
    {:application (select-keys application (keys (:application MiniCommand)))
     :action      action
     :user        (select-keys user (keys (:user MiniCommand)))})
  ([command attachment]
    (let [mini-attachment (-> (:latestVersion attachment)
                              (select-keys (keys (get MiniCommand (sc/optional-key :latestAttachmentVersion))))
                              (update :storageSystem keyword))]
      (assoc (minimize-command command) :latestAttachmentVersion mini-attachment))))

(defn- application-cancel-request [{:keys [application] :as command}]
  (let [allu-id (get-in application [:integrationKeys :ALLU :id])]
    (assert allu-id (str (:id application) " does not contain an ALLU id"))
    {::interface-path [:applications :cancel]
     ::params         {:id allu-id}
     ::command        (minimize-command command)}))

(defn- placement-creation-request [{:keys [application] :as command}]
  {::interface-path [:placementcontracts :create]
   ::params         {:application (application->allu-placement-contract true application)}
   ::command        (minimize-command command)})

(defn- placement-update-request [pending-on-client {:keys [application] :as command}]
  (let [allu-id (-> application :integrationKeys :ALLU :id)]
    (assert allu-id (str (:id application) " does not contain an ALLU id"))
    {::interface-path [:placementcontracts :update]
     ::params         {:id          allu-id
                       :application (application->allu-placement-contract pending-on-client application)}
     ::command        (minimize-command command)}))

(defn- attachment-send [{:keys [application] :as command}
                        {{:keys [type-group type-id]} :type :keys [latestVersion] :as attachment}]
  (let [allu-id (-> application :integrationKeys :ALLU :id)]
    (assert allu-id (str (:id application) " does not contain an ALLU id"))
    {::interface-path [:attachments :create]
     ::params         {:id       allu-id
                       :metadata {:name        (:filename latestVersion)
                                  :description (let [type (localize lang :attachmentType type-group type-id)
                                                     description (:contents attachment)]
                                                 (if (or (not description) (= type description))
                                                   type
                                                   (str type ": " description)))
                                  :mimeType    (:contentType latestVersion)}
                       :file     (:fileId latestVersion)}
     ::command        (minimize-command command attachment)}))

;;;; IntegrationMessage construction
;;;; ===================================================================================================================

(sc/defn ^{:private true :always-validate true} base-integration-message :- IntegrationMessage
  [{:keys [application user action]} :- MiniCommand message-subtype direction status data]
  {:id           (mongo/create-id)
   :direction    direction
   :messageType  message-subtype
   :transferType "http"
   :partner      "allu"
   :format       "json"
   :created      (now)
   :status       status
   :application  application
   :initator     user
   :action       action
   :data         data})

;; TODO: :attachment-files and :attachmentsCount for attachment messages
(defn- request-integration-message [command http-request message-subtype]
  (base-integration-message command message-subtype "out" "processing" http-request))

(defn- response-integration-message [command endpoint http-response message-subtype]
  (base-integration-message command message-subtype "in" "done"
                            {:endpoint endpoint
                             :response (select-keys http-response [:status :body])}))

;;;; REST API description and request conversion
;;;; ===================================================================================================================

(def- allu-interface
  {:applications       {:cancel {:request-method :put
                                 :uri            "/applications/:id/cancelled"
                                 :path-params    {:id ssc/NatString}}}

   :placementcontracts {:create {:request-method :post
                                 :uri            "/placementcontracts"
                                 :body           {:name :application
                                                  ;; TODO: :schema PlacementContract
                                                  }}
                        :update {:request-method :put
                                 :uri            "/placementcontracts/:id"
                                 :path-params    {:id ssc/NatString}
                                 :body           {:name :application
                                                  ;; TODO: :schema PlacementContract
                                                  }}}

   :attachments        {:create {:request-method :post
                                 :uri            "/applications/:id/attachments"
                                 :path-params    {:id ssc/NatString}
                                 :body           [{:name      :metadata
                                                   :schema    FileMetadata
                                                   :mime-type "application/json"}
                                                  {:name      :file
                                                   :schema    (sc/cond-pre sc/Str InputStream)
                                                   :mime-type (fn-> :metadata :mimeType)}]}}})

(defn- interpolate-uri [template path-params request-data]
  (reduce (fn [^String uri [k schema]]
            (let [^String value (k request-data)
                  kstr (str k)]
              (when schema (sc/validate schema value))
              (assert (s/includes? uri kstr) (str uri " does not contain " kstr))
              (.replace uri kstr value)))
          template path-params))

(defn- params->body [body-itf params]
  (cond
    (nil? body-itf) nil
    (map? body-itf) ((:name body-itf) params)
    (vector? body-itf) (mapv (fn [{:keys [mime-type] k :name}]
                               {:name      (name k)
                                :mime-type (if (fn? mime-type) ; HACK
                                             (mime-type params)
                                             mime-type)
                                :content   (k params)})
                             body-itf)
    :else (assert false (str "Unsupported body type: " body-itf))))

(defn- body->json [body-itf body]
  (cond
    (nil? body) nil
    (map? body) (let [{:keys [schema]} body-itf]
                  (when schema (sc/validate schema body))
                  (json/encode body))
    (vector? body) (mapv (fn [{:keys [schema]} {:keys [content] :as bodypart}]
                           (when schema (sc/validate schema content))
                           (if (or (string? content)
                                   (instance? InputStream content)) ; HACK
                             bodypart
                             (-> bodypart
                                 (update :content json/encode)
                                 (assoc :encoding "UTF-8"))))
                         body-itf body)
    :else (assert false (str "Unsupported body type: " body))))

(defn- httpify-request [{interface-path ::interface-path params ::params :as request}]
  (let [interface (get-in allu-interface interface-path)]
    (-> request
        (assoc ::interface interface
               :uri (interpolate-uri (:uri interface) (:path-params interface) params)
               :request-method (:request-method interface))
        (assoc-when :body (params->body (:body interface) params)))))

(defn- jwt-authorize [request jwt]
  (assoc-in request [:headers "authorization"] (str "Bearer " jwt)))

(defn- content->json [{interface ::interface :as request}]
  (-> request
      (update :body (partial body->json (:body interface)))
      (assoc-when :content-type (when-not (vector? (:body interface)) :json))))

(defn- interface-path->string [path] (s/join \. (map name path)))

;;;; HTTP request sender for production
;;;; ===================================================================================================================

;; TODO: Use clj-http directly so that this isn't needed:
(def- http-method-fns {:post http/post, :put http/put})

(defn- perform-http-request! [base-url {:keys [request-method uri body] :as request}]
  ((request-method http-method-fns)
    (str base-url uri)
    (cond
      (nil? body) request
      (string? body) request
      (vector? body) (-> request (dissoc :body) (assoc :multipart body))
      :else (assert false (str "Unsupported body type: " body)))))

(defn- make-remote-handler [allu-url]
  (fn [request]
    (perform-http-request! allu-url request)))

;;;; Mock for interactive development
;;;; ===================================================================================================================

(defn- creation-response-ok? [allu-id]
  (mongo/any? :integration-messages {:direction            "in" ; i.e. the response
                                     :messageType          "placementcontracts.create"
                                     :status               "done"
                                     :application.id       (format "LP-%s-%s-%s"
                                                                   (subs allu-id 0 3)
                                                                   (subs allu-id 3 7)
                                                                   (subs allu-id 7 12))
                                     :data.response.status {$in [200 201]}}))

;; This approximates the ALLU state with the `imessages` data:
(defn- imessages-mock-handler [{interface-path ::interface-path params ::params}]
  (match interface-path
    [:applications :cancel] (if (creation-response-ok? (:id params))
                              {:status 200, :body ""}
                              {:status 404, :body (str "Not Found: " (:id params))})

    [:placementcontracts :create] (if-let [validation-error (sc/check PlacementContract (:application params))]
                                    {:status 400, :body validation-error}
                                    {:status 200
                                     :body   (.replace (subs (-> params :application :identificationNumber) 3)
                                                       "-" "")})

    [:placementcontracts :update] (if-let [validation-error (sc/check PlacementContract (:application params))]
                                    {:status 400, :body validation-error}
                                    (if (creation-response-ok? (:id params))
                                      {:status 200, :body (:id params)}
                                      {:status 404, :body (str "Not Found: " (:id params))}))

    [:attachments :create] (if (creation-response-ok? (:id params))
                             {:status 200, :body ""}
                             {:status 404, :body (str "Not Found: " (:id params))})))

;;;; Middleware
;;;; ===================================================================================================================

(defn- preprocessor->middleware
  "(Request -> Request) -> ((Request -> Response) -> (Request -> Response))"
  [preprocess]
  (fn [handler] (fn [request] (handler (preprocess request)))))

(defn- get-attachment-files! [{{:keys [application latestAttachmentVersion]} ::command interface-path ::interface-path
                               :as                                           request}]
  (if (= interface-path [:attachments :create])
    (if-let [file-map (get-attachment-file! (:id application) (:fileId latestAttachmentVersion)
                                            {:versions [latestAttachmentVersion]})] ; HACK
      (let [file-content ((:content file-map))]
        (-> request
            (assoc-in [::params :file] file-content)
            (assoc-in [:body 1 :content] file-content)))
      (assert false "unimplemented"))
    request))

(defn- save-messages! [handler]
  (fn [{command ::command :as request}]
    (let [endpoint (-> request :uri)
          message-subtype (interface-path->string (::interface-path request))
          {msg-id :id :as msg} (request-integration-message command
                                                            (select-keys request [:uri :request-method :body])
                                                            message-subtype)
          _ (imessages/save msg)
          response (handler request)]
      (imessages/update-message msg-id {$set {:status "done", :acknowledged (now)}})
      (imessages/save (response-integration-message command endpoint response message-subtype))
      response)))

(def allu-fail!
  "A hook for testing error cases. Calls `sade.core/fail!` by default."
  (fn [text info-map] (fail! text info-map)))

(defn- handle-response
  [handler]
  (fn [{{{app-id :id} :application} ::command interface-path ::interface-path :as msg}]
    (match (handler msg)
      {:status (:or 200 201), :body body}
      (do (info "ALLU operation" (interface-path->string interface-path) "succeeded")
          (when (= interface-path [:placementcontracts :create])
            (application/set-integration-key app-id :ALLU {:id body})))

      response (allu-fail! :error.allu.http (select-keys response [:status :body])))))

(def- combined-middleware
  (comp handle-response
        (preprocessor->middleware httpify-request)
        save-messages!
        (preprocessor->middleware (fn-> get-attachment-files! content->json (jwt-authorize (env/value :allu :jwt))))))

;;;; Request handling and JMS resources
;;;; ===================================================================================================================

(defn- make-handler
  ([] (make-handler (env/dev-mode?)))
  ([dev-mode?] (if dev-mode? imessages-mock-handler (make-remote-handler (env/value :allu :url)))))

(def allu-request-handler
  "ALLU request handler. Returns nil, calls `allu-fail!` on HTTP errors."
  (combined-middleware (make-handler)))

(def- allu-jms-queue-name "lupapalvelu.backing-system.allu")

(when (env/feature? :jms)
  ;; FIXME: HTTP timeout handling
  ;; FIXME: Error handling is very crude
  (defn- allu-jms-msg-handler [session]
    (fn [{{{app-id :id} :application {user-id :user} :user} ::command interface-path ::interface-path :as msg}]
      (logging/with-logging-context {:userId user-id :applicationId app-id}
        (try
          (allu-request-handler msg)
          (jms/commit session)

          (catch Exception exn
            (let [operation-name (interface-path->string interface-path)]
              (error operation-name "failed:" (type exn) (.getMessage exn))
              (error "Rolling back" operation-name))
            (jms/rollback session))))))

  (defstate ^AutoCloseable allu-jms-session
    "JMS session for `allu-jms-consumer`"
    :start (jms/create-transacted-session (jms/get-default-connection))
    :stop (.close allu-jms-session))

  (defstate ^AutoCloseable allu-jms-consumer
    "JMS consumer for the ALLU request JMS queue"
    :start (jms-client/listen allu-jms-session (jms/queue allu-jms-queue-name)
                              (jms/message-listener (jms/nippy-callbacker (allu-jms-msg-handler allu-jms-session))))
    :stop (.close allu-jms-consumer)))

(defn- send-allu-request! [request]
  (if (env/feature? :jms)
    (jms/produce-with-context allu-jms-queue-name (nippy/freeze request))
    (allu-request-handler request)))

;;;; Mix up pure and impure into an API
;;;; ===================================================================================================================

(defn allu-application?
  "Should ALLU integration be used?"
  [organization-id permit-type]
  (and (env/feature? :allu) (= organization-id "091-YA") (= permit-type "YA")))

(defn- create-placement-contract!
  "Create placement contract in ALLU."
  [command]
  (send-allu-request! (placement-creation-request command)))

(declare update-application!)

;; TODO: Non-placement-contract ALLU applications
(defn submit-application!
  "Submit application to ALLU and save the returned id as application.integrationKeys.ALLU.id. If this is a resubmit
  (after return-to-draft) just does `update-application!` instead."
  [{{:keys [permitSubtype] :as application} :application :as command}]
  {:pre [(or (= permitSubtype "sijoituslupa") (= permitSubtype "sijoitussopimus"))]}
  (if-not (get-in application [:integrationKeys :ALLU :id])
    (create-placement-contract! command)
    (update-application! command)))

;; TODO: Will error if user changes the application to contain invalid data, is that what we want?
(defn- update-placement-contract!
  "Update placement contract in ALLU (if it had been sent there)."
  [{:keys [application] :as command}]
  (when (application/submitted? application)
    (send-allu-request! (placement-update-request true command))))

;; TODO: Non-placement-contract ALLU applications
(defn update-application!
  "Update application in ALLU (if it had been sent there)."
  [{{:keys [permitSubtype]} :application :as command}]
  {:pre [(or (= permitSubtype "sijoituslupa") (= permitSubtype "sijoitussopimus"))]}
  (update-placement-contract! command))

(defn- lock-placement-contract!
  "Lock placement contract in ALLU for verdict evaluation."
  [command]
  (send-allu-request! (placement-update-request false command)))

;; TODO: Non-placement-contract ALLU applications
(defn lock-application!
  "Lock application in ALLU for verdict evaluation."
  [{{:keys [permitSubtype]} :application :as command}]
  {:pre [(or (= permitSubtype "sijoituslupa") (= permitSubtype "sijoitussopimus"))]}
  (lock-placement-contract! command))

(defn cancel-application!
  "Cancel application in ALLU (if it had been sent there)."
  [{:keys [application] :as command}]
  (when (application/submitted? application)
    (send-allu-request! (application-cancel-request command))))

(defn- send-attachment!
  "Send `attachment` of `application` to ALLU. Return the fileId of the file that was sent."
  [command attachment]
  (send-allu-request! (attachment-send command attachment))
  (-> attachment :latestVersion :fileId))

(defn send-attachments!
  "Send the specified `attachments` of `(:application command)` to ALLU
  Returns a seq of attachment file IDs that were sent."
  [command attachments]
  (doall (for [attachment attachments]
           (send-attachment! command attachment))))
