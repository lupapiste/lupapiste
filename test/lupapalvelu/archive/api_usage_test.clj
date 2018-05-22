(ns lupapalvelu.archive.api-usage-test
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :refer [warnf]]
            [sade.env :as env]
            [lupapalvelu.archive.api-usage :refer :all]
            [lupapalvelu.integrations.jms :as jms]))

(def mongo-mock (atom []))

(background (before :facts (reset! mongo-mock [])))

(if-not (env/feature? :jms)
  (warnf "JMS not enabled for unit testing")

  (facts "Archive API usage"
    (let [onkalo-mock (jms/create-nippy-producer archive-api-usage-queue)]
      (with-redefs [lupapalvelu.mongo/insert
                    (fn [collection entry]
                      (when (= collection archive-api-usage-collection)
                        (swap! mongo-mock conj entry)))]

        (fact "consumed message is inserted into collection"
              (onkalo-mock {:organization "753-R" :timestamp 12345})
              (Thread/sleep 100)
              (->> @mongo-mock count) => 1
              (->> @mongo-mock first :timestamp) => 12345)

        (fact "multiple messages are inserted"
              (onkalo-mock {:organization "753-R" :timestamp 12345})
              (onkalo-mock {:organization "753-R" :timestamp 12346})
              (onkalo-mock {:organization "753-R" :timestamp 12347})
              (Thread/sleep 100)
              (->> @mongo-mock count) => 3
              (->> @mongo-mock last :timestamp) => 12347)

        (fact "messages that do not match schema are dropped"
              (onkalo-mock {:does :not :match :schema})
              (Thread/sleep 100)
              (->> @mongo-mock count) => 0)))))
