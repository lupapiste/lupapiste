(ns lupapalvelu.onnistuu.process-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [cheshire.core :as json]
            [lupapalvelu.onnistuu.crypt :as c]
            [lupapalvelu.onnistuu.process :refer :all]))

;
; Setup:
;

(fact "cheshire knows how to encode byte arrays"
  (json/encode {:foo (byte-array (map byte (range 3)))})
  => "{\"foo\":[0,1,2]}")

;
; Utils:
;

(testable-privates lupapalvelu.onnistuu.process str->bytes bytes->str)

(fact "str->bytes and bytes->str"
  (-> "Hullo" str->bytes bytes->str) => "Hullo")

(testable-privates lupapalvelu.onnistuu.process validate-process-update!)

(defn exception-with-type? [error-type e]
  (and (instance? clojure.lang.ExceptionInfo e)
       (= error-type (-> e .getData :object :error))))

(def not-found? (partial exception-with-type? :not-found))
(def bad-request? (partial exception-with-type? :bad-request))

(facts process-update!
  (validate-process-update! nil ..irrelevant..) => (throws not-found?)
  (validate-process-update! {:status "created"} :start) => truthy
  (validate-process-update! {:status "created"} :document) => (throws bad-request?))

;
; Jump data:
;

(testable-privates lupapalvelu.onnistuu.process jump-data)

(facts jump-data
  (let [crypto-iv  (byte-array (map byte (range 32)))
        crypto-key (-> (byte-array (map byte (range 32)))
                       c/base64-encode
                       bytes->str)]
    (jump-data {:process       {:stamp "process-stamp"
                                :company {:y "company-y"}}
               :success-url   "success-url"
               :document-url  "document-url"
               :crypto-iv     crypto-iv
               :crypto-key    crypto-key
               :customer-id   "customer-id"
               :post-to       "target-url"})
    => (contains {:data       string?
                  :iv         string?
                  :customer   "customer-id"
                  :post-to    "target-url"})))

