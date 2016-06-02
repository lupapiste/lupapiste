(ns lupapalvelu.calendars-api-itest
    (require [midje.sweet :refer :all]
             [lupapalvelu.itest-util :refer :all]
             [clj-time.core :as t]
             [clj-time.coerce :as tc]))

(facts :ajanvaraus
  (def authority (apikey-for "sonja"))

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
    (command sipoo :set-calendar-enabled-for-authority :userId "777777777777777777888823" :enabled true) => ok?
    (let [calendars (:calendars (query authority :my-calendars))]
      (count calendars) => 1

      (let [authority-calendar-id (-> calendars first :id)]
        (fact "Add reservation type for organization"
          (command sipoo :add-reservation-type-for-organization :reservationType "Testityyppi")
          (command sipoo :add-reservation-type-for-organization :reservationType "Katselmus")
          (let [result (query sipoo :reservation-types-for-organization)
                varaustyypit (:reservationTypes result)
                varaustyypit (zipmap (map (comp keyword :name) varaustyypit) (map :id varaustyypit))]
            (keys varaustyypit) => (just #{:Testityyppi :Katselmus})

            (fact "Delete reservation type created in the previous fact"
              (let [reservation-type-id (get varaustyypit :Katselmus)]
                (command sipoo :delete-reservation-type :reservationTypeId reservation-type-id) => ok?
                (let [foobar (query sipoo :reservation-types-for-organization)]
                  (count (:reservationTypes foobar)) => 1
                  (map :name (:reservationTypes foobar)) => (just #{"Testityyppi"}))))

            (fact "Create calendar slots in the past time will fail"
              (let [yesterday-ay-ten    (t/minus (t/today-at 10 00) (-> 1 t/days))
                    yesterday-ay-eleven (t/plus yesterday-ay-ten (-> 1 t/hours))]
                (command authority :create-calendar-slots
                         :calendarId authority-calendar-id
                         :slots [{:start (tc/to-long yesterday-ay-ten)
                                  :end   (tc/to-long yesterday-ay-eleven)
                                  :reservationTypes [(get varaustyypit :Testityyppi)]}]) => fail?))

            (fact "Create calendar slots for tomorrow"
              (let [tomorrow-at-ten    (t/plus (t/today-at 10 00) (-> 1 t/days))
                    tomorrow-at-eleven (t/plus tomorrow-at-ten (-> 1 t/hours))
                    current-year (-> tomorrow-at-ten t/year)
                    current-week (.getWeekOfWeekyear tomorrow-at-ten)]
                (command authority :create-calendar-slots
                                   :calendarId authority-calendar-id
                                   :slots [{:start (tc/to-long tomorrow-at-ten)
                                            :end   (tc/to-long tomorrow-at-eleven)
                                            :reservationTypes [(get varaustyypit :Testityyppi)]}]) => ok?
                (let [slots (:slots (query authority :calendar-slots
                                         :calendarId authority-calendar-id
                                         :week       current-week
                                         :year       current-year))]
                  (count slots) => 1

                  (fact "The available slot is deleteable"
                    (command authority :delete-calendar-slot :slotId (-> slots first :id)) => ok?
                    (count (:slots (query authority :calendar-slots
                                                    :calendarId authority-calendar-id
                                                    :week       current-week
                                                    :year       current-year))) => 0)))))))))

  (fact "clear db"
    (clear-ajanvaraus-db)))
