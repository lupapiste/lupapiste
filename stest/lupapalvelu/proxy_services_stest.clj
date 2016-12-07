(ns lupapalvelu.proxy-services-stest
  (:require [lupapalvelu.proxy-services :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [lupapalvelu.wfs :as wfs]
            [lupapalvelu.organization :as org]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.fixture.core :as fixture]
            [sade.core :refer [now]]
            [sade.env :as env]
            [sade.coordinate :as coord]
            [cheshire.core :as json]))

; make sure proxies are enabled:
(http-post (str (server-address) "/api/proxy-ctrl/on") {})

(defn- proxy-request [apikey proxy-name & args]
  (:body (http-post
           (str (server-address) "/proxy/" (name proxy-name))
           {:headers {"content-type" "application/json;charset=utf-8"}
            :oauth-token apikey
            :socket-timeout 10000
            :conn-timeout 10000
            :body (json/encode (apply hash-map args))
            :as :json})))

(facts "find-addresses-proxy"
  (against-background (org/get-krysp-wfs anything :osoitteet) => nil)
  (let [r (proxy-request mikko :find-address :term "piiriniitynkatu 9, tampere" :lang "fi")]
    (fact r =contains=> {:kind         "address"
                         :type         "street-number-city"
                         :street       "Piiriniitynkatu"
                         :number       "9"
                         :municipality "837"
                         :name         {:fi "Tampere" :sv "Tammerfors"}})
    (fact (-> r first :location keys) => (just #{:x :y})))
  (let [r (proxy-request mikko :find-address :term "piiriniitynkatu" :lang "fi")]
    (fact r =contains=> [{:kind         "address"
                          :type         "street"
                          :street       "Piiriniitynkatu"
                          :number       "1"
                          :name         {:fi "Tampere" :sv "Tammerfors"}
                          :municipality "837"}])
    (fact (-> r first :location keys) => (just #{:x :y})))

  (facts "empty query, empty response"
    (let [{:keys [suggestions data]} (-> (get-addresses-proxy {:params {}}) :body (json/decode true))]
      suggestions => empty?
      data => empty?))

  (let [response (get-addresses-proxy {:params {:query "piiriniitynkatu 9, tampere" :lang "fi"}})
        r (json/decode (:body response) true)]
    (fact (:suggestions r) => ["Piiriniitynkatu 9, Tampere"])
    (fact (:data r) =contains=> [{:street       "Piiriniitynkatu",
                                  :number       "9",
                                  :name         {:fi "Tampere" :sv "Tammerfors"}
                                  :municipality "837"}])
    (fact (-> r :data first :location keys) => (just #{:x :y})))
  (let [response (get-addresses-proxy {:params {:query "piiriniitynkatu 19, tampere" :lang "fi"}})
        r (json/decode (:body response) true)]
    (fact (:suggestions r) => ["Piiriniitynkatu 19, Tampere"])
    (fact (:data r) =contains=> [{:street       "Piiriniitynkatu",
                                  :number       "19",
                                  :name         {:fi "Tampere" :sv "Tammerfors"}
                                  :municipality "837"}])
    (fact (-> r :data first :location keys) => (just #{:x :y}))))

(facts "point-by-property-id"
  (against-background
    (mongo/select :propertyCache anything) => nil
    (mongo/insert-batch :propertyCache anything anything) => nil)
  (let [property-id "09100200990013"
        request {:params {:property-id property-id}}
        response (point-by-property-id-proxy request)]
    (fact (get-in response [:headers "Content-Type"]) => "application/json; charset=utf-8")
    (let [body (json/decode (:body response) true)
          data (:data body)
          {:keys [x y]} (first data)]
      (fact "collection format"
        (count data) => 1
        (keys (first data)) => (just #{:x :y})
        x => string?
        y => string?)
      (fact "valid x" x => coord/valid-x?)
      (fact "valid y" y => coord/valid-y?))))

(facts "property-id-by-point"
  (let [x 385648
        y 6672157
        request {:params {:x x :y y}}
        response (property-id-by-point-proxy request)]
    (fact (get-in response [:headers "Content-Type"]) => "application/json; charset=utf-8")
    (let [body (json/decode (:body response) true)]
      (fact body => "09100200990013"))))

(defn property-info-for-75341600380021 [params]
  (let [response (property-info-by-wkt-proxy {:params params})]
    (fact (get-in response [:headers "Content-Type"]) => "application/json; charset=utf-8")
    (let [body (json/decode (:body response) true)
          {:keys [x y rekisteriyksikkolaji kiinttunnus kunta wkt]} (first body)
          {:keys [id selite]} rekisteriyksikkolaji]

      (fact "collection format"
        (count body) => 1
        (keys (first body)) => (just #{:x :y :rekisteriyksikkolaji :kiinttunnus :kunta :wkt}))
      (fact "valid x" x => coord/valid-x?)
      (fact "valid y" y => coord/valid-y?)
      (fact "kiinttunnus" kiinttunnus => "75341600380021")
      (fact "kunta" kunta => "753")
      (fact "wkt" wkt => #"^POLYGON")
      (fact "rekisteriyksikkolaji"
        id => "1"
        selite => {:fi "Tila", :sv "L\u00e4genhet"})))
  )

(facts "property-info-by-point"
  (fact "missing params"
    (let [response (property-info-by-wkt-proxy {:params {}})]
      response => map?
      (:status response) => 503))

  (fact "404271,6693892"
    (property-info-for-75341600380021 {:wkt "POINT(404271 6693892)"})))

(facts "property-info-by-point with radius"
  (property-info-for-75341600380021 {:wkt "POINT(404271 6693892)", :radius "30"}))

(facts "property-info-by-line"
  (property-info-for-75341600380021 {:wkt "LINESTRING(404271 6693892,404273 6693895)"}))

(facts "property-info-by-polygon"
  (property-info-for-75341600380021 {:wkt "POLYGON((404270 6693890,404270 6693895,404275 6693890,404270 6693890))"}))

(facts "address-by-point - street number not null"
  (against-background (org/get-krysp-wfs anything :osoitteet) => nil)
  (let [x 333168
        y 6822000
        request {:params {:x x :y y}}
        response (address-by-point-proxy request)]
    (fact (get-in response [:headers "Content-Type"]) => "application/json; charset=utf-8")
    (let [body (json/decode (:body response) true)]
      (fact (:street body) => "Luhtaankatu")
      (fact (:number body) => #"\d")
      (fact (:fi (:name body)) => "Tampere"))))

(fact "address-by-point-proxy - municipality is retured even if no address found"
  (against-background (org/get-krysp-wfs anything :osoitteet) => nil)
  (let [x 296734.231 ; Island in Raasepori
        y 6647154.2190031
        request {:params {:x x :y y}}
        response (address-by-point-proxy request)]
    (fact (get-in response [:headers "Content-Type"]) => "application/json; charset=utf-8")
    (let [body (json/decode (:body response) true)]
      (fact "empty street data"
        (:street body) => ""
        (:number body) => "")
      (fact "Municipality code" (:municipality body) => "710")
      (fact "Municipality name" (:fi (:name body)) => "Raasepori"))))

(facts "address-by-point - street number null"
  (against-background (org/get-krysp-wfs anything :osoitteet) => nil)
  (let [x 403827.289
        y 6694204.426
        request {:params {:x x :y y}}
        response (address-by-point-proxy request)]
    (fact (get-in response [:headers "Content-Type"]) => "application/json; charset=utf-8")
    (let [body (json/decode (:body response) true)]
      (fact (:street body) => "Kirkkomaanpolku")
      (fact (:number body) => #"\d")
      (fact (:fi (:name body)) => "Sipoo"))))

(facts "address-by-point - outside Finland"
  (against-background (org/get-krysp-wfs anything :osoitteet) => nil)
  (let [x 229337
        y 7669179
        request {:params {:x x :y y}}
        response (address-by-point-proxy request)]
    response => http400?))

(facts "plan-urls-by-point-proxy"

  ;; TODO: Testaa "Liiteri"

  (fact "Helsinki"
   (let [response (plan-urls-by-point-proxy {:params {:x "395628" :y "6677704" :municipality "091"}})
         body     (json/decode (:body response) true)]
     (first body) => {:id "8755"
                      :kuntanro "91"
                      :kaavanro "8755"
                      :vahvistett_pvm "19.12.1985"
                      :linkki "http://img.sito.fi/kaavamaaraykset/91/8755.pdf"
                      :type "sito"}))

  #_(fact "Mikkeli"
    (let [response (plan-urls-by-point-proxy {:params {:x "533257.514" :y "6828489.823" :municipality "491"}})
          body (json/decode (:body response) true)]

      (first body) => {:id "1436"
                       :kaavanro "12891"
                       :kaavalaji "RKM"
                       :kasitt_pvm "3/31/1989 12:00:00 AM"
                       :linkki "http://194.111.49.141/asemakaavapdf/12891.pdf"
                       :type "bentley"}

      (second body) => {:id "1440"
                        :kaavanro "12021"
                        :kaavalaji "RK"
                        :kasitt_pvm "6/1/1984 12:00:00 AM"
                        :linkki "http://194.111.49.141/asemakaavapdf/12021.pdf"
                        :type "bentley"})))

(facts "general-plan-urls-by-point-proxy"

 (fact "Helsinki"
   (let [response (general-plan-urls-by-point-proxy {:params {:x "395628" :y "6677704"}})
         body (json/decode (:body response) true)]
     (first body) => {:id "0912007"
                      :nimi "Helsingin maanalainen kaava"
                      :pvm "2010-12-08"
                      :tyyppi "Kunnan hyv\u00e4ksym\u00e4"
                      :oikeusvaik "Oikeusvaikutteinen"
                      :lisatieto ""
                      :linkki "http://liiteri.ymparisto.fi/maarays/0912007x.pdf"
                      :type "yleiskaava"}
     (second body) => {:id "0911001"
                       :nimi "Helsingin yleiskaava 2002"
                       :pvm "2003-11-26"
                       :tyyppi "Kunnan hyv\u00e4ksym\u00e4"
                       :oikeusvaik "Oikeusvaikutteinen"
                       :lisatieto "Kaupungin toimittamasta aineistosta puuttuu etel\u00e4inen eli merellinen osa"
                       :linkki "http://liiteri.ymparisto.fi/maarays/0911001x.pdf"
                       :type "yleiskaava"})))

(facts "geoserver-layers"
  (let [base-params {"FORMAT" "image/png"
                     "SERVICE" "WMS"
                     "VERSION" "1.1.1"
                     "REQUEST" "GetMap"
                     "STYLES"  ""
                     "SRS"     "EPSG:3067"
                     "BBOX"   "444416,6666496,444672,6666752"
                     "WIDTH"   "256"
                     "HEIGHT" "256"}]
    (doseq [layer [{"LAYERS" "lupapiste:491_asemakaava"
                    "BBOX"   "512000,6837760,514560,6840320"}
                   {"LAYERS" "lupapiste:109_asemakaava"
                    "BBOX"   "358400,6758400,409600,6809600"}
                   {"LAYERS" "lupapiste:109_kantakartta"
                    "BBOX"   "358400,6758400,409600,6809600"}
                   {"LAYERS" "lupapiste:Naantali_Asemakaavayhdistelma_Velkua"
                    "BBOX"   "208384,6715136,208640,6715392"}
                   {"LAYERS" "lupapiste:Naantali_Asemakaavayhdistelma_Naantali"
                    "BBOX"   "226816,6713856,227328,6714368"}]]
      (fact {:midje/description (get layer "LAYERS")}
        (let [request {:query-params (merge base-params layer)
                       :headers {"accept-encoding" "gzip, deflate"}
                       :as :stream}]
          (http-get (env/value :maps :geoserver) request) => http200?)))))

(facts "WMTS layers"
  (let [base-params {:FORMAT "image/png"
                     :SERVICE "WMTS"
                     :VERSION "1.0.0"
                     :REQUEST "GetTile"
                     :STYLE "default"
                     :TILEMATRIXSET "ETRS-TM35FIN"
                     :TILEMATRIX "11"
                     :TILEROW "1247"
                     :TILECOL "891"}]
    (doseq [layer [{:LAYER "taustakartta"}
                   {:LAYER "kiinteistojaotus"}
                   {:LAYER "kiinteistotunnukset"}]]
      (fact {:midje/description (get layer "LAYERS")}
        (let [request {:params (merge base-params layer)
                       :headers {"accept-encoding" "gzip, deflate"}}]
          (wfs/raster-images request "wmts") => http200?)))

    (fact "Error response is returned to caller"
      (let [request {:params (merge base-params {:LAYER "taustakartta"
                                                 :TILECOL "-1"
                                                 :TILEROW "3"})
                     :headers {"accept-encoding" "gzip, deflate"}}]
        (wfs/raster-images request "wmts") => http404?))))

(facts "WMS layers"
  (let [base-params {:FORMAT "image/png"
                     :SERVICE "WMS"
                     :VERSION "1.1.1"
                     :REQUEST "GetMap"
                     :STYLES  ""
                     :SRS     "EPSG:3067"
                     :BBOX   "444416,6666496,444672,6666752"
                     :WIDTH   "256"
                     :HEIGHT "256"}]
    (doseq [layer [{:LAYERS "taustakartta_5k"}
                   {:LAYERS "taustakartta_10k"}
                   {:LAYERS "taustakartta_20k"}
                   {:LAYERS "taustakartta_40k"}
                   {:LAYERS "ktj_kiinteistorajat" "TRANSPARENT" "TRUE"}
                   {:LAYERS "ktj_kiinteistotunnukset" "TRANSPARENT" "TRUE"}
                   {:LAYERS "yleiskaava"}
                   {:LAYERS "yleiskaava_poikkeavat"}]]
      (fact {:midje/description (get layer "LAYERS")}
        (let [request {:params (merge base-params layer)
                       :headers {"accept-encoding" "gzip, deflate"}}]
         (wfs/raster-images request "wms") => http200?)))))

(fact "WMS capabilites"
  (http-get (str (server-address) "/proxy/wmscap")
    {:query-params {:v (str (now))}
     :throw-exceptions false}) => http200?)

(fact "General plan documents"
  (let [request {:params {:id "0911001"}
                 :headers {"accept-encoding" "gzip, deflate"}}]
    (wfs/raster-images request "plandocument") => http200?))

(facts "Get address from Turku"
  #_(against-background (org/get-krysp-wfs anything :osoitteet) => {:url "http://kartta.kuopio.fi/TeklaOGCWeb/wfs.ashx"})
  #_(fact "get-addresses-proxy"
    (let [response (get-addresses-proxy {:params {:query "Isokaari 10, Kuopio" :lang "fi"}})
          body (json/decode (:body response) true)]
      (fact (:suggestions body) =contains=> "Linnankatu 80, Turku")
      (fact (first (:data body)) => (contains {:street "Linnankatu",
                                              :number "80",
                                              :name {:fi "Turku" :sv "\u00c5bo"}
                                              :municipality "853"
                                              :location {:x 237551.371, :y 6709441.9}
                                              }))))

  (fact "address-by-point-proxy"
    (let [response (address-by-point-proxy {:params {:lang "fi" :x "237557" :y "6709410"}})
          body (json/decode (:body response) true)]
      (fact (:street body) => "Linnankatu")
      (fact (:number body) => "80")
      (fact (:fi (:name body)) => "Turku"))))

#_(facts "Get address from Helsinki test service"
  (against-background (org/get-krysp-wfs anything :osoitteet) => {:url "http://212.213.116.162/geos_facta/wfs?request"})
  (fact "get-addresses-proxy"
    (let [response (get-addresses-proxy {:params {:query "Liljankuja 6, helsinki" :lang "fi"}})
          body (json/decode (:body response) true)]
      (fact (:suggestions body) =contains=> "Liljankuja 6, Helsinki")
      (fact (first (:data body)) => {:street "Liljankuja",
                                     :number "6",
                                     :name {:fi "Helsinki" :sv "Helsingfors"}
                                     :municipality "186"
                                     :location {:x 394978.12474035326,
                                                :y 6706574.7845555935}})))


  ; The point is in Jarvenpaa although the address refers to Helsinki.
  ; Test data has Liljankuja 6 at the same point location as the first result.

  (fact "address-by-point-proxy"
    (let [response (address-by-point-proxy {:params {:lang "fi" :x "394978" :y "6706574"}})
          body (json/decode (:body response) true)]
      (fact (:street body) => "Liljankuja")
      (fact (:number body) => "6")
      (fact (:fi (:name body)) => "J\u00e4rvenp\u00e4\u00e4"))))

(facts "municipality-by-point"
  (fact "Helsinki"
    (municipality-by-point 386169.912 6671577.21) => "091")

  (fact "Jalasjarvi, as of 2016 part of Kurikka and shoud return Kurikka's code"
    (municipality-by-point 281160 6936532.8125001) => "301"))

(facts "Get address from Salo"
  (against-background (org/get-krysp-wfs anything :osoitteet) => {:url "http://kartta.salo.fi/teklaogcweb/wfs.ashx"})

  (fact "address-by-point-proxy"
    (let [response (address-by-point-proxy {:params {:lang "fi" :x "279444.75" :y "6703424.390625"}})
          body (json/decode (:body response) true)]
      (fact (:street body) => "Nikkil\u00e4ntie")
      (fact (:number body) => "33")
      (fact (:fi (:name body)) => "Salo")
      )))
