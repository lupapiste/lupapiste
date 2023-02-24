(ns lupapalvelu.batchrun-test
  "Tests rely heavily on 'provided' because of non-functional nature (lots of side effects) of batchrun processes."
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer [$set]]
            [taoensso.timbre :refer [debug]]
            [lupapalvelu.allu :as allu-semiconstants :refer [fixed-location->drawing]]
            [lupapalvelu.backing-system.allu.core :as allu]
            [lupapalvelu.backing-system.allu.schemas :refer [ApplicationType FixedLocation]]
            [lupapalvelu.batchrun :as batchrun]
            [lupapalvelu.document.allu-schemas :as da]
            [lupapalvelu.mongo :as mongo]
            [schema.core :as sc]
            [sade.core :refer [now]]
            [sade.schema-generators :as ssg]
            [sade.util :as util]))

(testable-privates lupapalvelu.batchrun
                   fetch-all-application-kinds fetch-all-fixed-locations
                   fetch-reviews-for-organization fetch-reviews-for-organization-permit-type
                   organization-has-krysp-url-function get-valid-applications
                   verdicts-scheduled-for-today)

(fact "fetch-all-application-kinds"
  (let [kinds [:other]
        KINDS ["OTHER"]]
    (sc/with-fn-validation
      (with-redefs [mongo/update-by-id (fn [collection id updates & opts]
                                         (facts "update-by-id arguments"
                                           collection => :allu-data
                                           id => :application-kinds
                                           (sc/check {(sc/eq $set) {ApplicationType (sc/eq kinds)}} updates) => nil
                                           opts => [:upsert true])
                                         nil)]
        (fetch-all-application-kinds) => nil
        (provided (mongo/connected?) => true)
        (provided (allu/logged-in?) => true)
        (provided (allu/load-application-kinds! anything) => KINDS)))))

