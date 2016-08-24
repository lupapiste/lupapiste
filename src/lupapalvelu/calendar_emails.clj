(ns lupapalvelu.calendar-emails
  (:require [clj-time.local :refer [to-local-date-time local-now]]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.calendar :as cal :refer [update-reservation]]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.icalendar :as ical]
            [sade.util :as util]
            [sade.env :as env]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all])
  (:import (net.fortuna.ical4j.model.property Method)
           (net.fortuna.ical4j.model.parameter PartStat)))

(defn- generate-unique-id []
  (str (.toString (to-local-date-time (local-now))) "/" (java.util.UUID/randomUUID) "@lupapiste.fi"))

(defn- recipients-fn []
  (fn [{application :application result :result}]
    (let [reservation (util/find-by-id (:reservationId result) (:reservations application))
          recipient-ids (flatten (vals (select-keys reservation [:from :to])))]
      (map usr/get-user-by-id recipient-ids))))

(defn- model-fn []
  (fn [{application :application} _ recipient]
    {:link-fi (notifications/get-application-link application nil "fi" recipient)
     :link-sv (notifications/get-application-link application nil "sv" recipient)
     :info-fi (str (env/value :host) "/ohjeet")
     :info-sv (str (env/value :host) "/ohjeet")}))

(notifications/defemail
  :suggest-appointment
  {:subject-key                  "application.calendar.appointment.suggestion"
   :application-fn               (fn [{id :id}] (domain/get-application-no-access-checking id))
   :calendar-fn                  (fn [{application :application result :result} recipient]
                                   (let [reservation (util/find-by-id (:reservationId result) (:reservations application))
                                         reservation (assoc reservation :attendee recipient
                                                                        :unique-id (generate-unique-id)
                                                                        :sequence 0
                                                                        :method Method/REQUEST)]
                                     (cal/update-reservation application
                                                             (:reservationId result)
                                                             {$set {:reservations.$.unique-id (:unique-id reservation)
                                                                    :reservations.$.sequence  (:sequence reservation)}})
                                     (ical/create-calendar-event reservation)))
   :show-municipality-in-subject true
   :recipients-fn                (recipients-fn)
   :model-fn                     (model-fn)})

(notifications/defemail
  :decline-appointment
  {:subject-key                  "application.calendar.appointment.decline"
   :application-fn               (fn [{id :id}] (domain/get-application-no-access-checking id))
   :calendar-fn                  (fn [{application :application result :result} recipient]
                                   (let [reservation (util/find-by-id (:reservationId result) (:reservations application))
                                         reservation (assoc reservation :attendee (assoc recipient :partstat PartStat/DECLINED)
                                                                        :unique-id (:unique-id reservation)
                                                                        :sequence (inc (:sequence reservation))
                                                                        :method Method/REPLY)]
                                     (cal/update-reservation application
                                                             (:reservationId result)
                                                             {$set {:reservations.$.sequence (:sequence reservation)}})
                                     (ical/create-calendar-event reservation)))
   :show-municipality-in-subject true
   :recipients-fn                (recipients-fn)
   :model-fn                     (model-fn)})

(notifications/defemail
  :accept-appointment
  {:subject-key                  "application.calendar.appointment.accept"
   :application-fn               (fn [{id :id}] (domain/get-application-no-access-checking id))
   :calendar-fn                  (fn [{application :application result :result} recipient]
                                   (let [reservation (util/find-by-id (:reservationId result) (:reservations application))
                                         reservation (assoc reservation :attendee (assoc recipient :partstat PartStat/ACCEPTED)
                                                                        :unique-id (:unique-id reservation)
                                                                        :sequence (inc (:sequence reservation))
                                                                        :method Method/REPLY)]
                                     (cal/update-reservation application
                                                             (:reservationId result)
                                                             {$set {:reservations.$.sequence (:sequence reservation)}})
                                     (ical/create-calendar-event reservation)))
   :show-municipality-in-subject true
   :recipients-fn                (recipients-fn)
   :model-fn                     (model-fn)})