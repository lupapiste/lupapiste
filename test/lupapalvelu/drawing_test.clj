(ns lupapalvelu.drawing-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.drawing :refer :all]))

(def drawing
  {:id 1,
   :name "Pipe",
   :category 123,
   :geometry "LINESTRING(404286.304 6693999.457,404262.304 6694037.457,404223.304 6694095.457,404208.304 6694101.457,404132.304 6694019.457)",
   :area 0,
   :height 0,
   :length 150})

(facts "Should create wgs84 geometry block from map drawing data"
  (fact (wgs84-geometry drawing) => {:type "Linestring"
                                     :coordinates [[25.26441 60.37108]
                                                   [25.26395 60.37142]
                                                   [25.26322 60.37193]
                                                   [25.26294 60.37198]
                                                   [25.26161 60.37123]]}))

(facts "Should handle NaN in coordinates"
  (fact (wgs84-geometry (assoc drawing :geometry "POLYGON(NaN NaN, NaN NaN")) => nil))

(facts "Should handle empty geometry"
  (fact (wgs84-geometry (assoc drawing :geometry "")) => nil))

(facts "Should handle nil geometry"
  (fact (wgs84-geometry (assoc drawing :geometry nil)) => nil))
