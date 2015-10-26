(ns lupapalvelu.wfs
  (:require [taoensso.timbre :as timbre :refer [trace debug info infof warn error errorf fatal]]
            [ring.util.codec :as codec]
            [net.cgrand.enlive-html :as enlive]
            [sade.http :as http]
            [clojure.string :as s]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :refer [xml-> text attr=]]
            [sade.env :as env]
            [sade.xml :as sxml]
            [sade.strings :as ss]
            [sade.util :refer [future*] :as util]
            [sade.core :refer :all]
            [sade.common-reader :as reader]
            [lupapalvelu.logging :as logging]))


;; SAX options
(defn startparse-sax-non-validating [s ch]
  (.. (doto (javax.xml.parsers.SAXParserFactory/newInstance)
        (.setValidating false)
        (.setFeature javax.xml.XMLConstants/FEATURE_SECURE_PROCESSING true)
        (.setFeature "http://apache.org/xml/features/nonvalidating/load-dtd-grammar" false)
        (.setFeature "http://apache.org/xml/features/nonvalidating/load-external-dtd" false)
        (.setFeature "http://xml.org/sax/features/validation" false)
        (.setFeature "http://xml.org/sax/features/external-general-entities" false)
        (.setFeature "http://xml.org/sax/features/external-parameter-entities" false))
    (newSAXParser) (parse s ch)))

;;
;; config:
;;

(def ktjkii "https://ws.nls.fi/ktjkii/wfs/wfs")
(def maasto "https://ws.nls.fi/maasto/wfs")
(def nearestfeature "https://ws.nls.fi/maasto/nearestfeature")

(def wms-url (str (env/value :geoserver :host) (env/value :geoserver :wms :path)))

(def- auth
  (let [conf (env/value :nls)]
    {:raster        [(:username (:raster conf))     (:password (:raster conf))]
     :kiinteisto    [(:username (:kiinteisto conf)) (:password (:kiinteisto conf))]
     ktjkii         [(:username (:ktjkii conf))     (:password (:ktjkii conf))]
     maasto         [(:username (:maasto conf))     (:password (:maasto conf))]
     nearestfeature [(:username (:maasto conf))     (:password (:maasto conf))]}))

(def rekisteriyksikkolaji {"0" {:fi "(Tuntematon)" :sv "(Ok\u00e4nd)"}
                           "1" {:fi "Tila" :sv "L\u00e4genhet"}
                           "3" {:fi "Valtion mets\u00e4maa" :sv "Statens skogsmark"}
                           "4" {:fi "Lunastusyksikk\u00f6" :sv "Inl\u00f6sningsenhet"}
                           "5" {:fi "Kruununkalastus" :sv "Kronofiske"}
                           "6" {:fi "Yleiseen tarpeeseen erotettu alue" :sv "Omr\u00e5de avskilt f\u00f6r allm\u00e4nt behov"}
                           "7" {:fi "Erillinen vesij\u00e4tt\u00f6" :sv "Frist\u00e5ende tillandning"}
                           "8" {:fi "Yleinen vesialue" :sv "Allm\u00e4nt vattenomr\u00e5de"}
                           "9" {:fi "Yhteinen alue" :sv "Samf\u00e4llt omr\u00e5de"}
                           "10" {:fi "Yhteismets\u00e4" :sv "Samf\u00e4lld skog"}
                           "11" {:fi "Tie- tai liit\u00e4nn\u00e4isalue" :sv "V\u00e4g- eller biomr\u00e5de"}
                           "12" {:fi "Lakkautettu tie- tai liit\u00e4nn\u00e4isalue" :sv "Indraget v\u00e4g- eller biomr\u00e5de"}
                           "13" {:fi "Tontti" :sv "Tomt"}
                           "14" {:fi "Yleinen alue" :sv "Allm\u00e4nt omr\u00e5de"}
                           "15" {:fi "Selvitt\u00e4m\u00e4t\u00f6n yhteinen alue" :sv "Outrett samf\u00e4llt omr\u00e5de"}
                           "17" {:fi "Yhteinen vesialue" :sv "Samf\u00e4llt vattenomr\u00e5de"}
                           "18" {:fi "Yhteinen maa-alue" :sv "Samf\u00e4llt jordomr\u00e5de"}
                           "19" {:fi "Suojelualuekiinteist\u00f6" :sv "Skyddsomr\u00e5desfastighet"}
                           "21" {:fi "Tie- tai liit\u00e4nn\u00e4isalue tieoikeudella" :sv "V\u00e4g- eller biomr\u00e5de med v\u00e4gr\u00e4tt"}
                           "22" {:fi "Tie- tai liit\u00e4nn\u00e4isalue omistusoikeudella" :sv "V\u00e4g- eller biomr\u00e5de med \u00e4gander\u00e4tt"}
                           "23" {:fi "Yleisen alueen lis\u00e4osa" :sv "Allm\u00e4nna omr\u00e5dets till\u00e4ggsomr\u00e5de"}
                           "24" {:fi "Tuntematon kunnan rekisteriyksikk\u00f6" :sv "Ok\u00e4nd kommunens registerenhet"}
                           "25" {:fi "Yhteinen erityinen etuus" :sv "Gemensam s\u00e4rskild f\u00f6rm\u00e5n"}
                           "99" {:fi "Selvitt\u00e4m\u00e4t\u00f6n alue" :sv "Outrett omr\u00e5de"}})

