(ns lupapalvelu.proxy-services
  (:require [clj-http.client :as client]
            [noir.response :as resp]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.string :as string])
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
  (let [[x y] (string/split (address-part feature :oso:sijainti) #" ")]
    {:katunimi (address-part feature :oso:katunimi)
     :katunumero (address-part feature :oso:katunumero)
     :kuntanimiFin (address-part feature :oso:kuntanimiFin)
     :kuntanimiSwe (address-part feature :oso:kuntanimiSwe)
     :x x
     :y y}))

(defn- haku-kunta [search]
  (let [terms (string/split search #",")]
    (if (= 2 (count terms))
      (string/trim (second terms))
      "")))

(defn- haku-katunumero [search]
  (let [katunumero (read-string (string/trim (last (string/split (first (string/split search #",")) #"\s"))))]
    (if (number? katunumero)
      katunumero
      "")))

(defn- haku-katunimi [search]  
  (let [parts (string/split (first (string/split search #",")) #"\s")]  
    (if (number? (haku-katunumero search))  
      (string/trim (string/join " " (take (- (count parts) 1) parts)))  
      (string/trim (string/join " " parts)))))

(defn osoite [request]
  (let [query (get (:query-params request) "query")]
    (resp/json {:query query :suggestions ["foo" "foozzaa" "foozoo"] :data ["FO" "FU" "FI"]}))
  
  #_(let [haku (get (:query-params request) "query")
        kunta (haku-kunta haku)
        katunimi (haku-katunimi haku)
        katunumero (haku-katunumero haku)
        request (format osoite-template #_kunta #_kunta katunimi #_katunumero)
        response (client/post "https://ws.nls.fi/maasto/wfs" {:body request :basic-auth auth :throw-exceptions false})]
    (if (= (:status response) 200)
      (let [input-xml (:body response)
            features (-> input-xml
                       (string/replace "UTF-8" "ISO-8859-1")
                       .getBytes
                       java.io.ByteArrayInputStream.
                       xml/parse
                       zip/xml-zip)
            feature-members (xml-> features :gml:featureMember)
            result (map feature-to-address feature-members)]
        (resp/json {:ok true :result result}))
      (do
        (error "Address query failed: status=%s response=%s" (:status response) (str response))
        (resp/json {:ok false :error "address-query-failed"})))))

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
  (let [[x y] (string/split (first (xml-> feature :ktjkiiwfs:PalstanTietoja :ktjkiiwfs:tunnuspisteSijainti :gml:Point :gml:pos text)) #" ")]
    {:x x :y y}))

(defn pointbykiinteistotunnus [request]
  (let [kiinteistotunnus (get (:query-params request) "kiinteistotunnus")
        input-xml (:body (client/post "https://ws.nls.fi/ktjkii/wfs/wfs" {:body (format pointbykiinteistotunnus-template kiinteistotunnus) :basic-auth auth}))
        features (-> input-xml (string/replace "UTF-8" "ISO-8859-1") .getBytes java.io.ByteArrayInputStream. xml/parse zip/xml-zip)
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
          features (-> input-xml (string/replace "UTF-8" "ISO-8859-1") .getBytes java.io.ByteArrayInputStream. xml/parse zip/xml-zip)
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
