(ns lupapalvelu.migration.core-itest
  (:require [lupapalvelu.migration.core :refer :all]
            [lupapalvelu.mongo :as mongo]
            [sade.strings :as ss]
            [sade.core :refer [now]]
            [lupapalvelu.fixture.core :as fixture]
            [midje.sweet :refer :all]
            [mount.core :as mount]))

(def db-name (str "test_migration-core-itest_" (now)))

(mount/start #'mongo/connection)
(mongo/with-db db-name (fixture/apply-fixture "minimal"))

(defn cleanup! []
  (reset! migrations {})
  (reset! migration-order 0)
  (mongo/with-db db-name 
    (mongo/remove-many :migrations {})))

(background (before :facts (cleanup!)))

(def oh-noes (fn [s] (re-find #"java.lang.RuntimeException: oh noes!" s)))
(defn starts-with [prefix] (fn [s] (ss/starts-with s prefix)))

(facts "define migration"
  (mongo/with-db db-name
    (defmigration a "a")
    (count @migrations) => 1
    (@migrations "a") => (contains {:name "a"})
    (migration-history) => empty?
    (unexecuted-migrations) => (just [(contains {:name "a"})])))

(facts "migration have order"
  (mongo/with-db db-name
    (defmigration a "a")
    (defmigration b "b")
    (defmigration c "c")
    (count @migrations) => 3
    (unexecuted-migrations) => (just [(contains {:name "a" :order 1})
                                      (contains {:name "b" :order 2})
                                      (contains {:name "c" :order 3})])))

(fact "executing migration adds it to migration history"
  (mongo/with-db db-name
    (defmigration a "A")
    (execute-migration! "a") => (contains {:name "a" :ok true :result "A" :time 100}) (provided (now) => 100)
    (migration-history) => (just [(contains {:name "a" :ok true :result "A" :time 100})])
    (unexecuted-migrations) => empty?))

(fact "migration may return nil"
  (mongo/with-db db-name
    (defmigration a nil)
    (execute-migration! "a") => (contains {:name "a" :ok true :result nil})
    (migration-history) => (just [(contains {:name "a" :ok true :result nil})])))

(fact "executing two migrations"
  (mongo/with-db db-name
    (defmigration a "a")
    (defmigration b "b")
    (defmigration c "c")
    (execute-migration! "a") => (contains {:name "a" :ok true :result "a"})
    (execute-migration! "b") => (contains {:name "b" :ok true :result "b"})
    (migration-history) => (just [(contains {:name "a" :ok true :result "a"})
                                  (contains {:name "b" :ok true :result "b"})])
    (unexecuted-migrations) => (just [(contains {:name "c"})])))

(fact "executing migration that fails"
  (mongo/with-db db-name
    (defmigration a (throw (RuntimeException. "oh noes!")))
    (execute-migration! "a") => (contains {:name "a" :ok false :error oh-noes})
    (migration-history) => (just [(contains {:name "a" :ok false :error oh-noes})])))

(facts "single map is not considered as an options map"
  (mongo/with-db db-name
    (defmigration a {:foo "bar"})
    (execute-migration! "a") => (contains {:result {:foo "bar"}})))

(facts "first map is considered as an options map"
  (mongo/with-db db-name
    (defmigration a {} {:foo "bar"})
    (execute-migration! "a") => (contains {:result {:foo "bar"}})))

(fact "pre-condition failure"
  (mongo/with-db db-name
    (defmigration a {:pre [(= 1 2)]} (throw (Error. "never")))
    (execute-migration! "a") => (contains {:name "a" :ok false :error (starts-with "pre-condition failed")})
    (migration-history) => (just [(contains {:name "a" :ok false :error (starts-with "pre-condition failed")})])))

(fact "post-condition failure"
  (mongo/with-db db-name
    (defmigration a {:post [(= 1 2)]} "a")
    (execute-migration! "a") => (contains {:name "a" :ok false :error (starts-with "post-condition failed")})
    (migration-history)) => (just [(contains {:name "a" :ok false :error (starts-with "post-condition failed")})]))

(fact "apply-when says no"
  (mongo/with-db db-name
    (defmigration a {:apply-when (identity false)} (throw (Error. "never")))
    (execute-migration! "a") => (contains {:name "a" :ok true :result (starts-with "execution not needed")})))

(fact "apply-when says yes"
  (mongo/with-db db-name
    (let [v (atom true)
          f (fn [] (let [r @v] (reset! v false) r))]
      (defmigration a {:apply-when (f)} "a")
      (execute-migration! "a") => (contains {:name "a" :ok true :result "a"}))))

(fact "apply-when says yes all the time"
  (mongo/with-db db-name
    (defmigration a {:apply-when (identity true)} "a")
    (execute-migration! "a") => (contains {:name "a" :ok false :error "migration execution did not change result of apply-when"})))

; leave everything is pristine condition
(cleanup!)
