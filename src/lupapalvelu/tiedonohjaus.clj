(ns lupapalvelu.tiedonohjaus
  (:require [sade.http :as http]
            [sade.env :as env]
            [clojure.core.memoize :as memo]))

(defn- build-url [& path-parts]
  (apply str (env/value :toj :host) path-parts))

(defn- get-tos-functions-from-toj [organization]
  (if (env/feature? :tiedonohjaus)
    (try
      (let [url (build-url "/tiedonohjaus/api/org/" organization "/asiat")
            response (http/get url {:as :json
                                    :throw-exceptions false})]
        (if (= 200 (:status response))
          (:body response)
          []))
      (catch Exception _
        []))
    []))

(def available-tos-functions
  (memo/ttl get-tos-functions-from-toj
            :ttl/threshold 10000))

(defn- get-metadata-for-document-from-toj [organization code {:keys [type-group type-id]}]
  (if (env/feature? :tiedonohjaus)
    (when (and organization code type-group type-id)
      (try
        (let [url (build-url "/tiedonohjaus/api/org/" organization "/asiat/" code "/document/" (str (name type-group) "." (name type-id)))
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
