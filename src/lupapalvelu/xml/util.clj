(ns lupapalvelu.xml.util
  "Utilities, mostly related to XML"
  (:require [clojure.java.io :as io])
  (:import [jakarta.xml.bind JAXBContext Marshaller]
           [java.io StringWriter]
           [javax.xml XMLConstants]
           [javax.xml.transform Source]
           [javax.xml.transform.stream StreamSource]
           [javax.xml.validation Schema SchemaFactory]
           [org.glassfish.jaxb.runtime.marshaller NamespacePrefixMapper]))

(set! *warn-on-reflection* true)

(def ^SchemaFactory sf (SchemaFactory/newInstance XMLConstants/W3C_XML_SCHEMA_NS_URI))

(defn stream-source
  "Create a `Source` from a resource path pointing to a `.xsd`."
  ^StreamSource [schema-path]
  (let [ss (-> (ClassLoader/getSystemResourceAsStream schema-path)
               StreamSource.)]
    ;; Fixes resource loading issue
    (.setSystemId ss (str "classpath:" schema-path))
    ss))

(defn load-schemas
  "Create a merged `Schema` from a collection of `Source`s`."
  ^Schema [sources]
  (let [^Source/1 source-arr (into-array Source sources)]
    (.newSchema sf source-arr)))

(defn load-schema
  "Create a `Schema` from a resource path pointing to a `.xsd`'"
  ^Schema [schema-path]
  (if-let [url (io/resource schema-path)]
    (.newSchema sf url)
    (throw (ex-info "Failed to find schema-resource" {:path schema-path}))))

(defn merge-schema-paths
  "Create a merged `Schema` from a collection of resource paths pointing to `.xsd`s."
  [schema-paths]
  (load-schemas (mapv stream-source schema-paths)))

(defn make-mapper
  "Map XLMNS URLs to desired prefixes instead of using the default `ns1`, `ns2`, .."
  [prefixes]
  (proxy [NamespacePrefixMapper] []
    (getPreferredPrefix [namespaceUrl suggestion requirePrefix]
      (get prefixes namespaceUrl suggestion))))

(defn ->xml
  "Serialize JAXB document into a string"
  [{:keys [^JAXBContext jaxb-context
           document
           ^Schema schema
           schema-name
           schema-prefixes
           pretty-print?]}]
  (with-open [sw (StringWriter.)]
    ;; According to various sources, `JAXBContext` are thread-safe and reusable,
    ;; but `Marshaller`s are not.
    (let [^Marshaller marshaller (.createMarshaller jaxb-context)]
      (when schema
        (.setSchema marshaller schema))
      (when schema-name
        (.setProperty marshaller Marshaller/JAXB_NO_NAMESPACE_SCHEMA_LOCATION schema-name))
      (.setProperty marshaller Marshaller/JAXB_FORMATTED_OUTPUT (boolean pretty-print?))
      (when (seq schema-prefixes)
        (.setProperty marshaller "org.glassfish.jaxb.namespacePrefixMapper" (make-mapper schema-prefixes)))
      (.marshal marshaller document sw)
      (.toString sw))))
