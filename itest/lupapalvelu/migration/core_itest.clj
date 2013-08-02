(ns lupapalvelu.migration.core-itest
  (:require [lupapalvelu.core :refer [now]]
            [lupapalvelu.migration.core :refer :all]
            [lupapalvelu.mongo :as mongo]
            [sade.strings :as ss]
            [midje.sweet :refer :all]))

(mongo/connect!)
(reset! migration-id 0)
(reset! migrations {})

(defn clean-history! []
  (mongo/remove-many :migrations {})
  (assert (zero? (mongo/count :migrations))))

(background (before :facts (clean-history!)))

(defmigration a
  "a")

(defmigration b
  "b")

(defmigration c
  (throw (RuntimeException. "oh noes!")))

(defmigration d {:pre (= 1 2)}
  (throw (Exception. "never")))

(defmigration e {:post (= 1 2)}
  "e")

(def oh-noes (fn [s] (re-find #"java.lang.RuntimeException: oh noes!" s)))

(defn starts-with [prefix]
  (fn [s] (ss/starts-with s prefix)))

(facts "setup"
  (count @migrations) => 5
  (@migrations "a") => (contains {:id 1 :name "a"})
  (@migrations "b") => (contains {:id 2 :name "b"})
  (@migrations "c") => (contains {:id 3 :name "c"})
  (@migrations "d") => (contains {:id 4 :name "d"})
  (@migrations "e") => (contains {:id 5 :name "e"})
  (migration-history) => [])

(facts "execute-migration! and migration-history"
  (unexecuted-migrations) => (just [(contains {:id 1}) (contains {:id 2}) (contains {:id 3}) (contains {:id 4}) (contains {:id 5})])
  
  (execute-migration! (@migrations "a")) => (contains {:id 1 :ok true :result "a" :time 100})
    (provided (now) => 100)
  (migration-history) => (just [(contains {:id 1 :ok true :result "a" :time 100})])
  (unexecuted-migrations) => (just [(contains {:id 2}) (contains {:id 3}) (contains {:id 4}) (contains {:id 5})])
  
  (execute-migration! (@migrations "b")) => (contains {:id 2 :ok true :result "b" :time 101})
    (provided (now) => 101)
  (migration-history) => (just [(contains {:ok true :time 100 :result "a"})
                                (contains {:ok true :time 101 :result "b"})])
  (unexecuted-migrations) => (just [(contains {:id 3}) (contains {:id 4}) (contains {:id 5})])
  
  (execute-migration! (@migrations "c")) => (contains {:id 3 :ok false :ex oh-noes})
    (provided (now) => 102)
  (migration-history) => (just [(contains {:ok true  :time 100 :result "a" })
                                (contains {:ok true  :time 101 :result "b"})
                                (contains {:ok false :time 102 :ex oh-noes})])
  (unexecuted-migrations) => (just [(contains {:id 3}) (contains {:id 4}) (contains {:id 5})])
  
  (execute-migration! (@migrations "d")) => (contains {:id 4 :ok false :ex (starts-with "pre-condition failed")})
    (provided (now) => 102)
  (last (migration-history)) => (contains {:ok false :time 102 :ex (starts-with "pre-condition failed")})
  (unexecuted-migrations) => (just [(contains {:id 3}) (contains {:id 4}) (contains {:id 5})])
  
  (execute-migration! (@migrations "e")) => (contains {:id 5 :ok false :ex (starts-with "post-condition failed")})
    (provided (now) => 103)
  (last (migration-history)) => (contains {:ok false :time 103 :ex (starts-with "post-condition failed")})
  (unexecuted-migrations) => (just [(contains {:id 3}) (contains {:id 4}) (contains {:id 5})]))
