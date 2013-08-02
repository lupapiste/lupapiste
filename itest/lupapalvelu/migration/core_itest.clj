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

(defmigration a "a")
(defmigration b "b")
(defmigration c (throw (RuntimeException. "oh noes!")))

(def starts-with-oh-noes (fn [v] (ss/starts-with v "java.lang.RuntimeException: oh noes!")))

(facts "setup"
  (count @migrations) => 3
  (@migrations "a") => (contains {:id 1 :name "a"})
  (@migrations "b") => (contains {:id 2 :name "b"})
  (@migrations "c") => (contains {:id 3 :name "c"})
  (migration-history) => [])

(facts "execute-migration! and migration-history"
  (unexecuted-migrations) => (just [(contains {:id 1}) (contains {:id 2}) (contains {:id 3})])
  
  (execute-migration! (@migrations "a")) => (contains {:id 1 :ok true :result "a"})
    (provided (now) => 100)
  (migration-history) => (just [(contains {:ok true :time 100 :result "a"})])
  (unexecuted-migrations) => (just [(contains {:id 2}) (contains {:id 3})])
  
  (execute-migration! (@migrations "b")) => (contains {:id 2 :ok true :result "b"})
    (provided (now) => 101)
  (migration-history) => (just [(contains {:ok true :time 100 :result "a"})
                                (contains {:ok true :time 101 :result "b"})])
  (unexecuted-migrations) => (just [(contains {:id 3})])
  
  (execute-migration! (@migrations "c")) => (contains {:id 3 :ok false :ex starts-with-oh-noes})
    (provided (now) => 102)
  (migration-history) => (just [(contains {:ok true  :time 100 :result "a" })
                                (contains {:ok true  :time 101 :result "b"})
                                (contains {:ok false :time 102 :ex starts-with-oh-noes})])
  (unexecuted-migrations) => (just [(contains {:id 3})]))
