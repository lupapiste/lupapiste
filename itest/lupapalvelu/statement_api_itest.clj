(ns lupapalvelu.statement-api-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer [facts* fact*]]
            [midje.sweet :refer :all]))

(apply-remote-minimal)

(facts* "statements"
  (let [ronja-email  (email-for "ronja")
        veikko-email (email-for "veikko")
        application-id     (create-app-id sonja :municipality sonja-muni :address "Lausuntobulevardi 1 A 1")
        resp (command sipoo :create-statement-giver :email (email-for "ronja") :text "<b>bold</b>") => ok?
        statement-giver-ronja (:id resp)
        email (last-email)]

    (fact "new statement person receives email which contains the (html escaped) input text"
      (:to email) => ronja-email
      (:subject email) => "Lupapiste.fi: Lausunnot"
      (get-in email [:body :plain]) => (contains "<b>bold</b>")
      (get-in email [:body :html]) => (contains "&lt;b&gt;bold&lt;/b&gt;"))

    (fact "Statement person can be added from another organization"
      (let [resp (command sipoo :create-statement-giver :email veikko-email :text "<b>bold</b>") => ok?
            statement-giver-veikko (:id resp)]

        ; Inbox zero
        (last-email) => truthy

        (fact "Initially Veikko does not have access to application"
          (query veikko :application :id application-id) => unauthorized?)

        (let [application-before (query-application sonja application-id)
              resp (command sonja :request-for-statement :id application-id :personIds [statement-giver-veikko]) => ok?
              application-after  (query-application sonja application-id)
              emails (sent-emails)
              email (first emails)]
          (fact "Veikko receives email"
            (:to email) => veikko-email
            (:subject email) => "Lupapiste.fi: Lausuntobulevardi 1 A 1 - Lausuntopyynt\u00f6")
          (fact "...but no-one else"
            (count emails) => 1)
          (fact "auth array has one entry more (veikko)"
            (count (:auth application-after)) => (inc (count (:auth application-before)))
            (count (filter #(= (:username %) "veikko") (:auth application-after))) => 1)
          (fact "ronja did not get access"
            (count (filter #(= (:username %) "ronja") (:auth application-after))) => 0)

          (fact "Veikko really has access to application"
            (query veikko :application :id application-id) => ok?))))

    (fact "Veikko gives a statement"
      (last-email) ; Inbox zero
      (let [application (query-application veikko application-id)
            statement   (first (:statements application))
            sonja-email (email-for "sonja")]
        (get-in statement [:person :email]) => veikko-email
        (command veikko :give-statement :id application-id :statementId (:id statement) :status "yes" :text "I will approve" :lang "fi") => ok?

        (println (:auth application))

        (fact "Sonja got email"
          (let [emails (sent-emails)
                email  (first emails)]
          (count emails) => 1
          (:to email) => sonja-email
          email => (partial contains-application-link-with-tab? application-id "conversation")))
        ))

    ; TODO facts about what Veikko can and can not do to application
    )

  (let [new-email "kirjaamo@museovirasto.example.com"]
    (fact "User does not exist before so she can not be added as a statement person"
      (command sipoo :create-statement-giver :email new-email :text "hello") => (partial expected-failure? "error.user-not-found")))

  )