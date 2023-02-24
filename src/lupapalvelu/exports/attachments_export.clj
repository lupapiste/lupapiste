(ns lupapalvelu.exports.attachments-export
  "Namespace containing functions for exporting an organization's attachment files.
  An Excel file that contains the metadata for each file is generated and provided alongside the files.
  See LPK-6156 for more information."
  (:require [clojure.java.io :as io]
            [dk.ative.docjure.spreadsheet :as xls]
            [lupapalvelu.attachment.onkalo-client :as oc]
            [lupapalvelu.file-upload :as file-upload]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.storage.file-storage :as storage]
            [monger.operators :refer :all]
            [sade.core :as sade]
            [sade.files :as files]
            [sade.strings :as ss]
            [sade.util :as util])
  (:import (java.io InputStream ByteArrayInputStream ByteArrayOutputStream)))

;;
;; Logging
;;

(defn log-event
  "Utility for logging events less verbosely"
  [level app-id att-id message & [exception]]
  (logging/log-event level (util/assoc-when
                             {:run-by "Attachments export"
                              :event  (ss/join " " (concat [message]
                                                           (when app-id ["for application" app-id])
                                                           (when att-id ["attachment" att-id])))}
                             :exception-message (when exception (.getMessage exception)))))

;;
;; Excel generation
;;

(def columns
  "Columns to be included in the metadata excel, in the order given"
  [:app-id :contents :type-group :type-id :filename])

(defn- column-loc [col]
  (->> col name (str "attachment.export.") i18n/loc))

(defn- loc-attachment-type [{:keys [type-group type-id]}]
  {:type-group (i18n/loc (format "attachmentType.%s._group_label" type-group))
   :type-id    (i18n/loc (format "attachmentType.%s.%s" type-group type-id))})

(defn- header-row []
  (mapv column-loc columns))

(defn- body-row [{:keys [type latestVersion] :as attachment}]
  (let [data (->> (loc-attachment-type type)
                  (merge attachment latestVersion))]
    (mapv #(get data %) columns)))

(defn generate-metadata-excel
  "Returns Excel Workbook (saving/uploading has to be done separately)"
  [lang attachments]
  (try
    (i18n/with-lang lang
      (xls/create-workbook
        (column-loc :_group_label)
        (cons (header-row)
              (mapv body-row attachments))))
    (catch Exception ex
      (log-event :error nil nil "Failed to generate metadata Excel file" ex))))

;;
;; Storage file manipulation
;;

(defn- get-file-stream-fn
  "Returns a function which returns an InputStream for the attachment file contents"
  [{:keys [id app-id org-id] {:keys [fileId onkaloFileId]} :latestVersion :as attachment}]
  (cond
    fileId       (:content (storage/download app-id fileId attachment))
    onkaloFileId (:content (oc/get-file org-id onkaloFileId))
    :else        (log-event :error app-id id "No fileId or onkaloFileId found")))

(defn- append-metadata-excel [zip-output-stream lang attachments]
  (when-let [metadata-wb (generate-metadata-excel lang attachments)]
    (with-open [excel-out (ByteArrayOutputStream.)]
      (xls/save-workbook-into-stream! excel-out metadata-wb)
      (files/open-and-append! zip-output-stream
                              (str (i18n/with-lang lang (column-loc :_group_label)) ".xlsx")
                              (fn [] (ByteArrayInputStream. (.toByteArray excel-out)))))))

(defn- append-attachments [zip-output-stream attachments]
  (doseq [{{:keys [id app-id filename]} :latestVersion :as attachment} attachments]
    (log-event :info app-id id "Appending to zip file")
    (if-let [file-stream-fn (get-file-stream-fn attachment)]
      (files/open-and-append! zip-output-stream filename file-stream-fn)
      (log-event :warning app-id id "No file found"))))

(defn ^InputStream attachments-zip-stream
  "Returns a piped zip input stream which contains the attachments and the metadata excel"
  [lang attachments]
  (files/piped-zip-input-stream
    (fn [zip-output-stream]
      (append-metadata-excel zip-output-stream lang attachments)
      (append-attachments zip-output-stream attachments))))

;;
;; MongoDB data collection
;;

(defn- get-zip-filename
  "Makes sure the filename is unique across the organization"
  [application-id attachment-id old-filename]
  (format "%s_%s_%s" application-id attachment-id old-filename))

(defn get-attachments
  "Returns a seq of all of the organization's attachments with the application id and new filename"
  [organization-id]
  (->> (mongo/select :applications
                     {:organization              organization-id
                      :attachments.latestVersion {$exists true}}
                     [:attachments])
       (mapcat (fn [{:keys [attachments] app-id :id}]
                 (->> attachments
                      (filter :latestVersion)
                      (map (fn [{att-id :id :as attachment}]
                             (-> attachment
                                 (update-in [:latestVersion :filename] #(get-zip-filename app-id att-id %))
                                 (merge {:app-id app-id
                                         :org-id organization-id})))))))))

;;
;; Export API
;;

(defn export-attachments
  "Collects all attachments from the given organization's applications and uploads them
  to storage in a zip file also containing an Excel file containing metadata for the attachments.
  Returns the filedata for the uploaded zip file and stores it in the DB under the organization
  for admin download links"
  [lang organization-id]
  {:pre [(string? lang) (string? organization-id)]}
  (logging/with-logging-context {:userId   "batchrun-user"
                                 :batchrun "attachments-export"}
    (try
      (log-event :info nil nil "Starting attachments-export batchrun")
      (let [attachments (get-attachments organization-id)
            created     (sade/now)
            filename    (format "attachments_export_%s_%s.zip" organization-id created)]
        (with-open [zip-stream (attachments-zip-stream lang attachments)]
          (log-event :info nil nil "Starting export zip upload")
          (let [filedata (file-upload/save-file {:filename filename
                                                 :content  zip-stream}
                                                {:uploader-user-id "batchrun-user"})]
              (log-event :info nil nil (str "Upload done " filedata))
              (mongo/update-by-id
                :organizations
                organization-id
                {$push {:export-files (-> filedata
                                          (select-keys [:fileId :filename :size :contentType])
                                          (assoc :created created))}})
              filedata)))
      (catch Exception ex
        (log-event :error nil nil "Error during batchrun" ex)))))
