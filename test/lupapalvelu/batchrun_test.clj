(ns lupapalvelu.batchrun-test
  "Tests rely heavily on 'provided' because of non-functional nature (lots of side effects) of batchrun processes."
  (:require [integrant.core :as ig]
            [lupapalvelu.action :as action]
            [lupapalvelu.allu :as allu-semiconstants :refer [fixed-location->drawing]]
            [lupapalvelu.backing-system.allu.core :as allu]
            [lupapalvelu.backing-system.allu.schemas :refer [ApplicationType FixedLocation]]
            [lupapalvelu.backing-system.dmcity.config :as dm-cfg]
            [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.backing-system.krysp.common-reader :as common-reader]
            [lupapalvelu.batchrun :as batchrun]
            [lupapalvelu.document.allu-schemas :as da]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.system.system :as system]
            [lupapalvelu.system.system-util :as system-util]
            [lupapalvelu.user :as usr]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer [$set]]
            [sade.core :refer [now]]
            [sade.env :as env]
            [sade.schema-generators :as ssg]
            [sade.util :as util]
            [schema.core :as sc]))

(set! *warn-on-reflection* true)

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
          drawings  (some->> locations
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

(def dmcity (dm-cfg/simple->config {:username "hello" :password "world"
                                    :vault    "howdy" :url      "http://example.net"
                                    :targets  {:R true}}))

(fact "organization-has-krysp-url-function"
  ((organization-has-krysp-url-function {}) {:permitType "T" :organization "FOO"}) => false
  ((organization-has-krysp-url-function {"FOO" {:krysp nil}}) {:permitType "T" :organization "FOO"}) => false
  ((organization-has-krysp-url-function {"FOO" {:krysp {:T {:url ""}}}})
   {:permitType "T" :organization "FOO"}) => false
  ((organization-has-krysp-url-function {"FOO" {:krysp {:T {:url "testi"}}}})
   {:permitType "T" :organization "FOO"}) => true
  ((organization-has-krysp-url-function {"FOO" {:krysp {:T {:url "testi"}}}})
   {:permitType "G" :organization "FOO"}) => false
  ((organization-has-krysp-url-function {"FOO" {:krysp  {:R {:url "testi"}}
                                                :dmcity dmcity}})
   {:permitType "R" :organization "FOO"}) => false
  ((organization-has-krysp-url-function {"FOO" {:krysp  {:YA {:url "testi"}}
                                                :dmcity dmcity}})
   {:permitType "YA" :organization "FOO"}) => true)

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

    (provided (#'krysp-fetch/get-application-xmls
                {:id "org-id"} :R :application-id ["LP-666-2000-00001"])
              => {"LP-666-2000-00001" "xml1"}))

  (fact "fetch two applications in a chunk"
    (fetch-reviews-for-organization-permit-type {:id "org-id"} :R [{:id "LP-666-2000-00001"}
                                                                   {:id "LP-ORG-2000-00002"}])
    => [[{:id "LP-666-2000-00001"} "xml1"]
        [{:id "LP-ORG-2000-00002"} "xml2"]]

    (provided (#'krysp-fetch/get-application-xmls
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

      (provided (#'krysp-fetch/get-application-xmls
                  organization :R :application-id ["LP-666-2000-00001" "LP-ORG-2000-00002"])
                => {"LP-666-2000-00001" "xml1"
                    "LP-ORG-2000-00002" "xml2"})

      (provided (#'krysp-fetch/get-application-xmls
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
      (provided (#'krysp-fetch/get-application-xmls
                  organization :R :application-id ["LP-666-2000-00001" "LP-ORG-2000-00002"])
                => {"LP-666-2000-00001" "xml1"})

      ;; Second chunk with app-id
      (provided (#'krysp-fetch/get-application-xmls
                  organization :R :application-id ["LP-ORG-2000-00003" "LP-ORG-2000-00004"])
                => {})

      ;; First chunk with back-end-id
      (provided (#'krysp-fetch/get-application-xmls
                  organization :R :kuntalupatunnus ["bck-id-02" "bck-id-03"])
                => {"bck-id-02" "xml2"})

      ;; Second chunk with back-end-id
      (provided (#'krysp-fetch/get-application-xmls
                  organization :R :kuntalupatunnus ["bck-id-04"])
                => {"bck-id-04" "xml4"}))))

(facts fetch-reviews-for-organization
  (fact "fetch single application"
    (fetch-reviews-for-organization {} ..test-user.. 1 {:id "org-id"} [:R] [{:id "LP-666-2000-00001" :permitType "R"}] {})
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
                {} ..test-user.. irrelevant {:id "LP-666-2000-00001" :permitType "R"} {:result "read result"})
              => {:ok true} :times 1)

    (provided (#'lupapalvelu.batchrun/mark-reviews-faulty-for-application
                {:id "LP-666-2000-00001" :permitType "R"} {:result "read result"})
              => irrelevant :times 1))

  (fact "fetch all organization applications - one dropped in fetch"
    (fetch-reviews-for-organization {} ..test-user.. 1 {:id "org-id" :krysp {:R {:url "url"}}} [:R] nil {})
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
                {} ..test-user.. irrelevant {:id "LP-666-2000-00001" :permitType "R"} {:result "read result"})
              => {:ok true} :times 1)

    (provided (#'lupapalvelu.batchrun/save-reviews-for-application
                {} ..test-user.. irrelevant {:id "LP-ORG-2000-00003" :permitType "R"} {:new-faulty-tasks ..faulty-tasks..})
              => {:ok true} :times 1)

    (provided (#'lupapalvelu.batchrun/mark-reviews-faulty-for-application
                {:id "LP-666-2000-00001" :permitType "R"} {:result "read result"})
              => irrelevant :times 1)

    (provided (#'lupapalvelu.batchrun/mark-reviews-faulty-for-application
                {:id "LP-ORG-2000-00003" :permitType "R"} {:new-faulty-tasks ..faulty-tasks..})
              => irrelevant :times 1))

  (fact "application becomes irrelevant during review fetch - nothing is done"
    (fetch-reviews-for-organization {} ..test-user.. 1 {:id "org-id" :krysp {:R {:url "url"}}} [:R] nil {})
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
    (fetch-reviews-for-organization {} ..test-user.. 1 {:id "org-id" :krysp {:R {:url "url"}}} [:R] nil {})
    => '(["LP-666-2000-00001" ({:ok false :desc "Application modified does not match (was: 1, now: 123)"})])

    (provided (#'lupapalvelu.batchrun/organization-applications-for-review-fetching "org-id" :R anything)
              => [{:id "LP-666-2000-00001" :permitType "R" :modified 0}])

    (provided (#'org/get-organization anything anything)
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

    (provided (#'action/update-application anything {:modified 1} anything :return-count? true)
              => 0)

    (provided (#'domain/get-application-no-access-checking "LP-666-2000-00001")
              => {:modified 123} :times 1)))

(defmethod common-reader/get-tunnus-xml-path :TEST [& _]
  [:tunnus])

(def result (atom []))

(defmethod permit/fetch-xml-from-krysp :TEST [_ _ _ ids _ & _]
  (Thread/sleep (+ (* (count ids) 30) 1))
  (swap! result conj ids)
  (->> (map (fn [id] {:tag :app-xml :content [{:tag :tunnus :content [id]}]}) ids)
       (hash-map :tag :xml :content)))

(facts "orgs-for-review-fetch"
  (fact "Default"
    (batchrun/orgs-for-review-fetch) => :good
    (provided
      (mongo/select :organizations
                    {:deactivated                    {"$ne" true}
                     :krysp.R.url                    {"$exists" true}
                     :krysp.R.version                {"$gte" "2.1.5"}
                     :dmcity.targets.R.enabled       {"$ne" true}
                     :automatic-review-fetch-enabled true}
                    [:krysp]) => :good))
  (fact "Organizations"
    (batchrun/orgs-for-review-fetch {:organization-ids ["org1" "org2"]}) => :good
    (provided
      (mongo/select :organizations
                    {:deactivated              {"$ne" true}
                     :krysp.R.url              {"$exists" true}
                     :krysp.R.version          {"$gte" "2.1.5"}
                     :dmcity.targets.R.enabled {"$ne" true}
                     :_id                      {"$in" ["org1" "org2"]}}
                    [:krysp]) => :good))
  (fact "dmcity-allowed?"
    (batchrun/orgs-for-review-fetch {:dmcity-allowed? true}) => :good
    (provided
      (mongo/select :organizations
                    {:deactivated                    {"$ne" true}
                     :krysp.R.url                    {"$exists" true}
                     :krysp.R.version                {"$gte" "2.1.5"}
                     :automatic-review-fetch-enabled true}
                    [:krysp]) => :good))
  (fact "Both"
    (batchrun/orgs-for-review-fetch {:organization-ids ["org1" "org2"]
                                     :dmcity-allowed?  true}) => :good
    (provided
      (mongo/select :organizations
                    {:deactivated     {"$ne" true}
                     :krysp.R.url     {"$exists" true}
                     :krysp.R.version {"$gte" "2.1.5"}
                     :_id             {"$in" ["org1" "org2"]}}
                    [:krysp]) => :good)))

(facts "poll-verdicts-for-reviews"
  (fact "single application"
    (batchrun/poll-verdicts-for-reviews {} :application-ids ["LP-666-2000-00001"]) => nil
    (provided (#'log/log-event :info {:run-by            "Automatic review/verdict batchrun"
                                      :event             "Application ids that were found in xml"
                                      :organization-id   "org-id"
                                      :permit-type       "TEST"
                                      :found-app-ids     ["LP-666-2000-00001"]
                                      :not-found-app-ids []})
              => irrelevant :times 1)

    (provided (#'lupapalvelu.mongo/select-one :organizations {:_id "org-id"} [:krysp])
              => {:id "org-id" :krysp {:TEST {:url "url"}}})

    (provided (#'lupapalvelu.mongo/select :applications {:_id        {"$in" ["LP-666-2000-00001"]}
                                                         :permitType :R})
              => [{:id "LP-666-2000-00001" :permitType "TEST" :organization "org-id"}])

    (provided (#'lupapalvelu.batchrun/orgs-for-review-fetch {:organization-ids ["org-id"]
                                                             :dmcity-allowed?  true})
              => [{:id "org-id" :krysp {:TEST {:url "url"}}}])

    (provided (usr/batchrun-user ["org-id"])
              => ..test-user..)

    (provided (#'lupapalvelu.batchrun/organization-applications-for-review-fetching "org-id" "TEST" anything
                                                                                    {:application-ids ["LP-666-2000-00001"]
                                                                                     :include-closed? true})
              => [{:id "LP-666-2000-00001" :permitType "TEST"}])

    (provided (#'krysp-fetch/group-content-by
                irrelevant "TEST" ["LP-666-2000-00001"] anything)
              =>
              {"LP-666-2000-00001" [..xml1..]})

    (provided (#'lupapalvelu.batchrun/read-reviews-for-application
                ..test-user.. anything {:id "LP-666-2000-00001" :permitType "TEST"} anything anything)
              => {:result "read result"} :times 1)

    (provided (#'lupapalvelu.batchrun/save-reviews-for-application
                {} ..test-user.. irrelevant {:id "LP-666-2000-00001" :permitType "TEST"} {:result "read result"})
              => {:ok true} :times 1)

    (provided (#'lupapalvelu.batchrun/mark-reviews-faulty-for-application
                {:id "LP-666-2000-00001" :permitType "TEST"} {:result "read result"})
              => irrelevant :times 1)

    (provided (#'log/log-event :info {:run-by            "Automatic review checking"
                                      :event             "Start fetching xmls"
                                      :organization-id   "org-id"
                                      :permit-type       "TEST"
                                      :application-count 1
                                      :applications      ["LP-666-2000-00001"]})
              => ..test-result.. :times 1)

    (provided (#'log/log-event :debug {:run-by          "Automatic review/verdict batchrun"
                                       :event           "Start fetching XML(s)"
                                       :organization-id "org-id"
                                       :permit-type     "TEST"
                                       :search-type     :application-id
                                       :ids             ["LP-666-2000-00001"]})
              => ..test-result.. :times 1)

    (provided (#'log/log-event :info {:run-by            "Automatic review checking"
                                      :event             "Review checking finished for organization"
                                      :organization-id   "org-id"
                                      :application-count 1
                                      :save-results      {"LP-666-2000-00001" '({:ok true})}})
              => ..test-result.. :times 1))353

  (facts "multiple applications - fetches are chunked as per :fetch-chunk-size"
    (fact
        (batchrun/poll-verdicts-for-reviews {} :application-ids ["lots-of-applications-ids"]) => nil

        (provided (#'lupapalvelu.mongo/select-one :organizations {:_id "org-id"} [:krysp])
                  => {:id "org-id" :krysp {:TEST {:url "url" :fetch-chunk-size 1}}})

        (provided (#'lupapalvelu.mongo/select-one :organizations {:_id "org-id2"} [:krysp])
                  => {:id "org-id2" :krysp {:TEST {:url "url" :fetch-chunk-size 2}}})

        (provided (#'lupapalvelu.mongo/select :applications {:_id        {"$in" ["lots-of-applications-ids"]}
                                                             :permitType :R})
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

        (provided (#'lupapalvelu.batchrun/orgs-for-review-fetch {:organization-ids ["org-id" "org-id2"]
                                                                 :dmcity-allowed?  true})
                  => [{:id "org-id" :krysp {:TEST {:url "url" :fetch-chunk-size 1}}}
                      {:id "org-id2" :krysp {:TEST {:url "url" :fetch-chunk-size 2}}}])

        (provided (usr/batchrun-user ["org-id" "org-id2"])
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

        (provided (#'krysp-fetch/group-content-by
                    irrelevant "TEST" ["LP-123-2000-00001"] anything)
                  =>
                  {"LP-123-2000-00001" [..xml1..]})
        (provided (#'krysp-fetch/group-content-by
                    irrelevant "TEST" ["LP-321-2000-00001" "LP-321-2000-00002"] anything)
                  =>
                  {"LP-321-2000-00001" [..xml1..]
                   "LP-321-2000-00002" [..xml2..]})

                                        ; there are only three  XML "reponses", thus we do these only three times (previously it was 10, lol?)
        (provided (#'lupapalvelu.batchrun/read-reviews-for-application
                    ..test-user.. anything anything anything anything)
                  => {:result "read result"} :times 3)

        (provided (#'lupapalvelu.batchrun/save-reviews-for-application
                    {} ..test-user.. irrelevant anything {:result "read result"})
                  => {:ok true} :times 3)


        (provided (#'lupapalvelu.batchrun/mark-reviews-faulty-for-application
                    anything {:result "read result"})
                  => irrelevant :times 3)

        (provided (#'log/log-event :info {:run-by            "Automatic review checking"
                                          :event             "Start fetching xmls"
                                          :organization-id   "org-id"
                                          :permit-type       "TEST"
                                          :application-count 6
                                          :applications      ["LP-123-2000-00001"
                                                              "LP-123-2000-00001"
                                                              "LP-123-2000-00001"
                                                              "LP-123-2000-00001"
                                                              "LP-123-2000-00001"
                                                              "LP-123-2000-00001"]})
                  => ..test-result.. :times 1)

        (provided (#'log/log-event :info {:run-by            "Automatic review checking"
                                          :event             "Start fetching xmls"
                                          :organization-id   "org-id2"
                                          :permit-type       "TEST"
                                          :application-count 4
                                          :applications      ["LP-321-2000-00001"
                                                              "LP-321-2000-00002"
                                                              "LP-321-2000-00001"
                                                              "LP-321-2000-00002"]})
                  => ..test-result.. :times 1)

        (provided (#'log/log-event :debug {:run-by          "Automatic review/verdict batchrun"
                                           :event           "Start fetching XML(s)"
                                           :organization-id "org-id"
                                           :permit-type     "TEST"
                                           :search-type     :application-id
                                           :ids             ["LP-123-2000-00001"]})
                  => ..test-result.. :times 6)

        (provided (#'log/log-event :debug {:run-by          "Automatic review/verdict batchrun"
                                           :event           "Start fetching XML(s)"
                                           :organization-id "org-id2"
                                           :permit-type     "TEST"
                                           :search-type     :application-id
                                           :ids             ["LP-321-2000-00001"
                                                             "LP-321-2000-00002"]})
                  => ..test-result.. :times 2)

        (provided (#'log/log-event :info {:run-by            "Automatic review checking"
                                          :event             "Review checking finished for organization"
                                          :organization-id   "org-id"
                                          :application-count 6
                                          :save-results      {"LP-123-2000-00001" '({:ok true})}})
                  => ..test-result.. :times 1)

        (provided (#'log/log-event :info {:run-by            "Automatic review checking"
                                          :event             "Review checking finished for organization"
                                          :organization-id   "org-id2"
                                          :application-count 4
                                          :save-results      {"LP-321-2000-00001" '({:ok true})
                                                              "LP-321-2000-00002" '({:ok true})}})
                  => ..test-result.. :times 1)

        (provided (#'log/log-event :info {:run-by            "Automatic review/verdict batchrun"
                                          :event             "Application ids that were found in xml"
                                          :organization-id   "org-id2"
                                          :permit-type       "TEST"
                                          :found-app-ids     ["LP-321-2000-00001" "LP-321-2000-00002"]
                                          :not-found-app-ids []})
                  => irrelevant :times 1)

        (provided (#'log/log-event :info {:run-by            "Automatic review/verdict batchrun"
                                          :event             "Application ids that were found in xml"
                                          :organization-id   "org-id"
                                          :permit-type       "TEST"
                                          :found-app-ids     ["LP-123-2000-00001"]
                                          :not-found-app-ids []})
                  => irrelevant :times 1)))
  (fact "Application given but not found"
    (batchrun/poll-verdicts-for-reviews {} :application-ids ["app-id"])
    => (throws Exception #"No suitable applications found.")
    (provided
      (#'lupapalvelu.mongo/select :applications {:_id        {"$in" ["app-id"]}
                                                 :permitType :R})
      => [])))

(facts "scheduled-verdict-publishing"
  (let [ts               (now)
        past-ts          (dec ts)
        future-ts        (inc ts)
        make-app         (fn [v] {:pate-verdicts [v]})
        draft            {:state {:_value "draft" :_user "test" :_modified 1}}
        scheduled        {:state {:_value "scheduled" :_user "test" :_modified 1}}
        past-julkipano   {:data {:julkipano past-ts}}
        future-julkipano {:data {:julkipano future-ts}}
        past-kuulutus    {:data {:k-kuulutus past-ts}}
        future-kuulutus  {:data {:k-kuulutus future-ts}}]
    (against-background [(log/log-event :warn anything) => nil]
      (facts "not OK"
        (fact "draft + past"
          (verdicts-scheduled-for-today ts (make-app (merge draft past-julkipano)))
          => empty?)
        (fact "draft + past (kuulutus)"
          (verdicts-scheduled-for-today ts (make-app (merge draft past-kuulutus)))
          => empty?)
        (fact "draft + future"
          (verdicts-scheduled-for-today ts (make-app (merge draft future-julkipano)))
          => empty?)
        (fact "draft + future (kuulutus)"
          (verdicts-scheduled-for-today ts (make-app (merge draft future-kuulutus)))
          => empty?)
        (fact "scheduled + future"
          (verdicts-scheduled-for-today ts (make-app (merge scheduled future-julkipano)))
          => empty?)
        (fact "scheduled + future (kuulutus)"
          (verdicts-scheduled-for-today ts (make-app (merge scheduled future-kuulutus)))
          => empty?)
        (fact "no julkipano"
          (verdicts-scheduled-for-today ts (make-app (merge scheduled {:data {}})))
          => empty?))
      (facts "OK"
        (fact "scheduled + past"
          (verdicts-scheduled-for-today ts (make-app (merge scheduled past-julkipano)))
          => (just [(contains {:data {:julkipano past-ts}})]))
        (fact "scheduled + past (kuulutus)"
          (verdicts-scheduled-for-today ts (make-app (merge scheduled past-kuulutus)))
          => (just [(contains {:data {:k-kuulutus past-ts}})]))))))

(against-background
    [(system-util/get-raw-system) => {}
     (lupapalvelu.backing-system.allu.core/login!) => nil]
  (facts "resubmit-allu-applications"
    (fact "Success"
      (batchrun/do-resubmit-allu-applications {}) => nil
      (provided
        (env/feature? :allu) => true
        (domain/get-multiple-applications-no-access-checking
          {:organization            "091-YA"
           :state                   :submitted
           :integrationKeys.ALLU.id nil}) => [{:organization "091-YA"
                                               :permitType   (rand-nth ["A" "YA"])}]
        (lupapalvelu.backing-system.allu.core/submit-application! anything) => nil))
    (fact "Allu not enabled"
      (batchrun/do-resubmit-allu-applications {}) => nil
      (provided
        (env/feature? :allu) => false
        (domain/get-multiple-applications-no-access-checking
          {:organization            "091-YA"
           :state                   :submitted
           :integrationKeys.ALLU.id nil}) => [{:organization "091-YA"
                                               :permitType   (rand-nth ["A" "YA"])}]
        (lupapalvelu.backing-system.allu.core/submit-application! anything)
        => nil :times 0))
    (fact "Unsupported applications (should not happen)"
      (batchrun/do-resubmit-allu-applications {}) => nil
      (provided
        (env/feature? :allu) => true
        (domain/get-multiple-applications-no-access-checking
          {:organization            "091-YA"
           :state                   :submitted
           :integrationKeys.ALLU.id nil})
        => [{:organization "091-YA" :permitType "R"}
            {:organization "091-R" :permitType "YA"}]
        (lupapalvelu.backing-system.allu.core/submit-application! anything)
        => nil :times 0))
    (fact "Exceptions are caught"
      (batchrun/do-resubmit-allu-applications {}) => nil
      (provided
        (env/feature? :allu) => true
        (domain/get-multiple-applications-no-access-checking
          {:organization            "091-YA"
           :state                   :submitted
           :integrationKeys.ALLU.id nil}) => [{:organization "091-YA"
                                               :permitType   (rand-nth ["A" "YA"])}]
        (lupapalvelu.backing-system.allu.core/submit-application! anything)
        => (throw (ex-info "This is fine." {}))))))
