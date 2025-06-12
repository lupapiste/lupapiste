(ns lupapalvelu.application-search-test
  (:require [lupapalvelu.application-search :as search :refer :all]
            [lupapalvelu.application-utils :refer [make-area-query]]
            [lupapalvelu.operations :refer [operation-names]]
            [lupapalvelu.states :as states]
            [lupapalvelu.test-util :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer [$in]]))

(testable-privates lupapalvelu.application-search
                   make-free-text-query
                   archival-query)

(facts "operation-names"
  (operation-names "bil") => ["auto-katos" "kiinteistonmuodostus"]
  (operation-names "venevaja") => ["auto-katos"]
  (operation-names "Työnjohtaja") => ["tyonjohtajan-nimeaminen-v2" "tyonjohtajan-nimeaminen"]
  (operation-names "ANNAN") => (just ["lannan-levittamisesta-poikkeustilanteessa"
                                      "lannan-varastointi"
                                      "lannan-varastointi-v2"
                                      "lannan-varastointi-v3"
                                      "muu-uusi-rakentaminen"
                                      "muu-tontti-tai-kort-muutos"
                                      "muu-laajentaminen"
                                      "muu-rakennus-laaj"
                                      "talousrakennus-laaj"
                                      "masto-tms"
                                      "muu-maisema-toimenpide"
                                      "varasto-tms"
                                      "sisatila-muutos"] :in-any-order)
  (operation-names "Sähkö-, data ja muiden kaapelien sijoittaminen")
  => ["ya-sijoituslupa-sahko-data-ja-muiden-kaapelien-sijoittaminen"])

(facts "sorting parameter parsing"
  (make-sort {:sort {:field "unknown" :asc false}}) => {:modified 1}
  (make-sort {:sort {:field "unknown" :asc true}}) => {:modified -1}
  (make-sort {:sort {:field "id" :asc false}}) => {:_id -1 :modified 1}
  (make-sort {:sort {:field "_id" :asc false}}) => {:modified 1}
  (make-sort {:sort {:field "type" :asc true}}) => {:infoRequest -1 :permitSubtype 1 :modified -1}
  (make-sort {:sort {:field "type" :asc false}}) => {:infoRequest 1 :permitSubtype -1 :modified 1}
  (make-sort {:sort {:field "location" :asc false}}) => {:address -1 :modified 1}
  (make-sort {:sort {:field "applicant" :asc false}}) => {:applicant -1 :modified 1}
  (make-sort {:sort {:field "submitted" :asc true}}) => {:submitted 1 :modified -1}
  (make-sort {:sort {:field "modified" :asc true}}) => {:modified 1}
  (make-sort {:sort {:field "state" :asc true}}) => {:state 1 :modified -1}
  (make-sort {:sort {:field "handler" :asc true}}) => {:handlers.0.lastName 1 :handlers.0.firstName 1 :modified -1}
  (make-sort {:sort {:field {:injection "attempt"}
                     :asc   "; drop database;"}}) => {:modified -1}
  (make-sort {}) => {:modified 1}
  (make-sort nil) => {:modified 1})

(fact "make-query (LUPA-519) with filter-user checks both authority and auth.id"
  (-> (make-query {} {:kind  "both"
                      :applicationType "all"
                      :handlers  ["123"]}
                  {:role "authority"})
      (get "$and")
      second)
  =>
  {:handlers.userId {"$in" ["123"]}})

(fact "query contais user query"
  (-> (make-query {:auth.id "123"} {} {}) (get "$and") first) => {:auth.id "123"})

(fact "Tags are present in query"
  (-> (make-query {} {:tags ["test1" "test2"]} {}) (get "$and") last :tags) => {"$in" ["test1" "test2"]})

(fact "Make query has correct query form"
  (make-query
    {:auth.id "123"}
    {:kind                     "applications"
     :applicationType          "all"
     :handlers                 ["321"]
     :tags                     ["test1" "test2"]
     :includePaperApplications true}
    {:role "authority"
     :id   "123"}) => {"$and"
                       [{:auth.id "123"}
                        {"$and" [{:permitType {"$ne" "ARK"},
                                  :state      {"$ne" "canceled"}}
                                 {"$or" [{:state {"$ne" "draft"}}
                                         {:auth.id "123"}]}]}
                        {:handlers.userId {"$in" ["321"]}}
                        {:tags {"$in" ["test1" "test2"]}}
                        {:primaryOperation.name {"$nin" ["tyonjohtajan-nimeaminen-v2"]}}
                        {:facta-imported {"$ne" true}}]})

