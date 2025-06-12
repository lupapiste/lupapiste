(ns lupapalvelu.xml.validator
  (:require [clojure.java.io :as io]
            [lupapalvelu.logging :refer [debugf tracef]]
            [lupapalvelu.publish-dates :as pd]
            [sade.core :refer [def- fail!]]
            [sade.date :as date]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.xml :as xml])
  (:import [clojure.lang RT]
           [java.io InputStream StringReader]
           [java.time ZonedDateTime]
           [javax.xml.transform.stream StreamSource]
           [javax.xml.validation SchemaFactory Validator]
           [org.w3c.dom.ls LSInput LSResourceResolver]
           [org.xml.sax SAXParseException]))

(set! *warn-on-reflection* true)

(def- ^SchemaFactory schema-factory (SchemaFactory/newInstance "http://www.w3.org/2001/XMLSchema"))
(.setResourceResolver
  schema-factory
  (reify LSResourceResolver
    (resolveResource [_ _ _ publicId systemId baseURI]
      (let [filename (ss/suffix baseURI "/")
            path     (ss/replace (ss/suffix baseURI "classpath:") filename systemId)]
        (tracef "Loading XML schema resource 'classpath:%s' which was referenced by %s" path filename)
        (reify LSInput
          (getBaseURI [_] baseURI)
          (getByteStream [_])
          (getCertifiedText [_] false)
          (getCharacterStream [_] (if-let [r (io/resource path)]
                                    (-> r io/input-stream io/reader)
                                    (throw (ex-info "Failed to read path" {:filename filename
                                                                           :systemId systemId
                                                                           :path     path}))))
          (getEncoding [_])
          (getPublicId [_] publicId)
          (getStringData [_])
          (getSystemId [_] systemId))))))

(defn- stream-source [filename]
  (let [ss (StreamSource. (RT/resourceAsStream nil filename))]
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
    "krysp/ilmoitukset-2.2.1.xsd"
    "krysp/osoitteet-2.1.1.xsd"))

(def- yht-2_1_8
  (conj public-schema-sources
    (if (env/in-env? "QA") "krysp/yhteiset-2.1.8_qa.xsd" "krysp/yhteiset-2.1.8.xsd")
    (if (env/in-env? "QA") "krysp/rakennusvalvonta-2.2.2_qa.xsd" "krysp/rakennusvalvonta-2.2.2.xsd")
    "krysp/poikkeamispaatos_ja_suunnittelutarveratkaisu-2.2.3.xsd"
    "krysp/YleisenAlueenKaytonLupahakemus-2.2.3.xsd"
    "krysp/ymparistoluvat-2.2.3.xsd"
    "krysp/maaAinesluvat-2.2.3.xsd"
    "krysp/vesihuoltolaki-2.2.3.xsd"
    "krysp/ilmoitukset-2.2.3.xsd"
    "krysp/osoitteet-2.1.3.xsd"))

(def- yht-2_1_9
  (conj public-schema-sources
        "krysp/yhteiset-2.1.9.xsd"
        "krysp/poikkeamispaatos_ja_suunnittelutarveratkaisu-2.2.4.xsd"
        "krysp/YleisenAlueenKaytonLupahakemus-2.2.4.xsd"
        "krysp/ymparistoluvat-2.2.4.xsd"
        "krysp/maaAinesluvat-2.2.4.xsd"
        "krysp/vesihuoltolaki-2.2.4.xsd"
        "krysp/ilmoitukset-2.2.4.xsd"
        "krysp/osoitteet-2.1.4.xsd"))

(def- yht-2_1_9-dmcity
  (conj public-schema-sources
        "krysp/yhteiset-2.1.9.xsd"
        "krysp/osoitteet-2.1.4.xsd"
        "krysp/dmcity/lupapiste-1.0.0.xsd"
        "krysp/dmcity/ymparistoluvat-2.2.4.xsd"
        "krysp/dmcity/maaAinesluvat-2.2.4.xsd"
        "krysp/dmcity/vesihuoltolaki-2.2.4.xsd"
        "krysp/dmcity/ilmoitukset-2.2.4.xsd"))

