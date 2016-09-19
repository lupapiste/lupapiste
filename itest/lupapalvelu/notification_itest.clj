(ns lupapalvelu.notification-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.user :as usr]
            [lupapalvelu.itest-util :refer :all]))

(apply-remote-minimal)

(last-email) ; inbox zero

(facts "Subscription"
  (let [{:keys [id]} (create-and-submit-application pena :propertyId sipoo-property-id)]

    (fact "Application was created" id => truthy)

    (fact "applicant gets email"
      (comment-application sonja id false) => ok?
      (:to (last-email)) => (contains (email-for "pena")))

    (fact "pena unsubscribes, no more email"
      (command pena :unsubscribe-notifications :id id :username "pena") => ok?
      (comment-application sonja id false) => ok?
      (last-email) => nil?)

    (fact "sonja resubscribes pena, emails start again"
      (command sonja :subscribe-notifications :id id :username "pena") => ok?
      (comment-application sonja id false) => ok?
      (:to (last-email)) => (contains (email-for "pena")))

    (fact "sonja unsubscribes pena, no more email"
      (command sonja :unsubscribe-notifications :id id :username "pena") => ok?
      (comment-application sonja id false) => ok?
      (last-email) => nil?)

    (fact "pena resubscribes, emails start again"
      (command pena :subscribe-notifications :id id :username "pena") => ok?
      (comment-application sonja id false) => ok?
      (:to (last-email)) => (contains (email-for "pena")))
    
    (fact "admin disables pena, no more email"
      (command admin :set-user-enabled :email "pena@example.com" :enabled "false") => ok?
      (comment-application sonja id false) => ok?
      (command admin :set-user-enabled :email "pena@example.com" :enabled "true") => ok?
      (last-email) => nil?)
   
    (fact "pena sees mails after being enabled again"
      (comment-application sonja id false) 
      (:to (last-email)) => (contains (email-for "pena")))
 ))

