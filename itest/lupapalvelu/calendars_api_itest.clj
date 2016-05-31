(ns lupapalvelu.calendars-api-itest
  (require [midje.sweet :refer :all]
           [lupapalvelu.itest-util :refer :all]))

(facts :ajanvaraus
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

  (fact "Add reservation type for organization"
    (command sipoo :add-reservation-type-for-organization :reservationType "Katselmus")
    (let [result (query sipoo :reservation-types-for-organization)]
      (map :name (:reservationTypes result)) => (just #{"Katselmus"})
      (clear-ajanvaraus-db)))

  (fact "Delete reservation type"
    (command sipoo :add-reservation-type-for-organization :reservationType "Katselmus")
    (let [result (query sipoo :reservation-types-for-organization)
          reservation-type-id (first (map :id (:reservationTypes result)))]
      (command sipoo :delete-reservation-type :reservationTypeId reservation-type-id) => ok?
      (let [foobar (query sipoo :reservation-types-for-organization)]
        (count (:reservationTypes foobar)) => 0))))
