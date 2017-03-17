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
  (let [content (apply str (inspection-summary-template/inspection-summary application lang summary-id))
        header  (apply str (common/basic-header application lang))
        footer  (apply str (common/basic-footer application lang))
        file-id (mongo/create-id)
        resp    (muuntaja/convert-html-to-pdf (:id application) "inspection-summary" content header footer)]
    (if (:ok resp)
      (with-open [stream (:pdf-file-stream resp)]
        (mongo/upload file-id (str (:id application) "_inspection-summary_" summary-id \_ (now) ".pdf") "application/pdf" stream)
        file-id)
      resp)))

(comment

  (create-inspection-summary-pdf application lang summary-id)

  (i18n/with-lang lang
    (spit (io/file (io/resource "t2.html")) (apply str (page-content application lang summary-id))))

  (i18n/with-lang lang
    (spit (io/file (io/resource "page.html")) (apply str (page application lang summary-id))))

  (i18n/with-lang lang
    (spit (io/file (io/resource "page.html")) (apply str (lupapalvelu.pdf.html-templates.inspection-summary-template/inspection-summary application lang summary-id))))

  (spit (io/file (io/resource "header.html")) (apply str (header)))

  (spit (io/file (io/resource "footer.html")) (apply str (footer)))


  (lupapalvelu.mongo/connect!)

  (def application (lupapalvelu.domain/get-application-no-access-checking "LP-753-2017-90006"))
  (def application (update-in application [:inspection-summaries 0 :targets] (partial map-indexed #(update %2 :target-name str "__" %1))))

  (def lang "fi")

  (def summary-id (-> application :inspection-summaries first :id))

  (lupapalvelu.pdf.html-templates.inspection-summary-template/inspection-summary application lang summary-id)

  (lupapalvelu.pdf.html-templates.inspection-summary-template/inspection-summary application lang summary-id)

  (hiccup/html [:style (-> page-style slurp (ss/replace #"\s+" " "))])

    )
