(ns lupapalvelu.pdf-export-itest
  (:require [clojure.java.io :as io]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.pdf-export :as pdf-export]
            [lupapalvelu.i18n :refer [with-lang loc]]
            [sade.util :as util]
            [midje.sweet :refer :all]
            [pdfboxing.text :as pdfbox]
            [lupapalvelu.mongo :as mongo])
  (:import (java.io File)))

(apply-remote-minimal)

(defn- localize-value [value]
  (cond
    (= java.lang.Boolean (type value))
      (let [loc-key (if value "yes" "no")]
        (loc loc-key))
    :else value))

(defn- walk-function [pdf-content node]
  ; What to do when this is a value node
  (when (and (map? node) (contains? node :value))
    (let [loc-value (localize-value (:value node))]
      pdf-content => (contains loc-value)))

  ; When do we need to stop going deeper in the tree
  (if (and (vector? node) (#{:_selected :userId} (first node)))
    nil
    node)
  )

(fact "Generate PDF from an R application with dummy document values"
  (let [test-municipality        444
        test-address             "Testikuja 1234"
        test-property-id         "44400100100100"
        test-submitted           (clj-time.coerce/to-long "2014-01-01")
        test-authority           {:id "foo" :firstName "Erkki" :lastName "Testihenkilo"}
        test-primaryOperation    {:name "kerrostalo-rivitalo"}
        test-secondaryoperations [{:name "aita"}]

        application (create-and-submit-application pena)
        _           (generate-documents application pena)
        application (query-application pena (:id application))

        ; Add some manual test data to application common fields
        application (merge application
                           {:municipality        test-municipality
                            :address             test-address
                            :submitted           test-submitted
                            :propertyId          test-property-id
                            :authority           test-authority
                            :primaryOperation    test-primaryOperation
                            :secondaryOperations test-secondaryoperations})

        lang        "fi"
        file        (File/createTempFile "pdf-export-itest-" ".pdf")]


    (with-lang lang
      (fact "Test data assertions (just in case)"
        (loc (str "municipality." test-municipality)) => "Lohja"
        (loc (str "operations.kerrostalo-rivitalo")) => "Asuinkerrostalon tai rivitalon rakentaminen"
        (loc (str "operations.aita")) => "Aidan rakentaminen")

      (pdf-export/generate application lang file)

      (let [pdf-content (pdfbox/extract (.getAbsolutePath file))
            documents-data (map :data (:documents application))]

        ; common fields
        pdf-content => (contains test-address)
        pdf-content => (contains "Lohja")
        pdf-content => (contains (loc (:state application)))
        pdf-content => (contains "444-1-10-100")
        pdf-content => (contains "01.01.2014")
        pdf-content => (contains (:id application))
        pdf-content => (contains "Testihenkilo Erkki")
        pdf-content => (contains "Asuinkerrostalon tai rivitalon rakentaminen, Aidan rakentaminen")
        ; documents
        (doseq [doc-data documents-data]
          (clojure.walk/prewalk (partial walk-function pdf-content) doc-data)))
        (.delete file))))
