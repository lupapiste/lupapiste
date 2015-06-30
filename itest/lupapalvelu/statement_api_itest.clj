(ns lupapalvelu.statement-api-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer [facts* fact*]]
            [midje.sweet :refer :all]))

(apply-remote-minimal)

(def sonja-email (email-for-key sonja))

(defn- auth-contains-ronjas-statement [{auth :auth}]
  (some #(and
          (:statementId %)
          (= (:role %) "statementGiver")
          (= (:username %) "ronja")) auth))

(facts* "statements"

  (fact "authorityAdmin can't query get-statement-givers"
    (query sipoo :get-statement-givers) => unauthorized?)

    (let [resp (query sipoo :get-organizations-statement-givers) => ok?
          givers (:data resp)]
      (fact "One statement giver in Sipoo, Sonja (set in minimal fixture)"
        (count givers) => 1
        (-> givers first :email) => (contains sonja-email)))

  (let [ronja-email  (email-for "ronja")
        veikko-email (email-for "veikko")
        application-id     (:id (create-and-submit-application mikko :propertyId sipoo-property-id :address "Lausuntobulevardi 1 A 1"))
        resp (command sipoo :create-statement-giver :email (email-for "ronja") :text "<b>bold</b>") => ok?
        statement-giver-ronja (:id resp)
        email (last-email)]

    (let [resp (query sipoo :get-organizations-statement-givers) => ok?
          givers (:data resp)]
      (fact "Two statement givers in Sipoo, Sonja & Ronja"
        (count givers) => 2
        (-> givers first :email) => sonja-email
        (-> givers second :email) => ronja-email))

    (fact "new statement person receives email which contains the (html escaped) input text"
      (:to email) => (contains ronja-email)
      (:subject email) => "Lupapiste.fi: Lausunnot"
      (get-in email [:body :plain]) => (contains "<b>bold</b>")
      (get-in email [:body :html]) => (contains "&lt;b&gt;bold&lt;/b&gt;"))

    (fact "Statement person can be added from another organization"
      (let [resp (command sipoo :create-statement-giver :email veikko-email :text "<b>bold</b>") => ok?
            statement-giver-veikko (:id resp)]

        ; Inbox zero
        (last-email) => truthy

        (fact "Initially Veikko does not have access to application"
          (query veikko :application :id application-id) => not-accessible?)

        (let [application-before (query-application sonja application-id)
              resp (command sonja :request-for-statement :id application-id :personIds [statement-giver-veikko]) => ok?
              application-after  (query-application sonja application-id)
              emails (sent-emails)
              email (first emails)]
          (fact "Veikko receives email"
            (:to email) => (contains veikko-email)
            (:subject email) => "Lupapiste.fi: Sipoo, Lausuntobulevardi 1 A 1 - Lausuntopyynt\u00f6"
            email => (partial contains-application-link? application-id "authority"))
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
            statement   (first (:statements application))]

        (fact* "Veikko is one of the possible statement givers"
          (let [resp (query sonja :get-statement-givers :id application-id) => ok?
                giver-emails (->> resp :data (map :email) set)]
            (count giver-emails) => 3
            (giver-emails sonja-email) => sonja-email
            (giver-emails ronja-email) => ronja-email
            (giver-emails veikko-email) => veikko-email))

        (fact "Veikko can see unsubmitted statements"
          (query veikko :should-see-unsubmitted-statements :id application-id) => ok?)

        (fact "Sonja can see unsubmitted statements"
          (query sonja :should-see-unsubmitted-statements :id application-id) => ok?)

        (fact "Applicant can not see unsubmitted statements"
          (query mikko :should-see-unsubmitted-statements :id application-id) => unauthorized?)

        (fact "Statement cannot be given with invalid status"
          (command veikko :give-statement :id application-id :statementId (:id statement) :status "yes" :text "I will approve" :lang "fi") => (partial expected-failure? "error.unknown-statement-status"))

        (get-in statement [:person :email]) => veikko-email
        (command veikko :give-statement :id application-id :statementId (:id statement) :status "puoltaa" :text "I will approve" :lang "fi") => ok?

        (fact "Applicant got email"
          (let [emails (sent-emails)
                email  (first emails)]
          (count emails) => 1
          (:to email) => (contains "mikko@example.com")
          email => (partial contains-application-link-with-tab? application-id "conversation" "applicant")))
        ))

    (fact "Statement person has access to application"
      (let [resp (command sonja :request-for-statement :id application-id :personIds [statement-giver-ronja]) => ok?
            application (query-application (apikey-for "ronja") application-id)]
        (auth-contains-ronjas-statement application) => truthy

        (fact "but not after statement has been deleted"
          (let [statement-id (some #(when (= ronja-id (get-in % [:person :userId])) (:id %)) (:statements application)) => truthy
                resp (command sonja :delete-statement :id application-id :statementId statement-id) => ok?
                application (query-application sonja application-id)]

            (some #(= ronja-id (get-in % [:person :userId])) (:statements application)) => falsey

            (auth-contains-ronjas-statement application) => falsey)))))

  (let [new-email "kirjaamo@museovirasto.example.com"]
    (fact "User does not exist before so she can not be added as a statement person"
      (command sipoo :create-statement-giver :email new-email :text "hello") => (partial expected-failure? "error.user-not-found")))

  )
