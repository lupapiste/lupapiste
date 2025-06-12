(ns lupapalvelu.ryhti.api.ryhti-api
  "Handles the connections to and from the Ryhti API.
  Ryhti (Rakennetun ympäristön tietojärjestelmä) is the national building information service
  which replaces the old VTJ equivalent"
  (:require [lupapalvelu.action :refer [defcommand defquery]]
            [lupapalvelu.json :as json]
            [lupapalvelu.logging :refer [warnf]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.ryhti.api.mappings :as ryhti-mappings]
            [monger.operators :refer [$set]]
            [sade.core :refer [fail ok]]
            [sade.env :as env]
            [sade.http :as http]
            [sade.schemas :as ssc]
            [sade.util :as util]))

(defn- config []
  (let [{:keys [url x-road-client]} (env/value :ryhti :api)]
    (if (and (some? url)
             (some? x-road-client))
      {:url           url
       :x-road-client x-road-client}
      (throw (ex-info "No Ryhti API config available" {:url-configured?           (some? url)
                                                       :x-road-client-configured? (some? x-road-client)})))))

(defn- organization-config [org-id]
  (-> (mongo/select-one :organizations {:_id org-id} [:ryhti-api-configuration])
      :ryhti-api-configuration))

(defn- config-for-municipality
  [ryhti-api-configuration municipality]
  (util/find-by-key :municipality municipality ryhti-api-configuration))

(defn municipality-config [org-id municipality]
  (if-let [conf (config-for-municipality (organization-config org-id) municipality)]
    conf
    (throw (ex-info "No Ryhti API municipality config available" {:organization org-id :municipality municipality}))))

(defn organization-has-config?
  [{:keys [ryhti-api-configuration]} municipality]
  (boolean (config-for-municipality ryhti-api-configuration municipality)))

(defn request [uri params]
  (let [base-url (:url (config))]
    (http/post (str base-url uri) params)))

(defn authenticate!
  "Returns OAuth 2 authentication token and the time for it to expire in seconds"
  [org-id municipality]
  (let [{:keys [x-road-client]}           (config)
        {:keys [client-id client-secret]} (municipality-config org-id municipality)
        resp                              (request (str "/Authenticate?clientId=" client-id)
                                                   {:body     (str "\"" client-secret "\"")
                                                    :headers  {"Content-Type"  "application/json;"
                                                               "X-Road-Client" x-road-client}
                                                    :throw-exceptions false})]
    (cond
      (-> resp :status (= 200))                     {:token (:body resp)
                                                     :ttl   (get-in resp [:headers "X-token-Expires-In"])}
      (= (get-in resp [:headers "Content-Type"])
         "application/problem+json; charset=utf-8") (throw (ex-info "Ryhti API could not authenticate"
                                                                    (json/decode (:body resp) true)))
      :else                                         (throw (ex-info "Ryhti API could not authenticate"
                                                                    {:status (:status resp)})))))

(defn refresh-token!
  "The token from Ryhti is alive for 3600s (1 hour)"
  [org-id municipality]
  (when-let [token (-> (authenticate! org-id municipality)
                       :token)]
    (mongo/insert :ryhti-tokens {:organization org-id
                                 :municipality municipality
                                 :token        token
                                 ;; Mongos expiresAfterSeconds requires a Date object to work
                                 :created      (java.util.Date.)})
    token))

(defn get-token! [org-id municipality]
  (let [{token :token} (mongo/select-one :ryhti-tokens {:organization org-id :municipality municipality} [:token])]
    (or token (refresh-token! org-id municipality))))

(defn with-ryhti-headers! [org-id municipality params]
  (let [{:keys [x-road-client]} (config)]
    (merge {:headers {"Authorization" (str "Bearer " (get-token! org-id municipality))
                      "Content-Type"  "application/json"
                      "X-Road-Client" x-road-client}}
           params)))

(defn get-permanent-permit-id!
  "Creates a new or returns existing Permanent (Building) Permit Identifier

  Happy paths:
  200 - Permanent permit id already exists for this municipalityPermitId (:created false)
  201 - New permanent permit id created (:created true)

  Returns a map of :response :created and :permanentPermitIdentifier"
  [org-id municipality app-id building-control-type]
  {:pre [(contains? ryhti-mappings/building-control-case building-control-type)]}
  (let [permit-type (building-control-type ryhti-mappings/building-control-case)
        resp        (request "/PermanentIdentifiers/BuildingPermitIdentifier"
                             (with-ryhti-headers! org-id municipality {:body             (json/encode
                                                                                           {:municipalityNumber   municipality
                                                                                            :municipalityPermitId app-id
                                                                                            :permitType           permit-type})
                                                                       :throw-exceptions false}))
        status      (:status resp)]
    (if (or (= status 200)
            (= status 201))
      (-> resp :body (json/decode true))
      (warnf "Ryhti API Failed to get building permit identifier! status: %s, application: %s" status app-id))))

(defn organization-has-ryhti-configuration [{{:keys [organizationId]} :data}]
  (when (empty? (organization-config organizationId))
    (fail :error.no-ryhti-configuration-found-for-org)))

(defn upload-file! [org-id municipality {:keys [file content-type]}]
  (let [resp (request (str "/File?municipality-id=" municipality)
                      (with-ryhti-headers! org-id municipality
                        {:multipart [{:name "file"
                                      :mime-type content-type
                                      :content file}]
                         :throw-exceptions false}))
        status (:status resp)]
    (if (#{200 201} status)
      ;; Success response body is just the ID as text
      (:body resp)
      (throw (ex-info "File upload to Ryhti failed" {:resp resp})))))

(defquery ryhti-api-configuration
  {:description      "Get all the current Ryhti configurations for some organization"
   :parameters       [organizationId]
   :user-roles       #{:admin}
   :pre-checks       [org/organization-exists]}
  [_]
  (ok :configuration (or (organization-config organizationId) [])))

(defcommand set-ryhti-api-configuration
  {:description      "Set the Ryhti configurations for a given organization."
   :parameters       [organizationId ryhtiConfig]
   :user-roles       #{:admin}
   :input-validators [{:organizationId ssc/NonBlankStr
                       :ryhtiConfig    [org/RyhtiApiConfiguration]}]
   :pre-checks       [org/organization-exists]}
  [_]
  (mongo/update-by-id :organizations organizationId {$set {:ryhti-api-configuration ryhtiConfig}})
  (ok :configuration (or (organization-config organizationId) [])))
