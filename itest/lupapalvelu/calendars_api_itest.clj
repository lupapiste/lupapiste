(ns lupapalvelu.calendars-api-itest
  (require [midje.sweet :refer :all]
           [lupapalvelu.itest-util :refer :all]
           [lupapalvelu.fixture.minimal :as minimal]
           [lupapalvelu.user :as u]))

(apply-remote-minimal) ; minimal ensures wanted organization tags exist

(fact "non-authority-admin users should not be able to use the calendar admin API"
  (query pena :calendars-for-authority-admin) => unauthorized?)

(fact "authority-admin users should be able to use the calendar admin API"
  (query sipoo :calendars-for-authority-admin) => ok?)

(fact "calendars-for-authority-admin returns the users in the appropriate organization"
      (let [result   (:users (query sipoo :calendars-for-authority-admin))
            expected (filter #(u/user-is-authority-in-organization? % "753-R") minimal/users)]
        (map :id result) => (just (map :id expected) :in-any-order)))