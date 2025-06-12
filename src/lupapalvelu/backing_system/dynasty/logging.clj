(ns lupapalvelu.backing-system.dynasty.logging
  (:require [clojure.walk :as walk]
            [lupapalvelu.backing-system.dynasty.api.client-proto :as client-proto]
            [lupapalvelu.integrations.messages :as imessages]
            [lupapalvelu.mongo :as mongo]
            [sade.core :as sade])
  (:import [java.io File]))

(set! *warn-on-reflection* true)

(defn- drop-files
  [m]
  (walk/postwalk
    (fn [form]
      (if (instance? File form)
        (format "File:[%s]" form)
        form))
    m))

(defn- persist-request!
  [{:keys [operation application user]} request]
  (imessages/save
    {:id           (mongo/create-id)
     :messageType  (name operation)
     :direction    "out"
     :transferType "http"
     :partner      "dynasty"
     :format       "clojure"
     :created      (sade/now)
     :status       "published"
     :initiator    (select-keys user [:id :username])
     :application  (select-keys application [:id :organization])
     :data         (-> request
                       (dissoc :auth)
                       drop-files)}))

(defn- persist-response!
  "Note that the client returns just the response body (if any), not the whole response."
  [{:keys [operation application user]} response-body]
  (imessages/save
    (merge
      {:id           (mongo/create-id)
       :messageType  (name operation)
       :direction    "in"
       :transferType "http"
       :partner      "dynasty"
       :format       "clojure"
       :created      (sade/now)
       :status       "received"
       :initiator    (select-keys user [:id :username])
       :application  (select-keys application [:id :organization])}
      (when response-body
        {:data response-body}))))

(defn middleware
  "Wrap a `DynastyClient` instance in a request+response persisting middleware"
  [client application user]
  (let [data {:application application
              :user        user}]
    (reify client-proto/DynastyClient
      (-get [_ opts]
        (let [data (assoc data :operation :get)]
          (persist-request! data opts)
          (let [resp (client-proto/get! client opts)]
            (persist-response! data resp)
            resp)))
      (-post [_ opts]
        (let [data (assoc data :operation :post)]
          (persist-request! data opts)
          (let [resp (client-proto/post! client opts)]
            (persist-response! data resp)
            resp))))))