(def- yht-2_1_11
  (conj public-schema-sources
        "www.w3.org/2001/XMLSchema.xsd"
        (if (env/in-env? "QA")
          "krysp/yhteiset-2.1.11_qa.xsd"
          "krysp/yhteiset-2.1.11.xsd")
        "krysp/poikkeamispaatos_ja_suunnittelutarveratkaisu-2.2.6.xsd"
        "krysp/YleisenAlueenKaytonLupahakemus-2.2.6.xsd"
        "krysp/ymparistoluvat-2.2.6.xsd"
        "krysp/maaAinesluvat-2.2.6.xsd"
        "krysp/vesihuoltolaki-2.2.6.xsd"
        "krysp/ilmoitukset-2.2.6.xsd"
        "krysp/osoitteet-2.1.4.xsd"))

(def- rakval-2_1_6
  (conj public-schema-sources
        "krysp/yhteiset-2.1.5.xsd"
        "krysp/rakennusvalvonta-2.1.6.xsd"))

(def- rakval-2_1_8
  (conj public-schema-sources
        "krysp/yhteiset-2.1.5.xsd"
        "krysp/rakennusvalvonta-2.1.8.xsd"))

(def- rakval-2_2_3
  (conj public-schema-sources
        "krysp/yhteiset-2.1.9.xsd"
        "krysp/rakennusvalvonta-2.2.3.xsd"))

(def- rakval-2_2_4
  (concat public-schema-sources
          (if (env/in-env? "QA")
            ["krysp/yhteiset-2.1.9_qa.xsd" "krysp/rakennusvalvonta-2.2.4_qa.xsd"]
            ["krysp/yhteiset-2.1.9.xsd" "krysp/rakennusvalvonta-2.2.4.xsd"])))

(def- rakval-2_2_6
  (concat public-schema-sources
          (if (env/in-env? "QA")
            ["www.w3.org/2001/XMLSchema.xsd"
             "krysp/yhteiset-2.1.11_qa.xsd"
             "krysp/rakennusvalvonta-2.2.6_qa.xsd"]
            ["www.w3.org/2001/XMLSchema.xsd"
             "krysp/yhteiset-2.1.11.xsd"
             "krysp/rakennusvalvonta-2.2.6.xsd"])))

(def- mkmu-1_0_1
  (conj public-schema-sources
        "krysp/yhteiset-2.1.6.xsd"
        "krysp/maankaytonmuutos-1.0.1.xsd"))

(def- mkmu-1_0_3
  (conj public-schema-sources
        "krysp/yhteiset-2.1.8.xsd"
        "krysp/maankaytonmuutos-1.0.3.xsd"))

(def- mkmu-1_0_3-dmcity
  (conj public-schema-sources
        "krysp/yhteiset-2.1.8.xsd"
        "krysp/dmcity/lupapiste-1.0.0.xsd"
        "krysp/dmcity/maankaytonmuutos-1.0.3.xsd"))

(def- mkmu-1_0_6
  (conj public-schema-sources
        "www.w3.org/2001/XMLSchema.xsd"
        "krysp/yhteiset-2.1.11.xsd"
        "krysp/maankaytonmuutos-1.0.6.xsd"))

(def- kiito-1_0_2
  (conj public-schema-sources
        "krysp/yhteiset-2.1.6.xsd"
        "krysp/kiinteistotoimitus-1.0.2.xsd"))

(def- kiito-1_0_5
  (conj public-schema-sources
        "krysp/yhteiset-2.1.8.xsd"
        "krysp/kiinteistotoimitus-1.0.5.xsd"))

(def- kiito-1_0_6
  (conj public-schema-sources
        "krysp/yhteiset-2.1.9.xsd"
        "krysp/kiinteistotoimitus-1.0.6.xsd"))

(def- kiito-1_0_6-dmcity
  (conj public-schema-sources
        "krysp/yhteiset-2.1.9.xsd"
        "krysp/dmcity/lupapiste-1.0.0.xsd"
        "krysp/dmcity/kiinteistotoimitus-1.0.6.xsd"))

(def- kiito-1_0_8
  (conj public-schema-sources
        "www.w3.org/2001/XMLSchema.xsd"
        "krysp/yhteiset-2.1.11.xsd"
        "krysp/kiinteistotoimitus-1.0.8.xsd"))

(def- kiito-1_0_8-lp
  (conj public-schema-sources
        "www.w3.org/2001/XMLSchema.xsd"
        "krysp/yhteiset-2.1.11.xsd"
        "krysp/lp/lupapiste-1.0.1.xsd"
        "krysp/lp/kiinteistotoimitus-1.0.8.xsd"))

(def- asianhallinta_1_1
  (conj xml-sources "asianhallinta/asianhallinta_1.1.xsd"))

(def- asianhallinta_1_2
  (conj xml-sources "asianhallinta/asianhallinta_1.2.xsd"))

