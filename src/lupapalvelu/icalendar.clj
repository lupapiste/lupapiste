; Source: https://github.com/jeffmad/clj-icalendar/blob/master/src/clj_icalendar/core.clj
(ns lupapalvelu.icalendar
  (:require [sade.email :as email]
            [taoensso.timbre :as timbre :refer [info error]]
            [clj-time.local :refer [to-local-date-time local-now]])
  (:import (java.util Date)
           (net.fortuna.ical4j.model Calendar DateTime)
           (net.fortuna.ical4j.model.property Method ProdId Version CalScale Attendee Uid Organizer Url Location Description)
           (net.fortuna.ical4j.model.parameter Cn Role)
           (net.fortuna.ical4j.model.component VEvent)
           (net.fortuna.ical4j.data CalendarOutputter)
           (java.io StringWriter)))

(defn- create-attendee
  [{:keys [url role name]}]
  (let [att (Attendee. (java.net.URI/create url))]
    (.add (.getParameters att) role)
    (.add (.getParameters att) (Cn. name))
    att))

(defn- add-properties
  "take a vevent and add properties to it.
  the supported properties are url description location organizer-email and organizer-name."
  [vevent {:keys [^String description ^String url ^String location ^String organizer-email ^String organizer-name]}]
  (let [u (str (java.util.UUID/randomUUID) (.toString (to-local-date-time (local-now))) "@lupapiste.fi")
        props (.getProperties vevent)
        organizer-url (str "mailto:" organizer-email)]
    (.add props (Uid. u))
    (.add props (Organizer. organizer-url))
    (when (seq url) (.add props (Url. (java.net.URI. url))))
    (when (seq location) (.add props (Location. location)))
    (when (seq description) (.add props (Description. description)))
    (.add props (create-attendee {:url organizer-url
                                  :role Role/REQ_PARTICIPANT
                                  :name organizer-name}))
    vevent))

(defn- create-cal
  "create an empty calendar container. it is assumed to be Gregorian ical 2.0 and a published calendar "
  [^String org-name ^String product ^String version ^String lang]
  (let [c (Calendar.)
        props (.getProperties c)]
    (.add props (ProdId. (str "-//" org-name " //" product " " version "//" lang)))
    (.add props Version/VERSION_2_0)
    (.add props Method/REQUEST)
    (.add props CalScale/GREGORIAN) c))

(defn- create-event [^Date start ^Date end ^String title & {:keys [^String description ^String url ^String location
                                                                   ^String organizer-email ^String organizer-name] :as all}]
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

(defn create-calendar-event [{:keys [startTime endTime location] :as data}]
  (let [cal  (create-cal "Lupapiste" "Lupapiste Calendar" "V0.1" "EN")
        event (create-event  (Date. startTime)
                             (Date. endTime)
                             "title"
                             :description "description"
                             :url "url"
                             :location location
                             :organizer-email "lupapiste@solita.fi"
                             :organizer-name "lupapiste")
        _ (add-event! cal event)]
    (output-calendar cal)))