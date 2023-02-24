(ns lupapalvelu.archive.api-usage-itest
  (:require [artemis-server]
            [lupapalvelu.archive.api-usage :refer :all]
            [lupapalvelu.integrations.message-queue :as mq]
            [lupapalvelu.integrations.pubsub :as pubsub]
            [midje.sweet :refer :all]
            [mount.core :as mount]
            [sade.env :as env]
            [taoensso.timbre :refer [warnf]]))

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

(if-not (= (env/value :integration-message-queue) "pubsub")
  (warnf "Pub/Sub not enabled for unit testing")

  (with-state-changes [(before :facts (do
                                       (reset! mongo-mock [])
                                       (mount/start #'pubsub/client)
                                       (mount/start #'api-usage-pubsub-subscriber)))
                       (after :facts (mount/stop #'api-usage-pubsub-subscriber))]
    (facts "Archive API usage"
      (let [onkalo-mock #(mq/publish archive-api-usage-queue %)]
        (with-redefs [lupapalvelu.mongo/insert
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
