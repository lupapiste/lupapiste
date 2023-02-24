(ns lupapalvelu.gis.gis-itest
  (:require [clojure.core.memoize :as memo]
            [lupapalvelu.fixture.core :as fix]
            [lupapalvelu.gis.gis-api :as gis-api]
            [lupapalvelu.itest-util :as itu :refer [pena ok?]]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [monger.operators :refer :all])
  (:import [java.net SocketTimeoutException]))

(facts "Unit tests"
  (facts "by-lang"
    (gis-api/by-lang {:en "Hello" :fi "Moi"} "en" "fi") => "Hello"
    (gis-api/by-lang {:en "Hello" :fi "Moi"} :en :fi) => "Hello"
    (gis-api/by-lang {:en "Hello" :fi "Moi"} "en" "asdasd") => "Hello"
    (gis-api/by-lang {:en "Hello" :fi "Moi"} "sv" "fi") => "Moi"
    (gis-api/by-lang {:en "Hello" :fi "Moi"} :sv :fi) => "Moi"
    (gis-api/by-lang {:en "Hello" :fi "Moi"} "sv" "ch") => nil
    (gis-api/by-lang nil "sv" "ch") => nil)

  (let [layer  {:baseLayerId "foo"
                :id          "bar"
                :isBaseLayer false
                :name        {:en "Name"
                              :fi "Nimi"
                              :sv "Namn"}
                :subtitle    {:en "en-sub" :fi "fi-sub" :sv "sv-sub"}
                :wmsName     "layer-name"
                :wmsUrl      "/proxy/wms"}
        result {:id         "layer-name"
                :url        "/proxy/wms"
                :title      "Nimi"
                :subtitle   "fi-sub"
                :format     "image/png"
                :projection "EPSG:3067"
                :protocol   "WMS"}]
    (facts "process-layer"
      (gis-api/process-layer "fi" layer) => result
      (gis-api/process-layer "bad" layer) => result
      (gis-api/process-layer "en" layer)
      => (assoc result :title "Name" :subtitle "en-sub")
      (gis-api/process-layer "en" (-> layer
                                      (assoc-in [:name :en] "  ")
                                      (assoc-in [:subtitle :en] "")))
      => result
      (gis-api/process-layer "sv" (dissoc layer :subtitle))
      => (-> result
             (assoc :title "Namn")
             (dissoc  :subtitle))
      (gis-api/process-layer "fi" (assoc-in layer [:subtitle :fi] ""))
      => (dissoc result :subtitle)
      (gis-api/process-layer "fi" (dissoc layer :wmsUrl)) => nil)

    (facts "process-capabilities"
      (gis-api/process-layers "fi" []) => empty?
      (gis-api/process-layers "fi" [layer]) => [result]
      (gis-api/process-layers "fi" [layer {:id "bad"} layer])
      => [result result])))



