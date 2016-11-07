(ns lupapalvelu.application-search-test
   (:require [midje.sweet :refer :all]
             [midje.util :refer [testable-privates]]
             [monger.operators :refer [$in]]
             [lupapalvelu.test-util :refer :all]
             [lupapalvelu.application-search :refer :all]
             [lupapalvelu.application-utils :refer [operation-names make-area-query]]
             [lupapalvelu.geojson :as geo]))

(facts "operation-names"
  (operation-names "bil") => ["auto-katos" "kiinteistonmuodostus"]
  (operation-names "grilli") => ["auto-katos"]
  (operation-names "Ty\u00f6njohtaja") => ["tyonjohtajan-nimeaminen-v2" "tyonjohtajan-nimeaminen"]
  (operation-names "ANNAN") => (just ["lannan-varastointi" "muu-uusi-rakentaminen" "muu-tontti-tai-kort-muutos" "ya-kayttolupa-muu-kayttolupa" "muu-laajentaminen" "muu-rakennus-laaj" "talousrakennus-laaj" "masto-tms" "muu-maisema-toimenpide" "varasto-tms" "sisatila-muutos"] :in-any-order)
  (operation-names "S\u00e4hk\u00f6-, data ja muiden kaapelien sijoittaminen") => ["ya-sijoituslupa-sahko-data-ja-muiden-kaapelien-sijoittaminen"])

(facts "sorting parameter parsing"
  (make-sort {:sort {:field "unknown" :asc false}})  => {}
  (make-sort {:sort {:field "unknown" :asc true}})  => {}
  (make-sort {:sort {:field "id" :asc false}}) => {:_id -1}
  (make-sort {:sort {:field "_id" :asc false}}) => {}
  (make-sort {:sort {:field "type" :asc true }})  => {:infoRequest -1, :permitSubtype 1}
  (make-sort {:sort {:field "type" :asc false }}) => {:infoRequest 1, :permitSubtype -1}
  (make-sort {:sort {:field "location" :asc false }}) => {:address -1}
  (make-sort {:sort {:field "applicant" :asc false }}) => {:applicant -1}
  (make-sort {:sort {:field "submitted" :asc true }})  => {:submitted 1}
  (make-sort {:sort {:field "modified" :asc true }})  => {:modified 1}
  (make-sort {:sort {:field "state" :asc true }})  => {:state 1}
  (make-sort {:sort {:field "handler" :asc true }}) => {"authority.lastName" 1, "authority.firstName" 1}
  (make-sort {:sort {:field {:injection "attempt"}
              :asc "; drop database;"}})   => {}
  (make-sort {})                                 => {}
  (make-sort nil)                                => {})

(fact "make-query (LUPA-519) with filter-user checks both authority and auth.id"
  (-> (make-query {} {:kind  "both"
                      :applicationType "all"
                      :handlers  ["123"]}
                  {:role "authority"}) (get "$and") second) => {"$or" [{:auth.id {"$in" ["123"]}} {:authority.id  {"$in" ["123"]}}]})

(fact "query contais user query"
  (-> (make-query {:auth.id "123"} {} {}) (get "$and") first) => {:auth.id "123"})

(fact "Tags are present in query"
  (-> (make-query {} {:tags ["test1" "test2"]} {}) (get "$and") last :tags) => {"$in" ["test1" "test2"]})

(fact "Make query has correct query form"
  (make-query
    {:auth.id "123"}
    {:kind  "applications"
     :applicationType "all"
     :handlers  ["321"]
     :tags ["test1" "test2"]}
    {:role "authority"}) => {"$and"
                               [{:auth.id "123"}
                                {"$and" [{:state {"$ne" "canceled"}}
                                         {"$or" [{:state {"$ne" "draft"}}
                                                 {:organization {"$nin" []}}]}]}
                                {"$or" [{:auth.id {"$in" ["321"]}} {:authority.id  {"$in" ["321"]}}]}
                                {:tags {"$in" ["test1" "test2"]}}
                                {:primaryOperation.name {"$nin" ["tyonjohtajan-nimeaminen-v2"]}}]})

(fact "Organization are present in query"
  (-> (make-query {} {:organizations ["753-R" "753-YA"]} {}) (get "$and") last :organization) => {"$in" ["753-R" "753-YA"]})


(facts "Area query is in correct form"
  (make-area-query ["polygon" "multi-polygon"] {:orgAuthz {:753-R #{:authority}}}) =>
  {"$or" ; two polygons are extracted from MultiPolygon
   [{:location-wgs84
     {"$geoWithin"
      {"$geometry"
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
           [409884.3098 6694316.865]]]]}}}}
    {:location-wgs84
     {"$geoWithin"
      {"$geometry"
       {:type "Polygon"
        :coordinates
        [[[402644.2941 6693912.6002]
          [401799.0131 6696356.5649]
          [406135.6722 6695272.4001]
          [406245.9263 6693673.7164]
          [404059.221 6693545.0867]
          [404059.221 6693545.0867]
          [402644.2941 6693912.6002]]]}}}}]}
  (provided
   (lupapalvelu.mongo/select
    :organizations
    {:_id {$in #{"753-R"}}
     :areas-wgs84.features.id {$in ["polygon" "multi-polygon"]}}
    [:areas-wgs84]) =>  [{:id "753-R"
                          :areas-wgs84 {:type "FeatureCollection"
                                        :features [{:id "multi-polygon",
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
                                                        [409884.3098 6694316.865]]]]}},
                                                   {:id "polygon"
                                                    :properties {:nimi "Test"}
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
                                                       [402644.2941 6693912.6002]]]}}]}}]))

(facts "Building ID search"
  (make-text-query "123456001M") => {:buildings.nationalId "123456001M"})

