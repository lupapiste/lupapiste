(ns lupapalvelu.pdf.libreoffice-pdfa-converter-test
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]]
            [hiccup.core :as hiccup]
            [sade.strings :as ss]
            [lupapalvelu.pdf.pdf-test-util :as util]
            [clojure.java.io :as io])
  (:import (java.io File FileOutputStream FileInputStream)
           (com.lowagie.text.rtf.parser RtfParser)
           (com.lowagie.text Document)
           (com.lowagie.text.pdf PdfWriter)))


#_(facts "Valid PDF/A via Hiccups LibreOffice"
       (let [app (util/dummy-application
                   {:id           "LP-1"
                    :address      "Korpikuusen kannon alla 1"
                    :title        "Korpikuusen kannon alla 1 "
                    :municipality "444"
                    :state        "draft"})
             stm (util/dummy-statement "1" "Minna Malli" "joku status" "Lorem ipsum dolor sit amet, quis sollicitudin, suscipit cupiditate et. Metus pede litora lobortis, vitae sit mauris, fusce sed, justo suspendisse, eu ac augue. Sed vestibulum urna rutrum, at aenean porta aut lorem mollis in. In fusce integer sed ac pellentesque, suspendisse quis sem luctus justo sed pellentesque, tortor lorem urna, aptent litora ac omnis. Eros a quis eu, aut morbi pulvinar in sollicitudin eu ac. Enim pretium ipsum convallis ante condimentum, velit integer at magna nec, etiam sagittis convallis, pellentesque congue ut id id cras. In mauris, platea rhoncus sociis potenti semper, aenean urna nibh dapibus, justo pellentesque sed in rutrum vulputate donec, in lacus vitae sed sint et. Dolor duis egestas pede libero.")
             dst-file (lupapalvelu.pdf.libreoffice-pdfa-converter/render-statement-pdf app stm)]
         (debug " rendered pdf: " (.getAbsolutePath dst-file))
         (fact "file exists  " (.length dst-file) => #(> % 1000))
         ;(.delete file)
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
         ;(.delete file)
         ))
