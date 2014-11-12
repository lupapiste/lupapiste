(ns sade.coordinate-test
  (:require [midje.sweet :refer :all]
            [sade.coordinate :as coordinate])
  (:import [java.math BigDecimal]))

(fact "EPSG:3067 to WGS84 works"
  (coordinate/convert "EPSG:3067" "WGS84" 3 [420893.934152 7177728.76993]) => [25.340 64.715])

(fact "WGS84 to EPSG:3067 works"
  (coordinate/convert "WGS84" "EPSG:3067" 4 [25.34 64.715]) => [420893.9342 7177728.7700])