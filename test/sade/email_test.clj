(ns sade.email-test
  (:require [sade.email :refer :all]
            [midje.sweet :refer :all]
            [postal.core :as postal]))

(facts "send-mail"
  ; Need some body:
  (send-mail ...to... ...subject...) => (throws AssertionError)

  ; Send plain text only:
  (send-mail ...to... ...subject... :plain "plain text") => nil
    (provided (deliver-email ...to... ...subject... [{:type "text/plain; charset=utf-8" :content "plain text"}]) => nil)

  ; Send html only:
  (send-mail ...to... ...subject... :html "html text") => nil
    (provided (deliver-email ...to... ...subject... [{:type "text/html; charset=utf-8" :content "html text"}]) => nil)

  ; Send both plain and html body, content is Multi-Part/alternative, and plain text is first:
  (send-mail ...to... ...subject... :plain "plain text" :html "html text") => nil
    (provided (deliver-email ...to... ...subject... [:alternative {:type "text/plain; charset=utf-8" :content "plain text"} {:type "text/html; charset=utf-8" :content "html text"}]) => nil)

  ; Send html and calendar only:
  (send-mail ...to... ...subject... :html "html text" :calendar {:method "REQUEST" :content "BEGIN:VCALENDAR\nPRODID:-//Lupapiste //Lupapiste Calendar V0.1//EN\nVERSION:2.0\nMETHOD:REQUEST\nCALSCALE:GREGORIAN\nBEGIN:VEVENT\nDTSTAMP:20160816T092730Z\nDTSTART:20160819T090000Z\nDTEND:20160819T100000Z\nSUMMARY:title\nUID:a2ce23b5-f464-41a5-a16e-75ec8a5a2de72016-08-16T12:27:30.006+03:00@lup\n apiste.fi\nORGANIZER:mailto:no-reply@lupapiste.fi\nURL:url\nDESCRIPTION:description\nATTENDEE;ROLE=REQ-PARTICIPANT;RSVP=FALSE;CN=Pena Panaani:mailto:pena.pan\n aani@example.com\nEND:VEVENT\nEND:VCALENDAR"}) => nil
    (provided (deliver-email ...to... ...subject... [:alternative
                                                       {:type "text/html; charset=utf-8" :content "html text"}
                                                       {:type "text/calendar; charset=utf-8; method=REQUEST" :content "BEGIN:VCALENDAR\nPRODID:-//Lupapiste //Lupapiste Calendar V0.1//EN\nVERSION:2.0\nMETHOD:REQUEST\nCALSCALE:GREGORIAN\nBEGIN:VEVENT\nDTSTAMP:20160816T092730Z\nDTSTART:20160819T090000Z\nDTEND:20160819T100000Z\nSUMMARY:title\nUID:a2ce23b5-f464-41a5-a16e-75ec8a5a2de72016-08-16T12:27:30.006+03:00@lup\n apiste.fi\nORGANIZER:mailto:no-reply@lupapiste.fi\nURL:url\nDESCRIPTION:description\nATTENDEE;ROLE=REQ-PARTICIPANT;RSVP=FALSE;CN=Pena Panaani:mailto:pena.pan\n aani@example.com\nEND:VEVENT\nEND:VCALENDAR"}]) => nil)

  ; Return error when postal returns an error:
  (send-mail ...to... ...subject... :plain "plain text") => {:error "oh noes"}
    (provided (deliver-email ...to... ...subject... irrelevant) => {:error "oh noes"}))

(facts "blacklisted?"
  (fact "sample or test addresses"
    (binding [sade.email/blacklist (re-pattern ".*@([0-9]{3}.fi|example\\.com|testi\\.fi)$")]
     (blacklisted? "mikko@example.com") => true
     (blacklisted? "mikko@testi.fi") => true
     (blacklisted? "mikko@000.fi") => true
     (blacklisted? "example.com@example.net") => false
     (blacklisted? "mikko@testaus.fi") => false
     (blacklisted? "mikko@0000.fi") => false))

  (fact "nil"
    (binding [sade.email/blacklist nil]
     (blacklisted? "mikko@example.com") => false
     (blacklisted? "mikko@testi.fi") => false
     (blacklisted? "mikko@000.fi") => false
     (blacklisted? "example.com@example.net") => false
     (blacklisted? "mikko@testaus.fi") => false
     (blacklisted? "mikko@0000.fi") => false))

  (fact "empty pattern"
    (binding [sade.email/blacklist (re-pattern "")]
     (blacklisted? "mikko@example.com") => false
     (blacklisted? "mikko@testi.fi") => false
     (blacklisted? "mikko@000.fi") => false
     (blacklisted? "example.com@example.net") => false
     (blacklisted? "mikko@testaus.fi") => false
     (blacklisted? "mikko@0000.fi") => false)))
