(ns lupapalvelu.proxy-services-itest
  "Extracted from stests. May fail locally."
  (:require [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.json :as json]
            [lupapalvelu.proxy-services :refer :all]
            [midje.sweet :refer :all]
            [sade.coordinate :as coord]
            [sade.core :refer [now]]
            [sade.env :as env]))

(defn- proxy-request [apikey proxy-name & args]
  (:body (http-post
           (str (server-address) "/proxy/" (name proxy-name))
           {:headers        {"content-type" "application/json;charset=utf-8"}
            :oauth-token    apikey
            :socket-timeout 10000
            :conn-timeout   10000
            :body           (json/encode (apply hash-map args))
            :as             :json})))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  ;; make sure proxies are enabled:
  (http-post (str (server-address) "/api/proxy-ctrl/on") {})

  (facts "find-addresses-proxy"
    (let [r (first (proxy-request mikko :find-address :term "piiriniitynkatu 9, tampere" :lang "fi"))]
      (fact r => (contains {:kind         "address"
                            :type         "street-number-city"
                            :street       "Piiriniitynkatu"
                            :number       "9"
                            :municipality "837"
                            :name         {:fi "Tampere" :sv "Tammerfors"}}))
      (fact (-> r :location keys) => (just #{:x :y})))
    (let [r (proxy-request mikko :find-address :term "piiriniitynkatu" :lang "fi")]
      (fact r => (contains (just {:kind         "address"
                                  :type         "street"
                                  :street       "Piiriniitynkatu"
                                  :number       "1"
                                  :location     (just {:x coord/valid-x? :y coord/valid-y?})
                                  :name         {:fi "Tampere" :sv "Tammerfors"}
                                  :municipality "837"})))
      (fact (-> r first :location keys) => (just #{:x :y}))))

  (fact "WMS capabilites"
    (http-get (str (server-address) "/proxy/wmscap")
              {:query-params     {:v (str (now))}
               :throw-exceptions false}) => http200?)

  (facts "geoserver-layers"
    (let [base-params {"FORMAT"  "image/png"
                       "SERVICE" "WMS"
                       "VERSION" "1.1.1"
                       "REQUEST" "GetMap"
                       "STYLES"  ""
                       "SRS"     "EPSG:3067"
                       "BBOX"    "444416,6666496,444672,6666752"
                       "WIDTH"   "256"
                       "HEIGHT"  "256"}]
      (doseq [layer [{"LAYERS" "lupapiste:109_asemakaava"
                      "BBOX"   "358400,6758400,409600,6809600"}
                     {"LAYERS" "lupapiste:109_kantakartta"
                      "BBOX"   "358400,6758400,409600,6809600"}]]
        (fact {:midje/description (get layer "LAYERS")}
          (let [request {:query-params (merge base-params layer)
                         :headers      {"accept-encoding" "gzip, deflate"}
                         :as           :stream}]
            (http-get (env/value :maps :geoserver) request) => http200?))))))
