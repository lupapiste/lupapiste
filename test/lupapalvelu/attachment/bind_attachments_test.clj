(ns lupapalvelu.attachment.bind-attachments-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.bind :as bind]
            [lupapalvelu.attachment.bind-attachments-api :as bind-api]
            [lupapalvelu.operations :as op]
            [lupapalvelu.test-util :refer [expected-failure?]]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test :refer [match?]]))

(deftest validate-attachment-groups-test
  (testing "one file"
    (with-redefs [op/get-primary-operation-metadata (constantly nil)
                  op/operation-id-exists?           (constantly true)
                  att/operations-checker            (constantly nil)]
      (let [filedatas [{:group {:groupType "operation" :operations [{:id "op-id" :name "op-name"}]}}]
            command   {:data {:filedatas filedatas} :application :test-application}]
        (is (nil? (bind-api/validate-attachment-groups command))))))

  (testing "multiple files"
    (with-redefs [op/get-primary-operation-metadata (constantly nil)
                  op/operation-id-exists?           (constantly true)
                  att/operations-checker            (constantly nil)]
      (let [filedatas [{:group {:groupType "operation" :operations [{:id "op-id" :name "op-name"}]}}
                       {:group {:groupType "parties"}}
                       {:group {:groupType "building-site" :operations nil}}
                       {:group nil}]
            command   {:data {:filedatas filedatas} :application :test-application}]
        (is (nil? (bind-api/validate-attachment-groups command))))))

  (testing "attachment op selection not allowed"
    (let [filedatas [{:group nil}]
          command   {:data {:filedatas filedatas} :application :test-application}]
      (is (nil? (bind-api/validate-attachment-groups command)))))

  (testing "attachment op selection not allowed - trying to set"
    (with-redefs [op/get-primary-operation-metadata (constantly false)]
      (let [filedatas [{:group {:groupType "building-site"}}]
            command   {:data {:filedatas filedatas} :application :test-application}]
        (is (expected-failure? :error.illegal-meta-type (bind-api/validate-attachment-groups command))))))

  (testing "illegal group type"
    (let [filedatas [{:group {:groupType "invalid group type"}}]
          command   {:data {:filedatas filedatas} :application :test-application}]
      (is (expected-failure? :error.illegal-attachment-group-type (bind-api/validate-attachment-groups command)))))

  (testing "illegal operation"
    (with-redefs [op/get-primary-operation-metadata (constantly nil)
                  att/operations-checker            (constantly :failure)]
      (let [filedatas [{:group {:groupType "parties"}}
                       {:group {:groupType "operation" :operations [{:id "op-id" :name "op-name"} {}]}}]
            command   {:data {:filedatas filedatas} :application :test-application}]
        (is (expected-failure? :error.illegal-attachment-operation (bind-api/validate-attachment-groups command)))))))

