(ns lupapalvelu.geojson-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.geojson :refer :all]
            [sade.core :refer [fail?]]))

(testable-privates lupapalvelu.geojson select-coordinates)

(facts "Validate Point" ; Coordinate values tested in coordinate_test.clj
  (validate-point [10001 6610000]) => nil
  (fact "fail if coordinates not numbers" (validate-point ["10001" "6610000"]) => fail?)
  (fact "fail if coordinate not pair" (validate-point [10001 6610000 0]) => fail?))

(def multi-feature-simple {:id "multi-simple",
                           :properties {:nimi "simple multi"},
                           :type "Feature"
                           :geometry
                           {:type "MultiPolygon"
                            :coordinates
                            [[[[402644.2941 6693912.6002]
                               [401799.0131 6696356.5649]
                               [406135.6722 6695272.4001]
                               [406245.9263 6693673.7164]
                               [404059.221 6693545.0867]
                               [404059.221 6693545.0867]
                               [402644.2941 6693912.6002]]]]}})

(def multi-feature {:id "multi-polygon",
                    :properties {:nimi "multi polygon"},
                    :type "Feature"
                    :geometry
                    {:type "MultiPolygon"
                     :coordinates
                     [[[[402644.2941 6693912.6002]
                        [401799.0131 6696356.5649]
                        [406135.6722 6695272.4001]
                        [406245.9263 6693673.7164]
                        [404059.221 6693545.0867]
                        [404059.221 6693545.0867]
                        [402644.2941 6693912.6002]]]
                      [[[409884.3098 6694316.865]
                        [413394.0636 6688105.8871]
                        [410894.9719 6687076.8493]
                        [409094.1558 6685367.9116]
                        [407421.9694 6683989.736]
                        [403985.7183 6689300.3059]
                        [404114.348 6693140.8219]
                        [409884.3098 6694316.865]]]]}})

(def polygon-feature {:id "polygon",
                      :properties {:nimi "Test"},
                      :type "Feature"
                      :geometry
                      {:type "Polygon"
                       :coordinates
                       [[[402644.2941 6693912.6002]
                         [401799.0131 6696356.5649]
                         [406135.6722 6695272.4001]
                         [406245.9263 6693673.7164]
                         [404059.221 6693545.0867]
                         [404059.221 6693545.0867]
                         [402644.2941 6693912.6002]]]}})

(fact "Multimethod for features' MultiPolygon/Polygon coordinates"
  (fact "Returns Polygon from MultiPolygon when only one Polygon is present"
    (resolve-polygons multi-feature-simple) => (resolve-polygons polygon-feature))
  (fact "returns two Polygons"
    (count (resolve-polygons multi-feature)) => 2))

(facts "Ensuring coordinates format"
  (select-coordinates nil) => nil
  (fact "Convert points to 2d"
    (select-coordinates [2.2 3.3 4.4]) => [2.2 3.3])
  (fact "if it's not sequence of numbers, arg is returned"
    (select-coordinates [[2.2 3.3 4.4]]) => [[2.2 3.3 4.4]]
    (select-coordinates []) => []))