(def- asianhallinta_1_3
  (conj xml-sources "asianhallinta/asianhallinta_1.3.xsd"))

(defn- create-validator
  "Returns a new `Validator` instance.
  Note that `Validator` instances are not thread-safe, do not use them concurrently from different threads."
  [schemas]
  (.newValidator (.newSchema schema-factory
                             ^"[Ljavax.xml.transform.stream.StreamSource;" (into-array (map stream-source schemas)))))

(def- common-validator-2_1_0 (create-validator yht-2_1_0))
(def- common-validator-2_1_1 (create-validator yht-2_1_1))
(def- common-validator-2_1_2 (create-validator yht-2_1_2))
(def- common-validator-2_1_3 (create-validator yht-2_1_3))
(def- common-validator-2_1_5 (create-validator yht-2_1_5))
(def- common-validator-2_1_6 (create-validator yht-2_1_6))
(def- common-validator-2_1_8 (create-validator yht-2_1_8))
(def- common-validator-2_1_9 (create-validator yht-2_1_9))
(def- common-validator-2_1_9-dmcity (create-validator yht-2_1_9-dmcity))
(def- common-validator-2_1_11 (create-validator yht-2_1_11))

(def- asianhallinta-validator-1_1 (create-validator asianhallinta_1_1))
(def- asianhallinta-validator-1_2 (create-validator asianhallinta_1_2))
(def- asianhallinta-validator-1_3 (create-validator asianhallinta_1_3))

; mapping-common contains the same information.
; Perhaps the permit type -- version -mapping could
; be generated from a single source.

(def- rakval-validators
  {"ah-1.3" asianhallinta-validator-1_3
   "2.1.2" common-validator-2_1_0
   "2.1.3" common-validator-2_1_1
   "2.1.4" common-validator-2_1_2
   "2.1.5" common-validator-2_1_3
   "2.1.6" (create-validator rakval-2_1_6)
   "2.1.8" (create-validator rakval-2_1_8)
   "2.2.0" common-validator-2_1_6
   "2.2.2" common-validator-2_1_8
   "2.2.3" (create-validator rakval-2_2_3)
   "2.2.4" (create-validator rakval-2_2_4)
   "2.2.6" (create-validator rakval-2_2_6)})

(def- ya-validators
  {"2.1.2" common-validator-2_1_0
   "2.1.3" common-validator-2_1_3
   "2.2.0" common-validator-2_1_5
   "2.2.1" common-validator-2_1_6
   "2.2.3" common-validator-2_1_8
   "ah-1.1" asianhallinta-validator-1_1
   "ah-1.2" asianhallinta-validator-1_2
   "ah-1.3" asianhallinta-validator-1_3
   "2.2.4" common-validator-2_1_9
   "2.2.6" common-validator-2_1_11})

(def- poik-validators
  {"2.1.2" common-validator-2_1_0
   "2.1.3" common-validator-2_1_1
   "2.1.4" common-validator-2_1_2
   "2.1.5" common-validator-2_1_3
   "2.2.0" common-validator-2_1_5
   "2.2.1" common-validator-2_1_6
   "2.2.3" common-validator-2_1_8
   "2.2.4" common-validator-2_1_9
   "2.2.6" common-validator-2_1_11
   "ah-1.1" asianhallinta-validator-1_1
   "ah-1.2" asianhallinta-validator-1_2
   "ah-1.3" asianhallinta-validator-1_3})

(def- ymp-validators
  {"2.1.2"           common-validator-2_1_3
   "2.2.1"           common-validator-2_1_6
   "2.2.3"           common-validator-2_1_8
   "2.2.4"           common-validator-2_1_9
   "2.2.4-dmcity-lp" (create-validator yht-2_1_9-dmcity)
   "2.2.6"           common-validator-2_1_11
   "ah-1.1"          asianhallinta-validator-1_1
   "ah-1.2"          asianhallinta-validator-1_2
   "ah-1.3"          asianhallinta-validator-1_3})

(def- mkmu-validators
  {"2.1.6"           common-validator-2_1_6
   "1.0.1"           (create-validator mkmu-1_0_1)
   "1.0.3"           (create-validator mkmu-1_0_3)
   "1.0.3-dmcity-lp" (create-validator mkmu-1_0_3-dmcity)
   "1.0.6"           (create-validator mkmu-1_0_6)
   "ah-1.1"          asianhallinta-validator-1_1
   "ah-1.2"          asianhallinta-validator-1_2
   "ah-1.3"          asianhallinta-validator-1_3})

