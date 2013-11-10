(ns lupapalvelu.statement-api-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]))

(facts "new statement person receives email"
  (command sipoo :create-statement-person :email "ronja.sibbo@sipoo.fi" :text "<b>bold</b>") => ok?
  (let [email (last-email)]
    (:to email) => (email-for "ronja")

    (fact "which contains the (html escaped) input text"
      email => has-html-and-plain?
      (get-in email [:body :plain]) => (contains "<b>bold</b>")
      (get-in email [:body :html]) => (contains "&lt;b&gt;bold&lt;/b&gt;"))))
