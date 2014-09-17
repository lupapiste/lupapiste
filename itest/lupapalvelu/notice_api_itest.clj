(ns lupapalvelu.notice-api-itest
  (require [midje.sweet :refer :all]
           [lupapalvelu.itest-util :refer :all]))

(fact "adding notice"
  (let [{id :id} (create-and-submit-application pena)]
    (fact "user can't set application urgent"
          pena =not=> (allowed? :toggle-urgent :id id :urgent true)
          (toggle-application-urgent pena id true) =not=> ok?)    

    (fact "authority can set application urgent"
          sonja => (allowed? :toggle-urgent :id id :urgent true)
          (toggle-application-urgent sonja id true) => ok?
          (:urgent (query-application sonja id)) => true)

    (fact "user can't set notice message"
          pena =not=> (allowed? :add-authority-notice :id id :authorityNotice "foobar")
          (add-authority-notice pena id "foobar") =not=> ok?)

    (fact "authority can set notice message"
          sonja => (allowed? :add-authority-notice :id id :authorityNotice "respect my authority")
          (add-authority-notice sonja id "respect my athority") => ok?
          (:authorityNotice (query-application sonja id)) => "respect my athority")))
          