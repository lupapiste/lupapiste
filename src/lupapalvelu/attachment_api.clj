(ns lupapalvelu.attachment-api
  (:require [clojure.java.io :as io]
            [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn warnf error errorf fatal]]
            [monger.operators :refer :all]
            [swiss-arrows.core :refer [-<> -<>>]]
            [sade.strings :as ss]
            [sade.util :refer [future*]]
            [lupapalvelu.core :refer [ok fail fail!]]
            [lupapalvelu.action :refer [defquery defcommand defraw update-application]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.attachment :refer [output-attachment get-attachment-as update-version-content set-attachment-version if-not-authority-states-must-match]]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.job :as job]
            [lupapalvelu.stamper :as stamper]
            [lupapalvelu.ke6666 :as ke6666]
            [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp])
  (:import [java.util.zip ZipOutputStream ZipEntry]
           [java.io File OutputStream FilterInputStream]))

(defn- get-data-argument-for-attachments-mongo-update [timestamp attachments]
  (reduce
    (fn [data-map attachment]
      (conj data-map {(keyword (str "attachments." (count data-map) ".sent")) timestamp}))
    {}
    attachments))

(defcommand move-attachments-to-backing-system
  {:parameters [id lang]
   :roles      [:authority]
   :validators [(partial if-not-authority-states-must-match #{:verdictGiven})
                (permit/validate-permit-type-is permit/R)]
   :states     [:verdictGiven]
   :description "Sends such attachments to backing system that are not yet sent."}
  [{:keys [created application] :as command}]

  (let [attachments-wo-sent-timestamp (filter
                                        #(and
                                           (pos? (-> % :versions count))
                                           (or
                                             (not (:sent %))
                                             (> (-> % :versions last :created) (:sent %)))
                                           (not (= "statement" (-> % :target :type)))
                                           (not (= "verdict" (-> % :target :type))))
                                        (:attachments application))]
    (if (pos? (count attachments-wo-sent-timestamp))

      (let [organization (organization/get-organization (:organization application))]
        (mapping-to-krysp/save-unsent-attachments-as-krysp
          (-> application
            (dissoc :attachments)
            (assoc :attachments attachments-wo-sent-timestamp))
          lang
          organization)

        (update-application command
          {$set (get-data-argument-for-attachments-mongo-update created (:attachments application))})
        (ok))

      (fail :error.sending-unsent-attachments-failed))))

;;
;; Download
;;


(defn- output-attachment-if-logged-in [attachment-id download? user]
  (if user
    (output-attachment attachment-id download? (partial get-attachment-as user))
    {:status 401
     :headers {"Content-Type" "text/plain"}
     :body "401 Unauthorized"}))

(defraw "view-attachment"
  {:parameters [:attachment-id]}
  [{{:keys [attachment-id]} :data user :user}]
  (output-attachment-if-logged-in attachment-id false user))

(defraw "download-attachment"
  {:parameters [:attachment-id]}
  [{{:keys [attachment-id]} :data user :user}]
  (output-attachment-if-logged-in attachment-id true user))

(defn- append-gridfs-file [zip file-name file-id]
  (when file-id
    (.putNextEntry zip (ZipEntry. (ss/encode-filename (str file-id "_" file-name))))
    (with-open [in ((:content (mongo/download file-id)))]
      (io/copy in zip))))

(defn- append-stream [zip file-name in]
  (when in
    (.putNextEntry zip (ZipEntry. (ss/encode-filename file-name)))
    (io/copy in zip)))

(defn- append-attachment [zip {:keys [filename fileId]}]
  (append-gridfs-file zip filename fileId))

(defn- get-all-attachments [application lang]
  (let [temp-file (File/createTempFile "lupapiste.attachments." ".zip.tmp")]
    (debugf "Created temporary zip file for attachments: %s" (.getAbsolutePath temp-file))
    (with-open [out (io/output-stream temp-file)]
      (let [zip (ZipOutputStream. out)]
        ; Add all attachments:
        (doseq [attachment (:attachments application)]
          (append-attachment zip (-> attachment :versions last)))
        ; Add submitted PDF, if exists:
        (when-let [submitted-application (mongo/by-id :submitted-applications (:id application))]
          (append-stream zip (i18n/loc "attachment.zip.pdf.filename.submitted") (ke6666/generate submitted-application lang)))
        ; Add current PDF:
        (append-stream zip (i18n/loc "attachment.zip.pdf.filename.current") (ke6666/generate application lang))
        (.finish zip)))
    temp-file))

(defn- temp-file-input-stream [^File file]
  (let [i (io/input-stream file)]
    (proxy [FilterInputStream] [i]
      (close []
        (proxy-super close)
        (when (= (io/delete-file file :could-not) :could-not)
          (warnf "Could not delete temporary file: %s" (.getAbsolutePath file)))))))

