(ns lupapalvelu.wfs-test
  (:require [lupapalvelu.wfs :as wfs]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.env :as env]
            [sade.strings :as ss]))

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

(defn kunta-gml-osoite [osoite]
  {:tag :Osoite,
   :attrs {:xmlns "http://www.kuntatietopalvelu.fi/gml/opastavattiedot/osoitteet",
           :xmlns:p3 "http://www.opengis.net/gml",
           :p3:id "osoite.3219"},
   :content [{:tag :yksilointitieto,
              :attrs {:xmlns "http://www.kuntatietopalvelu.fi/gml/yhteiset"},
              :content ["osoite.3219"]}
             {:tag :alkuHetki,
              :attrs {:xmlns "http://www.kuntatietopalvelu.fi/gml/yhteiset"},
              :content ["2002-04-15T00:00:00"]}
             {:tag :kunta,
              :attrs {:xmlns "http://www.kuntatietopalvelu.fi/gml/yhteiset"},
              :content [(:kunta osoite)]}
             {:tag :osoitenimi,
              :attrs {:xmlns "http://www.kuntatietopalvelu.fi/gml/yhteiset"},
              :content [{:tag :teksti, :attrs {:xml:lang "fi"},
                         :content [(:osoitenimi osoite)]}
                        {:tag :teksti, :attrs {:xml:lang "sv"},
                         :content ["Svensk gatan"]}]}
             {:tag :osoitenumero,
              :attrs {:xmlns "http://www.kuntatietopalvelu.fi/gml/yhteiset"},
              :content [(:osoitenumero osoite)]}
             {:tag :postinumero,
              :attrs {:xmlns "http://www.kuntatietopalvelu.fi/gml/yhteiset"},
              :content [(:postinumero osoite)]}
             {:tag :postitoimipaikannimi,
              :attrs {:xmlns "http://www.kuntatietopalvelu.fi/gml/yhteiset"},
              :content [(:postitoimipaikka osoite)]}
             {:tag :pistesijainti,
              :attrs {:xmlns "http://www.kuntatietopalvelu.fi/gml/yhteiset"},
              :content [{:tag :Point,
                         :attrs {:axisLabels "x y", :srsName "EPSG:3067", :srsDimension "2"},
                         :content [{:tag :pos,
                                    :attrs nil,
                                    :content [(str (:x osoite) " " (:y osoite))]}]}]}
             {:tag :tila,
              :attrs {:xmlns "http://www.kuntatietopalvelu.fi/gml/yhteiset"},
              :content ["voimassa"]}]})

(defn kunta-gml-osoite-feature [osoite]
  {:tag :featureMember,
   :attrs {:xmlns "http://www.opengis.net/gml"},
   :content [(kunta-gml-osoite osoite)]})


(facts "fetch-kuntagml-osoite"
  (fact "not osoite"
    (wfs/select-kuntagml-osoite {:tag :Foo :content [{:tag :Faa :content ["fii"]}]}) => nil)
  (fact "plain Osoite element"
    (wfs/select-kuntagml-osoite
      (kunta-gml-osoite {:kunta            "638" :osoitenimi "Testikatu"
                         :osoitenumero     "1" :postinumero "12300"
                         :postitoimipaikka "PORVOO"
                         :x                "424901.37361699" :y "6695339.45658077"})) => (every-checker sequential? not-empty))
  (fact "with featureMember"
    (wfs/select-kuntagml-osoite
      (kunta-gml-osoite-feature {:kunta            "638" :osoitenimi "Testikatu"
                                 :osoitenumero     "1" :postinumero "12300"
                                 :postitoimipaikka "PORVOO"
                                 :x                "424901.37361699" :y "6695339.45658077"})) => (every-checker sequential? not-empty))
  (fact "with featureCollection"
    (wfs/select-kuntagml-osoite
      {:tag     :FeatureCollection
       :content [(kunta-gml-osoite-feature {:kunta            "638" :osoitenimi "Testikatu"
                                            :osoitenumero     "1" :postinumero "12300"
                                            :postitoimipaikka "PORVOO"
                                            :x                "424901.37361699" :y "6695339.45658077"})]}) => (every-checker sequential? not-empty))
  (fact "with foo, still works"
    (wfs/select-kuntagml-osoite
      {:tag     :foofaa
       :content [(kunta-gml-osoite-feature {:kunta            "638" :osoitenimi "Testikatu"
                                            :osoitenumero     "1" :postinumero "12300"
                                            :postitoimipaikka "PORVOO"
                                            :x                "424901.37361699" :y "6695339.45658077"})]}) => (every-checker sequential? not-empty)))

(facts "krysp-to-address-details"
  (wfs/krysp-to-address-details
    "fi"
    (kunta-gml-osoite-feature {:kunta            "638" :osoitenimi "Testikatu"
                               :osoitenumero     "1" :postinumero "12300"
                               :postitoimipaikka "PORVOO"
                               :x                "424901.37361699" :y "6695339.45658077"})) => {:municipality "638"
                                                                                                 :number "1"
                                                                                                 :street "Testikatu"
                                                                                                 :x 424901.37361699 :y 6695339.45658077}
  (fact "Svensk works"                                      ; previous lang selector did not work
    (wfs/krysp-to-address-details
      "sv"
      (kunta-gml-osoite-feature {:kunta            "638" :osoitenimi "Testikatu"
                                 :osoitenumero     "1" :postinumero "12300"
                                 :postitoimipaikka "PORVOO"
                                 :x                "424901.37361699" :y "6695339.45658077"})) => {:municipality "638"
                                                                                                  :number "1"
                                                                                                  :street "Svensk gatan"
                                                                                                  :x 424901.37361699 :y 6695339.45658077}))

(facts "wfs-is-alive?"
  (wfs/wfs-is-alive? nil nil nil) => falsey
  (wfs/wfs-is-alive? " " "hello" "world") => falsey
  (wfs/wfs-is-alive? "bad-url" nil nil) => falsey
  (provided
    (#'lupapalvelu.wfs/query-get-capabilities "bad-url" "WFS" nil nil false)
    => {:status 501 :body "Bad url, bad!"})
  (with-open [is (ss/->inputstream "This is not my beautiful XML!")]
    (wfs/wfs-is-alive? "bad-data" nil nil) => falsey
    (provided
      (#'lupapalvelu.wfs/query-get-capabilities "bad-data" "WFS" nil nil false)
      => {:status 200 :body is}))
  (with-open [is (ss/->inputstream "<?xml version='awesome'>")]
    (wfs/wfs-is-alive? "good-data" nil nil) => truthy
    (provided
      (#'lupapalvelu.wfs/query-get-capabilities "good-data" "WFS" nil nil false)
      => {:status 200 :body is})))
