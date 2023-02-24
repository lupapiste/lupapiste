(ns lupapalvelu.admin-reports-api
  "Dynamic excel reports. Accessible via Reports tab in the admin
  admin UI."
  (:require [lupapalvelu.action :refer [defraw defquery defcommand] :as action]
            [lupapalvelu.admin-reports :as reps]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [schema.core :as sc]))

(defraw user-report
  {:description      "User report. Yes|no|both parameterization for company
  user, allow direct marketing and professional (architect) users."
   :parameters       [company allow professional]
   :input-validators [(reps/yes-no-both [:company :allow :professional])]
   :user-roles       #{:admin}}
  [_]
  (apply reps/user-report (map reps/string->keyword [company allow professional])))

(defcommand upsert-company-unsubscribed
  {:description      "Email addresses that do not wish to receive company
  email (Spam column value false in the excel). The parameter is a
  string where the email addresses are separated by whitespace."
   :parameters       [emails]
   :input-validators [(partial action/string-parameters [:emails])]
   :user-roles       #{:admin}}
  [_]
  (reps/upsert-company-unsubscribed emails))

(defquery company-unsubscribed-emails
  {:description "Email addresses that do not wish to receive company
  email."
   :user-roles  #{:admin}}
  [_]
  (ok :emails (reps/company-unsubscribed-emails)))

(defn parse-year
  "Parsed unsigned integer or nil."
  [s]
  (try
    (some-> (ss/trim s) ss/blank-as-nil Integer/parseUnsignedInt)
    (catch Exception _ nil)))

(defraw waste-report
  {:description      "Waste report. Includes both regular and extended.  Year is the year when
  the application was given a verdict. Note: no longer an admin, but an authority admin
  report."
   :parameters       [organizationId year]
   :input-validators [(partial action/non-blank-parameters [:organizationId])
                      (fn [command]
                        (when-not (some-> command :data :year parse-year)
                          (fail :a11y.error.year)))]
   :permissions      [{:required [:organization/admin]}]}
  [_]
  (reps/waste-report organizationId (parse-year year)))

(defquery applications-per-month-report
  {:user-roles       #{:admin}
   :input-validators [(partial action/non-blank-parameters [:month :year])]
   :parameters       [month year]}
  [_]
  (when-not (and (re-matches #"[0-9]+" month)
                 (#{1 2 3 4 5 6 7 8 9 10 11 12} (read-string month))
                 (re-matches #"[0-9]{4}" year))
    (fail! :error.invalid-month-or-year))
  (let [month-number         (read-string month)
        year-number          (read-string year)
        data                 (concat
                               (reps/applications-per-month-per-permit-type month-number year-number)
                               (reps/archiving-projects-per-month-query month-number year-number)
                               (reps/prev-permits-per-month-query month-number year-number))
        designer-and-foreman (reps/designer-and-foreman-applications-per-month
                               month-number year-number)]
    (ok :applications (map (fn [{permitType :permitType :as m}]
                             (if (= permitType "R")
                               (assoc m :operations designer-and-foreman)
                               m)) data))))

(defn resolve-period
  "Returns begin of start day and end of last day timestamps, just to make sure."
  [{{:keys [startTs endTs]} :data}]
  (when (and startTs endTs)
    (map date/timestamp [(date/start-of-day startTs) (date/end-of-day endTs)])))

(defn start-before-end
  [command]
  (when-let [[start-ts end-ts] (resolve-period command)]
    (when-not (date/before? start-ts end-ts)
      (fail :error.invalid-date))))

(defraw verdicts-contracts-report
  {:description      "Some details on the applications with a verdict or agreement signed date
  within the given period."
   :parameters       [:startTs :endTs]
   :input-validators [(partial action/numeric-parameters [:startTs :endTs])
                      start-before-end]
   :user-roles       #{:admin}}
  [command]
  (apply reps/verdicts-contracts-report
         (resolve-period command)))
