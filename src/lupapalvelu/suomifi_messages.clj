(ns lupapalvelu.suomifi-messages
  (:require [clj-time.coerce :refer [to-long]]
            [clj-time.local :refer [local-now]]
            [clj-uuid :as uuid]
            [cljstache.core :as clostache]
            [clojure.edn :as edn]
            [clojure.string :as s]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.ident.xml-signer :as xml-signer]
            [lupapalvelu.integrations.messages :as messages]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.pdf :as pdf]
            [lupapalvelu.pate.verdict-interface :as vif]
            [lupapalvelu.pdf.html-template :as html-pdf]
            [lupapalvelu.printing-order.mylly-client :refer [encode-file-from-stream]]
            [monger.operators :refer [$set]]
            [sade.core :refer [fail]]
            [sade.env :as env]
            [sade.http :as http]
            [sade.schemas :as ssc]
            [sade.util :as util]
            [sade.xml :as sxml]
            [schema.core :as sc]
            [taoensso.timbre :refer [error errorf infof info debugf debug]])
  (:import (java.io InputStream)))


(defn suomifi-enabled
  "Pre-check for whether Suomi.fi-messages service is enabled for the organization"
  [{:keys [organization]}]
  (when organization
    (when-not (get-in @organization [:suomifi-messages :verdict :enabled])
      (fail :error.suomifi-messages.not-enabled))))

;; The structure for elements of a request to Suomi.fi-messages API
;; are defined here as Schema records and their constructor functions with validation
;; enabled.

(sc/defrecord Viranomainen
  [viranomaisTunnus   :- sc/Str ;; The 'authority-id' found from the organization's details
   palveluTunnus      :- sc/Str ;; The 'service-id' found from the organization's details
   kayttajaTunnus     :- ssc/Email ;; Email of the authority sending the message
   sanomaTunniste     :- sc/Uuid
   sanomaVersio       :- sc/Str
   sanomaVarmenneNimi :- sc/Str])

(sc/defrecord Asiakas ;; Päätöksen toimitus-kohdassa määritelty vastaanottaja
  [asiakasTunnus      :- ssc/Hetu
   nimi               :- sc/Str
   osoite             :- sc/Str
   kaupunki           :- sc/Str
   postiNumero        :- ssc/Zipcode
   tunnusTyyppi       :- sc/Str
   maa                :- ssc/ISO-3166-alpha-2])

(sc/defrecord HaeAsiakkaitaAsiakas ;; Person who will be queried for if he/she can receive suomi.fi-messages
  [asiakasTunnus      :- ssc/Hetu
   tunnusTyyppi       :- sc/Str])

(sc/defrecord Tiedosto
  [tiedostonKuvaus    :- sc/Str
   tiedostoSisalto    :- sc/Str
   tiedostoMuoto      :- sc/Str
   tiedostoNimi       :- sc/Str])

(sc/defrecord Kohde
  [asiakas            :- Asiakas
   viranomaisTunniste :- sc/Str ;; "Asian yksilöivä tieto viranomaisen järjestelmässä" - here the format is "appid|verdict-id"
   nimeke             :- sc/Str
   lahetysPvm         :- sc/Str
   kuvausTeksti       :- sc/Str
   tiedosto           :- Tiedosto])

