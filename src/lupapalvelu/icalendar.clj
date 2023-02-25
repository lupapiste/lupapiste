; Source: https://github.com/jeffmad/clj-icalendar/blob/master/src/clj_icalendar/core.clj
(ns lupapalvelu.icalendar
  (:require [taoensso.timbre :refer [info error]])
  (:import [clojure.lang PersistentArrayMap]
           [java.io StringWriter]
           [java.net URI]
           [java.util Date]
           [net.fortuna.ical4j.data CalendarOutputter]
           [net.fortuna.ical4j.model Calendar DateTime]
           [net.fortuna.ical4j.model.component VEvent]
           [net.fortuna.ical4j.model.parameter Cn Role Rsvp]
           [net.fortuna.ical4j.model.property Method ProdId Version CalScale Attendee Uid Organizer Url Location
                                              Description Sequence]))

(defn- create-attendee
  [{:keys [url role name rsvp partstat]}]
  (let [att (Attendee. (URI/create url))]
    (.add (.getParameters att) role)
    (.add (.getParameters att) rsvp)
    (.add (.getParameters att) (Cn. name))
    (when partstat
      (.add (.getParameters att) partstat))
    att))

(defn- add-properties
  "take a vevent and add properties to it.
  the supported properties are url description location organizer-email and organizer-name."
  [^VEvent vevent {:keys [^String description ^String url ^String location ^String organizer-email
                          ^PersistentArrayMap attendee ^String unique-id ^long sequence]}]
  (let [props (.getProperties vevent)
        organizer-url (str "mailto:" organizer-email)]
    (.add props (Uid. unique-id))
    (.add props (Organizer. organizer-url))
    (.add props (Sequence. sequence))
    (when (seq url) (.add props (Url. (URI. url))))
    (when (seq location) (.add props (Location. location)))
    (when (seq description) (.add props (Description. description)))
    (when (map? attendee)
      (.add props (create-attendee {:url      (str "mailto:" (:email attendee))
                                    :rsvp     Rsvp/FALSE
                                    :role     Role/REQ_PARTICIPANT
                                    :partstat (:partstat attendee)
                                    :name     (str (:firstName attendee) " " (:lastName attendee))})))
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

(defn- create-event [^Date start ^Date end ^String title & all]
  (let [st (doto (DateTime. start) (.setUtc true))
        et (doto (DateTime. end) (.setUtc true))
        vevent (VEvent. st et title)]
    (add-properties vevent all)))

(defn- add-event!
  "take a calendar and a vevent, add the event to the calendar, and return the calendar"
  [^Calendar cal ^VEvent vevent]
  (.add (.getComponents cal) vevent) cal)

(defn- output-calendar
  "output the calendar to a string, using a folding writer,
   which will limit the line lengths as per ical spec."
  [^Calendar cal]
  (let [sw (StringWriter.)]
    (.output (CalendarOutputter.) cal sw)
    (.close sw)
    (.replaceAll (.toString sw) "\r" "")))

(defn create-calendar-event [{:keys [^Method method startTime endTime location comment attendee unique-id sequence
                                     title]}]
  (let [cal (create-cal method "Lupapiste" "Lupapiste Calendar" "V0.1" "EN")
        event (create-event (Date. startTime)
                            (Date. endTime)
                            title
                            :description comment
                            :url "url"
                            :location location
                            :attendee attendee
                            :organizer-email "no-reply@lupapiste.fi"
                            :organizer-name "Lupapiste-asiointipalvelu"
                            :unique-id unique-id
                            :sequence sequence)
        _ (add-event! cal event)]
    {:method  (.getValue method)
     :content (output-calendar cal)}))