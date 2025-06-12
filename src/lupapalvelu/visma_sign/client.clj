(ns lupapalvelu.visma-sign.client
  (:require [clojure.core.async :refer [<! go-loop timeout]]
            [hato.client :as http]
            [lupapalvelu.crypto :refer [calculate-md5-base64 hmac-sha512]]
            [lupapalvelu.json :as json]
            [lupapalvelu.visma-sign.process :as p]
            [sade.core :refer [fail fail! now]]
            [sade.strings :as ss])
  (:import [java.io ByteArrayInputStream]
           [java.text SimpleDateFormat]
           [java.util Date]
           [org.apache.commons.io IOUtils]))

(set! *warn-on-reflection* true)

(defn format-date []
  (let [date-format (SimpleDateFormat. "EEE, dd MMM yyyy HH:mm:ss Z")]
    (.format date-format (Date.))))

(defn validate-config [{:keys [system]}]
  {:pre [(map? system)]}
  (let [c (-> system :config :visma-sign)]
    (when-not (:base-url c)          (fail :error.missing-env-config :errorParams "visma-sign.base-url"))
    (when-not (:secret c)            (fail :error.missing-env-config :errorParams "visma-sign.secret"))
    (when-not (:client-identifier c) (fail :error.missing-env-config :errorParams "visma-sign.client-identifier"))))

(defn- create-request
  "Creates a Hato request map"
  [options]
  (let [{:keys [method body content-type as]
         :or   {method       :get
                body         ""
                content-type "application/json"
                as           :string}} options]
    {:method           method
     :headers          {"content-type" content-type}
     :as               as
     :body             body
     ;; This is not optimal but setting ':throw-exceptions true' will cause hato to expose auth header secrets
     ;; https://github.com/gnarroway/hato/issues/43
     :throw-exceptions false}))

(defn- with-url [request config path]
  (assoc request :url (str (:base-url config) path)))

(defn- with-content-md5-header [request body]
  (update request :headers merge {"Content-MD5" (calculate-md5-base64 body)}))

(defn- with-date [request]
  (update request :headers merge {"Date" (format-date)}))

(defn- with-auth-header
  "Creates and adds the authorization header required by Visma sign"
  [request config path]
  (let [method-str   (-> request :method name ss/upper-case)
        content-type (get-in request [:headers "content-type"])
        auth-string  (ss/join "\n" [method-str
                                    (get-in request [:headers "Content-MD5"])
                                    content-type
                                    (get-in request [:headers "Date"])
                                    path])
        signature    (hmac-sha512 auth-string (:secret config))]
    (update request :headers merge
            {"Authorization" (format "Onnistuu %s:%s" (:client-identifier config) signature)})))

(defn- with-visma-sign-headers
  "Adds Amazon-style HMAC authentication headers to a hato request map for Visma Sign API"
  [request config path]
  (let [body (:body request)
        body (cond
               (map? body)    (fail! "Body of request should be a String or Byte Array not map")
               (string? body) (.getBytes ^String body)
               :else          body)]
    (-> request
        (with-content-md5-header body)
        (with-date)
        (with-auth-header config path))))

(defn create-visma-request
  "Creates a Hato request map for the Visma Sign API"
  ([system endpoint]
   (create-visma-request system endpoint {}))
  ([system endpoint options]
   {:pre [(map? system)]}
   (let [config (-> system :config :visma-sign)
         path   (str "/api/v1" endpoint)]
     (-> (create-request options)
         (with-url config path)
         (with-visma-sign-headers config path)))))

(defn- handle-error [res]
  (let [message (-> res :body json/decode)]
    (case (int (:status res))
      400 (fail! :error.bad-request {:body message})
      403 (fail! :error.authentication-required {:body message})
      404 (fail! :error.not-found {:body message})
      (fail! :error.unexpected-response
             {:status (:status res)
              :body   message}))))

(defn create-doc
  "Creates a Document in Visma Sign return the document Id"
  [system doc-name]
  {:pre [(map? system)]}
  (let [req (create-visma-request system
                                  "/document/"
                                  {:method :post
                                   :body   (json/encode {:document {:name          doc-name
                                                                    :category_uuid "1a1d6e81-ecf6-4d2b-b8c2-3c08051e3c47"
                                                                    :category      "Yritystili-Sopimukset"}})})
        res (http/request req)]
    (if (= (:status res) 201)
      (-> res :headers (get "location"))
      (handle-error res))))

(defn create-invite
  "Creates a new invite in Visma Sign and returns the invitation url"
  [system uuid lang]
  {:pre [(map? system)]}
  (let [req (create-visma-request system
                                  (str "/document/" uuid "/invitations")
                                  {:method :post
                                   :body   (json/encode [{}])})
        res (http/request req)]
    (if (= (:status res) 201)
      (let [invitation            (-> res :body json/decode first)
            invitation-uuid       (get invitation "uuid")
            invitation-passphrase (get invitation "passphrase")]
        [invitation-uuid (str "/" lang "/invitation/" invitation-uuid "/" invitation-passphrase)])
      (handle-error res))))

(defn add-file
  "Adds a file to a Visma Sign document"
  [system uuid file-name file]
  {:pre [(map? system)]}
  (let [file (IOUtils/toByteArray ^ByteArrayInputStream file)
        req  (create-visma-request
               system
               (str "/document/" uuid "/files?filename=" file-name)
               {:method       :post
                :content-type "application/pdf"
                :body         file})
        res  (http/request req)]
    (if (= (:status res) 201)
      (:body res)
      (handle-error res))))

(defn- get-doc
  "Get a Visma Sign document by Id"
  [system uuid]
  (let [req (create-visma-request system (str "/document/" uuid))
        res (http/request req)]
    (if (= (:status res) 200)
      (-> res :body (json/decode true))
      (handle-error res))))

(defn- get-file
  "Get a file from a Visma Sign document by document Id"
  [system uuid]
  (let [req (create-visma-request system
                                  (str "/document/" uuid "/files/0")
                                  {:as :byte-array})
        res (http/request req)]
    (if (= (:status res) 200)
      (:body res)
      (handle-error res))))

(defn poll-visma-doc-state
  "Polls Vismas Sign API until a document is signed/cancelled or after the timeout has run out

  Document statuses:
  new       - The document has been added to Visma Sign, but no invitations have been created for it.
  pending   - The document has unfulfilled invitations.
  signed    - All the invitations have been fulfilled and the document is complete.
  deleted   - The document was deleted before any invitations were created for it.
  cancelled - The document and related invitations have been cancelled. "
  [system process-id max-attempts timeout-interval]
  {:pre [(map? system)]}
  (let [process (p/find-sign-process! process-id)
        doc-id  (:doc-id process)]
    (go-loop [attempts 0]
      (if (> attempts max-attempts)
        (p/failed! process-id "timeout" "Timeout reached" (now))
        (let [document (get-doc system doc-id)
              status   (:status document)]
          (case status
            "signed"    (let [signed-pdf (get-file system doc-id)]
                          (p/success! system process-id document signed-pdf (now)))
            "deleted"   (p/failed! process-id status "Document was deleted" (now))
            "cancelled" (p/failed! process-id status "Document signing cancelled" (now))
            (do (let [process (p/find-sign-process! process-id)]
                  (when-not (= status (:status process))
                    (p/update-status process-id (keyword status) (now))))
                (<! (timeout timeout-interval))
                (recur (inc attempts)))))))))
