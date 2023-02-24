(ns lupapalvelu.wfs
  (:require [clojure.data.zip.xml :refer [attr= text xml-> xml1->]]
            [clojure.string :as s]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [lupapalvelu.logging :as logging]
            [net.cgrand.enlive-html :as enlive]
            [ring.util.codec :as codec]
            [sade.common-reader :as cr]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.http :as http]
            [sade.strings :as ss]
            [sade.util :refer [future*] :as util]
            [sade.xml :as sxml]
            [taoensso.timbre :refer [error errorf infof warnf]])
  (:import [java.nio.charset Charset]
           [javax.xml XMLConstants]
           [org.apache.commons.io.input CharSequenceInputStream]))

(def startparse-sax-non-validating
  (sxml/make-startparse false {XMLConstants/FEATURE_SECURE_PROCESSING                           true
                               "http://apache.org/xml/features/nonvalidating/load-dtd-grammar"  false
                               "http://apache.org/xml/features/nonvalidating/load-external-dtd" false
                               "http://xml.org/sax/features/validation"                         false
                               "http://xml.org/sax/features/external-general-entities"          false
                               "http://xml.org/sax/features/external-parameter-entities"        false}))

;;
;; config:
;;

;; Deprecated, will be taken offline in October 2023
(def ktjkii "https://ws.nls.fi/ktjkii/wfs-2015/wfs")
;; Deprecated, will be taken offline in October 2023
(def new-ktjkii "https://sopimus-paikkatieto.maanmittauslaitos.fi/kiinteisto/wfs2/2019-02/ows")

(def wms-url (str (env/value :geoserver :host) (env/value :geoserver :wms :path)))

(def- auth
  (let [conf (env/value :nls)]
    {:raster        [(:username (:raster conf))     (:password (:raster conf))]
     :kiinteisto    [(:username (:kiinteisto conf)) (:password (:kiinteisto conf))]
     ktjkii         [(:username (:ktjkii conf))     (:password (:ktjkii conf))]
     ; LPK-4200
     new-ktjkii     [(:username (:ktjkii conf))     (:password (:ktjkii conf))]}))

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
  {"1.1.0" {"xmlns:wfs" "http://www.opengis.net/wfs"
            "xmlns:gml" "http://www.opengis.net/gml"
            "xmlns:ogc" "http://www.opengis.net/ogc"
            "xmlns:xsi" "http://www.w3.org/2001/XMLSchema-instance"}
   "2.0.0" {"xmlns:wfs" "http://www.opengis.net/wfs/2.0"
            "xmlns:gml" "http://www.opengis.net/gml/3.2"
            ; LPK-4201 in WFS 2.0.0 this is usually namespaced 'fes', but using it as ogc we need to change less code in this ns.
            "xmlns:ogc" "http://www.opengis.net/fes/2.0"
            "xmlns:xsi" "http://www.w3.org/2001/XMLSchema-instance"}})

(def nls-namespaces {"xmlns:oso"       "http://xml.nls.fi/Osoitteet/Osoitepiste/2011/02"
                     "xmlns:ktjkiiwfs" "http://xml.nls.fi/ktjkiiwfs/2010/02"})

(def krysp-namespaces {"xmlns:mkos" "http://www.paikkatietopalvelu.fi/gml/opastavattiedot/osoitteet"
                       "xmlns:yht"  "http://www.paikkatietopalvelu.fi/gml/yhteiset"
                       "xmlns:ogc"  "http://www.opengis.net/ogc"})