;;
;; DSL to WFS queries:
;;

(defn query [attrs & e]
  (str
    "<wfs:GetFeature version=\"1.1.0\"
            xmlns:oso=\"http://xml.nls.fi/Osoitteet/Osoitepiste/2011/02\"
            xmlns:ktjkiiwfs=\"http://xml.nls.fi/ktjkiiwfs/2010/02\"
            xmlns:wfs=\"http://www.opengis.net/wfs\"
            xmlns:gml=\"http://www.opengis.net/gml\"
            xmlns:ogc=\"http://www.opengis.net/ogc\"
            xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
            xsi:schemaLocation=\"http://www.opengis.net/wfs http://schemas.opengis.net/wfs/1.1.0/wfs.xsd\">
          <wfs:Query" (apply str (map (fn [[k v]] (format " %s=\"%s\"" k v)) attrs)) ">"
            (apply str e)
       "</wfs:Query></wfs:GetFeature>"))

(defn ogc-sort-by
  ([property-names]
    (ogc-sort-by property-names "desc"))
  ([property-names order]
    (let [sort-properties (apply str (map #(str "<ogc:SortProperty><ogc:PropertyName>" % "</ogc:PropertyName></ogc:SortProperty>") property-names))]
      (str "<ogc:SortBy>"
           sort-properties
           "<ogc:SortOrder>" (s/upper-case order) "</ogc:SortOrder>"
           "</ogc:SortBy>"))))

(defn ogc-filter [& e]
  (str "<ogc:Filter>" (apply str e) "</ogc:Filter>"))

(defn ogc-and [& e]
  (str "<ogc:And>" (apply str e) "</ogc:And>"))

(defn ogc-or [& e]
  (str "<ogc:Or>" (apply str e) "</ogc:Or>"))

(defn intersects [& e]
  (str "<ogc:Intersects>" (apply str e) "</ogc:Intersects>"))

(defn within [& e]
  (str "<DWithin>" (apply str e) "</DWithin>"))

(defn distance [distance]
  (format "<Distance units=\"m\">%s</Distance>" distance))

(defn point [x y]
  (format "<gml:Point><gml:pos>%s %s</gml:pos></gml:Point>" x y))

(defn line [c]
  (format "<gml:LineString><gml:posList srsDimension=\"2\">%s</gml:posList></gml:LineString>" (s/join " " c)))

(defn polygon [c]
  (format "<gml:Polygon><gml:outerBoundaryIs><gml:LinearRing><gml:posList srsDimension=\"2\">%s</gml:posList></gml:LinearRing></gml:outerBoundaryIs></gml:Polygon>" (s/join " " c)))

(defn property-name [prop-name]
  (str "<ogc:PropertyName>" prop-name "</ogc:PropertyName>"))

(defn property-filter [filter-name prop-name value]
  (str
    "<ogc:" filter-name " wildCard=\"*\" singleChar=\"?\" escape=\"!\" matchCase=\"false\">"
    (property-name prop-name)
    "<ogc:Literal>" value "</ogc:Literal>"
    "</ogc:" filter-name ">"))

(defn property-is-like [prop-name value]
  (property-filter "PropertyIsLike" prop-name value))

(defn property-is-equal [prop-name value]
  (property-filter "PropertyIsEqualTo" prop-name value))

(defn property-is-less [prop-name value]
  (property-filter "PropertyIsLessThan" prop-name value))

(defn property-is-greater [prop-name value]
  (property-filter "PropertyIsGreaterThan" prop-name value))

(defn property-is-between [name lower-value upper-value]
  (str
    "<ogc:PropertyIsBetween wildCard=\"*\" singleChar=\"?\" escape=\"!\" matchCase=\"false\">"
    (property-name name)
    "<ogc:LowerBoundary>" lower-value "</ogc:LowerBoundary>"
    "<ogc:UpperBoundary>" upper-value "</ogc:UpperBoundary>"
    "</ogc:PropertyIsBetween>"))

;;
;; Helpers for result parsing:
;;

(defn- address-part [feature part]
  (first (xml-> feature :oso:Osoitenimi part text)))

(defn feature-to-address [feature]
  (let [[x y] (s/split (address-part feature :oso:sijainti) #" ")]
    {:street (address-part feature :oso:katunimi)
     :number (address-part feature :oso:katunumero)
     :municipality (address-part feature :oso:kuntatunnus)
     :name {:fi (address-part feature :oso:kuntanimiFin)
            :sv (address-part feature :oso:kuntanimiSwe)}
     :location {:x x :y y}}))

(defn feature-to-simple-address-string [feature]
  (let [{street :street number :number {fi :fi sv :sv} :name} (feature-to-address feature)]
    (str street " " number ", " fi)))

(defn feature-to-address-string [[street number city]]
  (if (s/blank? city)
    (fn [feature]
      (let [{street :street {fi :fi} :name} (feature-to-address feature)]
        (str street ", " fi)))
    (fn [feature]
      (let [{street :street number :number {fi :fi sv :sv} :name} (feature-to-address feature)
            municipality-name (if (ss/starts-with-i fi city) fi sv)]
        (str street " " number ", " municipality-name)))))

(defn feature-to-position [feature]
  (let [[x y] (s/split (first (xml-> feature :ktjkiiwfs:PalstanTietoja :ktjkiiwfs:tunnuspisteSijainti :gml:Point :gml:pos text)) #" ")]
    {:x x :y y}))

(defn extract-coordinates [ring]
  (s/replace (first (xml-> ring :gml:LinearRing :gml:posList text)) #"(\d+\.*\d*)\s+(\d+\.*\d*)\s+" "$1 $2, "))

(defn feature-to-area [feature]
  (when feature
    (let [polygonpatch (first (xml-> feature :ktjkiiwfs:PalstanTietoja :ktjkiiwfs:sijainti :gml:Surface :gml:patches :gml:PolygonPatch))
          exterior (extract-coordinates (first (xml-> polygonpatch :gml:exterior)))
          interiors (map extract-coordinates (xml-> polygonpatch :gml:interior))]
    {:kiinttunnus (first (xml-> feature :ktjkiiwfs:PalstanTietoja :ktjkiiwfs:rekisteriyksikonKiinteistotunnus text))
     :wkt (str "POLYGON ((" exterior ")" (apply str(map #(str ",(" % ")") interiors)) ")")
     })))

(defn feature-to-property-id [feature]
  (when feature
    {:kiinttunnus (first (xml-> feature :ktjkiiwfs:PalstanTietoja :ktjkiiwfs:rekisteriyksikonKiinteistotunnus text))}))

;; http://www.maanmittauslaitos.fi/node/7365, i.e. "oso:katunumero" and "oso:jarjestysnumero" explained
(defn feature-to-address-details [feature]
  (when feature
    (let [katunumero (first (xml-> feature :oso:Osoitepiste :oso:osoite :oso:Osoite :oso:katunumero text))]
      {:street (first (xml-> feature :oso:Osoitepiste :oso:osoite :oso:Osoite :oso:katunimi text))
       :number (if (or (nil? katunumero) (= "0" katunumero))
                 (first (xml-> feature :oso:Osoitepiste :oso:osoite :oso:Osoite :oso:jarjestysnumero text))
                 katunumero)
       :municipality (first (xml-> feature :oso:Osoitepiste :oso:kuntatunnus text))
       :name {:fi (first (xml-> feature :oso:Osoitepiste :oso:kuntanimiFin text))
              :sv (first (xml-> feature :oso:Osoitepiste :oso:kuntanimiSwe text))}})))

(defn feature-to-property-info [feature]
  (when feature
    (let [[x y] (s/split (first (xml-> feature :ktjkiiwfs:RekisteriyksikonTietoja :ktjkiiwfs:rekisteriyksikonPalstanTietoja :ktjkiiwfs:RekisteriyksikonPalstanTietoja :ktjkiiwfs:tunnuspisteSijainti :gml:Point :gml:pos text)) #" ")]
    {:rekisteriyksikkolaji (let [id (first (xml-> feature :ktjkiiwfs:RekisteriyksikonTietoja :ktjkiiwfs:rekisteriyksikkolaji text))]
                             {:id id
                              :selite (rekisteriyksikkolaji id)})
     :kiinttunnus (first (xml-> feature :ktjkiiwfs:RekisteriyksikonTietoja :ktjkiiwfs:kiinteistotunnus text))
     :x x
     :y y})))

(defn- ->features [s parse-fn & [encoding]]
  (when s
    (-> (if encoding (.getBytes s encoding) (.getBytes s))
      java.io.ByteArrayInputStream.
      (xml/parse parse-fn)
      zip/xml-zip)))

;;
;; Executing HTTP calls to Maanmittauslaitos:
;;

(def- http-method {:post [http/post :body]
                   :get  [http/get  :query-params]})

(defn- exec-http [http-fn url request]
  (try
    (let [{status :status body :body} (http-fn url request)]
      (if (= status 200)
        [:ok body]
        [:error status body]))
    (catch Exception e
      [:failure e])))

(defn- exec [method url q]
  (let [[http-fn param-key] (method http-method)
        timeout (env/value :http-client :conn-timeout)
        request {:throw-exceptions false
                 :basic-auth (auth url)
                 param-key q}
        task (future* (exec-http http-fn url request))
        [status data error-body] (deref task timeout [:timeout])
        error-text (-> error-body  (ss/replace #"[\r\n]+" " ") (ss/limit 400 "..."))]
    (condp = status
      :timeout (do (errorf "wfs timeout: url=%s" url) nil)
      :error   (do
                 (case data
                   400 (errorf "wfs status 400 Bad Request '%s', url=%s, response body=%s" (ss/limit (str q) 220 "...") url error-text)
                   (errorf "wfs status %s: url=%s, response body=%s" data url error-text))
                 nil)
      :failure (do (errorf data "wfs failure: url=%s" url) nil)
      :ok      (let [features (-> data
                                (s/replace "UTF-8" "ISO-8859-1")
                                (->features sxml/startparse-sax-no-doctype "ISO-8859-1"))]
                 (xml-> features :gml:featureMember)))))

(defn post [url q]
  (exec :post url q))

(defn wms-get
  "WMS query with error handling. Returns response body or nil."
  [url query-params]
  (let [{:keys [status body]} (http/get url {:query-params query-params})
        error (when (ss/contains? body "ServiceException")
                (-> body ss/trim
                  (->features startparse-sax-non-validating "UTF-8")
                  (xml-> :ServiceException)
                  first text ss/trim))]
    (if (or (not= 200 status) error)
      (errorf "Failed to get %s (status %s): %s" url status (logging/sanitize 1000 error))
      body)))

;;
;; Public queries:
;;

(defn address-by-point [x y]
  (exec :get nearestfeature {:NAMESPACE "xmlns(oso=http://xml.nls.fi/Osoitteet/Osoitepiste/2011/02)"
                             :TYPENAME "oso:Osoitepiste"
                             :COORDS (str x "," y ",EPSG:3067")
                             :SRSNAME "EPSG:3067"
                             :MAXFEATURES "1"
                             :BUFFER "500"}))

(defn property-id-by-point [x y]
  (post ktjkii
    (query {"typeName" "ktjkiiwfs:PalstanTietoja" "srsName" "EPSG:3067"}
      (property-name "ktjkiiwfs:rekisteriyksikonKiinteistotunnus")
      (property-name "ktjkiiwfs:tunnuspisteSijainti")
      (ogc-filter
        (intersects
          (property-name "ktjkiiwfs:sijainti")
          (point x y))))))

(defn point-by-property-id [property-id]
  (post ktjkii
    (query {"typeName" "ktjkiiwfs:PalstanTietoja" "srsName" "EPSG:3067"}
      (property-name "ktjkiiwfs:rekisteriyksikonKiinteistotunnus")
      (property-name "ktjkiiwfs:tunnuspisteSijainti")
      (ogc-filter
        (property-is-equal "ktjkiiwfs:rekisteriyksikonKiinteistotunnus" property-id)))))

(defn area-by-property-id [property-id]
  (post ktjkii
    (query {"typeName" "ktjkiiwfs:PalstanTietoja" "srsName" "EPSG:3067"}
      (property-name "ktjkiiwfs:rekisteriyksikonKiinteistotunnus")
      (property-name "ktjkiiwfs:sijainti")
      (ogc-filter
        (property-is-equal "ktjkiiwfs:rekisteriyksikonKiinteistotunnus" property-id)))))

(defn property-info-by-radius [x y radius]
  (post ktjkii
    (query {"typeName" "ktjkiiwfs:RekisteriyksikonTietoja" "srsName" "EPSG:3067"}
      (property-name "ktjkiiwfs:rekisteriyksikkolaji")
      (property-name "ktjkiiwfs:kiinteistotunnus")
      (property-name "ktjkiiwfs:rekisteriyksikonPalstanTietoja")
      (ogc-filter
        (within
          (property-name "ktjkiiwfs:rekisteriyksikonPalstanTietoja/ktjkiiwfs:RekisteriyksikonPalstanTietoja/ktjkiiwfs:sijainti")
          (point x y)
          (distance radius))))))

(defn property-info-by-point [x y]
  (post ktjkii
    (query {"typeName" "ktjkiiwfs:RekisteriyksikonTietoja" "srsName" "EPSG:3067"}
      (property-name "ktjkiiwfs:rekisteriyksikkolaji")
      (property-name "ktjkiiwfs:kiinteistotunnus")
      (property-name "ktjkiiwfs:rekisteriyksikonPalstanTietoja")
      (ogc-filter
        (intersects
          (property-name "ktjkiiwfs:rekisteriyksikonPalstanTietoja/ktjkiiwfs:RekisteriyksikonPalstanTietoja/ktjkiiwfs:sijainti")
          (point x y))))))

(defn property-info-by-line [l]
  (post ktjkii
    (query {"typeName" "ktjkiiwfs:RekisteriyksikonTietoja" "srsName" "EPSG:3067"}
      (property-name "ktjkiiwfs:rekisteriyksikkolaji")
      (property-name "ktjkiiwfs:kiinteistotunnus")
      (property-name "ktjkiiwfs:rekisteriyksikonPalstanTietoja")
      (ogc-filter
        (intersects
          (property-name "ktjkiiwfs:rekisteriyksikonPalstanTietoja/ktjkiiwfs:RekisteriyksikonPalstanTietoja/ktjkiiwfs:sijainti")
          (line l))))))

(defn property-info-by-polygon [p]
  (post ktjkii
    (query {"typeName" "ktjkiiwfs:RekisteriyksikonTietoja" "srsName" "EPSG:3067"}
      (property-name "ktjkiiwfs:rekisteriyksikkolaji")
      (property-name "ktjkiiwfs:kiinteistotunnus")
      (property-name "ktjkiiwfs:rekisteriyksikonPalstanTietoja")
      (ogc-filter
        (intersects
          (property-name "ktjkiiwfs:rekisteriyksikonPalstanTietoja/ktjkiiwfs:RekisteriyksikonPalstanTietoja/ktjkiiwfs:sijainti")
          (polygon p))))))

(defn getcapabilities [request]
  (let [host (env/value :geoserver :host) ; local IP from Chef environment
        path (env/value :geoserver :wms :path)]
    (assert (and host path))
    (:body (http/get (str host path) {:query-params {"version" "1.1.1", "request" "GetCapabilities"}}))))

(defn capabilities-to-layers [capabilities]
  (when capabilities
    (xml-> (->features (s/trim capabilities) startparse-sax-non-validating "UTF-8") :Capability :Layer :Layer)))

(defn layer-to-name [layer]
  (first (xml-> layer :Name text)))

(defn- plan-info-config [municipality]
  (let [k (keyword municipality)]
    {:url    (or (env/value :plan-info k :url) wms-url)
     :layers (or (env/value :plan-info k :layers) (str municipality "_asemakaavaindeksi"))
     :format (or (env/value :plan-info k :format) "application/vnd.ogc.gml")}))

(defn plan-info-by-point [x y municipality]
  (let [bbox [(- (util/->double x) 128) (- (util/->double y) 128) (+ (util/->double x) 128) (+ (util/->double y) 128)]
        {:keys [url layers format]} (plan-info-config municipality)
        query {"REQUEST" "GetFeatureInfo"
               "EXCEPTIONS" "application/vnd.ogc.se_xml"
               "SERVICE" "WMS"
               "INFO_FORMAT" format
               "QUERY_LAYERS" layers
               "FEATURE_COUNT" "50"
               "Layers" layers
               "WIDTH" "256"
               "HEIGHT" "256"
               "format" "image/png"
               "styles" ""
               "srs" "EPSG:3067"
               "version" "1.1.1"
               "x" "128"
               "y" "128"
               "BBOX" (s/join "," bbox)}]
    (wms-get url query)))

;;; Mikkeli is special because it was done first and they use Bentley WMS
(defn gfi-to-features-mikkeli [gfi _]
  (when gfi
    (xml-> (->features gfi startparse-sax-non-validating) :FeatureKeysInLevel :FeatureInfo :FeatureKey)))

(defn feature-to-feature-info-mikkeli  [feature]
  (when feature
    {:id (first (xml-> feature :property (attr= :name "ID") text))
     :kaavanro (first (xml-> feature :property (attr= :name "Kaavanro") text))
     :kaavalaji (first (xml-> feature :property (attr= :name "Kaavalaji") text))
     :kasitt_pvm (first (xml-> feature :property (attr= :name "Kasitt_pvm") text))
     :linkki (first (xml-> feature :property (attr= :name "Linkki") text))
     :type "bentley"}))

(defn gfi-to-features-sito [gfi municipality]
  (when gfi
    (xml-> (->features gfi startparse-sax-non-validating "UTF-8") :gml:featureMember (keyword (str "lupapiste:" municipality "_asemakaavaindeksi")))))

(defn feature-to-feature-info-sito  [feature]
  (when feature
    {:id (first (xml-> feature :lupapiste:TUNNUS text))
     :kuntanro (first (xml-> feature :lupapiste:KUNTA_ID text))
     :kaavanro (first (xml-> feature :lupapiste:TUNNUS text))
     :vahvistett_pvm (first (xml-> feature :lupapiste:VAHVISTETT text))
     :linkki (first (xml-> feature :lupapiste:LINKKI text))
     :type "sito"}))

(defn feature-to-feature-info-liiteri-ak  [feature]
  (when feature
    {:id (first (xml-> feature :lupapiste:tunnus text))
     :kuntanro (first (xml-> feature :lupapiste:kuntaid text))
     :kunta (first (xml-> feature :lupapiste:kunta text))
     :kaavanro (first (xml-> feature :lupapiste:tunnus text))
     :linkki (first (xml-> feature :lupapiste:merkinnat text))
     :type "liiteri-ak"}))

(defn general-plan-info-by-point [x y]
  (let [bbox [(- (util/->double x) 128) (- (util/->double y) 128) (+ (util/->double x) 128) (+ (util/->double y) 128)]
        query {"REQUEST" "GetFeatureInfo"
               "EXCEPTIONS" "application/vnd.ogc.se_xml"
               "SERVICE" "WMS"
               "INFO_FORMAT" "application/vnd.ogc.gml"
               "QUERY_LAYERS" "yleiskaavaindeksi,yleiskaavaindeksi_poikkeavat"
               "FEATURE_COUNT" "50"
               "Layers" "yleiskaavaindeksi,yleiskaavaindeksi_poikkeavat"
               "WIDTH" "256"
               "HEIGHT" "256"
               "format" "image/png"
               "styles" ""
               "srs" "EPSG:3067"
               "version" "1.1.1"
               "x" "128"
               "y" "128"
               "BBOX" (s/join "," bbox)}]
    (wms-get wms-url query)))

(defn gfi-to-general-plan-features [gfi]
  (when gfi
    (let [info (->features gfi startparse-sax-non-validating "UTF-8")]
      (clojure.set/union
        (xml-> info :gml:featureMember :lupapiste:yleiskaavaindeksi)
        (xml-> info :gml:featureMember :lupapiste:yleiskaavaindeksi_poikkeavat)))))

(defn general-plan-feature-to-feature-info  [feature]
  (when feature
    {:id (first (xml-> feature :lupapiste:tunnus text))
     :nimi (first (xml-> feature :lupapiste:nimi text))
     :pvm (first (xml-> feature :lupapiste:pvm text))
     :tyyppi (first (xml-> feature :lupapiste:tyyppi text))
     :oikeusvaik (first (xml-> feature :lupapiste:oikeusvaik text))
     :lisatieto (first (xml-> feature :lupapiste:lisatieto text))
     :linkki (first (xml-> feature :lupapiste:linkki text))
     :type "yleiskaava"}))

(defn get-rekisteriyksikontietojaFeatureAddress []
  (let [url-for-get-ktj-capabilities (str ktjkii "?service=WFS&request=GetCapabilities&version=1.1.0")
        namespace-stripped-xml (reader/strip-xml-namespaces (reader/get-xml url-for-get-ktj-capabilities (get auth ktjkii) false))
        selector [:WFS_Capabilities :OperationsMetadata [:Operation (enlive/attr= :name "DescribeFeatureType")] :DCP :HTTP [:Get]]
        attribute-to-get :xlink:href]
    (sxml/select1-attribute-value namespace-stripped-xml selector attribute-to-get)))

(defn rekisteritiedot-xml [rekisteriyksikon-tunnus]
  (if (env/feature? :disable-ktj-on-create)
    (infof "ktj-client is disabled - not getting rekisteritiedot for %s" rekisteriyksikon-tunnus)
    (let [url (str (get-rekisteriyksikontietojaFeatureAddress) "SERVICE=WFS&REQUEST=GetFeature&VERSION=1.1.0&NAMESPACE=xmlns%28ktjkiiwfs%3Dhttp%3A%2F%2Fxml.nls.fi%2Fktjkiiwfs%2F2010%2F02%29&TYPENAME=ktjkiiwfs%3ARekisteriyksikonTietoja&PROPERTYNAME=ktjkiiwfs%3Akiinteistotunnus%2Cktjkiiwfs%3Aolotila%2Cktjkiiwfs%3Arekisteriyksikkolaji%2Cktjkiiwfs%3Arekisterointipvm%2Cktjkiiwfs%3Animi%2Cktjkiiwfs%3Amaapintaala%2Cktjkiiwfs%3Avesipintaala&FEATUREID=FI.KTJkii-RekisteriyksikonTietoja-" (codec/url-encode rekisteriyksikon-tunnus) "&SRSNAME=EPSG%3A3067&MAXFEATURES=100&RESULTTYPE=results")
          ktj-xml (reader/get-xml url (get auth ktjkii) false)
          features (-> ktj-xml reader/strip-xml-namespaces sxml/xml->edn)]
      (get-in features [:FeatureCollection :featureMember :RekisteriyksikonTietoja]))))


;;
;; Raster images:
;;
(defn raster-images [request service]
  (let [layer (or (get-in request [:params :LAYER])
                  (get-in request [:params :layer]))]
    (case service
      "nls" (http/get "https://ws.nls.fi/rasteriaineistot/image"
              {:query-params (:params request)
               :headers {"accept-encoding" (get-in request [:headers "accept-encoding"])}
               :basic-auth (:raster auth)
               :as :stream})
      "wms" (http/get wms-url
              {:query-params (:params request)
               :headers {"accept-encoding" (get-in request [:headers "accept-encoding"])}
               :as :stream})
      "wmts" (let [{:keys [username password]} (env/value :wmts :raster)
                   url-part (case layer
                              "taustakartta" "maasto"
                              "kiinteistojaotus" "kiinteisto"
                              "kiinteistotunnukset" "kiinteisto")
                   wmts-url (str "https://karttakuva.maanmittauslaitos.fi/" url-part "/wmts")]
               (http/get wmts-url
                         {:query-params (:params request)
                          :headers {"accept-encoding" (get-in request [:headers "accept-encoding"])}
                          :basic-auth [username password]
                          :as :stream}))
      "plandocument" (let [id (get-in request [:params :id])]
                       (assert (ss/numeric? id))
                       (http/get (str "http://194.28.3.37/maarays/" id "x.pdf")
                         {:query-params (:params request)
                          :headers {"accept-encoding" (get-in request [:headers "accept-encoding"])}
                          :as :stream})))))
