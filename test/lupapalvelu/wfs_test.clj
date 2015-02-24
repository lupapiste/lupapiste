(ns lupapalvelu.wfs-test
  (:require [lupapalvelu.wfs :as wfs]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.env :as env]))

(testable-privates lupapalvelu.wfs plan-info-config)

(facts "plan-info-config"

  (fact "default"
    (let [{:keys [url layers format]} (plan-info-config "999")]
      url => (str (env/value :geoserver :host) (env/value :geoserver :wms :path))
      layers => "999_asemakaavaindeksi"
      format => "application/vnd.ogc.gml"))

  (fact "Mikkeli"
    (let [{:keys [url layers format]} (plan-info-config "491")]
      url => "http://194.111.49.141/WMSMikkeli.mapdef?"
      layers => "Asemakaavaindeksi"
      format => "text/xml")))