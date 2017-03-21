(ns lupapalvelu.pdf.html-template
  (:require [clojure.java.io :as io]
            [sade.core :refer [now ok ok?]]
            [sade.strings :as ss]
            [sade.util :refer [fn->>] :as util]
            [net.cgrand.enlive-html :as enlive]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pdf.html-template-common :as common]
            [lupapalvelu.pdf.html-muuntaja-client :as muuntaja]
            [lupapalvelu.pdf.html-templates.inspection-summary-template :as inspection-summary-template]))

(defn- store-file [file-id file-name {stream :pdf-file-stream :as resp}]
  (if (ok? resp)
    (with-open [s stream]
      (->> (mongo/upload file-id file-name "application/pdf" s)
           (ok :file-id file-id :mongo-file)))
    resp))

(defn create-inspection-summary-pdf [application lang summary-id]
  (let [content (common/apply-page inspection-summary-template/inspection-summary application lang summary-id)
        header  (common/apply-page common/basic-header)
        footer  (common/apply-page common/basic-application-footer application)
        file-id (mongo/create-id)
        file-name (str (:id application) "_inspection-summary_" summary-id \_ (now) ".pdf")]
    (->> (muuntaja/convert-html-to-pdf (:id application) "inspection-summary" content header footer)
         (store-file file-id file-name)
         (util/future*)
         (assoc {:file-id file-id} :generation-response))))
