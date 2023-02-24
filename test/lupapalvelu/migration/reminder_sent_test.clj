(ns lupapalvelu.migration.reminder-sent-test
  (:require [lupapalvelu.migration.migrations :refer [refactor-reminders]]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]))

(def unset-both {$unset {:reminder-sent                    true
                         :work-time-expiring-reminder-sent true}})

(facts "unset-both"
  (refactor-reminders {}) => unset-both
  (refactor-reminders {:reminder-sent nil}) => unset-both
  (refactor-reminders {:reminder-sent                    nil
                       :work-time-expiring-reminder-sent nil}) => unset-both
  (refactor-reminders {:work-time-expiring-reminder-sent nil}) => unset-both)

(facts "reminder-sent"
  (refactor-reminders {:reminder-sent 12345})
  => {$unset {:work-time-expiring-reminder-sent true}
      $set   {:reminder-sent {:application-state 12345}}}
  (refactor-reminders {:reminder-sent                    12345
                       :work-time-expiring-reminder-sent nil})
  => {$unset {:work-time-expiring-reminder-sent true}
      $set   {:reminder-sent {:application-state 12345}}}
  (refactor-reminders {:reminder-sent                    12345
                       :work-time-expiring-reminder-sent 67890})
  => {$unset {:work-time-expiring-reminder-sent true}
      $set   {:reminder-sent {:application-state 12345
                              :work-time-expiring 67890}}}
  (refactor-reminders {:reminder-sent                    nil
                       :work-time-expiring-reminder-sent 67890})
  => {$unset {:work-time-expiring-reminder-sent true}
      $set   {:reminder-sent {:work-time-expiring 67890}}}
  (refactor-reminders {:work-time-expiring-reminder-sent 67890})
  => {$unset {:work-time-expiring-reminder-sent true}
      $set   {:reminder-sent {:work-time-expiring 67890}}})
