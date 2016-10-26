(ns lupapalvelu.link-permit-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.link-permit :refer :all]
            [lupapalvelu.domain :as domain]))


(testable-privates lupapalvelu.link-permit
                   get-backend-id
                   update-backend-id-in-link-permit
                   link-permits-with-app-data)

(facts get-backend-id
  (fact "single verdict with backend-id"
    (get-backend-id {:verdicts [{:kuntalupatunnus ..backend-id..}]}) => ..backend-id..)

  (fact "single verdict without backend-id"
    (get-backend-id {:verdicts [{}]}) => nil)

  (fact "two verdicts, second has backend-id"
    (get-backend-id {:verdicts [{} {:kuntalupatunnus ..backend-id..}]}) => ..backend-id..)

  (fact "two verdicts, two backend-ids, return first"
    (get-backend-id {:verdicts [{:kuntalupatunnus ..backend-id1..} {:kuntalupatunnus ..backend-id2..}]}) => ..backend-id1..)

  (fact "no verdicts"
    (get-backend-id {}) => nil))

(facts update-backend-id-in-link-permit
  (fact "link with verdict"
    (update-backend-id-in-link-permit {:id ..app-id.. :app-data {:verdicts [{:kuntalupatunnus ..backend-id..}]}})
    => {:lupapisteId ..app-id.. :type "kuntalupatunnus" :id ..backend-id.. :app-data {:verdicts [{:kuntalupatunnus ..backend-id..}]}})

  (fact "link without backend-id"
    (update-backend-id-in-link-permit {:id ..app-id.. :app-data {:verdicts [{}]}})
    => {:id ..app-id.. :app-data {:verdicts [{}]}})

  (fact "no links"
    (update-backend-id-in-link-permit {:id "id1"})
    => {:id "id1"}))

(facts link-permits-with-app-data
  (fact "one application"
    (link-permits-with-app-data {:linkPermitData [{:id ..app-id..}]}) => [{:id ..app-id.. :app-data {:id ..app-id.. :state ..state..}}]
    (provided (domain/get-multiple-applications-no-access-checking [..app-id..] anything) => [{:id ..app-id.. :state ..state..}]))

  (fact "application not found"
    (link-permits-with-app-data {:linkPermitData [{:id ..app-id..}]}) => [{:id ..app-id.. :app-data nil}]
    (provided (domain/get-multiple-applications-no-access-checking [..app-id..] anything) => nil))

  (fact "multiple applications"
    (link-permits-with-app-data {:linkPermitData [{:id ..app-id1..} {:id ..app-id2..} {:id ..app-id3..} {:id ..app-id4..}]})
    => [{:id ..app-id1.. :app-data {:id ..app-id1.. :state ..state1..}}
        {:id ..app-id2.. :app-data {:id ..app-id2.. :state ..state2..}}
        {:id ..app-id3.. :app-data nil}
        {:id ..app-id4.. :app-data {:id ..app-id4.. :state ..state4..}}]
    (provided (domain/get-multiple-applications-no-access-checking [..app-id1.. ..app-id2.. ..app-id3.. ..app-id4..] anything)
              => [{:id ..app-id4.. :state ..state4..} {:id ..app-id1.. :state ..state1..} {:id ..app-id2.. :state ..state2..}])))

(facts update-backend-ids-in-link-permit-data
  (fact "one application"
    (update-backend-ids-in-link-permit-data {:linkPermitData [{:id ..app-id..}]})
    => {:linkPermitData [{:lupapisteId ..app-id.. :type "kuntalupatunnus" :id ..backend-id..}]}

    (provided (domain/get-multiple-applications-no-access-checking [..app-id..] anything)
              => [{:id ..app-id.. :state ..state.. :verdicts [{:kuntalupatunnus ..backend-id..}]}]))

  (fact "multiple applications"
    (update-backend-ids-in-link-permit-data {:linkPermitData [{:id ..app-id1..} {:id ..app-id2..} {:id ..app-id3..} {:id ..app-id4..}]})
    => {:linkPermitData [{:lupapisteId ..app-id1.. :type "kuntalupatunnus" :id ..backend-id1..}
                         {:lupapisteId ..app-id2.. :type "kuntalupatunnus" :id ..backend-id2..}
                         {:id ..app-id3..}
                         {:lupapisteId ..app-id4.. :type "kuntalupatunnus" :id ..backend-id4..}]}

    (provided (domain/get-multiple-applications-no-access-checking [..app-id1.. ..app-id2.. ..app-id3.. ..app-id4..] anything)
              => [{:id ..app-id4.. :state ..state4.. :verdicts [{:kuntalupatunnus ..backend-id4..}]}
                  {:id ..app-id1.. :state ..state1.. :verdicts [{:kuntalupatunnus ..backend-id1..}]}
                  {:id ..app-id2.. :state ..state2.. :verdicts [{:kuntalupatunnus ..backend-id2..}]}])))
