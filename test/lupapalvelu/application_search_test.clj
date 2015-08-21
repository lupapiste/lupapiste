(ns lupapalvelu.application-search-test
   (:require [midje.sweet :refer :all]
             [midje.util :refer [testable-privates]]
             [monger.operators :refer [$in]]
             [lupapalvelu.test-util :refer :all]
             [lupapalvelu.application-search :refer :all]))

(testable-privates lupapalvelu.application-search make-sort operation-names make-area-query)

(facts "operation-names"
  (operation-names "bil") => ["auto-katos"]
  (operation-names "grilli") => ["auto-katos"]
  (operation-names "Ty\u00f6njohtaja") => ["tyonjohtajan-nimeaminen-v2" "tyonjohtajan-nimeaminen"]
  (operation-names "ANNAN") => (just ["muu-uusi-rakentaminen" "muu-tontti-tai-kort-muutos" "ya-kayttolupa-muu-kayttolupa" "muu-laajentaminen" "muu-rakennus-laaj" "talousrakennus-laaj" "masto-tms" "muu-maisema-toimenpide" "varasto-tms" "sisatila-muutos"] :in-any-order)
  (operation-names "S\u00e4hk\u00f6-, data ja muiden kaapelien sijoittaminen") => ["ya-sijoituslupa-sahko-data-ja-muiden-kaapelien-sijoittaminen"])

(facts "sorting parameter parsing"
  (make-sort {:sort {:field "unknown" :asc false}})  => {}
  (make-sort {:sort {:field "unknown" :asc true}})  => {}
  (make-sort {:sort {:field "id" :asc false}}) => {}
  (make-sort {:sort {:field "_id" :asc false}}) => {}
  (make-sort {:sort {:field "type" :asc true }})  => {:infoRequest 1}
  (make-sort {:sort {:field "type" :asc false }}) => {:infoRequest -1}
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
                     :handler  "123"}
                 {:role "authority"}) (get "$and") last) => (contains {"$or" [{"auth.id" "123"} {"authority.id" "123"}]}))

(fact "query contais user query"
  (-> (make-query {:auth.id "123"} {} {}) (get "$and") first) => {:auth.id "123"})

(fact "Tags are present in query"
  (-> (make-query {} {:applicationTags ["test1" "test2"]} {}) (get "$and") last :tags) => {"$in" ["test1" "test2"]})

(fact "Make query has correct query form"
  (make-query
    {:auth.id "123"}
    {:kind  "applications"
     :applicationType "all"
     :handler  "321"
     :applicationTags ["test1" "test2"]}
    {:role "authority"}) => (just {"$and" (just [{:auth.id "123"}
                                                 {:infoRequest false :state {"$nin" ["draft" "canceled"]}}
                                                 {"$or" [{"auth.id" "321"} {"authority.id" "321"}]}
                                                 {:tags {"$in" ["test1" "test2"]}}])}))


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
    (resolve-coordinates multi-feature-simple) => (resolve-coordinates polygon-feature))
  (fact "For , returns two Polygons"
    (count (resolve-coordinates multi-feature)) => 2))

(facts "Area query is in correct form"
  (make-area-query ["polygon" "multi-polygon"] {:orgAuthz {:753-R #{:authority}}}) =>
  {"$or" ; two polygons are extracted from MultiPolygon
   [{:location
     {"$geoWithin"
      {"$polygon"
       [[402644.2941 6693912.6002]
        [401799.0131 6696356.5649]
        [406135.6722 6695272.4001]
        [406245.9263 6693673.7164]
        [404059.221 6693545.0867]
        [404059.221 6693545.0867]
        [402644.2941 6693912.6002]]}}}
    {:location
     {"$geoWithin"
      {"$polygon"
       [[409884.3098 6694316.865]
        [413394.0636 6688105.8871]
        [410894.9719 6687076.8493]
        [409094.1558 6685367.9116]
        [407421.9694 6683989.736]
        [403985.7183 6689300.3059]
        [404114.348 6693140.8219]
        [409884.3098 6694316.865]]}}}
    {:location
     {"$geoWithin"
      {"$polygon"
       [[402644.2941 6693912.6002]
        [401799.0131 6696356.5649]
        [406135.6722 6695272.4001]
        [406245.9263 6693673.7164]
        [404059.221 6693545.0867]
        [404059.221 6693545.0867]
        [402644.2941 6693912.6002]]}}}]}
  (provided
    (lupapalvelu.mongo/select
      :organizations
      {:_id {$in #{"753-R"}} :areas.features.id {$in ["polygon" "multi-polygon"]}} [:areas]) => [{:id "753-R"
                                                                                                 :areas {:type "FeatureCollection"
                                                                                                         :features [multi-feature polygon-feature]}}]))
