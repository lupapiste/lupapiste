(ns lupapalvelu.oauth.core
  (:require [clojure.set :as set]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.token :as token]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.http :as http]
            [sade.strings :as ss]
            [sade.util :as util]))

(def MINUTE (* 60 1000))
(def HOUR   (* 60 MINUTE))

(defn check-user
  "Returns error loc-key if the current user is not valid for the client."
  [{:keys [user scope-vec]}]
  (when (and user
             (contains? (set scope-vec) "pay")
             (nil? (some-> user :company :id)))
    :oauth.warning.company-pay-only))

(defn grant-access-token [client scopes user & [expires-in]]
  (token/make-token :oauth-access
                    user
                    {:client-id (get-in client [:oauth :client-id])
                     :scopes scopes}
                    :ttl
                    (or expires-in
                        (* (get-in client [:oauth :token-minutes] 10)
                           MINUTE))
                    :auto-consume
                    false))

(defn grant-authorization-code [client scopes user]
  (token/make-token :oauth-code
                    user
                    {:client-id (get-in client [:oauth :client-id])
                     :scopes scopes}
                    :ttl MINUTE
                    :auto-consume true))

(defn grant-refresh-token [client scopes user & [expires-in]]
  (token/make-token :oauth-refresh
                    user
                    {:client-id (get-in client [:oauth :client-id])
                     :scopes    scopes}
                    :ttl (or expires-in
                             (+ (* (get-in client [:oauth :token-minutes] 10)
                                   MINUTE)
                                (* 2 HOUR)))
                    :auto-consume true))

(defn access-token-response [client-id client-secret code]
  (when-let [client (usr/get-user {:oauth.client-id     client-id
                                   :oauth.client-secret client-secret})]
    (let [{:keys [token-type user-id data]} (token/get-usable-token code)]
      (when (and (= token-type :oauth-code)
                 (= (:client-id data) client-id))
        (let [expires-in    (* 10 MINUTE)
              scopes        (:scopes data)
              user          {:id user-id}
              token         (grant-access-token client scopes user expires-in)
              refresh-token (grant-refresh-token client scopes user (* 6 expires-in))]
          {:access_token  token
           :refresh_token refresh-token
           :token_type    "bearer"
           :expires_in    expires-in
           :scope         (ss/join "," scopes)})))))

(defn user-for-access-token [request]
  (when-let [token-id (http/parse-bearer request)]
    (when-let [{:keys [token-type user-id data]} (token/get-usable-token token-id)]
      (when (= token-type :oauth-access)
        (when-let [{:keys [oauth-roles] :as user} (usr/get-user-by-id user-id)]
          (-> user
              (assoc :scopes (map keyword (:scopes data))
                     :oauth-role (some->> oauth-roles
                                          (util/find-by-key :client-id (:client-id data))
                                          :role))
              util/strip-nils))))))

(defn refresh-token-response
  "Invalidates the given refresh token and returns new access and refresh tokens. This is done in
  order to deter the cloning of refresh tokens, since every refresh token can be used only once. See
  RFC6749 10.4. for more."
  [client-id client-secret token-id]
  (let [{:keys [enabled]
         :as   client} (usr/get-user {:oauth.client-id     client-id
                                      :oauth.client-secret client-secret})
        {:keys [token-type user-id
                data]} (token/get-usable-token token-id)
        scopes         (:scopes data)]
    (when (and enabled
               (= token-type :oauth-refresh)
               (= client-id (:client-id data)))
      (let [expires-in    (* 10 MINUTE)
            user          {:id user-id}
            token         (grant-access-token client scopes user expires-in)
            refresh-token (grant-refresh-token client scopes user)]
        {:access_token  token
         :expires_in    expires-in
         :refresh_token refresh-token
         :token_type    "bearer"}))))

(defn client-id [{:keys [client]}]
  {:post [(ss/not-blank? %)]}
  (some-> client :oauth :client-id))

(defn- client-consent
  "User consent item for the client or nil."
  [{:keys [user] :as fields}]
  (util/find-by-key :client-id (client-id fields) (:oauth-consent user)))

(defn consented?
  "True if the `user` has already consented for _every_ `client` scope in `scope-vec`"
  [{:keys [scope-vec] :as fields}]
  (empty? (set/difference (set scope-vec) (some-> (client-consent fields) :scopes set))))

(defn add-consent
  "Registers user's consent for the OAuth `client` for the current
  scopes. Returns (unchanged) `fields`"
  [{:keys [user client scope-vec] :as fields}]
  {:pre [user client (not-empty scope-vec)]}
  (if (client-consent fields)
    (mongo/update-by-query :users
                           {:_id           (:id user)
                            :oauth-consent {$elemMatch {:client-id (client-id fields)}}}
                           {$addToSet {:oauth-consent.$.scopes {$each scope-vec}}})
    (mongo/update-by-id :users (:id user)
                        {$push {:oauth-consent {:client-id (client-id fields)
                                                :scopes    (set scope-vec)}}}))
  fields)
