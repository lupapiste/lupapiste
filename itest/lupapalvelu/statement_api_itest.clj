(ns lupapalvelu.statement-api-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer [facts* fact*]]
            [midje.sweet :refer :all]))

(apply-remote-minimal)

(def sonja-email  (email-for-key sonja))
(def ronja-email  (email-for-key ronja))
(def veikko-email (email-for-key veikko))
(def mikko-email  (email-for-key mikko))
(def pena-email  (email-for-key pena))

;; Simulating manually added (applicant) statement giver. Those do not have the key :id.
(def statement-giver-pena {:name "Pena Panaani"
                           :email pena-email
                           :text "<b>bold</b>"})

(defn- auth-contains-statement-giver [{auth :auth} user-id]
  (some #(and
          (:statementId %)
          (= "statementGiver" (:role %))
          (= user-id (:id %))) auth))

(defn- get-statement-by-user-id [{statements :statements} user-id]
  (some #(when (= user-id (get-in % [:person :userId])) %) statements))

(defn- create-statement-giver [giver-email]
  (let [resp-create-statement-giver (command sipoo :create-statement-giver :email giver-email :text "<b>bold</b>") ;=> ok?
        giver-id (:id resp-create-statement-giver) ;=> truthy
        resp-get-givers (query sipoo :get-organizations-statement-givers) ;=> ok?
        givers (:data resp-get-givers) ;=> truthy
        statement-giver (some #(when (= giver-id (:id %)) %) givers)
        ]
    (fact {:midje/description (str "create statement-giver with email " giver-email)}
      resp-create-statement-giver => ok?
      giver-id => truthy
      resp-get-givers => ok?
      givers => truthy
      statement-giver => truthy
      )
    statement-giver))


(facts* "statements"

  (fact "authorityAdmin can't query get-statement-givers"
    (query sipoo :get-statement-givers) => unauthorized?)

    (let [resp (query sipoo :get-organizations-statement-givers) => ok?
          givers (:data resp)]
      (fact "One statement giver in Sipoo, Sonja (set in minimal fixture)"
        (count givers) => 1
        (-> givers first :email) => (contains sonja-email)))

  (let [application-id (:id (create-and-submit-application mikko :propertyId sipoo-property-id :address "Lausuntobulevardi 1 A 1"))
        statement-giver-ronja (create-statement-giver ronja-email)
        email (last-email) => truthy]

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
      (let [statement-giver-veikko (create-statement-giver veikko-email)]

        ; Inbox zero
        (last-email) => truthy

        (fact "Initially Veikko does not have access to application"
          (query veikko :application :id application-id) => not-accessible?)

        (let [application-before (query-application sonja application-id)
              resp (command sonja :request-for-statement :functionCode nil :id application-id :selectedPersons [statement-giver-veikko] :saateText "saate" :dueDate 1450994400000) => ok?
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
            (query veikko :application :id application-id) => ok?))

        (fact "applicant type person can be requested for statement"
          (let [application-before (query-application sonja application-id)
                resp (command sonja :request-for-statement :functionCode nil :id application-id :selectedPersons [statement-giver-pena] :saateText "saate" :dueDate 1450994400000) => ok?
                application-after  (query-application sonja application-id)
                emails (sent-emails)
                email (first emails)]
            (fact "Pena receives email"
              (:to email) => (contains pena-email)
              (:subject email) => "Lupapiste.fi: Sipoo, Lausuntobulevardi 1 A 1 - Lausuntopyynt\u00f6"
              email => (partial contains-application-link? application-id "applicant"))
            (fact "...but no-one else"
              (count emails) => 1)
            (fact "auth array has one entry more (pena)"
              (count (:auth application-after)) => (inc (count (:auth application-before)))
              (count (filter
                       #(and (= "statementGiver" (:role %)) (= "pena" (:username %)))
                       (:auth application-after))) => 1)
            (fact "Pena really has access to application"
              (query pena :application :id application-id) => ok?)
            (fact "Applicant can not see unsubmitted statements"
              (query pena :should-see-unsubmitted-statements :id application-id) => ok?)))
        ))

    (fact "Veikko gives a statement"

      (last-email) ; Inbox zero

      (let [application (query-application veikko application-id)
            statement   (some #(when (= veikko-email (-> % :person :email)) %) (:statements application)) => truthy]

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

        (fact* "Statement is given"
          (command veikko :give-statement :id application-id :statementId (:id statement) :status "puoltaa" :text "I will approve" :lang "fi") => ok?)

        (fact "Applicant got email"
          (let [emails (sent-emails)
                email  (first emails)]
            (count emails) => 1
            (:to email) => (contains mikko-email)
            email => (partial contains-application-link-with-tab? application-id "conversation" "applicant")))
        ))

    (fact "Pena gives a statement"

      (last-email) ; Inbox zero

      (let [application (query-application pena application-id)
            statement   (some #(when (= pena-email (-> % :person :email)) %) (:statements application))]
        (fact "Statement cannot be given with invalid status"
          (command pena :give-statement :id application-id :statementId (:id statement) :status "yes" :text "I will approve" :lang "fi") => (partial expected-failure? "error.unknown-statement-status"))
        (fact* "Statement is given"
          (command pena :give-statement :id application-id :statementId (:id statement) :status "puoltaa" :text "I will approve" :lang "fi") => ok?)
        ))


    (fact "Statement person has access to application"
      (let [resp (command sonja :request-for-statement :functionCode nil :id application-id :selectedPersons [statement-giver-ronja] :saateText "saate" :dueDate 1450994400000) => ok?
            application (query-application ronja application-id)]
        (auth-contains-statement-giver application ronja-id) => truthy

        (fact "...but not after statement has been deleted"
          (let [statement-id (:id (get-statement-by-user-id application ronja-id)) => truthy
                resp (command sonja :delete-statement :id application-id :statementId statement-id) => ok?
                application (query-application sonja application-id)]
            (get-statement-by-user-id application ronja-id) => falsey
            (auth-contains-statement-giver application ronja-id) => falsey))))

    (fact "Applicant statement giver cannot delete his already-given statement"
      (let [application-before (query-application sonja application-id)
            statement-id (:id (get-statement-by-user-id application-before pena-id)) => truthy]
        (command pena :delete-statement :id application-id :statementId statement-id) => (partial expected-failure? "error.statement-already-given")))
    )


  (let [new-email "kirjaamo@museovirasto.example.com"]
    (fact "User does not exist before so she can not be added as a statement person"
      (command sipoo :create-statement-giver :email new-email :text "hello") => (partial expected-failure? "error.user-not-found")))

  )
