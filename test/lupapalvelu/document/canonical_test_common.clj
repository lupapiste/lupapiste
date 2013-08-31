(ns lupapalvelu.document.canonical-test-common
  (:require [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.model :refer [validate]]
            [midje.sweet :refer :all]))

;;
;; Document validator predicate
;;

(defn validate-against-current-schema
  "Validates document against the latest schema and returns list of errors."
  [{{{schema-name :name} :info} :schema document-data :data :as document}]
  (let [latest-schema (schemas/get-schema (schemas/get-latest-schema-version) schema-name)
        pimped-doc    (assoc document :schema latest-schema)]
    (validate pimped-doc)))

(defn valid-against-current-schema? [document]
  (or (fact (validate-against-current-schema document) => '()) true))

(defn validate-all-documents [documents]
  (fact "Meta test: all documents in fixture are valid" documents => (has every? valid-against-current-schema?)))