(deftest attachment-id-filedatas-pre-checks-test
  (let [locked     {:id     "att-locked"
                    :locked true}
        read-only  {:id       "att-read-only"
                    :readOnly true}
        not-needed {:id        "att-not-needed"
                    :notNeeded true}
        good       {:id               "att-good"
                    :applicationState "draft"}
        applicant  {:id   "user-id"
                    :role "applicant"}
        authority  {:id       "authority-id"
                    :orgAuthz {:123-R [:authority]}}
        cmd        (fn [user atts & filedatas]
                     {:application {:id           "app-id"
                                    :auth         [{:id   "user-id"
                                                    :role "writer"}]
                                    :attachments  atts
                                    :organization "123-R"}
                      :user        user
                      :data        {:id        "app-id"
                                    :filedatas filedatas}})
        tester     (comp bind-api/attachment-id-filedatas-pre-checks cmd)]
    (testing "No matching filedatas"
      (is (nil? (bind-api/attachment-id-filedatas-pre-checks {})))
      (is (nil? (bind-api/attachment-id-filedatas-pre-checks {:data {:filedatas [{:group  "hello"
                                                                                  :fileId "fid1"}]}}))))
    (testing "Good"
      (is (nil? (tester applicant [good] {:attachmentId "att-good"}))))

    (testing "Missing"
      (is (= {:ok false :text "error.attachment.id"}
             (tester applicant [locked read-only] {:attachmentId "att-good"}))))

    (testing "Locked"
      (is (= {:ok false :text "error.attachment-is-locked"}
             (tester applicant [good locked]
                     {:attachmentId "att-good"}
                     {:attachmentId "att-locked"}))))

    (testing "Read-only"
      (is (= {:desc "Attachment is read only."
              :ok   false
              :text "error.unauthorized"}
             (tester applicant [good read-only]
                     {:attachmentId "att-good"}
                     {:attachmentId "att-read-only"}))))

    (testing "Not needed"
      (is (= {:ok false :text "error.attachment.not-needed"}
             (tester applicant [good not-needed]
                     {:attachmentId "att-good"}
                     {:attachmentId "att-not-needed"}))))

    (testing "Attachment state is draft but application is verdictGiven."
      (let [command (assoc-in (cmd authority [good] {:attachmentId "att-good"})
                              [:application :state] "verdictGiven")]
        (testing "Authority can edit"
          (is (nil? (bind-api/attachment-id-filedatas-pre-checks command))))
        (testing "Applicant cannot edit"
          (is (= {:ok false :text "error.pre-verdict-attachment"}
                 (bind-api/attachment-id-filedatas-pre-checks (assoc command :user applicant)))))))

    (testing "Included in a published bulletin. NOTE: currently only YMP bulletins supported!"
      (with-redefs [att/included-in-published-bulletin? (constantly true)]
        (let [command (assoc (cmd authority [good] {:attachmentId "att-good"})
                             :application-bulletins [{:id "app-id"}])]
          (is (= {:ok false :text "error.attachment-included-in-published-bulletin"}
                 (bind-api/attachment-id-filedatas-pre-checks command))))))

    (testing "Shortcuts on the first error"
      (is (= {:ok false :text "error.attachment-is-locked"}
             (tester applicant [good locked not-needed]
                     {:attachmentId "att-good"}
                     {:attachmentId "att-locked"}
                     {:attachmentId "att-not-needed"})))
      (is (= {:ok false :text "error.attachment.not-needed"}
             (tester applicant [good locked not-needed]
                     {:attachmentId "att-good"}
                     {:attachmentId "att-not-needed"}
                     {:attachmentId "att-locked"}))))))

(deftest uniq-filenames-test
  (is (= #{}
         (bind/uniq-filenames [])))
  (is (= #{"f1"}
         (bind/uniq-filenames [{:filename "f1"}])))
  (is (= #{"f2" "f3"}
         (bind/uniq-filenames [{:filename "f1"}
                               {:filename "f2"}
                               {:filename "f1"}
                               {:filename "f3"}])))
  (is (= #{}
         (bind/uniq-filenames [{:filename "f1"}
                               {:filename "f2"}
                               {:filename "f1"}
                               {:filename "f2"}]))))

(defn- make-att [id filename]
  {:id id :latestVersion {:filename filename}})

(deftest resolve-attachment-update-candidates-test
  (let [application {:attachments [(make-att "a1" "name1")
                                   (make-att "a2" "name2")
                                   (make-att "a3" "name3")
                                   (make-att "a4" "name3") ;; Duplicate
                                   (make-att "a5" "name5")
                                   (make-att "a6" "name6")
                                   ;; The last two match normalization
                                   (make-att "a7" "hämy")
                                   (make-att "a8" (str "mo" (char 776) "rko" (char 776)))]}]
    (is (= [{:attachmentId "a1" :fileId "f1"}]
           (bind/resolve-attachment-update-candidates application
                                                      [{:fileId "f1" :filename "name1"}])))
    (is (match? (m/in-any-order [{:attachmentId "a1" :fileId "f1"}
                                 {:attachmentId "a2" :fileId "f2"}
                                 {:attachmentId "a7" :fileId "f7"}
                                 {:attachmentId "a8" :fileId "f8"}])
                (bind/resolve-attachment-update-candidates application
                                                           [{:fileId "f1" :filename "name1"}
                                                            {:fileId "f2" :filename "name2"}
                                                            {:fileId "f3" :filename "name3"}
                                                            {:fileId "f5" :filename "name5"}
                                                            {:fileId "f55" :filename "name5"}
                                                            {:fileId "f6" :filename "missing"}
                                                            {:fileId "f7" :filename (str "ha" (char 776) "my")}
                                                            {:fileId "f8" :filename "mörkö"}])))
    (is (= [] (bind/resolve-attachment-update-candidates application [])))))
