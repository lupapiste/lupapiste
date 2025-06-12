(ns lupapalvelu.visma-sign.process-test
  (:require [lupapalvelu.crypto :as c]
            [lupapalvelu.json :as json]
            [lupapalvelu.visma-sign.process :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

;
; Setup:
;

(fact "we know how to JSON-encode byte arrays"
  (json/encode {:foo (byte-array (map byte (range 3)))})
  => "{\"foo\":[0,1,2]}")

;
; Utils:
;

(fact "str->bytes and bytes->str"
  (-> "Hullo" c/str->bytes c/bytes->str) => "Hullo")

(testable-privates lupapalvelu.visma-sign.process validate-process-update!)

(defn exception-with-type? [error-type e]
  (and (instance? ExceptionInfo e)
       (= error-type (-> ^ExceptionInfo e .getData :error))))

(def not-found? (partial exception-with-type? :not-found))
(def bad-request? (partial exception-with-type? :bad-request))

(facts process-update!
  (validate-process-update! {:status "created"} :new) => truthy
  (validate-process-update! {:status "created"} :document) => (throws bad-request?))