(defraw "download-all-attachments"
  {:parameters [:id]}
  [{:keys [application lang]}]
  (if application
    {:status 200
       :headers {"Content-Type" "application/octet-stream"
                 "Content-Disposition" (str "attachment;filename=\"" (i18n/loc "attachment.zip.filename") "\"")}
       :body (temp-file-input-stream (get-all-attachments application lang))}
    {:status 404
     :headers {"Content-Type" "text/plain"}
     :body "404"}))


;;
;; Stamping:
;;

(defn- stampable? [attachment]
  (let [latest       (-> attachment :versions last)
        content-type (:contentType latest)
        stamped      (:stamped latest)]
    (and (not stamped) (or (= "application/pdf" content-type) (ss/starts-with content-type "image/")))))

(defn- loc-organization-name [organization]
  (get-in organization [:name i18n/*lang*] (str "???ORG:" (:id organization) "???")))

(defn- get-organization-name [application-id]
  (-<> application-id
       (mongo/by-id :applications <> [:organization])
       (:organization)
       (organization/get-organization <> [:name])
       (loc-organization-name <>)))

(defn- key-by [f coll]
  (into {} (for [e coll] [(f e) e])))

(defn ->long [v]
  (if (string? v) (Long/parseLong v) v))

(defn- ->file-info [attachment]
  (let [versions   (-> attachment :versions reverse)
        re-stamp?  (:stamped (first versions))
        source     (if re-stamp? (second versions) (first versions))]
    (assoc (select-keys source [:contentType :fileId :filename :size])
           :re-stamp? re-stamp?
           :attachment-id (:id attachment))))

(defn- add-stamp-comment [new-version new-file-id file-info context]
  ; mea culpa, but what the fuck was I supposed to do
  (mongo/update-by-id :applications (:application-id context)
    {$set {:modified (:created context)}
     $push {:comments {:text    (i18n/loc (if (:re-stamp? file-info) "stamp.comment.restamp" "stamp.comment"))
                       :created (:created context)
                       :user    (:user context)
                       :target  {:type "attachment"
                                 :id (:attachment-id file-info)
                                 :version (:version new-version)
                                 :filename (:filename file-info)
                                 :fileId new-file-id}}}}))

(defn- stamp-attachment! [stamp file-info context]
  (let [{:keys [application-id user created]} context
        {:keys [attachment-id contentType fileId filename re-stamp?]} file-info
        temp-file (File/createTempFile "lupapiste.stamp." ".tmp")
        new-file-id (mongo/create-id)]
    (debug "created temp file for stamp job:" (.getAbsolutePath temp-file))
    (with-open [in ((:content (mongo/download fileId)))
                out (io/output-stream temp-file)]
      (stamper/stamp stamp contentType in out (:x-margin context) (:y-margin context) (:transparency context)))
    (mongo/upload new-file-id filename contentType temp-file :application application-id)
    (let [new-version (if re-stamp?
                        (update-version-content application-id attachment-id new-file-id (.length temp-file) created)
                        (set-attachment-version application-id attachment-id new-file-id filename contentType (.length temp-file) created user true))]
      (add-stamp-comment new-version new-file-id file-info context))
    (try (.delete temp-file) (catch Exception _))))

(defn- stamp-attachments! [file-infos {:keys [user created job-id application-id] :as context}]
  (let [stamp (stamper/make-stamp
                (i18n/loc "stamp.verdict")
                created
                (str (:firstName user) \space (:lastName user))
                (get-organization-name application-id)
                (:transparency context))]
    (doseq [file-info (vals file-infos)]
      (try
        (job/update job-id assoc (:attachment-id file-info) :working)
        (stamp-attachment! stamp file-info context)
        (job/update job-id assoc (:attachment-id file-info) :done)
        (catch Exception e
          (errorf e "failed to stamp attachment: application=%s, file=%s" application-id (:fileId file-info))
          (job/update job-id assoc (:attachment-id file-info) :error))))))

(defn- stamp-job-status [data]
  (if (every? #{:done :error} (vals data)) :done :runnig))

(defn- make-stamp-job [file-infos context]
  (let [job (job/start (zipmap (keys file-infos) (repeat :pending)) stamp-job-status)]
    (future* (stamp-attachments! file-infos (assoc context :job-id (:id job))))
    job))

(defcommand stamp-attachments
  {:parameters [:id files xMargin yMargin]
   :roles      [:authority]
   :states     [:open :submitted :complement-needed :verdictGiven]
   :description "Stamps all attachments of given application"}
  [{application :application {transparency :transparency} :data :as command}]
  (ok :job (make-stamp-job
             (key-by :attachment-id (map ->file-info (filter (comp (set files) :id) (:attachments application))))
             {:application-id (:id application)
              :user (:user command)
              :created (:created command)
              :x-margin (->long xMargin)
              :y-margin (->long yMargin)
              :transparency (->long (or transparency 0))})))

(defquery stamp-attachments-job
  {:parameters [:job-id :version]
   :roles      [:authority]
   :description "Returns state of stamping job"}
  [{{job-id :job-id version :version timeout :timeout :or {version "0" timeout "10000"}} :data}]
  (assoc (job/status job-id (->long version) (->long timeout)) :ok true))