(ns lupapalvelu.rest.rest-api
  (:require [clojure.set :refer [rename-keys]]
            [lupapalvelu.action :as action]
            [lupapalvelu.api-common :as api-common]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.autologin :as autologin]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.http.noir.util :as noir-util]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.integrations.messages :as messages]
            [lupapalvelu.logging :refer [error errorf warnf]]
            [lupapalvelu.oauth.core :as oauth]
            [lupapalvelu.pdf.pdf-export :as pdf-export]
            [lupapalvelu.rest.applications-data :as applications-data]
            [lupapalvelu.rest.config :as config]
            [lupapalvelu.rest.dmcity-states :as dmcity-states]
            [lupapalvelu.rest.review :as rest-review]
            [lupapalvelu.rest.schemas :as rest-schemas]
            [lupapalvelu.rest.verdict :as rest-verdict]
            [lupapalvelu.user :as usr]
            [noir.core :refer [defpage]]
            [noir.request :as request]
            [noir.response :as resp]
            [noir.server :as server]
            [ring.swagger.swagger-ui :as ui]
            [ring.swagger.swagger2 :as rs]
            [sade.core :refer [fail now ok]]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

;; Endpoints to publish in swagger
(defonce endpoints (atom {}))

(defn- param-schema-map [interspersed-keys-and-values]
  (apply hash-map interspersed-keys-and-values))

