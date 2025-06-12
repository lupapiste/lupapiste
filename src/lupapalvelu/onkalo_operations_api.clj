(ns lupapalvelu.onkalo-operations-api
  (:require [lupapalvelu.onkalo-operations :as oo]
            [lupapalvelu.rest.rest-api :refer [defendpoint-for]]
            [lupapalvelu.rest.schemas :as rest-schemas]
            [lupapalvelu.user :as usr]
            [sade.schemas :refer [AttachmentId]]
            [schema.core :as sc]))

(defendpoint-for usr/onkalo-user? [:post "/rest/onkalo/change-attachment-archival-status"] false
  {:summary     "Changes attachment status to 'valmis' and allows it to be edited."
   :description ""
   :parameters  [:application-id rest-schemas/ApplicationId
                 :attachment-id AttachmentId
                 :target-state sc/Str
                 :deletion-explanation sc/Str]}
  (oo/attachment-archiving-operation application-id attachment-id (keyword target-state) deletion-explanation))
