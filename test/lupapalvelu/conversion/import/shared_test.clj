(ns lupapalvelu.conversion.import.shared-test
  (:require [lupapalvelu.conversion.import.shared :as shared]
            [clojure.test :refer :all]))

(deftest append-text-if-needed-test
  (testing "No append"
    (doseq [[old s] [[nil nil] ["" ""] [" ABCDEFG" " def "]
                     ["foobar" " "] ["foobar" nil]]]
      (is (nil? (shared/append-text-if-needed old s)))))
  (testing "Append"
    (is (= "Hello\n\nworld"
           (shared/append-text-if-needed " Hello " " world ")))
    (is (= "Hello*world"
           (shared/append-text-if-needed " Hello " " world " "*")))))
