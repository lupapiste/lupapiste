(ns lupapalvelu.xml.validator
  (:require [taoensso.timbre :as timbre :refer [trace tracef debug debugf info warn warnf error errorf fatal]]
            [sade.strings :as s]
            [sade.core :refer :all]
            [clojure.java.io :as io])
  (:import [java.io InputStream Reader StringReader]
           [javax.xml.transform.stream StreamSource]
           [javax.xml.validation SchemaFactory]
           [org.w3c.dom.ls LSResourceResolver LSInput]))

(def- schema-factory (SchemaFactory/newInstance "http://www.w3.org/2001/XMLSchema"))
(.setResourceResolver schema-factory
  (reify LSResourceResolver
    (^LSInput resolveResource [this ^String type, ^String namespaceURI, ^String publicId, ^String systemId, ^String baseURI]
      (let [filename (s/suffix baseURI "/")
            path (clojure.string/replace (s/suffix baseURI "classpath:") filename systemId)]
        (tracef "Loading XML schema resource 'classpath:%s' which was referenced by %s" path filename)
        (reify LSInput
          (^String getBaseURI [this] baseURI)
          (^InputStream getByteStream [this])
          (^boolean getCertifiedText [this] false)
          (^Reader getCharacterStream [this] (-> path io/resource io/input-stream io/reader))
          (^String getEncoding [this])
          (^String getPublicId [this] publicId)
          (^String getStringData [this])
          (^String getSystemId [this] systemId))))))

(defn- stream-source [filename]
  (let [ss (StreamSource. (clojure.lang.RT/resourceAsStream nil filename))]
    ; Fixes resource loading issue
    (.setSystemId ss (str "classpath:" filename))
    ss))

(def- xml-sources
  ["www.w3.org/2001/xml.xsd"
   "www.w3.org/1999/xlink.xsd"])

(def- public-schema-sources
  (conj xml-sources
    "schemas.opengis.net/gml/3.1.1/smil/smil20.xsd"
    "schemas.opengis.net/gml/3.1.1/base/gml.xsd"))

(def- yht-2_1_0
  (conj public-schema-sources
    "krysp/yhteiset-2.1.0.xsd"
    "krysp/rakennusvalvonta-2.1.2.xsd"
    "krysp/YleisenAlueenKaytonLupahakemus.xsd"
    "krysp/poikkeamispaatos_ja_suunnittelutarveratkaisu.xsd"))

(def- yht-2_1_1
  (conj public-schema-sources
    "krysp/yhteiset-2.1.1.xsd"
    "krysp/rakennusvalvonta-2.1.3.xsd"
    "krysp/poikkeamispaatos_ja_suunnittelutarveratkaisu-2.1.3.xsd"))

(def- yht-2_1_2
  (conj public-schema-sources
    "krysp/yhteiset-2.1.2.xsd"
    "krysp/rakennusvalvonta-2.1.4.xsd"
    "krysp/poikkeamispaatos_ja_suunnittelutarveratkaisu-2.1.4.xsd"))

(def- yht-2_1_3
  (conj public-schema-sources
    "krysp/yhteiset-2.1.3.xsd"
    "krysp/rakennusvalvonta-2.1.5.xsd"
    "krysp/poikkeamispaatos_ja_suunnittelutarveratkaisu-2.1.5.xsd"
    "krysp/YleisenAlueenKaytonLupahakemus-2.1.3.xsd"
    "krysp/ymparistoluvat-2.1.2.xsd"
    "krysp/maaAinesluvat-2.1.2.xsd"
    "krysp/vesihuoltolaki-2.1.3.xsd"
    "krysp/ilmoitukset-2.1.2.xsd"))

(def- yht-2_1_5
  (conj public-schema-sources
    "krysp/yhteiset-2.1.5.xsd"
    "krysp/rakennusvalvonta-2.1.5.xsd"
    "krysp/poikkeamispaatos_ja_suunnittelutarveratkaisu-2.2.0.xsd"
    "krysp/YleisenAlueenKaytonLupahakemus-2.2.0.xsd"
    "krysp/ymparistoluvat-2.2.0.xsd"
    "krysp/maaAinesluvat-2.2.0.xsd"
    "krysp/vesihuoltolaki-2.2.0.xsd"
    "krysp/ilmoitukset-2.2.0.xsd"))

