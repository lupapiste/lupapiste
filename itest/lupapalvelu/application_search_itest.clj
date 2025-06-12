(ns lupapalvelu.application-search-itest
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.elasticsearch.api :as es-api]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.minimal-util :as min-util]
            [lupapalvelu.pate-itest-util :refer [fill-verdict]]
            [lupapalvelu.pate-legacy-itest-util :refer [give-legacy-verdict]]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test :refer [match?]]
            [midje.sweet :refer :all]
            [sade.property :as p]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn- num-of-results? [n response]
  (and (ok? response)
       (= n (count (get-in response [:data :applications])))))

(defn- total-count-is-n? [n response]
  (and (ok? response)
       (= n (get-in response [:data :totalCount]))))

(def no-results? (partial num-of-results? 0))
(def one-result? (partial num-of-results? 1))
(def zero-total? (partial total-count-is-n? 0))
(def one-total? (partial total-count-is-n? 1))
(defn id-matcher [id]
  (fn [response]
    (and (one-result? response)
         (= (get-in response [:data :applications 0 :id]) id))))

(use-fixtures :each (local-fixture-factory (-> {:fixture "minimal"}
                                               test-system/with-remote-components
                                               test-system/with-mock-laundry
                                               test-system/with-elastic)))

(defn process-result
  "Takes care of intended differences in mongo result and elastic result"
  [result]
  (->> result
       :data
       :applications
       (map (util/fn-> (dissoc :documents :foreman :foremanRole :documentModificationsPerDoc)
                       (update :handlers (fn [hs] (seq (map (fn [h] (select-keys h [:userId :roleId :firstName :lastName])) hs))))
                       (update :primaryOperation select-keys [:created :id :name])
                       (update :creator select-keys [:firstName :lastName])
                       util/strip-nils))))

(defn merge-with-defaults [args]
  (->> args
       (apply hash-map)
       (merge {:applicationType "all"
               :limit           1000
               :skip            0
               :sort            {:field "modified" :asc false}})
       vec
       (apply concat)))

(defn search-query [api-key endpoint args]
  (apply datatables api-key endpoint (merge-with-defaults args)))

(defn- search [apikey & args]
  (es-api/full-index-update! (test-system/get-elastic) :elastic.index/applications)
  (let [elastic-result (search-query apikey :applications-search-es args)
        mongo-result   (search-query apikey :applications-search args)]
    (is (= (process-result elastic-result) (process-result mongo-result))
        "Results from Mongo and Elasticsearch should match")
    elastic-result))

(defn- search-total [apikey & args]
  (es-api/full-index-update! (test-system/get-elastic) :elastic.index/applications)
  (let [elastic-result (search-query apikey :applications-search-es args)
        mongo-result   (search-query apikey :applications-search-total-count args)]
    (is (= (-> elastic-result :data :totalCount) (-> mongo-result :data :totalCount))
        "Search count from Mongo and Elasticsearch should match")
    elastic-result))

(defn- map-marker-search [apikey & args]
  (es-api/full-index-update! (test-system/get-elastic) :elastic.index/applications)
  (let [elastic-result (search-query apikey :application-map-markers-search-es args)
        mongo-result   (search-query apikey :application-map-markers-search args)]
    (is (= elastic-result mongo-result)
        "Search count from Mongo and Elasticsearch should match")
    elastic-result))

(defn datatable-query [apikey query-name & args]
  (case query-name
    :applications-search (apply search apikey args)
    :applications-search-total-count (apply search-total apikey args)
    :application-map-markers-search (apply map-marker-search apikey args)
    (apply datatables apikey query-name args)))

(defn- marker-count? [n response]
  (and (ok? response)
       (-> response :markers count (= n))))

(def no-markers? (partial marker-count? 0))
(def one-marker? (partial marker-count? 1))

(defn marker-id-matches? [id]
  (fn [response]
    (and (one-marker? response)
         (= (get-in response [:markers 0 :id]) id))))

(defn- marker-search [query-s & args]
  (apply query mikko :application-map-markers-search :searchText query-s args))

