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

(def valid-polygon "POLYGON((395338 6707330,395319 6707252,395462 6707259,395451 6707347,395451 6707393,395338 6707330))")

(def invalid-polygon "POLYGON((395338 6707330,395319 6707252,395462 6707259,395451 6707347,395451 6707393))")

(def multipolygon "MULTIPOLYGON(((395338 6707330,395319 6707252,395462 6707259,395451 6707347,395451 6707393,395338 6707330)),((395338 6707330,395319 6707252,395462 6707259,395451 6707347,395451 6707393,395338 6707330)))")

(def point "POINT(395338 6707330)")

(fact "Should create wgs84 geometry block from map drawing data"
  (wgs84-geometry drawing) => {:type "LineString"
                               :coordinates [[25.264406676091 60.371084504513]
                                             [25.263953569417 60.371419866642]
                                             [25.26321904251 60.371931171836]
                                             [25.262944317452 60.37198147111]
                                             [25.26160610776 60.371227564596]]})

(fact "Should handle NaN in coordinates"
  (wgs84-geometry (assoc drawing :geometry "POLYGON(NaN NaN, NaN NaN")) => nil)

(fact "Should handle empty geometry"
  (wgs84-geometry (assoc drawing :geometry "")) => nil)

(fact "Should handle nil geometry"
  (wgs84-geometry (assoc drawing :geometry nil)) => nil)

(fact "Should handle valid polygon"
  (wgs84-geometry (assoc drawing :geometry valid-polygon)) => {:type "Polygon"
                                                               :coordinates [[[25.09525837049 60.488495242537]
                                                                              [25.094953876678 60.487790364259]
                                                                              [25.097550912364 60.487890298721]
                                                                              [25.097304587279 60.488677125947]
                                                                              [25.097280399964 60.489089913559]
                                                                              [25.09525837049 60.488495242537]]]})

(fact "Should return nil for invalid polygon"
  (wgs84-geometry (assoc drawing :geometry invalid-polygon)) => nil)

(fact "Should handle multipolygon"
  (wgs84-geometry (assoc drawing :geometry multipolygon)) => {:type "MultiPolygon",
                                                              :coordinates [[[[25.09525837049 60.488495242537]
                                                                              [25.094953876678 60.487790364259]
                                                                              [25.097550912364 60.487890298721]
                                                                              [25.097304587279 60.488677125947]
                                                                              [25.097280399964 60.489089913559]
                                                                              [25.09525837049 60.488495242537]]]
                                                                            [[[25.09525837049 60.488495242537]
                                                                              [25.094953876678 60.487790364259]
                                                                              [25.097550912364 60.487890298721]
                                                                              [25.097304587279 60.488677125947]
                                                                              [25.097280399964 60.489089913559]
                                                                              [25.09525837049 60.488495242537]]]]})

(fact "Should handle a single point"
  (wgs84-geometry (assoc drawing :geometry point)) => {:type "Point",
                                                       :coordinates [25.09525837049 60.488495242537]})

(fact "Should find interior point of any kind of polygon"
  (interior-point "POLYGON((385019.191 6672327.415, 386515.191 6672503.415, 386675.191 6670839.415, 385147.191 6671015.415, 385019.191 6672327.415))") => [385839.191 6671671.415]
  (interior-point "POLYGON((385019.191 6672327.415, 386675.191 6670839.415, 385147.191 6671015.415, 385019.191 6672327.415))") => [385469.48368292686 6671583.415]
  (interior-point "POLYGON((384545.484 6672467.415, 386885.484 6672539.415, 386129.484 6670599.415, 386149.484 6672295.415, 384545.484 6672467.415))") => [386324.20333962265 6671569.415]
  (interior-point "POLYGON((384740.203 6672505.415, 386896.203  6672593.415, 386808.203 6670813.415, 386480.203 6670829.415,
                            386443.785 6672399.809, 385167.785 6672387.809, 385227.785 6670807.809, 384847.785 6670799.809, 384740.203 6672505.415))") => [384992.6258320421 6671696.612])
