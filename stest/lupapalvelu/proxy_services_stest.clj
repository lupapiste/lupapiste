(ns lupapalvelu.proxy-services-stest
  (:require [lupapalvelu.backing-system.dmcity.config :as dmcity-config]
            [lupapalvelu.find-address :as find-address]
            [lupapalvelu.itest-util :refer [http200? http400? http404?]]
            [lupapalvelu.json :as json]
            [lupapalvelu.mml.geocoding.core :as geocoding]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.property :as property]
            [lupapalvelu.property-location :as prop-loc]
            [lupapalvelu.proxy-services :refer [address-by-point-proxy find-addresses-proxy
                                                general-plan-urls-by-point-proxy get-addresses-proxy
                                                location-by-property-id-proxy lot-property-id-by-point-proxy
                                                plan-urls-by-point-proxy property-info-by-wkt-proxy]]
            [lupapalvelu.wfs :as wfs]
            [midje.config :as midje-config]
            [midje.sweet :refer :all]
            [sade.coordinate :as coord]
            [sade.env :as env]
            [sade.util :as util]))

(defn proxy-call [proxy-fn params]
  (some-> {:params params}
          proxy-fn
          :body
          (json/decode true)))

(def valid-street? #"^[A-ZÅÄÖa-zåäö -]+$")
(def valid-number? #"^\d+$")

(defn valid-location? [{:keys [x y]}]
  (and (coord/valid-x? x)
       (coord/valid-y? y)))

(defn check-address-result [results street number expected]
  (let [result (util/find-by-keys {:street street :number number} results)]
    (facts
      result => (contains expected)
      (valid-location? (:location result)) => true
      (-> result :location keys) => (just #{:x :y}))))

(facts "find-addresses-proxy"
  (against-background (find-address/search-poi anything) => [])
  (let [r (proxy-call find-addresses-proxy {:term "piiriniitynkatu 9, tampere"
                                            :lang "fi"})]
    (check-address-result r
                          "Piiriniitynkatu"
                          "9"
                          {:kind         "address"
                           :type         "street-number-city"
                           :municipality "837"
                           :name         {:fi "Tampere" :sv "Tammerfors"}}))
  (let [r (proxy-call find-addresses-proxy {:term "piiriniitynkatu"
                                            :lang "fi"})]
    (check-address-result r
                          "Piiriniitynkatu"
                          "1"
                          {:kind         "address"
                           :type         "street"
                           :name         {:fi "Tampere" :sv "Tammerfors"}
                           :municipality "837"})))

(facts "get-address-proxy"
  (against-background [(org/get-krysp-wfs anything anything) => nil
                       (dmcity-config/address-endpoint "837") => nil])
  (fact "empty query, empty response"
    (let [{:keys [suggestions data]} (proxy-call get-addresses-proxy {})]
      suggestions => empty?
      data => empty?))

  (let [r (proxy-call get-addresses-proxy {:query "piiriniitynkatu 9, tampere" :lang "fi"})]
    (fact (:suggestions r) => ["Piiriniitynkatu 9, Tampere"])
    (check-address-result (:data r)
                          "Piiriniitynkatu"
                          "9"
                          {:name         {:fi "Tampere" :sv "Tammerfors"}
                           :municipality "837"}))

  (let [r (proxy-call get-addresses-proxy {:query "piiriniitynkatu 19, tampere"
                                           :lang  "fi"})]
    (fact (:suggestions r) => ["Piiriniitynkatu 19, Tampere"])
    (check-address-result (:data r)
                          "Piiriniitynkatu"
                          "19"
                          {:name         {:fi "Tampere" :sv "Tammerfors"}
                           :municipality "837"}))

  (let [r (proxy-call get-addresses-proxy {:params {:query "Hagrid's Hut, Hogwarts"
                                                    :lang  "fi"}})]
    (fact "Non-existing address"
      r => {:data [] :suggestions []}))

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

  (midje-config/change-defaults :partial-prerequisites false))

(facts "location-by-property-id"
  (against-background [(env/feature? :disable-ktj-on-create) => false
                       (prop-loc/property-lots-from-cache anything) => nil
                       (mongo/insert-batch :propertyCache anything anything) => nil
                       (mongo/select-one :propertyMunicipalityCache anything anything) => nil
                       (property/insert-to-cache anything) => nil])
  (let [property-id "09100200990013"
        request {:params {:property-id property-id}}
        response (location-by-property-id-proxy request)]
    (fact "content-type"
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
        selite => {:fi "Tila", :sv "Lägenhet"}))))

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
  (against-background [(org/get-krysp-wfs anything :osoitteet) => nil
                       (dmcity-config/address-endpoint "837") => nil])
  (let [x        328607
        y        6822240
        request  {:params {:x x :y y}}
        response (address-by-point-proxy request)]
    (fact (get-in response [:headers "Content-Type"]) => "application/json; charset=utf-8")
    (let [body (json/decode (:body response) true)]
      (fact (:street body) => valid-street?)
      (fact (:number body) => valid-number?)
      (fact (:fi (:name body)) => "Tampere"))))

(facts "address-by-point - street number null"
  (against-background [(org/get-krysp-wfs anything :osoitteet) => nil
                       (dmcity-config/address-endpoint "753") => nil])
  (let [x        403827.289
        y        6694204.426
        request  {:params {:x x :y y}}
        response (address-by-point-proxy request)]
    (fact (get-in response [:headers "Content-Type"]) => "application/json; charset=utf-8")
    (let [body (json/decode (:body response) true)]
      (fact (:street body) => valid-street?)
      (fact (:number body) => valid-number?)
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
          body     (json/decode (:body response) true)]
      (count body) => 1
      (first body) => {:id       "000810"
                       :kaavanro "000810"
                       :kunta    "Vantaa"
                       :kuntanro "092"
                       :linkki   "https://mfiles.matti.vantaa.fi/VampattiWebApplication/kaavamaaraykset/000810"
                       :type     "liiteri-ak"})))

(facts "general-plan-urls-by-point-proxy"

 (fact "Helsinki"
   (let [response (general-plan-urls-by-point-proxy {:params {:x "395628" :y "6677704"}})
         body     (json/decode (:body response) true)]
     body => [{:id         "0911010"
               :nimi       "Helsingin uusi yleiskaava - kaupunkikaava"
               :pvm        "26.10.2016"
               :tyyppi     "Kunnan hyväksymä"
               :oikeusvaik "Oikeusvaikutteinen"
               :lisatieto  "Muut karttalehdet löytyvät määräysten yhteydestä. Helsingin maanalainen yleiskaava 2021 kumoaa osia tästä kaavasta."
               :linkki     "https://liiteri.ymparisto.fi/maarays/0911010x.pdf"
               :type       "yleiskaava"}])))

(facts "WMTS layers"
  (let [base-params {:FORMAT        "image/png"
                     :SERVICE       "WMTS"
                     :VERSION       "1.0.0"
                     :REQUEST       "GetTile"
                     :STYLE         "default"
                     :TILEMATRIXSET "ETRS-TM35FIN"
                     :TILEMATRIX    "11"
                     :TILEROW       "1247"
                     :TILECOL       "891"}]
    (doseq [layer [{:LAYER "taustakartta"}
                   {:LAYER "kiinteistojaotus"}
                   {:LAYER "kiinteistotunnukset"}]]
      (fact {:midje/description (get layer "LAYERS")}
        (let [request {:params  (merge base-params layer)
                       :headers {"accept-encoding" "gzip, deflate"}}]
          (wfs/raster-images request "wmts") => http200?)))

    (fact "Error response is returned to caller"
      (let [request {:params  (merge base-params {:LAYER   "taustakartta"
                                                  :TILECOL "-1"
                                                  :TILEROW "3"})
                     :headers {"accept-encoding" "gzip, deflate"}}]
        (wfs/raster-images request "wmts") => http404?))

    (fact "Unsupported layer"
      (wfs/raster-images {:params (merge base-params {:LAYERS  "nowhere"
                                                      :TILECOL "1"
                                                      :TILEROW "2"})}
                         "wmts") => nil)))

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

(facts "Get address from Turku"
  (against-background (org/get-krysp-wfs anything :osoitteet) => {:url "https://opaskartta.turku.fi/TeklaOGCWeb/WFS.ashx"})
  (fact "get-addresses-proxy - this may fail if Turku backend doesn't respond, as MML fallback has different coords"
    (let [response (get-addresses-proxy {:params {:query "Linnankatu 80, Turku" :lang "fi"}})
          body (json/decode (:body response) true)]
      (fact (:suggestions body) => (contains "Linnankatu 80, Turku"))
      (fact (first (:data body)) => (contains {:street valid-street?,
                                               :number valid-number?,
                                               :name {:fi "Turku" :sv "Åbo"}
                                               :municipality "853"}))))

  (fact "address-by-point-proxy"
    (let [response (address-by-point-proxy {:params {:lang "fi" :x "237557" :y "6709410"}})
          body (json/decode (:body response) true)]
      (fact (:street body) => valid-street?)
      (fact (:number body) => valid-number?)
      (fact (:fi (:name body)) => "Turku"))))

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
      (fact (:street body) => valid-street?)
      (fact (:number body) => valid-number?)
      (fact (:fi (:name body)) => "Salo"))))

