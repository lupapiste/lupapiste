(ns lupapalvelu.archive.api-usage-itest
  (:require [lupapalvelu.archive.api-usage :refer [archive-api-usage-collection]]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer [with-test-system]]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]))

(set! *warn-on-reflection* true)

(def mongo-mock (atom []))

(defn test-message [& [fileId]]
  {:organization "753-R"
   :fileId       (or fileId "abcde1234")
   :apiUser      "docstore"
   :externalId   "foo123"
   :timestamp    12345
   :metadata     {:myyntipalvelu true}})

(defn wait-for-message-delivery [expected-count tries]
  (when (and (< (count @mongo-mock) expected-count)
             (< tries 10))
    (Thread/sleep 1000)
    (wait-for-message-delivery expected-count (inc tries))))

(with-test-system (-> {}
                      (test-system/with-pubsub-publisher :archive/api-usage)
                      (test-system/with-pubsub-subscriber :archive/api-usage))
  (let [onkalo-mock (test-system/get-publisher :archive/api-usage)]
    (with-state-changes [(before :facts (reset! mongo-mock []))]
      (facts "Archive API usage"
        (with-redefs [mongo/insert
                      (fn [collection entry]
                        (when (= collection archive-api-usage-collection)
                          (swap! mongo-mock conj entry)))]

          (fact "consumed message is inserted into collection"
            (onkalo-mock (test-message))
            (wait-for-message-delivery 1 0)
            (->> @mongo-mock count) => 1
            (->> @mongo-mock first :timestamp) => 12345)

          (fact "multiple messages are inserted"
            (onkalo-mock (test-message))
            (onkalo-mock (test-message))
            (onkalo-mock (test-message "lastFile"))
            (wait-for-message-delivery 1 3)
            ;; It seems that in the current pubsub implementation (or maybe just in this itest) order of messages is not
            ;; quaranteed. In this case, it's ok that the order of the messages may vary. Hence, we just check that in
            ;; the end, the all messages have been inserted in Mongodb.
            (->> @mongo-mock count) => 3
            (map :fileId @mongo-mock) => (just "abcde1234" "abcde1234" "lastFile" :in-any-order))

          (fact "messages that do not match schema are dropped"
            (onkalo-mock {:does :not :match :schema})
            (Thread/sleep 3000)
            (->> @mongo-mock count) => 0))))))
