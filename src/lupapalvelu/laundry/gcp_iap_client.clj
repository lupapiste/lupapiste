(ns lupapalvelu.laundry.gcp-iap-client
  (:require [lupapalvelu.logging :as log]
            [lupapiste-pubsub.bootstrap :as pubsub-bootstrap]
            [sade.env :as env])
  (:import [com.google.api.gax.core CredentialsProvider GoogleCredentialsProvider]
           [com.google.auth.oauth2 IdTokenCredentials IdTokenProvider ServiceAccountCredentials]
           [java.util Collection]))

(set! *warn-on-reflection* true)

(def ^String iam-scope "https://www.googleapis.com/auth/iam")
(def ^Collection scopes [iam-scope])

(def ^CredentialsProvider credentials-provider
  (if-let [service-account-file (env/value :gcs :service-account-file)]
    (pubsub-bootstrap/fixed-credentials-provider service-account-file)
    (-> (GoogleCredentialsProvider/newBuilder)
        (.setScopesToApply ["https://www.googleapis.com/auth/cloud-platform"])
        (.build))))

(defn- get-id-token-provider ^IdTokenProvider []
  (let [^IdTokenProvider credentials (-> ^ServiceAccountCredentials (.getCredentials credentials-provider)
                                         (.createScoped scopes))]
    (when (nil? credentials)
      (log/error "Could not load credentials for IAP Bearer Authorization"))
    (when-not (instance? IdTokenProvider credentials)
      (log/errorf "Expected credentials that can provide id tokens, got %s instead"
                  (-> credentials (.getClass) (.getName))))
    credentials))

(def credentials-cache (atom {}))

(defn- get-credentials ^IdTokenCredentials [jwt-aud-claim]
  (or (get @credentials-cache jwt-aud-claim)
      (let [credentials (-> (IdTokenCredentials/newBuilder)
                            (.setIdTokenProvider (get-id-token-provider))
                            (.setTargetAudience jwt-aud-claim)
                            (.build))]
        (swap! credentials-cache assoc jwt-aud-claim credentials)
        credentials)))

(defn get-id-token [jwt-aud-claim]
  (let [credentials (get-credentials jwt-aud-claim)]
    (.refreshIfExpired credentials)
    (some-> credentials
            (.getIdToken)
            (.getTokenValue))))
