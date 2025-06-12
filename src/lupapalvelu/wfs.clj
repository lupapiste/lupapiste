(ns lupapalvelu.wfs
  "WFS API for communicating with municipal WFS endpoints.
  Any references to MML are historical."
  (:require [clojure.data.zip :as data.zip]
            [clojure.data.zip.xml :refer [attr= text xml-> xml1->]]
            [clojure.set :as set]
            [clojure.string :as s]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [hato.client :as hc]
            [lupapalvelu.logging :as log :refer [error errorf warnf]]
            [net.cgrand.enlive-html :as enlive]
            [sade.async :refer [future*]]
            [sade.common-reader :as cr]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.http :as http]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as sxml]
            [swiss.arrows :refer :all])
  (:import [java.nio.charset Charset]
           [javax.xml XMLConstants]
           [org.apache.commons.io.input CharSequenceInputStream]))

(set! *warn-on-reflection* true)

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

(def wms-url (str (env/value :geoserver :host) (env/value :geoserver :wms :path)))

(def- auth
  (let [conf (env/value :nls)]
    {:raster [(:username (:raster conf)) (:password (:raster conf))]}))

;;
;; DSL to WFS queries:
;;

(def krysp-namespaces {"xmlns:mkos" "http://www.paikkatietopalvelu.fi/gml/opastavattiedot/osoitteet"
                       "xmlns:yht"  "http://www.paikkatietopalvelu.fi/gml/yhteiset"
                       "xmlns:ogc"  "http://www.opengis.net/ogc"})

(defn trimble-kaavamaaraykset-point-to-bbox [x y]
  (str "" (apply str x) "," (apply str y) " " (apply str (str (+ (util/->double (str x)) 0.01))) "," (apply str (str (+ (util/->double y) 0.01))) ) )


;; tm35fin to trimble wfs coordinate system using params from properties file

