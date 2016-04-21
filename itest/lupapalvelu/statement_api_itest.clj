(ns lupapalvelu.statement-api-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.factlet :refer [facts* fact*]]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.user-api :as user-api]))

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

(def non-existing-user-email "kirjaamo@museovirasto.example.com")
(def statement-giver-non-existing-user {:name "Master of museum"
                                        :email non-existing-user-email
                                        :text "<b>bold</b>"})

(def application-id (:id (create-and-submit-application mikko :propertyId sipoo-property-id :address "Lausuntobulevardi 1 A 1")))

(defn- auth-contains-statement-giver [{auth :auth} user-id]
  (some #(and
          (:statementId %)
          (= "statementGiver" (:role %))
          (= user-id (:id %))) auth))

(defn get-statement-giver-by-email [org-api-key email]
  (->> (query org-api-key :get-organizations-statement-givers)
       :data
       (some #(when (= email (:email %)) %))))

(defn- create-statement-giver [org-api-key giver-email]
  (let [resp-create-statement-giver (command org-api-key :create-statement-giver :email giver-email :text "<b>bold</b>") ;=> ok?
        giver-id (:id resp-create-statement-giver)]
    (fact {:midje/description (str "create statement-giver with email " giver-email)}
      resp-create-statement-giver => ok?
      giver-id => truthy)
    (get-statement-giver-by-email org-api-key giver-email)))

(fact "authorityAdmin can't query get-statement-givers"
    (query sipoo :get-statement-givers) => unauthorized?)

(facts "Create statement giver"
  (apply-remote-minimal)
  (fact "One statement giver in Sipoo, Sonja (set in minimal fixture)"
    (let [resp (query sipoo :get-organizations-statement-givers) => ok?
          givers (:data resp)]
      (count givers) => 1
      (-> givers first :email) => (contains sonja-email)))

  (facts "Add statement giver role for Ronja in Sipoo"
    (create-statement-giver sipoo ronja-email)

    (fact "Statement giver is added and sorted"
      (let [resp (query sipoo :get-organizations-statement-givers) => ok?
            givers (:data resp)]
        (count givers) => 2
        (-> givers first :email) => ronja-email
        (-> givers second :email) => sonja-email))

    (fact "new statement giver receives email which contains the (html escaped) input text"
      (let [email (last-email)]
      (:to email) => (contains ronja-email)
      (:subject email) => "Lupapiste: Lausunnot"
      (get-in email [:body :plain]) => (contains "<b>bold</b>")
        (get-in email [:body :html]) => (contains "&lt;b&gt;bold&lt;/b&gt;")))))

(fact "request-for-statement"
  (create-statement-giver sipoo ronja-email)
  (let [application-id (:id (create-and-submit-application mikko :propertyId sipoo-property-id :address "Lausuntobulevardi 1 A 1"))
        statement-giver-sonja (get-statement-giver-by-email sipoo sonja-email)
        statement-giver-ronja (get-statement-giver-by-email sipoo ronja-email)]

    (fact "request one statement giver"
      (command sonja :request-for-statement :functionCode nil :id application-id :selectedPersons [statement-giver-ronja]) => ok?
      (let [application (query-application ronja application-id)]
        (auth-contains-statement-giver application ronja-id) => truthy
        (-> (:statements application)
            (count)) => 1
        (-> (:statements application)
            (first)
            (get :person)) => (assoc statement-giver-ronja :userId ronja-id)))

    (fact "request two statement givers"
      (command sonja :request-for-statement :functionCode nil :id application-id :selectedPersons [statement-giver-sonja statement-giver-ronja]) => ok?
      (let [application (query-application ronja application-id)]
        (auth-contains-statement-giver application ronja-id) => truthy
        (auth-contains-statement-giver application sonja-id) => truthy
        (->> (:statements application)
             (count)) => 3
        (->> (:statements application)
             (map #(get-in % [:person :userId]))) => (contains #{sonja-id ronja-id})))))

(fact "delete-statement"
  (create-statement-giver sipoo ronja-email)
  (let [application-id (:id (create-and-submit-application mikko :propertyId sipoo-property-id :address "Lausuntobulevardi 1 A 1"))
        statement-giver-ronja (get-statement-giver-by-email sipoo ronja-email)
        resp (command sonja :request-for-statement :functionCode nil :id application-id :selectedPersons [statement-giver-ronja] :saateText "saate" :dueDate 1450994400000) => ok?
        application (query-application ronja application-id) => ok?
        statement-id (:id (get-statement-by-user-id application ronja-id)) => truthy]
    (fact "Statement giver has access to application"
      (auth-contains-statement-giver application ronja-id) => truthy)

    (fact "...but not after statement has been deleted"
      (command sonja :delete-statement :id application-id :statementId statement-id) => ok?
      (let [application (query-application sonja application-id)]
        (get-statement-by-user-id application ronja-id) => falsey
        (auth-contains-statement-giver application ronja-id) => falsey))))

(facts "Statement giver can be added from another organization"
  (let [application-id (:id (create-and-submit-application mikko :propertyId sipoo-property-id :address "Lausuntobulevardi 1 A 1"))
        statement-giver-veikko (create-statement-giver sipoo veikko-email)]

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
            (:subject email) => "Lupapiste: Sipoo, Lausuntobulevardi 1 A 1 - Lausuntopyynt\u00f6"
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

    (facts "Veikko gives a statement"

      (last-email) ; Inbox zero

      (let [application (query-application sonja application-id)
            statement   (some #(when (= veikko-email (-> % :person :email)) %) (:statements application)) => truthy]

        (fact* "Veikko is one of the possible statement givers"
          (let [resp (query sonja :get-statement-givers :id application-id) => ok?
                giver-emails (->> resp :data (map :email) set)]
            (count giver-emails) => 3
            (giver-emails sonja-email) => sonja-email
            (giver-emails ronja-email) => ronja-email
            (giver-emails veikko-email) => veikko-email))

        (fact "Statement cannot be given by applicant which is not statement owner"
          (command mikko :give-statement :id application-id :statementId (:id statement) :status "yes" :text "I will approve" :lang "fi") => unauthorized?)

        (fact "Statement cannot be given by authority which is not statement owner"
          (command sonja :give-statement :id application-id :statementId (:id statement) :status "yes" :text "I will approve" :lang "fi") => (partial expected-failure? "error.not-statement-owner"))

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

        (fact "One Attachment is generated"
          (let [gen-attachments (->> (query-application sonja application-id)
                                  :attachments
                                  (filter (comp #{"lausunto"} :type-id :type)))
                gen-statement-attachement (first gen-attachments)]

            (count gen-attachments) => 1

            (fact "cannot delete it since it is read-only"
              (:readOnly gen-statement-attachement) => true
              (command sonja :delete-attachment :id application-id :attachmentId (:id gen-statement-attachement)) => (partial expected-failure? "error.unauthorized"))))

        (fact "Comment is added"
          (->> (query-application sonja application-id)
               :comments
               (filter (comp #{"statement"} :type :target))
               (count)) => 1)))))

(facts "Applicant type person can be requested for statement"

  (let [application-id (:id (create-and-submit-application mikko :propertyId sipoo-property-id :address "Lausuntobulevardi 1 A 1"))
        _ (last-email) ; reset
        application-before (query-application sonja application-id)
        resp (command sonja :request-for-statement :functionCode nil :id application-id :selectedPersons [statement-giver-pena] :saateText "saate" :dueDate 1450994400000) => ok?
        application-after  (query-application sonja application-id)
        emails (sent-emails)
        email (first emails)]
    (fact "Pena receives email"
      (:to email) => (contains pena-email)
      (:subject email) => "Lupapiste: Sipoo, Lausuntobulevardi 1 A 1 - Lausuntopyynt\u00f6"
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

    (facts "Pena gives a statement"

      (last-email) ; Inbox zero

      (let [application (query-application pena application-id)
            statement   (some #(when (= pena-email (-> % :person :email)) %) (:statements application))]
        (fact* "Statement is given"
          (command pena :give-statement :id application-id :statementId (:id statement) :status "puoltaa" :text "I will approve" :lang "fi") => ok?)
        (fact "Applicant statement giver cannot delete his already-given statement"
          (command pena :delete-statement :id application-id :statementId (:id statement)) => (partial expected-failure? "error.statement-already-given"))

        (fact "One Attachment is generated"
          (->> (query-application sonja application-id)
               :attachments
               (filter (comp #{"lausunto"} :type-id :type))
               (count)) => 1)

        (fact "Comment is added"
          (->> (query-application sonja application-id)
               :comments
               (filter (comp #{"statement"} :type :target))
               (count)) => 1)))))

(fact "Applicant statement giver is able to delete his not-yet-given statement"
  (let [application-id (:id (create-and-submit-application mikko :propertyId sipoo-property-id :address "Lausuntobulevardi 1 A 1"))
        _ (command sonja :request-for-statement :functionCode nil :id application-id :selectedPersons [statement-giver-pena] :saateText "saate" :dueDate 1450994400000) => ok?
            application (query-application ronja application-id)
            non-given-statement-id (:id (some
                                          #(when (and (-> % :given not) (= pena-id (get-in % [:person :userId]))) %)
                                          (:statements application))) => truthy
            _ (command pena :delete-statement :id application-id :statementId non-given-statement-id) => ok?
            application (query-application sonja application-id)]
        (some #(= non-given-statement-id (:id %)) (:statements application)) => falsey
        (some #(= non-given-statement-id (:statementId %)) (:auth application)) => falsey
        ))

(facts "Non-existing user"
      (fact "Non-existing user cannot be added as a statement person (in authority admin view)"
        (command sipoo :create-statement-giver :email non-existing-user-email :text "hello") => (partial expected-failure? "error.user-not-found"))

      (fact "Non-existing user can be requested as a statement person (on the Statements tab)"
    (let [application-id (:id (create-and-submit-application mikko :propertyId sipoo-property-id :address "Lausuntobulevardi 1 A 1"))
          _ (command sonja :request-for-statement :functionCode nil :id application-id :selectedPersons [statement-giver-non-existing-user] :saateText "saate" :dueDate 1450994400000) => ok?
              application (query-application ronja application-id)
              resp (query admin :user-by-email :email non-existing-user-email) => ok?
              user (:user resp) => truthy]
          (get-statement-by-user-id application (:id user)) => truthy
          (auth-contains-statement-giver application (:id user)) => truthy)))

(facts "Statement reply"
  (create-statement-giver sipoo veikko-email)
  (let [statement-giver-veikko (get-statement-giver-by-email sipoo veikko-email)
        application-id (:id (create-and-submit-application mikko :propertyId sipoo-property-id :operation "ilmoitus-poikkeuksellisesta-tilanteesta"))
        resp (command sonja :request-for-statement :functionCode nil :id application-id :selectedPersons [statement-giver-veikko])
        application (query-application ronja application-id)
        statement-id (:id (get-statement-by-user-id application veikko-id)) => truthy]

    (fact "authorized-for-requesting-statement-reply"
      (query sonja :authorized-for-requesting-statement-reply :id application-id :statementId statement-id) => ok?
      (query ronja :authorized-for-requesting-statement-reply :id application-id :statementId statement-id) => ok?
      (query veikko :authorized-for-requesting-statement-reply :id application-id :statementId statement-id) => unauthorized?
      (query pena :authorized-for-requesting-statement-reply :id application-id :statementId statement-id) => unauthorized?)

    ;; (fact "statement-is-replyable - should fail when statement is not given"
    ;;   (query sonja :statement-is-replyable :id application-id :statementId statement-id) =not=> ok?)

    (fact "statement is given"
      (command veikko :give-statement :id application-id :statementId statement-id :status "puoltaa" :text "I will approve" :lang "fi") => ok?)

    (fact "statement-is-replyable - should fail when user is not requested for reply"
      (query sonja :statement-is-replyable :id application-id :statementId statement-id) =not=> ok?)

    (fact "applicant is not authorized for requesting reply"
      (command mikko :request-for-statement-reply :id application-id :statementId statement-id :status "puoltaa" :text "I will approve" :lang "fi") =not=> ok?)

    (fact "statement giver is not authorized for requesting reply"
      (command veikko :request-for-statement-reply :id application-id :statementId statement-id :status "puoltaa" :text "I will approve" :lang "fi") =not=> ok?)

    (fact "authority is able to request for reply"
      (command sonja :request-for-statement-reply :id application-id :statementId statement-id :status "puoltaa" :text "I will approve" :lang "fi") => ok?)

    (fact "statement-is-replyable - is replyable when user is requested for reply"
      (query sonja :statement-is-replyable :id application-id :statementId statement-id) => ok?)))


(facts "Statement data filtering"

  (create-statement-giver sipoo veikko-email)
  (let [statement-giver-veikko (get-statement-giver-by-email sipoo veikko-email)
        application-id (:id (create-and-submit-application pena :propertyId sipoo-property-id :operation "ilmoitus-poikkeuksellisesta-tilanteesta"))
        resp (command sonja :request-for-statement :functionCode nil :id application-id :selectedPersons [statement-giver-veikko] :saateText "saate" :dueDate 1450994400000)
        application (query-application sonja application-id)
        statement-id (:id (get-statement-by-user-id application veikko-id)) => truthy
        all-statement-keys  #{:id :person :state :requested :dueDate :saateText}
        filtered-statement-keys (disj all-statement-keys :saateText :dueDate)]


    (fact "All data of not-given statement data is available for authority Sonja"
      (:statements application) => (has every? #(= all-statement-keys (-> % keys set))))

    (fact "All data of not-given statement data is available for statementGiver himself"
      (let [app-veikko (query-application veikko application-id)]
        (:statements app-veikko) => (has every? #(= all-statement-keys (-> % keys set)))))

    (fact "Some data of not-given statement data is filtered from Pena"
      (let [app-pena (query-application pena application-id)]
        (:statements app-pena) => (has every? #(= filtered-statement-keys (-> % keys set)))))
    ))


