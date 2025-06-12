(ns lupapalvelu.mml.property.util-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.mml.property.util :as util]))

(deftest parse-test
  (testing "points"
    (is (= "POINT (100 0)"
           (util/geojson->wkt {:coordinates [100.0 0.0]
                               :type        "Point"}))))
  (testing "multipoints"
    (is (= "MULTIPOINT ((100 0), (101 1))"
           (util/geojson->wkt {:coordinates [[100.0 0.0]
                                             [101.0 1.0]]
                               :type        "MultiPoint"}))))
  (testing "lines"
    (is (= "LINESTRING (100 0, 101 1)"
           (util/geojson->wkt {:coordinates [[100.0 0.0]
                                             [101.0 1.0]]
                               :type        "LineString"}))))
  (testing "multilines"
    (is (= "MULTILINESTRING ((100 0, 101 1), (102 2, 103 3))"
           (util/geojson->wkt {:coordinates [[[100.0 0.0]
                                              [101.0 1.0]]
                                             [[102.0 2.0]
                                              [103.0 3.0]]]
                               :type        "MultiLineString"}))))
  (testing "polygons"
    (testing "no holes"
      (is (= "POLYGON ((100 0, 101 0, 101 1, 100 1, 100 0))"
             (util/geojson->wkt {:coordinates [[[100.0 0.0]
                                                [101.0 0.0]
                                                [101.0 1.0]
                                                [100.0 1.0]
                                                [100.0 0.0]]]
                                 :type        "Polygon"}))))
    (testing "with holes"
      (is (= "POLYGON ((100 0, 101 0, 101 1, 100 1, 100 0), (100.8 0.8, 100.8 0.2, 100.2 0.2, 100.2 0.8, 100.8 0.8))"
             (util/geojson->wkt {:coordinates [[[100.0 0.0]
                                                [101.0 0.0]
                                                [101.0 1.0]
                                                [100.0 1.0]
                                                [100.0 0.0]]
                                               [[100.8 0.8]
                                                [100.8 0.2]
                                                [100.2 0.2]
                                                [100.2 0.8]
                                                [100.8 0.8]]]
                                 :type        "Polygon"})))))
  (testing "multipolygons"
    (is (= "MULTIPOLYGON (((102 2, 103 2, 103 3, 102 3, 102 2)), ((100 0, 101 0, 101 1, 100 1, 100 0), (100.8 0.8, 100.8 0.2, 100.2 0.2, 100.2 0.8, 100.8 0.8)))"
           (util/geojson->wkt {:coordinates [[[[102.0 2.0]
                                               [103.0 2.0]
                                               [103.0 3.0]
                                               [102.0 3.0]
                                               [102.0 2.0]]]
                                             [[[100.0 0.0]
                                               [101.0 0.0]
                                               [101.0 1.0]
                                               [100.0 1.0]
                                               [100.0 0.0]]
                                              [[100.8 0.8]
                                               [100.8 0.2]
                                               [100.2 0.2]
                                               [100.2 0.8]
                                               [100.8 0.8]]]]
                               :type        "MultiPolygon"}))))
  (testing "geometry collections"
    (is (= "GEOMETRYCOLLECTION (POINT (100 0), LINESTRING (101 0, 102 1))"
           (util/geojson->wkt {:type       "GeometryCollection"
                               :geometries [{:coordinates [100.0 0.0]
                                             :type        "Point"}
                                            {:coordinates [[101.0 0.0]
                                                           [102.0 1.0]]
                                             :type        "LineString"}]})))))
