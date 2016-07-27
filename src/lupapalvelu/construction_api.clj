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
  [{:keys [user created] :as command}]
  (let [timestamp (util/to-millis-from-local-date-string startedTimestampStr)]
    (update-application command (util/deep-merge
                                  (application/state-transition-update :constructionStarted created user)
                                  {$set {:startedBy (select-keys user [:id :firstName :lastName])
                                         :started timestamp}})))
  (ok))

(comment
  (defcommand inform-building-construction-started
       {:parameters ["id" buildingIndex startedDate lang]
        ; rakentamisen aikaisen toimminan yhteydessa korjataan oikeae
        ;:user-roles ???
        :states     #{:verdictGiven :constructionStarted}
        :notified   true
        :pre-checks [(permit/validate-permit-type-is permit/R)]
        :input-validators [(partial action/non-blank-parameters [:buildingIndex :startedDate :lang])]}
       [{:keys [user created application organization] :as command}]
    (let [timestamp    (util/to-millis-from-local-date-string startedDate)
          app-updates  (merge
                        {:modified created}
                        (when
                            {:started created
                             :state  :constructionStarted}))
          application  (merge application app-updates)
          organization @organization
          krysp?       (organization/krysp-integration? organization (permit/permit-type application))
          building     (or
                        (some #(when (= (str buildingIndex) (:index %)) %) (:buildings application))
                        (fail! :error.unknown-building))]
         (when krysp?
           (mapping-to-krysp/save-aloitusilmoitus-as-krysp application lang organization timestamp building user))
         (update-application command
           {:buildings {$elemMatch {:index (:index building)}}}
           (util/deep-merge
             {$set {:modified created
                    :buildings.$.constructionStarted timestamp
                    :buildings.$.startedBy (select-keys user [:id :firstName :lastName])}}
             (when (states/verdict-given-states (keyword (:state application)))
               (application/state-transition-update :constructionStarted created user))
             ))
         (when (states/verdict-given-states (keyword (:state application)))
           (notifications/notify! :application-state-change command))
         (ok :integrationAvailable krysp?))))

(defcommand inform-construction-ready
  {:parameters ["id" readyTimestampStr lang]
   :user-roles #{:authority}
   :states     #{:constructionStarted}
   :on-success (notify :application-state-change)
   :pre-checks [(permit/validate-permit-type-is permit/YA)
                (partial state-machine/validate-state-transition :closed)]
   :input-validators [(partial action/non-blank-parameters [:readyTimestampStr])]}
  [{:keys [user created application organization] :as command}]
  (let [timestamp    (util/to-millis-from-local-date-string readyTimestampStr)
        app-updates  {:modified created
                      :closed timestamp
                      :closedBy (select-keys user [:id :firstName :lastName])
                      :state :closed}
        application  (merge application app-updates)
        organization @organization
        krysp?       (organization/krysp-integration? organization (permit/permit-type application))]
    (when krysp?
      (mapping-to-krysp/save-application-as-krysp application lang application organization))
    (update-application command (util/deep-merge
                                  (application/state-transition-update :closed created user)
                                  {$set app-updates}))
    (ok :integrationAvailable krysp?)))
