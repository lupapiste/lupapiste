(ns lupapalvelu.notice-api-itest
  (require [midje.sweet :refer :all]
           [lupapalvelu.itest-util :refer :all]))

(fact "adding notice"
  (let [{id :id} (create-and-submit-application pena)]
    (fact "user can't set application urgency"
      pena =not=> (allowed? :change-urgency :id id :urgency "urgent")
      (change-application-urgency pena id "urgent") =not=> ok?)

    (fact "authority can set application urgency"
      sonja => (allowed? :change-urgency :id id :urgency "urgent")
      (change-application-urgency sonja id "urgent") => ok?
      (:urgency (query-application sonja id)) => "urgent")

    (fact "user can't set notice message"
      pena =not=> (allowed? :add-authority-notice :id id :authorityNotice "foobar")
      (add-authority-notice pena id "foobar") =not=> ok?)

    (fact "authority can set notice message"
      sonja => (allowed? :add-authority-notice :id id :authorityNotice "respect my authority")
      (add-authority-notice sonja id "respect my athority") => ok?
      (:authorityNotice (query-application sonja id)) => "respect my athority")

    (fact "user can't set application tags"
      (command pena :add-application-tags :id id :tags ["foo" "bar"]) =not=> ok?)

    (fact "authority can set application tags"
      (command sonja :add-application-tags :id id :tags ["foo" "bar"]) => ok?
      (:tags (query-application sonja id)) => ["foo" "bar"])

    (fact "only auth admin can add new tags"
      (command sipoo :save-organization-tags :tags [{:id nil :label "makeja"} {:id nil :label "nigireja"}]) => ok?
      (command sonja :save-organization-tags :tags [{:id nil :label "illegal"}] =not=> ok?)
      (command pena :save-organization-tags :tags [{:id nil :label "makeja"}] =not=> ok?)
      (:tags (query sipoo :get-organization-tags)) => (just [(just {:id string? :label "makeja"})
                                                             (just {:id string? :label "nigireja"})]))

    (fact "only authority can fetch available tags"
      (query pena :get-organization-tags :organization "753-R") =not=> ok?
      (map :label (:tags (query sonja :get-organization-tags :organizationId "753-R"))) => ["makeja" "nigireja"])))