(deftest search-by-text-test
  (let [property-id    (str sonja-muni "-123-0000-1234")
        application    (is (create-and-submit-application mikko
                                                          :propertyId sipoo-property-id
                                                          :address "Hakukuja 123"
                                                          :propertyId (p/to-property-id property-id)
                                                          :operation "muu-uusi-rakentaminen"))
        application-id (:id application)
        id-matches?    (id-matcher application-id)
        marker-id?     (marker-id-matches? application-id)
        hakija-doc     (first (domain/get-documents-by-subtype (:documents application) "hakija"))]

    ;; Setup: set hakija, add secondary operation
    (is (ok? (command mikko :set-current-user-to-document :id application-id :documentId (:id hakija-doc) :path "henkilo")))
    (is (ok? (command mikko :add-operation :id application-id :operation "pientalo")))
    (is (seq (:secondaryOperations (query-application mikko application-id))))

    (testing "Required fields for external LupapisteApi exists"
      (is (set/subset? #{:id :location :address :municipality :applicant :permitType}
                       (set (keys (get-in (search mikko :searchText "") [:data :applications 0]))))))

    (testing "by applicant"
      (testing "no matches" (is (no-results? (search mikko :searchText "Pena"))))
      (testing "no markers" (is (no-markers? (marker-search "Pena"))))
      (testing "total count shows 0" (is (zero-total? (search-total mikko :searchText "Pena"))))
      (testing "one match" (is (id-matches? (search mikko :searchText "Mikko"))))
      (testing "one marker" (is (marker-id? (marker-search "Mikko"))))
      (testing "total count shows 1" (is (one-total? (search-total mikko :searchText "Mikko")))))

    ;; Change to company applicant for subsequent tests
    (is (ok? (command mikko :update-doc :id application-id
                      :doc (:id hakija-doc)
                      :updates [["_selected" "yritys"]
                                ["yritys.yritysnimi" "Firma Oy"]
                                ["yritys.yhteyshenkilo.henkilotiedot.sukunimi" "Widget"]])))

    (testing "Company contact"
      (is (id-matches? (search mikko :searchText "widget"))))

    (testing "by address"
      (testing "no matches" (is (no-results? (search mikko :searchText "hakukatu"))))
      (testing "no markers" (is (no-markers? (marker-search "hakukatu"))))
      (testing "one match" (is (id-matches? (search mikko :searchText "hakukuja"))))
      (testing "one marker" (is (marker-id? (marker-search "hakukuja")))))

    (testing "by ID"
      (testing "no matches" (is (no-results? (search mikko :searchText (str "LP-" sonja-muni "-2010-00001")))))
      (testing "no markers" (is (no-markers? (marker-search (str "LP-" sonja-muni "-2010-00001")))))
      (testing "one match" (is (id-matches? (search mikko :searchText application-id))))
      (testing "one marker" (is (marker-id? (marker-search application-id))))
      (testing "one match - lower case query" (is (id-matches? (search mikko :searchText (ss/lower-case application-id)))))
      (testing "one marker - lower case query" (is (marker-id? (marker-search (ss/lower-case application-id)))))
      (testing "one fuzzy match" (is (id-matches? (search mikko :searchText (subs application-id 3 7)))))
      (testing "one fuzzy marker" (is (marker-id? (marker-search (subs application-id 3 7))))))

    (testing "by property ID"
      (testing "no matches" (is (no-results? (search mikko :searchText (str sonja-muni "-123-0000-1230")))))
      (testing "no markers" (is (no-markers? (marker-search (str sonja-muni "-123-0000-1230")))))
      (testing "one match" (is (id-matches? (search mikko :searchText property-id))))
      (testing "one marker" (is (marker-id? (marker-search property-id)))))

    (testing "by verdict ID"
      (testing "no verdict, no matches" (is (no-results? (search mikko :searchText "Hakupäätös-00753-2014"))))
      (testing "no verdict, no markers" (is (no-markers? (marker-search "Hakupäätös-00753-2014"))))
      (is (give-legacy-verdict sonja application-id :R true :kuntalupatunnus "Hakupäätös-00753-2014-1"))
      (testing "no matches" (is (no-results? (search mikko :searchText "Hakupäätös-00753-2014-2"))))
      (testing "no markers" (is (no-markers? (marker-search "Hakupäätös-00753-2014-2"))))
      (testing "one match" (is (id-matches? (search mikko :searchText "Hakupäätös-00753-2014"))))
      (testing "one marker" (is (marker-id? (marker-search "Hakupäätös-00753-2014"))))
      (testing "search without leading zeros" (is (id-matches? (search mikko :searchText "753-2014"))))
      (testing "Legacy draft"
        (let [{vid :verdict-id} (command sonja :new-legacy-verdict-draft :id application-id)]
          (fill-verdict sonja application-id vid :kuntalupatunnus "Hakupäätös-00753-2014-3")
          (testing "no matches" (is (no-results? (search mikko :searchText "Hakupäätös-00753-2014-2"))))
          (testing "no markers" (is (no-markers? (marker-search "Hakupäätös-00753-2014-2"))))
          (testing "one match" (is (id-matches? (search mikko :searchText "Hakupäätös-00753-2014"))))
          (testing "one marker" (is (marker-id? (marker-search "Hakupäätös-00753-2014")))))))

    (testing "by operation name"
      (testing "no matches" (is (no-results? (search mikko :searchText "vaihtolavan sijoittaminen"))))
      (testing "no markers" (is (no-markers? (marker-search "vaihtolavan sijoittaminen"))))
      (testing "total count shows 0" (is (zero-total? (search-total mikko :searchText "vaihtolavan sijoittaminen"))))
      (testing "one match" (is (id-matches? (search mikko :searchText "Muun kuin edellä mainitun rakennuksen rakentaminen"))))
      (testing "one marker" (is (one-marker? (marker-search "Muun kuin edellä mainitun rakennuksen rakentaminen"))))
      (testing "total count shows 1" (is (one-total? (search-total mikko :searchText "Muun kuin edellä mainitun rakennuksen rakentaminen")))))

    (testing "by a very very long search term"
      (is (ok? (search mikko :searchText (ss/join (repeat 50 "1234567890")))))
      (is ((partial expected-failure? :error.search-text-is-too-long)
           (search mikko :searchText (ss/join (repeat 51 "1234567890")))))
      (is (ok? (marker-search (ss/join (repeat 50 "1234567890")))))
      (is ((partial expected-failure? :error.search-text-is-too-long)
           (marker-search (ss/join (repeat 51 "1234567890"))))))

    (testing "With municipality"
      (testing "Wrong municipality, no match because municipality is $and level search term"
        (is (no-results? (search mikko :searchText "hakukuja, Vantaa"))))
      (testing "Wrong municipality marker" (is (no-markers? (marker-search "hakukuja, Vantaa"))))
      (testing "Correct municipality - exact match" (is (id-matches? (search mikko :searchText "hakukuja, Sipoo"))))
      (testing "Correct municipality marker" (is (marker-id? (marker-search "hakukuja, Sipoo"))))
      (testing "Correct municipality - mismatch address" (is (no-results? (search mikko :searchText "hukkakuja, Sipoo"))))
      (testing "Correct municipality marker - mismatch address" (is (no-markers? (marker-search "hukkakuja, Sipoo"))))
      (testing "Bad municipality, too short" (is (no-results? (search mikko :searchText "hakukuja, Sip"))))
      (testing "Bad municipality marker, too short" (is (no-markers? (marker-search "hakukuja, Sip"))))
      (testing "Bad municipality, too long" (is (no-results? (search mikko :searchText "hakukuja, Sipoot"))))
      (testing "Bad municipality marker, too long" (is (no-markers? (marker-search "hakukuja, Sipoot"))))
      (testing "Municipality must be the last part" (is (no-results? (search mikko :searchText "hakukuja, Sipoo 123"))))
      (testing "Municipality must be the last part marker" (is (no-markers? (marker-search "hakukuja, Sipoo 123"))))
      (testing "Municipality only" (is (id-matches? (search mikko :searchText "Sipoo"))))
      (testing "Municipality only marker" (is (marker-id? (marker-search "Sipoo"))))
      (testing "Internal punctuation does not matter" (is (id-matches? (search mikko :searchText "  Hakukuja ___123,,,Sipoo  "))))
      (testing "Internal punctuation does not matter marker" (is (marker-id? (marker-search "  Hakukuja ___123,,,Sipoo  ")))))))

