(ns lupapalvelu.shapefile-test
  (:require [lupapalvelu.shapefile :as shp]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(testable-privates lupapalvelu.shapefile
                   coordinates->string)

(def schema-fail? (throws #"Value does not match schema"))

(defn file-info-cmd [filename size created]
  {:data    {:files [{:filename filename :size size}]}
   :created created})

(facts "command->file-info"
  (fact "good"
    (shp/command->fileinfo (file-info-cmd "hello.txt" 1024 12345))
    => {:filename    "hello.txt"
        :contentType "text/plain"
        :size        1024
        :created     12345})
  (fact "bad: no filename"
    (shp/command->fileinfo (file-info-cmd nil 1024 12345))
    => schema-fail?)
  (fact "bad: blank filename"
    (shp/command->fileinfo (file-info-cmd "   " 1024 12345))
    => schema-fail?)
  (fact "bad: size negative"
    (shp/command->fileinfo (file-info-cmd "hello.txt" -1 12345))
    => schema-fail?))

(facts "coordinates->string"
  (coordinates->string [1 2]) => "1 2"
  (coordinates->string [[1 2]]) => "(1 2)"
  (coordinates->string [[[1 2]]]) => "((1 2))"
  (coordinates->string [[1 2] [3 4]]) => "(1 2, 3 4)"
  (coordinates->string [[1 2] [[3 4]]]) => "(1 2, (3 4))"
  (coordinates->string [[[1 2] [3 4]]
                        [[5 6] [7 8]]]) => "((1 2, 3 4), (5 6, 7 8))")

(facts "areas->drawings"
  (fact "One multipolygon"
    (shp/areas->drawings {:areas       {:features [{:geometry   {:coordinates [[[[1 2]
                                                                                 [3 4]
                                                                                 [5 6]
                                                                                 [1 2]]]]
                                                                 :type        "MultiPolygon"}
                                                    :id         "shape-id1"
                                                    :properties {:nimi "Multipoly"
                                                                 :id   1}
                                                    :type       "Feature"}]
                                        :type     "FeatureCollection"}
                          :areas-wgs84 {:features [{:geometry   {:coordinates [[[[11 22]
                                                                                 [33 44]
                                                                                 [55 66]
                                                                                 [11 22]]]]
                                                                 :type        "MultiPolygon"}
                                                    :id         "shape-id1"
                                                    :properties {:NAME "Multipoly"
                                                                 :id   1}
                                                    :type       "Feature"}]
                                        :type     "FeatureCollection"}})
    => [{:geometry-wgs84 {:coordinates [[[[11 22]
                                          [33 44]
                                          [55 66]
                                          [11 22]]]]
                          :type        "MultiPolygon"}
         :id             "feature-shape-id1"
         :name           "Multipoly"
         :geometry       "MULTIPOLYGON(((1 2, 3 4, 5 6, 1 2)))"}])
  (fact "One multipolygon: name can be integer"
    (shp/areas->drawings {:areas       {:features [{:geometry   {:coordinates [[[[1 2]
                                                                                 [3 4]
                                                                                 [5 6]
                                                                                 [1 2]]]]
                                                                 :type        "MultiPolygon"}
                                                    :id         "shape-id1"
                                                    :properties {:nimi 22
                                                                 :id   1}
                                                    :type       "Feature"}]
                                        :type     "FeatureCollection"}
                          :areas-wgs84 {:features [{:geometry   {:coordinates [[[[11 22]
                                                                                 [33 44]
                                                                                 [55 66]
                                                                                 [11 22]]]]
                                                                 :type        "MultiPolygon"}
                                                    :id         "shape-id1"
                                                    :properties {:NAME "Ignored"
                                                                 :id   1}
                                                    :type       "Feature"}]
                                        :type     "FeatureCollection"}})
    => [{:geometry-wgs84 {:coordinates [[[[11 22]
                                          [33 44]
                                          [55 66]
                                          [11 22]]]]
                          :type        "MultiPolygon"}
         :id             "feature-shape-id1"
         :name           "22"
         :geometry       "MULTIPOLYGON(((1 2, 3 4, 5 6, 1 2)))"}])
  (fact "Linestring, point and polygon"
    (shp/areas->drawings {:areas       {:features [{:geometry   {:coordinates [[1 2]
                                                                               [3 4]
                                                                               [5 6]]
                                                                 :type        "LineString"}
                                                    :id         "line-id1"
                                                    :properties {:nimi "Skyline"
                                                                 :id   1}
                                                    :type       "Feature"}
                                                   {:geometry   {:coordinates [[8 9]]
                                                                 :type        "Point"}
                                                    :id         "point-id1"
                                                    :properties {:nimi "Pointer"
                                                                 :id   2}
                                                    :type       "Feature"}
                                                   {:geometry   {:coordinates [[[1 2]
                                                                                [3 4]
                                                                                [5 6]
                                                                                [1 2]]]
                                                                 :type        "Polygon"}
                                                    :id         "poly-id1"
                                                    :properties {:nimi "Polyp"
                                                                 :id   1}
                                                    :type       "Feature"}]
                                        :type     "FeatureCollection"}
                          :areas-wgs84 {:features [{:geometry   {:coordinates [[11 22]
                                                                               [33 44]
                                                                               [55 66]]
                                                                 :type        "LineString"}
                                                    :id         "line-id1"
                                                    :properties {:nimi "Skyline"
                                                                 :id   1}
                                                    :type       "Feature"}
                                                   {:geometry   {:coordinates [88 99]
                                                                 :type        "Point"}
                                                    :id         "point-id1"
                                                    :properties {:nimi "Pointer"
                                                                 :id   2}
                                                    :type       "Feature"}
                                                   {:geometry   {:coordinates [[[11 22]
                                                                                [33 44]
                                                                                [55 66]
                                                                                [11 22]]]
                                                                 :type        "Polygon"}
                                                    :id         "poly-id1"
                                                    :properties {:nimi "Polyp"
                                                                 :id   1}
                                                    :type       "Feature"}]
                                        :type     "FeatureCollection"}})
    => [{:geometry-wgs84 {:coordinates [[11 22]
                                        [33 44]
                                        [55 66]]
                          :type        "LineString"}
         :id             "feature-line-id1"
         :name           "Skyline"
         :geometry       "LINESTRING(1 2, 3 4, 5 6)"}
        {:geometry-wgs84 {:coordinates [88 99]
                          :type        "Point"}
         :id             "feature-point-id1"
         :name           "Pointer"
         :geometry       "POINT(8 9)"}
        {:geometry-wgs84 {:coordinates [[[11 22]
                                         [33 44]
                                         [55 66]
                                         [11 22]]]
                          :type        "Polygon"}
         :id             "feature-poly-id1"
         :name           "Polyp"
         :geometry       "POLYGON((1 2, 3 4, 5 6, 1 2))"}])
  (fact "Bad: unclosed polygon"
    (shp/areas->drawings {:areas       {:features [{:geometry   {:coordinates [[[1 2]
                                                                                [3 4]
                                                                                [5 6]]]
                                                                 :type        "Polygon"}
                                                    :id         "shape-id1"
                                                    :properties {:nimi "Polyp"
                                                                 :id   1}
                                                    :type       "Feature"}]
                                        :type     "FeatureCollection"}
                          :areas-wgs84 {:features [{:geometry   {:coordinates [[[11 22]
                                                                                [33 44]
                                                                                [55 66]]]
                                                                 :type        "Polygon"}
                                                    :id         "shape-id1"
                                                    :properties {:NAME "Multipoly"
                                                                 :id   1}
                                                    :type       "Feature"}]
                                        :type     "FeatureCollection"}})
    => schema-fail?)
  (fact "Bad: unknown geometry"
    (shp/areas->drawings {:areas       {:features [{:geometry   {:coordinates [[[1 2]
                                                                                [3 4]
                                                                                [5 6]]]
                                                                 :type        "Bad"}
                                                    :id         "shape-id1"
                                                    :properties {:nimi "Bad"
                                                                 :id   1}
                                                    :type       "Feature"}]
                                        :type     "FeatureCollection"}
                          :areas-wgs84 {:features [{:geometry   {:coordinates [[[11 22]
                                                                                [33 44]
                                                                                [55 66]]]
                                                                 :type        "Bad"}
                                                    :id         "shape-id1"
                                                    :properties {:NAME "Bad"
                                                                 :id   1}
                                                    :type       "Feature"}]
                                        :type     "FeatureCollection"}})
    => schema-fail?)
  (fact "Bad: non-supported name property"
    (shp/areas->drawings {:areas       {:features [{:geometry   {:coordinates [[8 9]]
                                                                 :type        "Point"}
                                                    :id         "point-id1"
                                                    :properties {:ming "Pointer"
                                                                 :id   2}
                                                    :type       "Feature"}]
                                        :type     "FeatureCollection"}
                          :areas-wgs84 {:features [{:geometry   {:coordinates [[8 9]]
                                                                 :type        "Point"}
                                                    :id         "point-id1"
                                                    :properties {:ming "Pointer"
                                                                 :id   2}
                                                    :type       "Feature"}]
                                        :type     "FeatureCollection"}})
    => schema-fail?))