(defn query [attrs & e]
  (let [type-name      (or (:typeName attrs) (get attrs "typeName")
                           ; LPK-4201 WFS 2.0.0 uses typeNames
                           (:typeNames attrs) (get attrs "typeNames"))
        ns-prefix      (first (ss/split (name type-name) #":"))
        version        (or (:version attrs) (get attrs "version") "1.1.0")
        xml-namespaces (if (some #(ss/ends-with % ns-prefix) (keys krysp-namespaces)) krysp-namespaces nls-namespaces)]
    (sxml/element-to-string
      {:tag     :wfs:GetFeature
       :attrs   (merge {:version version
                        :service :WFS}
                       (get common-namespaces version)
                       xml-namespaces)
       :content [{:tag     :wfs:Query
                  :attrs   attrs
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

; I guess this is usually with :fes: namespace, but we just use ogc for legacy code reasons...
(defn value-reference [& e] {:tag :ogc:ValueReference :content e})

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

(defn wfs-property-name [prop-name] {:tag :wfs:PropertyName :content [prop-name]})

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
                   {"wildCard" "*", "singleChar" "?", "escape" "\\", "matchCase" "false"}))

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

(defn value-reference-is-equal [value-ref value]
  {:tag :ogc:PropertyIsEqualTo
   :content [(value-reference value-ref)
             {:tag :ogc:Literal :content [value]}]})

;;
;; Helpers for result parsing:
;;

(defn extract-coordinates [ring]
  (s/replace (first (xml-> ring :gml:LinearRing :gml:posList text)) #"(\d+\.*\d*)\s+(\d+\.*\d*)\s+" "$1 $2, "))

(defn property-borders-wkt [feature]
  (when feature
    (let [path (cond
                 (seq (xml-> feature :ktjkiiwfs:PalstanTietoja))
                 [:ktjkiiwfs:PalstanTietoja :ktjkiiwfs:sijainti :gml:Surface :gml:patches :gml:PolygonPatch]

                 (seq (xml-> feature :ktjkiiwfs:PalstanSijaintitiedot)) ; new-ktjkii WFS 2.0.0
                 [:ktjkiiwfs:PalstanSijaintitiedot :ktjkiiwfs:sijainti :gml:Polygon]

                 :else
                 [:ktjkiiwfs:RekisteriyksikonTietoja :ktjkiiwfs:rekisteriyksikonPalstanTietoja :ktjkiiwfs:RekisteriyksikonPalstanTietoja :ktjkiiwfs:sijainti :gml:Surface :gml:patches :gml:PolygonPatch])
          polygonpatch (first (apply xml-> (cons feature path) ))
          exterior (extract-coordinates (first (xml-> polygonpatch :gml:exterior)))
          interiors (map extract-coordinates (xml-> polygonpatch :gml:interior))]
      (str "POLYGON((" exterior ")" (ss/join (map #(str ",(" % ")") interiors)) ")"))))

(defn lot-feature-to-location
  "Parse information from :ktjkiiwfs:PalstanSijaintitiedot.
  Reads feature from eg `lots-by-property-id-wfs2` return value."
  [feature]
  (when feature
    (let [[x y] (s/split (xml1-> feature :ktjkiiwfs:PalstanSijaintitiedot :ktjkiiwfs:kiinteistotunnuksenSijainti :gml:Point :gml:pos text) #" ")]
      {:kiinttunnus (xml1-> feature :ktjkiiwfs:PalstanSijaintitiedot :ktjkiiwfs:kiinteistotunnus text)
       :lot-id      (xml1-> feature :ktjkiiwfs:PalstanSijaintitiedot :ktjkiiwfs:id text)
       :x           x
       :y           y
       :wkt         (property-borders-wkt feature)})))

(defn feature-to-property-id-wfs2
  "Returns property ID from WFS2 PalstanSijaintitiedot response"
  [feature]
  (when feature
    {:kiinttunnus (first (xml-> feature :ktjkiiwfs:PalstanSijaintitiedot :ktjkiiwfs:kiinteistotunnus text))}))

(defn krysp-to-address-details [lang feature]
  (when (map? feature)
    (let [street-by-lang (sxml/get-text feature [:Osoite :osoitenimi #{[:teksti (enlive/attr= :xml:lang lang)]}])
          street (if (seq street-by-lang)
                   street-by-lang
                   (sxml/get-text feature [:Osoite :osoitenimi :teksti]))
          [x y] (ss/split (sxml/get-text feature [:Osoite :pistesijainti :Point :pos]) #"\s")]
      {:street street
       :number (sxml/get-text feature [:Osoite :osoitenumero])
       :municipality (sxml/get-text feature [:Osoite :kunta])
       :x (util/->double x)
       :y (util/->double y)})))


(defn feature-to-property-info
  "This data is returned to Kauppa"
  [feature]
  (when (seq feature)
    (let [[x y] (s/split (first (xml-> feature :ktjkiiwfs:RekisteriyksikonTietoja :ktjkiiwfs:rekisteriyksikonPalstanTietoja :ktjkiiwfs:RekisteriyksikonPalstanTietoja :ktjkiiwfs:tunnuspisteSijainti :gml:Point :gml:pos text)) #" ")
          id (first (xml-> feature :ktjkiiwfs:RekisteriyksikonTietoja :ktjkiiwfs:rekisteriyksikkolaji text))
          property-id (first (xml-> feature :ktjkiiwfs:RekisteriyksikonTietoja :ktjkiiwfs:kiinteistotunnus text))
          municipality-code (first (xml-> feature :ktjkiiwfs:RekisteriyksikonTietoja :ktjkiiwfs:kuntaTieto :ktjkiiwfs:KuntaTieto :ktjkiiwfs:kuntatunnus text))
          muni-fi (first (xml-> feature :ktjkiiwfs:RekisteriyksikonTietoja :ktjkiiwfs:kuntaTieto :ktjkiiwfs:KuntaTieto :ktjkiiwfs:nimiSuomeksi text))
          muni-sv (first (xml-> feature :ktjkiiwfs:RekisteriyksikonTietoja :ktjkiiwfs:kuntaTieto :ktjkiiwfs:KuntaTieto :ktjkiiwfs:nimiRuotsiksi text))]
      {:rekisteriyksikkolaji {:id id, :selite (get rekisteriyksikkolaji id)}
       :kiinttunnus property-id
       :kunta municipality-code
       :nimi {:fi muni-fi
              :sv muni-sv}
       :wkt (property-borders-wkt feature)
       :x x
       :y y})))


(defn feature-to-property-id-municipality
  "Parses property info without property borders (for property-info-by-point)"
  [root-element feature-zipper]
  (when (seq feature-zipper)
    (let [muni-zipper (xml1-> feature-zipper root-element :ktjkiiwfs:kuntaTieto :ktjkiiwfs:KuntaTieto)]
      {:propertyId (xml1-> feature-zipper root-element :ktjkiiwfs:kiinteistotunnus text)
       :municipality (xml1-> muni-zipper :ktjkiiwfs:kuntatunnus text)
       :name {:fi (xml1-> muni-zipper :ktjkiiwfs:nimiSuomeksi text)
              :sv (xml1-> muni-zipper :ktjkiiwfs:nimiRuotsiksi text)}})))


(defn- ->features [^String s parse-fn & [^String encoding]]
  (when s
    (-> (if encoding
          (CharSequenceInputStream. s encoding)
          (CharSequenceInputStream. s (Charset/defaultCharset)))
        (xml/parse parse-fn)
        zip/xml-zip)))

;;
;; Executing HTTP calls to Maanmittauslaitos:
;;

(def- http-method {:post [http/post :body]
                   :get  [http/get  :query-params]})

(defn- exec-http [http-fn url request]
  (let [conn-timeout   (env/value :mml :conn-timeout)
        socket-timeout (env/value :mml :socket-timeout)
        {status :status body :body} (http-fn url (merge request
                                                        {:socket-timeout   socket-timeout
                                                         :conn-timeout     conn-timeout
                                                         :throw-exceptions false
                                                         :quiet            true}))]
    (if (= status 200)
      [:ok body]
      [:error status body])))

(defn exec-raw
  ([method url q] (exec-raw method url nil q))
  ([method url credentials q] (exec-raw {:method      method
                                         :url         url
                                         :credentials credentials
                                         :q           q}))
  ([{:keys [method url credentials q throw?]}]
   (let [[http-fn param-key]      (method http-method)
         conn-timeout             (env/value :mml :conn-timeout)
         socket-timeout           (env/value :mml :socket-timeout)
         credentials              (or credentials (auth url))
         request                  {:basic-auth credentials
                                   param-key   q}
         task                     (future* (exec-http http-fn url request))
         [status data error-body] (deref task (+ conn-timeout socket-timeout) [:timeout])
         error-text               (-> error-body  (ss/replace #"[\r\n]+" " ") (ss/limit 400 "..."))]
     (if (and (= status :ok) (seq data))
       data
       (do
         (case status
           :timeout (error "error.integration - wfs timeout while requesting" url)
           :error   (case data
                      400 (errorf "error.integration -  wfs status 400 Bad Request '%s', url=%s, response body=%s" (ss/limit (str q) 220 "...") url error-text)
                      (errorf "error.integration - wfs status %s: url=%s, response body=%s" data url error-text))
           :ok      (error "error.integration - wfs response body empty for url" url))
         (when throw?
           (throw (ex-info "Integration error"
                           {:status     status
                            :data       data
                            :error-text error-text}))))))))

(defn exec-get-xml
  [& args]
  (let [[_ url & _] args]
    (try
      (-> ^String (apply exec-raw args)
          (CharSequenceInputStream. (Charset/defaultCharset))
          (xml/parse sxml/startparse-sax-no-doctype))
      (catch Exception e
        (errorf "Exception caught in wfs req from url %s %s" url e)
        nil))))

(defn exec
  ([method url q] (exec method url nil q))
  ([method url credentials q] (exec {:method      method
                                     :url         url
                                     :credentials credentials
                                     :q           q}))
  ([options]
   (when-let [data (exec-raw options)]
     (let [xml (->features data sxml/startparse-sax-no-doctype)]
       ;; Differences in WFS implementations:
       ;; sometimes gml:featureMember elements are retured (NLS), sometimes gml:featureMembers
       (or (seq (xml-> xml :gml:featureMember))
           (seq (xml-> xml :gml:featureMembers))
                                        ; MML WFS 2.0.0 LPK-4200
           (seq (xml-> xml :wfs:member)))))))

(defn post
  ([url q] (exec :post url q))
  ([url credentials q] (exec :post url credentials q))
  ([options] (exec (assoc options :method :post))))

(defn wms-get
  "WMS query with error handling. Returns response body or nil."
  [url query-params user passwd]
  (let [credentials (when-not (ss/blank? user) {:basic-auth [user passwd]})
         options     (merge {:query-params query-params :quiet true :throw-exceptions false}
                       credentials)
        {:keys [status body]} (http/get url options)
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
                  (for [maarays (xml-> features :gml:featureMember :akaava:Kaava :akaava:yhteisetkaavamaaraykset :akaava:Kaavamaarays)
                        :when (string? picurltemplate)]
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


(defn select-kuntagml-osoite [stripped-xml]
  (or (seq (sxml/select stripped-xml [:FeatureCollection :featureMember :Osoite]))
      (seq (sxml/select stripped-xml [:Osoite]))))

(defn address-by-point-from-municipality [x_d y_d {:keys [url credentials no-bbox-srs]}]
  (let [radii [25 50 100 250 500 1000]]
    (some identity
      ; Searching by point is not directly supported.
      ; Try searching with increasing radius.
      (for [radius radii
            :let [corners [(- x_d radius) (- y_d radius) (+ x_d radius) (+ y_d radius)]
                  ; Queries to some bockends must have the SRS defined at the end of BBOX,
                  ; but some bockends return NO RESULTS if it is defened!
                  bbox (ss/join "," (if no-bbox-srs corners (conj corners "EPSG:3067")))
                  xml (exec-get-xml :get url credentials
                                    {:REQUEST "GetFeature"
                                     :SERVICE "WFS"
                                     :VERSION "1.1.0"
                                     :TYPENAME "mkos:Osoite"
                                     :SRSNAME "EPSG:3067"
                                     :BBOX bbox
                                     :MAXFEATURES "50"})
                  stripped (cr/strip-xml-namespaces xml)
                  results (select-kuntagml-osoite stripped)]]
        (if (not-empty results)
          results
          (warnf "No results for x/y %s/%s within radius of %d. %s (bbox=%s)"
            x_d y_d radius (if (= radius (last radii)) "Giving up!" "Increasing radius...") bbox))))))

;;;
;;; Lot functions (Palsta)
;;;


(defn lot-property-id-by-point-wfs2
  "New WFS 2.0.0 KTJKii service (PalstanSijaintitiedot)." ; LPK-4201
  [x y]
  (post new-ktjkii
        (query {"typeNames" "ktjkiiwfs:PalstanSijaintitiedot" :version "2.0.0"}
               (wfs-property-name "ktjkiiwfs:kiinteistotunnus")
               (ogc-filter
                 (intersects
                   (value-reference "ktjkiiwfs:sijainti")
                   (point x y))))))


(defn lots-by-property-id-wfs2
  "New WFS 2.0.0. Returns lots (palstat) matching given property-id (uses PalstanSijaintitiedot)" ; LPK-4201
  [property-id]
  (post
    new-ktjkii
    (query {"typeNames" "ktjkiiwfs:PalstanSijaintitiedot" "srsName" "EPSG:3067" :version "2.0.0"}
           (ogc-filter
             (value-reference-is-equal "ktjkiiwfs:kiinteistotunnus" property-id)))))


;;;
;;; Property location functions (KiinteistotunnuksenSijaintitiedot), new MML WFS 2.0.0
;;;


(defn property-points-by-id-wfs2
  "New WFS 2.0.
  Returns all lot locations for given property-id. Lot locations are points.
  If you are only interested in points, use this. If you are interested in polygon areas of the lots,
  use `lots-by-property-id-wfs2` instead."
  [property-id]
  (post
    new-ktjkii
    (query {"typeNames" "ktjkiiwfs:KiinteistotunnuksenSijaintitiedot" :version "2.0.0"}
           (ogc-filter
             (value-reference-is-equal "ktjkiiwfs:kiinteistotunnus" property-id)))))


;;;
;;; Property unit functions (Rekisteriyksikkö)
;;;

(defn municipality-info-by-property-id
  "Fetch municipality information for property.
  RekisteriyksikonSijaintitiedot would also provide RekisteriyksikonPalstanTietoja, which would in turn contain
  location as point. But including that to query slows down the query too much.
  Thus this is optimized for fetching the municipality information.
  Use lots-by-property-id to fetch point and area for property-id."
  [property-id]
  (->> (query {"typeName" "ktjkiiwfs:RekisteriyksikonSijaintitiedot"}
              (wfs-property-name "ktjkiiwfs:kuntaTieto")
              (ogc-filter
                (property-is-equal "ktjkiiwfs:RekisteriyksikonSijaintitiedot/ktjkiiwfs:kiinteistotunnus" property-id)))
       (post ktjkii)
       first))

(defn property-info-by-radius [x y radius]
  (post ktjkii
    (query {"typeName" "ktjkiiwfs:RekisteriyksikonTietoja" "srsName" "EPSG:3067"}
      (wfs-property-name "ktjkiiwfs:rekisteriyksikkolaji")
      (wfs-property-name "ktjkiiwfs:kiinteistotunnus")
      (wfs-property-name "ktjkiiwfs:rekisteriyksikonPalstanTietoja/ktjkiiwfs:RekisteriyksikonPalstanTietoja/ktjkiiwfs:sijainti")
      (wfs-property-name "ktjkiiwfs:rekisteriyksikonPalstanTietoja/ktjkiiwfs:RekisteriyksikonPalstanTietoja/ktjkiiwfs:tunnuspisteSijainti")
      (wfs-property-name "ktjkiiwfs:kuntaTieto")
      (ogc-filter
        (within
          (property-name "ktjkiiwfs:rekisteriyksikonPalstanTietoja/ktjkiiwfs:RekisteriyksikonPalstanTietoja/ktjkiiwfs:sijainti")
          (point x y)
          (distance radius))))))

(defn property-info-by-point [x y]
  (post ktjkii
    (query {"typeName" "ktjkiiwfs:RekisteriyksikonTietoja" "srsName" "EPSG:3067"}
      (wfs-property-name "ktjkiiwfs:rekisteriyksikkolaji")
      (wfs-property-name "ktjkiiwfs:kiinteistotunnus")
      (wfs-property-name "ktjkiiwfs:rekisteriyksikonPalstanTietoja")
      (wfs-property-name "ktjkiiwfs:rekisteriyksikonPalstanTietoja/ktjkiiwfs:RekisteriyksikonPalstanTietoja/ktjkiiwfs:sijainti")
      (wfs-property-name "ktjkiiwfs:rekisteriyksikonPalstanTietoja/ktjkiiwfs:RekisteriyksikonPalstanTietoja/ktjkiiwfs:tunnuspisteSijainti")
      (wfs-property-name "ktjkiiwfs:kuntaTieto")
      (ogc-filter
        (intersects
          (property-name "ktjkiiwfs:rekisteriyksikonPalstanTietoja/ktjkiiwfs:RekisteriyksikonPalstanTietoja/ktjkiiwfs:sijainti")
          (point x y))))))

(defn property-id-muni-by-point [x y]
  (post ktjkii
        (query {"typeName" "ktjkiiwfs:RekisteriyksikonTietoja" "srsName" "EPSG:3067"}
               (wfs-property-name "ktjkiiwfs:kiinteistotunnus")
               (wfs-property-name "ktjkiiwfs:kuntaTieto")
               (ogc-filter
                 (intersects
                   (property-name "ktjkiiwfs:rekisteriyksikonPalstanTietoja/ktjkiiwfs:RekisteriyksikonPalstanTietoja/ktjkiiwfs:sijainti")
                   (point x y))))))

(defn property-info-by-line [l]
  (post ktjkii
    (query {"typeName" "ktjkiiwfs:RekisteriyksikonTietoja" "srsName" "EPSG:3067"}
      (wfs-property-name "ktjkiiwfs:rekisteriyksikkolaji")
      (wfs-property-name "ktjkiiwfs:kiinteistotunnus")
      (wfs-property-name "ktjkiiwfs:rekisteriyksikonPalstanTietoja")
      (wfs-property-name "ktjkiiwfs:rekisteriyksikonPalstanTietoja/ktjkiiwfs:RekisteriyksikonPalstanTietoja/ktjkiiwfs:sijainti")
      (wfs-property-name "ktjkiiwfs:rekisteriyksikonPalstanTietoja/ktjkiiwfs:RekisteriyksikonPalstanTietoja/ktjkiiwfs:tunnuspisteSijainti")
      (wfs-property-name "ktjkiiwfs:kuntaTieto")
      (ogc-filter
        (intersects
          (property-name "ktjkiiwfs:rekisteriyksikonPalstanTietoja/ktjkiiwfs:RekisteriyksikonPalstanTietoja/ktjkiiwfs:sijainti")
          (line l))))))

(defn property-info-by-polygon [p]
  (post ktjkii
    (query {"typeName" "ktjkiiwfs:RekisteriyksikonTietoja" "srsName" "EPSG:3067"}
      (wfs-property-name "ktjkiiwfs:rekisteriyksikkolaji")
      (wfs-property-name "ktjkiiwfs:kiinteistotunnus")
      (wfs-property-name "ktjkiiwfs:rekisteriyksikonPalstanTietoja")
      (wfs-property-name "ktjkiiwfs:rekisteriyksikonPalstanTietoja/ktjkiiwfs:RekisteriyksikonPalstanTietoja/ktjkiiwfs:sijainti")
      (wfs-property-name "ktjkiiwfs:rekisteriyksikonPalstanTietoja/ktjkiiwfs:RekisteriyksikonPalstanTietoja/ktjkiiwfs:tunnuspisteSijainti")
      (wfs-property-name "ktjkiiwfs:kuntaTieto")
      (ogc-filter
        (intersects
          (property-name "ktjkiiwfs:rekisteriyksikonPalstanTietoja/ktjkiiwfs:RekisteriyksikonPalstanTietoja/ktjkiiwfs:sijainti")
          (polygon p))))))


;;; Plan info functions

(defn capabilities-to-layers [capabilities]
  (if capabilities
    (xml-> (->features (s/trim capabilities) startparse-sax-non-validating "UTF-8") :Capability :Layer :Layer)
    []))

(defn layer-name-and-title [layer]
  [(xml1-> layer :Name text) (xml1-> layer :Title text)])

(defn- plan-info-config [municipality type]
  (let [k (keyword municipality)
        t (keyword type)]
    {:url    (or (env/value t k :url) wms-url)
     :layers (or (env/value t k :layers) (str municipality "_asemakaavaindeksi"))
     :format (or (env/value t k :format) "application/vnd.ogc.gml")
     :user (env/value t k :user)
     :passwd (env/value t k :passwd)}))

(defn plan-info-by-point [x y municipality type]
  (let [bbox [(- (util/->double x) 128) (- (util/->double y) 128) (+ (util/->double x) 128) (+ (util/->double y) 128)]
        {:keys [url layers format user passwd]} (plan-info-config municipality type)
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
    (wms-get url query user passwd)))

;;; Mikkeli is special because it was done first and they use Bentley WMS
(defn gfi-to-features-bentley [gfi _]
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

;;; Valkeakoski is almost same as Mikkeli
(defn feature-to-feature-info-valkeakoski-plan-info  [feature]
  (when feature
    {:id (first (xml-> feature :property (attr= :name "IDPR_RENDITION_ID") text))
     :kaavanro (first (xml-> feature :property (attr= :name "kaavatunnus") text))
     :linkki (first (xml-> feature :property (attr= :name "Linkki2") text))
     :type "bentley"}))

(defn feature-to-feature-info-valkeakoski-rakennustapaohje  [feature]
  (when feature
    {:id (first (xml-> feature :property (attr= :name "IDPR_RENDITION_ID") text))
     :kaavanro (first (xml-> feature :property (attr= :name "kaavatunnus") text))
     :linkki (first (xml-> feature :property (attr= :name "linkki2") text))
     :type "bentley"}))

(defn gfi-to-features-sito [gfi municipality]
  (when gfi
    (xml-> (->features gfi startparse-sax-non-validating "UTF-8") :gml:featureMember (keyword (str "lupapiste:" municipality "_asemakaavaindeksi")))))


(defn feature-to-feature-info-sito  [feature]
  (when feature
    {:id             (or (xml1-> feature :lupapiste:TUNNUS text) (xml1-> feature :lupapiste:Tunnus text))
     :kuntanro       (or (xml1-> feature :lupapiste:KUNTA_ID text) (xml1-> feature :lupapiste:Kunta_Id text) (xml1-> feature :lupapiste:Kunta_ID text))
     :kaavanro       (or (xml1-> feature :lupapiste:TUNNUS text) (xml1-> feature :lupapiste:Tunnus text))
     :vahvistett_pvm (or (xml1-> feature :lupapiste:VAHVISTETT text) (xml1-> feature :lupapiste:Vahvistett text))
     :linkki         (or (xml1-> feature :lupapiste:LINKKI text) (xml1-> feature :lupapiste:Linkki text))
     :type           "sito"}))

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
    (wms-get wms-url query nil nil)))

(defn gfi-to-general-plan-features [gfi]
  (when gfi
    (let [info (->features gfi startparse-sax-non-validating "UTF-8")]
      (clojure.set/union
        (xml-> info :gml:featureMember :lupapiste:yleiskaavaindeksi)
        (xml-> info :gml:featureMember :lupapiste:yleiskaavaindeksi_poikkeavat)))))

(defn general-plan-feature-to-feature-info  [feature]
  (when feature
    {:id         (first (xml-> feature :lupapiste:tunnus text))
     :nimi       (first (xml-> feature :lupapiste:nimi text))
     :pvm        (first (xml-> feature :lupapiste:paatoksen_paivays text))
     :tyyppi     (first (xml-> feature :lupapiste:tyypittely text))
     :oikeusvaik (first (xml-> feature :lupapiste:oikeusvaikutteisuus text))
     :lisatieto  (first (xml-> feature :lupapiste:lisatietoja text))
     :linkki     (first (xml-> feature :lupapiste:merkinnat_ja_maaraykset text))
     :type       "yleiskaava"}))

(defn- query-get-capabilities
  ([url service]
   (query-get-capabilities url service nil nil true))
  ([url service username password throw-exceptions?]
   {:pre [(ss/not-blank? url)]}
   (let [credentials (when-not (ss/blank? username) {:basic-auth [username password]})
         options     (merge {:as               :stream
                             :socket-timeout   (env/value :capabilities :socket-timeout),
                             :conn-timeout     (env/value :capabilities :conn-timeout)
                             :query-params     {:request "GetCapabilities", :service service} ;; , :version "1.1.0"
                             :throw-exceptions throw-exceptions?}
                            credentials)]
     (http/get url options))))

(defn get-capabilities-xml
  "Returns capabilities XML without namespaces"
  [base-url username password]
  (-> (query-get-capabilities base-url "WFS" username password true)
      :body
      sxml/parse
      cr/strip-xml-namespaces))

(defn feature-types [xml-no-ns]
  (sxml/select xml-no-ns [:FeatureType]))

(defn wfs-is-alive?
  "checks if the given system is Web Feature Service -enabled. kindof."
  [url username password]
  (when-not (s/blank? url)
    (let [{:keys [status body]} (query-get-capabilities url "WFS" username password false)]
      (if (= status 200)
        (let [body (slurp body)]
          (if (ss/contains? body "<?xml")
            true
            (warnf "GetCapabilities response from %s did not contain XML: %s" url (logging/sanitize 1000 body))))
        (errorf "error.integration - GetCapabilities from %s returned error %s" url status)))))

(defn get-our-capabilities []
  (slurp (:body (query-get-capabilities wms-url "WMS"))))

(def get-rekisteriyksikontietojaFeatureAddress
  (memoize
    #(let [namespace-stripped-xml (get-capabilities-xml  ktjkii (get-in auth [ktjkii 0]) (get-in auth [ktjkii 1]))
           selector [:WFS_Capabilities :OperationsMetadata [:Operation (enlive/attr= :name "DescribeFeatureType")] :DCP :HTTP [:Get]]]
       (sxml/select1-attribute-value namespace-stripped-xml selector :xlink:href))))

(defn rekisteritiedot-xml [rekisteriyksikon-tunnus]
  "rekisteriyksikon-tunnus = property id in db format"
  (if (env/feature? :disable-ktj-on-create)
    (infof "ktj-client is disabled - not getting rekisteritiedot for %s" rekisteriyksikon-tunnus)
    (let [url (str (get-rekisteriyksikontietojaFeatureAddress) "SERVICE=WFS&REQUEST=GetFeature&VERSION=1.1.0&NAMESPACE=xmlns%28ktjkiiwfs%3Dhttp%3A%2F%2Fxml.nls.fi%2Fktjkiiwfs%2F2010%2F02%29&TYPENAME=ktjkiiwfs%3ARekisteriyksikonTietoja&PROPERTYNAME=ktjkiiwfs%3Akiinteistotunnus%2Cktjkiiwfs%3Aolotila%2Cktjkiiwfs%3Arekisteriyksikkolaji%2Cktjkiiwfs%3Arekisterointipvm%2Cktjkiiwfs%3Animi%2Cktjkiiwfs%3Amaapintaala%2Cktjkiiwfs%3Avesipintaala&FEATUREID=FI.KTJkii-RekisteriyksikonTietoja-" (codec/url-encode rekisteriyksikon-tunnus) "&SRSNAME=EPSG%3A3067&MAXFEATURES=1&RESULTTYPE=results")
          options {:http-error :error.integration.ktj-down, :connection-error :error.integration.ktj-down :conn-timeout 15000 :socket-timeout 15000}
          ktj-xml (cr/get-xml url options (get auth ktjkii) false)
          features (-> ktj-xml cr/strip-xml-namespaces sxml/xml->edn)]
      (get-in features [:FeatureCollection :featureMember :RekisteriyksikonTietoja]))))

(defn trimble-kaavamaaraykset-by-point [x y municipality]
  (trimble-kaavamaaraykset-post municipality (trimble-kaavamaaraykset-request x y municipality) ))

;; Raster images:
;;
(defn raster-images [request service & [query-organization-map-server]]
  (let [{:keys [params headers]} request
        layer          (or (:LAYER params) (:LAYERS params) (:layer params))
        headers        (select-keys headers ["accept" "accept-encoding"])
        conn-timeout   (env/value :mml :conn-timeout)
        socket-timeout (env/value :mml :socket-timeout)
        base-opts      {:socket-timeout   socket-timeout
                        :conn-timeout     conn-timeout
                        :query-params     params
                        :headers          headers
                        :as               :stream
                        :throw-exceptions false}]
    (-> (case service
          "nls" (http/get "https://ws.nls.fi/rasteriaineistot/image"
                          (merge base-opts
                                 {:basic-auth (:raster auth)}))
          ;; Municipality map layers are prefixed. For example: Lupapiste-753-R:wms-layer-name
          "wms" (if-let [[_ org-id layer] (re-matches #"(?i)Lupapiste-([\d]+-[\w]+):(.+)" layer)]
                  (query-organization-map-server (ss/upper-case org-id)
                                                 (merge params {:LAYERS layer})
                                                 headers)
                  (http/get wms-url
                            base-opts))
          "wmts" (let [{:keys [username password]} (env/value :wmts :raster)
                       url-part (case layer
                                  "taustakartta" "maasto"
                                  "kiinteistojaotus" "kiinteisto"
                                  "kiinteistotunnukset" "kiinteisto")
                       wmts-url (str "https://karttakuva.maanmittauslaitos.fi/" url-part "/wmts")]
                   (http/get wmts-url
                             (merge base-opts
                                    {:basic-auth [username password]})))
          "plandocument" (let [id (:id params)]
                           (assert (ss/numeric? id))
                           (http/get (str "http://194.28.3.37/maarays/" id "x.pdf")
                                     base-opts)))
        (http/secure-headers))))
