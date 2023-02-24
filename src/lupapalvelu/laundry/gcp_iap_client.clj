(ns lupapalvelu.laundry.gcp-iap-client
  (:require [taoensso.timbre :as timbre]
            [sade.env :as env]
            [lupapiste-pubsub.bootstrap :as pubsub-bootstrap])
  (:import [com.google.auth.oauth2 IdTokenProvider IdTokenCredentials]
           [com.google.api.gax.core GoogleCredentialsProvider]))

(def iam-scope "https://www.googleapis.com/auth/iam")

(def credentials-provider
  (if-let [service-account-file (env/value :gcs :service-account-file)]
    (pubsub-bootstrap/fixed-credentials-provider service-account-file)
    (-> (GoogleCredentialsProvider/newBuilder)
        (.setScopesToApply ["https://www.googleapis.com/auth/cloud-platform"])
        (.build))))

(defn- ^IdTokenProvider get-id-token-provider []
  (let [credentials (-> (.getCredentials credentials-provider)
                        (.createScoped [iam-scope]))]
    (when (nil? credentials)
      (timbre/error "Could not load credentials for IAP Bearer Authorization"))
    (when-not (instance? IdTokenProvider credentials)
      (timbre/errorf "Expected credentials that can provide id tokens, got %s instead"
                     (-> credentials (.getClass) (.getName))))
    credentials))

(def credentials-cache (atom {}))

(defn- ^IdTokenCredentials get-credentials [jwt-aud-claim]
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
