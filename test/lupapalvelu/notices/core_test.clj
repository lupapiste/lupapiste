(ns lupapalvelu.notices.core-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.document.canonical-common :as canonical-common]
            [lupapalvelu.notices.core :as notices]
            [lupapalvelu.notices.util :as notices-util]
            [sade.env :as env]))

(deftest notice?-test
  (is (true? (notices-util/notice? {:primaryOperation {:name "purkamisilmoitus"}})))
  (is (false? (notices-util/notice? {:primaryOperation {:name "purkaminen"}}))))


(def parse-resolution #'notices/parse-resolution)

(deftest parse-resolution-test
  (let [ts       12345678
        u        {:firstName "Noomi"     :lastName "Noter"
                  :role      "authority" :id       "noomid"
                  :username  "noomi@example.com"}
        make-cmd (fn [& {:as data}]
                   {:created ts :user u :data data})]
    (testing "Draft"
      (is (= {:modified ts :user u}
             (parse-resolution (make-cmd))))
      (is (= {:modified ts :user u
              :state    "approved"
              :text     [{:i "hello\n"} {:i " world"}]}
             (parse-resolution (make-cmd :state "approved"
                                         :text [{:insert " hello\n"}
                                                {:insert " world\n"}])
                               false)))
      (is (thrown-with-msg? Exception #"schema"
                            (parse-resolution (make-cmd :state "bad")))))
    (testing "Published"
      (is (thrown-with-msg? Exception #"schema"
                            (parse-resolution (make-cmd) true)))
      (is (= {:published ts :user u
              :state     "approved"
              :text      [{:i "hello\n"} {:i " world"}]}
             (parse-resolution (make-cmd :state "approved"
                                         :text [{:insert " hello\n"}
                                                {:insert " world\n"}])
                               true))))))

(deftest kuntagml-state-mapping-test
  (testing "Ready state should not exist if purkamisilmoitus flag is not set"
    (env/with-feature-value :purkamisilmoitus false
      (is (nil? (canonical-common/application-state-to-krysp-state :ready)))
      (is (nil? ((canonical-common/application-state-to-krysp-state) :ready)))))
  (testing "Ready state should exist if purkamisilmoitus flag is set"
    (env/with-feature-value :purkamisilmoitus true
      (is (= (canonical-common/application-state-to-krysp-state :ready) "päätös toimitettu"))
      (is (= ((canonical-common/application-state-to-krysp-state) :ready) "päätös toimitettu")))))

(def state-timestamp-fn #'canonical-common/state-timestamp-fn)

(deftest kuntagml-state-timestamps-test
  (testing "Ready state should not exist if purkamisilmoitus flag is not set"
    (env/with-feature-value :purkamisilmoitus false
      (is (nil? (state-timestamp-fn :ready)))
      (is (nil? ((state-timestamp-fn) :ready)))))
  (testing "Ready state should exist if purkamisilmoitus flag is set"
    (env/with-feature-value :purkamisilmoitus true
      (is (= (state-timestamp-fn :ready) :submitted))
      (is (= ((state-timestamp-fn) :ready) :submitted)))))
