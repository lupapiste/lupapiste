(ns lupapalvelu.docx
  (:require [clojure.java.io :as io]
            [clojure.walk :as walk]
            [sade.core :refer :all]
            [sade.util :as util])
  (:import [java.io FileOutputStream]
           [fr.opensagres.xdocreport.converter Options ConverterTypeTo ConverterTypeVia]
           [fr.opensagres.xdocreport.document IXDocReport]
           [fr.opensagres.xdocreport.document.registry XDocReportRegistry]
           [fr.opensagres.xdocreport.template IContext]
           [fr.opensagres.xdocreport.template TemplateEngineKind]))

(set! *warn-on-reflection* true)

(defn- ^IContext create-context
  ([^IXDocReport report m]
    (create-context report m (constantly "")))
  ([^IXDocReport report m nil-convert]
    (reduce
      (fn [^IContext c, [k v]] (.put c k v) c)
      (.createContext report)
      (-> m walk/stringify-keys (util/convert-values #(nil? %2) nil-convert)))))

(defn testi []
  (with-open [in (-> "yritystilisopimus.docx" io/resource io/input-stream)
              out (FileOutputStream. (io/file "out.pdf"))]
    (let [report  (.loadReport (XDocReportRegistry/getRegistry) in TemplateEngineKind/Freemarker )
          context (create-context report {:company {:name "Asiakas Oy",
                                                    :y "123456-1"
                                                    :address1 "Osoiterivi 1"
                                                    :address2 "Osoiterivi 2"}
                                          :contact {:firstName "Etu",
                                                    :lastName "Suku"}
                                          :account {:type nil,
                                                    :price "100"}})

          options (doto (Options/getTo ConverterTypeTo/PDF) (.via ConverterTypeVia/XWPF))
          starting (now)]

      (.convert report context options out )
      (println "Took" (- (now) starting))
      )
    )
  )