(deftest marker-infos-test
  (let [application    (is (create-and-submit-application mikko
                                                          :propertyId sipoo-property-id
                                                          :address "Hakukuja 123"
                                                          :operation "muu-uusi-rakentaminen"))
        application-id (:id application)
        hakija-doc     (first (domain/get-documents-by-subtype (:documents application) "hakija"))]

    ;; Setup: set hakija to henkilo
    (is (ok? (command mikko :set-current-user-to-document :id application-id :documentId (:id hakija-doc) :path "henkilo")))

    (testing "Not allowed for Pena"
      (is (empty? (:infos (datatable-query pena :map-marker-infos :ids [application-id])))))

    (testing "Allowed for Mikko"
      (is (match? [(m/equals {:permitType "R" :state "submitted" :infoRequest false
                              :address    "Hakukuja 123" :municipality "753"
                              :submitted  pos? :operation "muu-uusi-rakentaminen"
                              :applicant  "Mikko Intonen" :id application-id})]
                  (:infos (datatable-query mikko :map-marker-infos :ids [application-id])))))

    (testing "Company applicant: no name"
      (is (ok? (command mikko :update-doc :id application-id
                        :doc (:id hakija-doc)
                        :updates [["_selected" "yritys"]])))
      (is (nil? (-> (datatable-query mikko :map-marker-infos :ids [application-id])
                    :infos first :applicant))))

    (testing "Company applicant with name"
      (is (ok? (command mikko :update-doc :id application-id
                        :doc (:id hakija-doc)
                        :updates [["yritys.yritysnimi" "Firma Oy"]
                                  ["yritys.yhteyshenkilo.henkilotiedot.sukunimi" "Widget"]])))
      (is (= "Firma Oy"
             (-> (datatable-query mikko :map-marker-infos :ids [application-id])
                 :infos first :applicant))))))

