(ns lupapalvelu.pdf-export-itest
  (:require [clojure.java.io :as io]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.pdf-export :as pdf-export]
            [lupapalvelu.i18n :refer [with-lang loc]]
            [midje.sweet :refer :all]
            [pdfboxing.text :as pdfbox]))

(apply-remote-minimal)

(defn- localize-value [value lang]
  (cond
    (= java.lang.Boolean (type value))
      (let [loc-key (if value "yes" "no")]
        (with-lang lang
          (loc loc-key)))
    :else value))

(defn- walk-function [locale pdf-content node]
  ; What to do when this is a value node
  (when (and (map? node) (contains? node :value))
    (let [loc-value (localize-value (:value node, locale) locale)]
      pdf-content => (contains loc-value)))

  ; When do we need to stop going deeper in the tree
  (if (and (vector? node) (#{:_selected :userId} (first node)))
    nil
    node)
  )

(fact* "Generate PDF from an R application with dummy document values"
  (let [application (create-and-submit-application pena)
        _           (generate-documents application pena)
        application (query-application pena (:id application))
        fpath       "/tmp/test.pdf"
        file        (io/file fpath)
        locale      "fi"]

    (pdf-export/generate application locale file)

    (let [pdf-content    (pdfbox/extract fpath)
          documents-data (map :data (:documents application))]

      (doseq [doc-data documents-data]
        (clojure.walk/prewalk (partial walk-function locale pdf-content) doc-data)))))