(ns lupapalvelu.campaign-api
  "Campaign management and related functionality for company
  accounts."
  (:require [lupapalvelu.action :refer [defcommand defquery] :as action]
            [lupapalvelu.campaign :as camp]
            [sade.core :refer :all]))

(defcommand add-campaign
  {:description "Creates new campaign. A campaign includes campaign
  code, prices, active period and a message. See campaign namespace
  for details."
   :user-roles #{:admin}
   :input-validators [camp/good-campaign]
   :feature camp/campaign-feature}
  [command]
  (camp/add-campaign command))

(defquery campaigns
  {:description "Every campaign."
   :user-roles #{:admin}
   :feature camp/campaign-feature}
  [_]
  (ok :campaigns (camp/campaigns)))

(defquery campaign
  {:description "Individual campaign information"
   :user-roles #{:anonymous}
   :parameters [code]
   :input-validators [(partial action/non-blank-parameters [:code])]
   :feature camp/campaign-feature}
  [{created :created}]
  (if-let [campaign (camp/active-campaign code created)]
    (ok :campaign campaign)
    (fail :error.campaign-not-found)))
