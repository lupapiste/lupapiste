(ns lupapalvelu.calendar-emails
  (:require [clj-time.local :refer [to-local-date-time local-now]]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.calendar :as cal :refer [update-reservation]]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.icalendar :as ical]
            [sade.util :as util]
            [sade.env :as env]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [lupapalvelu.i18n :as i18n])
  (:import (net.fortuna.ical4j.model.property Method)
           (net.fortuna.ical4j.model.parameter PartStat)))

(defn- generate-unique-id []
  (str (to-local-date-time (local-now)) "/" (java.util.UUID/randomUUID) "@lupapiste.fi"))

(defn- recipients-fn []
  (fn [{application :application result :result}]
    (let [reservation (util/find-by-id (:reservationId result) (:reservations application))
          recipient-ids (flatten (vals (select-keys reservation [:from :to])))]
      (map usr/get-user-by-id recipient-ids))))

(def model
  (fn [{application :application} _ recipient]
    {:link-calendar-fi (notifications/get-application-link application "calendar" "fi" recipient)
     :link-calendar-sv (notifications/get-application-link application "calendar" "sv" recipient)}))

(defn- display-names [users]
  (sade.strings/join ", " (map (fn [user] (str (:firstName user) " " (:lastName user))) users)))

(defn- reservation-participants [reservation]
  (map usr/get-user-by-id (flatten (vals (select-keys reservation [:from :to])))))

(def suggest-appointment-model
  (fn [{application :application result :result} _ recipient]
    (let [reservation (util/find-by-id (:reservationId result) (:reservations application))]
      {:reservation      {:startTime       (util/to-local-datetime (:startTime reservation))
                          :participants    (display-names (reservation-participants reservation))
                          :reservationType (:reservationType reservation)
                          :comment         (:comment reservation)
                          :location        (:location reservation)}
       :address          (:address application)
       :municipality     (i18n/localize-fallback nil (str "municipality." (:municipality application)))
       :link-calendar-fi (notifications/get-application-link application "calendar" "fi" recipient)
       :link-calendar-sv (notifications/get-application-link application "calendar" "sv" recipient)
       :user-first-name  (:firstName recipient)})))

(defn calendar-request
  [method partstat]
  (fn [{application :application result :result} recipient]
    (let [reservation (util/find-by-id (:reservationId result) (:reservations application))
          reservation (assoc reservation :title (str "[Lupapiste] " (get-in reservation [:reservationType :name])
                                                     " " (:address application))
                                         :attendee (if partstat
                                                     (assoc recipient :partstat partstat)
                                                     recipient)
                                         :unique-id (if (:unique-id reservation)
                                                      (:unique-id reservation)
                                                      (generate-unique-id))
                                         :sequence (if (:sequence reservation)
                                                     (inc (:sequence reservation))
                                                     0)
                                         :method method)]
      ; ICAL part not sent for reservations pending approval by the applicant,
      ; OR if the reservation is declined unless the ICAL part was previously sent
      (when-not (or (#{"PENDING"} (:reservationStatus reservation))
                    (and (#{"DECLINED"} (:reservationStatus reservation)) (= 0 (:sequence reservation))))
        (cal/update-reservation application
                                (:reservationId result)
                                {$set {:reservations.$.unique-id (:unique-id reservation)
                                       :reservations.$.sequence  (:sequence reservation)}})
        (ical/create-calendar-event reservation)))))

(defn filtered-recipients-fn
  [pred]
  (fn [{application :application result :result}]
    (let [reservation (util/find-by-id (:reservationId result) (:reservations application))
          recipient-ids (flatten (vals (select-keys reservation [:from :to])))]
      (filter (fn [recipient] (pred recipient reservation)) (map usr/get-user-by-id recipient-ids)))))

(notifications/defemail
  :suggest-appointment-authority
  {:subject-key                  "application.calendar.appointment.new"
   :application-fn               (fn [{id :id}] (domain/get-application-no-access-checking id))
   :calendar-fn                  (calendar-request Method/REQUEST nil)
   :recipients-fn                (filtered-recipients-fn
                                   (fn [recipient _] (usr/authority? recipient)))
   :model-fn                     suggest-appointment-model})

(notifications/defemail
  :suggest-appointment-from-applicant
  {:subject-key                  "application.calendar.appointment.new"
   :application-fn               (fn [{id :id}] (domain/get-application-no-access-checking id))
   :calendar-fn                  (calendar-request Method/REQUEST nil)
   :recipients-fn                (filtered-recipients-fn
                                   (fn [recipient reservation]
                                     (and (usr/applicant? recipient)
                                          (= (:from reservation) (:id recipient)))))
   :model-fn                     suggest-appointment-model})

(notifications/defemail
  :suggest-appointment-to-applicant
  {:subject-key                  "application.calendar.appointment.suggestion"
   :application-fn               (fn [{id :id}] (domain/get-application-no-access-checking id))
   :calendar-fn                  (calendar-request Method/REQUEST nil)
   :recipients-fn                (filtered-recipients-fn
                                   (fn [recipient reservation]
                                     (and (usr/applicant? recipient)
                                          (util/contains-value? (:to reservation) (:id recipient)))))
   :model-fn                     suggest-appointment-model})

(notifications/defemail
  :decline-appointment
  {:subject-key                  "application.calendar.appointment.decline"
   :application-fn               (fn [{id :id}] (domain/get-application-no-access-checking id))
   :calendar-fn                  (calendar-request Method/CANCEL PartStat/DECLINED)
   :recipients-fn                (recipients-fn)
   :model-fn                     model})

(notifications/defemail
  :accept-appointment
  {:subject-key                  "application.calendar.appointment.accept"
   :application-fn               (fn [{id :id}] (domain/get-application-no-access-checking id))
   :calendar-fn                  (calendar-request Method/REQUEST PartStat/ACCEPTED)
   :recipients-fn                (recipients-fn)
   :model-fn                     model})