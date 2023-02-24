(ns lupapalvelu.campaign-api
  "Campaign management and related functionality for company
  accounts."
  (:require [lupapalvelu.action :refer [defcommand defquery] :as action]
            [lupapalvelu.campaign :as camp]
            [sade.core :refer :all]))

(defcommand add-campaign
  {:description "Creates new campaign. Parameters:

  code: Campaign code string. Unique among
  campaigns (case-insensitive, will be trimmed).

  starts, ends: ISO dates (e.g, 2017-02-27). Dates are in Finnish time.

  account5, account 15, account30: Account prices (EUR/month) during
  the discount period.

  lastDiscountDate: Last date of the discount period. ISO date in
  Finnish time."
   :user-roles       #{:admin}
   :input-validators [camp/good-campaign]}
  [command]
  (camp/add-campaign command))

(defcommand delete-campaign
  {:user-roles       #{:admin}
   :parameters       [code]
   :input-validators [(partial action/non-blank-parameters [:code])]}
  [_]
  (camp/delete-campaign code)
  (ok))

(defquery campaigns
  {:description "Every campaign."
   :user-roles  #{:admin}}
  [_]
  (ok :campaigns (camp/campaigns)))

(defquery campaign
  {:description "Individual campaign information. Return campaign if
  the given code refers to an active campaign."
   :user-roles       #{:anonymous}
   :parameters       [code]
   :input-validators [(partial action/non-blank-parameters [:code])]}
  [{created :created}]
  (if-let [campaign (camp/active-campaign code created)]
    (ok :campaign campaign)
    (fail :error.campaign-not-found)))