(def- kiito-validators
  {"2.1.6"           common-validator-2_1_6
   "1.0.2"           (create-validator kiito-1_0_2)
   "1.0.5"           (create-validator kiito-1_0_5)
   "1.0.6"           (create-validator kiito-1_0_6)
   "1.0.6-dmcity-lp" (create-validator kiito-1_0_6-dmcity)
   "1.0.8"           (create-validator kiito-1_0_8)
   "1.0.8-lp"        (create-validator kiito-1_0_8-lp)
   "ah-1.1"          asianhallinta-validator-1_1
   "ah-1.2"          asianhallinta-validator-1_2
   "ah-1.3"          asianhallinta-validator-1_3})

(def- vvvl-validators
  {"2.1.3"           common-validator-2_1_3
   "2.2.1"           common-validator-2_1_6
   "2.2.3"           common-validator-2_1_8
   "2.2.4"           common-validator-2_1_9
   "2.2.4-dmcity-lp" common-validator-2_1_9-dmcity
   "2.2.6"           common-validator-2_1_11
   "ah-1.1"          asianhallinta-validator-1_1
   "ah-1.2"          asianhallinta-validator-1_2
   "ah-1.3"          asianhallinta-validator-1_3})

(def- osoitteet-validators
  {"2.1.1" common-validator-2_1_6
   "2.1.3" common-validator-2_1_8
   "2.1.4" common-validator-2_1_9})

(def- schema-validators
  {:R   rakval-validators
   :P   poik-validators
   :YA  ya-validators
   :YI  ymp-validators
   :MAL ymp-validators
   :VVVL vvvl-validators
   :YL  ymp-validators
   :YM  ymp-validators
   :MM  mkmu-validators ; maankayton muutos aka kaavat
   :KT  kiito-validators
   :osoitteet osoitteet-validators})

(def supported-versions-by-permit-type
  (reduce (fn [m [permit-type validators]] (assoc m permit-type (keys validators))) {} schema-validators))

(def publish-date
  {:R {"2.2.6" pd/ryhti-2025-publish-date}})

(defn supported-krysp-versions-by-permit-type
  ([]
   (supported-krysp-versions-by-permit-type (date/now)))
  ([now]
   (let [krysp-version?  (fn [[_schema-type ^String version]] (Character/isDigit (.charAt version 0)))
         published?      (fn [[schema-type version]]
                           (if-let [^ZonedDateTime valid-after (get-in publish-date [schema-type version])]
                             (or (env/feature? :ignore-kuntagml-publish-date)
                                 (.isBefore valid-after now))
                             true))
         accept-version? (every-pred krysp-version? published?)]
     (->> supported-versions-by-permit-type
          (keep (fn [[k versions]]
                  (when-let [v (filterv (fn [v] (accept-version? [k v])) versions)]
                    [k v])))
          (into {})))))

(def supported-asianhallinta-versions-by-permit-type
  (reduce (fn [m [k versions]] (assoc m k (filter #(ss/starts-with? % "ah") versions))) {} supported-versions-by-permit-type))

(defn dmcity-version? [krysp-version]
  (ss/ends-with? krysp-version "-dmcity-lp"))

(defn validate
  "Throws an exception if the markup is invalid"
  [xml permit-type schema-version]
  (let [xml-source           (if (string? xml)
                               (StreamSource. (StringReader. xml))
                               (StreamSource. ^InputStream xml))
        ^Validator validator (get-in schema-validators [(keyword permit-type) (name schema-version)])]
    (when-not validator
      (throw (IllegalArgumentException. (str "Unsupported schema version " schema-version " for permit type " permit-type))))

    (try
      ;; JDK: "A validator object is not thread-safe and not reentrant."
      (locking validator
        (.validate validator xml-source))
      (catch Exception e
        (debugf "Validation error with permit-type %s, schema-version %s: %s" permit-type schema-version (.getMessage e))
        (throw e)))))

(defn validate-integration-message!
  "Purges (removes invalid whitespace) and validates `xml-string`. Returns (purged)
  `xml-string` if valid. Throws using `sade.core/fail!` if validation failed."
  [xml-string permit-type krysp-version]
  (try
    (let [xml-string (xml/purge-xml-string xml-string)]
      (validate xml-string permit-type krysp-version)
      xml-string)
    (catch SAXParseException e
      (fail! :error.integration.create-message :details (.getMessage e)))))
