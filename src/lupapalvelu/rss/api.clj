(ns lupapalvelu.rss.api
  (:require [clojure.java.io :as io]
            [lupapalvelu.http.noir.util :as noir-util]
            [lupapalvelu.logging :as log]
            [noir.core :refer [defpage]]
            [noir.request :as request]
            [noir.response :as resp]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]))

(defmulti rss-content
  "Produce RSS XML as string for the given endpoint"
  (fn [{:keys [endpoint]}]
    endpoint))

(defmethod rss-content :default
  [_options]
  {:status :not-found})

(defmethod rss-content "extensions"
  [_options]
  (let [file (slurp (io/resource "rss/lupapiste_extensions.xsd"))]
    {:status :ok
     :xml file}))

(defn- supports-gzip?
  [request]
  (->> request
       :headers
       (keep (fn [[k v]]
               (when (ss/=trim-i k "accept-encoding")
                 v)))
       (filter (fn [encodings]
                 (->> (ss/split encodings #",")
                      (some #{"gzip"}))))
       seq
       boolean))

;; TODO: Add/check headers wrt. caching if the client already has fresh data
;; TODO: Return as GZIP if supported, otherwise unzip
(defpage [:get "/rss/:endpoint"]
  {:keys [endpoint]}
  (log/with-logging-context
    {:endpoint endpoint}
    (let [request              (request/ring-request)
          gzip?                (supports-gzip? request)
          options              (util/mmap {:now    (date/now)
                                           :system (noir-util/get-system)
                                           :params (get request :params {})}
                                          endpoint gzip?)
          {:keys [status xml]} (rss-content options)]
      (case status
        :bulletin-cache-cleared (resp/status 200 "Bulletins cache cleared")
        :not-found              (resp/status 404 "Not found")
        :error                  (resp/status 500 "Internal server error")
        :ok                     (->> {:body xml}
                                     (resp/content-type "application/xml")
                                     (resp/status 200))))))
