(ns lupapalvelu.preview-test
  (:require [clojure.java.io :as io]
            [lupapalvelu.preview :refer :all]
            [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]])
  (:import (java.io FileOutputStream FileInputStream)
           (javax.imageio ImageIO)))

(def pdf-1 "problematic-pdfs/Yhdistelmakartta_asema-johto_Oksapolku-Pihkakatu.pdf")
(def jpg-1 "resources/preview-sample-1.jpg")
(def image-1 (ImageIO/read (clojure.java.io/resource jpg-1)))
(def jbig-pdf "problematic-pdfs/M29235 Sopimus.pdf")

(facts "Test to-buffered-image fails quietly"
       (fact (to-buffered-image nil "application/pdf") => nil)
       (fact (to-buffered-image nil "image/png") => nil)
       (fact (to-buffered-image nil "text/plain") => nil)
       (fact (to-buffered-image nil "image/vnd.dwg") => nil))

(facts "Test pdf-to-buffered-image"
       (fact (.getWidth (pdf-to-buffered-image pdf-1)) => 1685))

(facts "Test scale-image"
       (fact (.getWidth (scale-image image-1)) => 428))

(facts "Test buffered-image-to-input-stream NB: changing preview compression fails this test"
       (fact (.available (buffered-image-to-input-stream image-1)) => 667967))

(facts "Test JBIG2 encoded pdf-to-buffered-image"
       (fact (.getWidth (pdf-to-buffered-image jbig-pdf)) => 1190))


;;(io/copy (pdf-to-image-input-stream "/home/michaelho/ws/lupapalvelu/problematic-pdfs/Yhdistelmakartta_asema-johto_Oksapolku-Pihkakatu.pdf") (FileOutputStream. "/tmp/a1.jpg"))