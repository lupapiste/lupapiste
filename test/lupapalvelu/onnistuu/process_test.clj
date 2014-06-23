(ns lupapalvelu.onnistuu.process-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [clojure.walk :as walk]
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

(fact "str->bytes and bytes->str"
  (-> "Hullo" c/str->bytes c/bytes->str) => "Hullo")

(testable-privates lupapalvelu.onnistuu.process validate-process-update!)

(defn exception-with-type? [error-type e]
  (and (instance? clojure.lang.ExceptionInfo e)
       (= error-type (-> e .getData :object :error))))

(def not-found? (partial exception-with-type? :not-found))
(def bad-request? (partial exception-with-type? :bad-request))

(facts process-update!
  (validate-process-update! {:status "created"} :started) => truthy
  (validate-process-update! {:status "created"} :document) => (throws bad-request?))

;
; Init:
;

(defn ubyte [v] (byte (if (>= v 0x80) (- v 0x100) v)))

(facts init-sign-process
  (let [ts         #inst "2014-06-20T08:52:45.785-00:00"
        crypto-key (->> (range 32) (map ubyte) (byte-array) (c/base64-encode) (c/bytes->str))]
    (let [resp (init-sign-process ts crypto-key "success-url" "document-url" ..irrelevant.. "company-y" ..irrelevant.. ..irrelevant.. ..irrelevant.. ..irrelevant..)
          {:keys [process-id data iv]} resp]
      process-id => #"\S+"
      data       => #"\S+"
      iv         => #"\S+"
      (let [crypto-iv (-> iv (c/str->bytes) (c/base64-decode))
            form-data (->> data
                           (c/str->bytes)
                           (c/base64-decode)
                           (c/decrypt (-> crypto-key (c/str->bytes) (c/base64-decode)) crypto-iv)
                           (c/bytes->str)
                           (json/decode))
            {:strs [stamp return_success document requirements]} form-data]
        stamp          => #"\S+"
        return_success => #"success-url/\S+"
        document       => #"document-url/\S+"
        requirements   => [{"type" "company" "identifier" "company-y"}]))))

