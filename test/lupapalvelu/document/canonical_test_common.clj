(ns lupapalvelu.document.canonical-test-common
  (:require [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.model :refer [validate get-document-schema]]
            [midje.sweet :refer :all]))

;;
;; Document validator predicate
;;

(defn validate-against-current-schema
  "Validates document against the latest schema and returns list of errors."
  [document]
  (validate document (get-document-schema document)))

(defn valid-against-current-schema? [document]
  (or (fact (validate-against-current-schema document) => empty?) true))

(defn validate-all-documents [documents]
  (fact "Meta test: all documents in fixture are valid" documents => (has every? valid-against-current-schema?)))

;; Fixture

(def statements [{:given 1379423133068
                  :id "52385377da063788effc1e93"
                  :person {:text "Paloviranomainen"
                           :name "Sonja Sibbo"
                           :email "sonja.sibbo@sipoo.fi"
                           :id "516560d6c2e6f603beb85147"}
                  :requested 1379423095616
                  :status "yes"
                  :text "Lausunto liitteen\u00e4."}])
