(ns lupapalvelu.pdf.html-template
  (:require [sade.core :refer [now ok ok?]]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.pdf.html-template-common :as common]
            [lupapalvelu.pdf.html-muuntaja-client :as muuntaja]
            [lupapalvelu.pdf.html-templates.inspection-summary-template :as inspection-summary-template]))

(defn create-inspection-summary-pdf [application lang summary-id]
  "Creates PDF in muuntaja and returns an input stream."
  (let [foreman-apps (foreman/get-linked-foreman-applications application)
        content (common/apply-page inspection-summary-template/inspection-summary application foreman-apps lang summary-id)
        header  (common/apply-page common/basic-header)
        footer  (common/apply-page common/basic-application-footer application)]
    (logging/with-logging-context
      {:applicationId (:id application)}
      (-> (muuntaja/convert-html-to-pdf (:id application) "inspection-summary" content header footer)
          :pdf-file-stream))))

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
