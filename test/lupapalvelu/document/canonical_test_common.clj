(ns lupapalvelu.document.canonical-test-common
  (:use [lupapalvelu.document.model :only [validate-against-current-schema]]
        [midje.sweet]))

;;
;; Document validator predicate
;;

(defn valid-against-current-schema? [document]
  (or (fact (validate-against-current-schema document) => '()) true))

(defn validate-all-documents [documents]
  (fact "Meta test: all documents in fixture are valid" documents => (has every? valid-against-current-schema?)))
