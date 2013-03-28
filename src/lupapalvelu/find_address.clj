(ns lupapalvelu.find-address
  (:use [monger.operators])
  (:require [clojure.string :as s]
            [monger.query :as q]
            [lupapalvelu.mongo :as mongo]))

(defn find-addresses [term]
  (map #(assoc % :kind :poi)
       (q/with-collection "poi"
         (q/find {:name {$regex (str \^ (s/lower-case term))}})
         (q/limit 11))))


(comment
  (defn- get-municipalities [name]
    (seq (let [municipalities (q/with-collection "poi"
                                (q/find {:name {$regex (str \^ (s/lower-case name))}
                                         :type {$in ["540" "550"]}})
                                (q/limit 11))]
           )))
  
  (some get-municipalities ["piiri" "tamm"])
  (get-municipalities "tammi"))

(comment
  (clojure.pprint/pprint
    (q/with-collection "poi"
      (q/find {:type {$in ["540" "550"]}})
      (q/limit 10))))

(comment
  
  (defn find-addresses [street number city]
    (wfs/execute wfs/maasto
      (cond
        (and (s/blank? number) (s/blank? city)) (wfs/query {"typeName" "oso:Osoitenimi"}
                                                           (wfs/sort-by "oso:kuntanimiFin")
                                                           (wfs/filter
                                                             (wfs/and
                                                               (wfs/property-is-like "oso:katunimi" (str street "*"))
                                                               (wfs/property-is-equal "oso:jarjestysnumero" "1"))))
        (s/blank? city) (wfs/query {"typeName" "oso:Osoitenimi"}
                                   (wfs/sort-by "oso:kuntanimiFin")
                                   (wfs/filter
                                     (wfs/and
                                       (wfs/property-is-like "oso:katunimi"   (str street "*"))
                                       (wfs/property-is-like "oso:katunumero" (str number "*"))
                                       (wfs/property-is-less "oso:jarjestysnumero" "10"))))
        (s/blank? number) (wfs/query {"typeName" "oso:Osoitenimi"}
                                     (wfs/sort-by "oso:katunumero")
                                     (wfs/filter
                                       (wfs/and
                                         (wfs/property-is-like "oso:katunimi" (str street "*"))
                                                    (wfs/or
                                                      (wfs/property-is-like "oso:kuntanimiFin" (str city "*"))
                                                      (wfs/property-is-like "oso:kuntanimiSwe" (str city "*"))))))
        :else (wfs/query {"typeName" "oso:Osoitenimi"}
                         (wfs/sort-by "oso:katunumero")
                         (wfs/filter
                           (wfs/and
                             (wfs/property-is-like "oso:katunimi"     (str street "*"))
                             (wfs/property-is-like "oso:katunumero"   (str number "*"))
                             (wfs/or
                               (wfs/property-is-like "oso:kuntanimiFin" (str city "*"))
                               (wfs/property-is-like "oso:kuntanimiSwe" (str city "*")))))))))
  
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