(def- yht-2_1_6
  (conj public-schema-sources
    "krysp/yhteiset-2.1.6.xsd"
    "krysp/rakennusvalvonta-2.2.0.xsd"
    "krysp/poikkeamispaatos_ja_suunnittelutarveratkaisu-2.2.1.xsd"
    "krysp/YleisenAlueenKaytonLupahakemus-2.2.1.xsd"
    "krysp/ymparistoluvat-2.2.1.xsd"
    "krysp/maaAinesluvat-2.2.1.xsd"
    "krysp/vesihuoltolaki-2.2.1.xsd"
    "krysp/ilmoitukset-2.2.1.xsd"))

(def- rakval-2_1_6
  (conj public-schema-sources
        "krysp/yhteiset-2.1.5.xsd"
        "krysp/rakennusvalvonta-2.1.6.xsd"))

(def- rakval-2_1_8
  (conj public-schema-sources
        "krysp/yhteiset-2.1.5.xsd"
        "krysp/rakennusvalvonta-2.1.8.xsd"))

(def- asianhallinta
   (conj xml-sources "asianhallinta/asianhallinta.xsd"))

(defn- create-validator [schemas]
  (.newValidator (.newSchema schema-factory (into-array (map stream-source schemas)))))

(def- common-validator-2_1_0 (create-validator yht-2_1_0))
(def- common-validator-2_1_1 (create-validator yht-2_1_1))
(def- common-validator-2_1_2 (create-validator yht-2_1_2))
(def- common-validator-2_1_3 (create-validator yht-2_1_3))
(def- common-validator-2_1_5 (create-validator yht-2_1_5))
(def- common-validator-2_1_6 (create-validator yht-2_1_6))

(def- asianhallinta-validator (create-validator asianhallinta))

; mapping-common contains the same information.
; Perhaps the permit type -- version -mapping could
; be generated from a single source.

(def- rakval-validators
  {"2.1.2" common-validator-2_1_0
   "2.1.3" common-validator-2_1_1
   "2.1.4" common-validator-2_1_2
   "2.1.5" common-validator-2_1_3
   "2.1.6" (create-validator rakval-2_1_6)
   "2.1.8" (create-validator rakval-2_1_8)})

(def- ya-validators
  {"2.1.2" common-validator-2_1_0
   "2.1.3" common-validator-2_1_3
   "2.2.0" common-validator-2_1_5
   "2.2.1" common-validator-2_1_6
   "ah-1.1" asianhallinta-validator})

(def- poik-validators
  {"2.1.2" common-validator-2_1_0
   "2.1.3" common-validator-2_1_1
   "2.1.4" common-validator-2_1_2
   "2.1.5" common-validator-2_1_3
   "ah-1.1" asianhallinta-validator})

(def- ymp-validators
  {"2.1.2" common-validator-2_1_3
   "ah-1.1" asianhallinta-validator})

(def- schema-validators
  {:R   rakval-validators
   :P   poik-validators
   :YA  ya-validators
   :YI  ymp-validators
   :MAL ymp-validators
   :VVVL {"2.1.3" common-validator-2_1_3
          "ah-1.1" asianhallinta-validator}
   :YL  ymp-validators
   :MM  {"ah-1.1" asianhallinta-validator} ; maankayton muutos aka kaavat
   :KT  {"ah-1.1" asianhallinta-validator}})

(def supported-versions-by-permit-type
  (reduce (fn [m [permit-type validators]] (assoc m permit-type (keys validators))) {} schema-validators))

(def supported-krysp-versions-by-permit-type
  (reduce (fn [m [k versions]] (assoc m k (filter #(Character/isDigit (.charAt % 0)) versions))) {} supported-versions-by-permit-type))

(def supported-asianhallinta-versions-by-permit-type
  (reduce (fn [m [k versions]] (assoc m k (filter #(s/starts-with % "ah") versions))) {} supported-versions-by-permit-type))

(defn validate
  "Throws an exception if the markup is invalid"
  [xml permit-type schema-version]
  (let [xml-reader (StringReader. xml)
        xml-source (StreamSource. xml-reader)
        validator  (get-in schema-validators [(keyword permit-type) (name schema-version)])]
    (when-not validator
      (throw (IllegalArgumentException. (str "Unsupported schema version " schema-version " for permit type " permit-type))))

    (try
      (.validate validator xml-source)
      (catch Exception e
        (debugf "Validation error with permit-type %s, schema-version %s: %s" permit-type schema-version (.getMessage e))
        (throw e)))))
