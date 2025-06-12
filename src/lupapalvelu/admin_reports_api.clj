(ns lupapalvelu.admin-reports-api
  "Dynamic excel reports. Accessible via Reports tab in the admin
  admin UI."
  (:require [lupapalvelu.action :refer [defcommand defquery defraw] :as action]
            [lupapalvelu.admin-reports :as reps]
            [sade.core :refer :all]
            [sade.date :as date]
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
    (when-let [year (some-> (ss/trim s) ss/blank-as-nil parse-long)]
      (when-not (neg? year)
        year))
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

(defraw operation-areas-report
  {:description      "Operation-specific area data for
  applications with a verdict or agreement signed date within the given period."
   :parameters       [:startTs :endTs]
   :input-validators [(partial action/numeric-parameters [:startTs :endTs])
                      start-before-end]
   :user-roles       #{:admin}}
  [command]
  (apply reps/operation-areas-report
         (resolve-period command)))

(defraw applications-period-report
  {:description      "Applications for the given period. The
  meaningful timestamp depends on the application permit type and primary operation."
   :parameters       [:startTs :endTs]
   :input-validators [(partial action/numeric-parameters [:startTs :endTs])
                      start-before-end]
   :user-roles       #{:admin}}
  [command]
  (apply reps/applications-period-report
         (resolve-period command)))

(defraw reviews-period-report
  {:description      "Reviews report for that last 6 months"
   :parameters       [organizationId]
   :input-validators [(partial action/non-blank-parameters [:organizationId])]
   :permissions      [{:required [:organization/admin]}]}
  [command]
  (reps/review-report organizationId
                      (-> (date/today) (date/minus :months 6) date/timestamp)
                      (now)))

(defraw archivable-applications-report
  {:description      "Count archivable applications per organization"
   :parameters       [:startTs :endTs]
   :input-validators [{:startTs (sc/pred ss/numeric?)
                       :endTs   (sc/pred ss/numeric?)}]
   :user-roles       #{:admin}}
  [{{:keys [startTs endTs]} :data}]
  (reps/archivable-applications-report {:modified-after  (date/timestamp (date/start-of-day startTs))
                                        :modified-before (date/timestamp (date/end-of-day endTs))}))
