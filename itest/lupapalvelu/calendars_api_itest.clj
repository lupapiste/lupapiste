(ns lupapalvelu.calendars-api-itest
    (require [midje.sweet :refer :all]
             [lupapalvelu.itest-util :refer :all]
             [clj-time.core :as t]
             [clj-time.coerce :as tc]))

(facts :ajanvaraus
  (def authority (apikey-for "sonja"))
  (def authority-id "777777777777777777888823")

  (apply-remote-fixture "ajanvaraus")

  (fact "non-authority-admin users should not be able to use the calendar admin API"
    (query pena :calendars-for-authority-admin) => fail?)

  (fact "authority-admin users should be able to use the calendar admin API"
    (query sipoo :calendars-for-authority-admin) => ok?)

  (fact "organization with calendars-enabled := false can not use the calendar API functions"
    (query veikko :my-calendars) => unauthorized?
    (query veikko :calendar :calendarId 999 :userId "foobar") => unauthorized?
    (command veikko :create-calendar-slots :calendarId 999 :slots []) => unauthorized?)

  (fact "calendars-for-authority-admin returns the users in the appropriate organization"
    (let [result   (:users (query sipoo :calendars-for-authority-admin))]
      (map :id result) => (just #{authority-id})))

  (fact "create calendar for bogus user fails"
    (command sipoo :set-calendar-enabled-for-authority :userId "123akuankka" :enabled true) => fail?)

  (fact "Add reservation type for organization"
    (command sipoo :add-reservation-type-for-organization :reservationType "Testityyppi")
    (command sipoo :add-reservation-type-for-organization :reservationType "Katselmus")
    (let [result (query sipoo :reservation-types-for-organization :organizationId "753-R")
          varaustyypit (:reservationTypes result)
          varaustyypit (zipmap (map (comp keyword :name) varaustyypit) (map :id varaustyypit))]
      (keys varaustyypit) => (just #{:Testityyppi :Katselmus})

      (fact "Delete reservation type created in the previous fact"
        (let [reservation-type-id (get varaustyypit :Katselmus)]
          (command sipoo :delete-reservation-type :reservationTypeId reservation-type-id) => ok?
          (let [foobar (query sipoo :reservation-types-for-organization :organizationId "753-R")]
            (count (:reservationTypes foobar)) => 1
            (map :name (:reservationTypes foobar)) => (just #{"Testityyppi"}))))))

  (fact "create calendar for existing user"
    (command sipoo :set-calendar-enabled-for-authority :userId "777777777777777777888823" :enabled true) => ok?)

  (let [calendars (:calendars (query authority :my-calendars))
        varaustyypit (:reservationTypes (query sipoo :reservation-types-for-organization :organizationId "753-R"))
        varaustyypit (zipmap (map (comp keyword :name) varaustyypit) (map :id varaustyypit))]
    (count calendars) => 1
    (count varaustyypit) => 1

    (let [authority-calendar-id (-> calendars first :id)
          current-year (-> (t/today) t/year)
          current-week (.getWeekOfWeekyear (t/today))]
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
              tomorrow-at-noon   (t/plus tomorrow-at-ten (-> 2 t/hours))
              tomorrow-year      (t/year tomorrow-at-ten)
              tomorrow-week      (.getWeekOfWeekyear tomorrow-at-ten)]
          (command authority :create-calendar-slots
                             :calendarId authority-calendar-id
                             :slots [{:start (tc/to-long tomorrow-at-ten)
                                      :end   (tc/to-long tomorrow-at-eleven)
                                      :reservationTypes [(get varaustyypit :Testityyppi)]}
                                     {:start (tc/to-long tomorrow-at-eleven)
                                      :end   (tc/to-long tomorrow-at-noon)
                                      :reservationTypes [(get varaustyypit :Testityyppi)]}]) => ok?
          (let [slots (:slots (query authority :calendar-slots
                                               :calendarId authority-calendar-id
                                               :week       tomorrow-week
                                               :year       tomorrow-year))]
            (count slots) => 2

            (fact "The available slot is deletable"
              (command authority :delete-calendar-slot :slotId (-> slots first :id)) => ok?
              (count (:slots (query authority :calendar-slots
                                              :calendarId authority-calendar-id
                                              :week       current-week
                                              :year       current-year))) => 1))))

      (fact "With application"
        (let [app-id (create-app-id pena)]
          (fact "Reservation API functions"
            (fact "Find available slots as authority"
              (let [result (query authority :available-calendar-slots
                                  :authorityId       authority-id
                                  :clientId          pena-id
                                  :reservationTypeId (get varaustyypit :Testityyppi)
                                  :id   app-id
                                  :year current-year
                                  :week current-week)]
                result => ok?
                (count (:slots result)) => 1))
            (fact "Find available slots as applicant"
              (let [result (query pena :available-calendar-slots
                                  :authorityId       authority-id
                                  :clientId          pena-id
                                  :reservationTypeId (get varaustyypit :Testityyppi)
                                  :id   app-id
                                  :year current-year
                                  :week current-week)]
                result => ok?
                (count (:slots result)) => 1))
            (fact "Find available slots as applicant without the correct application in context should fail"
              (let [result (query pena :available-calendar-slots
                                  :authorityId       authority-id
                                  :clientId          pena-id
                                  :reservationTypeId (get varaustyypit :Testityyppi)
                                  :year current-year
                                  :week current-week)]
                result => unauthorized?)))))))

  (fact "clear db"
    (clear-ajanvaraus-db)))
