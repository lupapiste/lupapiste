(ns lupapalvelu.rest.rest-api
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error errorf fatal]]
            [ring.swagger.swagger2 :as rs]
            [noir.core :refer [defpage]]
            [noir.response :as resp]
            [schema.core :as sc]
            [ring.swagger.ui :as ui]
            [noir.server :as server]
            [sade.core :refer [fail!]]
            [sade.util :as util]
            [lupapalvelu.rest.schemas :refer :all]
            [lupapalvelu.rest.applications-data :as applications-data]
            [lupapalvelu.api-common :refer :all]
            [noir.request :as request]
            [lupapalvelu.action :as action]
            [ring.swagger.json-schema :as rjs]
            [lupapalvelu.user :as usr]))

(defonce endpoints (atom []))

(defn valid-inputs? [request param-map]
  (not-every? (fn [key]
                (let [input-schema (or (get param-map key) sc/Any)
                      input-val    (get request key)]
                  (sc/check input-schema input-val))) (keys param-map)))

(defmacro defendpoint [path & content]
  (let [meta-data (when (map? (first content)) (first content))
        params (->> (util/select-values meta-data [:parameters :optional-parameters])
                     (apply concat)
                     (apply assoc {}))
        letkeys (keys params)]
    `(let [[m# p#]        (if (string? ~path) [:get ~path] ~path)
           retval-schema# (get ~meta-data :returns)]
       (swap! endpoints conj {:method (keyword m#)
                              :path   p#
                              :meta   ~meta-data})
       (defpage ~path {:keys ~letkeys :as request#}
         (if-let [~'user (basic-authentication (request/ring-request))]
           (if (usr/rest-user? ~'user)
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

(defendpoint "/rest/submitted-applications"
  {:summary          "Organisaation kaikki j\u00e4tetyt hakemukset."
   :description      "Palauttaa kaikki organisaatiolle osoitetut hakemukset, jotka ovat Lupapisteess\u00e4 tilassa Hakemus j\u00e4tetty. Toimenpiteet-taulukko sis\u00e4lt\u00e4\u00e4 hakemuksen tarkemmat tiedot (Rakennusvalvonta: toimenpiteet rakennuksittain, Yleisten alueiden k\u00e4ytt\u00f6luvat: toimenpiteen kuvaus sek\u00e4 karttakuviot) KRYSP-skeemaa noudattelevassa muodossa."
   :parameters       [:organization OrganizationId]
   :returns          JatetytHakemuksetResponse}
  (if (usr/user-is-authority-in-organization? user organization)
    {:ok true :data (applications-data/applications-by-organization organization)}
    {:ok false :data [] :text :error.unauthorized}))

(defendpoint "/rest/get-lp-id-from-previous-permit"
  {:summary     "Luo Lupapiste-hakemuksen taustaj\u00e4rjestelm\u00e4n hakemuksesta tai palauttaa olemassa olevan hakemuksen tunnuksen."
   :description ""
   :parameters  [:kuntalupatunnus (rjs/field sc/Str {:description "Taustaj\u00e4rjestelm\u00e4ss\u00e4 olevan hakemuksen kuntalupatunnus"})]
   :returns     {:ok sc/Bool
                 :text sc/Keyword
                 (sc/optional-key :id) sc/Int}}
   (let [response (execute (assoc (action/make-raw "get-lp-id-from-previous-permit" {:kuntalupatunnus kuntalupatunnus}) :user user))]
     (if (action/response? response)
       response
       (if (:ok response)
         (resp/status 200 (resp/json response))
         (resp/status 404 (resp/json response))))))

(defn paths []
  (letfn [(mapper [{:keys [path method meta]}]
            (let [parameters (apply assoc {} (:parameters meta))]
              {path {method {:summary    (:summary meta)
                             :description (:description meta)
                             :parameters {:query parameters}
                             :responses  {200 {:schema (:returns meta)}}}}}))]
    (rs/swagger-json {:paths (into {} (map mapper @endpoints))})))

(defpage "/rest/swagger.json" []
  (if-let [user (basic-authentication (request/ring-request))]
    (if (usr/rest-user? user)
      (resp/status 200 (resp/json (paths)))
      (resp/status 401 "Unauthorized"))
    basic-401))

(server/add-middleware
  (fn [handler]
    (ui/wrap-swagger-ui handler "/rest/api-docs/" :swagger-docs "/rest/swagger.json")))