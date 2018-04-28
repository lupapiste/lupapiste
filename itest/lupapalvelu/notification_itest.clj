(ns lupapalvelu.notification-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.user :as usr]
            [midje.sweet :refer :all]
            [sade.core :refer [now]]))

(apply-remote-minimal)

(def esimerkki-y "7208863-8")

(last-email) ; inbox zero

(defn check-emails [& recipient-apikeys]
  (if (seq recipient-apikeys)
    (fact "Email recipients"
      (map :to (sent-emails))
      => (just (map (comp contains email-for-key)
                    recipient-apikeys)
               :in-any-order))
    (fact "No emails"
      (sent-emails) => empty?)))

(defn add-to-company [apikey admin?]
  (let [email (email-for-key apikey)
        role  (if admin? "admin" "regular user")]
    (fact {:midje/description (format "Add %s to Esimerkki Oy as %s."
                                      email role)}
      (command erkki :company-invite-user
               :email email
               :admin admin?
               :submit true) => ok?)
    (fact {:midje/description (str email " accepts invitation.")}
      (http-token-call (token-from-email email)))))

(facts "Setup company"
  (add-to-company mikko true)
  (add-to-company teppo false))

(facts "Subscription"
  (let [{:keys [id]} (create-and-submit-application pena :propertyId sipoo-property-id)]

    (fact "Application was created" id => truthy)

    (fact "Invite company to the application"
      (command pena :company-invite :id id :company-id "esimerkki") => ok?)

    (fact "Clear emails"
      (sent-emails))

    (fact "No emails before accepting the invite"
      (comment-application sonja id false) => ok?
      (check-emails pena))

    (fact "Company accepts invitation"
      (command mikko :approve-invite :id id :invite-type "company") => ok?)

    (fact "applicant and company admins get email"
      (comment-application sonja id false) => ok?
      (check-emails pena erkki mikko))

    (fact "pena unsubscribes, no more email for him"
      (command pena :unsubscribe-notifications :id id :username "pena") => ok?
      (comment-application sonja id false) => ok?
      (check-emails erkki mikko))

    (fact "Teppo cannot unsubscribe company"
      (command teppo :unsubscribe-notifications :id id :username esimerkki-y)
      => fail?)

    (fact "Erkki unsubscribes for company, no more emails."
      (command erkki :unsubscribe-notifications :id id :username esimerkki-y)
      => ok?
      (comment-application sonja id false) => ok?
      (check-emails))

    (fact "sonja resubscribes pena and company, emails start again"
      (command sonja :subscribe-notifications :id id :username "pena") => ok?
      (command sonja :subscribe-notifications :id id :username esimerkki-y)
      => ok?
      (comment-application sonja id false) => ok?
      (check-emails pena mikko erkki))

    (fact "sonja unsubscribes pena and company, no more email"
      (command sonja :unsubscribe-notifications :id id :username "pena") => ok?
      (command sonja :unsubscribe-notifications :id id :username esimerkki-y)
      => ok?
      (comment-application sonja id false) => ok?
      (check-emails))

    (fact "pena resubscribes, emails start again"
      (command pena :subscribe-notifications :id id :username "pena") => ok?
      (comment-application sonja id false) => ok?
      (check-emails pena))

    (fact "Mikko resubscribes, emails start again"
      (command mikko :subscribe-notifications :id id :username esimerkki-y)
      => ok?
      (comment-application sonja id false) => ok?
      (check-emails pena erkki mikko))

    (fact "admin disables pena, no more email"
      (command admin :set-user-enabled :email "pena@example.com" :enabled "false") => ok?
      (comment-application sonja id false) => ok?
      (check-emails mikko erkki))

    (fact "Admin locks company, emails not affected"
      (command admin :company-lock
               :company "esimerkki"
               :timestamp (- (now) (* 1000 3600))) => ok?
      (comment-application sonja id false) => ok?
      (check-emails mikko erkki))

    (fact "pena sees mails after being enabled again"
      (command admin :set-user-enabled :email "pena@example.com" :enabled "true") => ok?
      (comment-application sonja id false)
      (check-emails pena mikko erkki))

    (fact "Admin unlocks company, emails not affected"
      (command admin :company-lock
               :company "esimerkki"
               :timestamp 0) => ok?
      (comment-application sonja id false) => ok?
      (check-emails pena mikko erkki))

    (fact "Admin disables mikko, no more email"
      (command admin :set-user-enabled :email "mikko@example.com" :enabled "false") => ok?
      (comment-application sonja id false) => ok?
      (check-emails pena erkki))

    (fact "Application state changes: emails!"
      (command sonja :check-for-verdict :id id) => ok?
      (check-emails pena erkki))

    (fact "Pena is added to company but still receives mails only once"
      (add-to-company pena true)
      (comment-application sonja id false) => ok?
      (check-emails pena erkki))))
