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