(defn- make-schema
  [{:keys [parameters optional-parameters]}]
  (merge
    {`sc/Keyword `sc/Any} ;; open schema
    (->> parameters
         (partition-all 2)
         (reduce (fn [m [k v]]
                   (assoc m k v))
                 {}))
    (->> optional-parameters
         (partition-all 2)
         (reduce (fn [m [k v]]
                   (assoc m `(sc/optional-key ~k) v))
                 {}))))

(defn- make-checker
  [schema]
  `(sc/checker ~schema))

(defmacro defendpoint-for
  "Register a HTTP endpoint.

  `role-check-fn`: Predicate for checking if `user` from request is authorized. Skipped if `oauth-scope` defined.
  `path`: Either a string or a pair [method, string] describing the path and HTTP method (default :get)
  `register-endpoint?`: If true, export endpoint in swagger API.
  `metadata`: Optional metadata, with keys:
  - `parameters`: Required parameters, vector of key + schema pairs
  - `optional-parameters`: Optional parameters, vector of key + schema pairs
  - `oauth-scope`: Authenticate using Bearer token, authorize by verifying `user` has given OAuth scope
                 (instead of `role-check-fn`).
  - `returns`: Validate response data against the given Schema. Ignored if endpoint body returns a raw
             HTTP response.
  - `meta`: Extra metadata for Swagger
  `content`: The endpoint body. May return a raw HTTP response (returned as-is), or a data structure,
           which will be checked against `returns` Schema and converted to JSON."
  {:arglists '([role-check-fn path register-endpoint? & content]
               [role-check-fn path register-endpoint? metadata & content])}
  [role-check-fn path register-endpoint? & content]
  (let [meta-data            (when (map? (first content)) (first content))
        params               {:required (->> (get meta-data :parameters)
                                             (param-schema-map))
                              :optional (->> (get meta-data :optional-parameters)
                                             (param-schema-map))}
        oauth-scope          (keyword (:oauth-scope meta-data))
        retval-schema        (get meta-data :returns)
        [method path-string] (if (string? path) [:get path] path)
        response-data        (gensym "response-data")]

    `(let [req-checker# ~(make-checker (make-schema meta-data))]
       ~(when register-endpoint?
          `(swap! endpoints assoc
                  ~[(keyword method) path-string]
                  ~{:method (keyword method)
                    :path   path-string
                    :meta   meta-data}))

       (defpage ~path {:keys ~(vec (mapcat keys (vals params))) :as request#}
         (let [~'user          ~(if oauth-scope
                                  `(oauth/user-for-access-token (request/ring-request))
                                  `(or (api-common/basic-authentication (request/ring-request))
                                       (autologin/autologin  (request/ring-request))))
               request-errors# (delay (req-checker# request#))
               ~response-data  (delay (try ~@content
                                           (catch Exception e#
                                             (error e#)
                                             (resp/status 500 "Unknown server error"))))]
           (cond
             (not ~'user)
             api-common/basic-401

             ;; `:enabled` can be missing if the user is actually a session summary.
             (false? (:enabled ~'user))
             api-common/basic-401

             (not ~(if oauth-scope
                     `(some #{~oauth-scope} (:scopes ~'user))
                     `(~role-check-fn ~'user)))
             (resp/status 401 "Unauthorized")

             (deref request-errors#)
             (do
               (warnf "Endpoint input validation error: %s" (deref request-errors#))
               (resp/status 400 (resp/json (fail :error.input-validation-error))))

             (action/response? @~response-data)
             (do
               ;; Warn if the endpoint defines a `:returns` schema yet returns a HTTP response.
               ;; Either drop the `:returns` parameter and do the validation yourself, or return
               ;; a plain data structure and let this macro validate and convert it to
               ;; a HTTP response (below).
               ~(when retval-schema
                  `(when (= 200 (:status @~response-data))
                     (warnf "Ignoring `:returns` schema for endpoint %s" ~path)))
               @~response-data)

             :else
             ~(if retval-schema
                `(try
                   (sc/validate ~retval-schema @~response-data)
                   (resp/status 200 (resp/json @~response-data))
                   (catch Exception e#
                     (errorf "Possible schema error or other failure in %s: %s" ~path e#)
                     (resp/status 500 "Unknown server error")))
                `(resp/status 200 {})))))
       ;; noir.core/defpage returns the contents of the noir.core/noir-routes atom
       ;; but that's just unnecessary noise when evaling this macro from a REPL
       nil)))

(defmacro defendpoint
  "Defines a plain JSON endpoint which can be accessed with basic authentication or autologin.

   If the metadata map given as second argument contains the key :oauth-scope, then the user will be looked up
   (only) by comparing the token given in Authorization header (Bearer token) to issued OAuth access tokens.
   The scopes requested at authorization time must include the one defined in endpoint metadata."
  [path & content]
  `(defendpoint-for usr/rest-user? ~path true ~@content))


(defn- save-building-data-message! [user partner status data timestamp]
  (let [message-id (messages/create-id)]
    (messages/save {:id          message-id
                    :direction   "in"
                    :messageType "update-building-data"
                    :format      "json"
                    :created     timestamp
                    :partner     partner
                    :status      status
                    :application {:id (:application-id data)}
                    :initiator   (select-keys user [:id :username])
                    :action      "update-building-data"
                    :data        data})
    message-id))

(defendpoint [:post "/rest/application/:application-id/update-building-data"]
  {:parameters          [:application-id     rest-schemas/ApplicationId
                         :operationId        rest-schemas/OperationId
                         :nationalBuildingId rest-schemas/NationalBuildingId
                         :location           rest-schemas/Location]
   :optional-parameters [:apartmentsData     rest-schemas/ApartmentsData]}
  (let [data            {:application-id     application-id
                         :operationId        operationId
                         :nationalBuildingId nationalBuildingId
                         :location           location
                         :apartmentsData     apartmentsData}

        apartments-data (mapv #(rename-keys %
                                            {:stairway            :porras
                                             :flatNumber          :huoneistonumero
                                             :splittingLetter     :jakokirjain
                                             :permanentFlatNumber :pysyvaHuoneistotunnus})
                              apartmentsData)
        timestamp       (now)
        {org-id :organization
         :as    app}    (domain/get-application-as application-id user)]
    (if (and (usr/user-is-authority-in-organization? user org-id)
             (applications-data/update-building! app operationId nationalBuildingId
                                                 location apartments-data timestamp))
      (do (save-building-data-message! user "dmcity" "processed" data timestamp)
          (resp/status 200 (ok)))
      (resp/status 404 "Not found"))))

(defendpoint "/rest/submitted-applications"
  {:summary     "Organisaation kaikki j\u00e4tetyt hakemukset."
   :description "Palauttaa kaikki organisaatiolle osoitetut hakemukset, jotka ovat Lupapisteessä tilassa Hakemus jätetty. Toimenpiteet-taulukko sisältää hakemuksen tarkemmat tiedot (Rakennusvalvonta: toimenpiteet rakennuksittain, Yleisten alueiden käyttöluvat: toimenpiteen kuvaus sekä karttakuviot) KuntaGML-skeemaa noudattelevassa muodossa."
   :parameters  [:organization rest-schemas/OrganizationId]
   :returns     rest-schemas/JatetytHakemuksetResponse}
  (if (usr/user-is-authority-in-organization? user organization)
    {:ok true :data (applications-data/applications-by-organization organization)}
    {:ok false :data [] :text :error.unauthorized}))

(defendpoint "/rest/get-lp-id-from-previous-permit"
  {:summary             "Luo Lupapiste-hakemuksen taustaj\u00e4rjestelm\u00e4n hakemuksesta tai palauttaa olemassa olevan hakemuksen tunnuksen."
   :description         "Paluuarvot:\n* id - Luodun/aiemmin luodun Lupapiste-hakemuksen tunnus\n* ok - Onnistuiko pyynt\u00f6\n* text - Kuvaus muutoksista, voi olla\n    * created-new-application - Luotiin uusi hakemus\n    * already-existing-application - Kuntalupatunnusta vasten oli jo olemassa hakemus"
   :parameters          [:kuntalupatunnus rest-schemas/Kuntalupatunnus]
   :optional-parameters [:authorizeApplicants (sc/maybe rest-schemas/AuthorizeApplicantsBoolean)]
   :returns             rest-schemas/IdFromPreviousPermitResponse}
  (let [authorizeApplicants (if-not (false? authorizeApplicants) true false)
        response            (-> (action/make-raw "get-lp-id-from-previous-permit"
                                                 {:kuntalupatunnus     kuntalupatunnus
                                                  :authorizeApplicants authorizeApplicants})
                                (assoc :user user :system (noir-util/get-system))
                                api-common/execute)]
    (if (action/response? response)
      response
      (if (:ok response)
        (resp/status 200 (resp/json response))
        (resp/status 404 (resp/json response))))))

(defendpoint "/rest/current-configuration"
  {:summary "Returns current configuration for some Lupapiste values"
   :description "Returns JSON object with keys 'permitType' (lupatyypit), 'states' (tilat), 'municipalities' (kunnat) and 'operations' (toimenpiteet)."
   :returns rest-schemas/Configuration}
  (resp/status 200 (resp/json (config/current-configuration))))

(def boolean-string #"(?i)^(true|false)$")
(defn true-string? [s] (ss/=trim-i s "true"))

(defendpoint "/rest/latest-attachment-version"
  {:summary             "Returns the latest version of the given attachment."
   :description         "REST counterpart for `latest-attachment-version`."
   :parameters          [:attachment-id ssc/AttachmentId]
   :optional-parameters [:preview  boolean-string
                         :download boolean-string]}
  (att/output-attachment (att/get-attachment-latest-version-file (noir-util/get-system)
                                                                 user
                                                                 attachment-id
                                                                 (true-string? preview))
                         (true-string? download)))

(defn error-response [status error]
  (resp/status status (resp/content-type "text/plain" (name error))))

(defn pdf-response [body]
  (resp/status 200 (resp/content-type "application/pdf" body)))

(defendpoint "/rest/submitted-application-pdf-export"
  {:summary             "Generates PDF for the given submitted application"
   :description         "REST counterpart for `submitted-application-pdf-export`"
   :parameters          [:id ssc/ApplicationId]
   :optional-parameters [:lang i18n/EnumSupportedLanguages]}
  (let [application (domain/get-application-as id user :include-canceled-apps? true)
        lang        (or lang i18n/default-lang)]
    (if-let [application-pdf (and application
                                  (pdf-export/export-submitted-application user lang id))]
      (pdf-response application-pdf)
      (error-response 404 :error.application-not-found))))

(defendpoint "/rest/pdf-export"
  {:summary             "Generates PDF for the given application"
   :description         "REST counterpart for `pdf-export`."
   :parameters          [:id ssc/ApplicationId]
   :optional-parameters [:lang i18n/EnumSupportedLanguages]}
  (let [{:keys [permitType]
         :as   application} (domain/get-application-as id user :include-canceled-apps? true)
        lang                (or lang i18n/default-lang)
        not-found           (error-response 404 :error.application-not-found)]
    (cond
      (nil? application)   not-found

      (= permitType "ARK") (error-response 403 :error.unsupported-permit-type)

      :else
      (-> application
          (app-utils/with-masked-person-ids user)
          (pdf-export/generate lang)
          pdf-response))))


;; View the review adding endpoint in swagger, too.
(swap! endpoints assoc
       [:put "/rest/application/:application-id/review/:review-id"]
       {:method :put
        :path   "/rest/application/:application-id/review/:review-id"
        :meta   {:summary             "Creates or updates a review task in Lupapiste that has a :muuTunnus UUID matching the \"review-id\" parameter."
                 :description         ""
                 ;; TODO: Make this work in the rest-swagger, with 'multipart/form-data' content-type.
                 ;; To fully support `multipart/form-data` (e.g. describe the JSON payload) in the swagger UI, we need to
                 ;; - describe the schema in OpenAPI 3.x (e.g. with `[metosin/schema-tools "0.12.3"]`)
                 ;; - have a swagger-like UI which supports it
                 :path-parameters     [:application-id rest-schemas/ApplicationId
                                       :review-id      rest-schemas/UUIDStr]
                 :formdata-parameters [:katselmus rest-schemas/KatselmusJSONStr]
                 :consumes            ["multipart/form-data"]
                 :returns             rest-schemas/YaMattiReviewResponse}})

(defpage [:put "/rest/application/:application-id/review/:review-id"]
  request
  (let [user   (or (api-common/basic-authentication (request/ring-request))
                   (autologin/autologin  (request/ring-request)))
        result (rest-review/upsert {:request   request
                                    :user      user
                                    :timestamp (now)
                                    :system    (noir-util/get-system)})]
    result))

(swap! endpoints assoc
       [:post "/rest/application/:application-id/verdict"]
       {:method :post
        :path   "/rest/application/:application-id/verdict"
        :meta   {:summary             "Creates a verdict in Lupapiste."
                 :description         "The target application must be in a suitable state and have no existing verdicts."
                 :path-parameters     [:application-id rest-schemas/ApplicationId]
                 :formdata-parameters [:verdict rest-verdict/verdict-form-data]
                 :consumes            ["multipart/form-data"]
                 :return-map          rest-verdict/return-code-map}})

(defpage [:post "/rest/application/:application-id/verdict"]
  request
  (try
    (let [user   (or (api-common/basic-authentication (request/ring-request))
                     (autologin/autologin  (request/ring-request)))
          result (rest-verdict/insert {:request           request
                                       :user              user
                                       :request-timestamp (now)
                                       :system            (noir-util/get-system)})]
      (->> result
           resp/json
           (resp/status (or (:status result) 200))))
    (catch Exception e
      (error e "Unexpected error from dmCity verdict processing")
      (->> (fail "Internal server error")
           resp/json
           (resp/status 500)))))

(swap! endpoints assoc
       [:put "/rest/application/:application-id/states"]
       {:method :put
        :path   "/rest/application/:application-id/states"
        :meta   {:summary         "Set the external state for an application in Lupapiste."
                 :path-parameters [:application-id rest-schemas/ApplicationId]
                 :body-parameters dmcity-states/RequestBody}})

(defpage [:put "/rest/application/:application-id/states"]
  request
  (try
    (let [user   (or (api-common/basic-authentication (request/ring-request))
                     (autologin/autologin  (request/ring-request)))
          result (dmcity-states/upsert {:request           request
                                        :user              user
                                        :request-timestamp (now)
                                        :system            (noir-util/get-system)})]
      (->> result
           resp/json
           (resp/status (or (:status result) 200))))
    (catch Exception e
      (error e "Unexpected error from dmCity status processing")
      (->> (fail "Internal server error")
           resp/json
           (resp/status 500)))))

(defn paths []
  (letfn [(mapper [{:keys [path method meta]}]
            (let [required            (some->>  meta :parameters seq (apply assoc {}))
                  optional            (some->>  meta :optional-parameters seq (apply assoc {}) (util/map-keys sc/optional-key))
                  parameters          (merge required optional)
                  path-parameters     (when (seq (:path-parameters meta))
                                        (apply assoc {} (:path-parameters meta)))
                  formdata-parameters (when (seq (:formdata-parameters meta))
                                        (apply assoc {} (:formdata-parameters meta)))
                  body-parameters     (not-empty (:body-parameters meta))
                  security            (if-let [scope (:oauth-scope meta)]
                                        {:OAuth2 [scope]}
                                        {})]
              {path {method (merge {:summary     (:summary meta)
                                    :description (:description meta)
                                    :parameters  {:query    (or parameters {})
                                                  :path     (or path-parameters {})
                                                  :body     body-parameters
                                                  :formData (or formdata-parameters {})}
                                    :responses   (or (:return-map meta)
                                                     {200 {:schema (:returns meta)}})
                                    :security    security}
                                   (when-let [consumes (seq (:consumes meta))]
                                     {:consumes consumes}))}}))]
    (rs/swagger-json {:securityDefinitions {:OAuth2 {:type             "oauth2"
                                                     :flow             "implicit"
                                                     :authorizationUrl (str (noir-util/hostname) "/oauth/authorize")
                                                     :scopes           {:read "Grants read access"
                                                                        :pay  "Allows payment with company account"}}}
                      :paths               (into {} (map mapper (vals @endpoints)))})))

(defpage "/rest/swagger.json" []
  (if-let [user (or (api-common/basic-authentication (request/ring-request))
                    (autologin/autologin  (request/ring-request)))]
    (if (usr/rest-user? user)
      (resp/status 200 (resp/json (paths)))
      (resp/status 401 "Unauthorized"))
    api-common/basic-401))

(server/add-middleware
  (fn [handler]
    (ui/wrap-swagger-ui handler {:path "/rest/api-docs/", :swagger-docs "/rest/swagger.json", :validator-uri nil})))
