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

    (let [application-id (create-app-id sonja :municipality sonja-muni)
          resp (command sonja :request-for-statement :id application-id :personIds [statement-person-id])
          email (last-email)]
      resp => ok?
      application-id => truthy
      (:to email) => (email-for "ronja"))))