(sc/defschema ^:private DrawingsPath
  (apply sc/enum (map #(util/kw-path % :drawings) (keys da/application-kinds))))

(fact "fetch-all-fixed-locations"
  (sc/with-fn-validation
    (let [locations (ssg/generate [FixedLocation])
          drawings (some->> locations
                            (allu-semiconstants/make-names-unique :area)
                            (sort-by :area)
                            (map fixed-location->drawing))]
      (with-redefs [mongo/update-by-id (fn [collection id updates & opts]
                                         (facts "update-by-id arguments"
                                           collection => :allu-data
                                           id => :fixed-locations
                                           (sc/check {(sc/eq $set) {DrawingsPath (sc/eq drawings)}} updates) => nil
                                           opts => [:upsert true]))]
        (fetch-all-fixed-locations) => nil
        (provided (mongo/connected?) => true)
        (provided (allu/logged-in?) => true)
        (provided (allu/load-fixed-locations! anything) => locations)))))

(fact "organization-has-krysp-url-function"
  ((organization-has-krysp-url-function {}) {:permitType "T" :organization "FOO"}) => false
  ((organization-has-krysp-url-function {"FOO" {:krysp nil}}) {:permitType "T" :organization "FOO"}) => false
  ((organization-has-krysp-url-function {"FOO" {:krysp {:T {:url ""}}}}) {:permitType "T" :organization "FOO"}) => false
  ((organization-has-krysp-url-function {"FOO" {:krysp {:T {:url "testi"}}}}) {:permitType "T" :organization "FOO"}) => true
  ((organization-has-krysp-url-function {"FOO" {:krysp {:T {:url "testi"}}}}) {:permitType "G" :organization "FOO"}) => false)

(fact "get-valid-applications"
  (get-valid-applications [{:id "FOO" :krysp nil}] {:organization "FOO"}) => (throws AssertionError)
  (get-valid-applications {:id "FOO" :krysp nil} [{:organization "FOO"}]) => (throws AssertionError)
  (get-valid-applications [{:id "FOO" :krysp nil}] [{:organization "FOO"}]) => empty?
  (get-valid-applications [{:id "FOO" :krysp nil}] [{:organization "FAA"}]) => empty?
  (get-valid-applications [{:id "FOO" :krysp nil}] [{:organization "FOO"}]) => empty?
  (get-valid-applications [{:id "FOO" :krysp {:T {:url ""}}}] [{:organization "FOO"}]) => empty?
  (fact "permitType missing"
    (get-valid-applications [{:id "FOO" :krysp {:T {:url ""}}}] [{:organization "FOO"}]) => empty?)
  (fact "success"
    (get-valid-applications [{:id "FOO" :krysp {:T {:url "works"}}}] [{:organization "FOO" :permitType "T"}]) => [{:organization "FOO" :permitType "T"}])
  (fact "wrong organization doesn't fool the function"
    (get-valid-applications [{:id "FOO" :krysp {:T {:url nil}}}
                             {:id "FAA" :krysp {:T {:url "works"}}}] [{:organization "FOO" :permitType "T"}]) => empty?))

(facts fetch-reviews-for-organization-permit-type
  (fact "fetch single application"
    (fetch-reviews-for-organization-permit-type {:id "org-id"} :R [{:id "LP-666-2000-00001"}])
    => [[{:id "LP-666-2000-00001"} "xml1"]]

    (provided (#'lupapalvelu.backing-system.krysp.application-from-krysp/get-application-xmls
               {:id "org-id"} :R :application-id ["LP-666-2000-00001"])
              => {"LP-666-2000-00001" "xml1"}))

  (fact "fetch two applications in a chunk"
    (fetch-reviews-for-organization-permit-type {:id "org-id"} :R [{:id "LP-666-2000-00001"}
                                                                   {:id "LP-ORG-2000-00002"}])
    => [[{:id "LP-666-2000-00001"} "xml1"]
        [{:id "LP-ORG-2000-00002"} "xml2"]]

    (provided (#'lupapalvelu.backing-system.krysp.application-from-krysp/get-application-xmls
               {:id "org-id"} :R :application-id ["LP-666-2000-00001" "LP-ORG-2000-00002"])
              => {"LP-666-2000-00001" "xml1"
                  "LP-ORG-2000-00002" "xml2"}))

  (let [organization {:id "org-id" :krysp {:R {:fetch-chunk-size 2}}}]
    (fact "fetch multiple application in two chunks"
      (fetch-reviews-for-organization-permit-type organization :R [{:id "LP-666-2000-00001"}
                                                                   {:id "LP-ORG-2000-00002"}
                                                                   {:id "LP-ORG-2000-00003"}])
      => [[{:id "LP-666-2000-00001"} "xml1"]
          [{:id "LP-ORG-2000-00002"} "xml2"]
          [{:id "LP-ORG-2000-00003"} "xml3"]]

      (provided (#'lupapalvelu.backing-system.krysp.application-from-krysp/get-application-xmls
                 organization :R :application-id ["LP-666-2000-00001" "LP-ORG-2000-00002"])
                => {"LP-666-2000-00001" "xml1"
                    "LP-ORG-2000-00002" "xml2"})

      (provided (#'lupapalvelu.backing-system.krysp.application-from-krysp/get-application-xmls
                 organization :R :application-id ["LP-ORG-2000-00003"])
                => {"LP-ORG-2000-00003" "xml3"})))

  (let [organization {:id "org-id" :krysp {:R {:fetch-chunk-size 2}}}]
    (fact "fetching missing xmls is retried with backend id"
      (fetch-reviews-for-organization-permit-type organization :R
                                                  [{:id "LP-666-2000-00001"}
                                                   {:id "LP-ORG-2000-00002" :verdicts [{:kuntalupatunnus "bck-id-02"}]}
                                                   {:id "LP-ORG-2000-00003" :verdicts [{:kuntalupatunnus "bck-id-03"}]}
                                                   {:id "LP-ORG-2000-00004" :verdicts [{} {:kuntalupatunnus "bck-id-04"}]}])
      => [[{:id "LP-666-2000-00001"} "xml1"]
          [{:id "LP-ORG-2000-00002" :kuntalupatunnus "bck-id-02" :verdicts [{:kuntalupatunnus "bck-id-02"}]} "xml2"]
          [{:id "LP-ORG-2000-00004" :kuntalupatunnus "bck-id-04" :verdicts [{} {:kuntalupatunnus "bck-id-04"}]} "xml4"]]

      ;; First chunk with app-id
      (provided (#'lupapalvelu.backing-system.krysp.application-from-krysp/get-application-xmls
                 organization :R :application-id ["LP-666-2000-00001" "LP-ORG-2000-00002"])
                => {"LP-666-2000-00001" "xml1"})

      ;; Second chunk with app-id
      (provided (#'lupapalvelu.backing-system.krysp.application-from-krysp/get-application-xmls
                 organization :R :application-id ["LP-ORG-2000-00003" "LP-ORG-2000-00004"])
                => {})

      ;; First chunk with back-end-id
      (provided (#'lupapalvelu.backing-system.krysp.application-from-krysp/get-application-xmls
                 organization :R :kuntalupatunnus ["bck-id-02" "bck-id-03"])
                => {"bck-id-02" "xml2"})

      ;; Second chunk with back-end-id
      (provided (#'lupapalvelu.backing-system.krysp.application-from-krysp/get-application-xmls
                 organization :R :kuntalupatunnus ["bck-id-04"])
                => {"bck-id-04" "xml4"}))))

(facts fetch-reviews-for-organization
  (fact "fetch single application"
    (fetch-reviews-for-organization ..test-user.. 1 {:id "org-id"} [:R] [{:id "LP-666-2000-00001" :permitType "R"}] {})
    => '(["LP-666-2000-00001" ({:ok true})])

    (provided (#'lupapalvelu.batchrun/organization-applications-for-review-fetching "org-id" "R" anything
                                                                                    {:application-ids ["LP-666-2000-00001"]
                                                                                     :include-closed? true})
              => [{:id "LP-666-2000-00001" :permitType "R"}])

    (provided (#'lupapalvelu.batchrun/fetch-reviews-for-organization-permit-type
                {:id "org-id"} "R" [{:id "LP-666-2000-00001" :permitType "R"}])
              => [[{:id "LP-666-2000-00001" :permitType "R"} [..xml1..]]] :times 1)

    (provided (#'lupapalvelu.batchrun/read-reviews-for-application
                ..test-user.. 1 {:id "LP-666-2000-00001" :permitType "R"} ..xml1.. anything)
              => {:result "read result"} :times 1)

    (provided (#'lupapalvelu.batchrun/save-reviews-for-application
                ..test-user.. irrelevant {:id "LP-666-2000-00001" :permitType "R"} {:result "read result"})
              => {:ok true} :times 1)

    (provided (#'lupapalvelu.batchrun/mark-reviews-faulty-for-application
                {:id "LP-666-2000-00001" :permitType "R"} {:result "read result"})
              => irrelevant :times 1))

  (fact "fetch all organization applications - one dropped in fetch"
    (fetch-reviews-for-organization ..test-user.. 1 {:id "org-id" :krysp {:R {:url "url"}}} [:R] nil {})
    => '(["LP-666-2000-00001" ({:ok true})]
         ["LP-ORG-2000-00003" ({:ok true})])


    (provided (#'lupapalvelu.batchrun/organization-applications-for-review-fetching "org-id" :R anything)
              => [{:id "LP-666-2000-00001" :permitType "R"}
                  {:id "LP-ORG-2000-00002" :permitType "R"}
                  {:id "LP-ORG-2000-00003" :permitType "R"}])

    (provided (#'lupapalvelu.batchrun/fetch-reviews-for-organization-permit-type
                {:id "org-id" :krysp {:R {:url "url"}}} :R [{:id "LP-666-2000-00001" :permitType "R"}
                                                            {:id "LP-ORG-2000-00002" :permitType "R"}
                                                            {:id "LP-ORG-2000-00003" :permitType "R"}])
              => [[{:id "LP-666-2000-00001" :permitType "R"} [..xml1..]]
                  [{:id "LP-ORG-2000-00003" :permitType "R"} [..xml3..]]] :times 1)

    (provided (#'lupapalvelu.batchrun/organization-applications-for-review-fetching "org-id" "R" anything
                                                                                    {:application-ids ["LP-666-2000-00001"]
                                                                                     :include-closed? false})
              => [{:id "LP-666-2000-00001" :permitType "R"}])

    (provided (#'lupapalvelu.batchrun/organization-applications-for-review-fetching "org-id" "R" anything
                                                                                    {:application-ids ["LP-ORG-2000-00003"]
                                                                                     :include-closed? false})
              => [{:id "LP-ORG-2000-00003" :permitType "R"}])

    (provided (#'lupapalvelu.batchrun/read-reviews-for-application
                ..test-user.. 1 {:id "LP-666-2000-00001" :permitType "R"} ..xml1.. anything)
              => {:result "read result"} :times 1)

    (provided (#'lupapalvelu.batchrun/read-reviews-for-application
                ..test-user.. 1 {:id "LP-ORG-2000-00003" :permitType "R"} ..xml3.. anything)
              => {:new-faulty-tasks ..faulty-tasks..} :times 1)

    (provided (#'lupapalvelu.batchrun/save-reviews-for-application
                ..test-user.. irrelevant {:id "LP-666-2000-00001" :permitType "R"} {:result "read result"})
              => {:ok true} :times 1)

    (provided (#'lupapalvelu.batchrun/save-reviews-for-application
                ..test-user.. irrelevant {:id "LP-ORG-2000-00003" :permitType "R"} {:new-faulty-tasks ..faulty-tasks..})
              => {:ok true} :times 1)

    (provided (#'lupapalvelu.batchrun/mark-reviews-faulty-for-application
                {:id "LP-666-2000-00001" :permitType "R"} {:result "read result"})
              => irrelevant :times 1)

    (provided (#'lupapalvelu.batchrun/mark-reviews-faulty-for-application
                {:id "LP-ORG-2000-00003" :permitType "R"} {:new-faulty-tasks ..faulty-tasks..})
              => irrelevant :times 1))

  (fact "application becomes irrelevant during review fetch - nothing is done"
    (fetch-reviews-for-organization ..test-user.. 1 {:id "org-id" :krysp {:R {:url "url"}}} [:R] nil {})
    => []


    (provided (#'lupapalvelu.batchrun/organization-applications-for-review-fetching "org-id" :R anything)
              => [{:id "LP-666-2000-00001" :permitType "R"}])

    (provided (#'lupapalvelu.batchrun/fetch-reviews-for-organization-permit-type
                {:id "org-id" :krysp {:R {:url "url"}}} :R [{:id "LP-666-2000-00001" :permitType "R"}])
              => [[{:id "LP-666-2000-00001" :permitType "R"} [..xml1..]]] :times 1)

    (provided (#'lupapalvelu.batchrun/organization-applications-for-review-fetching "org-id" "R" anything
                                                                                    {:application-ids ["LP-666-2000-00001"]
                                                                                     :include-closed? false})
              => []))

  (fact "application changed while reading reviews - changes not saved"
    (fetch-reviews-for-organization ..test-user.. 1 {:id "org-id" :krysp {:R {:url "url"}}} [:R] nil {})
    => '(["LP-666-2000-00001" ({:ok false :desc "Application modified does not match (was: 1, now: 123)"})])

    (provided (#'lupapalvelu.batchrun/organization-applications-for-review-fetching "org-id" :R anything)
              => [{:id "LP-666-2000-00001" :permitType "R" :modified 0}])

    (provided (#'lupapalvelu.organization/get-organization anything anything)
              => {:only-use-inspection-from-backend true
                  :assignments-enabled              true})

    (provided (#'lupapalvelu.batchrun/fetch-reviews-for-organization-permit-type
                {:id "org-id" :krysp {:R {:url "url"}}} :R [{:id "LP-666-2000-00001" :permitType "R" :modified 0}])
              => [[{:id "LP-666-2000-00001" :permitType "R"} [..xml1..]]] :times 1)

    (provided (#'lupapalvelu.batchrun/organization-applications-for-review-fetching "org-id" "R" anything
                                                                                    {:application-ids ["LP-666-2000-00001"]
                                                                                     :include-closed? false})
              => [{:id "LP-666-2000-00001" :permitType "R" :modified 1}])

    (provided (#'lupapalvelu.batchrun/read-reviews-for-application
                ..test-user.. 1 {:id "LP-666-2000-00001" :permitType "R" :modified 1} ..xml1.. anything)
              => {:ok true :updates ..updates..} :times 1)

    (provided (#'lupapalvelu.action/update-application anything {:modified 1} ..updates.. :return-count? true)
              => 0)

    (provided (#'lupapalvelu.domain/get-application-no-access-checking "LP-666-2000-00001")
              => {:modified 123} :times 1)))

(defmethod lupapalvelu.backing-system.krysp.common-reader/get-tunnus-xml-path :TEST [& _]
  [:tunnus])

(def result (atom []))

(defmethod lupapalvelu.permit/fetch-xml-from-krysp :TEST [_ _ _ ids _ & _]
  (Thread/sleep (+ (* (count ids) 30) 1))
  (swap! result conj ids)
  (->> (map (fn [id] {:tag :app-xml :content [{:tag :tunnus :content [id]}]}) ids)
       (hash-map :tag :xml :content)))

(facts "poll-verdicts-for-reviews"
  (fact "single application"
    (batchrun/poll-verdicts-for-reviews :application-ids ["LP-666-2000-00001"]) => nil
    (provided (#'lupapalvelu.logging/log-event :info {:run-by "Automatic review/verdict batchrun"
                                                      :event "Application ids that were found in xml"
                                                      :found-app-ids ["LP-666-2000-00001"]
                                                      :not-found-app-ids []})
              => irrelevant :times 1)

    (provided (#'lupapalvelu.mongo/select-one :organizations {:_id "org-id"} [:krysp])
              => {:id "org-id" :krysp {:TEST {:url "url"}}})

    (provided (#'lupapalvelu.mongo/select :applications {:_id {"$in" ["LP-666-2000-00001"]}})
              => [{:id "LP-666-2000-00001" :permitType "TEST" :organization "org-id"}])

    (provided (#'lupapalvelu.batchrun/orgs-for-review-fetch "org-id")
              => [{:id "org-id" :krysp {:TEST {:url "url"}}}])

    (provided (lupapalvelu.user/batchrun-user ["org-id"])
              => ..test-user..)

    (provided (#'lupapalvelu.batchrun/organization-applications-for-review-fetching "org-id" "TEST" anything
                                                                                    {:application-ids ["LP-666-2000-00001"]
                                                                                     :include-closed? true})
              => [{:id "LP-666-2000-00001" :permitType "TEST"}])

    (provided (#'lupapalvelu.backing-system.krysp.application-from-krysp/group-content-by
                irrelevant "TEST" ["LP-666-2000-00001"] anything)
              =>
              {"LP-666-2000-00001" [..xml1..]})

    (provided (#'lupapalvelu.batchrun/read-reviews-for-application
               ..test-user.. anything {:id "LP-666-2000-00001" :permitType "TEST"} anything anything)
              => {:result "read result"} :times 1)

    (provided (#'lupapalvelu.batchrun/save-reviews-for-application
               ..test-user.. irrelevant {:id "LP-666-2000-00001" :permitType "TEST"} {:result "read result"})
              => {:ok true} :times 1)

    (provided (#'lupapalvelu.batchrun/mark-reviews-faulty-for-application
               {:id "LP-666-2000-00001" :permitType "TEST"} {:result "read result"})
              => irrelevant :times 1)

    (provided (#'lupapalvelu.logging/log-event :info {:run-by "Automatic review checking"
                                                      :event "Start fetching xmls"
                                                      :organization-id "org-id"
                                                      :application-count 1
                                                      :applications ["LP-666-2000-00001"]})
              => ..test-result.. :times 1)

    (provided (#'lupapalvelu.logging/log-event :debug {:run-by "Automatic review/verdict batchrun"
                                                      :event "Start fetching XML(s)"
                                                      :search-type :application-id
                                                      :ids ["LP-666-2000-00001"]})
              => ..test-result.. :times 1)

    (provided (#'lupapalvelu.logging/log-event :info {:run-by "Automatic review checking"
                                                      :event "Review checking finished for organization"
                                                      :organization-id "org-id"
                                                      :application-count 1
                                                      :save-results {"LP-666-2000-00001" '({:ok true})}})
              => ..test-result.. :times 1))353

  (facts "multiple applications - fetches are chunked as per :fetch-chunk-size"
    (fact
        (batchrun/poll-verdicts-for-reviews :application-ids ["lots-of-applications-ids"]) => nil

        (provided (#'lupapalvelu.mongo/select-one :organizations {:_id "org-id"} [:krysp])
                  => {:id "org-id" :krysp {:TEST {:url "url" :fetch-chunk-size 1}}})

        (provided (#'lupapalvelu.mongo/select-one :organizations {:_id "org-id2"} [:krysp])
                  => {:id "org-id2" :krysp {:TEST {:url "url" :fetch-chunk-size 2}}})

        (provided (#'lupapalvelu.mongo/select :applications {:_id {"$in" ["lots-of-applications-ids"]}})
                  => [{:id "LP-123-2000-00001" :permitType "TEST" :organization "org-id"}
                      {:id "LP-321-2000-00001" :permitType "TEST" :organization "org-id2"}
                      {:id "LP-321-2000-00002" :permitType "TEST" :organization "org-id2"}
                      {:id "LP-123-2000-00001" :permitType "TEST" :organization "org-id"}
                      {:id "LP-321-2000-00001" :permitType "TEST" :organization "org-id2"}
                      {:id "LP-321-2000-00002" :permitType "TEST" :organization "org-id2"}
                      {:id "LP-123-2000-00001" :permitType "TEST" :organization "org-id"}
                      {:id "LP-123-2000-00001" :permitType "TEST" :organization "org-id"}
                      {:id "LP-123-2000-00001" :permitType "TEST" :organization "org-id"}
                      {:id "LP-123-2000-00001" :permitType "TEST" :organization "org-id"}])

        (provided (#'lupapalvelu.batchrun/orgs-for-review-fetch "org-id" "org-id2")
                  => [{:id "org-id" :krysp {:TEST {:url "url"  :fetch-chunk-size 1}}}
                      {:id "org-id2" :krysp {:TEST {:url "url"  :fetch-chunk-size 2}}}])

        (provided (lupapalvelu.user/batchrun-user ["org-id" "org-id2"])
                  => ..test-user..)

        (provided (#'lupapalvelu.batchrun/organization-applications-for-review-fetching "org-id" "TEST" anything
                                                                                        {:application-ids ["LP-123-2000-00001"]
                                                                                         :include-closed? true})
                  => [{:id "LP-123-2000-00001" :permitType "TEST"}])

      (provided (#'lupapalvelu.batchrun/organization-applications-for-review-fetching "org-id2" "TEST" anything
                  {:application-ids ["LP-321-2000-00001"]
                   :include-closed? true})
                => [{:id "LP-321-2000-00001" :permitType "TEST"}])

      (provided (#'lupapalvelu.batchrun/organization-applications-for-review-fetching "org-id2" "TEST" anything
                  {:application-ids ["LP-321-2000-00002"]
                   :include-closed? true})
                => [{:id "LP-321-2000-00002" :permitType "TEST"}])

      (provided (#'lupapalvelu.backing-system.krysp.application-from-krysp/group-content-by
                  irrelevant "TEST" ["LP-123-2000-00001"] anything)
                =>
                {"LP-123-2000-00001" [..xml1..]})
      (provided (#'lupapalvelu.backing-system.krysp.application-from-krysp/group-content-by
                  irrelevant "TEST" ["LP-321-2000-00001" "LP-321-2000-00002"] anything)
                =>
                {"LP-321-2000-00001" [..xml1..]
                 "LP-321-2000-00002" [..xml2..]})

      ; there are only three  XML "reponses", thus we do these only three times (previously it was 10, lol?)
        (provided (#'lupapalvelu.batchrun/read-reviews-for-application
                   ..test-user.. anything anything anything anything)
                  => {:result "read result"} :times 3)

        (provided (#'lupapalvelu.batchrun/save-reviews-for-application
                   ..test-user.. irrelevant anything {:result "read result"})
                  => {:ok true} :times 3)


        (provided (#'lupapalvelu.batchrun/mark-reviews-faulty-for-application
                   anything {:result "read result"})
                  => irrelevant :times 3)

        (provided (#'lupapalvelu.logging/log-event :info {:run-by "Automatic review checking"
                                                          :event "Start fetching xmls"
                                                          :organization-id "org-id"
                                                          :application-count 6
                                                          :applications ["LP-123-2000-00001"
                                                                         "LP-123-2000-00001"
                                                                         "LP-123-2000-00001"
                                                                         "LP-123-2000-00001"
                                                                         "LP-123-2000-00001"
                                                                         "LP-123-2000-00001"]})
                  => ..test-result.. :times 1)

        (provided (#'lupapalvelu.logging/log-event :info {:run-by "Automatic review checking"
                                                          :event "Start fetching xmls"
                                                          :organization-id "org-id2"
                                                          :application-count 4
                                                          :applications ["LP-321-2000-00001"
                                                                         "LP-321-2000-00002"
                                                                         "LP-321-2000-00001"
                                                                         "LP-321-2000-00002"]})
                  => ..test-result.. :times 1)

      (provided (#'lupapalvelu.logging/log-event :debug {:run-by "Automatic review/verdict batchrun"
                                                         :event "Start fetching XML(s)"
                                                         :search-type :application-id
                                                         :ids ["LP-123-2000-00001"]})
                => ..test-result.. :times 6)

      (provided (#'lupapalvelu.logging/log-event :debug {:run-by "Automatic review/verdict batchrun"
                                                         :event "Start fetching XML(s)"
                                                         :search-type :application-id
                                                         :ids ["LP-321-2000-00001"
                                                               "LP-321-2000-00002"]})
                => ..test-result.. :times 2)

        (provided (#'lupapalvelu.logging/log-event :info {:run-by "Automatic review checking"
                                                          :event "Review checking finished for organization"
                                                          :organization-id "org-id"
                                                          :application-count 6
                                                          :save-results {"LP-123-2000-00001" '({:ok true})}})
                  => ..test-result.. :times 1)

        (provided (#'lupapalvelu.logging/log-event :info {:run-by "Automatic review checking"
                                                          :event "Review checking finished for organization"
                                                          :organization-id "org-id2"
                                                          :application-count 4
                                                          :save-results {"LP-321-2000-00001" '({:ok true})
                                                                         "LP-321-2000-00002" '({:ok true})}})
                  => ..test-result.. :times 1)

      (provided (#'lupapalvelu.logging/log-event :info {:run-by "Automatic review/verdict batchrun"
                                                        :event "Application ids that were found in xml"
                                                        :found-app-ids ["LP-321-2000-00001" "LP-321-2000-00002"]
                                                        :not-found-app-ids []})
                => irrelevant :times 1)

      (provided (#'lupapalvelu.logging/log-event :info {:run-by "Automatic review/verdict batchrun"
                                                        :event "Application ids that were found in xml"
                                                        :found-app-ids ["LP-123-2000-00001"]
                                                        :not-found-app-ids []})
                => irrelevant :times 1))))

(facts "scheduled-verdict-publishing"
  (let [ts (now)
        past-ts (dec ts)
        future-ts (inc ts)
        make-app (fn [v ] {:pate-verdicts [v]})
        draft {:state {:_value "draft" :_user "test" :_modified 1}}
        scheduled {:state {:_value "scheduled" :_user "test" :_modified 1}}
        past-julkipano {:data {:julkipano past-ts}}
        future-julkipano {:data {:julkipano future-ts}}]
    (against-background [(lupapalvelu.logging/log-event :warn anything) => nil]
      (facts "not OK"
        (fact "draft + past"
          (verdicts-scheduled-for-today ts (make-app (merge draft past-julkipano)))
          => empty?)
        (fact "draft + future"
          (verdicts-scheduled-for-today ts (make-app (merge draft future-julkipano)))
          => empty?)
        (fact "scheduled + future"
          (verdicts-scheduled-for-today ts (make-app (merge scheduled future-julkipano)))
          => empty?)
        (fact "no julkipano"
          (verdicts-scheduled-for-today ts (make-app (merge scheduled {:data {}})))
          => empty?))
      (facts "OK"
        (fact "scheduled + past"
          (verdicts-scheduled-for-today ts (make-app (merge scheduled past-julkipano)))
          => (just [(contains {:data {:julkipano past-ts}})]))))))
