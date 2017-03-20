(ns lupapalvelu.pdf.html-template
  (:require [clojure.java.io :as io]
            [sade.core :refer [now]]
            [sade.strings :as ss]
            [sade.util :refer [fn->>] :as util]
            [net.cgrand.enlive-html :as enlive]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pdf.html-template-common :as common]
            [lupapalvelu.pdf.html-muuntaja-client :as muuntaja]
            [lupapalvelu.pdf.html-templates.inspection-summary-template :as inspection-summary-template]))

(defn create-inspection-summary-pdf [application lang summary-id]
  (let [content (common/apply-page inspection-summary-template/inspection-summary application lang summary-id)
        header  (common/apply-page common/basic-header application lang)
        footer  (common/apply-page common/basic-footer application lang)
        file-id (mongo/create-id)
        file-name (str (:id application) "_inspection-summary_" summary-id \_ (now) ".pdf")
        resp    (muuntaja/convert-html-to-pdf (:id application) "inspection-summary" content header footer)]
    (if (:ok resp)
      (with-open [stream (:pdf-file-stream resp)]
        (mongo/upload file-id file-name "application/pdf" stream)
        file-id)
      resp)))
