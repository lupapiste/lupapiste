(ns lupapalvelu.domain-attachment-test
  (:require [clojure.test :refer [deftest is]]
            [lupapalvelu.domain-attachment :as domain-att]))

(deftest attachment-map-test
  (let [app {:attachments [{:id 1}
                           {:id 2}
                           {:id 3}
                           {:id 4}]}]
    (is (nil? (domain-att/get-attachment-by-id nil nil)))
    (is (nil? (domain-att/get-attachment-by-id {} nil)))
    (is (nil? (domain-att/get-attachment-by-id nil 1)))
    (is (= {:id 1} (-> app
                       (domain-att/get-attachment-by-id 1))))
    (is (= {:id 1} (-> app
                       domain-att/add-attachment-map
                       (domain-att/get-attachment-by-id 1))))
    (is (= {1 {:id 1}
            2 {:id 2}
            3 {:id 3}
            4 {:id 4}} (-> app domain-att/add-attachment-map :attachments-by-id force)))))
