(ns lupapalvelu.rest.rest-api
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error errorf fatal]]
            [noir.core :refer [defpage]]
            [noir.request :as request]
            [noir.response :as resp]
            [noir.server :as server]
            [ring.swagger.swagger2 :as rs]
            [ring.swagger.ui :as ui]
            [schema.core :as sc]
            [sade.core :refer [fail!]]
            [sade.util :as util]
            [lupapalvelu.action :as action]
            [lupapalvelu.api-common :refer :all]
            [lupapalvelu.autologin :as autologin]
            [lupapalvelu.rest.schemas :refer :all]
            [lupapalvelu.rest.applications-data :as applications-data]
            [lupapalvelu.user :as usr]))

(defonce endpoints (atom []))

(defn valid-inputs? [request param-map]
  (let [param-keys (keys param-map)]
    (or (empty? param-keys)
        (not-every? (fn [key]
                      (let [input-schema (or (get param-map key) sc/Any)
                            input-val    (get request key)]
                        (sc/check input-schema input-val))) param-keys))))

(defmacro defendpoint-for [role-check-fn path & content]
  (let [meta-data (when (map? (first content)) (first content))
        params-list (->> (util/select-values meta-data [:parameters :optional-parameters])
                         (apply concat))
        params  (if (empty? params-list)
                  {}
                  (apply assoc {} params-list))
        letkeys (keys params)]
    `(let [[m# p#]        (if (string? ~path) [:get ~path] ~path)
           retval-schema# (get ~meta-data :returns)]
       (swap! endpoints conj {:method (keyword m#)
                              :path   p#
                              :meta   ~meta-data})
       (defpage ~path {:keys ~letkeys :as request#}
         (if-let [~'user (or (basic-authentication (request/ring-request))
                             (autologin/autologin  (request/ring-request)))]
           (if (~role-check-fn ~'user)
             ; Input schema validation
             (if (valid-inputs? request# ~params)
               (let [response-data# (do ~@content)]
                 ; Response data schema validation
                 (if (action/response? response-data#)
                   response-data#
                   (try
                     (sc/validate retval-schema# response-data#)
                     (resp/status 200 (resp/json response-data#))
                     (catch Exception e#
                       (errorf "Possible schema error or other failure in %s: %s" ~path e#)
                       (resp/status 500 "Unknown server error")))))
               (resp/status 404 {:ok "false" :text :error.input-validation-error}))
             (resp/status 401 "Unauthorized"))
           basic-401)))))

(defmacro defendpoint [path & content]
  `(defendpoint-for usr/rest-user? ~path ~@content))

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

(defn paths []
  (letfn [(mapper [{:keys [path method meta]}]
            (let [parameters (apply assoc {} (:parameters meta))]
              {path {method {:summary     (:summary meta)
                             :description (:description meta)
                             :parameters  {:query parameters}
                             :responses   {200 {:schema (:returns meta)}}}}}))]
    (rs/swagger-json {:paths (into {} (map mapper @endpoints))})))

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
