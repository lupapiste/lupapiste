(ns lupapalvelu.migration.auth-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.migration.auth :as auth]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [monger.operators :refer [$elemMatch $push $set]]
            [sade.date :as date]
            [sade.strings :as ss]))

(def test-old-user
  {:id       "old-user-id-123"
   :username "old.user@example.com"
   :email    "old.user@example.com"})

(def test-new-user
  {:id       "new-user-id-456"
   :username "new.user@example.com"
   :email    "new.user@example.com"})

(def test-application-with-old-auth
  {:id   "LP-123-2024-00001"
   :auth [{:id       "old-user-id-123"
           :username "old.user@example.com"
           :role     "writer"}
          {:id       "other-user-id"
           :username "other.user@example.com"
           :role     "reader"}]})

(deftest transfer-auths-precondition-test
  (testing "Precondition: emails must be different"
    (is (thrown? AssertionError
                 (auth/transfer-auths "TEST" "same@example.com" "same@example.com")))))

(deftest transfer-auths-returns-correct-structure-test
  (testing "Returns map with :apply-when and :execute functions"
    (with-redefs [usr/get-user-by-email (constantly nil)]
      (let [result (auth/transfer-auths "TEST-1234" "old@example.com" "new@example.com")]
        (is (map? result))
        (is (contains? result :apply-when))
        (is (contains? result :execute))
        (is (fn? (:apply-when result)))
        (is (fn? (:execute result)))))))

(deftest transfer-auths-apply-when-test
  (testing "apply-when returns false when old user doesn't exist"
    (with-redefs [usr/get-user-by-email (fn [email]
                                          (when (= email "new@example.com")
                                            test-new-user))]
      (let [{:keys [apply-when]} (auth/transfer-auths "TEST" "old@example.com" "new@example.com")]
        (is (false? (apply-when))))))

  (testing "apply-when returns false when new user doesn't exist"
    (with-redefs [usr/get-user-by-email (fn [email]
                                          (when (= email "old@example.com")
                                            test-old-user))]
      (let [{:keys [apply-when]} (auth/transfer-auths "TEST" "old@example.com" "new@example.com")]
        (is (false? (apply-when))))))

  (testing "apply-when returns false when both users exist but no matching applications"
    (with-redefs [usr/get-user-by-email (fn [email]
                                          (case email
                                            "old@example.com" test-old-user
                                            "new@example.com" test-new-user
                                            nil))
                  mongo/any?            (constantly false)]
      (let [{:keys [apply-when]} (auth/transfer-auths "TEST" "old@example.com" "new@example.com")]
        (is (false? (apply-when))))))

  (testing "apply-when returns true when both users exist and there are matching applications"
    (with-redefs [usr/get-user-by-email (fn [email]
                                          (case email
                                            "old@example.com" test-old-user
                                            "new@example.com" test-new-user
                                            nil))
                  mongo/any?            (fn [collection query]
                                          (and (= collection :applications)
                                               (= query {:auth {$elemMatch {:id "old-user-id-123"}}})))]
      (let [{:keys [apply-when]} (auth/transfer-auths "TEST" "old@example.com" "new@example.com")]
        (is (true? (apply-when)))))))

(deftest transfer-auths-execute-test
  (testing "execute does nothing when users don't exist"
    (let [update-called? (atom false)]
      (with-redefs [usr/get-user-by-email (constantly nil)
                    mongo/update-by-query (fn [& _] (reset! update-called? true))]
        (let [{:keys [execute]} (auth/transfer-auths "TEST" "old@example.com" "new@example.com")]
          (execute)
          (is (false? @update-called?))))))

  (testing "execute updates applications with correct parameters"
    (let [update-args (atom nil)
          now         (date/now)
          now-ts      (date/timestamp now)]
      (with-redefs [usr/get-user-by-email (fn [email]
                                            (case email
                                              "old@example.com" test-old-user
                                              "new@example.com" test-new-user
                                              nil))
                    mongo/update-by-query (fn [collection query update-map]
                                            (reset! update-args {:collection collection
                                                                 :query      query
                                                                 :update-map update-map}))
                    date/now              (constantly now)]
        (let [{:keys [execute]} (auth/transfer-auths "TT-1234" "old@example.com" "new@example.com")]
          (execute)
          (is (some? @update-args))
          (is (= :applications (:collection @update-args)))
          (is (= {:auth {$elemMatch {:id "old-user-id-123"}}} (:query @update-args)))
          (let [update-map (:update-map @update-args)]
            (is (contains? update-map $set))
            (is (= "new-user-id-456" (get-in update-map [$set :auth.$.id])))
            (is (= "new.user@example.com" (get-in update-map [$set :auth.$.username])))
            (is (contains? update-map $push))
            (is (= now-ts (get-in update-map [$push :_sheriff-notes :created])))
            (is (= "TT-1234 Migration: Auth change old.user@example.com (old-user-id-123) -> new.user@example.com (new-user-id-456)."
                   (get-in update-map [$push :_sheriff-notes :note]))))))))

  (testing "execute uses prefix in sheriff note"
    (let [update-args (atom nil)]
      (with-redefs [usr/get-user-by-email (fn [email]
                                            (case email
                                              "old@example.com" test-old-user
                                              "new@example.com" test-new-user
                                              nil))
                    mongo/update-by-query (fn [collection query update-map]
                                            (reset! update-args {:collection collection
                                                                 :query      query
                                                                 :update-map update-map}))
                    date/now              (constantly (date/now))]
        (let [{:keys [execute]} (auth/transfer-auths "CUSTOM-PREFIX" "old@example.com" "new@example.com")]
          (execute)
          (let [note (get-in @update-args [:update-map $push :_sheriff-notes :note])]
            (is (ss/starts-with? note "CUSTOM-PREFIX Migration:"))))))))

