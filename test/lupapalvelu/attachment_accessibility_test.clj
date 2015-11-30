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
        att-authority-no-auth {:metadata {:nakyvyys "viranomainen"}
                               :latestVersion   {:fileId "322" :user {:id "1"}}
                               :versions        [{:fileId "321" :user {:id "1"}}
                                                 {:fileId "322" :user {:id "1"}}]}
        att-authority-auth-u1 {:metadata {:nakyvyys "viranomainen"}
                               :auth [user1]
                               :latestVersion   {:fileId "322" :user {:id "1"}}
                               :versions        [{:fileId "321" :user {:id "1"}}
                                                 {:fileId "322" :user {:id "1"}}]}
        att-parties-no-auth {:metadata {:nakyvyys "asiakas-ja-viranomainen"}
                             :latestVersion   {:fileId "322" :user {:id "1"}}
                             :versions        [{:fileId "321" :user {:id "1"}}
                                               {:fileId "322" :user {:id "1"}}]}
        att-parties-auth-u1 {:metadata {:nakyvyys "viranomainen"}
                             :auth [user1]
                             :latestVersion   {:fileId "322" :user {:id "1"}}
                             :versions        [{:fileId "321" :user {:id "1"}}
                                               {:fileId "322" :user {:id "1"}}]}]

     (fact "empty attachment can be accessed by anyone, to upload versions"
           (can-access-attachment? user1 nil att0-empty) => true
           (can-access-attachment? user-authority nil att0-empty) => true)
     (fact "if no metadata or auth, attachment is regarded as public"
           (can-access-attachment? user1 nil att1-no-meta) => true)

    (facts "only authority attachment visibility"
      (facts "regarded as public if no auth array for attachment is set"
        (can-access-attachment? user1 nil att-authority-no-auth) => true
        (can-access-attachment? user1 {:auth [{:user {:id "1"}}]} att-authority-no-auth) => true
        (fact "authority can access"
          (can-access-attachment? user-authority nil att-authority-no-auth)) => true)

     (facts "attachment auth for user1"
       (fact "can access when user is authed to attachment"
         (can-access-attachment? user1 nil att-authority-auth-u1) => true)
       (fact "user2 not authed"
         (can-access-attachment? user2 nil att-authority-auth-u1) => false)
       (fact "authority can access"
         (can-access-attachment? user-authority nil att-authority-auth-u1) => true)))

     (facts "authed and authority attachment visibility")))
