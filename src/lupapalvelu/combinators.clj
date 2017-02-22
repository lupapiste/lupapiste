(ns lupapalvelu.combinators
  (:require [sade.core :refer [ok fail? unauthorized]]))

(defn- validator-and2 [validator1 validator2]
  (fn [command]
    (let [result1 (validator1 command)]
      (if (fail? result1)
        result1
        (validator2 command)))))

(defn validator-and
  "Combines validator functions used in commands' :pre-check vectors. The
   combined validator returns the first failing result or the result of the
   last validator in case none fail.

   Suppose a validator is defined as

     (validator-and val1 val2 val3 val4)

   and command passes validators val1 and val3, then the combined validator
   returns the fail given by val2."
  [& validators]
  (reduce validator-and2 (constantly nil) validators))

(defn- validator-or2 [validator1 validator2]
  (fn [command]
    (let [res1 (validator1 command)]
      (if (fail? res1)
        (validator2 command)
        res1))))

(defn validator-or [& validators]
  "Combines validator functions used in commands' :pre-check vectors. The
   combined validator returns the first non-failing result or the result of the
   last validator in case all fail.

   Suppose a validator is defined as

     (validator-or val1 val2 val3)

   and command passes validators val2 and val3, then the combined validator
   returns the pass given by val2."
  (reduce validator-or2 (constantly unauthorized) validators))

(defn pred->validator
  "Turns a predicate function into a validator for commands' :pre-check vectors"
  [pred]
  (fn [x]
    (if (pred x)
      nil
      unauthorized)))