(deftest applications-endpoint-test
  (let [application    (is (create-and-submit-application mikko
                                                          :propertyId sipoo-property-id
                                                          :address "Hakukuja 123"
                                                          :operation "muu-uusi-rakentaminen"))
        application-id (:id application)]

    ;; Setup: add secondary operation, give verdict
    (is (ok? (command mikko :add-operation :id application-id :operation "pientalo")))
    (is (give-legacy-verdict sonja application-id :R true :kuntalupatunnus "Hakupäätös-00753-2014-1"))

    (testing "applications integration endpoint returns localized data"
      (let [resp        (datatable-query mikko :applications)
            application (util/find-by-id application-id (:applications resp))
            {primary :primaryOperation secondaries :secondaryOperations} application
            {state-fi :stateNameFi state-sv :stateNameSv} application]
        (testing "Primary operation is localized"
          (is (= "Muun kuin edellä mainitun rakennuksen rakentaminen (navetta, liike-, toimisto-, opetus-, päiväkoti-, palvelu-, hoitolaitos- tai muu rakennus)"
                 (:displayNameFi primary)))
          (is (seq (:displayNameSv primary))))

        (testing "Result contains all - and only - the documented keys"
          (is (= (sort [:id :address :applicant
                        :drawings :infoRequest :location
                        :modified :municipality
                        :primaryOperation :secondaryOperations
                        :permitType
                        :state :stateNameFi :stateNameSv
                        :submitted])
                 (sort (keys application))))
          (is (= [:x :y] (sort (keys (:location application))))))

        (testing "Secondary operation is localized"
          (is (= 1 (count secondaries)))
          (is (= "pientalo" (-> secondaries first :name)))
          (is (= "Asuinpientalon rakentaminen (enintään kaksiasuntoinen erillispientalo)"
                 (-> secondaries first :displayNameFi)))
          (is (= "Byggande av småhus (högst ett fristående småhus för två bostäder)"
                 (-> secondaries first :displayNameSv))))

        (testing "Sonja gave verdict"
          (is (= "Päätös annettu" state-fi))
          (is (= "Beslut givet" state-sv)))))))

(deftest search-filters-test
  (let [application    (is (create-and-submit-application sonja
                                                          :address "Latokuja"
                                                          :propertyId "75341600550007"
                                                          :x 404369.304 :y 6693806.957
                                                          :operation "muu-uusi-rakentaminen"))
        application-id (:id application)
        marker-id?     (marker-id-matches? application-id)

        application2   (is (create-and-submit-application sonja
                                                          :address "Hakukuja 10"
                                                          :propertyId (p/to-property-id (str sonja-muni "-123-0000-1234"))
                                                          :operation "purkaminen"))

        modified       (:modified application2)]

    (testing "No applications modified after the last one was created"
      (let [resp (datatable-query sonja :applications :modifiedAfter modified)]
        (is (ok? resp))
        (is (= 0 (-> resp :applications count)))))

    (testing "One applications modified since just before the last one was created"
      (let [resp (datatable-query sonja :applications :modifiedAfter (dec modified))]
        (is (ok? resp))
        (is (= 1 (-> resp :applications count)))))

    (testing "Sonja not defined as handler"
      (is (= 0 (count (get-in (datatable-query sonja :applications-search :handlers [sonja-id]) [:data :applications]))))
      (is (no-markers? (datatable-query sonja :application-map-markers-search :handlers [sonja-id])))
      (is (every? (comp empty? :handlers) [application application2])))

    (let [{handler-id :id :as resp} (command sonja :upsert-application-handler :id application-id
                                             :roleId sipoo-general-handler-id :userId ronja-id)]
      (testing "Set handler"
        (is (ok? resp)))

      (testing "Handler ID filter"
        (let [resp (datatable-query sonja :applications-search :handlers [ronja-id])]
          (is (= 1 (count (get-in resp [:data :applications]))))
          (is (= application-id
                 (get-in (datatable-query sonja :applications-search :handlers [ronja-id]) [:data :applications 0 :id]))))
        (is (one-marker? (datatable-query sonja :application-map-markers-search :handlers [ronja-id]))))

      (testing "Handler email filter"
        (let [resp (datatable-query sonja :applications-search :handlers [(min-util/email-for "ronja")])]
          (is (= 1 (count (get-in resp [:data :applications]))))
          (is (= application-id (get-in resp [:data :applications 0 :id]))))
        (is (one-marker? (datatable-query sonja :application-map-markers-search :handlers [(min-util/email-for "ronja")]))))

      (is (ok? (command sonja :add-application-tags :id application-id :tags ["222222222222222222222222"])))
      (testing "$and query returns 1"
        (is (= 1 (count (get-in (datatable-query sonja :applications-search
                                                 :handlers [ronja-id]
                                                 :tags ["222222222222222222222222"])
                                [:data :applications]))))
        (is (one-marker? (datatable-query sonja :application-map-markers-search
                                          :handlers [ronja-id]
                                          :tags ["222222222222222222222222"]))))

      (testing "Remove handler"
        (is (ok? (command sonja :remove-application-handler :id application-id :handlerId handler-id))))

      (testing "$and query returns 0 when handler is returning 0 matches"
        (is (= 0 (count (get-in (datatable-query sonja :applications-search
                                                 :handlers [ronja-id]
                                                 :tags ["222222222222222222222222"])
                                [:data :applications]))))
        (is (no-markers? (datatable-query sonja :application-map-markers-search
                                          :handlers [ronja-id]
                                          :tags ["222222222222222222222222"])))))

    (testing "Tags filter"
      (is (= application-id
             (get-in (datatable-query sonja :applications-search :tags ["222222222222222222222222"])
                     [:data :applications 0 :id])))
      (is (marker-id? (datatable-query sonja :application-map-markers-search :tags ["222222222222222222222222"]))))

    (testing "Organization areas"
      (let [body     (:body (decode-response (upload-area sipoo "753-R")))
            features (get-in body [:areas :features])
            keskusta (first (filter (fn [feature]
                                      (= "Nikkilä" (get-in feature [:properties :nimi]))) features))]
        (testing "Area filter"
          (let [res        (datatable-query sonja :applications-search :areas [(:id keskusta)])
                marker-res (datatable-query sonja :application-map-markers-search :areas [(:id keskusta)])]
            (is (= 1 (count (get-in res [:data :applications]))))
            (is (= application-id (get-in res [:data :applications 0 :id])))
            (is (one-marker? marker-res))
            (is (marker-id? marker-res))))

        (testing "Combined"
          (let [res        (datatable-query sonja :applications-search
                                            :areas [(:id keskusta)]
                                            :tags ["222222222222222222222222" "111111111111111111111111"])
                marker-res (datatable-query sonja :application-map-markers-search
                                            :areas [(:id keskusta)]
                                            :tags ["222222222222222222222222" "111111111111111111111111"])]
            (is (= 1 (count (get-in res [:data :applications]))))
            (is (= application-id (get-in res [:data :applications 0 :id])))
            (is (one-marker? marker-res))
            (is (marker-id? marker-res))))

        (testing "Area filter works with same id after shapefile is uploaded again"
          (upload-area sipoo "753-R")
          (let [res        (datatable-query sonja :applications-search :areas [(:id keskusta)])
                marker-res (datatable-query sonja :application-map-markers-search :areas [(:id keskusta)])]
            (is (= 1 (count (get-in res [:data :applications]))))
            (is (= application-id (get-in res [:data :applications 0 :id])))
            (is (one-marker? marker-res))
            (is (marker-id? marker-res))))))))

