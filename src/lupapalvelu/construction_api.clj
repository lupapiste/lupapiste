(ns lupapalvelu.construction-api
  (:require [monger.operators :refer [$set $elemMatch]]
            [lupapalvelu.action :refer [defcommand update-application notify] :as action]
            [lupapalvelu.application :as application]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]
            [lupapalvelu.state-machine :as state-machine]
            [sade.core :refer :all]
            [sade.util :as util]))


;;
;; Inform construction started & ready
;;

(defcommand inform-construction-started
  {:parameters ["id" startedTimestampStr]
   :user-roles #{:applicant :authority}
   :states     #{:verdictGiven}
   :notified   true
   :on-success (notify :application-state-change)
   :pre-checks [(permit/validate-permit-type-is permit/YA)]
   :input-validators [(partial action/non-blank-parameters [:startedTimestampStr])]}
  [{:keys [user created application] :as command}]
  (let [timestamp (util/to-millis-from-local-date-string startedTimestampStr)]
    (update-application command (util/deep-merge
                                  (application/state-transition-update :constructionStarted created application user)
                                  {$set {:startedBy (select-keys user [:id :firstName :lastName])
                                         :started timestamp}})))
  (ok))

(defcommand inform-construction-ready
  {:parameters ["id" readyTimestampStr lang]
   :user-roles #{:authority}
   :states     #{:constructionStarted}
   :on-success (notify :application-state-change)
   :pre-checks [(permit/validate-permit-type-is permit/YA)
                (partial state-machine/validate-state-transition :closed)]
   :input-validators [(partial action/non-blank-parameters [:readyTimestampStr])]}
  [{user :user created :created orig-app :application org :organization :as command}]
  (let [timestamp    (util/to-millis-from-local-date-string readyTimestampStr)
        app-updates  {:modified created
                      :closed timestamp
                      :closedBy (select-keys user [:id :firstName :lastName])
                      :state :closed}
        application  (merge orig-app app-updates)
        organization @org
        krysp?       (organization/krysp-integration? organization (permit/permit-type application))]
    (when krysp?
      (mapping-to-krysp/save-application-as-krysp application lang application organization))
    (update-application command (util/deep-merge
                                  (application/state-transition-update :closed orig-app created user)
                                  {$set app-updates}))
    (ok :integrationAvailable krysp?)))
