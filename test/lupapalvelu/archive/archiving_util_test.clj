(ns lupapalvelu.archive.archiving-util-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.action :as action]
            [lupapalvelu.archive.archiving-util :as archiving-util]
            [monger.operators :refer :all]))

(defn check-mark [archived ts ks set-map]
  (let [updated-call (atom nil)]
    (with-redefs [action/application->command (fn [_] :command)
                  action/update-application   (fn [command update]
                                                (reset! updated-call [command update])
                                                nil)]
      (is (nil? (archiving-util/mark-application-archived {:archived archived} ts ks)))
      (is (= [:command {$set set-map}] @updated-call)))))

(deftest mark-application-archived-test
  (let [key-fail?    #"No archived timestamp keys"
        schema-fail? #"does not match schema"]
    (testing "failure"
      (is (thrown-with-msg? Throwable key-fail?
                            (archiving-util/mark-application-archived {} 12345)))
      (is (thrown-with-msg? Throwable key-fail?
                            (archiving-util/mark-application-archived {} 12345 [])))
      (is (thrown-with-msg? Throwable key-fail?
                            (archiving-util/mark-application-archived {} 12345 [[]])))

      (is (thrown-with-msg? Throwable schema-fail?
                            (archiving-util/mark-application-archived {} 12345 nil)))
      (is (thrown-with-msg? Throwable schema-fail?
                            (archiving-util/mark-application-archived {} 12345 [nil])))
      (is (thrown-with-msg? Throwable schema-fail?
                            (archiving-util/mark-application-archived {} 12345 :bad)))
      (is (thrown-with-msg? Throwable schema-fail?
                            (archiving-util/mark-application-archived {} 12345 [:bad])))
      (is (thrown-with-msg? Throwable schema-fail?
                            (archiving-util/mark-application-archived {} 12345 :bad :application)))
      (is (thrown-with-msg? Throwable schema-fail?
                            (archiving-util/mark-application-archived {} 12345 [:completed :bad])))
      (is (thrown-with-msg? Throwable schema-fail?
                            (archiving-util/mark-application-archived {} 12345 :completed nil)))
      (is (thrown-with-msg? Throwable schema-fail?
                            (archiving-util/mark-application-archived {} "bad" :completed)))
      (is (thrown-with-msg? Throwable schema-fail?
                            (archiving-util/mark-application-archived {} 123.45 :completed))))

    (testing "success"
      (check-mark nil 12345 :initial {:archived.initial 12345})
      (check-mark nil nil :application {:archived.application nil
                                        :archived.initial     nil})
      (check-mark {:initial nil} 12345 :application
                  {:archived.application 12345
                   :archived.initial     12345})
      (check-mark {:initial 12345} 34567 :application
                  {:archived.application 34567})
      (check-mark {} 987 [:application :completed]
                  {:archived.application 987
                   :archived.completed   987
                   :archived.initial     987}))))

(deftest archival-access-test
  (let [permission :organization/document-search-access]
    (testing "returns true when at least one active organization has archive enabled and required permission"
      (is (true? (archiving-util/archival-access?
                   {:user               {:id       "user-id"
                                         :orgAuthz {:org-no-perm [:commenter]
                                                    :org-ok      [:authority]}}
                    :user-organizations [{:id                        "org-no-perm"
                                          :permanent-archive-enabled true}
                                         {:id                        "org-ok"
                                          :permanent-archive-enabled true
                                          :deactivated               false}]}
                   permission))))

    (testing "returns false when organization is deactivated"
      (is (false? (archiving-util/archival-access?
                    {:user               {:id       "user-id"
                                          :orgAuthz {:org-deactivated [:authority]}}
                     :user-organizations [{:id                        "org-deactivated"
                                           :permanent-archive-enabled true
                                           :deactivated               true}]}
                    permission))))

    (testing "returns false when permanent archive is not enabled"
      (is (false? (archiving-util/archival-access?
                    {:user               {:id       "user-id"
                                          :orgAuthz {:org-no-archive [:authority]}}
                     :user-organizations [{:id "org-no-archive"}]}
                    permission))))

    (testing "returns false when permission is missing"
      (is (false? (archiving-util/archival-access?
                    {:user               {:id       "user-id"
                                          :orgAuthz {:org-no-perm [:commenter]}}
                     :user-organizations [{:id                        "org-no-perm"
                                           :permanent-archive-enabled true}]}
                    permission))))

    (testing "returns false when user has no organizations"
      (is (false? (archiving-util/archival-access?
                    {:user               {:id "user-id"}
                     :user-organizations []}
                    permission)))
      (is (false? (archiving-util/archival-access?
                    {:user {:id "user-id"}}
                    permission))))))
