(ns lupapalvelu.notices.schemas
  (:require [lupapalvelu.quill :as quill]
            [lupapalvelu.user :as usr]
            [sade.schemas :as ssc]
            [schema.core :as sc]))

(sc/defschema ResolutionState
  (sc/enum "approved" "rejected"))

(sc/defschema PublishedResolution
  {:state                           ResolutionState
   :text                            [quill/QuillOp]
   :published                       ssc/Timestamp
   :user                            usr/SummaryUser
   ;; Job-id is replace with attachment-id after the attachment has been created.
   (sc/optional-key :job-id)        ssc/ObjectIdStr
   (sc/optional-key :attachment-id) ssc/ObjectIdStr})

(sc/defschema DraftResolution
  {(sc/optional-key :state) ResolutionState
   (sc/optional-key :text)  [quill/QuillOp]
   :modified                ssc/Timestamp
   :user                    usr/SummaryUser})

(sc/defschema Resolution
  (sc/conditional
    :published PublishedResolution
    :modified DraftResolution))

(def valid-resolution! (sc/validator Resolution))
(def check-resolution  (sc/checker Resolution))
