(ns lupapalvelu.admin-reports-api
  "Dynamic excel reports. Accessible via Reports tab in the admin
  admin UI."
  (:require [lupapalvelu.action :refer [defraw]]
            [sade.core :refer :all]
            [lupapalvelu.admin-reports :as reps]))

(defraw user-report
  {:description "User report. Yes|no|both parameterization for company
  user, allow direct marketing and professional (architect) users."
   :parameters [company allow professional]
   :input-validators [(reps/yes-no-both [:company :allow :professional])]
   :user-roles #{:admin}}
  [_]
  (apply reps/user-report (map reps/string->keyword [company allow professional])))