(fact "Organizations are present in query"
  (-> (make-query {} {:organizations ["753-R" "753-YA"]} {}) (get "$and") last :organization)
  => {"$in" ["753-R" "753-YA"]})


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

(fact "Should make event query with correct event type"
  (make-query {} {:event {:eventType ["warranty-period-end"], :start 123, :end 134}}
              {:id "sonja"})
  => {"$and" [{"$and" [{:permitType {"$ne" "ARK"} :state {"$ne" "canceled"}}
                       {"$or" [{:state {"$ne" "draft"}} {:auth.id "sonja"}]}]}
              {"$and" [{:warrantyEnd {"$gte" 123 "$lt" 134}}]}]})

(fact "Shouldn't make event query when event type is empty"
  (make-query {} {:event {:eventType [], :start 123, :end 134}}
              {:id "sonja" :company {:id "office"}})
  => {"$and" [{"$and" [{:permitType {"$ne" "ARK"} :state {"$ne" "canceled"}}
                       {"$or" [{:state {"$ne" "draft"}}
                               {:auth.id {$in ["sonja" "office"]}}]}]}]})

(facts "Event queries are correct"
  (make-query {} {:event {:eventType ["warranty-period-end"] :start 123 :end 134}}
              {:id "sonja"})
  => {"$and" [{"$and" [{:permitType {"$ne" "ARK"} :state {"$ne" "canceled"}}
                       {"$or" [{:state {"$ne" "draft"}}
                               {:auth.id "sonja"}]}]}
              {"$and" [{:warrantyEnd {"$gte" 123 "$lt" 134}}]}]}

  (make-query {} {:event {:eventType ["license-period-start"] :start 123 :end 134}}
              {:id "sonja"})
  => {"$and" [{"$and" [{:permitType {"$ne" "ARK"} :state {"$ne" "canceled"}}
                       {"$or" [{:state {"$ne" "draft"}}
                               {:auth.id "sonja"}]}]}
              {"$and" [{:documents.data.tyoaika-alkaa-ms.value {"$gte" 123 "$lt" 134}}]}]}

  (make-query {} {:event {:eventType ["license-period-end"], :start 123 :end 134}}
              {:id "sonja"})
  => {"$and" [{"$and" [{:permitType {"$ne" "ARK"} :state {"$ne" "canceled"}}
                       {"$or" [{:state {"$ne" "draft"}}
                               {:auth.id "sonja"}]}]}
              {"$and" [{:documents.data.tyoaika-paattyy-ms.value {"$gte" 123 "$lt" 134}}]}]})

