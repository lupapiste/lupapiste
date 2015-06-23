(ns lupapalvelu.preview-test
  (:require [clojure.java.io :as io]
    [clojure.string :as s]
            [sade.strings :refer [encode-filename]]
            [sade.env :as env]
            [monger.core :as monger]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.preview :refer :all]
            [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]])
  (:import (java.awt.image BufferedImage)
           (java.io FileOutputStream ByteArrayOutputStream ByteArrayInputStream FileInputStream)
           (javax.imageio ImageIO)))


;;(io/copy (pdf-to-image-input-stream "/home/michaelho/ws/lupapalvelu/problematic-pdfs/Yhdistelmakartta_asema-johto_Oksapolku-Pihkakatu.pdf") (FileOutputStream. "/tmp/a1.jpg"))