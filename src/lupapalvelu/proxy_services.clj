(ns lupapalvelu.proxy-services
  (:require [clj-http.client :as client]
            [noir.response :as resp]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.string :as string])
  (:use [clojure.data.zip.xml]))
;;
;; NLS:
;;  
(def auth ["***REMOVED***" "***REMOVED***"])

(defn osoite [request]
  (let [kunta (get (:query-params request) "kunta")
        katunimi (get (:query-params request) "katunimi")
        katunumero (get (:query-params request) "katunumero")
        input-xml (:body (client/post "https://ws.nls.fi/maasto/wfs"
	  {:body (format "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
						<wfs:GetFeature version=\"1.1.0\"
						xmlns:oso=\"http://xml.nls.fi/Osoitteet/Osoitepiste/2011/02\"
						xmlns:wfs=\"http://www.opengis.net/wfs\"
						xmlns:gml=\"http://www.opengis.net/gml\"
						xmlns:ogc=\"http://www.opengis.net/ogc\"
						xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
						xsi:schemaLocation=\"http://www.opengis.net/wfs
						http://schemas.opengis.net/wfs/1.1.0/wfs.xsd\">
						 <wfs:Query typeName=\"oso:Osoitenimi\">
						  <ogc:Filter>
               <ogc:And>
                <ogc:Or>
                 <ogc:PropertyIsLike wildCard=\"*\" singleChar=\"?\" escape=\"!\" matchCase=\"false\">
                  <ogc:PropertyName>oso:kuntanimiFin</ogc:PropertyName>
                  <ogc:Literal>%s*</ogc:Literal>
                 </ogc:PropertyIsLike>
                 <ogc:PropertyIsLike wildCard=\"*\" singleChar=\"?\" escape=\"!\" matchCase=\"false\">
                  <ogc:PropertyName>oso:kuntanimiSwe</ogc:PropertyName>
                  <ogc:Literal>%s*</ogc:Literal>
                 </ogc:PropertyIsLike>
                </ogc:Or>
                <ogc:PropertyIsLike wildCard=\"*\" singleChar=\"?\" escape=\"!\" matchCase=\"false\">
                 <ogc:PropertyName>oso:katunimi</ogc:PropertyName>
                 <ogc:Literal>%s*</ogc:Literal>
                </ogc:PropertyIsLike>
                <ogc:PropertyIsLike wildCard=\"*\" singleChar=\"?\" escape=\"!\" matchCase=\"false\">
                 <ogc:PropertyName>oso:katunumero</ogc:PropertyName>
                 <ogc:Literal>%s*</ogc:Literal>
                </ogc:PropertyIsLike>
               </ogc:And>
              </ogc:Filter>						
						 </wfs:Query>
						</wfs:GetFeature>" kunta kunta katunimi katunumero)
	   :basic-auth auth}))
        features (zip/xml-zip (xml/parse (java.io.ByteArrayInputStream. (.getBytes (string/replace input-xml "UTF-8" "ISO-8859-1")))))]
      (resp/json (->> (xml-> features :gml:featureMember)
                   (map (fn [feature] { :katunimi (first (xml-> feature :oso:Osoitenimi :oso:katunimi text))
                                        :katunumero (first (xml-> feature :oso:Osoitenimi :oso:katunumero text))
                                        :kuntanimiFin (first (xml-> feature :oso:Osoitenimi :oso:kuntanimiFin text))
                                        :kuntanimiSwe (first (xml-> feature :oso:Osoitenimi :oso:kuntanimiSwe text))
                                        :x (first (string/split (first (xml-> feature :oso:Osoitenimi :oso:sijainti text)) #" "))
                                        :y (second (string/split (first (xml-> feature :oso:Osoitenimi :oso:sijainti text)) #" "))}))))))

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
               "osoite" (secure osoite)})
