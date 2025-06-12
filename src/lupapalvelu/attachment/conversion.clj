(ns lupapalvelu.attachment.conversion
  (:require [lupapalvelu.laundry.client :as laundry-client]
            [lupapalvelu.logging :as log]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.storage.gcs :as gcs]
            [sade.schemas :as ssc]
            [sade.shared-schemas :as sssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema-tools.core :as st]
            [schema.core :as sc])
  (:import [org.apache.commons.io FilenameUtils]))

(set! *warn-on-reflection* true)

(defn enabled? []
  (if (ss/blank? laundry-client/url)
    (log/warn "PDF/A conversion feature disabled or vaahtera-laundry host not configured")
    true))

(def archivability-errors
  (into #{;; Deprecated values
          :invalid-tiff
          :libre-conversion-error}
        laundry-client/archivability-errors))

(def all-convertable-mime-types
  #{:application/vnd.openxmlformats-officedocument.presentationml.presentation
    :application/vnd.openxmlformats-officedocument.wordprocessingml.document
    :application/vnd.oasis.opendocument.text
    :application/vnd.oasis.opendocument.graphics
    :application/vnd.oasis.opendocument.presentation
    :application/vnd.ms-powerpoint
    :application/rtf
    :application/msword
    :text/plain
    :image/jpeg
    :image/tiff
    :image/png
    :application/pdf})

(defn pdf-a-required? [organization-or-id]
  (organization/check-organization-flag organization-or-id :permanent-archive-enabled))

(defn- tiff-archiving-enabled? [organization-or-id]
  (organization/check-organization-flag organization-or-id :tiff-archiving-enabled))

(defn- jpeg-archiving-enabled? [organization-or-id]
  (organization/check-organization-flag organization-or-id :jpeg-archiving-enabled))

(sc/defn ^:always-validate archivability-conversion :- laundry-client/ConversionResultAndFile
  "Validates file for archivability, and converts content if needed. Conversion is only supported for files in GCS storage.
   Returns `ConversionResultAndFile` map, where the file part is present if actual conversion occurred.
   Note that one of the keys in `metadata` map must be present or an exception will be thrown.

   If permanent archive is not enabled for the organization of the provided `application`, only Office-y files
   are converted, not images or regular PDFs.

   Having `:application` id present means that the convertable file is assumed to be in the application bucket,
   while having `:user-id` present means that it is assumed to be in the user files bucket.
   Having neither of the above and `:uploader-user-id` or `:sessionId` present means that the file is assumed
   to be in the unlinked files bucket."
  [metadata :- (st/optional-keys {:uploader-user-id (sc/maybe sc/Str)
                                  :sessionId        (sc/maybe sc/Str)
                                  :application      (sc/maybe sc/Str) ; application id
                                  :user-id          (sc/maybe sc/Str)})
   {:keys [organization] :as _application} :- {:organization ssc/NonBlankStr
                                               sc/Keyword    sc/Any}
   {:keys [fileId contentType filename]} :- {:fileId      sssc/FileId
                                             :contentType (sc/cond-pre sc/Keyword sc/Str)
                                             :filename    sc/Str
                                             sc/Keyword   sc/Any}]
  (let [kw-content (keyword contentType)
        bucket     (gcs/actual-bucket-name (storage/bucket-name metadata))
        object-id  (storage/actual-object-id metadata fileId)]
    (cond
      (not (contains? all-convertable-mime-types kw-content))
      {:result {:archivable         false
                :archivabilityError :invalid-mime-type}}

      ;; To match with traditional behaviour, we only convert "office files" if the customer has not bought the
      ;; permanent archiving service.
      (and (not (pdf-a-required? organization))
           (or (= (namespace kw-content) "image")
               (= (name kw-content) "pdf")))
      {:result {:archivable         false
                :archivabilityError :permanent-archive-disabled}}

      (and (= kw-content :image/tiff)
           (tiff-archiving-enabled? organization)
           (laundry-client/verify-image-archivable!
             (util/mmap {:expected-content-type "image/tiff"} bucket object-id)))
      {:result {:archivable         false
                :archivabilityError :tiff-archiving}}

      (and (= kw-content :image/jpeg)
           (jpeg-archiving-enabled? organization)
           (laundry-client/verify-image-archivable!
             (util/mmap {:expected-content-type "image/jpeg"} bucket object-id)))
      {:result {:archivable         false
                :archivabilityError :jpeg-archiving}}

      (or (not= (storage/default-storage-system-id) :gcs)
          (not (enabled?)))
      {:result {:archivable         false
                :archivabilityError :not-validated}}

      :else
      (let [result (laundry-client/convert-to-pdfa bucket object-id)]
        (cond-> result
          (:file result) (assoc-in [:file :filename] (str (FilenameUtils/removeExtension filename) ".pdf")))))))