(defn get-attachment-by-type [application type-id]
  (->> application
       :attachments
       (util/find-first #(= type-id (get-in % [:type :type-id])))
       :latestVersion))

(sc/defn ^:always-validate attachment->tiedosto :- (sc/maybe Tiedosto)
  [application
   type-id            :- sc/Str
   title              :- sc/Str]
  (let [{:keys [contentType filename fileId]} (get-attachment-by-type application type-id)]
    (when-not (nil? fileId)
      (try
        (with-open [^InputStream content-is ((:content (att/get-attachment-file! application fileId)))]
          (->Tiedosto title
                      (encode-file-from-stream content-is)
                      contentType
                      filename))
        (catch Exception e
          (error e)
          (errorf "Attachment %s not found for application %s" fileId (:id application)))))))

(sc/defn ^:always-validate ->tiedosto :- (sc/maybe Tiedosto)
  [application
   verdict-id :- ssc/ObjectIdStr]
  (let [verdict       (vif/find-verdict application verdict-id)
        description   "Hakemukseen liittyvä päätösdokumentti"
        pate-verdict? (some-> verdict :published :tags)]
    ;; PATE- or PATE legacy -verdict
    (if pate-verdict?
      (let [attachment-id (some-> verdict :published :attachment-id)
            att-content   (some->> (att/get-attachment-info application attachment-id)
                                   :latestVersion
                                   :fileId
                                   (att/get-attachment-file! application)
                                   :content)]
        (with-open [^InputStream content-is (or (when att-content
                                                  (att-content))
                                                ;; Fallback to re-rendering HTML->PDF
                                                (some->> verdict
                                                         :published
                                                         :tags
                                                         edn/read-string
                                                         pdf/verdict-tags-html
                                                         html-pdf/html->pdf
                                                         :pdf-file-stream))]
          (->Tiedosto description
                      (encode-file-from-stream content-is) ;; tiedostosisältö
                      "application/pdf"
                      "Paatos.pdf")))
      (attachment->tiedosto application "paatos" "Päätös"))))

(sc/defn ^:always-validate ->asiakas :- Asiakas
  [{:keys [person-id first-name last-name address zipcode country city]}]
  (->Asiakas person-id (str first-name " " last-name) address city zipcode "SSN" country))

(sc/defn ^:always-validate ->haeAsiakkaitaAsiakas :- HaeAsiakkaitaAsiakas
  [person-id]
  (->HaeAsiakkaitaAsiakas person-id "SSN"))

(sc/defschema SoapResponse
  {:SanomaTunniste    ssc/NonBlankStr
   :TilaKoodi         (sc/enum "202" "400" "403" "404" "405" "406" "453" "525" "550")
   :TilaKoodiKuvaus   ssc/NonBlankStr})

(sc/defschema SuomifiConfig
  (merge org/SuomifiSettings
         {:cert-common-name             sc/Str
          :dummy                        sc/Bool
          :url                          ssc/HttpUrl
          :sign                         sc/Bool
          (sc/optional-key :cert)       sc/Str
          (sc/optional-key :privatekey) sc/Str
          (sc/optional-key :debug)      sc/Bool}))

(sc/defrecord VerdictMessage
  [Viranomainen       :- Viranomainen
   Asiakas            :- Asiakas
   Tiedostot          :- [Tiedosto]
   viranomaisTunniste :- sc/Str
   nimeke             :- sc/Str
   lahetysPvm         :- sc/Str
   kuvausTeksti       :- sc/Str])

(sc/defrecord HaeAsiakkaitaMessage
  [Viranomainen         :- Viranomainen
   HaeAsiakkaitaAsiakas :- HaeAsiakkaitaAsiakas])

(sc/defn ^:always-validate ->viranomainen :- Viranomainen
  [org-id             :- sc/Str
   email              :- ssc/Email
   sanomaTunniste     :- sc/Uuid
   suomifi-settings   :- SuomifiConfig]
  (let [{:keys [authority-id service-id]} suomifi-settings]
    (->Viranomainen authority-id service-id email sanomaTunniste "1.0" (:cert-common-name suomifi-settings))))

(sc/defn ^:always-validate build-verdict-message :- (sc/maybe VerdictMessage)
  "Build the verdict message. Returns the message record (or nil, if it fails)"
  [{:keys [id organization] :as application} verdict-id user recipient suomifi-settings]
  (try
    (let [authority (->viranomainen organization (:email user) (uuid/v1) suomifi-settings)
          recipient (->asiakas recipient)
          viranomaisTunniste (str id "|" verdict-id)
          nimeke (format "Rakennusasiaan %s liittyvä päätös" id)
          description (get-in suomifi-settings [:verdict :message])
          verdict [(->tiedosto application verdict-id)]
          attachment-settings (get-in suomifi-settings [:verdict :attachments])
          attachments (->> attachment-settings
                           (map #(attachment->tiedosto application (:type-id %) (:title %)))
                           (filterv record?))]
      (->VerdictMessage authority recipient (into verdict attachments) viranomaisTunniste nimeke (str (local-now)) description))
    (catch Exception e
      (do
        (error e)
        (errorf "Failed to build Suomi.fi-verdict message - organization: %s, app: %s, verdict: %s"
                organization id verdict-id)))))

(sc/defn build-hae-asiakkaita-message :- HaeAsiakkaitaMessage
  "Build a record that can be used to create a HaeAsiakkaita XML message which in turn will be sent as a query to suomi.fi"
  [person-id           :- ssc/Hetu
   org-id              :- sc/Str
   authority-username  :- ssc/Email
   msg-id              :- sc/Uuid
   suomifi-settings]
  (let [authority (->viranomainen org-id authority-username msg-id suomifi-settings)
        person (->haeAsiakkaitaAsiakas person-id)]
    (->HaeAsiakkaitaMessage authority person)))

(sc/defn ^:always-validate parse-response  :- SoapResponse
  "Parses the response received from VIA. Parsed response contains the keys :SanomaTunniste,
  :TilaKoodi and :TilaKoodiKuvaus."
  [resp]
  (-> resp
      :body
      (sxml/parse-string "utf-8")
      sxml/xml->edn
      (get-in [:soap:Envelope :soap:Body :LahetaViestiResponse :LahetaViestiResult :TilaKoodi])))

(defn get-attachment-ids
  "Takes an application and the attachment selections from organization's Suomi.fi-message settings,
  returns the file id's of the files that should be attached to the outgoing message."
  [application attachment-selections]
  (let [attachment-ids (->> attachment-selections
                            (mapv (comp :fileId #(get-attachment-by-type application (:type-id %))))
                            (remove nil?))
        file-id (:fileId (get-attachment-by-type application "paatos"))]
    (if-not (nil? file-id)
      (conj attachment-ids file-id)
      attachment-ids)))

(sc/defn ^:always-validate build-integration-message :- messages/IntegrationMessage
  [internal-id user created-ts {:keys [id organization state] :as application} {:keys [Tiedostot]} verdict-id attachment-selections]
  (let [attachment-ids (get-attachment-ids application attachment-selections)
        message {:id                 internal-id
                 :direction          "out"
                 :messageType        "suomifi-messages-verdict"
                 :transferType       "http"
                 :partner            "suomifi-messages"
                 :format             "xml"
                 :status             "processing"
                 :created            created-ts
                 :action             "send-suomifi-verdict"
                 :application        {:id id :organization organization :state state}
                 :initiator           (select-keys user [:id :username])
                 :attachmentsCount   (count Tiedostot)}]
    (cond-> message
      (seq attachment-ids) (assoc :attached-files attachment-ids))))

(defn- save-debug-data
  "When testing the message sending in QA, save the complete generated message and received response to DB."
  [id message message-string response]
  (mongo/insert :suomifi-messages-debug {:id id
                                         :message message
                                         :message-string message-string
                                         :response response}))

(defn- update-debug-data [id res]
  (mongo/update-by-id :suomifi-messages-debug id {$set {:response res}}))

(defn message-too-large?
  "The maximum size for a Suomifi-message is 3 mb."
  [message-string]
  (let [size-in-bytes (count (.getBytes message-string "UTF-8"))
        size-in-mb (/ size-in-bytes (* 1024 1024))]
    (> size-in-mb 3.0)))

(defn- mark-acknowledged [internal-id message-id]
  (infof "mark-acknowledged %s %s" internal-id message-id)
  (messages/mark-acknowledged-and-return internal-id (to-long (local-now))))

(sc/defn ^:always-validate HaeAsiakkaitaMessage->string :- sc/Str
  [message :- HaeAsiakkaitaMessage]
  (clostache/render-resource "suomifi-messages/HaeAsiakkaitaMessage.xml" message))

(defn- trim-extra-whitespace [xml]
  (-> xml
      s/trim
      (s/replace #"\n\s+" "")
      (s/replace #">\n<" "><")))

(defn send-suomifi-message!
  [message application user verdict-id suomifi-settings]
  (if (:dummy suomifi-settings)
    {:ok true}
    (let [{:keys [cert privatekey sign]} suomifi-settings
          message-string (cond->> (-> (clostache/render-resource "suomifi-messages/VerdictMessage.xml" message)
                                      trim-extra-whitespace)
                           sign (xml-signer/sign-soap-envelope-xml-string cert privatekey))]
      (if (message-too-large? message-string)
        {:ok false
         :message :error.suomifi-messages.message-too-large}
        (let [internal-id (mongo/create-id)
              message-id (get-in message [:Viranomainen :sanomaTunniste])
              integration-message (build-integration-message internal-id user (to-long (:lahetysPvm message))
                                                             application message verdict-id
                                                             (get-in suomifi-settings [:verdicts :attachments]))]
          (when (:debug suomifi-settings)
            (save-debug-data internal-id message message-string nil))
          (messages/save integration-message)
          (try
            (let [{:keys [status] :as res} (http/post (:url suomifi-settings) {:content-type "text/xml;charset=UTF-8"
                                                                               :throw-exceptions false
                                                                               :body message-string})
                  _ (when (:debug suomifi-settings)
                      (infof "RESPONSE: %s" res)
                      (update-debug-data internal-id res))]
              (if (= 404 status)
                {:ok false :message :error.suomifi-messages.server-not-responding}
                (let [{:keys [TilaKoodi TilaKoodiKuvaus]} (parse-response res)]
                  (if (= "202" TilaKoodi)
                    (do
                      (mark-acknowledged internal-id message-id)
                      (infof "Suomifi-verdict sent successfully, id: %s, message-id: %s"
                             internal-id message-id)
                      {:ok true})
                    (do
                      (errorf "SENDING SUOMI.FI-VERDICT FAILED: %s, ERROR: %s, ID: %s,
                              INTEGRATION MESSAGE: %s" TilaKoodiKuvaus TilaKoodi message-id internal-id)
                      {:ok false :message (if (#{\4 \5} (first TilaKoodi))
                                            (keyword (str "error.suomifi-messages.code-" TilaKoodi))
                                            :error.suomifi-messages.unknown-error)})))))
            (catch Exception e
              (errorf "Sending of Suomi.fi-message failed, integration message: %s, exception: %s" internal-id (.getMessage e))
              {:ok false :message :error.suomifi-messages.unknown-error})))))))

(defn- send-hae-asiakkaita-request
  "Requests suomi.fi personal information"
  [xml {:keys [url cert privatekey sign]}]
  (http/post url {:content-type     "text/xml;charset=UTF-8"
                  :throw-exceptions false
                  :body             (cond->> (trim-extra-whitespace xml)
                                      sign (xml-signer/sign-soap-envelope-xml-string cert privatekey))}))

(defn- parse-xml
  "Parses the response received from VIA"
  [resp-xml]
  (-> resp-xml
      (sxml/parse-string "utf-8")
      sxml/xml->edn))

(defn allows-suomifi-messages?
  "Interpretes suomi.fi HaeAsiakkaita xml response and returns true if the person
   can receive electronic messages via suomi.fi.
   HaeAsiakkaita interface could be used to check data of multiple people
   but we are using for a single person here"
  [response-xml]
  (let [response-edn (parse-xml response-xml)
        path-to-result [:soapenv:Envelope :soapenv:Body :asi:HaeAsiakkaitaResponse :asi:HaeAsiakkaitaResult]
        asiakas (get-in response-edn (concat path-to-result [:asi:Asiakkaat :asi:Asiakas]))
        state-value (:asi:Tila asiakas)
        account-disabled-value (:asi:TiliPassivoitu asiakas)
        person-can-receive-electronic-msgs "300"
        account-not-disabled "0"]
    (and (= state-value person-can-receive-electronic-msgs)
         (= account-disabled-value account-not-disabled))))

(defn query-person-data-from-suomifi
  "Get data for a single person from suomi.fi, e.g. if they have allowed suomi.fi messages to be sent to them"
  [person-id user organization]
  (let [authority-username (:email user)
        suomifi-settings (merge (:suomifi-messages (env/get-config))
                                (select-keys (:suomifi-messages organization) [:authority-id :service-id]))
        msg-id  (uuid/v1)
        hae-asiakkaita-record (build-hae-asiakkaita-message person-id (:id organization) authority-username
                                                            msg-id suomifi-settings)
        xml (HaeAsiakkaitaMessage->string hae-asiakkaita-record)]
    (send-hae-asiakkaita-request xml suomifi-settings)))
