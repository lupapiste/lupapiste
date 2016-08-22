; Source: https://github.com/jeffmad/clj-icalendar/blob/master/src/clj_icalendar/core.clj
(ns lupapalvelu.icalendar
  (:require [taoensso.timbre :as timbre :refer [info error]])
  (:import (java.util Date)
           (net.fortuna.ical4j.model Calendar DateTime)
           (net.fortuna.ical4j.model.property Method ProdId Version CalScale Attendee Uid Organizer Url Location Description Sequence)
           (net.fortuna.ical4j.model.parameter Cn Role Rsvp)
           (net.fortuna.ical4j.model.component VEvent)
           (net.fortuna.ical4j.data CalendarOutputter)
           (java.io StringWriter)
           (clojure.lang PersistentArrayMap)))

(defn- create-attendee
  [{:keys [url role name rsvp]}]
  (let [att (Attendee. (java.net.URI/create url))]
    (.add (.getParameters att) role)
    (.add (.getParameters att) rsvp)
    (.add (.getParameters att) (Cn. name))
    att))

(defn- add-properties
  "take a vevent and add properties to it.
  the supported properties are url description location organizer-email and organizer-name."
  [vevent {:keys [^String description ^String url ^String location ^String organizer-email
                  ^PersistentArrayMap attendee ^String unique-id ^Number sequence]}]
  (let [props (.getProperties vevent)
        organizer-url (str "mailto:" organizer-email)]
    (.add props (Uid. unique-id))
    (.add props (Organizer. organizer-url))
    (.add props (Sequence. sequence))
    (when (seq url) (.add props (Url. (java.net.URI. url))))
    (when (seq location) (.add props (Location. location)))
    (when (seq description) (.add props (Description. description)))
    (when (map? attendee)
      (.add props (create-attendee {:url  (str "mailto:" (:email attendee))
                                    :rsvp Rsvp/FALSE
                                    :role Role/REQ_PARTICIPANT
                                    :name (str (:firstName attendee) " " (:lastName attendee))})))
    vevent))

(defn- create-cal
  "create an empty calendar container. it is assumed to be Gregorian ical 2.0 and a published calendar "
  [^Method method ^String org-name ^String product ^String version ^String lang]
  (let [c (Calendar.)
        props (.getProperties c)]
    (.add props (ProdId. (str "-//" org-name " //" product " " version "//" lang)))
    (.add props Version/VERSION_2_0)
    (.add props method)
    (.add props CalScale/GREGORIAN) c))

(defn- create-event [^Date start ^Date end ^String title & {:keys [^String description ^String url ^String location
                                                                   ^String organizer-email ^String organizer-name
                                                                   ^PersistentArrayMap attendee ^String unique-id
                                                                   ^Number sequence] :as all}]
  (let [st (doto  (DateTime. start) (.setUtc true))
        et (doto  (DateTime. end) (.setUtc true))
        vevent (VEvent. st et title)]
    (add-properties vevent all)))

(defn- add-event!
  "take a calendar and a vevent, add the event to the calendar, and return the calendar"
  [^net.fortuna.ical4j.model.Calendar cal  ^VEvent vevent]
  (.add (.getComponents cal) vevent) cal)

(defn- output-calendar
  "output the calendar to a string, using a folding writer,
   which will limit the line lengths as per ical spec."
  [^net.fortuna.ical4j.model.Calendar cal]
  (let [co (CalendarOutputter.)
        sw (StringWriter.)
        output (.output co cal sw)
        _ (.close sw)]
    (.replaceAll (.toString sw) "\r" "")))

(defn create-calendar-event [{:keys [method startTime endTime location attendee unique-id sequence] :as data}]
  (let [cal  (create-cal method "Lupapiste" "Lupapiste Calendar" "V0.1" "EN")
        event (create-event  (Date. startTime)
                             (Date. endTime)
                             "title"
                             :description "description"
                             :url "url"
                             :location location
                             :attendee attendee
                             :organizer-email "no-reply@lupapiste.fi"
                             :organizer-name "Lupapiste-asiointipalvelu"
                             :unique-id unique-id
                             :sequence sequence)
        _ (add-event! cal event)]
    (output-calendar cal)))