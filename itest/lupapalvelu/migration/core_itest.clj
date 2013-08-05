(ns lupapalvelu.migration.core-itest
  (:require [lupapalvelu.migration.core :refer :all]
            [lupapalvelu.mongo :as mongo]
            [sade.strings :as ss]
            [midje.sweet :refer :all]))

(mongo/connect!)

(defn cleanup! []
  (reset! migrations {})
  (reset! migration-order 0)
  (mongo/remove-many :migrations {}))

(background (before :facts (cleanup!)))

(def oh-noes (fn [s] (re-find #"java.lang.RuntimeException: oh noes!" s)))
(defn starts-with [prefix] (fn [s] (ss/starts-with s prefix)))

(facts "define migration"
  (defmigration a "a")
  (count @migrations) => 1
  (@migrations "a") => (contains {:name "a"})
  (migration-history) => empty?
  (unexecuted-migrations) => (just [(contains {:name "a"})]))

(facts "migration have order"
  (defmigration a "a")
  (defmigration b "b")
  (defmigration c "c")
  (count @migrations) => 3
  (unexecuted-migrations) => (just [(contains {:name "a" :order 1})
                                    (contains {:name "b" :order 2})
                                    (contains {:name "c" :order 3})]))

(fact "executing migration adds it to migration history"
  (defmigration a "A")
  (execute-migration! (@migrations "a")) => (contains {:name "a" :ok true :result "A" :time 100}) (provided (now) => 100)
  (migration-history) => (just [(contains {:name "a" :ok true :result "A" :time 100})])
  (unexecuted-migrations) => empty?)

(fact "migration may return nil"
  (defmigration a nil)
  (execute-migration! (@migrations "a")) => (contains {:name "a" :ok true :result nil})
  (migration-history) => (just [(contains {:name "a" :ok true :result nil})]))

(fact "executing two migrations"
  (defmigration a "a")
  (defmigration b "b")
  (defmigration c "c")
  (execute-migration! (@migrations "a")) => (contains {:name "a" :ok true :result "a"})
  (execute-migration! (@migrations "b")) => (contains {:name "b" :ok true :result "b"})
  (migration-history) => (just [(contains {:name "a" :ok true :result "a"})
                                (contains {:name "b" :ok true :result "b"})])
  (unexecuted-migrations) => (just [(contains {:name "c"})]))

(fact "executing migration that fails"
  (defmigration a (throw (RuntimeException. "oh noes!")))
  (execute-migration! (@migrations "a")) => (contains {:name "a" :ok false :error oh-noes})
  (migration-history) => (just [(contains {:name "a" :ok false :error oh-noes})]))

(fact "pre-condition failure"
  (defmigration a {:pre (= 1 2)} (throw (Error. "never")))
  (execute-migration! (@migrations "a")) => (contains {:name "a" :ok false :error (starts-with "pre-condition failed")})
  (migration-history) => (just [(contains {:name "a" :ok false :error (starts-with "pre-condition failed")})]))

(fact "post-condition failure" 
  (defmigration a {:post (= 1 2)} "a")
  (execute-migration! (@migrations "a")) => (contains {:name "a" :ok false :error (starts-with "post-condition failed")})
  (migration-history) => (just [(contains {:name "a" :ok false :error (starts-with "post-condition failed")})]))

(fact "apply-when says no"
  (defmigration a {:apply-when (identity false)} (throw (Error. "never")))
  (execute-migration! (@migrations "a")) => (contains {:name "a" :ok true :result (starts-with "execution not needed")}))

(fact "apply-when says yes"
  (let [v (atom true)
        f (fn [] (let [r @v] (reset! v false) r))]
    (defmigration a {:apply-when (f)} "a")
    (execute-migration! (@migrations "a")) => (contains {:name "a" :ok true :result "a"})
    (migration-history) => (just [(contains {:name "a" :ok true :result "a"})])))

(fact "apply-when says yes all the time"
  (defmigration a {:apply-when (identity true)} "a")
  (execute-migration! (@migrations "a")) => (contains {:name "a" :ok false :error "migration execution did not change result of apply-when"}))
