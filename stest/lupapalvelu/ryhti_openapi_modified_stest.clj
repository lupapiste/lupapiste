(ns lupapalvelu.ryhti-openapi-modified-stest
  "Stest for detecting when the Ryhti (test or production) API changes."
  (:require [clojure.test :refer [deftest is]]
            [pandect.core :as pandect]
            [lupapalvelu.ryhti.api.mappings :as mappings]
            [sade.http :as http]))

(deftest verify-current-ryhti-api-hash
  ;; See https://liityntakatalogi.suomi.fi/dataset/ryhti-syke-service
  (let [url                   mappings/CURRENT-API
        {:keys [status body]} (http/hato-get url {:as :text})]
    (is (= 200 status))
    (is (string? body))
    ;; Check if the JSON content matches what we expect
    ;; NOTE: If this fails -> The production API in Ryhti has changed:
    ;; 1. Run `ryhti-openapi-parser/write-schemas` to recreate our Malli schemas
    ;; 2. Verify the changes in the schemas, update mappings, run tests etc. as needed
    ;; 3. Update the hash to match the current value
    (is (= "d823f2b2d4361da77eec16e64452c44b78a08af6da345fe466ce45164980b0c2"
           (pandect/sha256 body)))))
