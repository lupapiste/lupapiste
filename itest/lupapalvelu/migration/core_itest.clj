(ns lupapalvelu.migration.core-itest
  (:require [lupapalvelu.core :refer [now]]
            [lupapalvelu.migration.core :refer :all]
            [lupapalvelu.mongo :as mongo]
            [sade.strings :as ss]
            [midje.sweet :refer :all]))

(mongo/connect!)

(defn cleanup! []
  (reset! migration-id 0)
  (reset! migrations {})
  (mongo/remove-many :migrations {}))

(background (before :facts (cleanup!)))

(def oh-noes (fn [s] (re-find #"java.lang.RuntimeException: oh noes!" s)))
(defn starts-with [prefix] (fn [s] (ss/starts-with s prefix)))

(facts "define migration"
  (defmigration a "a")
  (count @migrations) => 1
  (@migrations "a") => (contains {:id 1 :name "a"})
  (migration-history) => empty?
  (unexecuted-migrations) => (just [(contains {:id 1})]))

(fact "executing migration adds it to migration history"
  (defmigration a "a")
  (execute-migration! (@migrations "a")) => (contains {:id 1 :ok true :result "a" :time 100})
    (provided (now) => 100)
  (migration-history) => (just [(contains {:id 1 :ok true :result "a" :time 100})])
  (unexecuted-migrations) => empty?)

(fact "executing two migrations"
  (defmigration a "a")
  (defmigration b "b")
  (defmigration c "c")
  (execute-migration! (@migrations "a")) => (contains {:id 1 :ok true :result "a" :time 100}) (provided (now) => 100)
  (execute-migration! (@migrations "b")) => (contains {:id 2 :ok true :result "b" :time 101}) (provided (now) => 101)
  (migration-history) => (just [(contains {:ok true :time 100 :result "a"})
                                (contains {:ok true :time 101 :result "b"})])
  (unexecuted-migrations) => (just [(contains {:id 3})]))

(fact "executing migration that fails"
  (defmigration a (throw (RuntimeException. "oh noes!")))
  (execute-migration! (@migrations "a")) => (contains {:id 1 :ok false :error oh-noes})
  (migration-history) => (just [(contains {:ok false :error oh-noes})]))

(fact "pre-condition failure"
  (defmigration a {:pre (= 1 2)} "aa" #_(throw (Error. "never")))
  (execute-migration! (@migrations "a")) => (contains {:id 1 :ok false :error (starts-with "pre-condition failed")})
  (migration-history) => (just [(contains {:ok false :error (starts-with "pre-condition failed")})]))

(fact "post-condition failure" 
  (defmigration a {:post (= 1 2)} "a")
  (execute-migration! (@migrations "a")) => (contains {:id 1 :ok false :error (starts-with "post-condition failed")})
  (migration-history) => (just [(contains {:ok false :error (starts-with "post-condition failed")})]))

(fact "apply-when says no"
  (defmigration a {:apply-when (identity false)} (throw (Error. "never")))
  (execute-migration! (@migrations "a")) => nil?
  (migration-history) => empty?)

(fact "apply-when says yes"
  (let [v (atom true)
        f (fn [] (let [r @v] (reset! v false) r))]
    (defmigration a {:apply-when (f)} "a")
    (execute-migration! (@migrations "a")) => (contains {:id 1 :ok true :result "a"})
    (migration-history) => (just [(contains {:id 1 :ok true :result "a"})])))

(fact "apply-when says yes all the time"
  (defmigration a {:apply-when (identity true)} "a")
  (execute-migration! (@migrations "a")) => (contains {:ok false :error "migration execution did not change result of apply-when"}))
