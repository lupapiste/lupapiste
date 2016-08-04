(ns lupapalvelu.attachment.appeal
  (:require [sade.core :refer :all]
            [monger.operators :refer :all]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.file-upload :as file-upload]
            [lupapalvelu.pdf.pdfa-conversion :as pdf-conversion]
            [lupapalvelu.tiff-validation :as tiff-validation]))


(defn save-pdfa-file!
  "Save PDF/A file from pdf-conversion processing result to mongo gridfs.
   Returns map with archivability flags (archivable, missing-fonts, archivability error)
   and if valid PDF/A, fileId, filename and content-type of the uploaded file."
  [{app-id :id} {:keys [pdfa? output-file missing-fonts autoConversion]} filename content-type]
  (if pdfa?
    (let [filedata  (file-upload/save-file {:filename (att/filename-for-pdfa filename)
                                            :content  output-file
                                            :size     (.length output-file)}
                                           :application app-id)]
      (merge {:archivable true
              :archivabilityError nil
              :file-name (:filename filedata)
              :contentType content-type
              :autoConversion autoConversion}
             filedata))
    {:archivable false :missing-fonts (or missing-fonts []) :archivabilityError :invalid-pdfa}))

(defn archivability-steps!
  "If file is PDF or TIFF, returns map to indicate if file is archivable.
   If not PDF/TIFF nor required by organization, returns nil.
   In case of PDF, PDF/A conversion is made if needed, and new converted file uploaded to mongo.
   If PDF/A conversion was made, additional :fileId, :filename, :content-type and :content-length keys are returned."
  [{application :application} {:keys [content-type content file-name]}]
  (case (name content-type)
    "application/pdf" (when (pdf-conversion/pdf-a-required? (:organization application))
                        (let [processing-result (pdf-conversion/convert-to-pdf-a (content) {:application application :filename file-name})] ; content is a function from mongo.clj
                          (if (:already-valid-pdfa? processing-result)
                            {:archivable true :archivabilityError nil :already-valid true}
                            (save-pdfa-file! application processing-result file-name content-type))))
    "image/tiff"      (let [valid? (tiff-validation/valid-tiff? content)]
                        {:archivable valid? :archivabilityError (when-not valid? :invalid-tiff)})
    nil))

(def- initial-archive-options {:archivable false :archivabilityError :invalid-mime-type})

(defn- version-options
  "Returns version options for subject (a file). This is NOT final version model (see make-version)."
  [subject pdfa-result now]
  (merge {:fileId           (:fileId subject)
          :original-file-id (:fileId subject)
          :filename         (:file-name subject)
          :contentType      (or (:content-type subject) (:contentType subject))
          :size             (:size subject)
          :now now
          :stamped false}
         (select-keys pdfa-result [:archivable :archivabilityError :missing-fonts :autoConversion])))

(defn- appeal-attachment-versions-options
  "Create options maps for needed versions. Created version(s) are returned in vector."
  [{now :created} pdfa-result original-file]
  (if-not (nil? pdfa-result) ; nil if content-type is not regarded as archivable
    (if (:already-valid pdfa-result)
      [(version-options original-file pdfa-result now)]
      (let [initial-versions-options [(version-options original-file ; initial version without archive results
                                                       (merge pdfa-result initial-archive-options)
                                                       now)]]
        (if (contains? pdfa-result :fileId) ; if PDF/A file was uploaded to mongo
          (conj initial-versions-options (version-options pdfa-result pdfa-result now)) ; add PDF/A version
          [(version-options original-file pdfa-result now)]))) ; just return original file, with pdfa conversion result merged
    [(version-options original-file initial-archive-options now)]))

(defn- create-appeal-attachment-data!
  "Return attachment model for new appeal attachment with version(s) included.
   If PDF, converts to PDF/A (if applicable) and thus creates a new file to GridFS as side effect."
  [{app :application now :created user :user :as command} appeal-id appeal-type file]
  (let [type                 (att-type/attachment-type-for-appeal appeal-type)
        target               {:id appeal-id
                              :type appeal-type}
        attachment-data      (att/create-attachment-data app type nil now target true false false nil nil true)
        archivability-result (archivability-steps! command file)
        versions-options     (appeal-attachment-versions-options command archivability-result file)]
    (reduce (fn [attachment version-options] ; reduce attachment over versions, thus version number gets incremented correctly
              (let [version (att/make-version user attachment version-options)]
                (-> attachment
                    (update :versions conj version)
                    (assoc :latestVersion version))))
            attachment-data
            versions-options)))

(defn new-appeal-attachment-updates!
  "Return $push operation for attachments, with attachments created for given fileIds.
   As a side effect, creates converted PDF/A version to mongo for PDF files (if applicable)."
  [command appeal-id appeal-type fileIds]
  (let [file-objects    (seq (mongo/download-find-many {:_id {$in fileIds}}))
        new-attachments (map
                          (partial
                            create-appeal-attachment-data!
                            command
                            appeal-id
                            appeal-type)
                          file-objects)]
    (when (seq new-attachments)
      {$push {:attachments {$each new-attachments}}})))
