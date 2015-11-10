(ns lupapalvelu.pdf.pdfa-core-test
  (:require [lupapalvelu.pdf.pdfa-core :as pdfa-core]
            [lupapalvelu.pdf.pdf-test-util :as util]
            [midje.sweet :refer :all]
            [taoensso.timbre :as timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]]
            [lupapalvelu.pdf.pdf-export :as pdf-export])
  (:import (org.apache.pdfbox.pdmodel PDDocument)
           (java.io File FileOutputStream)
           (java.util List ArrayList)))


(facts "Valid embedded Fonts "
       (fact "default Font loads  " (.getPostscriptFontName (.getBaseFont (pdfa-core/font {:size 22}))) => "NimbusSanL-Regu")
       (fact "Given existing font loads" (.getPostscriptFontName (.getBaseFont (pdfa-core/font {:size 22 :ttf-name "fonts/n019003l.afm"}))) => "NimbusSanL-Regu")
       (fact "Given non-existing font throws exception " (.getPostscriptFontName (.getBaseFont (pdfa-core/font {:size 22 :ttf-name "fonts/n019003lXXXX.afm"}))) => (throws IllegalArgumentException))
       (fact "Given illegal font-file extension throws exception " (.getPostscriptFontName (.getBaseFont (pdfa-core/font {:size 22 :ttf-name "fonts/n019003l.txt"}))) => (throws IllegalArgumentException))
       )

(facts "Valid PDF/A metadata "
       (let [application (util/dummy-application
                           {:id "LP-1"
                            :address "Korpikuusen kannon alla 1 "
                            :municipality "444"
                            :state "draft"
                            :statements [(util/dummy-statement "2" "Matti Malli" "puollettu" "Lorelei ipsum")
                                         (util/dummy-statement "1" "Minna Malli" "joku status" "Lorem ipsum dolor sit amet, quis sollicitudin, suscipit cupiditate et. Metus pede litora lobortis, vitae sit mauris, fusce sed, justo suspendisse, eu ac augue. Sed vestibulum urna rutrum, at aenean porta aut lorem mollis in. In fusce integer sed ac pellentesque, suspendisse quis sem luctus justo sed pellentesque, tortor lorem urna, aptent litora ac omnis. Eros a quis eu, aut morbi pulvinar in sollicitudin eu ac. Enim pretium ipsum convallis ante condimentum, velit integer at magna nec, etiam sagittis convallis, pellentesque congue ut id id cras. In mauris, platea rhoncus sociis potenti semper, aenean urna nibh dapibus, justo pellentesque sed in rutrum vulputate donec, in lacus vitae sed sint et. Dolor duis egestas pede libero.")]})
             file (File/createTempFile "test-pdf-core-" ".pdf")
             fis (FileOutputStream. file)]
         (pdf-export/generate-pdf-with-child application :statements "2" :fi fis)
         (debug "  test file: " (.getAbsolutePath file))
         (let [doc (PDDocument/load file)
               info (.getDocumentInformation doc)]
           (fact "title" (.getPropertyStringValue info "Title") => "Lupapiste.fi")
           (fact "keywords" (.getKeywords info) => "Lupapiste.fi, XMP, Metadata")

           ;(debug "dc meta:                   "  (.getInputStreamAsString (.getMetadata (.getDocumentCatalog doc))))
           )
         (.delete file)
         ))