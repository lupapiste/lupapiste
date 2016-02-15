(ns lupapalvelu.archiving
  (:require [sade.http :as http]
            [sade.env :as env]
            [cheshire.core :as json]
            [lupapiste-commons.tos-metadata-schema :as tms]
            [schema.core :as s]
            [lupapalvelu.tiedonohjaus :as tiedonohjaus]
            [lupapalvelu.pdf.pdf-export :as pdf-export]
            [clojure.java.io :as io]
            [lupapalvelu.attachment]
            [ring.util.codec :as codec]
            [lupapalvelu.action :as action]
            [monger.operators :refer :all]
            [taoensso.timbre :refer [info error warn]]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [clj-time.format :as f])
  (:import (java.util.concurrent ThreadFactory Executors)
           (java.io File)))

(defn thread-factory []
  (let [security-manager (System/getSecurityManager)
        thread-group (if security-manager
                       (.getThreadGroup security-manager)
                       (.getThreadGroup (Thread/currentThread)))]
    (reify
      ThreadFactory
      (newThread [this runnable]
        (doto (Thread. thread-group runnable "archive-upload-worker")
          (.setDaemon true)
          (.setPriority Thread/NORM_PRIORITY))))))

(defonce upload-threadpool (Executors/newFixedThreadPool 1 (thread-factory)))

(defonce unfinished-uploads (atom {}))

(defn- build-url [id]
  (let [host (env/value :arkisto :host)
        app-id (env/value :arkisto :app-id)
        app-key (env/value :arkisto :app-key)
        encoded-id (codec/url-encode id)]
    (str host "/documents/" encoded-id "?app-id=" app-id "&app-key=" app-key)))

(defn- upload-file [id is-or-file content-type metadata]
  (http/put (build-url id) {:multipart        [{:name      "metadata"
                                                :mime-type "application/json"
                                                :encoding  "UTF-8"
                                                :content   (json/generate-string metadata)}
                                               {:name      "file"
                                                :content   is-or-file
                                                :mime-type content-type}]}))

(defn- set-attachment-state [application now id]
  (action/update-application
    (action/application->command application)
    {:attachments.id id}
    {$set {:modified now
           :attachments.$.modified now
           :attachments.$.metadata.tila :arkistoitu}}))

(defn- set-application-state [application now _]
  (action/update-application
    (action/application->command application)
    {$set {:modified now
           :metadata.tila :arkistoitu}}))