(deftest transfer-auths-lazy-evaluation-test
  (testing "User lookup is delayed until apply-when or execute is called"
    (let [lookup-count (atom 0)]
      (with-redefs [usr/get-user-by-email (fn [_]
                                            (swap! lookup-count inc)
                                            nil)]
        ;; Creating the migration doesn't trigger user lookup
        (auth/transfer-auths "TEST" "old@example.com" "new@example.com")
        (is (= 0 @lookup-count))

        ;; Calling apply-when triggers lookup
        (let [{:keys [apply-when]} (auth/transfer-auths "TEST" "old@example.com" "new@example.com")]
          (apply-when)
          (is (= 2 @lookup-count)))))))

(deftest transfer-auths-schema-validation-test
  (testing "Validates prefix is non-blank string"
    (is (thrown? Exception
                 (auth/transfer-auths "" "old@example.com" "new@example.com"))))

  (testing "Validates old-email format"
    (is (thrown? Exception
                 (auth/transfer-auths "TEST" "not-an-email" "new@example.com"))))

  (testing "Validates new-email format"
    (is (thrown? Exception
                 (auth/transfer-auths "TEST" "old@example.com" "not-an-email")))))

(deftest purge-auth-test
  (testing "Returns nil when auth already contains only one matching good id"
    (is (nil? (auth/purge-auth {:auth [{:id "good-id" :username "good@example.com"}
                                       {:id "other-id" :username "other@example.com"}]}
                               "good-id"
                               #{"bad-id-1" "bad-id-2"}))))

  (testing "Precondition requires auth to contain the good id"
    (is (thrown? AssertionError
                 (auth/purge-auth {:auth [{:id "other-id-1" :username "one@example.com"}
                                          {:id "other-id-2" :username "two@example.com"}]}
                                  "good-id"
                                  #{"bad-id-1" "bad-id-2"}))))

  (testing "Keeps first good id, removes duplicate good ids and bad ids, and preserves unrelated auths"
    (is (= [{:id "good-id" :username "good@example.com" :role "writer"}
            {:id "other-id" :username "other@example.com" :role "reader"}
            {:id "another-id" :username "another@example.com" :role "commenter"}]
           (auth/purge-auth {:auth [{:id "bad-id-1" :username "old1@example.com" :role "writer"}
                                    {:id "good-id" :username "good@example.com" :role "writer"}
                                    {:id "other-id" :username "other@example.com" :role "reader"}
                                    {:id "bad-id-2" :username "old2@example.com" :role "reader"}
                                    {:id "good-id" :username "duplicate@example.com" :role "commenter"}
                                    {:id "another-id" :username "another@example.com" :role "commenter"}]}
                            "good-id"
                            #{"bad-id-1" "bad-id-2"}))))

  (testing "If first matching auth is bad and later one is good, bad entry is removed and good one is kept"
    (is (= [{:id "other-id" :username "other@example.com"}
            {:id "good-id" :username "good@example.com"}]
           (auth/purge-auth {:auth [{:id "other-id" :username "other@example.com"}
                                    {:id "bad-id-1" :username "old@example.com"}
                                    {:id "good-id" :username "good@example.com"}]}
                            "good-id"
                            #{"bad-id-1"})))))
