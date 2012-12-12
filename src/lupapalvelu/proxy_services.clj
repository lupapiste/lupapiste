(ns lupapalvelu.proxy-services
  (:require [clj-http.client :as client]
            [noir.response :as resp]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.string :as s])
  (:use [clojure.data.zip.xml]
        [lupapalvelu.log]))

;;
;; NLS:
;;

(def auth ["***REMOVED***" "***REMOVED***"])

(def osoite-template
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
   <wfs:GetFeature version=\"1.1.0\"
       xmlns:oso=\"http://xml.nls.fi/Osoitteet/Osoitepiste/2011/02\"
       xmlns:wfs=\"http://www.opengis.net/wfs\"
       xmlns:gml=\"http://www.opengis.net/gml\"
       xmlns:ogc=\"http://www.opengis.net/ogc\"
       xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
       xsi:schemaLocation=\"http://www.opengis.net/wfs
       http://schemas.opengis.net/wfs/1.1.0/wfs.xsd\">
     <wfs:Query typeName=\"oso:Osoitenimi\">
       <ogc:SortBy>    
         <ogc:SortProperty>
           <ogc:PropertyName>
             oso:kuntanimiFin
           </ogc:PropertyName>
         </ogc:SortProperty>             
         <ogc:SortOrder>
           DESC
         </ogc:SortOrder> 
       </ogc:SortBy>
       <ogc:Filter>
         <ogc:And>
           <ogc:PropertyIsLike wildCard=\"*\" singleChar=\"?\" escape=\"!\" matchCase=\"false\">
             <ogc:PropertyName>oso:katunimi</ogc:PropertyName>
             <ogc:Literal>%s*</ogc:Literal>
           </ogc:PropertyIsLike>   
           <ogc:PropertyIsEqualTo>
             <ogc:PropertyName>oso:jarjestysnumero</ogc:PropertyName>
             <ogc:Literal>1</ogc:Literal>
           </ogc:PropertyIsEqualTo>                     
         </ogc:And>
       </ogc:Filter>            
     </wfs:Query>
   </wfs:GetFeature>")

(defn- address-part [feature part]
  (first (xml-> feature :oso:Osoitenimi part text)))

(defn- feature-to-address [feature]
  (let [[x y] (s/split (address-part feature :oso:sijainti) #" ")]
    {:katunimi (address-part feature :oso:katunimi)
     :katunumero (address-part feature :oso:katunumero)
     :kuntanimiFin (address-part feature :oso:kuntanimiFin)
     :kuntanimiSwe (address-part feature :oso:kuntanimiSwe)
     :x x
     :y y}))

(defn- feature-to-address-string [feature]
  (let [address (feature-to-address feature)]
    (str (:katunimi address) ", " (:kuntanimiFin address))))

(defn- haku-kunta [search]
  (let [terms (s/split search #",")]
    (if (= 2 (count terms))
      (s/trim (second terms))
      "")))

(defn- haku-katunumero [search]
  (let [katunumero (read-string (s/trim (last (s/split (first (s/split search #",")) #"\s"))))]
    (if (number? katunumero)
      katunumero
      "")))

(defn- haku-katunimi [search]  
  (let [parts (s/split (first (s/split search #",")) #"\s")]  
    (if (number? (haku-katunumero search))  
      (s/trim (s/join " " (take (- (count parts) 1) parts)))  
      (s/trim (s/join " " parts)))))

(defn parse-address [query]
  (let [[[_ street number city]] (re-seq #"([^,\d]+)\s*(\d+)?\s*(?:,\s*([\w ]+))?" query)
        street (if street (s/trim street))
        city (if (s/blank? city) nil (s/trim city))]
    {:street street
     :number number
     :city city}))

(defn not-blank? [& s]
  (every? (complement s/blank?) s))

(defn get-address-request [{street :street number :number city :city}]
  (if (not-blank? street city)
    (format osoite-template street) ; TODO
    (format osoite-template street)))

(defn osoite [request]
  (debug "resolving address: query='%s'" (get (:query-params request) "query"))
  (let [query (get (:query-params request) "query")
        address (parse-address query)
        request (get-address-request address)
        task (future (client/post "https://ws.nls.fi/maasto/wfs" {:body request :basic-auth auth :throw-exceptions false}))
        response (deref task 3000 {:status 408 :body "timeout"})]
    (case (:status response)
      200 (do
            (let [input-xml (:body response)
                  features (-> input-xml
                             (s/replace "UTF-8" "ISO-8859-1")
                             (.getBytes "ISO-8859-1")
                             java.io.ByteArrayInputStream.
                             xml/parse
                             zip/xml-zip)
                  feature-members (xml-> features :gml:featureMember)
                  feature-count (count feature-members)]
              (debug "Received %d addresses" feature-count)
              (if (> feature-count 15)
                {:status 413 :body "too-many"}
                (resp/json {:query query
                            :suggestions (map feature-to-address-string feature-members)
                            :data (map feature-to-address feature-members)}))))
      408 response
      (do
        (error "Address query failed: status=%s response=%s" (:status response) (str response))
        (resp/status 500 "ups")))))

(def pointbykiinteistotunnus-template
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
   <wfs:GetFeature version=\"1.1.0\"
       xmlns:ktjkiiwfs=\"http://xml.nls.fi/ktjkiiwfs/2010/02\" xmlns:wfs=\"http://www.opengis.net/wfs\"
       xmlns:gml=\"http://www.opengis.net/gml\" xmlns:ogc=\"http://www.opengis.net/ogc\"
       xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
       xsi:schemaLocation=\"http://www.opengis.net/wfs
       http://schemas.opengis.net/wfs/1.1.0/wfs.xsd\">
     <wfs:Query typeName=\"ktjkiiwfs:PalstanTietoja\" srsName=\"EPSG:3067\">
       <wfs:PropertyName>ktjkiiwfs:rekisteriyksikonKiinteistotunnus</wfs:PropertyName>
       <wfs:PropertyName>ktjkiiwfs:tunnuspisteSijainti</wfs:PropertyName>
       <ogc:Filter>
         <ogc:PropertyIsEqualTo>
           <ogc:PropertyName>ktjkiiwfs:rekisteriyksikonKiinteistotunnus</ogc:PropertyName>
           <ogc:Literal>%s</ogc:Literal>
         </ogc:PropertyIsEqualTo>
       </ogc:Filter>
     </wfs:Query>
   </wfs:GetFeature>")

(defn- feature-to-position [feature]
  (let [[x y] (s/split (first (xml-> feature :ktjkiiwfs:PalstanTietoja :ktjkiiwfs:tunnuspisteSijainti :gml:Point :gml:pos text)) #" ")]
    {:x x :y y}))

(defn pointbykiinteistotunnus [request]
  (let [kiinteistotunnus (get (:query-params request) "kiinteistotunnus")
        input-xml (:body (client/post "https://ws.nls.fi/ktjkii/wfs/wfs" {:body (format pointbykiinteistotunnus-template kiinteistotunnus) :basic-auth auth}))
        features (-> input-xml (s/replace "UTF-8" "ISO-8859-1") (.getBytes "ISO-8859-1") java.io.ByteArrayInputStream. xml/parse zip/xml-zip)
        result (map feature-to-position (xml-> features :gml:featureMember))]
    (resp/json result)))

(def kiinteistotunnusbypoint-template
  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
   <wfs:GetFeature version=\"1.1.0\" xmlns:ktjkiiwfs=\"http://xml.nls.fi/ktjkiiwfs/2010/02\"
       xmlns:wfs=\"http://www.opengis.net/wfs\" xmlns:gml=\"http://www.opengis.net/gml\"
       xmlns:ogc=\"http://www.opengis.net/ogc\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"  
       xsi:schemaLocation=\"http://www.opengis.net/wfs http://schemas.opengis.net/wfs/1.1.0/wfs.xsd\">
     <wfs:Query typeName=\"ktjkiiwfs:PalstanTietoja\" srsName=\"EPSG:3067\">
       <wfs:PropertyName>ktjkiiwfs:rekisteriyksikonKiinteistotunnus</wfs:PropertyName>
       <wfs:PropertyName>ktjkiiwfs:tunnuspisteSijainti</wfs:PropertyName>
       <ogc:Filter>
         <ogc:Intersects>
           <ogc:PropertyName>ktjkiiwfs:sijainti</ogc:PropertyName>
           <gml:Point>
             <gml:pos>%s %s</gml:pos>
           </gml:Point>
         </ogc:Intersects>
       </ogc:Filter>
     </wfs:Query>
   </wfs:GetFeature>")

(defn- feature-to-kiinteistotunnus [feature]
  {:kiinttunnus (first (xml-> feature :ktjkiiwfs:PalstanTietoja :ktjkiiwfs:rekisteriyksikonKiinteistotunnus text))})

(defn kiinteistotunnusbypoint
  ([{query-params :query-params}]
    (kiinteistotunnusbypoint (query-params "x") (query-params "y")))
  ([x y]
    (let [request-body (format kiinteistotunnusbypoint-template x y)
          response (client/post "https://ws.nls.fi/ktjkii/wfs/wfs" {:body request-body :basic-auth auth})
          input-xml (:body response)
          features (-> input-xml (s/replace "UTF-8" "ISO-8859-1") (.getBytes "ISO-8859-1") java.io.ByteArrayInputStream. xml/parse zip/xml-zip)
          result (map feature-to-kiinteistotunnus (xml-> features :gml:featureMember))]
      (resp/json result))))

(defn nls [request]
  (client/get "https://ws.nls.fi/rasteriaineistot/image"
    {:query-params (:query-params request)
     :headers {"accept-encoding" (get-in [:headers "accept-encoding"] request)}
     :basic-auth auth
     :as :stream}))

;
; Utils:
;

(defn- secure
  "Takes a service function as an argument and returns a proxy function that invokes the original
  function. Proxy function returns what ever the service function returns, excluding some unsafe
  stuff. At the moment strips the 'Set-Cookie' headers."
  [f]
  (fn [request]
    (let [resp (f request)]
      (assoc resp :headers (dissoc (:headers resp) "set-cookie")))))

;;
;; Proxy services by name:
;;

(def services {"nls" (secure nls)
               "pointbykiinteistotunnus" (secure pointbykiinteistotunnus)
               "kiinteistotunnusbypoint" (secure kiinteistotunnusbypoint)
               "osoite" (secure osoite)})
