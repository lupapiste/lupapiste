(ns lupapalvelu.pdf.mass-export
  (:require [lupapalvelu.pdf.pdf-export :as pdf]
            [lupapalvelu.mongo :as mongo]
            [me.raynes.fs :as fs]
            [lupapalvelu.application-utils :as app-utils]))

(defn generate-pdfs
  "Generates application pdfs with masked hetus."
  [folder query]
  (doseq [application (mongo/select :applications query)]
    (pdf/generate (app-utils/with-masked-person-ids application {})
                  :fi
                  (fs/file folder (str (:id application) ".pdf")))))
