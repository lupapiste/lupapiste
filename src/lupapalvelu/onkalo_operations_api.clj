(ns lupapalvelu.onkalo-operations-api
  (:require [lupapalvelu.attachment :as att]
            [lupapalvelu.onkalo-operations :as oo]
            [lupapalvelu.rest.rest-api :refer [defendpoint-for]]
            [lupapalvelu.rest.schemas :as rest-schemas]
            [lupapalvelu.user :as usr]
            [sade.core :refer [ok unauthorized fail]]
            [schema.core :as sc]
            [taoensso.timbre :refer [info]]))

(defendpoint-for usr/onkalo-user? [:post "/rest/onkalo/undo-archive-attachment"] false
  {:summary     "Changes attachment status to 'valmis' and allows it to be edited."
   :description ""
   :parameters  [:application-id rest-schemas/ApplicationId
                 :attachment-id att/AttachmentId
                 :archivist usr/SummaryUser
                 :explanation sc/Str]
   :returns     rest-schemas/ApiResponse}
  (let [modified (sade.core/now)
        op-success? (oo/undo-archiving application-id attachment-id modified)]
    (info "Undo archiving from Onkalo for attachment" attachment-id "in application" application-id "with explanation" explanation "was" (if op-success? "successful" "unsuccessful"))
    (if op-success?
      (ok)
      (fail {:text :error.undo-archiving-failed}))))

(defendpoint-for usr/onkalo-user? [:post "/rest/onkalo/redo-archive-attachment"] false
  {:summary     "Changes attachment status to 'arkistoitu'."
   :description ""
   :parameters  [:application-id rest-schemas/ApplicationId
                 :attachment-id att/AttachmentId
                 :archivist usr/SummaryUser
                 :explanation sc/Str]
   :returns     rest-schemas/ApiResponse}
  (let [modified (sade.core/now)
        op-success? (oo/redo-archiving application-id attachment-id modified)]
    (info "Redo archiving from Onkalo for attachment" attachment-id "in application" application-id "with explanation" explanation "was" (if op-success? "successful" "unsuccessful"))
    (if op-success?
      (ok)
      (fail {:text :error.redo-archiving-failed}))))
