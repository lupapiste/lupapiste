(ns lupapalvelu.jms-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.integrations.jms :as jms]
            [sade.core :refer :all]
            [sade.env :as env]
            [lupapalvelu.mongo :as mongo]))
(def ts (now))
(def test-queue (str "testijono_" ts))
(def test-db (str "jms_itest_" ts))
(when (env/feature? :jms)
  (mongo/connect!)
  (facts "test_db_name is respected"
    (mongo/drop-collection :testi)
    (mongo/with-db test-db
      (mongo/drop-collection :testi))
    (let [consumer (jms/create-consumer
                     test-queue
                     (fn [data]; 'with-db' is called in jms.clj MessageListener
                       (mongo/insert
                         :testi
                         {:id (mongo/create-id) :data data})))]
      (fact "Produce to normal db and custom db"
        (jms/produce-with-context test-queue "default db") => nil
        (mongo/with-db test-db
          (jms/produce-with-context test-queue "with db")) => nil)
      (Thread/sleep 100)
      (fact "From normal DB"
        (mongo/select :testi) => (just (contains {:data "default db"})))
      (fact "From test-db DB"
        (mongo/with-db test-db
          (mongo/select :testi) => (just (contains {:data "with db"}))))
      (.close consumer))))
