(ns lupapalvelu.application.submit-data-source
  "When submitting an application, the applicant may indicate
  whether the the application should be reviewed using the BIM -model
  or the application documents (the default)."
  (:require [schema.core :as sc :refer [defschema]]))

(def data-sources
  "Available sources, order matters."
  [:document :bim])

(def default-data-source (first data-sources))

(defschema SubmitDataSourceStr (apply sc/enum (map name data-sources)))

(defn help-sections
  [data-source]
  (case data-source
    :document [:submit-data-source.help.document.data
               :submit-data-source.help.document.attachments
               :submit-data-source.help.document.building-model]
    :bim      [:submit-data-source.help.bim.building-model
               :submit-data-source.help.bim.data
               :submit-data-source.help.bim.attachments
               :submit-data-source.help.bim.completion-model]))
