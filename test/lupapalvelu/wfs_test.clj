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