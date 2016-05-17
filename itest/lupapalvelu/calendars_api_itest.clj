(ns lupapalvelu.calendars-api-itest
  (require [midje.sweet :refer :all]
           [lupapalvelu.itest-util :refer :all]))

(apply-remote-minimal) ; minimal ensures wanted organization tags exist

(fact "non-authority-admin users should not be able to use the calendar admin API"
  (query pena :calendars-for-authority-admin) => unauthorized?)

(fact "authority-admin users should be able to use the calendar admin API"
  (query sipoo :calendars-for-authority-admin) => ok?)

#_(fact "calendars-for-authority-admin returns the users in the appropriate organization"
      (->> (query sipoo :calendars-for-authority-admin)
           :users
           (group-by :organization)
           keys) => (just #{"753-R"}))
