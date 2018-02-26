(ns lupapalvelu.pdf.html-template
  (:require [sade.core :refer [now ok ok?]]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.pdf.html-template-common :as common]
            [lupapalvelu.pdf.html-muuntaja-client :as muuntaja]
            [lupapalvelu.pdf.html-templates.inspection-summary-template :as inspection-summary-template]))

(defn create-inspection-summary-pdf [application lang summary-id & {:keys [file-id] :or {file-id (mongo/create-id)}}]
  (let [foreman-apps (foreman/get-linked-foreman-applications application)
        content (common/apply-page inspection-summary-template/inspection-summary application foreman-apps lang summary-id)
        header  (common/apply-page common/basic-header)
        footer  (common/apply-page common/basic-application-footer application)
        file-name (str (:id application) "_inspection-summary_" summary-id \_ (now) ".pdf")]
    (logging/with-logging-context
      {:applicationId (:id application)}
      (->> (muuntaja/convert-html-to-pdf (:id application) "inspection-summary" content header footer)
           (muuntaja/upload-pdf-stream file-id file-name)))))

(defn html->pdf
  "Convenience function that combines sensible defaults and muuntaja
  details. The return value is map, where :ok is true on success (PDF
  stream is in :pdf-file-stream). If :ok is false, the error message
  is in :text.

  application: The default footer requires the whole application, but
  muuntaja only the application id. Thus, if the footer is provided
  the application map may contain only the id property.

  template-name: String used by muuntaja logging.

  html: HTML contents with the following keys [optional, has sensible default]:
    body:     HTML for body
    [header]: HTML for header.
    [footer]: HTML for footer."
  ([{app-id :id :as application} template-name {:keys [body header footer]}]
   (let [header   (or header (common/apply-page common/basic-header))
         footer   (or footer (common/apply-page common/basic-application-footer application))]
     (muuntaja/convert-html-to-pdf app-id
                                   template-name
                                   body
                                   header
                                   footer))))

(defn create-and-upload-pdf
  "Convenience function that combines sensible defaults and muuntaja
  details. Returns file upload metadata.

  application: Logging context requires id and the default footer the
  whole application. Thus, if the footer is provided the application
  map can contain only the id property. Also, the default filename is
  prefixed with the application ID.

  template-name: String used by muuntaja logging.

  html-body: HTML contents of the body.

  Optional argument is a map with the following optional keys. Each
  has a reasonable default.

  file-id: file-id for the uploaded file.

  header: HTML for header.

  footer: HTML for footer.

  filename: Name of the created PDF file."
  ([application template-name html-body]
   (create-and-upload-pdf application template-name html-body nil))
  ([{app-id :id :as application} template-name html-body optional]
   (let [file-id  (get optional :file-id (mongo/create-id))
         filename (get optional :filename (format "%s-%s.pdf" app-id file-id))]
     (logging/with-logging-context
       {:applicationId app-id}
       (muuntaja/upload-pdf-stream file-id
                                   filename
                                   (html->pdf application
                                              template-name
                                              (assoc optional
                                                     :body html-body)))))))
