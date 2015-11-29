(ns lupapalvelu.attachment-accessibility-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.attachment-accessibility :refer :all]))

(facts "facts about accessing attachment(s)"
  (let [user1 {:id "1" :role "applicant"}
        user2 {:id "2" :role "applicant"}
        user-authority {:id "2" :role "authority"}

        att0-empty {}
        att1-no-meta {:latestVersion {:fileId "322" :user {:id "1"}}
                      :versions [{:fileId "321" :user {:id "1"}}
                                 {:fileId "322" :user {:id "1"}}]}
        att2-authority {:metadata {:nakyvyys "viranomainen"}
                        :latestVersion {:fileId "322" :user {:id "1"}}
                        :versions [{:fileId "321" :user {:id "1"}}
                                   {:fileId "322" :user {:id "1"}}]}]

    (fact "empty attachment can be accessed by anyone, to upload versions"
      (can-access-attachment? user1 att0-empty) => true
      (can-access-attachment? user1 att0-empty) => true)

    (can-access-attachment? user1 att1-no-meta) => true
    (can-access-attachment? user1 att1-no-meta) => true

    (fact "can't access only authority attachment unless owner or authority"
      (can-access-attachment? user1 att2-authority) => true ; owner of latestVersion
      (can-access-attachment? user2 att2-authority) => false
      (can-access-attachment? user-authority att2-authority) => true)))
