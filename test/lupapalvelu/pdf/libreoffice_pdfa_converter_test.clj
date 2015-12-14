(ns lupapalvelu.pdf.libreoffice-pdfa-converter-test
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]]
            [hiccup.core :as hiccup]
            [sade.strings :as ss]
            [lupapalvelu.pdf.pdf-test-util :as util]
            [clojure.java.io :as io]
            [pdfboxing.text :as pdfbox]
            [lupapalvelu.pdf.libreoffice-pdfa-converter :as libre]
            [clojure.string :as str])
  (:import (java.io File FileOutputStream FileInputStream)
           (com.lowagie.text.rtf.parser RtfParser)
           (com.lowagie.text Document)
           (com.lowagie.text.pdf PdfWriter)))


(def expected '(:tbody
                [:tr [:td "toim. 1"] [:td ""] [:td ""] [:td 100]]
                [:tr [:td ""] [:td :document] [:td ""] [:td 100]]
                [:tr [:td ""] [:td ""] [:td :attachment] [:td 200]]
                [:tr [:td "toim. 2"] [:td ""] [:td ""] [:td 250]]
                [:tr [:td ""] [:td ""] [:td :attachment] [:td 300]]
                [:tr [:td ""] [:td ""] [:td :attachment] [:td 500]]))
(def app (util/dummy-application
           {:id           "LP-1"
            :address      "Korpikuusen kannon alla 1"
            :title        "Korpikuusen kannon alla 1 "
            :municipality "444"
            :propertyId   "111-111-11-11"
            :created      "11.11.2011"
            :_applicantIndex ["Matti Mallikas"]
            :text         "lorei"
            :authority {:lastName "Sonja Sibbo "}
            :primaryOperation {:name "Kerrostalo-rivitalo"}
            :state        "draft"}))

#_(facts "Valid PDF/A statement from by LibreOffice via Hiccups "
       (let [stm (util/dummy-statement "1" "Minna Malli" "joku status" "Lorem ipsum dolor sit amet")
             file (libre/render-statement-pdf app stm :fi)]
         (debug " rendered pdf: " (.getAbsolutePath file))
         (fact "file exists  " (.length file) => #(> % 1000))
         (let [pdf-content (pdfbox/extract (.getAbsolutePath file))
               rows (remove str/blank? (str/split pdf-content #"\r?\n"))]
           (fact "PDF data rows " (count rows) => 31)
           (fact "Pdf data rows " (nth rows 22) => "14.10.2015")
           (fact "Pdf data rows " (nth rows 24) => "Minna Malli")
           (fact "Pdf data rows " (nth rows 26) => "15.10.2015")
           (fact "Pdf data rows " (nth rows 28) => "joku status")
           (fact "Pdf data rows " (nth rows 30) => "Lorem ipsum dolor sit amet"))
           (.delete file)))

#_(facts "Valid PDF/A history from by LibreOffice via Hiccups "
       (let [history '({:action "toim. 1"
                :start 100
                :documents
                        ({:type :hakemus, :category :document, :ts 100}
                          {:type {:foo :bar}, :category :attachment, :version 1, :ts 200})}
                {:action "toim. 2"
                 :start 250
                 :documents
                         ({:type {:foo :qaz}, :category :attachment, :version 1, :ts 300}
                           {:type {:foo :bar}, :category :attachment, :version 2, :ts 500})})
             file (libre/render-history-pdf app history :fi)]
         (debug " rendered pdf: " (.getAbsolutePath file))
         (fact "file exists  " (.length file) => #(> % 1000))
         (let [pdf-content (pdfbox/extract (.getAbsolutePath file))
               rows (remove str/blank? (str/split pdf-content #"\r?\n"))]
           (fact "PDF data rows " (count rows) => 28)
           (fact "Pdf data rows " (nth rows 22) => "toim. 1 100")
           (fact "Pdf data rows " (nth rows 24) => "attachment 200")
           (fact "Pdf data rows " (nth rows 26) => "attachment 300")
           (fact "Pdf data rows " (nth rows 27) => "attachment 500"))
         #_(.delete file)))

;LibreOffice makes beatiful PDF from RTF
#_(facts "RTF to PDF/A via LibreOffice"
         (let [file-in (File. (.toURI (io/resource "resources/sample-paatosote.rtf")))
               file-out (File/createTempFile "test-libre-rtf-" ".pdf")]
           (debug " LibreOffice converted RTF to DPF: " (.getAbsolutePath file-out))
           (lupapalvelu.pdf.libreoffice-pdfa-converter/render-pdf file-in file-out)
           (fact "file exists  " (.length file-out) => #(> % 1000))
           ;(.delete file-out)
           ))

;iText makes horrible PDF from RTF
#_(facts "RTF to PDF/A via iText"
       (let [file-in (File. (.toURI (io/resource "resources/sample-paatosote.rtf")))
             file-out (File/createTempFile "test-itext-rtf-" ".pdf")
             fos (FileOutputStream. file-out)
             parser (RtfParser. nil)
             doc (Document.)]
         (PdfWriter/getInstance doc fos)
         (.open doc)
         (.convertRtfDocument parser (FileInputStream. file-in) doc)
         (.close doc)
         (debug " itext converted RTF to DPF: " (.getAbsolutePath file-out))
         (fact "file exists  " (.length file-out) => #(> % 1000))
         ;(.delete file-out)
         ))
