(ns lupapalvelu.printing-order.domain-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.printing-order.domain :as domain]
            [midje.sweet :refer :all]
            [sade.core :as sade])
  (:import (java.util Date)))

(def mock-vats
  [{:value 1000 :start-ts #inst "1900-01-01"}
   {:value 2550 :start-ts (Date. (+ (sade/now) (* 24 60 60 1000)))} ; tomorrow
   {:value 3100 :start-ts #inst "2004-01-01"}
   {:value 4200 :start-ts #inst "2019-01-01"}])

(facts "mocked VAT"
  ;; These should work regardless of environment
  (domain/vat-for-timestamp (sade/now) mock-vats) => 4200
  (domain/vat-for-timestamp #inst "2000-01-01" mock-vats) => 1000
  (domain/vat-for-timestamp #inst "2004-01-01" mock-vats) => 3100
  (domain/vat-for-timestamp 1724679436831 mock-vats) => 4200)

(facts "actual VAT"
  ;; These require that a valid "vats.edn" is found in the environment resources
  (domain/vat-for-timestamp #inst "2024-06-01") => 2400
  (domain/vat-for-timestamp #inst "2024-10-10") => 2550)
