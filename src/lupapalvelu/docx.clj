(ns lupapalvelu.docx
  (:require [clojure.java.io :as io]
            [clojure.walk :as walk]
            [lupapalvelu.campaign :as camp]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [taoensso.timbre :refer [debug debugf info warn error]])
  (:import [fr.opensagres.xdocreport.converter Options ConverterTypeTo ConverterTypeVia]
           [fr.opensagres.xdocreport.document IXDocReport]
           [fr.opensagres.xdocreport.document.registry XDocReportRegistry]
           [fr.opensagres.xdocreport.template IContext]
           [fr.opensagres.xdocreport.template TemplateEngineKind]
           [java.io InputStream ByteArrayOutputStream ByteArrayInputStream]))

(set! *warn-on-reflection* true)

(def ^Options to-pdf (-> (Options/getTo ConverterTypeTo/PDF) (.via ConverterTypeVia/XWPF)))

(defn- freemarker-compliant [m]
  ; Freemarker throws exception from null values by default
  (-> m walk/stringify-keys (util/convert-values #(nil? %2) (constantly ""))))

(defn- ^IContext create-context [^IXDocReport report m]
  (reduce (fn [^IContext c, [k v]] (.put c k v) c) (.createContext report) m))

(defn ^InputStream docx-template-to-pdf [template-name model]
  (with-open [in (-> template-name io/resource io/input-stream)
              out (ByteArrayOutputStream. 4096)]
    (let [report  (.loadReport (XDocReportRegistry/getRegistry) in TemplateEngineKind/Freemarker )
          context (->> model freemarker-compliant (create-context report))
          starting (now)]
      (.convert report context to-pdf out)
      (debugf "Conversion took %d ms" (- (now) starting))
      (ByteArrayInputStream. (.toByteArray out)))))


(def- yritystilisopimus-default-model {:date ""
                                       :company {:name "", :y "", :address1 "", :zip "", :po ""}
                                       :contact {:firstName "", :lastName ""}
                                       :account {:type "", :price ""}})

(defmulti ^InputStream yritystilisopimus (fn [company & _]
                                           (when (-> company :campaign ss/not-blank?)
                                             :campaign)))

(defmethod ^InputStream yritystilisopimus :default
  [company contact account timestamp]
  {:pre [(map? company) (map? contact) (map? account)]}
  (let [model (util/deep-merge
                yritystilisopimus-default-model
                {:date (util/to-local-date timestamp)
                 :company company
                 :contact contact
                 :account account})]
    (docx-template-to-pdf "yritystilisopimus.docx" model)))

(defmethod ^InputStream yritystilisopimus :campaign
  [company contact account timestamp]
  {:pre [(map? company) (map? contact) (map? account)]}
  (let [model (util/deep-merge
                yritystilisopimus-default-model
                {:date (util/to-local-date timestamp)
                 :company company
                 :contact contact
                 :account account
                 :campaign (camp/contract-info company)})]
    (docx-template-to-pdf "kampanja-yritystilisopimus.docx" model)))
