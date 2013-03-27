(ns lupapalvelu.find-address
  (:use [monger.operators])
  (:require [clojure.string :as s]
            [monger.query :as query]
            [lupapalvelu.mongo :as mongo]))

(defn find-addresses [term]
  (map #(assoc % :kind :poi)
     (query/with-collection "poi"
       (query/find {:name {$regex (str \^ (s/lower-case term))}})
       (query/limit 10))))

(comment
  (clojure.pprint/pprint
    (query/with-collection "poi"
      (query/find {:type {$in ["540" "550"]}})
      (query/limit 10))))

(comment
  (defn find-addresses-proxy [request]
    (let [query (get (:query-params request) "query")
          address (parse-address query)
          [status response] (apply find-addresses address)
          feature->string (wfs/feature-to-address-string address)]
      (if (= status :ok)
        (let [features (take 10 response)]
          (resp/json {:query query
                      :suggestions (map feature->string features)
                      :data (map wfs/feature-to-address features)}))
        (do
          (errorf "find-addresses failed: status=%s, response=%s" status response)
          (resp/status 503 "Service temporarily unavailable"))))))
