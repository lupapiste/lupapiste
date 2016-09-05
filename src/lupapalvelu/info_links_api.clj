(ns lupapalvelu.info-links-api
  (:require [clojure.set :refer [union]]
            [monger.operators :refer :all]
            [sade.env :as env]
            [sade.util :as util]
            [sade.core :refer [ok fail fail!]]
            [sade.strings :as ss]
            [lupapalvelu.action :refer [defquery defcommand update-application notify] :as action]
            [lupapalvelu.application :as application]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.info-links :as info-links]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.open-inforequest :as open-inforequest]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as user]))


;;
;; API
;;

(defcommand info-link-delete
  {:description "Remove an application-specific info-link"
   :user-roles #{:authority :applicant}
   :parameters [id linkId]
   :input-validators [(partial action/non-blank-parameters [:linkId])]
   :org-authz-roles #{:authority}
   :states states/all-states}
  [command]
  (ok (info-links/delete-info-link! (:application command) linkId)))

(defcommand info-link-reorder
   {:description "Reorder application-specific info-links"
   :user-roles #{:authority :applicant}
   :parameters [id linkIds]
   :input-validators [(partial action/non-blank-parameters [:linkIds])]
   :org-authz-roles #{:authority}
   :states states/all-states}
  [command]
  (ok (info-links/reorder-info-links! (:application command) linkIds)))

(defcommand info-link-upsert
  {:description "Add or update application-specific info-link"
   :user-roles #{:authority :applicant}
   :parameters [id text url linkId] ; linkId optional
   :input-validators [(partial action/non-blank-parameters [:text]) 
                      (partial action/non-blank-parameters [:url])] 
   :org-authz-roles #{:authority}
   :states      states/all-states}
  [command]
  (let [app (:application command)]
     (ok
        (if linkId
           (info-links/update-info-link! app linkId text url)
           (info-links/add-info-link! app text url)))))

(defquery info-links
  {:description "Return a list of application-specific info-links"
   :parameters []
   :user-roles #{:authority :applicant}}
  [command]
  (let [app (:application command)]
     (ok (info-links/info-links app))))


