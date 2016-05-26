(ns lupapalvelu.calendars-api-itest
  (require [midje.sweet :refer :all]
           [lupapalvelu.itest-util :refer :all]
           [lupapalvelu.fixture.minimal :as minimal]
           [lupapalvelu.user :as u]))

(apply-remote-fixture "ajanvaraus")

(fact "non-authority-admin users should not be able to use the calendar admin API"
  (query pena :calendars-for-authority-admin) => fail?)

(fact "authority-admin users should be able to use the calendar admin API"
  (query sipoo :calendars-for-authority-admin) => ok?)

(fact "calendars-for-authority-admin returns the users in the appropriate organization"
  (let [result   (:users (query sipoo :calendars-for-authority-admin))]
    (map :id result) => (just #{"777777777777777777888823"})))

(fact "create calendar for bogus user fails"
  (command sipoo :set-calendar-enabled-for-authority :userId "123akuankka" :enabled true) => fail?)

(fact "create calendar for existing user"
  (command sipoo :set-calendar-enabled-for-authority :userId "777777777777777777888823" :enabled true) => ok?)