(deftest state-filter-test
  (let [;; Create app with verdict -> verdictGiven state
        app-with-verdict   (is (create-and-submit-application mikko
                                                              :propertyId sipoo-property-id
                                                              :address "Hakukuja 123"
                                                              :operation "muu-uusi-rakentaminen"))
        _                  (is (give-legacy-verdict sonja (:id app-with-verdict) :R true
                                                    :kuntalupatunnus "verdict-1"))

        ;; Create submitted app + foreman
        application        (is (create-and-submit-application sonja
                                                              :address "Latokuja"
                                                              :propertyId "75341600550007"
                                                              :x 404369.304 :y 6693806.957
                                                              :operation "muu-uusi-rakentaminen"))
        application-id     (:id application)
        _                  (is (command sonja :create-foreman-application :id application-id
                                        :taskId "" :foremanRole "ei tiedossa"
                                        :foremanEmail ""))
        ;; Create another submitted app
        _                  (is (create-and-submit-application sonja
                                                              :address "Hakukuja 10"
                                                              :propertyId (p/to-property-id (str sonja-muni "-123-0000-1234"))
                                                              :operation "purkaminen"))

        q                  (fn [action states & others]
                             (apply datatable-query (remove nil?
                                                            (concat [sonja action
                                                                     :applicationType "state-filter"
                                                                     :states states]
                                                                    others))))
        state-search       (fn [states & others]
                             (->> (apply q :applications-search (cons states others))
                                  :data
                                  :applications
                                  (map (comp keyword :state))))
        state-search-count (fn [states & others]
                             (-> (apply q :applications-search-total-count (cons states others))
                                 :data :totalCount))
        state-marker-count (fn [states & others]
                             (-> (apply q :application-map-markers-search (cons states others))
                                 :markers
                                 count))]

    (testing "No states -> every state: three applications (foreman application excluded)"
      (is (= (sort [:submitted :submitted :verdictGiven]) (sort (state-search []))))
      (is (= 3 (state-search-count [])))
      (is (= 3 (state-marker-count []))))
    (testing "Verdict given"
      (is (= [:verdictGiven] (state-search [:verdictGiven])))
      (is (= 1 (state-search-count [:verdictGiven])))
      (is (= 1 (state-marker-count [:verdictGiven]))))
    (testing "Verdict given and finished"
      (is (= [:verdictGiven] (state-search [:verdictGiven :finished])))
      (is (= 1 (state-search-count [:verdictGiven :finished])))
      (is (= 1 (state-marker-count [:verdictGiven :finished]))))
    (testing "Verdict given, finished and submitted"
      (is (= (sort [:submitted :submitted :verdictGiven])
             (sort (state-search [:verdictGiven :finished :submitted]))))
      (is (= 3 (state-search-count [:verdictGiven :finished :submitted])))
      (is (= 3 (state-marker-count [:verdictGiven :finished :submitted]))))
    (testing "Finished"
      (is (= [] (state-search [:finished])))
      (is (= 0 (state-search-count [:finished])))
      (is (= 0 (state-marker-count [:finished]))))
    (testing "State filter with operations"
      (is (= [:open] (state-search [] :operations [:tyonjohtajan-nimeaminen-v2])))
      (is (= 1 (state-search-count [] :operations [:tyonjohtajan-nimeaminen-v2])))
      (is (= 1 (state-marker-count [] :operations [:tyonjohtajan-nimeaminen-v2])))
      (is (= (sort [:submitted :open])
             (sort (state-search [:open :submitted] :operations [:tyonjohtajan-nimeaminen-v2 :purkaminen]))))
      (is (= 2 (state-search-count [:open :submitted] :operations [:tyonjohtajan-nimeaminen-v2 :purkaminen])))
      (is (= 2 (state-marker-count [:open :submitted] :operations [:tyonjohtajan-nimeaminen-v2 :purkaminen]))))))

