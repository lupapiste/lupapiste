(ns lupapalvelu.wfs
  (:refer-clojure :exclude [and or sort-by filter])
  (:require [clj-http.client :as client]
            [clojure.string :as s]
            [clojure.xml :as xml]
            [clojure.zip :as zip])
  (:use [clojure.data.zip.xml :only [xml-> text]]
        [lupapalvelu.env :only [config]]
        [lupapalvelu.strings :only [starts-with-i]]))

(def ^:private auth [(:username (:nls config)) (:password (:nls config))])

(def ^:private timeout 30000)

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
  ([property-name]
    (sort-by property-name "desc"))
  ([property-name order]
    (str "<ogc:SortBy>
            <ogc:SortProperty>
              <ogc:PropertyName>" property-name "</ogc:PropertyName>
            </ogc:SortProperty>
            <ogc:SortOrder>" (.toUpperCase order) "</ogc:SortOrder>
          </ogc:SortBy>")))

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

(defn- address-part [feature part]
  (first (xml-> feature :oso:Osoitenimi part text)))

(defn feature-to-address [feature]
  (let [[x y] (s/split (address-part feature :oso:sijainti) #" ")]
    {:katunimi (address-part feature :oso:katunimi)
     :katunumero (address-part feature :oso:katunumero)
     :kuntanimiFin (address-part feature :oso:kuntanimiFin)
     :kuntanimiSwe (address-part feature :oso:kuntanimiSwe)
     :kuntatunnus (address-part feature :oso:kuntatunnus)
     :x x
     :y y}))

(defn feature-to-simple-address-string [feature]
  (let [{:keys [katunimi katunumero kuntanimiFin]} (feature-to-address feature)]
    (str katunimi " " katunumero ", " kuntanimiFin)))

(defn feature-to-address-string [[street number city]]
  (if (s/blank? city)
    (fn [feature]
      (let [{:keys [katunimi kuntanimiFin]} (feature-to-address feature)]
        (str katunimi ", " kuntanimiFin)))
    (fn [feature]
      (let [{:keys [katunimi katunumero kuntanimiFin kuntanimiSwe]} (feature-to-address feature)
            kuntanimi (if (starts-with-i kuntanimiFin city) kuntanimiFin kuntanimiSwe)]
        (str katunimi " " katunumero ", " kuntanimi)))))

(defn feature-to-position [feature]
  (let [[x y] (s/split (first (xml-> feature :ktjkiiwfs:PalstanTietoja :ktjkiiwfs:tunnuspisteSijainti :gml:Point :gml:pos text)) #" ")]
    {:x x :y y}))

(defn feature-to-property-id [feature]
  {:kiinttunnus (first (xml-> feature :ktjkiiwfs:PalstanTietoja :ktjkiiwfs:rekisteriyksikonKiinteistotunnus text))})

(defn response->features [response]
  (let [input-xml (:body response)
       features (-> input-xml
                  (s/replace "UTF-8" "ISO-8859-1")
                  (.getBytes "ISO-8859-1")
                  java.io.ByteArrayInputStream.
                  xml/parse
                  zip/xml-zip)]
    (xml-> features :gml:featureMember)))

(def ktjkii "https://ws.nls.fi/ktjkii/wfs/wfs")
(def maasto "https://ws.nls.fi/maasto/wfs")

(defn execute
  "Takes a query (in XML) and returns a vector. If the first element of that
   vector is :ok, then the next element is a list of features that match the
   query. If the first element is :error, the next element is the HTTP response.
   Finally, in case of time-out, a vector [:timeout] is returned."
  [url q]
  (deref
    (future
      (let [response (client/post url {:body q :basic-auth auth :throw-exceptions false})]
        (if (= (:status response) 200)
          [:ok (response->features response)]
          [:error response])))
    timeout
    [:timeout]))

;;
;; Raster images:
;;

(defn raster-images [request]
  (client/get "https://ws.nls.fi/rasteriaineistot/image"
    {:query-params (:query-params request)
     :headers {"accept-encoding" (get-in [:headers "accept-encoding"] request)}
     :basic-auth auth
     :as :stream}))
