(ns lupapalvelu.appeal-schema
  (:require [sade.shared-schemas :as ssc]
            [schema.core :refer [defschema] :as sc]
            [schema-tools.core :as st]))

(def appeal-types
  "appeal = Valitus, rectification = Oikaisuvaatimus"
  ["appeal" "rectification"])

(defschema Appeal
           "Schema for a verdict appeal."
           (st/merge
             {:id             ssc/ObjectIdStr
              :target-verdict ssc/ObjectIdStr ;; refers to verdicts.paatokset.id
              :type           (apply sc/enum appeal-types)
              :appellant      sc/Str ;; Name of the person who made the appeal
              :datestamp      ssc/Timestamp ;; Date of appeal made - defined manually by authority
              }
             (st/optional-keys {:text             sc/Str ;; Optional description
                                :from-bulletin?   sc/Bool ;; True if appeal was submitted from bulletin
                                :appellant-street sc/Str ;; Appellant's contact details if appeal from bulletin
                                :appellant-zip    sc/Str
                                :appellant-city   sc/Str
                                :appellant-email  sc/Str
                                :rectification-id sc/Str
                                })))

(def appeal-checker (sc/checker Appeal))

(defschema FrontendAppealFile
           "File presentation expected by the frontend from appeals query."
           {:fileId      ssc/FileId
            :filename    sc/Str
            :contentType sc/Str
            :size        sc/Num})

(defschema FrontendAppeal
           "Schema for Appeal presentation for frontend queries"
           (assoc Appeal :editable sc/Bool :files [FrontendAppealFile]))
