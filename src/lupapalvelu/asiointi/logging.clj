(ns lupapalvelu.asiointi.logging
  (:require [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [sade.strings :as ss])
  (:import [java.util Date]))

(set! *warn-on-reflection* true)

(defn- preprocess-request
  [request]
  (let [ignored-headers #{"authorization" "cookie"}
        stripped        (-> request
                            (select-keys #{:remote-addr :request-method :uri
                                           :form-params :query-params :path-params :body-params
                                           :query-string
                                           :headers})
                            (update :headers (fn [headers]
                                               (->> headers
                                                    (remove (fn [[k _]] (some (fn [h] (ss/=trim-i h k)) ignored-headers)))
                                                    (into {})))))]
    (->> stripped
         (filter (fn [[_k v]]
                   (if (coll? v)
                     (not-empty v)
                     (some? v))))
         (into {}))))

(defn default-commit!
  "Store finished log entries into Mongo"
  [entry]
  (try
    (let [entry (assoc entry :stored-at (Date.))]
      (mongo/insert :asiointiapi-logging-entries entry))
    (catch Exception e
      (log/error e "Failed to log AsiointiAPI request"))))

(def middleware
  "Capture requests and responses and commit them using the provided fn.
  Attaches a `::logger` into the request for other handlers."
  {:name ::logging
   :wrap (fn [handler commit!]
           (fn [request]
             (let [entry-id (mongo/create-object-id)
                   *entry   (atom {:id          entry-id
                                   :received-at (Date.)
                                   :request     (preprocess-request request)})
                   ;; Allow others to enrich the context
                   logger   (fn logger-fn [m]
                              (swap! *entry update :context (fnil merge {}) m))]
               (log/with-logging-context {:entryId entry-id}
                 (try
                   (let [response (handler (assoc request ::logger logger))]
                     (swap! *entry assoc :response response)
                     (commit! (deref *entry))
                     response)
                   (catch Exception e
                     (logger {:error-message (.getMessage e)})
                     (commit! (deref *entry))
                     (throw e)))))))})
