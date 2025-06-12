(ns lupapalvelu.core-itest
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [matcher-combinators.test :refer [match?]]))

(use-fixtures :each (fn [run-tests]
                      (with-test-system (-> {:fixture "minimal"}
                                            test-system/with-remote-components)
                        (run-tests))))

(deftest action-core-test
  (testing "unknown command"
    (is (= {:ok   false
            :text "error.invalid-command"}
           (query mikko :actions))))
  (testing "list actions"
    (is (match? {:ok      true
                 :actions map?}
                (query mikko :allowed-actions))))
  (testing "check individual action"
    (is (= {:ok true}
           (query mikko :allowed-action :action "sent-emails"
                  :args {:reset true})))))
