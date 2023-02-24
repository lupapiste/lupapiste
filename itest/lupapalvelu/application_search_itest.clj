(ns lupapalvelu.application-search-itest
  (:require [lupapalvelu.domain :as domain]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
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

(defn- search [query-s & args]
  (apply datatables mikko :applications-search :searchText query-s args))

(defn- search-total [query-s & args]
  (apply datatables mikko :applications-search-total-count :searchText query-s args))

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

(facts* "Search"
  (apply-remote-minimal)

  (let [property-id    (str sonja-muni "-123-0000-1234")
        application    (create-and-submit-application mikko
                                                      :propertyId sipoo-property-id
                                                      :address "Hakukuja 123"
                                                      :propertyId (p/to-property-id property-id)
                                                      :operation "muu-uusi-rakentaminen")
        =>             truthy
        application-id (:id application)
        id-matches?    (id-matcher application-id)
        marker-id?     (marker-id-matches? application-id)
        hakija-doc     (first (domain/get-documents-by-subtype (:documents application) "hakija"))]

    (command mikko :set-current-user-to-document :id application-id :documentId (:id hakija-doc) :path "henkilo") => ok?
    (command mikko :add-operation :id application-id :operation "pientalo") => ok?
    (:secondaryOperations (query-application mikko application-id)) => seq

    (fact "Required fields for external LupapisteApi exists"
      (keys (get-in (search "") [:data :applications 0])) => (contains [:id :location
                                                                        :address :municipality
                                                                        :applicant :permitType] :gaps-ok :in-any-order))
    (facts "by applicant"
      (fact "no matches" (search "Pena") => no-results?)
      (fact "no markers" (marker-search "Pena") => no-markers?)
      (fact "total count shows 0" (search-total "Pena") => zero-total?)
      (fact "one match" (search "Mikko") => id-matches?)
      (fact "one marker" (marker-search "Mikko") => marker-id?)
      (fact "total count shows 1" (search-total "Mikko") => one-total?))

    (facts "Marker infos"
      (fact "Not allowed for Pena"
        (:infos (datatables pena :map-marker-infos :ids [application-id]))
        => empty?)
      (fact "Allowed for Mikko"
        (:infos (datatables mikko :map-marker-infos :ids [application-id]))
        => (just [(just {:permitType "R"             :state        "submitted" :infoRequest false
                         :address    "Hakukuja 123"  :municipality "753"
                         :submitted  pos?            :operation    "muu-uusi-rakentaminen"
                         :applicant  "Mikko Intonen" :id           application-id})]))
      (fact "Company applicant: no name"
        (command mikko :update-doc :id application-id
                 :doc (:id hakija-doc)
                 :updates [[:_selected "yritys"]]
                 :collection "documents") => ok?
        (-> (datatables mikko :map-marker-infos :ids [application-id])
            :infos first :applicant) => nil)
      (fact "Company applicant with name"
        (command mikko :update-doc :id application-id
                 :doc (:id hakija-doc)
                 :updates [[:yritys.yritysnimi "Firma Oy"]
                           [:yritys.yhteyshenkilo.henkilotiedot.sukunimi "Widget"]]
                 :collection "documents") => ok?
        (-> (datatables mikko :map-marker-infos :ids [application-id])
            :infos first :applicant) => "Firma Oy"))

    (fact "Company contact"
      (search "widget") => id-matches?)

    (facts "by address"
      (fact "no matches" (search "hakukatu") => no-results?)
      (fact "no markers" (marker-search "hakukatu") => no-markers?)
      (fact "one match" (search "hakukuja") => id-matches?)
      (fact "one marker" (marker-search "hakukuja") => marker-id?)
      (fact "one fuzzy match" (search "ku 2") => one-result?)
      (fact "one fuzzy marker" (marker-search "ku 2") => one-marker?)
      (fact "one fuzzy case-insensitive match" (search "JA 3") => one-result?)
      (fact "one fuzzy case-insensitive marker" (marker-search "JA 3") => one-marker?))

    (facts "by ID"
      (fact "no matches" (search (str "LP-" sonja-muni "-2010-00001")) => no-results?)
      (fact "no markers" (marker-search (str "LP-" sonja-muni "-2010-00001")) => no-markers?)
      (fact "one match" (search application-id) => id-matches?)
      (fact "one marker" (marker-search application-id) => marker-id?)
      (fact "one match - lower case query" (search (ss/lower-case application-id)) => id-matches?)
      (fact "one marker - lower case query" (marker-search (ss/lower-case application-id)) => marker-id?)
      (fact "one fuzzy match" (search (subs application-id 3 7)) => id-matches?)
      (fact "one fuzzy marker" (marker-search (subs application-id 3 7)) => marker-id?))

    (facts "by property ID"
      (fact "no matches" (search (str sonja-muni "-123-0000-1230")) => no-results?)
      (fact "no markers" (marker-search (str sonja-muni "-123-0000-1230")) => no-markers?)
      (fact "one match" (search property-id) => id-matches?)
      (fact "one marker" (marker-search property-id) => marker-id?))

    (facts "by verdict ID"
      (fact "no verdict, no matches" (search "Hakup\u00e4\u00e4t\u00f6s-2014") => no-results?)
      (fact "no verdict, no markers" (marker-search "Hakup\u00e4\u00e4t\u00f6s-2014") => no-markers?)
      (give-legacy-verdict sonja application-id :kuntalupatunnus "Hakup\u00e4\u00e4t\u00f6s-2014-1") => truthy
      (fact "no matches" (search "Hakup\u00e4\u00e4t\u00f6s-2014-2") => no-results?)
      (fact "no markers" (marker-search "Hakup\u00e4\u00e4t\u00f6s-2014-2") => no-markers?)
      (fact "one match" (search "Hakup\u00e4\u00e4t\u00f6s-2014") => id-matches?)
      (fact "one marker" (marker-search "Hakup\u00e4\u00e4t\u00f6s-2014") => marker-id?)
      (fact "Legacy draft"
        (let [{vid :verdict-id} (command sonja :new-legacy-verdict-draft :id application-id)]
          (fill-verdict sonja application-id vid :kuntalupatunnus "Hakup\u00e4\u00e4t\u00f6s-2014-3")
          (fact "no matches" (search "Hakup\u00e4\u00e4t\u00f6s-2014-2") => no-results?)
          (fact "no markers" (marker-search "Hakup\u00e4\u00e4t\u00f6s-2014-2") => no-markers?)
          (fact "one match" (search "Hakup\u00e4\u00e4t\u00f6s-2014") => id-matches?)
          (fact "one marker" (marker-search "Hakup\u00e4\u00e4t\u00f6s-2014") => marker-id?)
          (fact "also one match" (search "Hakup\u00e4\u00e4t\u00f6s-2014-3") => id-matches?)
          (fact "also one marker" (marker-search "Hakup\u00e4\u00e4t\u00f6s-2014-3") => marker-id?))))

    (facts "by operation name"
      (fact "no matches" (search "vaihtolavan sijoittaminen") => no-results?)
      (fact "no markers" (marker-search "vaihtolavan sijoittaminen") => no-markers?)
      (fact "total count shows 0" (search-total "vaihtolavan sijoittaminen") => zero-total?)
      (fact "one match" (search "Muun kuin edell\u00e4 mainitun rakennuksen rakentaminen") => id-matches?)
      (fact "one marker" (marker-search "Muun kuin edell\u00e4 mainitun rakennuksen rakentaminen") => one-marker?)
      (fact "total count shows 1" (search-total "Muun kuin edell\u00e4 mainitun rakennuksen rakentaminen")
                                  => one-total?))

    (fact "by a very very long search term"
      (search (ss/join (repeat 50 "1234567890"))) => ok?
      (search (ss/join (repeat 51 "1234567890"))) => fail?
      (marker-search (ss/join (repeat 50 "1234567890"))) => ok?
      (marker-search (ss/join (repeat 51 "1234567890"))) => fail?)

    (facts "With municipality"
      (fact "Wrong municipality, no match because municipality is $and level search term"
        (search "hakukuja, Vantaa") => no-results?)
      (fact "Wrong municipality marker" (marker-search "hakukuja, Vantaa") => no-markers?)
      (fact "Correct municipality - exact match" (search "hakukuja, Sipoo") => id-matches?)
      (fact "Correct municipality marker" (marker-search "hakukuja, Sipoo") => marker-id?)
      (fact "Correct municipality - mismatch address" (search "hukkakuja, Sipoo") => no-results?)
      (fact "Correct municipality marker - mismatch address" (marker-search "hukkakuja, Sipoo") => no-markers?)
      (fact "Bad municipality, too short" (search "hakukuja, Sip") => no-results?)
      (fact "Bad municipality marker, too short" (marker-search "hakukuja, Sip") => no-markers?)
      (fact "Bad municipality, too long" (search "hakukuja, Sipoot") => no-results?)
      (fact "Bad municipality marker, too long" (marker-search "hakukuja, Sipoot") => no-markers?)
      (fact "Municipality must be the last part" (search "hakukuja, Sipoo 123") => no-results?)
      (fact "Municipality must be the last part marker" (marker-search "hakukuja, Sipoo 123") => no-markers?)
      (fact "Municipality only" (search "Sipoo") => id-matches?)
      (fact "Municipality only marker" (marker-search "Sipoo") => marker-id?)
      (fact "Internal punctuation does not matter" (search "  Hakukuja ___123,,,Sipoo  ") => id-matches?)
      (fact "Internal punctuation does not matter marker" (marker-search "  Hakukuja ___123,,,Sipoo  ") => marker-id?))

    (fact "Submitted application is returned by latest-applications"
      (let [resp         (query pena :latest-applications)
            applications (:applications resp)
            application  (first applications)]
        resp => ok?
        (count applications) => 1
        (fact "Contains only public information: municipality, operation and timestamp"
          (:municipality application) => sonja-muni
          (get-in application [:operationName :fi]) => "Muun kuin edell\u00e4 mainitun rakennuksen rakentaminen (navetta, liike-, toimisto-, opetus-, p\u00e4iv\u00e4koti-, palvelu-, hoitolaitos- tai muu rakennus)"
          (get-in application [:operationName :sv]) => seq
          (:timestamp application) => pos?
          (count (keys application)) => 4)))

    (fact "applications integration endpoint returns localized data"
      (let [resp                                                         (datatables mikko :applications)
            application                                                  (util/find-by-id application-id (:applications resp))
            {primary :primaryOperation secondaries :secondaryOperations} application
            {state-fi :stateNameFi state-sv :stateNameSv}                application]
        (fact "Primary operation is localized"
          (:displayNameFi primary) => "Muun kuin edell\u00e4 mainitun rakennuksen rakentaminen (navetta, liike-, toimisto-, opetus-, p\u00e4iv\u00e4koti-, palvelu-, hoitolaitos- tai muu rakennus)"
          (:displayNameSv primary) => seq)

        (fact "Result contains all - and only - the documented keys"
          (keys application) => (just [:id :address :applicant
                                       :drawings :infoRequest :location
                                       :modified :municipality
                                       :primaryOperation :secondaryOperations
                                       :permitType
                                       :state :stateNameFi :stateNameSv
                                       :submitted] :in-any-order)
          (keys (:location application)) => (just [:x :y] :in-any-order))

        (fact "Secondary operation is localized"
          (count secondaries) => 1
          (-> secondaries first :name) => "pientalo"
          (-> secondaries first :displayNameFi) =>
          "Asuinpientalon rakentaminen (enint\u00e4\u00e4n kaksiasuntoinen erillispientalo)"
          (-> secondaries first :displayNameSv) =>
          "Byggande av sm\u00e5hus (h\u00f6gst ett frist\u00e5ende sm\u00e5hus f\u00f6r tv\u00e5 bost\u00e4der)")

        (fact "Sonja gave verdict"
          state-fi => "P\u00e4\u00e4t\u00f6s annettu"
          state-sv => "Beslut givet"))))



  (let [property-id     (str sonja-muni "-123-0000-1234")
        application     (create-and-submit-application sonja
                                                       :address "Latokuja"
                                                       :propertyId "75341600550007"
                                                       :x 404369.304 :y 6693806.957
                                                       :operation "muu-uusi-rakentaminen")
        =>              truthy
        application-id  (:id application)
        marker-id?      (marker-id-matches? application-id)
        foreman-app     (command sonja :create-foreman-application :id application-id
                                 :taskId "" :foremanRole "ei tiedossa"
                                 :foremanEmail "")

        application2    (create-and-submit-application sonja
                                                       :address "Hakukuja 10"
                                                       :propertyId (p/to-property-id property-id)
                                                       :operation "purkaminen")
        application-id2 (:id application2)
        modified        (:modified application2)]

    (fact "No applications modified after the last one was created"
      (let [resp (datatables sonja :applications :modifiedAfter modified)]
        resp => ok?
        (-> resp :applications count) => 0))

    (fact "One applications modified since just before the last one was created"
      (let [resp (datatables sonja :applications :modifiedAfter (dec modified))]
        resp => ok?
        (-> resp :applications count) => 1))

    (fact "Sonja not defined as handler"
      (count (get-in (datatables sonja :applications-search :handlers [sonja-id]) [:data :applications]))
      => 0
      (datatables sonja :application-map-markers-search :handlers [sonja-id]) => no-markers?
      (every? (comp empty? :handlers) [application application2])
      => true)

    (let [{handler-id :id :as resp} (command sonja :upsert-application-handler :id application-id
                                             :roleId sipoo-general-handler-id :userId ronja-id)]
      (fact "Set handler"
        resp => ok?)

      (fact "Handler ID filter"
        (let [resp (datatables sonja :applications-search :handlers [ronja-id])]
          (count (get-in resp [:data :applications])) => 1
          (get-in (datatables sonja :applications-search :handlers [ronja-id]) [:data :applications 0 :id])
          => application-id)
        (datatables sonja :application-map-markers-search :handlers [ronja-id]) => one-marker?)

      (fact "Handler email filter"
        (let [resp (datatables sonja :applications-search :handlers [(email-for "ronja")])]
          (count (get-in resp [:data :applications])) => 1
          (get-in resp [:data :applications 0 :id]) => application-id)
        (datatables sonja :application-map-markers-search :handlers [(email-for "ronja")]) => one-marker?)

      (command sonja :add-application-tags :id application-id :tags ["222222222222222222222222"]) => ok?
      (fact "$and query returns 1"
        (count (get-in (datatables sonja :applications-search :handlers [ronja-id] :tags ["222222222222222222222222"])
                       [:data :applications])) => 1
        (datatables sonja :application-map-markers-search :handlers [ronja-id]
                    :tags ["222222222222222222222222"]) => one-marker?)

      (fact "Remove handler"
        (command sonja :remove-application-handler :id application-id :handlerId handler-id) => ok?)

      (fact "$and query returns 0 when handler is returning 0 matches"
        (count (get-in (datatables sonja :applications-search :handlers [ronja-id] :tags ["222222222222222222222222"])
                       [:data :applications])) => 0
        (datatables sonja :application-map-markers-search :handlers [ronja-id]
                    :tags ["222222222222222222222222"]) => no-markers?))

    (fact "Tags filter"
      (get-in (datatables sonja :applications-search :tags ["222222222222222222222222"]) [:data :applications 0 :id])
      => application-id
      (datatables sonja :application-map-markers-search :tags ["222222222222222222222222"]) => marker-id?)

    (facts "Organization areas"
      (let [body     (:body (decode-response (upload-area sipoo "753-R")))
            features (get-in body [:areas :features])
            keskusta (first (filter (fn [feature]
                                      (= "Nikkil\u00e4" (get-in feature [:properties :nimi]))) features))]
        (fact "Area filter"
          (let [res        (datatables sonja :applications-search :areas [(:id keskusta)])
                marker-res (datatables sonja :application-map-markers-search
                                       :areas [(:id keskusta)])]
            (count (get-in res [:data :applications])) => 1
            (get-in res [:data :applications 0 :id]) => application-id
            marker-res => one-marker?
            marker-res => marker-id?))

        (fact "Combined"
          (let [res        (datatables sonja :applications-search
                                       :areas [(:id keskusta)]
                                       :tags ["222222222222222222222222" "111111111111111111111111"])
                marker-res (datatables sonja :application-map-markers-search
                                       :areas [(:id keskusta)]
                                       :tags ["222222222222222222222222" "111111111111111111111111"])]
            (count (get-in res [:data :applications])) => 1
            (get-in res [:data :applications 0 :id]) => application-id
            marker-res => one-marker?
            marker-res => marker-id?))

        (fact "Area filter works with same id after shapefile is uploaded again"
          (upload-area sipoo "753-R")
          (let [res        (datatables sonja :applications-search :areas [(:id keskusta)])
                marker-res (datatables sonja :application-map-markers-search :areas [(:id keskusta)])]
            (count (get-in res [:data :applications])) => 1
            (get-in res [:data :applications 0 :id]) => application-id
            marker-res => one-marker?
            marker-res => marker-id?))))

    (let [q                  (fn [action states & others]
                               (apply datatables (remove nil?
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

      (facts "State filter"
        (fact "No states -> every state: three applications (foreman application excluded)"
          (state-search []) => (just :submitted :submitted :verdictGiven :in-any-order)
          (state-search-count []) => 3
          (state-marker-count []) => 3)
        (fact "Verdict given"
          (state-search [:verdictGiven]) => [:verdictGiven]
          (state-search-count [:verdictGiven]) => 1
          (state-marker-count [:verdictGiven]) => 1)
        (fact "Verdict given and finished"
          (state-search [:verdictGiven :finished]) => [:verdictGiven]
          (state-search-count [:verdictGiven :finished]) => 1
          (state-marker-count [:verdictGiven :finished]) => 1)
        (fact "Verdict given, finished and submitted"
          (state-search [:verdictGiven :finished :submitted])
          => (just :submitted :submitted :verdictGiven :in-any-order)
          (state-search-count [:verdictGiven :finished :submitted]) => 3
          (state-marker-count [:verdictGiven :finished :submitted]) => 3)
        (fact "Finished"
          (state-search [:finished]) => []
          (state-search-count [:finished]) => 0
          (state-marker-count [:finished]) => 0)
        (fact "State filter with operations"
          (state-search [] :operations [:tyonjohtajan-nimeaminen-v2]) => [:open]
          (state-search-count [] :operations [:tyonjohtajan-nimeaminen-v2]) => 1
          (state-marker-count [] :operations [:tyonjohtajan-nimeaminen-v2]) => 1
          (state-search [:open :submitted] :operations [:tyonjohtajan-nimeaminen-v2 :purkaminen])
          => (just :submitted :open :in-any-order)
          (state-search-count [:open :submitted] :operations [:tyonjohtajan-nimeaminen-v2 :purkaminen]) => 2
          (state-marker-count [:open :submitted] :operations [:tyonjohtajan-nimeaminen-v2 :purkaminen]) => 2)))

    (fact "canceled application and foreman application"
      (command sonja :cancel-application :id application-id :text "test" :lang "fi") => ok?

      (let [{default-res :applications}   (datatables sonja :applications)
            {unlimited-res :applications} (datatables sonja :applications :applicationType "unlimited")
            default-ids                   (map :id default-res)
            all-ids                       (map :id unlimited-res)]
        (fact "are NOT returned with default search"
          (count default-res) => 2
          default-ids =not=> (contains application-id)
          default-ids =not=> (contains (:id foreman-app)))

        (fact "are returned with unlimited search"
          (count unlimited-res) => 4
          all-ids => (contains application-id)
          all-ids => (contains (:id foreman-app)))))

    (facts "limit, skip, sort"
      (let [limit                   2
            {results :applications} (datatables sonja :applications :applicationType "unlimited"
                                                ;; latest first, skip the absolute latest and return 2
                                                :sort {:field :id, :asc false} :skip 1 :limit limit)]

        (count results) => limit
        (fact "Application 2 was the last to be creaded, not in results"
          (map :id results) =not=> (contains application-id2))
        (fact "Foreman app was the second to last to be creaded" (-> results first :id) => (:id foreman-app))
        (fact "Application 1 was the third to last to be creaded" (-> results second :id) => application-id)
        (fact "Applications search and limit"
          (-> (datatables sonja :applications-search :applicationType "unlimited"
                          :skip 1 :limit limit)
              :data :applications count) => limit)
        (fact "Search total count and map markers are not limited"
          (-> (datatables sonja :applications-search-total-count :applicationType "unlimited"
                          :skip 1 :limit limit)
              :data :totalCount) => 4
          (-> (datatables sonja :application-map-markers-search :applicationType "unlimited"
                          :skip 1 :limit limit)
              :markers count) => 4)))

    (facts "nothing for Jussi"
      (get-in (datatables jussi :applications-search :organizations ["753-R"]) [:data :applications]) => empty?)))

(facts* "Inforequest"
  (let [search-inforequest #(search % :applicationType "inforequest")
        marker-search-inforequest #(marker-search % :applicationType "inforequest")
        search-inforequest-total #(search-total % :applicationType "inforequest")
        property-id (str sonja-muni "-123-0000-1234")
        inforequest-id (create-app-id mikko
                                      :propertyId sipoo-property-id
                                      :address "Hakukuja 123"
                                      :propertyId (p/to-property-id property-id)
                                      :operation "muu-uusi-rakentaminen"
                                      :infoRequest true) => truthy
        id-matches? (id-matcher inforequest-id)
        marker-id? (marker-id-matches? inforequest-id)]
    (facts "for inforequest"
      (facts "by creator"                                   ; LPK-3911
        (fact "no matches for Pena" (search-inforequest "Pena") => no-results?)
        (fact "no markers for Pena" (marker-search-inforequest "Pena") => no-markers?)
        (fact "total count for Pena shows 0" (search-inforequest-total "Pena") => zero-total?)
        (fact "one match for Mikko" (search-inforequest "Mikko") => id-matches?)
        (fact "one marker for Mikko" (marker-search-inforequest "Mikko") => marker-id?)
        (fact "total count for Mikko shows 1" (search-inforequest-total "Mikko") => one-total?)
        (fact "one match for Intonen" (search-inforequest "Intonen") => id-matches?)
        (fact "one marker for Intonen" (marker-search-inforequest "Intonen") => marker-id?)
        (fact "total count for Intonen shows 1" (search-inforequest-total "Intonen") => one-total?)))
    (fact "Map marker info"
      (:infos (datatables mikko :map-marker-infos :ids [inforequest-id]))
      => (just [(just {:permitType "R" :state "info" :infoRequest true
                       :address "Hakukuja 123" :municipality "753"
                       :operation "muu-uusi-rakentaminen"
                       :applicant "Mikko Intonen" :id inforequest-id})]))))


(facts "Authority as statement giver"
  #_(apply-remote-minimal)
  (let [{:keys [id] :as app} (create-and-submit-application mikko
                                                            :propertyId sipoo-property-id
                                                            :address "Lausuntotie 666"
                                                            :operation "muu-uusi-rakentaminen")]
    (fact "Invite Jussi from Tre as statementGiver"
      (command sonja :request-for-statement
               :functionCode nil
               :id id
               :selectedPersons [{:name "Juzzi" :text "Lausunto" :email"jussi.viranomainen@tampere.fi"}])
      => ok?)

    (fact "auth for Jussi is there"
      (:auth (query-application sonja id)) => (contains
                                                [(contains {:username "jussi"
                                                            :role "statementGiver"})]))

    (facts "Search for Jussi"
      (fact "no results as handler"
        (get-in (datatables jussi :applications-search :organizations ["753-R"] :handlers [jussi-id]) [:data :applications])
        => empty?
        (datatables jussi :application-map-markers-search :organizations ["753-R"] :handlers [jussi-id]) => no-markers?)
      (fact "Results if only org params"
        (get-in (datatables jussi :applications-search :organizations ["753-R"]) [:data :applications])
        => (just [(contains {:address "Lausuntotie 666"})])
        (datatables jussi :applications-search-total-count :organizations ["753-R"])
        => one-total?
        (datatables jussi :application-map-markers-search :organizations ["753-R"])
        => one-marker?)
      (fact "No results with generalHandler"
        (get-in (datatables jussi :applications-search :userIsGeneralHandler true) [:data :applications])
        => empty?
        (datatables jussi :applications-search-total-count :userIsGeneralHandler true) => zero-total?
        (datatables jussi :application-map-markers-search :userIsGeneralHandler true)
        => no-markers?)
      (fact "Results with 'other roles' selection, because Jussi is statementGiver"
        ; Setting userIsGeneralHandler to 'false' means 'all roles, except general handler', thus including statementGiver
        ; LPK-5348
        (get-in (datatables jussi :applications-search :userIsGeneralHandler false) [:data :applications])
        => (just [(contains {:address "Lausuntotie 666"})])
        (datatables jussi :applications-search-total-count :userIsGeneralHandler false) => one-total?
        (datatables jussi :application-map-markers-search :userIsGeneralHandler false)
        => one-marker?))))

(facts* "Ready for archival"
  (let [ready-for-archival (fn []
                             (->> (datatables sonja :applications-search :applicationType "readyForArchival")
                                  :data
                                  :applications
                                  (filter #(-> % :address (= "Tavastia 999"))))) ; ignore pollution from other tests
        {:keys [id]}       (create-and-submit-application mikko
                                                          :propertyId sipoo-property-id
                                                          :address "Tavastia 999"
                                                          :operation "muu-uusi-rakentaminen")]

    (fact "App not listed yet"
      (ready-for-archival) => [])

    (fact "Sonja adds verdict to the app"
      (command sonja :update-app-bulletin-op-description :id id :description "otsikko julkipanoon") => ok?
      (command sonja :approve-application :id id :lang "fi") => ok?
      (command sonja :check-for-verdict :id id) => ok?)

    (fact "App with verdict is listed in ready for archival"
      (ready-for-archival) => (just (contains {:address "Tavastia 999"})))

    (fact "Sonja extincts the app"
      (command sonja :change-application-state :id id :state "extinct") => ok?)

    (fact "Extinct app still listed in ready for archival"
      (ready-for-archival) => (just (contains {:address "Tavastia 999"})))))

(facts "get-application-operations"
  (fact "YA: No R extra operations"
    (let [ops (:operationsByPermitType (query (apikey-for "olli-ya")
                                              :get-application-operations))]
      (:R ops) => empty?
      (:YA ops) => not-empty))
  (fact "R: select only one operation for 753-R"
    (command sipoo :set-organization-selected-operations
             :organizationId "753-R"
             :operations ["aita"]) => ok?)
  (fact "R: implicit operations"
    (let [ops (:operationsByPermitType (query ronja
                                              :get-application-operations))]
      (:P ops) => empty?
      (:R ops) => (just "aita" "tyonjohtajan-nimeaminen-v2" "aiemmalla-luvalla-hakeminen"
                        :in-any-order))))
