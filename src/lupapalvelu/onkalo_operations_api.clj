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
                 :explanation sc/Str]}
  (let [modified (sade.core/now)]
    (oo/undo-archiving application-id attachment-id modified explanation)))

(defendpoint-for usr/onkalo-user? [:post "/rest/onkalo/redo-archive-attachment"] false
  {:summary     "Changes attachment status to 'arkistoitu'."
   :description ""
   :parameters  [:application-id rest-schemas/ApplicationId
                 :attachment-id att/AttachmentId
                 :archivist usr/SummaryUser
                 :explanation sc/Str]}
  (let [modified (sade.core/now)]
    (oo/redo-archiving application-id attachment-id modified explanation)))