(facts "Archival queries"
  (fact "Archival query with one organization and archiving starting timestamp"
    (make-query {} {:applicationType          "readyForArchival"
                    :includePaperApplications false}
                {:role "authority" :orgAuthz {:091-R #{:authority :archivist}}})
    => {"$and"
        [{"$and"
          [{"$or" [{:state                {"$in" ["verdictGiven" "constructionStarted" "appealed" "inUse"
                                                  "foremanVerdictGiven" "acknowledged" "ready" "extinct"]}
                    :archived.application nil
                    :permitType           {"$ne" "YA"}}
                   {:state              {"$in" states/archival-final-states}
                    :archived.completed nil
                    :permitType         {"$ne" "ARK"}}]}
           {:organization "091-R"
            :permitType   {"$ne" "ARK"}
            "$or"         [{:submitted {"$gte" 1451599200000}}
                           {:created   {"$gte" 1451599200000}
                            :submitted nil}]}]}
         {:primaryOperation.name {"$nin" ["tyonjohtajan-nimeaminen-v2" "aiemmalla-luvalla-hakeminen"]}}
         {:facta-imported {"$ne" true}}]}
    (provided
      (search/archival-org-datas #{"091-R"}) => [{:id "091-R" :since-ts 1451599200000}]))

  (fact "Archival query including paper applications"
    (make-query {} {:applicationType          "readyForArchival"
                    :includePaperApplications true}
                {:role "authority" :orgAuthz {:091-R #{:authority :archivist}}})
    => {"$and"
        [{"$and"
          [{"$or" [{:state                {"$in" ["verdictGiven" "constructionStarted" "appealed" "inUse"
                                                  "foremanVerdictGiven" "acknowledged" "ready" "extinct"]}
                    :archived.application nil
                    :permitType           {"$ne" "YA"}}
                   {:state              {"$in" states/archival-final-states}
                    :archived.completed nil
                    :permitType         {"$ne" "ARK"}}]}
           {:organization "091-R"
            :permitType   {"$ne" "ARK"}
            "$or"         [{:submitted {"$gte" 1451599200000}}
                           {:created   {"$gte" 1451599200000}
                            :submitted nil}]}]}
         {:primaryOperation.name {"$nin" ["tyonjohtajan-nimeaminen-v2"]}}
         {:facta-imported {"$ne" true}}]}
    (provided
      (search/archival-org-datas #{"091-R"}) => [{:id "091-R" :since-ts 1451599200000}]))

  (fact "Archival query with several organizations and archiving starting timestamp"
    (make-query {} {:applicationType "readyForArchival"}
                {:role "authority" :orgAuthz {:091-R #{:authority :archivist}
                                              :092-R #{:authority :archivist}}})
    => {"$and"
        [{"$and"
          [{"$or" [{:state                {"$in" ["verdictGiven" "constructionStarted" "appealed" "inUse"
                                                  "foremanVerdictGiven" "acknowledged" "ready" "extinct"]}
                    :archived.application nil
                    :permitType           {"$ne" "YA"}}
                   {:state              {"$in" states/archival-final-states}
                    :archived.completed nil
                    :permitType         {"$ne" "ARK"}}]}
           {"$or" [{:organization "091-R"
                    :permitType   {"$ne" "ARK"}
                    "$or"         [{:submitted {"$gte" 1451599200000}}
                                   {:created   {"$gte" 1451599200000}
                                    :submitted nil}]}
                   {:organization "092-R"
                    :permitType   {"$ne" "ARK"}
                    "$or"         [{:submitted {"$gte" 1485900000000}}
                                   {:created   {"$gte" 1485900000000}
                                    :submitted nil}]}]}]}
         {:primaryOperation.name {"$nin" ["tyonjohtajan-nimeaminen-v2"]}}
         {:facta-imported {"$ne" true}}]}
    (provided
      (search/archival-org-datas #{"091-R" "092-R"})
      => [{:id "091-R" :since-ts 1451599200000} {:id "092-R" :since-ts 1485900000000}])))

(facts "parse-search-term"
  (fact "Empty terms"
    (parse-municipality-codes "") => {:original-term "" :parsed-term nil :municipalities nil}
    (parse-municipality-codes nil) => {:original-term nil :parsed-term nil :municipalities nil})
  (fact "No municipality"
    (parse-municipality-codes "hello, world")
    => {:parsed-term    nil
        :original-term  "hello, world"
        :municipalities #{}})
  (fact "Just the municipality part"
    (parse-municipality-codes "Vantaa")
    => {:parsed-term    ""
        :original-term  "Vantaa"
        :municipalities #{"092"}})
  (fact "Just the municipality part: one municipality"
    (parse-municipality-codes "Vantaa")
    => {:parsed-term    ""
        :original-term  "Vantaa"
        :municipalities #{"092"}})
  (fact "Both parts"
    (parse-municipality-codes "Dongdaqiao Lu 88, Humppila")
    => {:parsed-term    "Dongdaqiao Lu 88"
        :original-term  "Dongdaqiao Lu 88, Humppila"
        ;; Humppila
        :municipalities #{"103"}})
  (fact "Must match, starts-with is not enough"
    (parse-municipality-codes "Hu")
    => {:parsed-term    nil
        :original-term  "Hu"
        :municipalities #{}})
  (fact "Preceding whitespace is not mandatory: comma"
    (parse-municipality-codes "Dongdaqiao Lu 88,Humppila")
    => {:parsed-term    "Dongdaqiao Lu 88"
        :original-term  "Dongdaqiao Lu 88,Humppila"
        ;; Humppila
        :municipalities #{"103"}})
  (fact "Preceding whitespace is not mandatory: dot"
    (parse-municipality-codes "Gongti Nan Lu.Huittinen")
    => {:parsed-term    "Gongti Nan Lu"
        :original-term  "Gongti Nan Lu.Huittinen"
        ;; Huittien
        :municipalities #{"102"}}))

(facts "archival-query"
  (against-background [(search/archival-org-datas anything) => nil])
  (fact "no user, base query"
    (archival-query nil nil) => archival-base-query)
  (fact "authority user with one org, base query"
    (archival-query {:orgAuthz {:FOO-R #{:authority}}} nil)
    => archival-base-query)
  (fact "archivist user with one org, base query"
    (archival-query {:orgAuthz {:FOO-R #{:authority :archivist}}} nil)
    => archival-base-query)
  (fact "archivist user with two orgs without archive enabled, base query"
    (archival-query {:orgAuthz {:FOO-R      #{:authority :archivist}
                                :TOINEN-ORG #{:authority :archivist}}}
                    nil)
    => archival-base-query)
  (fact "archivist user with one org no ts, second with ts, third not enabled "
    (archival-query {:orgAuthz {:FOO-R      #{:authority :archivist}
                                :TOINEN-ORG #{:authority :archivist}
                                :BAR-R      #{:authority :archivist}}}
                    nil)
    => {"$and" [archival-base-query
                {"$or" [{:organization "FOO-R"
                         :permitType   {"$ne" "ARK"}}
                        {:organization "TOINEN-ORG"
                         :permitType   {"$ne" "ARK"}
                         "$or"         [{:submitted {"$gte" 123}}
                                        {:created {"$gte" 123} :submitted nil}]}]}]}
    (provided
      (search/archival-org-datas #{"TOINEN-ORG" "FOO-R" "BAR-R"})
      => [{:id "FOO-R"} {:id "TOINEN-ORG" :since-ts 123}]))
  (fact "Query with organizations parameter"
    (archival-query {:orgAuthz {:FOO-R      #{:authority :archivist}
                                :TOINEN-ORG #{:authority :archivist}
                                :BAR-R      #{:authority :archivist}}}
                    {:organizations ["FOO-R"]})
    => {"$and" [archival-base-query
                {:organization "FOO-R"
                 :permitType   {"$ne" "ARK"}}]}
    (provided
      (search/archival-org-datas #{"FOO-R"}) => [{:id "FOO-R"}]))
  (fact "Query with organizations parameter, unknown organization"
    (archival-query {:orgAuthz {:FOO-R      #{:authority :archivist}
                                :TOINEN-ORG #{:authority :archivist}
                                :BAR-R      #{:authority :archivist}}}
                    {:organizations ["NOWHERE-R"]})
    => archival-base-query))

(facts "make-free-text-query"
  (let [check-regex (fn [$regex regex-map]
                      (and (= $regex (get regex-map "$regex"))
                           (= "i" (get regex-map "$options"))))]
    (fact "format is correct"
      (let [result (make-free-text-query "Tunturi-Lapin Vesi Vesilahti")]
        (keys result) => (just "$or")
        (doall
          (for [field-query (get result "$or")
                [k v] field-query]
            (fact {:midje/description (str " regex field " k)}
              v => (partial check-regex "^.*\\QTunturi\\E.+\\QLapin\\E.+\\QVesi\\E.+\\QVesilahti\\E.*$"))))))))

(facts "Test that :applicationType filter triggrered by tabs shows correct applications"
  ;; See lupapalvelu.application-search-itest for readyForArchival test.
  (letfn [(test-for [applicationType roles & {:keys [expected-states-in expected-state expected-state-not
                                                     expect-related-query-term-to-be because]}]
            (doseq [role roles
                    :let [{actual "$and"} (make-query {}
                                                      {:applicationType applicationType}
                                                      (if (= role :applicant)
                                                        {:role role :id "uid"}
                                                        {:role role :id "uid" :orgAuthz {"organization" {}}}))]]
              (fact {:midje/description (str "Testing application type " applicationType " and role" role " " because)}
                (cond expected-state     (get-in (first actual) [:state]) => expected-state
                      expected-state-not (get-in (first actual) [:state "$ne"]) => expected-state-not
                      expected-states-in (get-in (first actual) [:state "$in"]) => expected-states-in
                      expect-related-query-term-to-be (first actual)  => expect-related-query-term-to-be))))]
    (test-for "application"  [:applicant]
              :expected-states-in (just "submitted" "complementNeeded" "draft" "open" "sent"
                                        :in-any-order))
    (test-for "application" [:authority]
              :expected-states-in (just "submitted" "complementNeeded" "sent"
                                        :in-any-order))
    (test-for "construction" [:authority :applicant]
              :expected-states-in (just "constructionStarted" "inUse" "onHold" :in-any-order))
    (test-for "verdict" [:authority :applicant]
              :expected-states-in (just "verdictGiven" "appealed" "foremanVerdictGiven"
                                        "acknowledged" :in-any-order))
    (test-for "inforequest" [:authority]
              :expected-states-in (just "answered" "info" "open" :in-any-order))
    (test-for "finalized" [:authority :applicant]
              :expected-states-in (just "closed" "ready" "finished" "extinct"
                                        "agreementPrepared" "agreementSigned"
                                        :in-any-order))
    (test-for "canceled" [:authority :applicant]
              :expected-state "canceled" )
    (test-for "all" [:applicant]
              :expected-state-not "canceled" )
    (test-for "all" [:authority]
              :expect-related-query-term-to-be {"$and" [{:permitType {"$ne" "ARK"}
                                                         :state {"$ne" "canceled"}}
                                                        {"$or" [{:state {"$ne" "draft"}}
                                                                {:auth.id "uid"}]}]}

              :because "It should exclude:
                       (1) applications of ARK permit type applications and
                       (2) cancelelled applications, and
                       (3) draft applications unless they are users own applications as applicant role.")
    (test-for "archivingProjects" [:authority]
              :expect-related-query-term-to-be {:permitType "ARK" :state {"$nin" [:archived :canceled]}})
    (test-for "non existing applicationType fallbacks (in practice) to all" [:applicant]
              :expected-state-not "canceled")))

(facts "state-filter query"
  (fact "No states (should not happen)"
    (make-query {} {:applicationType "state-filter"}
                [])
    => {"$and" []}
    (make-query {} {:applicationType "state-filter"
                    :tags            ["foo" "bar"]}
                [])
    => {"$and" [{:tags {"$in" ["foo" "bar"]}}]})
  (fact "One state"
    (make-query {} {:applicationType "state-filter"
                    :states          ["open"]}
                [])
    => {"$and" [{:state "open"}]}
    (make-query {} {:applicationType "state-filter"
                    :states          ["open"]
                    :tags            ["foo" "bar"]}
                [])
    => {"$and" [{:state "open"}
                {:tags  {"$in" ["foo" "bar"]}}]})
  (fact "Multiple states"
    (make-query {} {:applicationType "state-filter"
                    :states          ["open" "finished" "sent"]}
                [])
    => {"$and" [{:state {"$in" ["open" "finished" "sent"]}}]}
    (make-query {} {:applicationType "state-filter"
                    :states          ["open" "finished" "sent"]
                    :tags            ["foo" "bar"]}
                [])
    => {"$and" [{:state {"$in" ["open" "finished" "sent"]}}
                {:tags  {"$in" ["foo" "bar"]}}]}))

(facts "includeConverted query"
  (facts "Authority"
    (fact "Default/false"
      (let [result {"$and"
                    [{"$and" [{:permitType {"$ne" "ARK"},
                               :state      {"$ne" "canceled"}}
                              {"$or" [{:state {"$ne" "draft"}}
                                      {:auth.id "sonja"}]}]}
                     {:primaryOperation.name {"$nin" ["tyonjohtajan-nimeaminen-v2"]}}
                     {:facta-imported {"$ne" true}}]}]
        (make-query {}
                    {:kind                     "applications"
                     :applicationType          "all"
                     :includePaperApplications true}
                    {:role "authority" :id "sonja"})
        => result
        (make-query {}
                    {:kind                     "applications"
                     :applicationType          "all"
                     :includeConverted         false
                     :includePaperApplications true}
                    {:role "authority" :id "sonja"})
        => result))
    (fact "True"
      (make-query {}
                  {:kind                     "applications"
                   :applicationType          "all"
                   :includeConverted         true
                   :includePaperApplications true}
                  {:role "authority" :id "sonja"})
      => {"$and"
          [{"$and" [{:permitType {"$ne" "ARK"},
                     :state      {"$ne" "canceled"}}
                    {"$or" [{:state {"$ne" "draft"}}
                            {:auth.id "sonja"}]}]}
           {:primaryOperation.name {"$nin" ["tyonjohtajan-nimeaminen-v2"]}}]}))
  (fact "Others: converted always included"
    (let [result {"$and"
                  [{"$and" [{:permitType {"$ne" "ARK"},
                             :state      {"$ne" "canceled"}}
                            {"$or" [{:state {"$ne" "draft"}}
                                    {:auth.id "pena"}]}]}]}]
      (doseq [m [nil {:includeConverted false} {:includeConverted true}]]
        (make-query {}
                    (merge m {:kind                     "applications"
                              :applicationType          "all"
                              :includePaperApplications true})
                    ;; Silly pseudo-authority test
                    {:id "pena"})
        => result))))
