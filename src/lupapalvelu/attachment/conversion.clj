(ns lupapalvelu.attachment.conversion
  (:require [clojure.string :as str]
            [lupapalvelu.laundry.client :as laundry-client]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.storage.gcs :as gcs]
            [sade.shared-schemas :as sssc]
            [schema-tools.core :as st]
            [schema.core :as sc]
            [taoensso.timbre :as timbre])
  (:import [org.apache.commons.io FilenameUtils]))

(defn enabled? []
  (if (str/blank? laundry-client/url)
    (timbre/warn "PDF/A conversion feature disabled or vaahtera-laundry host not configured")
    true))

(def archivability-errors #{:invalid-mime-type
                            :invalid-pdfa
                            :not-validated
                            :permanent-archive-disabled
                            ;; Deprecated values
                            :invalid-tiff
                            :libre-conversion-error})

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
  (cond
    (string? organization-or-id) (organization/some-organization-has-archive-enabled? #{organization-or-id})
    (map? organization-or-id) (true? (:permanent-archive-enabled organization-or-id))
    (delay? organization-or-id) (true? (:permanent-archive-enabled @organization-or-id))
    :else (throw (IllegalArgumentException. (str "Not an organization: " organization-or-id)))))

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
   application :- {sc/Keyword sc/Any}
   {:keys [fileId contentType filename]} :- {:fileId      sssc/FileId
                                             :contentType (sc/cond-pre sc/Keyword sc/Str)
                                             :filename    sc/Str
                                             sc/Keyword   sc/Any}]
  (let [kw-content (keyword contentType)]
    (cond
      (not (contains? all-convertable-mime-types kw-content))
      {:result {:archivable         false
                :archivabilityError :invalid-mime-type}}

      ;; To match with traditional behaviour, we only convert "office files" if the customer has not bought the
      ;; permanent archiving service.
      (and (not (pdf-a-required? (:organization application)))
           (or (= (namespace kw-content) "image")
               (= (name kw-content) "pdf")))
      {:result {:archivable         false
                :archivabilityError :permanent-archive-disabled}}

      (or (not= (storage/default-storage-system-id) :gcs)
          (not (enabled?)))
      {:result {:archivable         false
                :archivabilityError :not-validated}}

      :else
      (let [bucket    (gcs/actual-bucket-name (storage/bucket-name metadata))
            object-id (storage/actual-object-id metadata fileId)
            result    (laundry-client/convert-to-pdfa bucket object-id)]
        (cond-> result
          (:file result) (assoc-in [:file :filename] (str (FilenameUtils/removeExtension filename) ".pdf")))))))
