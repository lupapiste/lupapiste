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
      (map :id result) => (just #{authority-id ronja-id})))

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
        (let [tomorrow-at-ten (t/plus (t/today-at 10 00) (-> 1 t/days))
              slot-times      (for [x (range 1 5)] (t/plus tomorrow-at-ten (-> x t/hours)))
              slots           (map (fn [time]
                                     {:start (tc/to-long time)
                                      :end (tc/to-long (t/plus time (-> 1 t/hours)))
                                      :reservationTypes [(get varaustyypit :Testityyppi)]}) slot-times)
              tomorrow-year   (t/year tomorrow-at-ten)
              tomorrow-week   (.getWeekOfWeekyear tomorrow-at-ten)]
          (command authority :create-calendar-slots
                             :calendarId authority-calendar-id
                             :slots      (vec slots)) => ok?
          (let [slots (:slots (query authority :calendar-slots
                                               :calendarId authority-calendar-id
                                               :week       tomorrow-week
                                               :year       tomorrow-year))]
            (count slots) => 4

            (fact "The available slot is deletable"
              (command authority :delete-calendar-slot :slotId (-> slots first :id)) => ok?
              (count (:slots (query authority :calendar-slots
                                              :calendarId authority-calendar-id
                                              :week       current-week
                                              :year       current-year))) => 3))))

      (fact "With application"
        (let [app-id (create-app-id pena)]
          ; Invite & approve Teppo
          (command pena :invite-with-role :id app-id :email (email-for-key teppo) :role "writer" :text "wilkommen" :documentName "" :documentId "" :path "") => ok?
          (command teppo :approve-invite :id app-id) => ok?
          ;clear inbox
          (sent-emails)

          (fact "Application calendar config"
            (let [result (query pena :application-calendar-config
                                     :id app-id)]
              result => ok?
              (map :id (:reservationTypes result)) => (vals varaustyypit)))
          (fact "Reservation API functions"
            (fact "Find available slots as authority"
              (let [result (query authority :available-calendar-slots
                                  :authorityId       authority-id
                                  :clientId          pena-id
                                  :reservationTypeId (get varaustyypit :Testityyppi)
                                  :id   app-id
                                  :year current-year
                                  :week current-week)
                    available-slots (:availableSlots result)]
                result => ok?
                (count available-slots) => 3
                (fact "Authority invites the applicant"
                  (let [result (command authority :reserve-calendar-slot
                                        :clientId pena-id
                                        :authorityId authority-id
                                        :reservationTypeId (get varaustyypit :Testityyppi)
                                        :id app-id
                                        :slotId (:id (first available-slots))
                                        :comment "diipadaapa"
                                        :location "paikka")
                        reservation-id (:reservationId result)]
                    result => ok?
                    (fact "Email notifications with calendar event have been sent"
                      (let [emails (sent-emails)]
                        (count emails) => 2
                        (:to (first emails)) => (contains "Sonja Sibbo")
                        (:calendar (:body (first emails))) => (every-checker (contains "BEGIN:VCALENDAR")
                                                                             (contains "METHOD:REQUEST"))
                        (:to (last emails)) => (contains "Pena Panaani")
                        (:calendar (:body (last emails))) => (every-checker (contains "BEGIN:VCALENDAR")
                                                                            (contains "METHOD:REQUEST"))))
                    (fact "my-reserved-slots for authority includes the new reservation"
                      (->> (query authority :my-reserved-slots :year current-year :week current-week)
                           :reservations
                           (map #(get-in % [:reservation :id]))) => (contains #{reservation-id}))
                    (fact "another authority in the same organization sees the reservation as a 'read-only' slot"
                      (->> (query ronja :available-calendar-slots
                             :authorityId ronja-id
                             :clientId pena-id
                             :reservationTypeId (get varaustyypit :Testityyppi)
                             :id   app-id
                             :year current-year
                             :week current-week)
                           :readOnlySlots
                           (map #(get-in % [:reservation :id]))) => (just #{reservation-id}))
                    (fact "Calendar can not be disabled if it has reservations in the future"
                      (command sipoo :set-calendar-enabled-for-authority
                                     :userId authority-id :enabled false) => fail?)
                    (fact "Pena declines the invitation"
                      (command pena :decline-reservation
                                    :reservationId reservation-id
                                    :id app-id) => ok?)))))
            (fact "Find available slots as applicant"
              (let [result (query pena :available-calendar-slots
                                  :authorityId       authority-id
                                  :clientId          pena-id
                                  :reservationTypeId (get varaustyypit :Testityyppi)
                                  :id   app-id
                                  :year current-year
                                  :week current-week)]
                result => ok?
                (count (:availableSlots result)) => 3))
            (fact "Find available slots as applicant without the correct application in context should fail"
              (let [result (query pena :available-calendar-slots
                                  :authorityId       authority-id
                                  :clientId          pena-id
                                  :reservationTypeId (get varaustyypit :Testityyppi)
                                  :id "LP-001-2016-99999"
                                  :year current-year
                                  :week current-week)]
                result => fail?)))))))

  (fact "clear db"
    (clear-ajanvaraus-db)))
