(ns lupapalvelu.comment-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]))

(fact "adding comments"
  (let [{id :id}  (create-and-submit-application pena)]
    (fact "applicant can't comment with to"
      pena =not=> (allowed? :can-target-comment-to-authority)
      pena =not=> (allowed? :add-comment :id id :to irrelevant)
      (command pena :add-comment :id id :text "comment1" :target "application") => ok?
      (command pena :add-comment :id id :text "comment1" :target "application" :to sonja-id) =not=> ok?)
    (fact "authority can comment with to"
      sonja => (allowed? :can-target-comment-to-authority)
      sonja => (allowed? :add-comment :id id :to sonja-id)
      (command sonja :add-comment :id id :text "comment1" :target "application") => ok?
      (command sonja :add-comment :id id :text "comment1" :target "application" :to sonja-id) => ok?)))
