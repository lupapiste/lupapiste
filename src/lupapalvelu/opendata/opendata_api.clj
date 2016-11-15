(ns lupapalvelu.opendata.opendata-api
  (:require [ring.swagger.swagger2 :as rs]
            [noir.core :refer [defpage]]
            [noir.response :as resp]
            [schema.core :as sc]
            [ring.swagger.ui :as ui]
            [noir.server :as server]
            [sade.http :as http]
            [sade.schemas :as ssc]
            [lupapalvelu.mongo :as mongo]
            [schema.core :as s]))

(defonce endpoints (atom []))

(defmacro defendpoint [path params & content]
  (let [meta-data (when (map? (first content)) (first content))]
    `(let [[m# p#] (if (string? ~path) [:get ~path] ~path)]
       (swap! endpoints conj {:method (keyword m#)
                              :path p#
                              :meta ~meta-data
                              :params ~params})
       (defpage ~path ~params
         (let [response-data# (do ~@content)]
           (resp/set-headers
             http/no-cache-headers
             (resp/json response-data#)))))))


(sc/defschema PublicApplicationData
  {:id #"^[0-9a-f]{24}$"})

(defendpoint "/opendata/applications" []
  {:returns PublicApplicationData}
  (mongo/select :applications {} {:_id 1}))

(defn paths []
  (let [paths (map
                (fn [{:keys [path method meta]}]
                  {path {method {:responses {200 {:schema (:returns meta)}}}}})
                @endpoints)]
    (rs/swagger-json {:paths (into {} paths)})))

(defpage "/opendata/swagger.json" []
  (resp/status 200 (resp/json (paths))))

(server/add-middleware
  (fn [handler]
    (ui/wrap-swagger-ui handler "/opendata" :swagger-docs "/opendata/swagger.json")))