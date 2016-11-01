(ns lupapalvelu.pdf.mass-export
  (:require [lupapalvelu.application :as app]
            [lupapalvelu.pdf.pdf-export :as pdf]
            [lupapalvelu.mongo :as mongo]
            [me.raynes.fs :as fs]))

(defn generate-pdfs
  "Generates application pdfs with masked hetus."
  [folder query]
  (doseq [application (mongo/select :applications query)]
    (pdf/generate (app/with-masked-person-ids application {})
                  :fi
                  (fs/file folder (str (:id application) ".pdf")))))
