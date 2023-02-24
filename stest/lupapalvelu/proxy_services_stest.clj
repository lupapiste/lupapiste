(ns lupapalvelu.proxy-services-stest
  (:require [clojure.data.zip.xml :refer [xml-> xml1-> text]]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.json :as json]
            [lupapalvelu.mml.geocoding.core :as geocoding]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.property-location :as prop-loc]
            [lupapalvelu.proxy-services :refer :all]
            [lupapalvelu.wfs :as wfs]
            [midje.config :as midje-config]
            [midje.sweet :refer :all]
            [mount.core :as mount]
            [sade.coordinate :as coord]
            [sade.core :refer [now]]
            [sade.env :as env]))

; make sure proxies are enabled:

(http-post (str (server-address) "/api/proxy-ctrl/on") {})

(mount/start #'mongo/connection)

(defn- proxy-request [apikey proxy-name & args]
  (:body (http-post
           (str (server-address) "/proxy/" (name proxy-name))
           {:headers        {"content-type" "application/json;charset=utf-8"}
            :oauth-token    apikey
            :socket-timeout 10000
            :conn-timeout   10000
            :body           (json/encode (apply hash-map args))
            :as             :json})))

(facts "find-addresses-proxy"
  (against-background (org/get-krysp-wfs anything :osoitteet) => nil)
  (let [r (first (proxy-request mikko :find-address :term "piiriniitynkatu 9, tampere" :lang "fi"))]
    (fact r => (contains {:kind         "address"
                          :type         "street-number-city"
                          :street       "Piiriniitynkatu"
                          :number       "9"
                          :municipality "837"
                          :name         {:fi "Tampere" :sv "Tammerfors"}}))
    (fact (-> r :location keys) => (just #{:x :y})))
  (let [r (proxy-request mikko :find-address :term "piiriniitynkatu" :lang "fi")]
    (fact r => (contains {:kind         "address"
                          :type         "street"
                          :street       "Piiriniitynkatu"
                          :number       "1"
                          :location     {:x 320531.265 :y 6825180.25}
                          :name         {:fi "Tampere" :sv "Tammerfors"}
                          :municipality "837"}))
    (fact (-> r first :location keys) => (just #{:x :y}))))

(facts "get-address-proxy"
  (fact "empty query, empty response"
    (let [{:keys [suggestions data]} (-> (get-addresses-proxy {:params {}}) :body (json/decode true))]
      suggestions => empty?
      data => empty?))

  (let [response (get-addresses-proxy {:params {:query "piiriniitynkatu 9, tampere" :lang "fi"}})]
    (fact "no errors" (:status response) => nil?)
    (let [r (json/decode (:body response) true)]
      (fact (:suggestions r) => ["Piiriniitynkatu 9, Tampere"])
      (fact (:data r) => (contains {:street       "Piiriniitynkatu",
                                    :location     {:x 320371.953 :y 6825180.72}
                                    :number       "9",
                                    :name         {:fi "Tampere" :sv "Tammerfors"}
                                    :municipality "837"}))
      (fact (-> r :data first :location keys) => (just #{:x :y}))))
  (let [response (get-addresses-proxy {:params {:query "piiriniitynkatu 19, tampere" :lang "fi"}})]
    (fact "no errors" (:status response) => nil?)
    (let [r (json/decode (:body response) true)]
      (fact (:suggestions r) => ["Piiriniitynkatu 19, Tampere"])
      (fact (:data r) => (contains {:street       "Piiriniitynkatu",
                                    :location     {:x 320199.606 :y 6825190.72}
                                    :number       "19",
                                    :name         {:fi "Tampere" :sv "Tammerfors"}
                                    :municipality "837"}))
      (fact (-> r :data first :location keys) => (just #{:x :y}))))

  (let [response (get-addresses-proxy {:params {:query "Hagrid's Hut, Hogwarts"
                                                :lang  "fi"}})]
    (fact "no errors" (:status response) => nil?)
    (let [r (-> response :body (json/decode true))]
      (fact "Non-existing address"
        r => {:data [] :suggestions []})))

  ;; env/value requires this
  (midje-config/change-defaults :partial-prerequisites true)

  (fact "Timeout"
    (get-addresses-proxy {:params {:query "piiriniitynkatu 9, tampere"
                                   :lang  "fi"}})
    => {:status 503 :body "Service temporarily unavailable"}
    (provided
      (env/value :mml :conn-timeout) => 1
      (env/value :mml :socket-timeout) => 1
      (env/value :mml :geocoding :url) => "https://sopimus-paikkatieto.maanmittauslaitos.fi"
      (env/value :mml :geocoding :username) => "user"
      (env/value :mml :geocoding :password) => "password"
      (env/value :http-client) => {:socket-timeout 60000
                                   :conn-timeout   60000
                                   :insecure?      false}
      (lupapalvelu.organization/municipality-address-endpoint anything) => nil))

  (midje-config/change-defaults :partial-prerequisites false)

  #_(fact "Other error"
    (with-redefs [wfs/maasto (str (server-address) "/dev/statusecho/404")]
      (get-addresses-proxy {:params {:query "piiriniitynkatu 9, tampere"
                                    :lang  "fi"}}))
    => {:status 404 :body "<FOO>Echo 404 status</FOO>"}))

(facts "location-by-property-id"
  (against-background (env/feature? :disable-ktj-on-create) => false)
  (let [property-id "09100200990013"
        request {:params {:property-id property-id}}
        response (location-by-property-id-proxy request)]
    (fact "content-tyoe"
      (get-in response [:headers "Content-Type"]) => "application/json; charset=utf-8")
    (let [{:keys [x y municipality] :as body} (json/decode (:body response) true)]
      (fact "response is a map"
        (map? body) => true
        (keys body) => (contains #{:x :y :municipality :propertyId} :gaps-ok)
        x => string?
        y => string?)
      (fact "also municipality is returned"
        municipality => "091")
      (fact "valid x" x => coord/valid-x?)
      (fact "valid y" y => coord/valid-y?))

    (let [new-body (-> (location-by-property-id-proxy request)
                       :body
                       (json/decode true))]
      (fact "although hits cache, same data is returned"
        (keys new-body) => (contains #{:x :y :municipality :propertyId} :gaps-ok)))))

(facts "property-id-by-point"
  (let [x 385648
        y 6672157
        request {:params {:x x :y y}}
        response (lot-property-id-by-point-proxy request)]
    (fact (get-in response [:headers "Content-Type"]) => "application/json; charset=utf-8")
    (let [body (json/decode (:body response) true)]
      (fact body => "09100200990013"))))

(defn property-info-for-75341600380021 [params]
  (let [response (property-info-by-wkt-proxy {:params params})]
    (fact (get-in response [:headers "Content-Type"]) => "application/json; charset=utf-8")
    (let [body (json/decode (:body response) true)
          {:keys [x y rekisteriyksikkolaji kiinttunnus kunta wkt nimi]} (first body)
          {:keys [id selite]} rekisteriyksikkolaji]

      (fact "collection format"
        (count body) => 1
        (keys (first body)) => (just #{:x :y :rekisteriyksikkolaji :kiinttunnus :kunta :wkt :nimi}))
      (fact "valid x" x => coord/valid-x?)
      (fact "valid y" y => coord/valid-y?)
      (fact "kiinttunnus" kiinttunnus => "75341600380021")
      (fact "kunta" kunta => "753")
      (fact "nimi"
        (:fi nimi) => "Sipoo"
        (:sv nimi) => "Sibbo")
      (fact "wkt" wkt => #"^POLYGON")
      (fact "rekisteriyksikkolaji"
        id => "1"
        selite => {:fi "Tila", :sv "L\u00e4genhet"})))
  )

(facts "property-info-by-point"
  (fact "missing params"
    (let [response (property-info-by-wkt-proxy {:params {}})]
      response => map?
      (:status response) => 400))

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
      (fact (:street body) => "Tanhuankatu")
      (fact (:number body) => #"\d")
      (fact (:fi (:name body)) => "Tampere"))))

(facts "address-by-point - street number null"
  (against-background (org/get-krysp-wfs anything :osoitteet) => nil)
  (let [x 403827.289
        y 6694204.426
        request {:params {:x x :y y}}
        response (address-by-point-proxy request)]
    (fact (get-in response [:headers "Content-Type"]) => "application/json; charset=utf-8")
    (let [body (json/decode (:body response) true)]
      (fact (:street body) => "Brobölentie")
      (fact (:number body) => #"\d")
      (fact (:fi (:name body)) => "Sipoo"))))

(facts "address-by-point - outside Finland"
  (against-background (org/get-krysp-wfs anything :osoitteet) => nil)
  (let [x 229337
        y 7669179
        request {:params {:x x :y y}}
        response (address-by-point-proxy request)]
    response => http404?))

(facts "address-by-point - invalid ETRS-TM35FIN coordinates"
  (against-background (org/get-krysp-wfs anything :osoitteet) => nil)
  (let [x 9999
        y 7669179
        request {:params {:x x :y y}}
        response (address-by-point-proxy request)]
    response => http400?))

(facts "plan-urls-by-point-proxy"

  (fact "Liiteri Vantaa"
    (let [response (plan-urls-by-point-proxy {:params {:x "391489.5625" :y "6685643.5" :municipality "liiteri"}})
          body (json/decode (:body response) true)]
      (count body) => 1
      (first body) => {:id "000810"
                       :kaavanro "000810"
                       :kunta "Vantaa"
                       :kuntanro "092"
                       :linkki "https://mfiles.matti.vantaa.fi/VampattiWebApplication/kaavamaaraykset/000810"
                       :type "liiteri-ak"}))


  (fact "Mikkeli"
    (let [response (plan-urls-by-point-proxy {:params {:x "533257.514" :y "6828489.823" :municipality "491"}})
          body (json/decode (:body response) true)]
      (count body) => 2
      (first body) => {:id "635"
                       :kaavalaji "akm"
                       :kaavanro "14019"
                       :kasitt_pvm "4/22/1982 12:00:00 AM"
                       :linkki "http://194.111.49.141/asemakaavapdf/14019.pdf"
                       :type "bentley"}

      (second body) => {:id "678"
                        :kaavalaji "akm"
                        :kaavanro "14032"
                        :kasitt_pvm "8/28/1989 12:00:00 AM"
                        :linkki "http://194.111.49.141/asemakaavapdf/14032.pdf"
                        :type "bentley"})))

(facts "general-plan-urls-by-point-proxy"

 (fact "Helsinki"
   (let [response (general-plan-urls-by-point-proxy {:params {:x "395628" :y "6677704"}})
         body (json/decode (:body response) true)]
     (first body) => {:id "0912014"
                      :nimi "Helsingin maanalainen yleiskaava 2021"
                      :pvm "16.06.2021"
                      :tyyppi "Kunnan hyv\u00e4ksym\u00e4"
                      :oikeusvaik "Oikeusvaikutteinen"
                      :lisatieto nil
                      :linkki "http://liiteri.ymparisto.fi/maarays/0912014x.pdf"
                      :type "yleiskaava"}
     (second body) => {:id "0911010"
                       :nimi "Helsingin uusi yleiskaava - kaupunkikaava"
                       :pvm "26.10.2016"
                       :tyyppi "Kunnan hyv\u00e4ksym\u00e4"
                       :oikeusvaik "Oikeusvaikutteinen"
                       :lisatieto "Muut karttalehdet löytyvät määräysten yhteydestä. Helsingin maanalainen yleiskaava 2021 kumoaa osia tästä kaavasta."
                       :linkki "http://liiteri.ymparisto.fi/maarays/0911010x.pdf"
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
                    "BBOX"   "358400,6758400,409600,6809600"}]]
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

#_(fact "General plan documents. NO LONGER WORKS."
  (let [request {:params {:id "0911001"}
                 :headers {"accept-encoding" "gzip, deflate"}}]
    (wfs/raster-images request "plandocument") => http404?))

(facts "Get address from Turku"
  (against-background (org/get-krysp-wfs anything :osoitteet) => {:url "https://opaskartta.turku.fi/TeklaOGCWeb/WFS.ashx"})
  (fact "get-addresses-proxy - this may fail if Turku backend doesn't respond, as MML fallback has different coords"
    (let [response (get-addresses-proxy {:params {:query "Linnankatu 80, Turku" :lang "fi"}})
          body (json/decode (:body response) true)]
      (fact (:suggestions body) => (contains "Linnankatu 80, Turku"))
      (fact (first (:data body)) => (contains {:street "Linnankatu",
                                               :number "80",
                                               :name {:fi "Turku" :sv "\u00c5bo"}
                                               :municipality "853"}))))

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
      (fact (:suggestions body) => (contains "Liljankuja 6, Helsinki"))
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
    (:municipality (prop-loc/property-id-muni-by-point 386169.912 6671577.21)) => "091")

  (fact "Jalasjarvi, as of 2016 part of Kurikka and shoud return Kurikka's code"
    (:municipality (prop-loc/property-id-muni-by-point 281160 6936532.8125001)) => "301"))

(facts "Get address from Salo"
  (against-background (org/get-krysp-wfs anything :osoitteet) => {:url "http://kartta.salo.fi/teklaogcweb/wfs.ashx" :no-bbox-srs true})

  (fact "address-by-point-proxy"
    (let [response (address-by-point-proxy {:params {:lang "fi" :x "281813" :y "6702378"}})
          body (json/decode (:body response) true)]
      (fact (:street body) => "Vanha Turuntie")
      (fact (:number body) => "222")
      (fact (:fi (:name body)) => "Salo")
      )))

(facts "Get address from Porvoo municipality server"
  (against-background (org/get-krysp-wfs anything :osoitteet) => {:url "https://sitogis.sito.fi/ows/handler.ashx?xmlset=porvoo"})
  (fact "address-by-point-proxy"
    (let [response (address-by-point-proxy {:params {:lang "fi" :x "425909" :y "6695518"}})
          body (json/decode (:body response) true)]
      (fact (:street body) => "Laamanninkatu")
      (fact (:number body) => "1")
      (fact (:fi (:name body)) => "Porvoo")
      ))
  (fact "NLS responds something else"
    (geocoding/address-by-point! "fi" 425909 6695518) => (contains [:municipality "638"]
                                                                   [:street "Aleksanterinkaari"]
                                                                   [:number "12"])))

(facts "Municipality number with address"
  (against-background (org/get-krysp-wfs anything :osoitteet) => nil)
  (fact "Jarvenpaa, in border of Mantsala"
    (let [x "399309.136" y "6709508.629"
          response (address-by-point-proxy {:params {:lang "fi" :x x :y y}})
          body (json/decode (:body response) true)]
      (fact (:street body) => "Kulmapolku")                 ; Street data is from MML Nearestfeature
      (fact (:number body) => "37")
      (fact (:municipality body) => "186")                  ; Municipality data is from MML KTJKii (more accurate)
      (fact (:fi (:name body)) => "J\u00e4rvenp\u00e4\u00e4")

      (fact "only using NLS address-by-point WFS would actually give the same results.."
        (let [details (geocoding/address-by-point! "fi" x y)]
          (:municipality details) => "186"
          (get-in details [:name :fi]) => "Järvenpää")))))

(def jakku {:x "436885.026" :y "7242129.297"})
(def jarvenpaa-point {:x "399309.136" :y "6709508.629"})
(def raasepori-island {:x "296734.231" :y "6647154.2190031"})

(facts "property-info-by-point from KTJKii"                 ; LPK-3683
  (fact "Jakkukyla"
    ; was formerly in Oulu, property id indicates that
    (prop-loc/property-id-muni-by-point (:x jakku) (:y jakku)) => (contains {:municipality "139",
                                                                          :name         {:fi "Ii", :sv "Ii"},
                                                                          :propertyId   "56442100060084"}))
  (fact "Jarvenpaa in border of Mantsala"
    (prop-loc/property-id-muni-by-point (:x jarvenpaa-point) (:y jarvenpaa-point)) => (contains {:municipality "186"
                                                                                              :propertyId   "18640100090041"
                                                                                              :name         {:fi "J\u00e4rvenp\u00e4\u00e4"
                                                                                                             :sv "Tr\u00e4sk\u00e4nda"}}))
  (fact "Island inside Raasepori"
    (prop-loc/property-id-muni-by-point (:x raasepori-island) (:y raasepori-island)) => (contains {:municipality "710"
                                                                                                :propertyId   "71042400010059"
                                                                                                :name         {:fi "Raasepori"
                                                                                                               :sv "Raseborg"}})))

(facts "Several Palstas"
  ; A place in Järvenpää...
  (let [[x y] ["394586.881" "6707894.303"]]
    (fact "seven palstas"
      (-> (wfs/property-info-by-point x y)
          (first) ; first feature
          (xml-> :ktjkiiwfs:RekisteriyksikonTietoja :ktjkiiwfs:rekisteriyksikonPalstanTietoja)
          (count)) => 7)))


(facts "Municipality info from KTJKii"
  (fact "Jakkukyla"
    (->> (wfs/municipality-info-by-property-id "56442100060084")
         (wfs/feature-to-property-id-municipality :ktjkiiwfs:RekisteriyksikonSijaintitiedot))
    => {:propertyId "56442100060084" :municipality "139"
        :name       {:fi "Ii", :sv "Ii"}}))

(facts "New KTJKii WFS 2.0.0 service"
  (let [[x y] ["404271" "6693892"]]
    (facts "ktjkiiwfs:PalstanSijaintitiedot"
      (fact "sipoo propertyid"
        (->> (wfs/lot-property-id-by-point-wfs2 x y)
             (first)
             (wfs/feature-to-property-id-wfs2))
        => {:kiinttunnus "75341600380021"})
      (fact "tough bastards"
        (fact "raasepori island"
          (->> (wfs/lot-property-id-by-point-wfs2 (:x raasepori-island) (:y raasepori-island))
               (first)
               (wfs/feature-to-property-id-wfs2))
          => {:kiinttunnus "71042400010059"})
        (fact "jarvenpaa-point"
          (->> (wfs/lot-property-id-by-point-wfs2 (:x jarvenpaa-point) (:y jarvenpaa-point))
               (first)
               (wfs/feature-to-property-id-wfs2))
          => {:kiinttunnus "18640100090041"}))

      (fact "lot by property-id returns sequence of lots with Polygon"
        (->> (wfs/lots-by-property-id-wfs2 "71042400010059")
             (map wfs/lot-feature-to-location))
        => (just '({:kiinttunnus "71042400010059"
                    :lot-id "19713990"
                    :wkt "POLYGON((296718.279 6647133.913, 296659.084 6647144.960, 296675.200 6647221.928, 296676.225 6647226.822, 296682.197 6647226.267, 296684.189 6647226.096, 296686.147 6647225.714, 296686.559 6647225.576, 296688.360 6647224.711, 296689.577 6647224.049, 296691.698 6647223.182, 296693.685 6647223.020, 296694.625 6647223.049, 296696.394 6647223.966, 296697.160 6647224.477, 296698.842 6647225.558, 296700.456 6647226.734, 296700.989 6647227.237, 296702.223 6647228.809, 296702.796 6647229.624, 296704.005 6647231.217, 296705.246 6647232.601, 296706.723 6647233.947, 296707.654 6647234.652, 296709.369 6647235.677, 296711.263 6647236.290, 296711.590 6647236.327, 296713.571 6647236.139, 296714.958 6647235.711, 296716.757 6647234.848, 296717.367 6647234.366, 296718.526 6647232.751, 296718.950 6647231.825, 296719.600 6647229.936, 296720.022 6647228.226, 296720.324 6647226.252, 296720.329 6647224.816, 296720.343 6647222.823, 296720.526 6647222.337, 296720.967 6647221.854, 296722.337 6647220.398, 296723.922 6647218.942, 296727.476 6647217.115, 296727.990 6647216.751, 296729.325 6647215.267, 296730.190 6647214.181, 296731.631 6647212.797, 296732.263 6647212.234, 296733.693 6647210.838, 296737.794 6647206.463, 296739.342 6647204.967, 296742.414 6647202.407, 296743.856 6647201.025, 296744.384 6647200.416, 296745.508 6647198.766, 296746.385 6647196.971, 296747.127 6647195.114, 296747.494 6647194.101, 296748.172 6647192.221, 296748.881 6647190.642, 296750.966 6647187.232, 296751.178 6647186.833, 296752.015 6647185.018, 296752.880 6647183.514, 296754.399 6647182.230, 296755.568 6647181.540, 296757.348 6647180.629, 296758.593 6647180.168, 296760.558 6647179.819, 296761.661 6647179.720, 296763.647 6647179.788, 296764.146 6647180.071, 296764.987 6647181.576, 296765.840 6647183.389, 296766.031 6647185.375, 296766.015 6647187.241, 296766.281 6647189.219, 296766.631 6647190.609, 296767.220 6647192.520, 296767.352 6647192.892, 296768.143 6647194.726, 296769.207 6647196.358, 296770.649 6647197.528, 296772.806 6647197.430, 296774.369 6647196.221, 296774.732 6647195.644, 296775.404 6647193.452, 296776.104 6647191.877, 296776.994 6647191.065, 296778.962 6647190.205, 296780.949 6647190.350, 296781.286 6647190.408, 296782.700 6647190.960, 296783.050 6647191.871, 296784.462 6647193.212, 296784.815 6647193.334, 296786.800 6647193.412, 296787.133 6647193.384, 296789.120 6647193.168, 296790.199 6647192.937, 296792.161 6647191.921, 296793.704 6647190.654, 296794.244 6647190.159, 296793.369 6647185.235, 296783.076 6647127.230, 296781.609 6647121.503, 296718.279 6647133.913))"
                    :x "296706.586"
                    :y "6647161.239"}))))

    ; KiinteistotunnuksenSijaintitiedot sounds tempting, but it doesn't seem to find data for
    ; some crucial test points
    (facts "ktjkiiwfs:KiinteistotunnuksenSijaintiedot"
      (fact "testing Raasepori island property-id location"
        (-> (wfs/property-points-by-id-wfs2 "71042400010059")
            (first)
            (xml1-> :ktjkiiwfs:KiinteistotunnuksenSijaintitiedot :ktjkiiwfs:kiinteistotunnuksenSijainti :gml:Point text))
        => "296706.586 6647161.239"))))
