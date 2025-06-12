(ns sade.async-test
  (:require [midje.sweet :refer :all]
            [sade.async :refer [future*]])
  (:import [org.apache.commons.io.output NullWriter]))

(set! *warn-on-reflection* true)

(facts future*
  (binding [*out* (NullWriter.)
            *err* (NullWriter.)]
    (deref (future* (throw (Exception. "bang!")))))
  => (throws java.util.concurrent.ExecutionException "java.lang.Exception: bang!"))
