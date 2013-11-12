(ns lupapalvelu.statement-api-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer [facts* fact*]]
            [midje.sweet :refer :all]))

(facts* "statements"
  (let [resp (command sipoo :create-statement-person :email (email-for "ronja") :text "<b>bold</b>") => ok?
        statement-person-id (:id resp)
        email (last-email)]

    (fact "new statement person receives email which contains the (html escaped) input text"
      (:to email) => (email-for "ronja")
      email => has-html-and-plain?
      (:subject email) => "Lupapiste.fi: Lausunnot"
      (get-in email [:body :plain]) => (contains "<b>bold</b>")
      (get-in email [:body :html]) => (contains "&lt;b&gt;bold&lt;/b&gt;"))

    ; Add another statement person for the next test
    (command sipoo :create-statement-person :email (email-for "veikko") :text "<b>bold</b>") => ok?
    ; Zero inbox
    (last-email) => truthy

    (let [application-id     (create-app-id sonja :municipality sonja-muni :address "Lausuntobulevardi 1 A 1")
          application-before (query-application sonja application-id)
          resp (command sonja :request-for-statement :id application-id :personIds [statement-person-id]) => ok?
          application-after  (query-application sonja application-id)
          emails (sent-emails)
          email (first emails)]
      (fact "Ronja receives email"
        (:to email) => (email-for "ronja")
        (:subject email) => "Lupapiste.fi: Lausuntobulevardi 1 A 1 - Lausuntopyynt\u00f6")
      (fact "...but no-one else"
        (count emails) => 1)
      (fact "auth array has one entry more (ronja)"
        (count (:auth application-after)) => (inc (count (:auth application-before)))
        (count (filter #(= (:username %) "ronja") (:auth application-after))) => 1)
      (fact "veikko did not get access"
        (count (filter #(= (:username %) "veikko") (:auth application-after))) => 0))))
