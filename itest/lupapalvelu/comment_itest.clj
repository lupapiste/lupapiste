(ns lupapalvelu.comment-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]))

(fact "adding comments"
  (let [{:keys [id state]}  (create-and-submit-application pena)]

    (fact "...to a submitted application"
      state => "submitted")

    (fact "applicant can't comment with to"
      pena =not=> (allowed? :can-target-comment-to-authority :id id)
      pena =not=> (allowed? :add-comment :id id :to irrelevant)
      (comment-application pena id false) => ok?
      (comment-application pena id false sonja-id) =not=> ok?)

    (fact "authority can comment and applicant gets email"
      (comment-application sonja id false) => ok?
      (let [email (last-email)]
        (:to email) => (contains (email-for "pena"))
        email => (partial contains-application-link-with-tab? id "conversation" "applicant")))

    (fact "authority can comment with to"
      sonja => (allowed? :can-target-comment-to-authority :id id)
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

    (fact "can't refer to non-existent user id"
      (comment-application sonja id false 0) => (partial expected-failure? "to-is-not-id-of-any-user-in-system"))

    (fact "the target parameter cannot be a string"
      (command sonja :add-comment :id id :text "comment1" :target "application" :roles []) => (partial expected-failure? "error.unknown-type"))

    (fact "the roles parameter cannot be a string"
      (command sonja :add-comment :id id :text "comment1" :target {:type "application"} :roles "applicant") => (partial expected-failure? "error.non-vector-parameters"))))
