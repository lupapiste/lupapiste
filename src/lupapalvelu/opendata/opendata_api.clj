(ns lupapalvelu.opendata.opendata-api
  (:require [ring.swagger.swagger2 :as rs]
            [ring.swagger.json-schema :as rjs]
            [noir.core :refer [defpage]]
            [noir.response :as resp]
            [schema.core :as sc]
            [ring.swagger.ui :as ui]
            [noir.server :as server]
            [sade.http :as http]
            [sade.schemas :as ssc]
            [lupapalvelu.mongo :as mongo]
            [schema.core :as s]
            [sade.util :as util]))

(defonce endpoints (atom []))

(defmacro defendpoint [path & content]
  (let [meta-data (when (map? (first content)) (first content))
        params (->> (util/select-values meta-data [:parameters :optional-parameters])
                     (apply concat)
                     (apply assoc {}))
        letkeys (keys params)]
    `(let [[m# p#]  (if (string? ~path) [:get ~path] ~path)]
       (swap! endpoints conj {:method (keyword m#)
                              :path p#
                              :meta ~meta-data})
       (defpage ~path {:keys ~letkeys}
         (let [response-data# (do ~@content)]
           (resp/set-headers
             http/no-cache-headers
             (resp/json response-data#)))))))

(sc/defschema Asiointitunnus
  (rjs/field sc/Str {:description "Hakemuksen asiointitunnus esim. LP-2016-000-90001"}))

(sc/defschema PublicApplicationData
  {:asiointitunnus Asiointitunnus})

(sc/defschema OrganizationId
  (rjs/field sc/Str {:description "Organisaation tunnus"}))

(defendpoint "/opendata/applications"
  {:summary "Palauttaa organisaation kaikki vireillä olevat hankkeet."
   :parameters [:organization OrganizationId]
   :returns PublicApplicationData}
  (mongo/select :applications {:organization organization} {:_id 1}))

(defn paths []
  (let [paths (map
                (fn [{:keys [path method meta]}]
                  (let [parameters (apply assoc {} (:parameters meta))]
                    {path {method {:summary (:summary meta)
                                   :parameters {:query parameters}
                                   :responses {200 {:schema (:returns meta)}}}}}))
                @endpoints)]
    (rs/swagger-json {:paths (into {} paths)})))

(defpage "/opendata/swagger.json" []
  (resp/status 200 (resp/json (paths))))

(server/add-middleware
  (fn [handler]
    (ui/wrap-swagger-ui handler "/opendata" :swagger-docs "/opendata/swagger.json")))