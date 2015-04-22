(ns lupapalvelu.docx
  (:require [clojure.java.io :as io]
            [sade.core :refer :all])
  (:import [java.io FileOutputStream]
           [fr.opensagres.xdocreport.converter Options ConverterTypeTo ConverterTypeVia]
           [fr.opensagres.xdocreport.document IXDocReport]
           [fr.opensagres.xdocreport.document.registry XDocReportRegistry]
           [fr.opensagres.xdocreport.template IContext]
           [fr.opensagres.xdocreport.template TemplateEngineKind]
           ))

(defn testi []
  (with-open [in (-> "yritystilisopimus.docx" io/resource io/input-stream)
              out (FileOutputStream. (io/file "out.pdf"))]
    (let [report  (.loadReport (XDocReportRegistry/getRegistry) in TemplateEngineKind/Freemarker )
          context (doto (.createContext report)
                    (.put "company" {"name" "Asiakas Oy",
                                     "y" "123456-1"
                                     "address1" "Osoiterivi 1"
                                     "address2" "Osoiterivi 2"
                                     })
                    (.put "contact" {"firstName" "Etu",
                                     "lastName" "Suku"})
                    (.put "account" {"type" "T",
                                     "price" "100"})
                    )
          options (doto (Options/getTo ConverterTypeTo/PDF) (.via ConverterTypeVia/XWPF))
          starting (now)]

      (.convert report context options out )
      (println "Took" (- (now) starting))
      )
    )
  )
