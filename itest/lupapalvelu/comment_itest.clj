(ns lupapalvelu.comment-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]))

(fact "adding comments"
  (let [{:keys [id state]}  (create-and-submit-application pena)
        has-html-and-plain? (fn [{body :body}] (and (:html body) (:plain body)))
        has-correct-link? (fn [email] (let [body (get-in email [:body :plain])
                                            [href a-id a-id-again tab]   (re-find #"(?sm)http.+/app/fi/applicant\?hashbang=!/application/([A-Za-z0-9-]+)/conversation#!/application/([A-Za-z0-9-]+)/([a-z]+)" body)]
                                        (and (= id a-id a-id-again) (= tab "conversation"))))]

    (fact "...to a submitted application"
      state => "submitted")

    (fact "applicant can't comment with to"
      pena =not=> (allowed? :can-target-comment-to-authority)
      pena =not=> (allowed? :add-comment :id id :to irrelevant)
      (command pena :add-comment :id id :text "comment1" :target "application") => ok?
      (command pena :add-comment :id id :text "comment1" :target "application" :to sonja-id) =not=> ok?)

    (fact "authority can comment and applicant gets email"
      (command sonja :add-comment :id id :text "comment1" :target "application") => ok?
      (let [email (last-email)]
        (:to email) => (email-for "pena")
        email => has-html-and-plain?
        email => has-correct-link?))

    (fact "authority can comment with to"
      sonja => (allowed? :can-target-comment-to-authority)
      sonja => (allowed? :add-comment :id id :to sonja-id))

    (fact "when sonja adds comment, both pena and ronja will receive email"
      (command sonja :add-comment :id id :text "comment1" :target "application" :to ronja-id) => ok?
      (let [emails (sent-emails)
            ronja-email (email-for "ronja")
            pena-email  (email-for "pena")
            to1 (:to (first emails))
            to2 (:to (last emails))]
        (count emails) => 2
        (doseq [email emails]
          email => has-html-and-plain?
          email => has-correct-link?)
        (or
          (and (= to1 ronja-email) (= to2 pena-email))
          (and (= to2 ronja-email) (= to1 pena-email))) => true))

    (fact "can't refer to non-existent user id"
      (command sonja :add-comment :id id :text "comment1" :target "application" :to 0) => (partial expected-failure? "to-is-not-id-of-any-user-in-system"))))
