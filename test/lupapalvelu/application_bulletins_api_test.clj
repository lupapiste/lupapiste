(ns lupapalvelu.application-bulletins-api-test
  (:require [clojure.test :refer [deftest is]]
            [lupapalvelu.application-bulletins-api :as api]))

(deftest split-results-test
  (is (= {:left -10
          :data []}
         (api/split-results 0 [])))
  (let [;; "0" - "101"
        results (mapv str (range 102))]
    ;; Our UI considers `page 1` to be the first page, but some tests
    ;; also use `page 0`..
    (is (= {:left 92
            :data ["0" "1" "2" "3" "4" "5" "6" "7" "8" "9"]}
           (api/split-results 0 results)))
    (is (= {:left 92
            :data ["0" "1" "2" "3" "4" "5" "6" "7" "8" "9"]}
           (api/split-results 1 results)))
    (is (= {:left 82
            :data ["10" "11" "12" "13" "14" "15" "16" "17" "18" "19"]}
           (api/split-results 2 results)))
    (is (= {:left 2
            :data ["90" "91" "92" "93" "94" "95" "96" "97" "98" "99"]}
           (api/split-results 10 results)))
    (is (= {:left -8
            :data ["100" "101"]}
           (api/split-results 11 results)))
    (is (= {:left -10
            :data []}
           (api/split-results 12 results)))))
