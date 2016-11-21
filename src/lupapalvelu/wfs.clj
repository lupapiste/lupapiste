(ns lupapalvelu.wfs
  (:require [taoensso.timbre :as timbre :refer [trace debug info infof warn warnf error errorf]]
            [ring.util.codec :as codec]
            [net.cgrand.enlive-html :as enlive]
            [sade.http :as http]
            [clojure.string :as s]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :refer [xml-> xml1-> text text= attr=]]
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

(def ktjkii "https://ws.nls.fi/ktjkii/wfs-2015/wfs")

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

(def common-namespaces
  {"xmlns:wfs" "http://www.opengis.net/wfs"
   "xmlns:gml" "http://www.opengis.net/gml"
   "xmlns:ogc" "http://www.opengis.net/ogc"
   "xmlns:xsi" "http://www.w3.org/2001/XMLSchema-instance"})

(def nls-namespaces (merge common-namespaces {"xmlns:oso" "http://xml.nls.fi/Osoitteet/Osoitepiste/2011/02"
                                              "xmlns:ktjkiiwfs" "http://xml.nls.fi/ktjkiiwfs/2010/02"}))

(def krysp-namespaces (merge common-namespaces {"xmlns:mkos" "http://www.paikkatietopalvelu.fi/gml/opastavattiedot/osoitteet"
                                                "xmlns:yht" "http://www.paikkatietopalvelu.fi/gml/yhteiset"}))

(defn query [attrs & e]
  (let [type-name (or (:typeName attrs) (get attrs "typeName"))
        ns-prefix (first (ss/split (name type-name) #":"))
        xml-namespaces (if (some #(ss/ends-with % ns-prefix) (keys krysp-namespaces)) krysp-namespaces nls-namespaces)]
    (sxml/element-to-string
      {:tag :wfs:GetFeature
       :attrs (merge {:version "1.1.0"}
                xml-namespaces
                {:xsi:schemaLocation "http://www.opengis.net/wfs http://schemas.opengis.net/wfs/1.1.0/wfs.xsd"})
       :content [{:tag :wfs:Query
                  :attrs attrs
                  :content (if (string? e) [e] e)}]})))

(defn trimble-kaavamaaraykset-point-to-bbox [x y]
(str "" (apply str x) "," (apply str y) " " (apply str (str (+ (util/->double (str x)) 0.01))) "," (apply str (str (+ (util/->double y) 0.01))) ) )


; tm35fin to trimble wfs coordinate system using params from properties file

(defn trimble-kaavamaaraykset-muunnax [x y municipality]
  (let [k (keyword municipality)]
    (let [[a b c d e] (s/split (env/value :trimble-kaavamaaraykset k :muunnosparams :x) #",")]
      (format "%s" (+ (util/->double a) (* (util/->double b) (- (util/->double x) (util/->double c))) (* (util/->double d) (- (util/->double y) (util/->double e))))))))

(defn trimble-kaavamaaraykset-muunnay [x y municipality]
  (let [k (keyword municipality)]
    (let [[a b c d e] (s/split (env/value :trimble-kaavamaaraykset k :muunnosparams :y) #",")]
      (format "%s" (+ (util/->double a) (* (util/->double b) (- (util/->double y) (util/->double c))) (* (util/->double d) (- (util/->double x) (util/->double e))))))))

(defn trimble-kaavamaaraykset-request [x y municipality]
  (let [bbox [(trimble-kaavamaaraykset-point-to-bbox (trimble-kaavamaaraykset-muunnax x y municipality) (trimble-kaavamaaraykset-muunnay x y municipality))]]
  (str "<?xml version='1.0' encoding='utf-8'?><GetFeature xmlns='http://www.opengis.net/wfs' xmlns:akaava='http://www.paikkatietopalvelu.fi/gml/asemakaava' xmlns:ogc='http://www.opengis.net/ogc' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xmlns:gml='http://www.opengis.net/gml' service='WFS' version='1.0.0' outputFormat='GML2' maxFeatures='1' handle='' ><Query typeName='akaava:Kaava' srsName='EPSG:3877' ><ogc:Filter><ogc:BBOX><ogc:PropertyName>voimassaolosijainti</ogc:PropertyName><gml:Box srsName='EPSG:3877'><gml:coordinates>" (apply str bbox) "</gml:coordinates></gml:Box></ogc:BBOX></ogc:Filter></Query></GetFeature>" )))

(defn ogc-sort-by
  ([property-names]
    (ogc-sort-by property-names "desc"))
  ([property-names order]
    {:tag :ogc:SortBy
     :content (conj
                (mapv (fn [property-name] {:tag :ogc:SortProperty :content [{:tag :ogc:PropertyName :content [property-name]}]}) property-names)
                {:tag :ogc:SortOrder :content [{:tag :ogc:SortOrder :content [(s/upper-case order)]}]})}))

(defn ogc-filter [& e] {:tag :ogc:Filter :content e})

(defn ogc-bbox [& e] {:tag :ogc:BBOX :content e})

(defn ogc-and [& e] {:tag :ogc:And :content e})

(defn ogc-or [& e] {:tag :ogc:Or :content e})

(defn intersects [& e] {:tag :ogc:Intersects :content e})

(defn within [& e] {:tag :ogc:DWithin :content e})

(defn box [srs coords]
  {:tag :gml:Box
   :attrs {:srsName srs}
   :content (ss/join " " coords)})

(defn envelope [srs lower-pair upper-pair]
  {:tag :gml:Envelope :attrs {:srsName srs}
   :content [{:tag :gml:lowerCorner :content (ss/join " " lower-pair)}
             {:tag :gml:upperCorner :content (ss/join " " upper-pair)}]})

(defn distance [distance] {:tag :ogc:Distance :attrs {:units "m"} :content [distance]})

(defn point [x y]
  {:tag :gml:Point :attrs {:srsDimension "2"}
   :content [{:tag :gml:pos :content [(str x \space y)]}]})

(defn line [c]
  {:tag :gml:LineString
   :content [{:tag :gml:posList :attrs {:srsDimension "2"} :content [(s/join " " c)]}]})

(defn polygon [c]
  {:tag :gml:Polygon
   :content [{:tag :gml:outerBoundaryIs
              :content [{:tag :gml:LinearRing
                         :content [{:tag :gml:posList :attrs {:srsDimension "2"} :content [(s/join " " c)]}]}]}]})

(defn property-name [prop-name] {:tag :ogc:PropertyName :content [prop-name]})

(defn property-filter [filter-name prop-name value & attrs]
  (let [attributes (if (map? (first attrs) )
                     (first attrs)
                     (apply hash-map (partition 2 attrs)))]
    {:tag (keyword (str "ogc:" (name filter-name)))
     :attrs attributes
     :content [(property-name prop-name)
               {:tag :ogc:Literal :content [value]}]}))

(defn property-is-like [prop-name value]
  (property-filter "PropertyIsLike" prop-name value
    (merge {"wildCard" "*", "singleChar" "?", "escape" "\\", "matchCase" "false"})))

(defn property-is-equal [prop-name value]
  (property-filter "PropertyIsEqualTo" prop-name value))

(defn property-in [prop-name values]
  (if (> (count values) 1)
    (apply ogc-or (map (partial property-is-equal prop-name) values))
    (property-is-equal prop-name (first values))))

(defn property-is-less [prop-name value]
  (property-filter "PropertyIsLessThan" prop-name value))

(defn property-is-greater [prop-name value]
  (property-filter "PropertyIsGreaterThan" prop-name value))

(defn property-is-between [name lower-value upper-value]
  {:tag :ogc:PropertyIsBetween
   :content [(property-name name)
             {:tag :ogc:LowerBoundary :content [lower-value]}
             {:tag :ogc:UpperBoundary :content [upper-value]}]})

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

(defn extract-coordinates [ring]
  (s/replace (first (xml-> ring :gml:LinearRing :gml:posList text)) #"(\d+\.*\d*)\s+(\d+\.*\d*)\s+" "$1 $2, "))

(defn property-borders-wkt [feature]
  (when feature
    (let [path (if (seq (xml-> feature :ktjkiiwfs:PalstanTietoja))
                 [:ktjkiiwfs:PalstanTietoja :ktjkiiwfs:sijainti :gml:Surface :gml:patches :gml:PolygonPatch]
                 [:ktjkiiwfs:RekisteriyksikonTietoja :ktjkiiwfs:rekisteriyksikonPalstanTietoja :ktjkiiwfs:RekisteriyksikonPalstanTietoja :ktjkiiwfs:sijainti :gml:Surface :gml:patches :gml:PolygonPatch])
          polygonpatch (first (apply xml-> (cons feature path) ))
          exterior (extract-coordinates (first (xml-> polygonpatch :gml:exterior)))
          interiors (map extract-coordinates (xml-> polygonpatch :gml:interior))]
      (str "POLYGON((" exterior ")" (ss/join (map #(str ",(" % ")") interiors)) ")"))))

(defn feature-to-location [feature]
  (when feature
    (let [[x y] (s/split (first (xml-> feature :ktjkiiwfs:PalstanTietoja :ktjkiiwfs:tunnuspisteSijainti :gml:Point :gml:pos text)) #" ")]
      {:kiinttunnus (first (xml-> feature :ktjkiiwfs:PalstanTietoja :ktjkiiwfs:rekisteriyksikonKiinteistotunnus text))
       :x x
       :y y
       :wkt (property-borders-wkt feature)})))

(defn feature-to-property-id [feature]
  (when feature
    {:kiinttunnus (first (xml-> feature :ktjkiiwfs:PalstanTietoja :ktjkiiwfs:rekisteriyksikonKiinteistotunnus text))}))

;; http://www.maanmittauslaitos.fi/node/7365, i.e. "oso:katunumero" and "oso:jarjestysnumero" explained
(defn feature-to-address-details [lang feature]
  (when (seq feature)
    (let [lang3 (get {"fi" "fin", "sv" "swe"} lang "fin")
          osoitteet (clojure.data.zip.xml/xml-> feature :oso:Osoitepiste :oso:osoite :oso:Osoite)
          osoite (first (or
                         (seq (filter #(xml1-> % :oso:kieli (text= lang3)) osoitteet))
                         osoitteet))
          katunumero (xml1-> osoite :oso:katunumero text)
          xy (ss/split (xml1-> feature :oso:Osoitepiste :oso:sijainti :gml:Point :gml:pos text) #"\s")]
      {:street (xml1-> osoite :oso:katunimi text)
       :number (if (or (nil? katunumero) (= "0" katunumero))
                 (xml1-> osoite :oso:jarjestysnumero text)
                 katunumero)
       :municipality (xml1-> feature :oso:Osoitepiste :oso:kuntatunnus text)
       :x (util/->double (first xy))
       :y (util/->double (second xy))
       :name {:fi (xml1-> feature :oso:Osoitepiste :oso:kuntanimiFin text)
              :sv (xml1-> feature :oso:Osoitepiste :oso:kuntanimiSwe text)}})))

(defn krysp-to-address-details [lang feature]
  (when (seq feature)
    (let [street-by-lang (xml-> feature :mkos:Osoite :yht:osoitenimi :yht:teksti (attr= :xml:lang lang) text)
          street (if (seq street-by-lang)
                   street-by-lang
                   (xml-> feature :mkos:Osoite :yht:osoitenimi :yht:teksti text))
          xy (ss/split (first (xml-> feature :mkos:Osoite :yht:pistesijainti :gml:Point :gml:pos text)) #"\s")]
      {:street (first street)
       :number (first (xml-> feature :mkos:Osoite :yht:osoitenumero text))
       :municipality (first (xml-> feature :mkos:Osoite :yht:kunta text))
       :x (util/->double (first xy))
       :y (util/->double (second xy))})))

(defn feature-to-property-info [feature]
  (when (seq feature)
    (let [[x y] (s/split (first (xml-> feature :ktjkiiwfs:RekisteriyksikonTietoja :ktjkiiwfs:rekisteriyksikonPalstanTietoja :ktjkiiwfs:RekisteriyksikonPalstanTietoja :ktjkiiwfs:tunnuspisteSijainti :gml:Point :gml:pos text)) #" ")
          id (first (xml-> feature :ktjkiiwfs:RekisteriyksikonTietoja :ktjkiiwfs:rekisteriyksikkolaji text))
          property-id (first (xml-> feature :ktjkiiwfs:RekisteriyksikonTietoja :ktjkiiwfs:kiinteistotunnus text))
          municipality-code (first (xml-> feature :ktjkiiwfs:RekisteriyksikonTietoja :ktjkiiwfs:kuntaTieto :ktjkiiwfs:KuntaTieto :ktjkiiwfs:kuntatunnus text))]
      {:rekisteriyksikkolaji {:id id, :selite (get rekisteriyksikkolaji id)}
       :kiinttunnus property-id
       :kunta municipality-code
       :wkt (property-borders-wkt feature)
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
  (let [{status :status body :body} (http-fn url (assoc request :throw-exceptions false :quiet true))]
    (if (= status 200)
      [:ok body]
      [:error status body])))

(defn parse-features-as-latin1 [s]
  (-> s (s/replace-first "UTF-8" "ISO-8859-1") (->features sxml/startparse-sax-no-doctype "ISO-8859-1")))

(defn exec
  ([method url q] (exec method url nil q))
  ([method url credentials q]
    (let [[http-fn param-key] (method http-method)
         timeout (env/value :http-client :conn-timeout)
         credentials (or credentials (auth url))
         request {:basic-auth credentials
                  param-key q}
         task (future* (exec-http http-fn url request))
         [status data error-body] (deref task timeout [:timeout])
         error-text (-> error-body  (ss/replace #"[\r\n]+" " ") (ss/limit 400 "..."))]
      (case status
        :timeout (error "error.integration - wfs timeout while requesting" url)
        :error   (case data
                   400 (errorf "error.integration -  wfs status 400 Bad Request '%s', url=%s, response body=%s" (ss/limit (str q) 220 "...") url error-text)
                   (errorf "error.integration - wfs status %s: url=%s, response body=%s" data url error-text))
        :ok      (let [xml (if (= url nearestfeature)
                             (parse-features-as-latin1 data)
                             (->features data sxml/startparse-sax-no-doctype))
                       member-list (xml-> xml :gml:featureMember)]
                   ; Differences in WFS implementations:
                   ; sometimes gml:featureMember elements are retured (NLS), sometimes gml:featureMembers
                   (if (seq member-list)
                     member-list
                     (xml-> xml :gml:featureMembers)))))))

(defn post
  ([url q] (exec :post url q))
  ([url credentials q] (exec :post url credentials q)))

(defn wms-get
  "WMS query with error handling. Returns response body or nil."
  [url query-params]
  (let [{:keys [status body]} (http/get url {:query-params query-params :quiet true :throw-exceptions false})
        error (when (ss/contains? body "ServiceException")
                (-> body ss/trim
                  (->features startparse-sax-non-validating "UTF-8")
                  (xml-> :ServiceException)
                  first text ss/trim))]
    (if (or (not= 200 status) error)
      (errorf "error.integration - Failed to get %s (status %s): %s" url status (logging/sanitize 1000 error))
      body)))

;;
;;
;;

(defn- exec-trimble-kaavamaaraykset-post [method url user passwd q picurltemplate]
  (let [[http-fn param-key] (method http-method)
        timeout (env/value :http-client :conn-timeout)
        request {:basic-auth [user passwd]
                 param-key q}
        task (future* (exec-http http-fn url request))
        [status data] (deref task timeout [:timeout])]
    (case status
      :timeout (error "error.integration - wfs timeout from" url)
      :error   (error "error.integration - wfs status" data "from" url)
      :ok      (let [features (->features data sxml/startparse-sax-no-doctype "UTF-8")
                     muukaavatunnus (first (xml-> features :gml:featureMember :akaava:Kaava :akaava:muuKaavatunnus text))
                     kaavatunnus (first (xml-> features :gml:featureMember :akaava:Kaava :akaava:kaavatunnus text))
                     arkistotunnus (first (xml-> features :gml:featureMember :akaava:Kaava :akaava:arkistotunnus text))
                     kaavanimi1 (first (xml-> features :gml:featureMember :akaava:Kaava :akaava:kaavanimi1 text))
                     hyvaksyja (first (xml-> features :gml:featureMember :akaava:Kaava :akaava:hyvaksyja text))
                     hyvaksymispvm (first (xml-> features :gml:featureMember :akaava:Kaava :akaava:hyvaksymispvm text))
                     kaavanvaihe (first (xml-> features :gml:featureMember :akaava:Kaava :akaava:kaavanvaihe text))
                     kaavatyyppi (first (xml-> features :gml:featureMember :akaava:Kaava :akaava:kaavatyyppi text))]
                 [{"Kaavatunnus" (str kaavatunnus)
                   "Arkistotunnus" (str arkistotunnus)
                   "Nimi" (str kaavanimi1)
                   "Hyv." (str hyvaksyja)
                   "Pvm." (str hyvaksymispvm)
                   "Vaihe" (str kaavanvaihe)
                   "Tyyppi" (str kaavatyyppi)},
                  (for [maarays (xml-> features :gml:featureMember :akaava:Kaava :akaava:yhteisetkaavamaaraykset :akaava:Kaavamaarays)]
                    {:pic (format picurltemplate muukaavatunnus (first(xml-> maarays :akaava:tunnus text)))
                     :desc (first(xml-> maarays :akaava:maaraysteksti_primaari text))})]))))


(defn trimble-kaavamaaraykset-post [municipality q]
  (let [k (keyword municipality)]
    (let [url (env/value :trimble-kaavamaaraykset k :url)
          picurltemplate (env/value :trimble-kaavamaaraykset k :picurltemplate) user (env/value :trimble-kaavamaaraykset k :user) passwd (env/value :trimble-kaavamaaraykset k :passwd)]
      (exec-trimble-kaavamaaraykset-post :post url user passwd q picurltemplate))))

;;
;; Public queries:
;;

(defn address-by-point [x y]
  (exec :get nearestfeature {:NAMESPACE "xmlns(oso=http://xml.nls.fi/Osoitteet/Osoitepiste/2011/02)"
                             :TYPENAME "oso:Osoitepiste"
                             :REQUEST "GetFeature"
                             :SERVICE "WFS"
                             :VERSION "1.1.0"
                             :COORDS (str x "," y ",EPSG:3067")
                             :SRSNAME "EPSG:3067"
                             :MAXFEATURES "1"
                             :BUFFER "500"}))

(defn address-by-point-from-municipality [x y {:keys [url credentials no-bbox-srs]}]
  (let [x_d (util/->double x)
        y_d (util/->double y)
        radii [25 50 100 250 500 1000]]
    (some identity
      ; Searching by point is not directly supported.
      ; Try searching with increasing radius.
      (for [radius radii
            :let [corners [(- x_d radius) (- y_d radius) (+ x_d radius) (+ y_d radius)]
                  ; Queries to some bockends must have the SRS defined at the end of BBOX,
                  ; but some bockends return NO RESULTS if it is defened!
                  bbox (ss/join "," (if no-bbox-srs corners (conj corners "EPSG:3067")))
                  results (exec :get url credentials
                            {:REQUEST "GetFeature"
                             :SERVICE "WFS"
                             :VERSION "1.1.0"
                             :TYPENAME "mkos:Osoite"
                             :SRSNAME "EPSG:3067"
                             :BBOX bbox
                             :MAXFEATURES "50"})]]
        (if (seq results)
          results
          (warnf "No results for x/y %s/%s within radius of %d. %s"
            x y radius (if (= radius (last radii)) "Giving up!" "Increasing radius...")))))))

(defn property-id-by-point [x y]
  (post ktjkii
    (query {"typeName" "ktjkiiwfs:PalstanTietoja" "srsName" "EPSG:3067"}
      (property-name "ktjkiiwfs:rekisteriyksikonKiinteistotunnus")
      (property-name "ktjkiiwfs:tunnuspisteSijainti")
      (ogc-filter
        (intersects
          (property-name "ktjkiiwfs:sijainti")
          (point x y))))))

(defn location-info-by-property-id [property-id]
  (post ktjkii
    (query {"typeName" "ktjkiiwfs:PalstanTietoja" "srsName" "EPSG:3067"}
      (property-name "ktjkiiwfs:rekisteriyksikonKiinteistotunnus")
      (property-name "ktjkiiwfs:tunnuspisteSijainti")
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

(defn query-get-capabilities
  ([url service]
    (query-get-capabilities url service nil nil true))
  ([url service username password throw-exceptions?]
    {:pre [(ss/not-blank? url)]}
    (let [credentials (when-not (ss/blank? username) {:basic-auth [username password]})
         options     (merge {:socket-timeout 30000, :conn-timeout 30000 ; 30 secs should be enough for GetCapabilities
                             :query-params {:request "GetCapabilities", :service service} ;; , :version "1.1.0"
                             :throw-exceptions  throw-exceptions?}
                       credentials)]
     (http/get url options))))

(defn get-capabilities-xml
  "Returns capabilities XML without namespaces"
  [base-url username password]
  (let [capabilities-resp  (query-get-capabilities base-url "WFS" username password true)
        xml-s (:body capabilities-resp)]
    (-> xml-s sxml/parse reader/strip-xml-namespaces)))

(defn feature-types [xml-no-ns]
  (sxml/select xml-no-ns [:FeatureType]))

(defn wfs-is-alive?
  "checks if the given system is Web Feature Service -enabled. kindof."
  [url username password]
  (when-not (s/blank? url)
    (let [{:keys [status body]} (query-get-capabilities url "WFS" username password false)]
      (cond
        (not= 200 status) (errorf "error.integration - GetCapabilities from %s returned error %s" url status)
        (not (ss/contains? body "<?xml ")) (warnf "GetCapabilities response from %s did not contain XML: " (logging/sanitize 1000 body))
        :else true))))

(defn get-our-capabilities []
  (let [host (env/value :geoserver :host) ; local IP from Chef environment
        path (env/value :geoserver :wms :path)]
    (:body (query-get-capabilities (str host path) "WMS"))))

(def get-rekisteriyksikontietojaFeatureAddress
  (memoize
    #(let [namespace-stripped-xml (get-capabilities-xml  ktjkii (get-in auth [ktjkii 0]) (get-in auth [ktjkii 1]))
           selector [:WFS_Capabilities :OperationsMetadata [:Operation (enlive/attr= :name "DescribeFeatureType")] :DCP :HTTP [:Get]]]
       (sxml/select1-attribute-value namespace-stripped-xml selector :xlink:href))))

(defn rekisteritiedot-xml [rekisteriyksikon-tunnus]
  (if (env/feature? :disable-ktj-on-create)
    (infof "ktj-client is disabled - not getting rekisteritiedot for %s" rekisteriyksikon-tunnus)
    (let [url (str (get-rekisteriyksikontietojaFeatureAddress) "SERVICE=WFS&REQUEST=GetFeature&VERSION=1.1.0&NAMESPACE=xmlns%28ktjkiiwfs%3Dhttp%3A%2F%2Fxml.nls.fi%2Fktjkiiwfs%2F2010%2F02%29&TYPENAME=ktjkiiwfs%3ARekisteriyksikonTietoja&PROPERTYNAME=ktjkiiwfs%3Akiinteistotunnus%2Cktjkiiwfs%3Aolotila%2Cktjkiiwfs%3Arekisteriyksikkolaji%2Cktjkiiwfs%3Arekisterointipvm%2Cktjkiiwfs%3Animi%2Cktjkiiwfs%3Amaapintaala%2Cktjkiiwfs%3Avesipintaala&FEATUREID=FI.KTJkii-RekisteriyksikonTietoja-" (codec/url-encode rekisteriyksikon-tunnus) "&SRSNAME=EPSG%3A3067&MAXFEATURES=100&RESULTTYPE=results")
          options {:http-error :error.integration.ktj-down, :connection-error :error.integration.ktj-down}
          ktj-xml (reader/get-xml url options (get auth ktjkii) false)
          features (-> ktj-xml reader/strip-xml-namespaces sxml/xml->edn)]
      (get-in features [:FeatureCollection :featureMember :RekisteriyksikonTietoja]))))

(defn trimble-kaavamaaraykset-by-point [x y municipality]
  (trimble-kaavamaaraykset-post municipality (trimble-kaavamaaraykset-request x y municipality) ))

;; Raster images:
;;
(defn raster-images [request service & [query-organization-map-server]]
  (let [{:keys [params headers]}  request
        layer   (or (:LAYER params) (:LAYERS params) (:layer params))
        headers (select-keys headers ["accept" "accept-encoding"])]
    (-> (case service
          "nls" (http/get "https://ws.nls.fi/rasteriaineistot/image"
                          {:query-params     params
                           :headers          headers
                           :basic-auth       (:raster auth)
                           :as               :stream
                           :throw-exceptions false})
          ;; Municipality map layers are prefixed. For example: Lupapiste-753-R:wms-layer-name
          "wms" (if-let [[_ org-id layer] (re-matches #"(?i)Lupapiste-([\d]+-[\w]+):(.+)" layer)]
                  (query-organization-map-server (ss/upper-case org-id)
                                                 (merge params {:LAYERS layer})
                                                 headers)
                  (http/get wms-url
                            {:query-params     params
                             :headers          headers
                             :as               :stream
                             :throw-exceptions false}))
          "wmts" (let [{:keys [username password]} (env/value :wmts :raster)
                       url-part (case layer
                                  "taustakartta" "maasto"
                                  "kiinteistojaotus" "kiinteisto"
                                  "kiinteistotunnukset" "kiinteisto")
                       wmts-url (str "https://karttakuva.maanmittauslaitos.fi/" url-part "/wmts")]
                   (http/get wmts-url
                             {:query-params     params
                              :headers          headers
                              :basic-auth       [username password]
                              :as               :stream
                              :throw-exceptions false}))
          "plandocument" (let [id (:id params)]
                           (assert (ss/numeric? id))
                           (http/get (str "http://194.28.3.37/maarays/" id "x.pdf")
                                     {:query-params     params
                                      :headers          headers
                                      :as               :stream
                                      :throw-exceptions false})))
        (http/secure-headers))))
