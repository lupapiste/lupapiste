(ns lupapalvelu.wfs
  (:refer-clojure :exclude [and or sort-by filter])
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn errorf fatal]]
            [sade.http :as http]
            [clojure.string :as s]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [sade.env :as env]
            [clojure.data.zip.xml :refer [xml-> text]]
            [sade.strings :refer [starts-with-i]]
            [sade.util :refer [future*]]))

;;
;; config:
;;

(def ktjkii "https://ws.nls.fi/ktjkii/wfs/wfs")
(def maasto "https://ws.nls.fi/maasto/wfs")
(def nearestfeature "https://ws.nls.fi/maasto/nearestfeature")

(def ^:private auth
  (let [conf (env/value :nls)]
    {:raster        [(:username (:raster conf))     (:password (:raster conf))]
     :kiinteisto    [(:username (:kiinteisto conf)) (:password (:kiinteisto conf))]
     ktjkii         [(:username (:ktjkii conf))     (:password (:ktjkii conf))]
     maasto         [(:username (:maasto conf))     (:password (:maasto conf))]
     nearestfeature [(:username (:maasto conf))     (:password (:maasto conf))]}))

;;
;; DSL to WFS queries:
;;

(defn query [attrs & e]
  (str "<?xml version='1.0' encoding='UTF-8'?>
        <wfs:GetFeature version='1.1.0'
            xmlns:oso='http://xml.nls.fi/Osoitteet/Osoitepiste/2011/02'
            xmlns:ktjkiiwfs='http://xml.nls.fi/ktjkiiwfs/2010/02'
            xmlns:wfs='http://www.opengis.net/wfs'
            xmlns:gml='http://www.opengis.net/gml'
            xmlns:ogc='http://www.opengis.net/ogc'
            xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'
            xsi:schemaLocation='http://www.opengis.net/wfs http://schemas.opengis.net/wfs/1.1.0/wfs.xsd'>
          <wfs:Query" (apply str (map (fn [[k v]] (format " %s='%s'" k v)) attrs)) ">"
            (apply str e)
       "  </wfs:Query>
        </wfs:GetFeature>"))

(defn sort-by
  ([property-names]
    (sort-by property-names "desc"))
  ([property-names order]
    (let [sort-properties (apply str (map #(str "<ogc:SortProperty><ogc:PropertyName>" % "</ogc:PropertyName></ogc:SortProperty>") property-names))]
      (str "<ogc:SortBy>"
           sort-properties
           "<ogc:SortOrder>" (s/upper-case order) "</ogc:SortOrder>"
           "</ogc:SortBy>"))))

(defn filter [& e]
  (str "<ogc:Filter>" (apply str e) "</ogc:Filter>"))

(defn and [& e]
  (str "<ogc:And>" (apply str e) "</ogc:And>"))

(defn or [& e]
  (str "<ogc:Or>" (apply str e) "</ogc:Or>"))

(defn intersects [& e]
  (str "<ogc:Intersects>" (apply str e) "</ogc:Intersects>"))

(defn point [x y]
  (format "<gml:Point><gml:pos>%s %s</gml:pos></gml:Point>" x y))

(defn property-name [n]
  (str "<wfs:PropertyName>" n "</wfs:PropertyName>"))

(defn property-filter [filter-name property-name property-value]
  (str
    "<ogc:" filter-name " wildCard='*' singleChar='?' escape='!' matchCase='false'>
       <ogc:PropertyName>" property-name "</ogc:PropertyName>
       <ogc:Literal>" property-value "</ogc:Literal>
     </ogc:" filter-name ">"))

(defn property-is-like [property-name property-value]
  (property-filter "PropertyIsLike" property-name property-value))

(defn property-is-equal [property-name property-value]
  (property-filter "PropertyIsEqualTo" property-name property-value))

(defn property-is-less [property-name property-value]
  (property-filter "PropertyIsLessThan" property-name property-value))

(defn property-is-greater [property-name property-value]
  (property-filter "PropertyIsGreaterThan" property-name property-value))

(defn property-is-between [property-name property-lower-value property-upper-value]
  (str
    "<ogc:PropertyIsBetween wildCard='*' singleChar='?' escape='!' matchCase='false'>
       <ogc:PropertyName>" property-name "</ogc:PropertyName>
       <ogc:LowerBoundary>" property-lower-value "</ogc:LowerBoundary>"
    "  <ogc:UpperBoundary>" property-upper-value "</ogc:UpperBoundary>
     </ogc:PropertyIsBetween>"))

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
     :location {:x x
                :y y}}))

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
            municipality-name (if (starts-with-i fi city) fi sv)]
        (str street " " number ", " municipality-name)))))

(defn feature-to-position [feature]
  (let [[x y] (s/split (first (xml-> feature :ktjkiiwfs:PalstanTietoja :ktjkiiwfs:tunnuspisteSijainti :gml:Point :gml:pos text)) #" ")]
    {:x x :y y}))

(defn feature-to-property-id [feature]
  (when feature
    {:kiinttunnus (first (xml-> feature :ktjkiiwfs:PalstanTietoja :ktjkiiwfs:rekisteriyksikonKiinteistotunnus text))}))

(defn feature-to-address-details [feature]
  (when feature
    {:street (first (xml-> feature :oso:Osoitepiste :oso:osoite :oso:Osoite :oso:katunimi text))
     :number (first (xml-> feature :oso:Osoitepiste :oso:osoite :oso:Osoite :oso:katunumero text))
     :municipality (first (xml-> feature :oso:Osoitepiste :oso:kuntatunnus text))
     :name {:fi (first (xml-> feature :oso:Osoitepiste :oso:kuntanimiFin text))
            :sv (first (xml-> feature :oso:Osoitepiste :oso:kuntanimiSwe text))}}))

(defn response->features [input-xml]
  (when input-xml
    (let [features (-> input-xml
                     (s/replace "UTF-8" "ISO-8859-1")
                     (.getBytes "ISO-8859-1")
                     java.io.ByteArrayInputStream.
                     xml/parse
                     zip/xml-zip)]
      (xml-> features :gml:featureMember))))

;;
;; Executing HTTP calls to Maanmittauslaitos:
;;

(def ^:private http-method {:post [http/post :body]
                            :get  [http/get  :query-params]})

(defn- exec-http [http-fn url request]
  (try
    (let [{status :status body :body} (http-fn url request)]
      (if (= status 200)
        [:ok body]
        [:error status]))
    (catch Exception e
      [:failure e])))

(defn- exec [method url q]
  (let [[http-fn param-key] (method http-method)
        timeout (env/value :http-client :conn-timeout)
        request {:throw-exceptions false
                 :basic-auth (auth url)
                 param-key q}
        task (future* (exec-http http-fn url request))
        [status data] (deref task timeout [:timeout])]
    (condp = status
      :timeout (do (errorf "wfs timeout: url=%s" url) nil)
      :error   (do (errorf "wfs status %s: url=%s" data url) nil)
      :failure (do (errorf data "wfs failure: url=%s" url) nil)
      :ok      (response->features data))))

(defn post [url q]
  (exec :post url q))

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
      (filter
        (intersects
          (property-name "ktjkiiwfs:sijainti")
          (point x y))))))

(defn point-by-property-id [property-id]
  (post ktjkii
    (query {"typeName" "ktjkiiwfs:PalstanTietoja" "srsName" "EPSG:3067"}
      (property-name "ktjkiiwfs:rekisteriyksikonKiinteistotunnus")
      (property-name "ktjkiiwfs:tunnuspisteSijainti")
      (filter
        (property-is-equal "ktjkiiwfs:rekisteriyksikonKiinteistotunnus" property-id)))))

;;
;; Raster images:
;;
(defn raster-images [request service]
  (let [layer (get-in request [:params "LAYERS"])]
    (case service
      "nls" (http/get "https://ws.nls.fi/rasteriaineistot/image"
                {:query-params (:params request)
                 :headers {"accept-encoding" (get-in [:headers "accept-encoding"] request)}
                 :basic-auth (:raster auth)
                 :as :stream})
      ;; TODO: get GeoServer URL from conf
      "wms" (http/get "http://geoserver-qa.lupapiste.fi:8080/geoserver/lupapiste/wms"
                {:query-params (:params request)
                 :headers {"accept-encoding" (get-in [:headers "accept-encoding"] request)}
                 :as :stream}))))
