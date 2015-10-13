(ns lupapalvelu.tiedonohjaus
  (:require [sade.http :as http]
            [sade.env :as env]
            [clojure.core.memoize :as memo]
            [lupapalvelu.organization :as o]))

(defn- build-url [& path-parts]
  (apply str (env/value :toj :host) path-parts))

(defn- get-tos-functions-from-toj [organization-id]
  (let [has-archive? (:permanent-archive-enabled (o/get-organization organization-id))]
    (if (and (env/feature? :tiedonohjaus) has-archive?)
      (try
        (let [url (build-url "/tiedonohjaus/api/org/" organization-id "/asiat")
              response (http/get url {:as :json
                                      :throw-exceptions false})]
          (if (= 200 (:status response))
            (:body response)
            []))
        (catch Exception _
          []))
      [])))

(def available-tos-functions
  (memo/ttl get-tos-functions-from-toj
            :ttl/threshold 10000))

(defn- get-metadata-for-document-from-toj [organization tos-function document-type]
  (if (env/feature? :tiedonohjaus)
    (when (and organization tos-function document-type)
      (try
        (let [doc-id (if (map? document-type) (str (name (:type-group document-type)) "." (name (:type-id document-type))) document-type)
              url (build-url "/tiedonohjaus/api/org/" organization "/asiat/" tos-function "/document/" doc-id)
              response (http/get url {:as :json
                                      :throw-exceptions false})]
          (if (= 200 (:status response))
            (:body response)
            {}))
        (catch Exception _
          {})))
    {}))

(def metadata-for-document
  (memo/ttl get-metadata-for-document-from-toj
            :ttl/threshold 10000))

(defn document-with-updated-metadata [document organization tos-function & type]
  (let [document-type (or type (:type document))]
    (->> (metadata-for-document organization tos-function document-type)
         (assoc document :metadata))))
