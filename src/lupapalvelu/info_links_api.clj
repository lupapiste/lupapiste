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
   :user-roles #{:authority}
   :user-authz-roles #{:statementGiver}
   :parameters [id linkId]
   :input-validators [(partial action/non-blank-parameters [:linkId])]
   :states states/all-states}
  [command]
  (println "Poistetaan linkki id:llä " linkId)
  (println " - numerona " (Integer/parseInt linkId 10))
  (ok :res (info-links/delete-info-link! (:application command) 
     (Integer/parseInt linkId 10))))

(defcommand info-link-reorder
   {:description "Reorder application-specific info-links"
   :user-roles #{:authority}
   :user-authz-roles #{:statementGiver}
   :parameters [id linkIds]
   :input-validators [(partial action/vector-parameters-with-non-blank-items [:linkIds])]
   ;:input-validators [(partial action/non-blank-parameters [:linkIds])] ;; todo: check why this fails in unexpected way
   :states states/all-states}
  [command]
  (let [ids (map #(Integer/parseInt % 10) linkIds)]
     (if (empty? (remove number? ids))
        (ok :res (info-links/reorder-info-links! (:application command) ids))
        (ok :res false))))

(defcommand info-link-upsert
  {:description "Add or update application-specific info-link"
   :user-roles #{:authority}
   :user-authz-roles #{:statementGiver}
   :parameters [id text url]
   :optional-parameters [linkId]
   :input-validators [(partial action/non-blank-parameters [:text]) 
                      (partial action/non-blank-parameters [:url])] 
   :states      states/all-states}
  [command]
  (let [app (:application command)
        res (if linkId
              (info-links/update-info-link! app linkId text url (:user command))
              (info-links/add-info-link! app text url (:user command)))]
     (if res
        (info-links/mark-links-seen! command))
     (ok :linkId res)))

(defquery info-links
  {:description "Return a list of application-specific info-links"
   :parameters [id]
   :user-roles #{:authority :applicant}
   :states      states/all-states}
  [command]
  (let [app (:application command)]
     (info-links/mark-links-seen! command)
     (ok :links (info-links/info-links-with-new-flag app (:user command)))))

