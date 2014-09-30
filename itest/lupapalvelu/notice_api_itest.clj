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
          (:authorityNotice (query-application sonja id)) => "respect my athority")))
          