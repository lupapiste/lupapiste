(ns lupapalvelu.comment-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]))

(fact "adding comments"
  (let [id  (create-app-id pena)]

    (fact "authority can see the draft application, but can't comment draft"
      (query sonja :application :id id) => ok?
      sonja =not=> (allowed? :add-comment :id id :to irrelevant))

    (fact "applicant asks for help using add-comment"
      (comment-application pena id true) => ok?)

    (fact "application is now in open state"
      (let [app (query-application pena id)]
        (:state app) => "open"
        (-> app :history last :state) => "open"))

    (fact "authority can now see the application"
      (query sonja :application :id id) => ok?)

    (fact "applicant can't comment with to"
      (command pena :can-target-comment-to-authority :id id) => fail?
      pena =not=> (allowed? :add-comment :id id :to irrelevant)
      (comment-application pena id false) => ok?
      (comment-application pena id false sonja-id) => fail?)

    (fact "authority can comment and applicant gets email"
      (comment-application sonja id false) => ok?
      (let [email (last-email)]
        (:to email) => (contains (email-for "pena"))
        email => (partial contains-application-link-with-tab? id "conversation" "applicant")))

    (fact "authority can comment with to"
      (command sonja :can-target-comment-to-authority :id id) => ok?
      sonja => (allowed? :add-comment :id id :to sonja-id))

    (fact "when sonja adds comment, both pena and ronja will receive email"
      (comment-application sonja id false ronja-id) => ok?
      (let [emails (sent-emails)
            ronja-email (email-for "ronja")
            pena-email  (email-for "pena")
            to1 (:to (first emails))
            to2 (:to (last emails))]
        (count emails) => 2
        (or
          (and (.contains to1 ronja-email) (.contains to2 pena-email))
          (and (.contains to2 ronja-email) (.contains to1 pena-email))) => true))

    (fact "kosti can comment application with commenter role"
      (command kosti :can-target-comment-to-authority :id id) => ok?
      (comment-application kosti id false luukas-id) => ok?)

    (fact "luukas cannot comment application without commenter role"
      (command luukas :can-target-comment-to-authority :id id) => fail?
      (comment-application luukas id false) => unauthorized?)

    (fact "can't refer to non-existent user id"
      (comment-application sonja id false 0) => (partial expected-failure? "to-is-not-id-of-any-user-in-system"))

    (fact "the target parameter cannot be a string"
      (command sonja :add-comment :id id :text "comment1" :target "application" :roles []) => (partial expected-failure? "error.unknown-type"))

    (fact "the roles parameter cannot be a string"
      (command sonja :add-comment :id id :text "comment1" :target {:type "application"} :roles "applicant") => (partial expected-failure? "error.non-vector-parameters"))))
