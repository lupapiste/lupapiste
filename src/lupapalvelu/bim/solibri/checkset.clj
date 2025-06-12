(ns lupapalvelu.bim.solibri.checkset
  "Performs validation and compliance checking for BIM ICF files by sending the URL's for them
  and the chosen checkset CSET files to Solibri."
  (:require [clojure.walk :as walk]
            [lupapalvelu.bim.checking-plan.schema :as bim-cp-schema]
            [lupapalvelu.bim.solibri.schema :as solibri-schema]
            [lupapalvelu.json :as json]
            [lupapalvelu.linked-files :as linked-files]
            [lupapalvelu.logging :as log :refer [error errorf infof]]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.storage.file-utils :as file-utils]
            [lupapalvelu.storage.object-storage :as object-storage]
            [lupapalvelu.token :as token]
            [monger.operators :refer :all]
            [sade.core :as sade :refer [fail!]]
            [sade.env :as env]
            [sade.http :as http]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [schema.core :as sc]
            [lupapalvelu.system.util-components :as components]))

(defn- validate-config!
  "Checks that all required values for Solibri integration have been given"
  []
  (when-not (every? #(apply env/value %)
                    [[:solibri :api-base-url]
                     [:solibri :api-key]
                     [:solibri :api-token]])
    (fail! :error.solibri-not-configured)))

(defn- checkset-download-urls
  "Returns a list of the links for downloading the checkset (.CSET) files, uploading them to storage if
  they are not there yet."
  [checking-plans]
  (mapv (fn [{{:keys [bucket object-key]} :file}]
          (storage/create-download-link bucket object-key 600))
        checking-plans))

(defn- ifc-download-url
  "Find the latest version of the IFC file and return a download link for it"
  [model-object-key]
  (storage/create-download-link object-storage/application-bucket model-object-key 600))

(defn- webhook-url
  [system]
  (let [token-id (token/make-token :bim-checking nil nil :ttl 1800000) ; ttl = 30 minutes
        host     (components/system->host system)
        base-url (or (env/value :solibri :webhook-url)
                     (str host "/api/check-model-webhook"))]
    (when (ss/includes? base-url "localhost")
      (error "BIM webhook refers to localhost and won't work; use an external webhook when developing locally"))
    (str base-url "?tokenId=" token-id)))

(defn- solibri-check-job-request-body [system model-object-key checking-plans]
  {:csets      (checkset-download-urls checking-plans)
   :ifcFiles   [{:url (ifc-download-url model-object-key)}]
   :webhookUrl (webhook-url system)
   :resultLang "fi"})

(defn- solibri-auth-opts []
  {:headers     {"x-api-key"  (env/value :solibri :api-key)
                 "User-Agent" "Cloudpermit"}
   :oauth-token (env/value :solibri :api-token)})

(defn- solibri-checking-url [& path]
  (validate-config!)
  (->> (map name path)
       (into ["/api/check/jobs"])
       (ss/join "/")
       (str (env/value :solibri :api-base-url))))

(sc/defn create-solibri-checking-job :- solibri-schema/CheckingJobResponse
  [system
   model-object-key :- sc/Str
   checking-plans :- [bim-cp-schema/CheckingPlan]]
  (let [req-body (solibri-check-job-request-body system model-object-key checking-plans)]
    (infof "Creating Solibri check job for BIM file %s: %s" model-object-key req-body)
    (:body (http/post-as-json (solibri-checking-url)
                              (merge {:body req-body}
                                     (solibri-auth-opts))))))

(defn get-solibri-checking-result [job-id]
  (infof "Getting Solibri check result for %s" job-id)
  (walk/keywordize-keys (http/get-json-body (solibri-checking-url job-id :result :json)
                                            (solibri-auth-opts))))

(defn get-solibri-checking-result-bcf-url [job-id]
  (let [{:keys [status body]} (http/get-json (solibri-checking-url job-id :result :bcf)
                                             (solibri-auth-opts))]
    (if (= status 200)
      (get body "url")
      (infof "Tried to download Solibri BCF check result for %s but it is not available." job-id))))

(defn get-solibri-bcf-file-content [job-id]
  (when-let [url (get-solibri-checking-result-bcf-url job-id)]
    (let [{:keys [status body headers length]} (http/get url {:as :stream})]
      (if (= status 200)
        {:content     (constantly body)
         :contentType (get headers "Content-Type")
         :size        length}
        (infof "Tried to download Solibri BCF file for %s but it is not available." job-id)))))

(def stored-result-content-type "application/transit+json")

(sc/defn database-result :- linked-files/LinkedFileBimResult
  "Returns a map of the values that can be stored in the database for the given check result"
  [external-job-id check-result other-values]
  (merge {:external-job-id external-job-id
          :integration     "solibri"
          :timestamp       (sade/now)
          :tests-count     (-> check-result :rules count)
          :tests-passed    (->> check-result :rules (remove #(-> % :status (= "FAILED"))) count)}
         other-values))

(sc/defn get-and-upload-checking-result :- linked-files/LinkedFileBimResult
  [external-job-id :- sc/Str
   application-id :- ssc/ApplicationId]
  (let [check-result (get-solibri-checking-result external-job-id)
        file-id      (str (random-uuid))
        {:keys [object-key bucket]} (storage/upload file-id
                                                    (str application-id "-" external-job-id "-check-result.json")
                                                    stored-result-content-type
                                                    (-> check-result json/encode-transit file-utils/->gzip)
                                                    {:application application-id})]
    (infof "Solibri check result for %s uploaded at %s %s" external-job-id bucket object-key)
    (database-result external-job-id check-result {:bucket bucket :result-file-id file-id})))

(sc/defn handle-checking-failure :- linked-files/LinkedFileBimResult
  [external-job-id :- sc/Str
   status :- sc/Str]
  (let [check-result (get-solibri-checking-result external-job-id)]
    (log/with-logging-context {:solibri-check-result check-result}
      (errorf "BIM integration webhook job %s failed with status %s" external-job-id status))
    (database-result external-job-id nil {:check-failed true})))
