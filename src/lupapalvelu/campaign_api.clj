(ns lupapalvelu.campaign-api
  "Campaign management and related functionality for company
  accounts."
  (:require [lupapalvelu.action :refer [defcommand defquery] :as action]
            [lupapalvelu.campaign :as camp]
            [sade.core :refer :all]))

(defcommand add-campaign

  {:description "Creates new campaign. Parameters:

  code: Campaign code string. Unique among
  campaigns (case-insensitive).

  starts, ends: ISO dates (e.g, 2017-2-27). Dates are in Finnish time.

  account5, account 15, account30: Account prices (€/m) during the

  discount period.  lastDiscountDate: Last date of the discount
  period. ISO date in Finnish time."
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
