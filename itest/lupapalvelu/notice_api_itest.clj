(ns lupapalvelu.notice-api-itest
  (require [midje.sweet :refer :all]
           [lupapalvelu.itest-util :refer :all]))

(apply-remote-minimal) ; minimal ensures wanted organization tags exist

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

    (fact "read-only authority can't set notice message"
      luukas =not=> (allowed? :add-authority-notice :id id :authorityNotice "foobar")
      (add-authority-notice luukas id "foobar") =not=> ok?)

    (fact "authority can set notice message"
      sonja => (allowed? :add-authority-notice :id id :authorityNotice "respect my authority")
      (add-authority-notice sonja id "respect my athority") => ok?
      (:authorityNotice (query-application sonja id)) => "respect my athority")

    (facts "Application tags" ; tags are in minimal fixture
      (fact "user can't set application tags"
        (command pena :add-application-tags :id id :tags ["111111111111111111111111" "222222222222222222222222"]) =not=> ok?)
      (fact "authority can set application tags"
        (command sonja :add-application-tags :id id :tags ["111111111111111111111111" "222222222222222222222222"]) => ok?)
      (fact "authority can't set tags that are not defined in organization"
        (command sonja :add-application-tags :id id :tags ["foo" "bar"]) => (partial expected-failure? "error.unknown-tags"))

      (let [query (query-application sonja id)
            org-tags (get-in query [:organizationMeta :tags])]
        (:tags query) => ["111111111111111111111111" "222222222222222222222222"]

        (fact "application's organization meta includes correct tags with ids as keys"
          org-tags => {:111111111111111111111111 "yl\u00E4maa", :222222222222222222222222 "ullakko"}))

      (fact "When tag is removed, it is also removed from applications"
        (let [id (create-app-id sonja)]
          (command sipoo :save-organization-tags :tags [{:id "123000000000000000000000" :label "foo"} {:id "321000000000000000000000" :label "bar"}]) => ok?
          (command sonja :add-application-tags :id id :tags ["123000000000000000000000" "321000000000000000000000"]) => ok?
          (:tags (query-application sonja id)) => (just ["123000000000000000000000" "321000000000000000000000"])
          (command sipoo :save-organization-tags :tags [{:id "123000000000000000000000" :label "foo"}]) => ok?

          (fact "only 123 is left as 321 was removed"
            (:tags (query-application sonja id)) => (just ["123000000000000000000000"])))))))
