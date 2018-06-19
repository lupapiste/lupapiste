(ns lupapalvelu.archive.api-usage-itest
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :refer [warnf]]
            [sade.env :as env]
            [lupapalvelu.archive.api-usage :refer :all]
            [lupapalvelu.integrations.jms :as jms]))

(def mongo-mock (atom []))

(defn test-message [& [fileId]]
  (pr-str {:organization "753-R"
           :fileId (or fileId "abcde1234")
           :apiUser "docstore"
           :externalId "foo123"
           :timestamp 12345
           :metadata {:myyntipalvelu true}}))

(background (before :facts (reset! mongo-mock [])))

(if-not (env/feature? :jms)
  (warnf "JMS not enabled for unit testing")

  (facts "Archive API usage"
    (let [onkalo-mock (jms/create-producer archive-api-usage-queue)]
      (with-redefs [lupapalvelu.mongo/insert
                    (fn [collection entry]
                      (when (= collection archive-api-usage-collection)
                        (swap! mongo-mock conj entry)))]

        (fact "consumed message is inserted into collection"
              (onkalo-mock (test-message))
              (Thread/sleep 500)
              (->> @mongo-mock count) => 1
              (->> @mongo-mock first :timestamp) => 12345)

        (fact "multiple messages are inserted"
              (onkalo-mock (test-message))
              (onkalo-mock (test-message))
              (onkalo-mock (test-message "lastFile"))
              (Thread/sleep 500)
              (->> @mongo-mock count) => 3
              (->> @mongo-mock last :fileId) => "lastFile")

        (fact "messages that do not match schema are dropped"
              (onkalo-mock (pr-str {:does :not :match :schema}))
              (Thread/sleep 500)
              (->> @mongo-mock count) => 0)))))
