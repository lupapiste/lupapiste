(ns lupapalvelu.attachment.integrations-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.integrations-api]))

(deftest transmittable-test
  (let [conversation {:type     {:type-group "muut" :type-id "keskustelu"}
                      :versions [{:fileId "123"}]}]
    (testing "Target is a conversation - KRYSP"
      (is (true? (att/transmittable-to-krysp? conversation))))))
