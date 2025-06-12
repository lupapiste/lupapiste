(ns lupapalvelu.notice-utils-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.notice-utils :as notice-utils]))


(deftest notice-utils-demolition-document-test
  (testing "demolition-notice? flag"
    (is (= true
           (notice-utils/demolition-notice-document? {:schema {:info {:demolition-notice? true}}})))
    (is (= false
           (notice-utils/demolition-notice-document? {:schema {:info {:demolition-notice false}}})))
    (is (= false
           (notice-utils/demolition-notice-document? {:schema {:info {}}})))))
