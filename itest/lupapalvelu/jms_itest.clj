(ns lupapalvelu.jms-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.integrations.jms :as jms]
            [sade.core :refer :all]
            [sade.env :as env]
            [lupapalvelu.mongo :as mongo]))
(def ts (now))
(def test-queue (str "testijono_" ts))
(def test-db (str "test_jms_itest_" ts))
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
                         {:id (mongo/create-id) :data data})))
          normal-producer (jms/create-producer test-queue)]
      (fact "Produce with context"
        (jms/produce-with-context test-queue "default db") => nil
        (mongo/with-db test-db
          (jms/produce-with-context test-queue "with db")) => nil)
      (fact "Produce with normal producer"
        (normal-producer "normal producer default db") => nil
        (mongo/with-db test-db
          (normal-producer "normal producer with-db")) => nil)
      (Thread/sleep 200)
      (fact "From normal DB"
        (mongo/select :testi) => (just (contains {:data "default db"})
                                       (contains {:data "normal producer default db"})
                                       :in-any-order))
      (fact "From test-db DB"
        (mongo/with-db test-db
          (mongo/select :testi) => (just (contains {:data "with db"})
                                         (contains {:data "normal producer with-db"})
                                         :in-any-order)))
      (.close consumer))))
