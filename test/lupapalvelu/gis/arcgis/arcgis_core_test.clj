(ns lupapalvelu.gis.arcgis.arcgis-core-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.gis.arcgis.arcgis-core :as arcgis]
            [lupapalvelu.gis.arcgis.geometry :as arcgis-geom]))

(def mock-config
  {:server {:username "testuser"
            :service-url "http://test/rest/"}
   :search {:folder       "Folder"
            :service      "Service"
            :service-type "MapServer"
            :layer-id     1
            :fields       {:feature-id "FEATURE_ID"}}
   :layers [{:layer-ids    [1 2 3]
             :folder       "Folder"
             :service      "Service"
             :service-type "MapServer"
             :title        {:fi "Taso 1" :sv "Lager 1"}}
            {:layer-ids    [4 5 6]
             :service-type "FeatureServer"
             :title        {:fi "Taso 2"}}]})

(deftest loc-layer-title
  (testing "localization found"
    (is (= "Lager 1"
           (arcgis/loc-layer-title "sv" (get-in mock-config [:layers 0 :title])))))
  (testing "localization not found, use default"
    (is (= "Taso 2"
           (arcgis/loc-layer-title "sv" (get-in mock-config [:layers 1 :title]))))))

(deftest query-url
  (let [query-url @#'arcgis/query-url]
    (testing "fully configured path"
      (is (= "http://test/rest/Folder/Service/MapServer/1/query?"
             (query-url mock-config))))
    (testing "no service part in path"
      (is (= "http://test/rest/Folder/MapServer/1/query?"
             (query-url (update mock-config :search dissoc :service)))))
    (testing "no optional parts in path"
      (is (= "http://test/rest/1/query?"
             (query-url (update mock-config :search dissoc :folder :service :service-type)))))))

(deftest tile-url
  (let [tile-url @#'arcgis/tile-url]
    (testing "fully configured path"
      (is (= "http://test/rest/Folder/Service/MapServer/export"
             (tile-url (get mock-config :server) (get-in mock-config [:layers 0])))))
    (testing "partially configured path"
      (is (= "http://test/rest/FeatureServer/export"
             (tile-url (get mock-config :server) (get-in mock-config [:layers 1])))))))

(deftest esri->wkt
  (testing "empty in, empty out"
    (is (empty? (arcgis-geom/esri->wkt nil))))
  (testing "invalid polygon"
    (is (thrown?
          IllegalArgumentException
          (doall (arcgis-geom/esri->wkt {:rings [[[1 1] [2 2] [3 3] [4 4]]]})))))
  (testing "multiple valid geometries"
    (is (= ["POINT (12 23)"
            "POINT (1 2)"
            "POINT (3 4)"
            "LINESTRING (2 3, 4 5, 6 7)"
            "POLYGON ((1 1, 2 2, 3 3, 1 1))"
            "POLYGON ((3 3, 4 4, 5 5, 6 6, 3 3))"]
           (arcgis-geom/esri->wkt {:x      12
                                   :y      23
                                   :points [[1 2] [3 4]]
                                   :paths  [[[2 3] [4 5] [6 7]]]
                                   :rings  [[[1 1] [2 2] [3 3] [1 1]]
                                            [[3 3] [4 4] [5 5] [6 6] [3 3]]]})))))
