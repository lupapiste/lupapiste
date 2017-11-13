(ns lupapalvelu.xml.krysp.http
  (:require [clj-http.cookies :as cookies]
            [sade.http :as http]
            [sade.core :refer :all]
            [sade.env :as env]
            [lupapalvelu.cookie :as lupa-cookies]
            [lupapalvelu.mongo :as mongo]))


(defn- with-krysp-defaults [options]
  (merge
    options
    (when env/dev-mode?
      {:cookie-store
       (doto (cookies/cookie-store)
         (cookies/add-cookie (lupa-cookies/->lupa-cookie "test_db_name" mongo/*db-name*)))})))

(defn POST
  ([xml http-conf]
    (POST xml http-conf nil))
  ([xml http-conf options]
   (let [opts (with-krysp-defaults (assoc options :body xml))]
     (http/post (:url http-conf) opts))))
