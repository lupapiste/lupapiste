(ns lupapalvelu.attachment.accessibility-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.attachment.accessibility :as accessibility]))

(defn make-auths [users]
  (mapv (fn [{:keys [id company]}]
          (merge {:id id :role "writer" :type "writer"}
                 (when company
                   {:type "company"})))
        users))

(deftest accessing-attachment-test
  (let [user1                      {:id "1" :role "applicant"}
        user2                      {:id "2" :role "applicant"}
        company-user               {:id      "salaryman"
                                    :role    "applicant"
                                    :company {:id     "factory"
                                              :role   "admin"
                                              :submit true}}
        colleague                  {:id      "colleague"
                                    :role    "applicant"
                                    :company {:id     "factory"
                                              :role   "user"
                                              :submit true}}
        user-authority             {:id "3" :role "authority" :orgAuthz {:123 #{:authority}}}
        other-authority            {:id "4" :role "authority" :orgAuthz {:321 #{:authority}}}
        application-skeleton       {:auth [] :organization "123"}
        user1-application          (assoc application-skeleton :auth (make-auths [user1 company-user]))
        user2-application          (assoc application-skeleton :auth (make-auths [user2 company-user]))
        both-users-application     (assoc application-skeleton :auth (make-auths [user1 user2 company-user]))

        att0-empty                 {}
        att1-auth-empty            {:latestVersion {:fileId "322" :user {:id "1"}}
                                    :auth          []
                                    :versions      [{:fileId "321" :user {:id "1"}}
                                                    {:fileId "322" :user {:id "1"}}]}
        att1-no-meta-u1            {:latestVersion {:fileId "322" :user {:id "1"}}
                                    :auth          [user1]
                                    :versions      [{:fileId "321" :user {:id "1"}}
                                                    {:fileId "322" :user {:id "1"}}]}
        att-authority              {:metadata      {:nakyvyys "viranomainen"}
                                    :auth          [user-authority]
                                    :latestVersion {:fileId "322" :user {:id "1"}}
                                    :versions      [{:fileId "321" :user {:id "1"}}
                                                    {:fileId "322" :user {:id "1"}}]}
        att-authority-auth-u1      {:metadata      {:nakyvyys "viranomainen"}
                                    :auth          [user1]
                                    :latestVersion {:fileId "322" :user {:id "1"}}
                                    :versions      [{:fileId "321" :user {:id "1"}}
                                                    {:fileId "322" :user {:id "1"}}]}
        att-authority-company-user {:metadata      {:nakyvyys "viranomainen"}
                                    :auth          [company-user]
                                    :latestVersion {:fileId "322" :user {:id "1"}}
                                    :versions      [{:fileId "321" :user {:id "1"}}
                                                    {:fileId "322" :user {:id "1"}}]}
        att-parties-auth-u1        {:metadata      {:nakyvyys "asiakas-ja-viranomainen"}
                                    :auth          [user1]
                                    :latestVersion {:fileId "322" :user {:id "1"}}
                                    :versions      [{:fileId "321" :user {:id "1"}}
                                                    {:fileId "322" :user {:id "1"}}]}
        att-public                 {:metadata      {:nakyvyys "julkinen"}
                                    :auth          [user1]
                                    :latestVersion {:fileId "322" :user {:id "1"}}
                                    :versions      [{:fileId "321" :user {:id "1"}}
                                                    {:fileId "322" :user {:id "1"}}]}]
    (testing "nils und nulls"
      (is (thrown? AssertionError (accessibility/can-access-attachment? nil nil nil)))
      (is (thrown? AssertionError (accessibility/can-access-attachment? nil nil att1-auth-empty)))
      (is (true? (accessibility/can-access-attachment? nil application-skeleton att1-no-meta-u1))))
    (testing "if versions exists, then should also auth, else assert fail"
      (is (thrown? AssertionError (accessibility/can-access-attachment? user-authority application-skeleton att1-auth-empty))))

    (testing "empty attachment can be accessed by anyone, to upload versions"
      (is (true? (accessibility/can-access-attachment? user1 application-skeleton att0-empty)))
      (is (true? (accessibility/can-access-attachment? company-user application-skeleton att0-empty)))
      (is (true? (accessibility/can-access-attachment? user-authority application-skeleton att0-empty))))
    (testing "if no metadata, attachment is regarded as public"
      (is (true? (accessibility/can-access-attachment? user1 application-skeleton att1-no-meta-u1)))
      (is (true? (accessibility/can-access-attachment? user2 application-skeleton att1-no-meta-u1)))
      (is (true? (accessibility/can-access-attachment? company-user application-skeleton att1-no-meta-u1)))
      (is (true? (accessibility/can-access-attachment? nil application-skeleton att1-no-meta-u1))))

    (testing "only authority attachment visibility ('viranomainen')"
      (testing "authority can access"
        (is (true? (accessibility/can-access-attachment? user-authority application-skeleton att-authority)))
        (is (true? (accessibility/can-access-attachment? user-authority application-skeleton att-authority-auth-u1)))
        (testing "... but only if in same org as application"
          (is (false? (accessibility/can-access-attachment? other-authority application-skeleton att-authority-auth-u1)))))
      (testing "normal users can't access unless authed to attachment"
        (is (false? (accessibility/can-access-attachment? user1 application-skeleton att-authority)))
        (testing ".. not even when authed to application"
          (is (false? (accessibility/can-access-attachment? user1 user1-application att-authority)))
          (is (false? (accessibility/can-access-attachment? company-user user1-application att-authority)))))

      (testing "can access when user is authed to attachment"
        (is (true? (accessibility/can-access-attachment? user1 application-skeleton att-authority-auth-u1))))
      (testing "can access when user is authed to both attachment and application"
        (is (true? (accessibility/can-access-attachment? user1 user1-application att-authority-auth-u1))))
      (testing "user2 not authed to attachment"
        (is (false? (accessibility/can-access-attachment? user2 application-skeleton att-authority-auth-u1))))
      (testing "application auth for user2 is not enough"
        (is (false? (accessibility/can-access-attachment? user2 user2-application att-authority-auth-u1)))
        (is (false? (accessibility/can-access-attachment? user2 both-users-application att-authority-auth-u1))))
      (testing "Colleague cannot access company-users private attachment"
        (is (true? (accessibility/can-access-attachment? company-user user2-application att-authority-company-user)))
        (is (false? (accessibility/can-access-attachment? colleague user2-application att-authority-company-user)))))

    (testing "authed and authority attachment visibility ('asiakas-ja-viranomainen')"
      (testing "can't access if user isn't authed to application"
        (is (false? (accessibility/can-access-attachment? user2 user1-application att-parties-auth-u1))))
      (testing "is available for users authed in application, and authorities"
        (is (true? (accessibility/can-access-attachment? user2 user2-application att-parties-auth-u1)))
        (is (false? (accessibility/can-access-attachment? user2 user1-application att-parties-auth-u1)))
        (is (true? (accessibility/can-access-attachment? user1 user2-application att-parties-auth-u1)))
        (is (true? (accessibility/can-access-attachment? user2 both-users-application att-parties-auth-u1)))
        (is (true? (accessibility/can-access-attachment? user-authority application-skeleton att-parties-auth-u1))))
      (testing "is available for the members of the authed company"
        (is (true? (accessibility/can-access-attachment? company-user user2-application att-parties-auth-u1))))
      (testing "not visible for authorities in other organization"
        (is (false? (accessibility/can-access-attachment? other-authority application-skeleton att-parties-auth-u1))))
      (testing "can be seen by parties, even if attachment authed only to authority"
        (is (true? (accessibility/can-access-attachment? user2
                                                         both-users-application
                                                         (assoc-in att-authority [:metadata :nakyvyys] "asiakas-ja-viranomainen"))))))

    (testing "public"
      (is (true? (accessibility/can-access-attachment? nil application-skeleton att-public)))
      (is (true? (accessibility/can-access-attachment? user1 user2-application att-public)))
      (is (true? (accessibility/can-access-attachment? user1 both-users-application att-public)))
      (is (true? (accessibility/can-access-attachment? user-authority application-skeleton att-public)))
      (is (true? (accessibility/can-access-attachment? other-authority application-skeleton att-public)))
      (is (true? (accessibility/can-access-attachment? company-user application-skeleton att-public))))))
