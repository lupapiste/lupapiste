(ns lupapalvelu.invoices.integration.xml-validator
  (:require [clojure.java.io :as io]
            [lupapalvelu.logging :refer [debugf tracef]]
            [sade.strings :as ss])
  (:import [clojure.lang RT]
           [java.io InputStream StringReader]
           [javax.xml.transform.stream StreamSource]
           [javax.xml.validation SchemaFactory Validator]
           [org.w3c.dom.ls LSInput LSResourceResolver]))

(set! *warn-on-reflection* true)

(def ^SchemaFactory schema-factory
  (doto (SchemaFactory/newInstance "http://www.w3.org/2001/XMLSchema")
    (.setResourceResolver (reify LSResourceResolver
                            (resolveResource [_ _ _ publicId systemId baseURI]
                              (let [filename (ss/suffix baseURI "/")
                                    path     (ss/replace (ss/suffix baseURI "classpath:") filename systemId)]
                                (tracef "Loading XML schema resource 'classpath:%s' which was referenced by %s" path filename)
                                (reify LSInput
                                  (getBaseURI [_] baseURI)
                                  (getByteStream [_])
                                  (getCertifiedText [_] false)
                                  (getCharacterStream [_] (-> path io/resource io/input-stream io/reader))
                                  (getEncoding [_])
                                  (getPublicId [_] publicId)
                                  (getStringData [_])
                                  (getSystemId [_] systemId))))))))

(defn stream-source [filename]
  (let [ss (StreamSource. (RT/resourceAsStream nil filename))]
    ;; Fixes resource loading issue
    (.setSystemId ss (str "classpath:" filename))
    ss))

(defmulti provide-schemas identity)

(defmethod provide-schemas :default
  [integration]
  (throw (ex-info "No schema provider registered!" {:integration integration})))

(defn create-validator
  "Returns a new `Validator` instance.
  Note that `Validator` instances are not thread-safe, do not use them concurrently from different threads."
  ^Validator [schemas]
  (when schemas
    (.newValidator (.newSchema schema-factory
                               ^"[Ljavax.xml.transform.stream.StreamSource;" (into-array (map stream-source schemas))))))

(defn validate
  "Throws an exception if the markup is invalid"
  [xml integration]
  (let [xml-source (if (string? xml)
                     (StreamSource. (StringReader. xml))
                     (StreamSource. ^InputStream xml))
        validator  (create-validator (provide-schemas integration))]
    (when-not validator
      (throw (IllegalArgumentException. (str "Unsupported integration type " integration))))
    (try
      (.validate validator xml-source)
      (catch Exception e
        (debugf "Validation error with integration %s: %s" integration (.getMessage e))
        (throw e)))))
