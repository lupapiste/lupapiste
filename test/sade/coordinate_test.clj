(ns sade.coordinate-test
  (:require [midje.sweet :refer :all]
            [sade.coordinate :as coordinate])
  (:import [java.math BigDecimal]))

(fact "EPSG:3067 to WGS84 works"
;  (map #(coordinate/round-to % 3)
    (coordinate/convert "EPSG:3067" "WGS84" 3 [420893.934152M 7177728.76993M])
;    )
  => [25.340M 64.715M])

(fact "WGS84 to EPSG:3067 works"
;  (map #(coordinate/round-to % 4)
    (coordinate/convert "WGS84" "EPSG:3067" 4 [25.34M 64.715M])
;    )
  => [420893.9342M 7177728.7700M])