(deftest application-visibility-and-pagination-test
  (let [;; Create app with verdict -> verdictGiven state
        app-with-verdict (is (create-and-submit-application mikko
                                                            :propertyId sipoo-property-id
                                                            :address "Hakukuja 123"
                                                            :operation "muu-uusi-rakentaminen"))
        _                (is (give-legacy-verdict sonja (:id app-with-verdict) :R true
                                                  :kuntalupatunnus "verdict-1"))

        ;; Create submitted app + foreman
        application      (is (create-and-submit-application sonja
                                                            :address "Latokuja"
                                                            :propertyId "75341600550007"
                                                            :x 404369.304 :y 6693806.957
                                                            :operation "muu-uusi-rakentaminen"))
        application-id   (:id application)
        foreman-app      (command sonja :create-foreman-application :id application-id
                                  :taskId "" :foremanRole "ei tiedossa"
                                  :foremanEmail "")

        ;; Create another submitted app
        application2     (is (create-and-submit-application sonja
                                                            :address "Hakukuja 10"
                                                            :propertyId (p/to-property-id (str sonja-muni "-123-0000-1234"))
                                                            :operation "purkaminen"))
        application-id2  (:id application2)]

    (testing "canceled application and foreman application"
      (is (ok? (command sonja :cancel-application :id application-id :text "test" :lang "fi")))

      (let [{default-res :applications} (datatable-query sonja :applications)
            {unlimited-res :applications} (datatable-query sonja :applications :applicationType "unlimited")
            default-ids (map :id default-res)
            all-ids     (map :id unlimited-res)]
        (testing "are NOT returned with default search"
          (is (= 2 (count default-res)))
          (is (not (some #{application-id} default-ids)))
          (is (not (some #{(:id foreman-app)} default-ids))))

        (testing "are returned with unlimited search"
          (is (= 4 (count unlimited-res)))
          (is (some #{application-id} all-ids))
          (is (some #{(:id foreman-app)} all-ids)))))

    (testing "limit, skip, sort"
      (let [limit 2
            {results :applications} (datatable-query sonja :applications :applicationType "unlimited"
                                                     :sort {:field :id, :asc false} :skip 1 :limit limit)]

        (is (= limit (count results)))
        (testing "Application 2 was the last to be created, not in results"
          (is (not (some #{application-id2} (map :id results)))))
        (testing "Foreman app was the second to last to be created"
          (is (= (:id foreman-app) (-> results first :id))))
        (testing "Application 1 was the third to last to be created"
          (is (= application-id (-> results second :id))))
        (testing "Applications search and limit"
          (is (= limit (-> (datatable-query sonja :applications-search :applicationType "unlimited"
                                            :skip 1 :limit limit)
                           :data :applications count))))
        (testing "Search total count and map markers are not limited"
          (is (= 4 (-> (datatable-query sonja :applications-search-total-count :applicationType "unlimited"
                                        :skip 1 :limit limit)
                       :data :totalCount)))
          (is (= 4 (-> (datatable-query sonja :application-map-markers-search :applicationType "unlimited"
                                        :skip 1 :limit limit)
                       :markers count))))))

    (testing "nothing for Jussi"
      (is (empty? (get-in (datatable-query jussi :applications-search :organizations ["753-R"])
                          [:data :applications]))))))

(deftest inforequest-test
  (let [search-inforequest        #(search mikko :searchText % :applicationType "inforequest")
        marker-search-inforequest #(marker-search % :applicationType "inforequest")
        search-inforequest-total  #(search-total mikko :searchText % :applicationType "inforequest")
        property-id               (str sonja-muni "-123-0000-1234")
        inforequest-id            (is (create-app-id mikko
                                                     :propertyId sipoo-property-id
                                                     :address "Hakukuja 123"
                                                     :propertyId (p/to-property-id property-id)
                                                     :operation "muu-uusi-rakentaminen"
                                                     :infoRequest true))
        id-matches?               (id-matcher inforequest-id)
        marker-id?                (marker-id-matches? inforequest-id)]
    (testing "for inforequest"
      (testing "by creator"
        (testing "no matches for Pena" (is (no-results? (search-inforequest "Pena"))))
        (testing "no markers for Pena" (is (no-markers? (marker-search-inforequest "Pena"))))
        (testing "total count for Pena shows 0" (is (zero-total? (search-inforequest-total "Pena"))))
        (testing "one match for Mikko" (is (id-matches? (search-inforequest "Mikko"))))
        (testing "one marker for Mikko" (is (marker-id? (marker-search-inforequest "Mikko"))))
        (testing "total count for Mikko shows 1" (is (one-total? (search-inforequest-total "Mikko"))))
        (testing "one match for Intonen" (is (id-matches? (search-inforequest "Intonen"))))
        (testing "one marker for Intonen" (is (marker-id? (marker-search-inforequest "Intonen"))))
        (testing "total count for Intonen shows 1" (is (one-total? (search-inforequest-total "Intonen"))))))
    (testing "Map marker info"
      (is (= [{:permitType "R" :state "info" :infoRequest true
               :address    "Hakukuja 123" :municipality "753"
               :operation  "muu-uusi-rakentaminen"
               :applicant  "Mikko Intonen" :id inforequest-id}]
             (:infos (datatable-query mikko :map-marker-infos :ids [inforequest-id])))))))

(deftest authority-as-statement-giver-test
  (let [{:keys [id]} (create-and-submit-application mikko
                                                    :propertyId sipoo-property-id
                                                    :address "Lausuntotie 666"
                                                    :operation "muu-uusi-rakentaminen")]
    (testing "Invite Jussi from Tre as statementGiver"
      (is (ok? (command sonja :request-for-statement
                        :functionCode nil
                        :id id
                        :selectedPersons [{:name "Juzzi" :text "Lausunto" :email "jussi.viranomainen@tampere.fi"}]))))

    (testing "auth for Jussi is there"
      (is (match? (m/embeds [(m/embeds {:username "jussi" :role "statementGiver"})])
                  (:auth (query-application sonja id)))))

    (testing "Search for Jussi"
      (testing "no results as handler"
        (is (empty? (get-in (datatable-query jussi :applications-search
                                             :organizations ["753-R"] :handlers [jussi-id])
                            [:data :applications])))
        (is (no-markers? (datatable-query jussi :application-map-markers-search
                                          :organizations ["753-R"] :handlers [jussi-id]))))
      (testing "Results if only org params"
        (is (match? [(m/embeds {:address "Lausuntotie 666"})]
                    (get-in (datatable-query jussi :applications-search :organizations ["753-R"])
                            [:data :applications])))
        (is (one-total? (datatable-query jussi :applications-search-total-count :organizations ["753-R"])))
        (is (one-marker? (datatable-query jussi :application-map-markers-search :organizations ["753-R"]))))
      (testing "No results with generalHandler"
        (is (empty? (get-in (datatable-query jussi :applications-search :userIsGeneralHandler true)
                            [:data :applications])))
        (is (zero-total? (datatable-query jussi :applications-search-total-count :userIsGeneralHandler true)))
        (is (no-markers? (datatable-query jussi :application-map-markers-search :userIsGeneralHandler true))))
      (testing "Results with 'other roles' selection, because Jussi is statementGiver"
        (is (match? [(m/embeds {:address "Lausuntotie 666"})]
                    (get-in (datatable-query jussi :applications-search :userIsGeneralHandler false)
                            [:data :applications])))
        (is (one-total? (datatable-query jussi :applications-search-total-count :userIsGeneralHandler false)))
        (is (one-marker? (datatable-query jussi :application-map-markers-search :userIsGeneralHandler false)))))))

(deftest ready-for-archival-test
  (let [ready-for-archival (fn []
                             (->> (datatable-query sonja :applications-search :applicationType "readyForArchival")
                                  :data
                                  :applications
                                  (filter #(-> % :address (= "Tavastia 999")))))
        {:keys [id]} (create-and-submit-application mikko
                                                    :propertyId sipoo-property-id
                                                    :address "Tavastia 999"
                                                    :operation "muu-uusi-rakentaminen")]

    (testing "App not listed yet"
      (is (= [] (ready-for-archival))))

    (testing "Sonja adds verdict to the app"
      (is (ok? (command sonja :update-app-bulletin-op-description :id id :description "otsikko julkipanoon")))
      (is (ok? (command sonja :approve-application :id id :lang "fi")))
      (is (ok? (command sonja :check-for-verdict :id id))))

    (testing "App with verdict is listed in ready for archival"
      (is (match? [(m/embeds {:address "Tavastia 999"})] (ready-for-archival))))

    (testing "Sonja extincts the app"
      (is (ok? (command sonja :change-application-state :id id :state "extinct"))))

    (testing "Extinct app still listed in ready for archival"
      (is (match? [(m/embeds {:address "Tavastia 999"})] (ready-for-archival))))))

(deftest get-application-operations-test
  (testing "YA: No R extra operations"
    (let [ops (:operationsByPermitType (query (min-util/apikey-for "olli-ya")
                                              :get-application-operations))]
      (is (empty? (:R ops)))
      (is (seq (:YA ops)))))
  (testing "R: select only one operation for 753-R"
    (is (ok? (command sipoo :set-organization-selected-operations
                      :organizationId "753-R"
                      :operations ["aita"]))))
  (testing "R: implicit operations"
    (let [ops (:operationsByPermitType (query ronja :get-application-operations))]
      (is (empty? (:P ops)))
      (is (= (sort ["aita" "tyonjohtajan-nimeaminen-v2" "aiemmalla-luvalla-hakeminen"
                    "konversio" "konversio-rakennus"])
             (sort (map name (:R ops))))))))

(deftest address-number-letter-suffix-search-test
  (let [addresses    ["Latokuja 13" "Latokuja 31" "Latokuja 3"
                      "Latokuja 3a" "Latokuja 3A" "Latokuja 3 A"
                      "Latokuja 3 a" "Latokuja 3 b"]
        _            (do (doseq [addr addresses]
                           (create-and-submit-application mikko
                                                          :propertyId sipoo-property-id
                                                          :address addr
                                                          :operation "muu-uusi-rakentaminen"))
                         (es-api/full-index-update! (test-system/get-elastic) :elastic.index/applications))
        result-addrs (fn [text]
                       (->> (search-query mikko :applications-search-es [:searchText text])
                            :data
                            :applications
                            (map :address)
                            set))]

    (testing "Street name prefix 'Latok' finds all 8 addresses"
      (is (= (set addresses) (result-addrs "Latok"))))

    (testing "'Latokuja 3' finds only 'Latokuja 3', not 'Latokuja 13' or 'Latokuja 31'"
      (is (= #{"Latokuja 3" "Latokuja 3 A" "Latokuja 3 a"
               "Latokuja 3 b" "Latokuja 3A" "Latokuja 3a"}
             (result-addrs "Latokuja 3"))))

    (let [a-variants #{"Latokuja 3a" "Latokuja 3A" "Latokuja 3 A" "Latokuja 3 a"}]
      (testing "All 'Latokuja 3a' variants found regardless of case and spacing"
        (testing "lowercase 'Latokuja 3a'" (is (= a-variants (result-addrs "Latokuja 3a"))))
        (testing "uppercase 'Latokuja 3A'" (is (= a-variants (result-addrs "Latokuja 3A"))))
        (testing "spaced lowercase 'Latokuja 3 a'" (is (= a-variants (result-addrs "Latokuja 3 a"))))
        (testing "spaced uppercase 'Latokuja 3 A'" (is (= a-variants (result-addrs "Latokuja 3 A"))))))

    (testing "'Latokuja 3b' variants find only 'Latokuja 3 b'"
      (testing "without space 'Latokuja 3b'" (is (= #{"Latokuja 3 b"} (result-addrs "Latokuja 3b"))))
      (testing "with space 'Latokuja 3 b'" (is (= #{"Latokuja 3 b"} (result-addrs "Latokuja 3 b")))))))

(deftest search-sorting-test
  (let [app1    (create-and-submit-application mikko
                                               :propertyId sipoo-property-id
                                               :address "Ankkalinna 1"
                                               :operation "muu-uusi-rakentaminen")
        app2    (create-and-submit-application mikko
                                               :propertyId sipoo-property-id
                                               :address "Ceetie 3"
                                               :operation "muu-uusi-rakentaminen")
        app3    (create-and-submit-application mikko
                                               :propertyId sipoo-property-id
                                               :address "Bpolku 2"
                                               :operation "muu-uusi-rakentaminen")
        get-ids (fn [field asc]
                  (->> (search mikko :sort {:field field :asc asc})
                       :data :applications (map :id)))]

    (testing "Sort by address (location)"
      (is (= [(:id app1) (:id app3) (:id app2)]
             (get-ids "location" true)))
      (is (= [(:id app2) (:id app3) (:id app1)]
             (get-ids "location" false))))

    (testing "Sort by modified ascending (oldest first)"
      (is (= [(:id app1) (:id app2) (:id app3)]
             (get-ids "modified" true))))

    (testing "Sort by modified descending (newest first)"
      (is (= [(:id app3) (:id app2) (:id app1)]
             (get-ids "modified" false))))

    (testing "Sort by id ascending"
      (is (= (sort [(:id app1) (:id app2) (:id app3)])
             (get-ids "id" true))))

    (testing "Sort by id descending"
      (is (= (reverse (sort [(:id app1) (:id app2) (:id app3)]))
             (get-ids "id" false))))

    (testing "Sort by submitted ascending"
      (is (= [(:id app1) (:id app2) (:id app3)]
             (get-ids "submitted" true))))

    (testing "Sort by submitted descending"
      (is (= [(:id app3) (:id app2) (:id app1)]
             (get-ids "submitted" false))))))
