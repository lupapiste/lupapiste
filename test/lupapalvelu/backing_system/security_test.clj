(ns lupapalvelu.backing-system.security-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.backing-system.security :as bs-sec]
            [sade.strings :as ss]))

(deftest security-test
  (testing "blank params"
    (doseq [[u p] [["" ""] [" " " "] [nil nil]
                   ["user" ""] [nil "pass"]]]
      (is (nil? (bs-sec/encode-credentials u p)))
      (is (nil? (bs-sec/encode-credentials {:username u
                                            :password p})))
      (is (nil? (bs-sec/decode-password u p)))
      (is (nil? (bs-sec/decode-password {:crypto-iv u
                                         :password  p})))
      (is (nil? (bs-sec/get-credentials {:crypto-iv "foobar"
                                         :username  u
                                         :password  p}))))
    (doseq [c [nil "" "  "]]
      (is (nil? (bs-sec/get-credentials {:crypto-iv c
                                         :username  "hello"
                                         :password  "world"})))))

  (testing "encode - decode"
    (doseq [args [["hello" "world"]
                  [{:username "hello" :password "world"}]]
            :let [{:keys [username password
                          crypto-iv]} (apply bs-sec/encode-credentials args)]]
      (is (= "hello" username))
      (is (and (ss/not-blank? password) (not= password "world")))
      (is (ss/not-blank? crypto-iv))
      (is (= "world"
             (bs-sec/decode-password password crypto-iv)
             (bs-sec/decode-password {:password  password
                                      :crypto-iv crypto-iv})))
      (is (= ["hello" "world"]
             (bs-sec/get-credentials {:username  username
                                      :password  password
                                      :crypto-iv crypto-iv})))))

  (testing "corrupted data"
    (is (thrown? IllegalArgumentException
                 (bs-sec/decode-password "foobar" "badcrypto")))
    (is (thrown? IllegalArgumentException
                 (bs-sec/get-credentials {:username  "foo"
                                          :password  "bar"
                                          :crypto-iv "baz"})))))
