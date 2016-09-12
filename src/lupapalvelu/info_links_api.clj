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
  {:description      "Remove an application-specific info-link"
   :user-roles       #{:authority :applicant}
   :user-authz-roles #{:statementGiver}
   :parameters       [id linkId]
   :input-validators [(partial action/non-blank-parameters [:linkId])]
   :states           (states/all-states-but :draft)}
  [command]
  (if (info-links/delete-info-link! (:application command) linkId)
    (ok :res true)
    (fail :error.badlink)))

(defcommand info-link-reorder
  {:description      "Reorder application-specific info-links"
   :user-roles       #{:authority :applicant}
   :user-authz-roles #{:statementGiver}
   :parameters       [id linkIds]
   :input-validators [(partial action/vector-parameters-with-non-blank-items [:linkIds])]
   :states           (states/all-states-but :draft)}
  [command]
  (println "Reordering links by " linkIds)
  (if (info-links/reorder-info-links! (:application command) linkIds)
    (ok :res true)
    (fail :error.badlinks)))

(defcommand info-link-upsert
  {:description         "Add or update application-specific info-link"
   :user-roles          #{:authority :applicant}
   :user-authz-roles    #{:statementGiver}
   :parameters          [id text url]
   :optional-parameters [linkId]
   :input-validators    [(partial action/non-blank-parameters [:text])
                         (partial action/non-blank-parameters [:url])
                         (partial action/optional-parameter-of :linkId #(not (= "" %)))
                         ]
   :states              (states/all-states-but :draft)}
  [command]
  (let [app (:application command)
        timestamp (:created command)
        res (if linkId
              (info-links/update-info-link! app linkId text url timestamp)
              (info-links/add-info-link! app text url timestamp))]
    (if res
       (ok :linkId res)
       (fail :error.badlink))))

(defquery info-links
  {:description      "Return a list of application-specific info-links"
   :parameters       [id]
   :user-roles       #{:authority :applicant}
   :user-authz-roles auth/all-authz-roles
   :org-authz-roles  auth/reader-org-authz-roles
   :states           states/all-states}
  [command]
  (let [app (:application command)]
    (ok :links (info-links/info-links-with-flags app (:user command)))))

