(ns lupapalvelu.rest.rest-api
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error errorf fatal]]
            [noir.core :refer [defpage]]
            [noir.request :as request]
            [noir.response :as resp]
            [noir.server :as server]
            [ring.swagger.swagger2 :as rs]
            [ring.swagger.ui :as ui]
            [schema.core :as sc]
            [sade.core :refer [fail ok]]
            [sade.env :as env]
            [lupapalvelu.action :as action]
            [lupapalvelu.api-common :refer :all]
            [lupapalvelu.autologin :as autologin]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.rest.config :as config]
            [lupapalvelu.rest.schemas :refer :all]
            [lupapalvelu.rest.applications-data :as applications-data]
            [lupapalvelu.user :as usr]
            [lupapalvelu.oauth :refer [user-for-access-token]]))

(defonce endpoints (atom {}))

(defn valid-inputs?
  ([request param-map]
   (and (valid-inputs? request (:required param-map) identity)
        (valid-inputs? request (:optional param-map) sc/maybe)))
  ([request param-map schema-fn]
   (every? (fn [key]
             (let [input-schema (-> (or (get param-map key)
                                        sc/Any)
                                    (schema-fn))
                   input-val    (get request key)]
               (not (sc/check input-schema input-val))))
           (keys param-map))))

(defn- param-schema-map [interspersed-keys-and-values]
  (apply hash-map interspersed-keys-and-values))

