(ns lupapalvelu.admin-reports-api
  "Dynamic excel reports. Accessible via Reports tab in the admin
  admin UI."
  (:require [lupapalvelu.action :refer [defraw defquery]]
            [sade.core :refer :all]
            [lupapalvelu.admin-reports :as reps]
            [lupapalvelu.action :as action]
            [sade.util :as util]))

(defraw user-report
  {:description "User report. Yes|no|both parameterization for company
  user, allow direct marketing and professional (architect) users."
   :parameters [company allow professional]
   :input-validators [(reps/yes-no-both [:company :allow :professional])]
   :user-roles #{:admin}}
  [_]
  (apply reps/user-report (map reps/string->keyword [company allow professional])))

(defraw waste-report
  {:description "Waste report. Includes both regular and extended."
   :user-roles #{:admin}}
  [_]
  (reps/waste-report))

(defquery applications-per-month-report
  {:user-roles       #{:admin}
   :input-validators [(partial action/non-blank-parameters [:month :year])]
   :parameters       [month year]}
  [_]
  (when-not (and (re-matches #"[0-9]+" month)
                 (#{1 2 3 4 5 6 7 8 9 10 11 12} (read-string month))
                 (re-matches #"[0-9]{4}" year))
    (fail! :error.invalid-month-or-year))
  (let [month-number (read-string month)
        year-number (read-string year)
        data (concat
               (reps/applications-per-month-per-permit-type month-number year-number)
               (reps/archiving-projects-per-month-query month-number year-number))
        designer-and-foreman (reps/designer-and-foreman-applications-per-month
                               month-number year-number)]
    (ok :applications (map (fn [{permitType :permitType :as m}]
                             (if (= permitType "R")
                               (assoc m :operations designer-and-foreman)
                               m)) data))))