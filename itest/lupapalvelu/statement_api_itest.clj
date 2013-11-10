(ns lupapalvelu.statement-api-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]))

(facts "statements"
  (command sipoo :create-statement-person :email "ronja.sibbo@sipoo.fi" :text "<b>bold</b>") => ok?
  (let [email (last-email)]
    (:to email) => (email-for "ronja")

    (fact "new statement person receives email which contains the (html escaped) input text"
      email => has-html-and-plain?
      (:subject email) => "Lupapiste.fi: Lausunnot"
      (get-in email [:body :plain]) => (contains "<b>bold</b>")
      (get-in email [:body :html]) => (contains "&lt;b&gt;bold&lt;/b&gt;")))

  (let [application-id (create-app-id sonja :municipality sonja-muni)
        resp (command sonja :request-for-statement :id application-id :personIds [ronja-id])
        email (last-email)]
    resp => ok?
    application-id => truthy
    (:to email) => (email-for "ronja")
    ))
