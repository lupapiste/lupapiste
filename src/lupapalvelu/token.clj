(ns lupapalvelu.token
  (:require [lupapalvelu.logging :refer [errorf]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]
            [lupapalvelu.ttl :as ttl]
            [monger.operators :refer :all]
            [noir.request :as request]
            [sade.core :refer [now]]
            [sade.http :as http]))

(defmulti handle-token (fn [token-data _params _system] (:token-type token-data)))

(defmethod handle-token :default [token-data _ _]
  (errorf "consumed token: token-type %s does not have handler: id=%s" (:token-type token-data) (:_id token-data)))

(def make-token-id (partial security/random-password 48))

(defn make-token [token-type user data & {:keys [ttl auto-consume] :or {ttl ttl/default-ttl auto-consume true}}]
  (let [token-id (make-token-id)
        now      (now)
        request  (request/ring-request)]
    (mongo/insert :token {:_id          token-id
                          :token-type   token-type
                          :data         data
                          :used         nil
                          :auto-consume auto-consume
                          :created      now
                          :expires      (+ now ttl)
                          :ip           (http/client-ip request)
                          :user-id      (:id (or user (:user request)))})
    token-id))

(defn- keywordize-token-type [token]
  (update-in token [:token-type] keyword))

(defn get-token [id & {:keys [consume] :or {consume false}}]
  (if-let [token (mongo/select-one :token {:_id id})]
    (cond
      (:used token) [:used (keywordize-token-type token)]
      (< (:expires token) (now)) [:expired (keywordize-token-type token)]
      :else (do
              (when (or consume (:auto-consume token))
                (mongo/update-by-id :token id {$set {:used (now)}}))
              [:usable (keywordize-token-type token)]))
    [:not-found]))

(defn get-usable-token [id & {:keys [consume] :or {consume false}}]
  (let [[status token] (get-token id :consume consume)]
    (when (= status :usable)
      token)))

(defn consume-token [system id params & {:keys [consume] :or {consume false}}]
  {:pre [(map? system)]}
  (let [[status token] (get-token id :consume consume)]
    (case status
      :usable [:usable (handle-token token params system)]
      [status])))

(defn refresh-token [token-id expiration-ttl]
  (mongo/update-by-id :token token-id {$set {:expires (+ (now) expiration-ttl)}}))

(defn generate-or-refresh-map-token [user]
  (let [token (mongo/select-one
                :token
                {:token-type   "map-3d-token"
                 :user-id      (:id user)
                 :expires      {$gte (now)}})]
    (if token
      (do
        (refresh-token (:id token) ttl/map-token-ttl)
        (:id token))
      (make-token
        :map-3d-token
        user
        {}
        :auto-consume false
        :ttl ttl/map-token-ttl))))

(defn count-password-reset-tokens-by-email [email max-age-milliseconds]
  (let [created-after (- (now) max-age-milliseconds)]
    (mongo/count :token {:token-type "password-reset"
                         :created {$gte created-after}
                         :data {:email email}})))