(defmacro defendpoint-for [role-check-fn path register-endpoint? & content]
  (let [meta-data (when (map? (first content)) (first content))
        params {:required (->> (get meta-data :parameters)
                               (param-schema-map))
                :optional (->> (get meta-data :optional-parameters)
                               (param-schema-map))}
        oauth-scope (keyword (:oauth-scope meta-data))
        retval-schema (get meta-data :returns)
        [method path-string]  (if (string? path) [:get path] path)
        response-data (gensym "response-data")]

    `(do
       ~(when register-endpoint?
          `(swap! endpoints assoc
                  ~[(keyword method) path-string]
                  ~{:method (keyword method)
                    :path   path-string
                    :meta   meta-data}))

       (defpage ~path {:keys ~(mapcat keys (vals params)) :as request#}
           (let [~'user ~(if oauth-scope
                           `(user-for-access-token (request/ring-request))
                           `(or (basic-authentication (request/ring-request))
                                (autologin/autologin  (request/ring-request))))
                 ~response-data (delay (try ~@content
                                            (catch Exception e#
                                              (error e#)
                                              (resp/status 500 "Unknown server error"))))]
             (cond
               (not ~'user)
               basic-401

               (not ~(if oauth-scope
                       `(some #{~oauth-scope} (:scopes ~'user))
                       `(~role-check-fn ~'user)))
               (resp/status 401 "Unauthorized")

               (not (valid-inputs? request# ~params))
               (resp/status 400 (resp/json (fail :error.input-validation-error)))

               (action/response? @~response-data)
               @~response-data

               :else
               ~(if retval-schema
                  `(try
                    (sc/validate ~retval-schema @~response-data)
                    (resp/status 200 (resp/json @~response-data))
                    (catch Exception e#
                      (errorf "Possible schema error or other failure in %s: %s" ~path e#)
                      (resp/status 500 "Unknown server error")))
                  `(resp/status 200 {}))))))))

(defmacro defendpoint [path & content]
  "Defines a plain JSON endpoint which can be accessed with basic authentication or autologin.

   If the metadata map given as second argument contains the key :oauth-scope, then the user will be looked up
   (only) by comparing the token given in Authorization header (Bearer token) to issued OAuth access tokens.
   The scopes requested at authorization time must include the one defined in endpoint metadata."
  `(defendpoint-for usr/rest-user? ~path true ~@content))

(defendpoint [:post "/rest/application/:application-id/update-national-building-id"] ; FIXME: deprecated, remove when parter has implementation ready for new endpoint 'update-building-data'
  {:parameters       [:application-id     ApplicationId
                      :operationId        OperationId
                      :nationalBuildingId NationalBuildingId]}
  (let [{org-id :organization :as app} (domain/get-application-as application-id user)]
    (if (and (usr/user-is-authority-in-organization? user org-id)
             (applications-data/update-building! app operationId nationalBuildingId nil))
      (resp/status 200 (ok))
      (resp/status 404 "Not found"))))

(defendpoint [:post "/rest/application/:application-id/update-building-data"]
  {:parameters       [:application-id     ApplicationId
                      :operationId        OperationId
                      :nationalBuildingId NationalBuildingId
                      :location           Location]}
  (let [{org-id :organization :as app} (domain/get-application-as application-id user)]
    (if (and (usr/user-is-authority-in-organization? user org-id)
             (applications-data/update-building! app operationId nationalBuildingId location))
      (resp/status 200 (ok))
      (resp/status 404 "Not found"))))

(defendpoint "/rest/submitted-applications"
  {:summary          "Organisaation kaikki j\u00e4tetyt hakemukset."
   :description      "Palauttaa kaikki organisaatiolle osoitetut hakemukset, jotka ovat Lupapisteess\u00e4 tilassa Hakemus j\u00e4tetty. Toimenpiteet-taulukko sis\u00e4lt\u00e4\u00e4 hakemuksen tarkemmat tiedot (Rakennusvalvonta: toimenpiteet rakennuksittain, Yleisten alueiden k\u00e4ytt\u00f6luvat: toimenpiteen kuvaus sek\u00e4 karttakuviot) KuntaGML-skeemaa noudattelevassa muodossa."
   :parameters       [:organization OrganizationId]
   :returns          JatetytHakemuksetResponse}
  (if (usr/user-is-authority-in-organization? user organization)
    {:ok true :data (applications-data/applications-by-organization organization)}
    {:ok false :data [] :text :error.unauthorized}))

(defendpoint "/rest/get-lp-id-from-previous-permit"
  {:summary     "Luo Lupapiste-hakemuksen taustaj\u00e4rjestelm\u00e4n hakemuksesta tai palauttaa olemassa olevan hakemuksen tunnuksen."
   :description "Paluuarvot:\n* id - Luodun/aiemmin luodun Lupapiste-hakemuksen tunnus\n* ok - Onnistuiko pyynt\u00f6\n* text - Kuvaus muutoksista, voi olla\n    * created-new-application - Luotiin uusi hakemus\n    * already-existing-application - Kuntalupatunnusta vasten oli jo olemassa hakemus"
   :parameters  [:kuntalupatunnus Kuntalupatunnus
                 :authorizeApplicants (sc/maybe AuthorizeApplicantsBoolean)]
   :returns     IdFromPreviousPermitResponse}
   (let [authorizeApplicants (if-not (false? authorizeApplicants) true false)
         response (execute (assoc (action/make-raw "get-lp-id-from-previous-permit" {:kuntalupatunnus kuntalupatunnus
                                                                                     :authorizeApplicants authorizeApplicants}) :user user))]
     (if (action/response? response)
       response
       (if (:ok response)
         (resp/status 200 (resp/json response))
         (resp/status 404 (resp/json response))))))

(defendpoint "/rest/current-configuration"
  {:summary "Returns current configuration for some Lupapiste values"
   :description "Returns JSON object with keys 'permitType' (lupatyypit), 'states' (tilat), 'municipalities' (kunnat) and 'operations' (toimenpiteet)."
   :returns Configuration}
  (resp/status 200 (resp/json (config/current-configuration))))

(defn paths []
  (letfn [(mapper [{:keys [path method meta]}]
            (let [parameters (when (seq (:parameters meta))
                               (apply assoc {} (:parameters meta)))
                  security (if-let [scope (:oauth-scope meta)]
                             {:OAuth2 [scope]}
                             {})]
              {path {method {:summary     (:summary meta)
                             :description (:description meta)
                             :parameters  {:query (or parameters {})}
                             :responses   {200 {:schema (:returns meta)}}
                             :security    security}}}))]
    (rs/swagger-json {:securityDefinitions {:OAuth2 {:type             "oauth2"
                                                     :flow             "implicit"
                                                     :authorizationUrl (str (env/value :host) "/oauth/authorize")
                                                     :scopes           {:read "Grants read access"
                                                                        :pay  "Allows payment with company account"}}}
                      :paths               (into {} (map mapper (vals @endpoints)))})))

(defpage "/rest/swagger.json" []
  (if-let [user (or (basic-authentication (request/ring-request))
                    (autologin/autologin  (request/ring-request)))]
    (if (usr/rest-user? user)
      (resp/status 200 (resp/json (paths)))
      (resp/status 401 "Unauthorized"))
    basic-401))

(server/add-middleware
  (fn [handler]
    (ui/wrap-swagger-ui handler "/rest/api-docs/" :swagger-docs "/rest/swagger.json" :validator-uri nil)))