(defn- upload-and-set-state [id is-or-file content-type metadata {app-id :id :as application} now state-update-fn]
  (info "Trying to archive attachment id" id "from application" app-id)
  (if-not (get-in @unfinished-uploads [app-id id])
    (do (swap! unfinished-uploads update app-id #(conj (or % #{}) id))
        (.submit
          upload-threadpool
          (fn []
            (try
              (upload-file id is-or-file content-type metadata)
              (state-update-fn application now id)
              (info "Archived attachment id" id "from application" app-id)
              (catch Exception e
                (error e)
                (error "Failed to archive attachment id" id "from application" app-id)))
            (when (instance? File is-or-file)
              (io/delete-file is-or-file :silently))
            (swap! unfinished-uploads update app-id disj id))))
    (warn "Tried to archive attachment id" id "from application" app-id "again while it is still marked unfinished")))

(defn- find-op [{:keys [primaryOperation secondaryOperations]} op-id]
  (if (= op-id (:id primaryOperation))
    [(:name primaryOperation)]
    (->> (filter #(= op-id (:id %)) secondaryOperations)
         (map :name))))

(defn- ->iso-8601-date [date]
  (f/unparse (f/with-zone (:date-time-no-ms f/formatters) (t/time-zone-for-id "Europe/Helsinki")) date))

(defn- get-verdict-date [{:keys [verdicts]} type]
  (let [ts (->> verdicts
                (map (fn [{:keys [paatokset]}]
                       (->> (map #(get-in % [:paivamaarat type]) paatokset)
                            (remove nil?)
                            (first))))
                (remove nil?)
                (first))]
    (when ts
      (->iso-8601-date (c/from-long ts)))))

(defn- get-from-verdict-minutes [{:keys [verdicts]} key]
  (->> verdicts
       (map (fn [{:keys [paatokset]}]
              (map (fn [pt] (map key (:poytakirjat pt))) paatokset)))
       (flatten)
       (remove nil?)
       (first)))

(defn- get-paatospvm [{:keys [verdicts]}]
  (let [ts (->> verdicts
                (map (fn [{:keys [paatokset]}]
                       (map (fn [pt] (map :paatospvm (:poytakirjat pt))) paatokset)))
                (flatten)
                (remove nil?)
                (sort)
                (last))]
    (println ts)
    (when ts
      (->iso-8601-date (c/from-long ts)))))

(defn- get-usages [{:keys [documents]} op-id]
  (let [op-docs (remove #(nil? (get-in % [:schema-info :op :id])) documents)
        id-to-usage (into {} (map (fn [d] {(get-in d [:schema-info :op :id])
                                           (get-in d [:data :kaytto :kayttotarkoitus :value])}) op-docs))]
    (->> (if op-id
           [(get id-to-usage op-id)]
           (vals id-to-usage))
         (remove nil?))))

(defn- make-version-number [{{{:keys [major minor]} :version} :latestVersion}]
  (str major "." minor))

(defn- make-attachment-type [{{:keys [type-group type-id]} :type}]
  (str type-group "." type-id))

(defn- generate-archive-metadata
  [{:keys [id propertyId applicant address organization municipality location location-wgs84] :as application}
   user
   & [attachment]]
  (let [s2-metadata (-> (or (:metadata attachment) (:metadata application))
                        (assoc :tila :arkistoitu))
        base-metadata {:type                  (if attachment (make-attachment-type attachment) :hakemus)
                       :applicationId         id
                       :buildingIds           (remove nil? (map :buildingId (:buildings application)))
                       :nationalBuildingIds   (remove nil? (map :nationalId (:buildings application)))
                       :propertyId            propertyId
                       :applicant             applicant
                       :operations            (if (:op attachment)
                                                (find-op application (get-in attachment [:op :id]))
                                                (concat [(get-in application [:primaryOperation :name])] (map :name (:secondaryOperations application))))
                       :tosFunction           (first (filter #(= (:tosFunction application) (:code %)) (tiedonohjaus/available-tos-functions (:organization application))))
                       :address               address
                       :organization          organization
                       :municipality          municipality
                       :location-etrs-tm35fin location
                       :location-wgs84        location-wgs84
                       :kuntalupatunnukset    (map :kuntalupatunnus (:verdicts application))
                       :lupapvm               (get-verdict-date application :lainvoimainen)
                       :paatospvm             (get-paatospvm application)
                       :paatoksentekija       (get-from-verdict-minutes application :paatoksentekija)
                       :tiedostonimi          (get-in attachment [:latestVersion :filename] (str id ".pdf"))
                       :kasittelija           (select-keys (:authority application) [:username :firstName :lastName])
                       :arkistoija            (select-keys user [:username :firstName :lastName])
                       :kayttotarkoitukset    (if (:op attachment)
                                                (get-usages application (get-in attachment [:op :id]))
                                                (get-usages application nil))
                       :kieli                 "fi"
                       :versio                (if attachment (make-version-number attachment) "1.0")}]
    (cond-> base-metadata
            (:contents attachment) (conj {:contents (:contents attachment)})
            (:size attachment) (conj {:size (:size attachment)})
            (:scale attachment) (conj {:scale (:scale attachment)})
            true (merge s2-metadata))))

(defn send-to-archive [{:keys [user created] {:keys [attachments id] :as application} :application} attachment-ids archive-application?]
  (let [selected-attachments (filter (fn [{:keys [id latestVersion metadata]}]
                                       (and (attachment-ids id) (:archivable latestVersion) (seq metadata)))
                                     attachments)]
    (when archive-application?
      (let [application-file (pdf-export/generate-pdf-a-application-to-file application :fi)
            metadata (generate-archive-metadata application user)]
        (upload-and-set-state (str id "-application") application-file "application/pdf" metadata application created set-application-state)))
    (doseq [attachment selected-attachments]
      (let [{:keys [content content-type]} (lupapalvelu.attachment/get-attachment-file (get-in attachment [:latestVersion :fileId]))
            metadata (generate-archive-metadata application user attachment)]
        (upload-and-set-state (:id attachment) (content) content-type metadata application created set-attachment-state)))))
