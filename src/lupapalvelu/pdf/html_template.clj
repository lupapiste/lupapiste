(ns lupapalvelu.pdf.html-template
  (:require [sade.core :refer [now ok ok?]]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.pdf.html-template-common :as common]
            [lupapalvelu.pdf.html-muuntaja-client :as muuntaja]
            [lupapalvelu.pdf.html-templates.inspection-summary-template :as inspection-summary-template]))

(defn- store-file [file-id file-name {stream :pdf-file-stream :as resp}]
  (if (ok? resp)
    (with-open [s stream]
      (->> (mongo/upload file-id file-name "application/pdf" s)
           (ok :file-id file-id :mongo-file)))
    resp))

(defn create-inspection-summary-pdf [application lang summary-id & {:keys [file-id] :or {file-id (mongo/create-id)}}]
  (let [foreman-apps (foreman/get-linked-foreman-applications application)
        content (common/apply-page inspection-summary-template/inspection-summary application foreman-apps lang summary-id)
        header  (common/apply-page common/basic-header)
        footer  (common/apply-page common/basic-application-footer application)
        file-name (str (:id application) "_inspection-summary_" summary-id \_ (now) ".pdf")]
    (logging/with-logging-context
      {:applicationId (:id application)}
      (->> (muuntaja/convert-html-to-pdf (:id application) "inspection-summary" content header footer)
           (store-file file-id file-name)))))