(defn trimble-kaavamaaraykset-muunnax [x y municipality]
  (let [k           (keyword municipality)
        [a b c d e] (s/split (env/value :trimble-kaavamaaraykset k :muunnosparams :x) #",")]
    (format "%s" (+ (util/->double a) (* (util/->double b) (- (util/->double x) (util/->double c))) (* (util/->double d) (- (util/->double y) (util/->double e)))))))

(defn trimble-kaavamaaraykset-muunnay [x y municipality]
  (let [k           (keyword municipality)
        [a b c d e] (s/split (env/value :trimble-kaavamaaraykset k :muunnosparams :y) #",")]
    (format "%s" (+ (util/->double a) (* (util/->double b) (- (util/->double y) (util/->double c))) (* (util/->double d) (- (util/->double x) (util/->double e)))))))

(defn trimble-kaavamaaraykset-request [x y municipality]
  (let [bbox [(trimble-kaavamaaraykset-point-to-bbox
                (trimble-kaavamaaraykset-muunnax x y municipality)
                (trimble-kaavamaaraykset-muunnay x y municipality))]]
    (str "<?xml version='1.0' encoding='utf-8'?><GetFeature xmlns='http://www.opengis.net/wfs' xmlns:akaava='http://www.paikkatietopalvelu.fi/gml/asemakaava' xmlns:ogc='http://www.opengis.net/ogc' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xmlns:gml='http://www.opengis.net/gml' service='WFS' version='1.0.0' outputFormat='GML2' maxFeatures='1' handle='' ><Query typeName='akaava:Kaava' srsName='EPSG:3877' ><ogc:Filter><ogc:BBOX><ogc:PropertyName>voimassaolosijainti</ogc:PropertyName><gml:Box srsName='EPSG:3877'><gml:coordinates>" (apply str bbox) "</gml:coordinates></gml:Box></ogc:BBOX></ogc:Filter></Query></GetFeature>" )))

(defn ogc-filter [& e] {:tag :ogc:Filter :content e})

(defn ogc-and [& e] {:tag :ogc:And :content e})

(defn ogc-or [& e] {:tag :ogc:Or :content e})

(defn property-name [prop-name] {:tag :ogc:PropertyName :content [prop-name]})

(defn property-filter [filter-name prop-name value & attrs]
  (let [attributes (if (map? (first attrs) )
                     (first attrs)
                     (apply hash-map (partition 2 attrs)))]
    {:tag     (keyword (str "ogc:" (name filter-name)))
     :attrs   attributes
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

;;
;; Helpers for result parsing:
;;

(defn krysp-to-address-details [lang feature]
  (when (map? feature)
    (let [street-by-lang (sxml/get-text feature [:Osoite :osoitenimi #{[:teksti (enlive/attr= :xml:lang lang)]}])
          street         (if (seq street-by-lang)
                           street-by-lang
                           (sxml/get-text feature [:Osoite :osoitenimi :teksti]))
          [x y]          (ss/split (sxml/get-text feature [:Osoite :pistesijainti :Point :pos]) #"\s")]
      {:street       street
       :number       (sxml/get-text feature [:Osoite :osoitenumero])
       :municipality (sxml/get-text feature [:Osoite :kunta])
       :x            (util/->double x)
       :y            (util/->double y)})))


(defn- ->features [^String s parse-fn & [^String encoding]]
  (when s
    (-> (if encoding
          (CharSequenceInputStream. s encoding)
          (CharSequenceInputStream. s (Charset/defaultCharset)))
        (xml/parse parse-fn)
        zip/xml-zip)))

;;
;; Executing HTTP calls to municipal WFS servers:
;;

(def use-hato? (env/feature? :use-hato-for-gis-access))

(def *hato-client
  (when use-hato?
    (delay (hc/build-http-client {:redirect-policy :normal
                                  ;; Force 1.1 because streaming HTTP/2 responses directly will break in
                                  ;; GCP load balancer
                                  :version         :http-1.1
                                  :connect-timeout (env/value :mml :conn-timeout)}))))

(defn- format-basic-auth
  ([[username password]]
   (format-basic-auth username password))
  ([username password]
   {:basic-auth (if use-hato?
                  {:user username
                   :pass password}
                  [username password])}))

(def http-post (if use-hato? http/hato-post http/post))
(def http-get (if use-hato? http/hato-get http/get))

(def- http-method {:post [http-post :body]
                   :get  [http-get :query-params]})

(defn- exec-http [http-fn url request]
  (let [conn-timeout                (env/value :mml :conn-timeout)
        socket-timeout              (env/value :mml :socket-timeout)
        {status :status body :body} (http-fn url (merge request
                                                        {:throw-exceptions false}
                                                        (if use-hato?
                                                          {:http-client @*hato-client}
                                                          {:socket-timeout socket-timeout
                                                           :conn-timeout   conn-timeout
                                                           :quiet          true})))]
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
         request                  (merge (format-basic-auth credentials)
                                         {param-key q})
         task                     (future* (exec-http http-fn url request))
         [status data error-body] (deref task (+ conn-timeout socket-timeout) [:timeout])
         error-text               (-> error-body  (ss/replace #"[\r\n]+" " ") (ss/limit 400 "..."))]
     (if (and (= status :ok) (seq data))
       data
       (do
         (case status
           :timeout (error "error.integration - wfs timeout while requesting" url)
           :error   (if (= data 400)
                      (errorf "error.integration -  wfs status 400 Bad Request '%s', url=%s, response body=%s" (ss/limit (str q) 220 "...") url error-text)
                      (errorf "error.integration - wfs status %s: url=%s, response body=%s" data url error-text))
           :ok      (error "error.integration - wfs response body empty for url" url))
         (when throw?
           (throw (ex-info "Integration error"
                           {:status     status
                            :data       data
                            :error-text error-text}))))))))


(defn- exec-get-xml!
  [& args]
  (some-> ^String (apply exec-raw args)
          (CharSequenceInputStream. (Charset/defaultCharset))
          (xml/parse sxml/startparse-sax-no-doctype)))

(defn exec-get-xml
  [& args]
  (let [[_ url & _] args]
    (try
      (apply exec-get-xml! args)
      (catch Exception e
        (errorf "Exception caught in wfs req from url %s %s" url e)
        nil))))

(defn wms-get
  "WMS query with error handling. Returns response body or nil."
  [url query-params user passwd]
  (let [credentials           (when-not (ss/blank? user) (format-basic-auth user passwd))
        options               (merge {:query-params     query-params
                                      :quiet            true
                                      :throw-exceptions false}
                                     credentials)
        {:keys [status body]} (http-get url options)
        error                 (when (ss/includes? body "ServiceException")
                                (-> body ss/trim
                                    (->features startparse-sax-non-validating "UTF-8")
                                    (xml-> :ServiceException)
                                    first text ss/trim))]
    (if (or (not= 200 status) error)
      (errorf "error.integration - Failed to get %s (status %s): %s" url status (log/sanitize 1000 error))
      body)))

;;
;;
;;

(defn- exec-trimble-kaavamaaraykset-post [method url user passwd q picurltemplate]
  (let [[http-fn param-key] (method http-method)
        timeout             (env/value :http-client :conn-timeout)
        request             (merge (format-basic-auth user passwd)
                                   {param-key q})
        task                (future* (exec-http http-fn url request))
        [status data]       (deref task timeout [:timeout])]
    (case status
      :timeout (error "error.integration - wfs timeout from" url)
      :error   (error "error.integration - wfs status" data "from" url)
      :ok      (let [features       (->features data sxml/startparse-sax-no-doctype "UTF-8")
                     muukaavatunnus (first (xml-> features :gml:featureMember :akaava:Kaava :akaava:muuKaavatunnus text))
                     kaavatunnus    (first (xml-> features :gml:featureMember :akaava:Kaava :akaava:kaavatunnus text))
                     arkistotunnus  (first (xml-> features :gml:featureMember :akaava:Kaava :akaava:arkistotunnus text))
                     kaavanimi1     (first (xml-> features :gml:featureMember :akaava:Kaava :akaava:kaavanimi1 text))
                     hyvaksyja      (first (xml-> features :gml:featureMember :akaava:Kaava :akaava:hyvaksyja text))
                     hyvaksymispvm  (first (xml-> features :gml:featureMember :akaava:Kaava :akaava:hyvaksymispvm text))
                     kaavanvaihe    (first (xml-> features :gml:featureMember :akaava:Kaava :akaava:kaavanvaihe text))
                     kaavatyyppi    (first (xml-> features :gml:featureMember :akaava:Kaava :akaava:kaavatyyppi text))]
                 [{"Kaavatunnus"   (str kaavatunnus)
                   "Arkistotunnus" (str arkistotunnus)
                   "Nimi"          (str kaavanimi1)
                   "Hyv."          (str hyvaksyja)
                   "Pvm."          (str hyvaksymispvm)
                   "Vaihe"         (str kaavanvaihe)
                   "Tyyppi"        (str kaavatyyppi)},
                  (for [maarays (xml-> features :gml:featureMember :akaava:Kaava :akaava:yhteisetkaavamaaraykset :akaava:Kaavamaarays)
                        :when   (string? picurltemplate)]
                    {:pic  (format picurltemplate muukaavatunnus (first(xml-> maarays :akaava:tunnus text)))
                     :desc (first(xml-> maarays :akaava:maaraysteksti_primaari text))})]))))


(defn trimble-kaavamaaraykset-post [municipality q]
  (let [k              (keyword municipality)
        url            (env/value :trimble-kaavamaaraykset k :url)
        picurltemplate (env/value :trimble-kaavamaaraykset k :picurltemplate)
        user           (env/value :trimble-kaavamaaraykset k :user)
        passwd         (env/value :trimble-kaavamaaraykset k :passwd)]
    (exec-trimble-kaavamaaraykset-post :post url user passwd q picurltemplate)))

;;
;; Public queries:
;;


(defn select-kuntagml-osoite [stripped-xml]
  (or (seq (sxml/select stripped-xml [:FeatureCollection :featureMember :Osoite]))
      (seq (sxml/select stripped-xml [:Osoite]))))

(defn- query-address! [x y r url creds nobb]
  (let [srs     "EPSG:3067"
        corners [(- x r) (- y r) (+ x r) (+ y r)]
        bbox    (ss/join "," (cond-> corners
                               (not nobb) (conj srs)))]
    (some->> (exec-get-xml! {:method      :get
                             :url         url
                             :credentials creds
                             :q           {:REQUEST     "GetFeature"
                                           :SERVICE     "WFS"
                                           :VERSION     "1.1.0"
                                           :TYPENAME    "mkos:Osoite"
                                           :SRSNAME     srs
                                           :BBOX        bbox
                                           :MAXFEATURES "50"}
                             :throw?      true})
             cr/strip-xml-namespaces
             select-kuntagml-osoite
             not-empty)))

(defn address-by-point-from-municipality
  "Searching by point is not directly supported, we'll search wthin increasing radius. Some
  backends require that the coordinate system (EPSG:3067 for Lupapiste) must be appended
  to the BBOX parameter. However, some backends prohibit it. Thus we try it with both ways,
  where `no-bbox-srs` is the default (do not append)."
  [x y {:keys [url credentials no-bbox-srs]}]
  (letfn [(radii-query [nobb]
            (try
              (some #(query-address! x y % url credentials nobb)
                    [5 10 25 50 100 250 500 1000])
              (catch Exception e
                (warnf "Address query for %s, %s from %s (no-bbox-srs: %s) failed: %s."
                       x y url nobb (or (ex-message e) e)))))]
    (or (radii-query (boolean no-bbox-srs))
        (radii-query (not no-bbox-srs)))))

;;; Plan info functions

;; Workaround for DZIP-6:
;;   Fixing DZIP-3 in 0.1.2 introduced another problem, where traversing a document
;;   like `<a><a>1</a></a>` with `(xml-> document :a :a)` would not work, as the
;;   latter `:a` selector would select the root node and return `<a><a>1</a></a>`
;;   instead of `<a>1</a>`
;;
;;   Workaround taken from https://clojure.atlassian.net/browse/DZIP-6?focusedCommentId=24242
(defn descent=
  "Returns a query predicate that matches a node when its is a tag named tagname."
  [tagname]
  (fn [loc]
      (filter #(and (clojure.zip/branch? %) (= tagname (:tag (clojure.zip/node %))))
               (data.zip/children-auto loc))))

(defn capabilities-to-layers [capabilities]
  (if capabilities
    (xml-> (->features (s/trim capabilities) startparse-sax-non-validating "UTF-8") :Capability :Layer (descent= :Layer))
    []))

(defn layer-name-and-title [layer]
  [(xml1-> layer :Name text) (xml1-> layer :Title text)])

(defn- plan-info-config [municipality type]
  (let [k (keyword municipality)
        t (keyword type)]
    {:url    (or (env/value t k :url) wms-url)
     :layers (or (env/value t k :layers) (str municipality "_asemakaavaindeksi"))
     :format (or (env/value t k :format) "application/vnd.ogc.gml")
     :user   (env/value t k :user)
     :passwd (env/value t k :passwd)}))

(defn plan-info-by-point [x y municipality type]
  (let [bbox                                    [(- (util/->double x) 128)
                                                 (- (util/->double y) 128)
                                                 (+ (util/->double x) 128)
                                                 (+ (util/->double y) 128)]
        {:keys [url layers format user passwd]} (plan-info-config municipality type)
        query                                   {"REQUEST"       "GetFeatureInfo"
                                                 "EXCEPTIONS"    "application/vnd.ogc.se_xml"
                                                 "SERVICE"       "WMS"
                                                 "INFO_FORMAT"   format
                                                 "QUERY_LAYERS"  layers
                                                 "FEATURE_COUNT" "50"
                                                 "Layers"        layers
                                                 "WIDTH"         "256"
                                                 "HEIGHT"        "256"
                                                 "format"        "image/png"
                                                 "styles"        ""
                                                 "srs"           "EPSG:3067"
                                                 "version"       "1.1.1"
                                                 "x"             "128"
                                                 "y"             "128"
                                                 "BBOX"          (s/join "," bbox)}]
    (wms-get url query user passwd)))

;;; Mikkeli is special because it was done first and they use Bentley WMS
(defn- gfi-to-features-bentley [gfi _]
  (when gfi
    (xml-> (->features gfi startparse-sax-non-validating) :FeatureKeysInLevel :FeatureInfo :FeatureKey)))

(defn- gfi-to-features-sito [gfi municipality]
  (when gfi
    (xml-> (->features gfi startparse-sax-non-validating "UTF-8")
           :gml:featureMember
           (keyword (str "lupapiste:" municipality "_asemakaavaindeksi")))))

(defn get-gfi-to-features-fn
  [fn-name]
  (case fn-name
    "lupapalvelu.wfs/gfi-to-features-bentley" gfi-to-features-bentley
    "lupapalvelu.wfs/gfi-to-features-sito"    gfi-to-features-sito
    gfi-to-features-sito))

(defn- feature-to-feature-info-mikkeli  [feature]
  (when feature
    {:id         (first (xml-> feature :property (attr= :name "ID") text))
     :kaavanro   (first (xml-> feature :property (attr= :name "Kaavanro") text))
     :kaavalaji  (first (xml-> feature :property (attr= :name "Kaavalaji") text))
     :kasitt_pvm (first (xml-> feature :property (attr= :name "Kasitt_pvm") text))
     :linkki     (first (xml-> feature :property (attr= :name "Linkki") text))
     :type       "bentley"}))

;;; Valkeakoski is almost same as Mikkeli
(defn- feature-to-feature-info-valkeakoski-plan-info  [feature]
  (when feature
    {:id       (first (xml-> feature :property (attr= :name "IDPR_RENDITION_ID") text))
     :kaavanro (first (xml-> feature :property (attr= :name "kaavatunnus") text))
     :linkki   (first (xml-> feature :property (attr= :name "Linkki2") text))
     :type     "bentley"}))

(defn- feature-to-feature-info-valkeakoski-rakennustapaohje  [feature]
  (when feature
    {:id       (first (xml-> feature :property (attr= :name "IDPR_RENDITION_ID") text))
     :kaavanro (first (xml-> feature :property (attr= :name "kaavatunnus") text))
     :linkki   (first (xml-> feature :property (attr= :name "linkki2") text))
     :type     "bentley"}))

(defn- feature-to-feature-info-sito  [feature]
  (when feature
    {:id             (or (xml1-> feature :lupapiste:TUNNUS text)
                         (xml1-> feature :lupapiste:Tunnus text))
     :kuntanro       (or (xml1-> feature :lupapiste:KUNTA_ID text)
                         (xml1-> feature :lupapiste:Kunta_Id text)
                         (xml1-> feature :lupapiste:Kunta_ID text))
     :kaavanro       (or (xml1-> feature :lupapiste:TUNNUS text)
                         (xml1-> feature :lupapiste:Tunnus text))
     :vahvistett_pvm (or (xml1-> feature :lupapiste:VAHVISTETT text)
                         (xml1-> feature :lupapiste:Vahvistett text))
     :linkki         (or (xml1-> feature :lupapiste:LINKKI text)
                         (xml1-> feature :lupapiste:Linkki text))
     :type           "sito"}))

(defn- feature-to-feature-info-liiteri-ak  [feature]
  (when feature
    {:id       (first (xml-> feature :lupapiste:tunnus text))
     :kuntanro (first (xml-> feature :lupapiste:kuntaid text))
     :kunta    (first (xml-> feature :lupapiste:kunta text))
     :kaavanro (first (xml-> feature :lupapiste:tunnus text))
     :linkki   (first (xml-> feature :lupapiste:merkinnat text))
     :type     "liiteri-ak"}))

(defn get-feature-mapper-fn
  [fn-name]
  (case fn-name
    "lupapalvelu.wfs/feature-to-feature-info-liiteri-ak"                   feature-to-feature-info-liiteri-ak
    "lupapalvelu.wfs/feature-to-feature-info-mikkeli"                      feature-to-feature-info-mikkeli
    "lupapalvelu.wfs/feature-to-feature-info-valkeakoski-plan-info"        feature-to-feature-info-valkeakoski-plan-info
    "lupapalvelu.wfs/feature-to-feature-info-valkeakoski-rakennustapaohje" feature-to-feature-info-valkeakoski-rakennustapaohje
    "lupapalvelu.wfs/feature-to-feature-info-sito"                         feature-to-feature-info-sito
    feature-to-feature-info-sito))

(defn general-plan-info-by-point [x y]
  (let [bbox  [(- (util/->double x) 128)
               (- (util/->double y) 128)
               (+ (util/->double x) 128)
               (+ (util/->double y) 128)]
        query {"REQUEST"       "GetFeatureInfo"
               "EXCEPTIONS"    "application/vnd.ogc.se_xml"
               "SERVICE"       "WMS"
               "INFO_FORMAT"   "application/vnd.ogc.gml"
               "QUERY_LAYERS"  "yleiskaavaindeksi,yleiskaavaindeksi_poikkeavat"
               "FEATURE_COUNT" "50"
               "Layers"        "yleiskaavaindeksi,yleiskaavaindeksi_poikkeavat"
               "WIDTH"         "256"
               "HEIGHT"        "256"
               "format"        "image/png"
               "styles"        ""
               "srs"           "EPSG:3067"
               "version"       "1.1.1"
               "x"             "128"
               "y"             "128"
               "BBOX"          (s/join "," bbox)}]
    (wms-get wms-url query nil nil)))

(defn gfi-to-general-plan-features [gfi]
  (when gfi
    (let [info (->features gfi startparse-sax-non-validating "UTF-8")]
      (set/union
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
   (let [credentials (when-not (ss/blank? username) (format-basic-auth username password))
         options     (merge {:as               :stream
                             :socket-timeout   (env/value :capabilities :socket-timeout),
                             :conn-timeout     (env/value :capabilities :conn-timeout)
                             :query-params     {:request "GetCapabilities", :service service} ;; , :version "1.1.0"
                             :throw-exceptions throw-exceptions?}
                            credentials)]
     (http-get url options))))

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
          (if (ss/includes? body "<?xml")
            true
            (warnf "GetCapabilities response from %s did not contain XML: %s" url (log/sanitize 1000 body))))
        (errorf "error.integration - GetCapabilities from %s returned error %s" url status)))))

(defn get-our-capabilities []
  (slurp (:body (query-get-capabilities wms-url "WMS"))))

(defn trimble-kaavamaaraykset-by-point [x y municipality]
  (trimble-kaavamaaraykset-post municipality (trimble-kaavamaaraykset-request x y municipality) ))

;; Raster images:
;;
(defn raster-images [request service & [query-organization-map-server]]
  (let [{:keys [params headers]} request
        layer                    (ss/blank-as-nil (or (:LAYER params)
                                                      (:LAYERS params)
                                                      (:layer params)))
        headers                  (select-keys headers ["accept" "accept-encoding"])
        conn-timeout             (env/value :mml :conn-timeout)
        socket-timeout           (env/value :mml :socket-timeout)
        base-opts                (merge {:query-params     params
                                         :headers          headers
                                         :as               :stream
                                         :throw-exceptions false}
                                        (if use-hato?
                                          {:http-client @*hato-client}
                                          {:socket-timeout socket-timeout
                                           :conn-timeout   conn-timeout}))]
    (case service
      "nls"          (http-get "https://ws.nls.fi/rasteriaineistot/image"
                               (merge base-opts
                                      (format-basic-auth (:raster auth))))
      ;; Municipality map layers are prefixed. For example: Lupapiste-753-R:wms-layer-name
      "wms"          (if-let [[_ org-id layer] (re-matches #"(?i)Lupapiste-([\d]+-[\w]+):(.+)" layer)]
                       (query-organization-map-server (ss/upper-case org-id)
                                                      (merge params {:LAYERS layer})
                                                      headers)
                       (http-get wms-url base-opts))
      "wmts"         (let [{:keys [username password]} (env/value :wmts :raster)]
                       (some-<>> (case layer
                                   "taustakartta"        "maasto"
                                   "kiinteistojaotus"    "kiinteisto"
                                   "kiinteistotunnukset" "kiinteisto"
                                   (warnf "Unknown wmts layer '%s'." layer))
                                 (format "https://karttakuva.maanmittauslaitos.fi/%s/wmts")
                                 (http-get <> (merge base-opts
                                                     (format-basic-auth username password)))))
      "plandocument" (let [id (:id params)]
                       (assert (ss/numeric? id))
                       (http-get (str "http://194.28.3.37/maarays/" id "x.pdf")
                                 base-opts)))))
