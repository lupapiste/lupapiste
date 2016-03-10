(ns lupapalvelu.tiedonohjaus
  (:require [sade.http :as http]
            [sade.env :as env]
            [clojure.core.memoize :as memo]
            [taoensso.timbre :refer [trace debug debugf info infof warn warnf error fatal]]
            [lupapalvelu.organization :as o]
            [lupapalvelu.action :as action]
            [monger.operators :refer :all]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [sade.util :as util]
            [lupapalvelu.domain :as domain]))

(defn- build-url [& path-parts]
  (apply str (env/value :toj :host) path-parts))

(defn- get-tos-functions-from-toj [organization-id]
  (if (:permanent-archive-enabled (o/get-organization organization-id))
    (try
      (let [url (build-url "/tiedonohjaus/api/org/" organization-id "/asiat")
            response (http/get url {:as               :json
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

(defn- get-metadata-for-document-from-toj [organization tos-function document-type]
  (if (and organization tos-function document-type)
    (try
      (let [doc-id (if (map? document-type) (str (name (:type-group document-type)) "." (name (:type-id document-type))) document-type)
            url (build-url "/tiedonohjaus/api/org/" organization "/asiat/" tos-function "/document/" doc-id)
            response (http/get url {:as               :json
                                    :throw-exceptions false})]
        (if (= 200 (:status response))
          (:body response)
          {}))
      (catch Exception _
        {}))
    {}))

(def metadata-for-document
  (memo/ttl get-metadata-for-document-from-toj
            :ttl/threshold 10000))

(defn- get-metadata-for-process-from-toj [organization tos-function]
  (if (and organization tos-function)
    (try
      (let [url (build-url "/tiedonohjaus/api/org/" organization "/asiat/" tos-function)
            response (http/get url {:as               :json
                                    :throw-exceptions false})]
        (if (= 200 (:status response))
          (:body response)
          {}))
      (catch Exception _
        {}))
    {}))

(def metadata-for-process
  (memo/ttl get-metadata-for-process-from-toj
            :ttl/threshold 10000))

(defn- paatospvm-plus-years [verdicts years]
  (when-let [paatos-ts (->> verdicts
                            (map (fn [{:keys [paatokset]}]
                                   (map (fn [pt] (map :paatospvm (:poytakirjat pt))) paatokset)))
                            (flatten)
                            (remove nil?)
                            (sort)
                            (last))]
    (-> (c/from-long paatos-ts)
        (t/plus (t/years years))
        (.toDate))))

(defn- retention-end-date [{{:keys [arkistointi pituus]} :sailytysaika} verdicts]
  (when (= (keyword "m\u00E4\u00E4r\u00E4ajan") (keyword arkistointi))
    (paatospvm-plus-years verdicts pituus)))

(defn- security-end-date [{:keys [salassapitoaika julkisuusluokka]} verdicts]
  (when (and (#{:osittain-salassapidettava :salainen} (keyword julkisuusluokka)) salassapitoaika)
    (paatospvm-plus-years verdicts salassapitoaika)))

(defn update-end-dates [metadata verdicts]
  (let [retention-end (retention-end-date metadata verdicts)
        security-end (security-end-date metadata verdicts)]
    (cond-> (-> (util/dissoc-in metadata [:sailytysaika :retention-period-end])
                (dissoc :secrecy-period-end))
            retention-end (assoc-in [:sailytysaika :retention-period-end] retention-end)
            security-end (assoc :security-period-end security-end))))

(defn document-with-updated-metadata [{:keys [metadata] :as document} organization tos-function application & [type]]
  (let [document-type (or type (:type document))
        existing-tila (:tila metadata)
        existing-nakyvyys (:nakyvyys metadata)
        new-metadata (metadata-for-document organization tos-function document-type)
        processed-metadata (cond-> new-metadata
                                   existing-tila (assoc :tila (keyword existing-tila))
                                   true (update-end-dates (:verdicts application))
                                   (and (not (:nakyvyys new-metadata)) existing-nakyvyys) (assoc :nakyvyys existing-nakyvyys))]
    (assoc document :metadata processed-metadata)))

(defn- get-tos-toimenpide-for-application-state-from-toj [organization tos-function state]
  (if (and organization tos-function state)
    (try
      (let [url (build-url "/tiedonohjaus/api/org/" organization "/asiat/" tos-function "/toimenpide-for-state/" state)
            response (http/get url {:as               :json
                                    :throw-exceptions false})]
        (if (= 200 (:status response))
          (:body response)
          {}))
      (catch Exception _
        {}))
    {}))

(def toimenpide-for-state
  (memo/ttl get-tos-toimenpide-for-application-state-from-toj
            :ttl/threshold 10000))

(defn- full-name [{:keys [lastName firstName]}]
  (str lastName " " firstName))

(defn- get-documents-from-application [application]
  [{:type     :hakemus
    :category :document
    :ts       (:created application)
    :user     (:applicant application)}])

(defn- get-attachments-from-application [application]
  (reduce (fn [acc attachment]
            (if-let [versions (seq (:versions attachment))]
              (->> versions
                   (map (fn [ver]
                          {:type     (:type attachment)
                           :category :attachment
                           :version  (:version ver)
                           :ts       (:created ver)
                           :contents (:contents attachment)
                           :user     (full-name (:user ver))}))
                   (concat acc))
              acc))
          []
          (:attachments application)))

(defn- get-statement-requests-from-application [application]
  (map (fn [stm]
         {:type     (get-in stm [:person :text])
          :category :request-statement
          :ts       (:requested stm)
          :user     (str "" (:name stm))}) (:statements application)))

(defn- get-neighbour-requests-from-application [application]
  (map (fn [req] {:type     (get-in req [:owner :name])
                  :category :request-neighbor
                  :ts       (:created (first (filterv #(= "open" (name (:state %))) (:status req))))
                  :user     (full-name (:user req))}) (:neighbors application)))

(defn- get-review-requests-from-application [application]
  (reduce (fn [acc task]
            (if (= "task-katselmus" (name (get-in task [:schema-info :name])))
              (conj acc {:type     (:taskname task)
                           :category :request-review
                           :ts       (:created task)
                           :user      (full-name (:assignee task))})
              acc)) [] (:tasks application)))


(defn- get-held-reviews-from-application [application]
  (reduce (fn [acc task]
              (if-let [held (get-in task [:data :katselmus :pitoPvm :modified])]
              (conj acc {:type     (:taskname task)
                         :category :review
                         :ts       held  ;; TODO: What timestamp should this use ?
                         :user      (full-name (:assignee task))})
              acc)) [] (:tasks application)))

(defn generate-case-file-data [application]
  (let [documents (get-documents-from-application application)
        attachments (get-attachments-from-application application)
        statement-reqs (get-statement-requests-from-application application)
        neighbors-reqs (get-neighbour-requests-from-application application)
        review-reqs (get-review-requests-from-application application)
        reviews-held (get-held-reviews-from-application application)
        all-docs (sort-by :ts (concat documents attachments statement-reqs neighbors-reqs review-reqs reviews-held))]
    (map (fn [[{:keys [state ts user]} next]]
           (let [api-response (toimenpide-for-state (:organization application) (:tosFunction application) state)
                 ;; TODO: Muutospyyntö state not in toj
                 action-name (or (:name api-response) "Ei asetettu tiedonohjaussuunnitelmassa")]
             {:action    action-name
              :start     ts
              :user      (full-name user)
              :documents (filter (fn [{doc-ts :ts}]
                                   (and (>= doc-ts ts) (or (nil? next) (< doc-ts (:ts next)))))
                                 all-docs)}))
         (partition 2 1 nil (:history application)))))

(defn- document-metadata-final-state [metadata verdicts]
  (-> (assoc metadata :tila :valmis)
      (update-end-dates verdicts)))

(defn mark-attachment-final! [{:keys [attachments verdicts] :as application} now attachment-or-id]
  (let [{:keys [id metadata]} (if (map? attachment-or-id)
                                attachment-or-id
                                (first (filter #(= (:id %) attachment-or-id) attachments)))]
    (when (seq metadata)
      (let [new-metadata (document-metadata-final-state metadata verdicts)]
        (when-not (= metadata new-metadata)
          (action/update-application
            (action/application->command application)
            {:attachments.id id}
            {$set {:modified               now
                   :attachments.$.metadata new-metadata}}))))))

(defn mark-app-and-attachments-final! [app-id modified-ts]
  (let [{:keys [metadata attachments verdicts] :as application} (domain/get-application-no-access-checking app-id)]
    (when (seq metadata)
      (let [new-metadata (document-metadata-final-state metadata verdicts)]
        (when-not (= metadata new-metadata)
          (action/update-application
            (action/application->command application)
            {$set {:modified modified-ts
                   :metadata new-metadata}}))
        (doseq [attachment attachments]
          (mark-attachment-final! application modified-ts attachment))))))
