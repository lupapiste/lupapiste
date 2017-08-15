(ns lupapalvelu.wfs-test
  (:require [lupapalvelu.wfs :as wfs]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.env :as env]))

(testable-privates lupapalvelu.wfs plan-info-config)

(facts "plan-info-config"

  (fact "default"
    (let [{:keys [url layers format]} (plan-info-config "999" "plan-info")]
      url => (str (env/value :geoserver :host) (env/value :geoserver :wms :path))
      layers => "999_asemakaavaindeksi"
      format => "application/vnd.ogc.gml"))

  (fact "Mikkeli"
    (let [{:keys [url layers format]} (plan-info-config "491" "plan-info")]
      url => "http://194.111.49.141/WMSMikkeli.mapdef?"
      layers => "Asemakaavaindeksi"
      format => "text/xml"))

  (fact "Valkeakoski"
    (let [{:keys [url layers format]} (plan-info-config "908" "plan-info")]
      url => "http://193.208.197.20/ValkeakoskiWMS.mapdef?"
      layers => "Asemakaavaindeksi"
      format => "text/xml"))

  (fact "Valkeakoski-rakennustapaohje"
    (let [{:keys [url layers format]} (plan-info-config "908" "rakennustapaohje")]
      url => "http://193.208.197.20/ValkeakoskiWMS.mapdef?"
      layers => "Rakennustapaohjeindeksi"
      format => "text/xml")))

(defn build-gml-osoitepiste-feature [osoitteet]
  [{:tag :gml:featureMember,
    :attr nil,
    :content [{:tag :oso:Osoitepiste,
               :attr {:gml:id "OsoP_7102207"},
               :content [{:tag :oso:kuntanimiFin,
                          :attr nil,
                          :content ["Tampere"]}
                         {:tag :oso:kuntanimiSwe,
                          :attr nil,
                          :content ["Tammerfors"]}
                         {:tag :oso:kuntatunnus,
                          :attr nil,
                          :content ["837"]}
                         {:tag :oso:sijainti,
                          :attr nil,
                          :content [{:tag :gml:Point,
                                     :attr {:srsName "EPSG:3067"},
                                     :content [{:tag :gml:pos,
                                                :attr nil,
                                                :content ["627013.606 7117521.877"]}]}]}
                         {:tag :oso:osoite,
                          :attr nil,
                          :content (mapv (fn [osoite] {:tag :oso:Osoite,
                                                       :attr {:gml:id "OsoTieN_7688619"},
                                                       :content [{:tag :oso:katunimi,
                                                                  :attr nil,
                                                                  :content [(:katunimi osoite)]}
                                                                 {:tag :oso:katunumero,
                                                                  :attr nil,
                                                                  :content [(:katunumero osoite)]}
                                                                 {:tag :oso:jarjestysnumero,
                                                                  :attr nil,
                                                                  :content [(:jarjestysnumero osoite)]}
                                                                 {:tag :oso:kieli,
                                                                  :attr nil,
                                                                  :content [(:kieli osoite)]}]})
                                         osoitteet)}]}]}])

(facts feature-to-address-details
  (fact "Basic address"
    (wfs/feature-to-address-details "fi" (build-gml-osoitepiste-feature [{:katunimi "Tipotie"
                                                                          :katunumero "100"
                                                                          :jarjestysnumero "121"
                                                                          :kieli "fin"}]))
    => {:street "Tipotie"
        :number "100"
        :municipality "837"
        :x 627013.606
        :y 7117521.877
        :name {:fi "Tampere" :sv "Tammerfors"}})

  (fact "Address defaults to Finnish when Swedish not provided"
    (wfs/feature-to-address-details "sv" (build-gml-osoitepiste-feature [{:katunimi "Tipotie"
                                                                          :katunumero "100"
                                                                          :jarjestysnumero "121"
                                                                          :kieli "fin"}]))
    => {:street "Tipotie"
        :number "100"
        :municipality "837"
        :x 627013.606
        :y 7117521.877
        :name {:fi "Tampere" :sv "Tammerfors"}})

  (fact "Swedish address"
    (wfs/feature-to-address-details "sv" (build-gml-osoitepiste-feature [{:katunimi "Tipotie"
                                                                          :katunumero "100"
                                                                          :jarjestysnumero "121"
                                                                          :kieli "fin"}
                                                                         {:katunimi "Bortawag"
                                                                          :katunumero "100"
                                                                          :jarjestysnumero "121"
                                                                          :kieli "swe"}]))
    => {:street "Bortawag"
        :number "100"
        :municipality "837"
        :x 627013.606
        :y 7117521.877
        :name {:fi "Tampere" :sv "Tammerfors"}})

  (fact "Basic address - address defaults to Finnish when unknown language is given"
    (wfs/feature-to-address-details "en" (build-gml-osoitepiste-feature [{:katunimi "Tipotie"
                                                                          :katunumero "100"
                                                                          :jarjestysnumero "121"
                                                                          :kieli "fin"}
                                                                         {:katunimi "Bortawag"
                                                                          :katunumero "100"
                                                                          :jarjestysnumero "121"
                                                                          :kieli "swe"}]))
    => {:street "Tipotie"
        :number "100"
        :municipality "837"
        :x 627013.606
        :y 7117521.877
        :name {:fi "Tampere" :sv "Tammerfors"}})

  (fact "Street number '0' - using order number instead"
    (wfs/feature-to-address-details "fi" (build-gml-osoitepiste-feature [{:katunimi "Tipotie"
                                                                          :katunumero "0"
                                                                          :jarjestysnumero "121"
                                                                          :kieli "fin"}]))
    => {:street "Tipotie"
        :number "121"
        :municipality "837"
        :x 627013.606
        :y 7117521.877
        :name {:fi "Tampere" :sv "Tammerfors"}})

  (fact "No street number provided - using order number instead"
    (wfs/feature-to-address-details "fi" (build-gml-osoitepiste-feature [{:katunimi "Tipotie"
                                                                          :katunumero nil
                                                                          :jarjestysnumero "121"
                                                                          :kieli "fin"}]))
    => {:street "Tipotie"
        :number "121"
        :municipality "837"
        :x 627013.606
        :y 7117521.877
        :name {:fi "Tampere" :sv "Tammerfors"}})

  (fact "No street address provided"
    (wfs/feature-to-address-details "fi" (build-gml-osoitepiste-feature []))
    => {:street nil
        :number nil
        :municipality "837"
        :x 627013.606
        :y 7117521.877
        :name {:fi "Tampere" :sv "Tammerfors"}}))