(mongo/with-db itu/test-db-name
  (fix/apply-fixture "minimal")
  (itu/with-local-actions
    (memo/memo-clear! gis-api/cached-capabilities)
    (facts "Testing GIS data endpoint"
      (let [app-id (itu/create-app-id pena
                                      :x 329072
                                      :y 6823200
                                      :propertyId "83712103620001"
                                      :address "Pub Harald")
            resp   (itu/query pena :application-gis-data :id app-id :lang "fi")]
        (fact "was ok"
          resp => ok?)
        (fact "contains location"
          (get-in resp [:application :location]) => [(double 329072) (double 6823200)])
        (fact "contains layers"
          (> (count (:layers resp)) 1) => true?)
        (fact "has drawings and wgs-84 location"
          (keys (get-in resp [:application]))
          => (just [:location :location-wgs84 :drawings] :in-any-order))

        (facts "Nil-safe drawings"
          (mongo/update-by-id :applications app-id {$set {:drawings nil}})
          (-> (itu/query pena :application-gis-data :id app-id :lang "fi")
              :application :drawings) => [])

        (facts "Existing drawings"
          (mongo/update-by-id :applications app-id {$set {:drawings ["Mock" "drawings"]}})
          (-> (itu/query pena :application-gis-data :id app-id :lang "fi")
              :application :drawings) => ["Mock" "drawings"])

        (fact "Map config"
          (:base-layers (itu/query pena :map-config :lang "fi"))
          => (just [(contains {:title "Taustakartta" :url "moxy/maasto"})
                    (contains {:title "Kiinteistöjaotus" :url "moxy/kiinteisto"})
                    (contains {:title "Kiinteistötunnukset" :url "moxy/kiinteisto"})]
                   :in-any-order)
          (provided
            (sade.env/value :map :proxyserver-wmts) => "moxy")))

      (let [app-id (itu/create-app-id pena :propertyId itu/sipoo-property-id :operation "pientalo")]
        (fact "Organization map layers"
          (:layers (itu/query pena :application-gis-data :id app-id :lang "fi"))
          => (just [;; Base layers
                    (contains {:title "Taustakartta" :url "moxy/maasto"})
                    (contains {:title "Kiinteistöjaotus" :url "moxy/kiinteisto"})
                    (contains {:title "Kiinteistötunnukset" :url "moxy/kiinteisto"})
                    ;; Shared layers
                    {:title  "Suojellut alueet"
                     :id     "SuojellutAlueet"
                     :url    "/proxy/wms"
                     :format "image/png" :projection "EPSG:3067" :protocol "WMS"}
                    {:title  "Tulva-alueet"
                     :id     "Tulva-alueet"
                     :url    "/proxy/wms"
                     :format "image/png" :projection "EPSG:3067" :protocol "WMS"}
                    {:title  "Rantaviivat"
                     :id     "Rantaviiva"
                     :url    "/proxy/wms"
                     :format "image/png" :projection "EPSG:3067" :protocol "WMS"}
                    ;; Organization specific layers
                    {:title      "Little layer on the prairie" :id "Lupapiste-753-R:Little"
                     :url        "/proxy/kuntawms" :format "image/png"
                     :projection "EPSG:3067" :protocol "WMS"}
                    {:title      "Kantakartta" :id "Lupapiste-753-R:Yleiskaava_Manner"
                     :url        "/proxy/kuntawms" :format "image/png"
                     :projection "EPSG:3067" :protocol "WMS"}]
                   :in-any-order)
          (provided
            (sade.env/value :map :proxyserver-wmts) => "moxy"
            (lupapalvelu.gis.gis-api/cached-capabilities)
            => [{:id       "101"
                 :name     {:en "Detailed Plan (municipality)"
                            :fi "Asemakaava (kunta)"
                            :sv "Detaljplan (kommun)"}
                 :subtitle {:en "Detailed Plan (municipality)"
                            :fi "Kunnan palveluun toimittama ajantasa-asemakaava"
                            :sv "Detaljplan (kommun)"}
                 :wmsName  "092_asemakaava"
                 :wmsUrl   "/proxy/wms"}
                {:baseLayerId "Tulvavaarakartoitetut_alueet_Meritulva"
                 :id          "Tulvavaarakartoitetut_alueet_Meritulva"
                 :name        {:en "Tulvavaarakartoitetut_alueet_Meritulva"
                               :fi "Tulvavaarakartoitetut_alueet_Meritulva"
                               :sv "Tulvavaarakartoitetut_alueet_Meritulva"}
                 :subtitle    {:en "" :fi "" :sv ""}
                 :wmsName     "Tulvavaarakartoitetut_alueet_Meritulva"
                 :wmsUrl      "/proxy/wms"}
                {:id "bad-layer"}]
            (lupapalvelu.proxy-services/combine-municipality-layers anything "753")
            => [{:id       "Lupapiste-0"
                 :minScale 400000
                 :name     {:en "Little layer on the prairie"
                            :fi "Little layer on the prairie"
                            :sv "Little layer on the prairie"}
                 :subtitle {:en "" :fi "" :sv ""}
                 :wmsName  "Lupapiste-753-R:Little"
                 :wmsUrl   "/proxy/kuntawms"}
                {:id       "102"
                 :minScale 400000
                 :name     {:en "Map stock"
                            :fi "Kantakartta"
                            :sv "Baskarta"}
                 :subtitle {:en "" :fi "" :sv ""}
                 :wmsName  "Lupapiste-753-R:Yleiskaava_Manner"
                 :wmsUrl   "/proxy/kuntawms"}]))

        (fact "On exception, NLS layers are returned"
          (:layers (itu/query pena :application-gis-data :id app-id :lang "fi"))
          => (just
               [(contains {:title "Taustakartta" :url "moxy/maasto"})
                (contains {:title "Kiinteistöjaotus" :url "moxy/kiinteisto"})
                (contains {:title "Kiinteistötunnukset" :url "moxy/kiinteisto"})])
          (provided
            (sade.env/value :map :proxyserver-wmts) => "moxy"
            (lupapalvelu.gis.gis-api/cached-capabilities) =throws=> (SocketTimeoutException. "fail")))))

    (facts "plan-document-infos"
      (let [app-id (itu/create-app-id pena :propertyId itu/sipoo-property-id :operation "pientalo")
            x      "512775.1540419683"
            y      "6841103.018070641"]
        (fact "Municipality plan info"
          (itu/query pena :plan-document-infos :id app-id :x x :y y)
          => {:ok    true
              :infos [{:id         "209"
                       :kaavalaji  "AKM"
                       :kaavanro   "189"
                       :kasitt_pvm "3/8/1957 12:00:00 AM"
                       :linkki     "http://municipality/file.pdf"
                       :type       "bentley"}]}
          (provided (lupapalvelu.wfs/plan-info-by-point x y "753" "plan-info")
                    => "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?>\r\n<GetFeatureInfoResponse>\r\n<FeatureKeysInLevel xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" keys=\"209\" level=\"Asemakaavaindeksi\"><FeatureInfo ngsName=\"Kaavaindeksi\"><FeatureKey key=\"209\"><property type=\"integer\" name=\"ID\">209</property><property type=\"string\" name=\"Kaavanro\">189</property><property type=\"string\" name=\"Kaavalaji\">AKM</property><property type=\"unknown\" name=\"Kasitt_pvm\">3/8/1957 12:00:00 AM</property><property type=\"string\" name=\"Linkki\">http://municipality/file.pdf</property></FeatureKey></FeatureInfo></FeatureKeysInLevel>\r\n</GetFeatureInfoResponse>\r\n"
                    (sade.env/value :plan-info :753 :gfi-mapper) => "lupapalvelu.wfs/gfi-to-features-bentley"
                    (sade.env/value :plan-info :753 :feature-mapper) => "lupapalvelu.wfs/feature-to-feature-info-mikkeli"))
        (fact "Liiteri plan info"
          (itu/query pena :plan-document-infos :id app-id :x x :y y)
          => {:ok    true
              :infos [{:id       "N32",
                       :kaavanro "N32",
                       :kunta    "Sipoo",
                       :kuntanro "753",
                       :linkki   "https://liiteri/file.pdf"
                       :type     "liiteri-ak"}]}
          (provided (lupapalvelu.wfs/plan-info-by-point x y "753" "plan-info") => nil
                    (lupapalvelu.wfs/plan-info-by-point x y "liiteri" "plan-info")
                    => "<?xml version=\"1.0\" encoding=\"UTF-8\"?><wfs:FeatureCollection xmlns=\"http://www.opengis.net/wfs\" xmlns:wfs=\"http://www.opengis.net/wfs\" xmlns:lupapiste=\"lupapiste.fi\" xmlns:gml=\"http://www.opengis.net/gml\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.opengis.net/wfs http://dev.lupapiste.fi:80/geoserver/schemas/wfs/1.0.0/WFS-basic.xsd\"><gml:boundedBy><gml:null>unknown</gml:null></gml:boundedBy><gml:featureMember><lupapiste:liiteri_asemakaavaindeksi fid=\"asemakaavarajaus.5718505\"><lupapiste:boundedBy>Env[403978.36 : 404623.27, 6693267.83 : 6693925.39]</lupapiste:boundedBy><lupapiste:kunta>Sipoo</lupapiste:kunta><lupapiste:kuntaid>753</lupapiste:kuntaid><lupapiste:tunnus>N32</lupapiste:tunnus><lupapiste:merkinnat>https://liiteri/file.pdf</lupapiste:merkinnat><lupapiste:geom><gml:Polygon srsName=\"http://www.opengis.net/gml/srs/epsg.xml#3067\"><gml:outerBoundaryIs><gml:LinearRing><gml:coordinates xmlns:gml=\"http://www.opengis.net/gml\" decimal=\".\" cs=\",\" ts=\" \">404036.18,6693717.82 404120.91,6693762.95 404218.52,6693840.47 404246.06,6693859.53 404329.84,6693738.47 404427.57,6693807.26 404449.22,6693842.05 404440.25,6693854.81 404455.82,6693866.44 404446.37,6693879.16 404488.37,6693907.78 404505.52,6693901.29 404514.64,6693925.39 404623.27,6693882.45 404617.32,6693866.11 404612.23,6693858.13 404529.41,6693793.49 404520.46,6693781.8 404516.26,6693770.49 404515.55,6693764.16 404523.11,6693749.67 404534.5,6693733.95 404557.81,6693713.64 404564.6,6693704.91 404568.66,6693695.08 404570.64,6693684.32 404570.1,6693672.65 404566.77,6693662.54 404553.05,6693640.8 404549.23,6693629.07 404548.25,6693616.6 404551.79,6693599.61 404550.82,6693587.34 404543.71,6693572.7 404530.38,6693559.36 404512.03,6693544.39 404474.31,6693518.4 404446.67,6693488.68 404429.25,6693475.72 404373.24,6693445.87 404366.48,6693437.14 404361.77,6693423.82 404362.67,6693407.57 404368.59,6693370.76 404367.59,6693344.35 404363.2,6693319.92 404362.93,6693309.96 404367.12,6693286.49 404374.03,6693269.29 404209.38,6693268.6 404145.21,6693270.36 404077.16,6693267.83 404066.76,6693333.32 404065.26,6693362.16 404066.83,6693426.33 404058.35,6693449.85 404130.75,6693485.57 404072.14,6693619.13 404027.13,6693597.15 404025.01,6693603.83 403994.39,6693661.95 403978.36,6693698.42 404015.42,6693709.56 404036.18,6693717.82</gml:coordinates></gml:LinearRing></gml:outerBoundaryIs></gml:Polygon></lupapiste:geom></lupapiste:liiteri_asemakaavaindeksi></gml:featureMember></wfs:FeatureCollection>"))
        (let [result [{"Arkistotunnus" "07040"
                       "Hyv."          "Kunnanvaltuusto"
                       "Kaavatunnus"   "73407040"
                       "Nimi"          ""
                       "Pvm."          "2002-02-11"
                       "Tyyppi"        "asemakaava"
                       "Vaihe"         "lainvoimainen"}
                      [{:desc "First description"
                        :pic  "https://trimble/pic1_0.png"}
                       {:desc "Second description"
                        :pic  "https://trimble/pic2_0.png"}]]
              ](fact "Trimble plan info"
           (itu/query pena :plan-document-infos :id app-id :x x :y y)
           => {:ok    true
               :infos result}
           (provided (lupapalvelu.wfs/plan-info-by-point x y anything "plan-info") => nil
                     (sade.env/value :trimble-kaavamaaraykset :753 :url) => "http://good"
                     (lupapalvelu.wfs/trimble-kaavamaaraykset-by-point x y "753") => result)))
        (fact "General plan info"
          (itu/query pena :plan-document-infos :id app-id :x x :y y)
          => {:ok    true
              :infos [{:id         "7531001"
                       :linkki     "http://liiteri.ymparisto.fi/maarays/filex.pdf"
                       :lisatieto  "Pääkartta teemakaavatasolla."
                       :nimi       "§108, Sipoon yleiskaava 2025"
                       :oikeusvaik "Oikeusvaikutteinen",
                       :pvm        "15.12.2008"
                       :type       "yleiskaava"
                       :tyyppi     "Kunnan hyväksymä"}]}
          (provided (lupapalvelu.wfs/plan-info-by-point x y anything "plan-info") => nil
                    (sade.env/value :trimble-kaavamaaraykset :753 :url) => nil
                    (lupapalvelu.wfs/general-plan-info-by-point x y)
                    => "<?xml version=\"1.0\" encoding=\"UTF-8\"?><wfs:FeatureCollection xmlns=\"http://www.opengis.net/wfs\" xmlns:wfs=\"http://www.opengis.net/wfs\" xmlns:lupapiste=\"lupapiste.fi\" xmlns:gml=\"http://www.opengis.net/gml\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.opengis.net/wfs http://test.lupapiste.fi:80/geoserver/schemas/wfs/1.0.0/WFS-basic.xsd\"><gml:boundedBy><gml:null>unknown</gml:null></gml:boundedBy><gml:featureMember><lupapiste:yleiskaavaindeksi_poikkeavat fid=\"teemayleiskaavojen_hakemisto.380\"><lupapiste:geom><gml:MultiPolygon srsName=\"http://www.opengis.net/gml/srs/epsg.xml#3067\"><gml:polygonMember><gml:Polygon srsName=\"EPSG:3067\"><gml:outerBoundaryIs><gml:LinearRing><gml:coordinates xmlns:gml=\"http://www.opengis.net/gml\" decimal=\".\" cs=\",\" ts=\" \">6844397.02680022 511891.3623,6843774.72550022 512009.8959,6843863.62570022 512375.4198,6843825.48840023 512312.5798,6844073.17610023 512486.1469,6844145.14290023 512600.6642,6844129.05840023 512959.8187,6844281.42700023 513035.2251,6845805.43010023 513090.7878,6845817.33640022 513257.4756,6845825.27390022 513336.8508,6845753.83620022 513483.6948,6845801.46130023 513725.789,6845579.21090023 514047.2584,6845178.36630023 514483.8218,6844329.05210022 514420.3217,6844055.20780023 514491.7593,6843801.20730022 514813.2287,6843848.83240023</gml:coordinates></gml:LinearRing></gml:outerBoundaryIs></gml:Polygon></gml:polygonMember></gml:MultiPolygon></lupapiste:geom><lupapiste:tunnus>7531001</lupapiste:tunnus><lupapiste:nimi>§108, Sipoon yleiskaava 2025</lupapiste:nimi><lupapiste:paatoksen_paivays>15.12.2008</lupapiste:paatoksen_paivays><lupapiste:tyypittely>Kunnan hyväksymä</lupapiste:tyypittely><lupapiste:oikeusvaikutteisuus>Oikeusvaikutteinen</lupapiste:oikeusvaikutteisuus><lupapiste:pinta_ala>1.5457465639965853E8</lupapiste:pinta_ala><lupapiste:lisatietoja>Pääkartta teemakaavatasolla.</lupapiste:lisatietoja><lupapiste:merkinnat_ja_maaraykset>http://liiteri.ymparisto.fi/maarays/filex.pdf</lupapiste:merkinnat_ja_maaraykset><lupapiste:merkinnat_ja_maaraykset_koko>99606</lupapiste:merkinnat_ja_maaraykset_koko><lupapiste:pienennetyt_merkinnat_ja_maaraykset>http://liiteri.ymparisto.fi/maarays_xs/4912055xs.pdf</lupapiste:pienennetyt_merkinnat_ja_maaraykset><lupapiste:pienennetyt_merkinnat_ja_maaraykset_koko>8914</lupapiste:pienennetyt_merkinnat_ja_maaraykset_koko></lupapiste:yleiskaavaindeksi_poikkeavat></gml:featureMember></wfs:FeatureCollection>"))
        (fact "Plan info not found"
          (itu/query pena :plan-document-infos :id app-id :x x :y y)
          => {:ok   false
              :text "error.not-found"}
          (provided (lupapalvelu.wfs/plan-info-by-point x y anything "plan-info") => nil
                    (sade.env/value :trimble-kaavamaaraykset :753 :url) => nil
                    (lupapalvelu.wfs/general-plan-info-by-point x y) => nil))))))