(facts "Get address from Naantali municipality server"
  (let [x 224772
        y 6713382]
    (fact "address-by-point-proxy"
      (-> (address-by-point-proxy {:params {:lang "fi" :x (str x) :y (str y)}})
          :body (json/decode true))
      => (contains {:municipality "529"
                    :name         {:fi "Naantali" :sv "Nådendal"}
                    :number       valid-number?
                    :street       valid-street?})
      (provided
        (org/get-krysp-wfs anything :osoitteet)
        => {:url "http://kartta.naantali.fi:82/geoserver/wfs/krysp?/outputFormat=KRYSP"}))

    (fact "NLS also finds an address"
      (geocoding/address-by-point! "fi" x y)
      => (contains {:municipality "529"
                    :name         {:fi "Naantali" :sv "Nådendal"}
                    :number       valid-number?
                    :street       valid-street?}))))

(facts "Municipality number with address"
  (against-background [(org/get-krysp-wfs anything :osoitteet) => nil
                       (dmcity-config/address-endpoint "186") => nil])
  (fact "Jarvenpaa, in border of Mantsala"
    (let [x "399309.136" y "6709508.629"
          response (address-by-point-proxy {:params {:lang "fi" :x x :y y}})
          body (json/decode (:body response) true)]
      (fact (:street body) => valid-street?)                 ; Street data is from MML Nearestfeature
      (fact (:number body) => valid-number?)
      (fact (:municipality body) => "186")                  ; Municipality data is from MML KTJKii (more accurate)
      (fact (:fi (:name body)) => "Järvenpää")

      (fact "only using NLS address-by-point WFS would actually give the same results.."
        (let [details (geocoding/address-by-point! "fi" x y)]
          (:municipality details) => "186"
          (get-in details [:name :fi]) => "Järvenpää")))))

(def jakku {:x "436885.026" :y "7242129.297"})
(def jarvenpaa-point {:x "399309.136" :y "6709508.629"})
(def raasepori-island {:x "296734.231" :y "6647154.2190031"})

(facts "property-info-by-point from KTJKii"                 ; LPK-3683
  (fact "Jakkukyla"
    ;; was formerly in Oulu, property id indicates that
    (prop-loc/property-id-muni-by-point (:x jakku) (:y jakku))
    => (contains {:municipality "139",
                  :name         {:fi "Ii", :sv "Ii"},
                  :propertyId   valid-number?}))
  (fact "Jarvenpaa in border of Mantsala"
    (prop-loc/property-id-muni-by-point (:x jarvenpaa-point) (:y jarvenpaa-point))
    => (contains {:municipality "186"
                  :propertyId   valid-number?
                  :name         {:fi "Järvenpää"
                                 :sv "Träskända"}}))
  (fact "Island inside Raasepori"
    (prop-loc/property-id-muni-by-point (:x raasepori-island) (:y raasepori-island))
    => (contains {:municipality "710"
                  :propertyId   valid-number?
                  :name         {:fi "Raasepori"
                                 :sv "Raseborg"}})))
