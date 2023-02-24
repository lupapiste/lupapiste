(ns lupapalvelu.invoices.xml-validator
  (:require [clojure.java.io :as io]
            [taoensso.timbre :refer [trace tracef debug debugf info warn warnf error errorf fatal]]
            [sade.strings :as ss])
  (:import [clojure.lang RT]
           [java.io StringReader]
           [javax.xml.transform.stream StreamSource]
           [javax.xml.validation SchemaFactory]
           [org.w3c.dom.ls LSResourceResolver LSInput]))

(def ^SchemaFactory schema-factory (SchemaFactory/newInstance "http://www.w3.org/2001/XMLSchema"))
(.setResourceResolver schema-factory
  (reify LSResourceResolver
    (resolveResource [_ _ _ publicId systemId baseURI]
      (let [filename (ss/suffix baseURI "/")
            path (ss/replace (ss/suffix baseURI "classpath:") filename systemId)]
        (tracef "Loading XML schema resource 'classpath:%s' which was referenced by %s" path filename)
        (reify LSInput
          (getBaseURI [_] baseURI)
          (getByteStream [_])
          (getCertifiedText [_] false)
          (getCharacterStream [_] (-> path io/resource io/input-stream io/reader))
          (getEncoding [_])
          (getPublicId [_] publicId)
          (getStringData [_])
          (getSystemId [_] systemId))))))


(defn stream-source [filename]
  (let [ss (StreamSource. (RT/resourceAsStream nil filename))]
    ; Fixes resource loading issue
    (.setSystemId ss (str "classpath:" filename))
    ss))

(def schemas
  {:tampere-ya  ["invoices/tampere-ya/salesOrder_v11.2.xsd"]
   :general-api ["invoices/InvoiceTransfer.xsd"]})

(defn create-validator [schemas]
  (when schemas
    (.newValidator (.newSchema schema-factory
                               ^"[Ljavax.xml.transform.stream.StreamSource;" (into-array (map stream-source schemas))))))

(defn validate
  "Throws an exception if the markup is invalid"
  [xml integration]
  (let [xml-source (if (string? xml)
                     (StreamSource. (StringReader. xml))
                     (StreamSource. xml))
        validator  (create-validator (schemas integration))]
    (when-not validator
      (throw (IllegalArgumentException. (str "Unsupported integration type " integration))))
    (try
      (.validate validator xml-source)
      (catch Exception e
        (debugf "Validation error with integration %s: %s" integration (.getMessage e))
        (throw e)))))
