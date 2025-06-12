(ns lupapalvelu.comment-itest
  (:require [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.minimal-util :as min-util]
            [midje.sweet :refer :all]
            [sade.strings :as ss]))

(defn email-count [all-emails email]
  (->> all-emails
       (filter #(ss/includes? (:to %) email))
       count))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (fact "adding comments"
    (let [id (create-app-id pena)]

      (fact "authority can see the draft application, but can't comment draft"
        (query sonja :application :id id) => ok?
        (allowed? sonja :add-comment :id id :to irrelevant) => :error.unauthorized)

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
        (allowed? pena :add-comment :id id :to irrelevant) => :error.to-settable-only-by-authority
        (comment-application pena id false) => ok?
        (comment-application pena id false sonja-id) => (partial expected-failure? :error.to-settable-only-by-authority))

      (fact "authority can comment and applicant gets email"
        (comment-application sonja id false) => ok?
        (let [email (last-email)]
          (:to email) => (contains (min-util/email-for "pena"))
          email => (partial contains-application-link-with-tab? id "conversation" "applicant")))

      (fact "When applicant comments, no email is sent"
        (last-email) ;; Empty inbox just in case
        (comment-application pena id false) => ok?
        (last-email) => nil)

      (fact "authority can comment with to"
        (command sonja :can-target-comment-to-authority :id id) => ok?
        (allowed? sonja :add-comment :id id :to sonja-id) => true)

      (fact "when sonja adds comment, only ronja will receive email"
        (comment-application sonja id false ronja-id) => ok?
        (let [emails (sent-emails)]
          (count emails) => 1
          (email-count emails (min-util/email-for-key pena)) => 0
          (email-count emails (min-util/email-for-key ronja)) => 1))

      (fact "To must be string if given"
        (comment-application sonja id false 12345)
        => (partial expected-failure? "error.invalid-type")
        (comment-application sonja id false false)
        => (partial expected-failure? "error.invalid-type"))

      (fact "Blank to is ignored"
        (last-email) ;; Empty inbox
        (comment-application sonja id false "     ") => ok?
        (email-count (sent-emails) (min-util/email-for-key pena)) => 1
        (comment-application sonja id false nil) => ok?
        (email-count (sent-emails) (min-util/email-for-key pena)) => 1)

      (fact "kosti can comment application with commenter role"
        (command kosti :can-target-comment-to-authority :id id) => ok?
        (comment-application kosti id false luukas-id) => ok?)

      (fact "luukas cannot comment application without commenter role"
        (command luukas :can-target-comment-to-authority :id id) => fail?
        (comment-application luukas id false) => unauthorized?)

      (fact "can't refer to non-existent user id"
        (comment-application sonja id false "bad-id") => (partial expected-failure? "to-is-not-id-of-any-user-in-system"))

      (fact "the target parameter cannot be a string"
        (command sonja :add-comment :id id :text "comment1" :target "application" :roles []) => (partial expected-failure? "error.unknown-type"))

      (fact "the roles parameter cannot be a string"
        (command sonja :add-comment :id id :text "comment1" :target {:type "application"} :roles "applicant") => (partial expected-failure? "error.non-vector-parameters"))

      (fact "Comment text is trimmed"
        (command pena :add-comment :id id
                 :text "      This is the beef.    \n  \n"
                 :target {:type "application"}
                 :roles []) => ok?
        (-> (query-application pena id)
            :comments last :text)
        => "This is the beef."))))
