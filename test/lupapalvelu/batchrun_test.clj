(ns lupapalvelu.batchrun-test
  "Tests rely heavily on 'provided' beacause of non-functional nature (lots of side effects) of batchrun processes."
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [taoensso.timbre :refer [debug]]
            [lupapalvelu.batchrun :as batchrun]))

(testable-privates lupapalvelu.batchrun fetch-reviews-for-organization fetch-reviews-for-organization-permit-type
                   organization-has-krysp-url-function get-valid-applications)

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
    (fetch-reviews-for-organization-permit-type {:id "test-user"} {:id "org-id"} :R [{:id "LP-ORG-2000-00001"}])
    => [[{:id "LP-ORG-2000-00001"} "xml1"]]

    (provided (#'lupapalvelu.xml.krysp.application-from-krysp/get-application-xmls
               {:id "org-id"} :R :application-id ["LP-ORG-2000-00001"])
              => {"LP-ORG-2000-00001" "xml1"}))

  (fact "fetch two applications in a chunk"
    (fetch-reviews-for-organization-permit-type {:id "test-user"} {:id "org-id"} :R [{:id "LP-ORG-2000-00001"}
                                                                                     {:id "LP-ORG-2000-00002"}])
    => [[{:id "LP-ORG-2000-00001"} "xml1"]
        [{:id "LP-ORG-2000-00002"} "xml2"]]

    (provided (#'lupapalvelu.xml.krysp.application-from-krysp/get-application-xmls
               {:id "org-id"} :R :application-id ["LP-ORG-2000-00001" "LP-ORG-2000-00002"])
              => {"LP-ORG-2000-00001" "xml1"
                  "LP-ORG-2000-00002" "xml2"}))

  (let [organization {:id "org-id" :krysp {:R {:fetch-chunk-size 2}}}]
    (fact "fetch multiple application in two chunks"
      (fetch-reviews-for-organization-permit-type {:id "test-user"}  organization :R
                                                  [{:id "LP-ORG-2000-00001"}
                                                   {:id "LP-ORG-2000-00002"}
                                                   {:id "LP-ORG-2000-00003"}])
      => [[{:id "LP-ORG-2000-00001"} "xml1"]
          [{:id "LP-ORG-2000-00002"} "xml2"]
          [{:id "LP-ORG-2000-00003"} "xml3"]]

      (provided (#'lupapalvelu.xml.krysp.application-from-krysp/get-application-xmls
                 organization :R :application-id ["LP-ORG-2000-00001" "LP-ORG-2000-00002"])
                => {"LP-ORG-2000-00001" "xml1"
                    "LP-ORG-2000-00002" "xml2"})

      (provided (#'lupapalvelu.xml.krysp.application-from-krysp/get-application-xmls
                 organization :R :application-id ["LP-ORG-2000-00003"])
                => {"LP-ORG-2000-00003" "xml3"})))

  (let [organization {:id "org-id" :krysp {:R {:fetch-chunk-size 2}}}]
    (fact "fetching missing xmls is retried with backend id"
      (fetch-reviews-for-organization-permit-type {:id "test-user"}  organization :R
                                                  [{:id "LP-ORG-2000-00001"}
                                                   {:id "LP-ORG-2000-00002" :verdicts [{:kuntalupatunnus "bck-id-02"}]}
                                                   {:id "LP-ORG-2000-00003" :verdicts [{:kuntalupatunnus "bck-id-03"}]}
                                                   {:id "LP-ORG-2000-00004" :verdicts [{} {:kuntalupatunnus "bck-id-04"}]}])
      => [[{:id "LP-ORG-2000-00001"} "xml1"]
          [{:id "LP-ORG-2000-00002" :kuntalupatunnus "bck-id-02" :verdicts [{:kuntalupatunnus "bck-id-02"}]} "xml2"]
          [{:id "LP-ORG-2000-00004" :kuntalupatunnus "bck-id-04" :verdicts [{} {:kuntalupatunnus "bck-id-04"}]} "xml4"]]

      ;; First chunk with app-id
      (provided (#'lupapalvelu.xml.krysp.application-from-krysp/get-application-xmls
                 organization :R :application-id ["LP-ORG-2000-00001" "LP-ORG-2000-00002"])
                => {"LP-ORG-2000-00001" "xml1"})

      ;; Second chunk with app-id
      (provided (#'lupapalvelu.xml.krysp.application-from-krysp/get-application-xmls
                 organization :R :application-id ["LP-ORG-2000-00003" "LP-ORG-2000-00004"])
                => {})

      ;; First chunk with back-end-id
      (provided (#'lupapalvelu.xml.krysp.application-from-krysp/get-application-xmls
                 organization :R :kuntalupatunnus ["bck-id-02" "bck-id-03"])
                => {"bck-id-02" "xml2"})

      ;; Second chunk with back-end-id
      (provided (#'lupapalvelu.xml.krysp.application-from-krysp/get-application-xmls
                 organization :R :kuntalupatunnus ["bck-id-04"])
                => {"bck-id-04" "xml4"}))))

(facts fetch-reviews-for-organization
  (fact "fetch single application"
    (fetch-reviews-for-organization ..test-user.. 1 {:id "org-id"} [:R] [{:id "LP-ORG-2000-00001" :permitType "R"}] {})
    => ..test-result..

    (provided (#'lupapalvelu.batchrun/organization-applications-for-review-fetching "org-id" "R" anything "LP-ORG-2000-00001")
              => [{:id "LP-ORG-2000-00001" :permitType "R"}])

    (provided (#'lupapalvelu.batchrun/fetch-reviews-for-organization-permit-type
               ..test-user.. {:id "org-id"} "R" [{:id "LP-ORG-2000-00001" :permitType "R"}])
              => [[{:id "LP-ORG-2000-00001" :permitType "R"} ..xml1..]] :times 1)

    (provided (#'lupapalvelu.batchrun/read-reviews-for-application
               ..test-user.. 1 {:id "LP-ORG-2000-00001" :permitType "R"} ..xml1.. nil)
              => {:result "read result"} :times 1)

    (provided (#'lupapalvelu.batchrun/save-reviews-for-application
               ..test-user.. {:id "LP-ORG-2000-00001" :permitType "R"} {:result "read result"})
              => {:ok true} :times 1)

    (provided (#'lupapalvelu.batchrun/mark-reviews-faulty-for-application
               {:id "LP-ORG-2000-00001" :permitType "R"} {:result "read result"})
              => irrelevant :times 1)

    (provided (#'lupapalvelu.logging/log-event :info {:run-by "Automatic review checking"
                                                      :event "Review checking finished for organization"
                                                      :organization-id "org-id"
                                                      :application-count 1
                                                      :faulty-tasks {"LP-ORG-2000-00001" nil}})
              => ..test-result.. :times 1))

  (fact "fetch all organization applications - one dropped in fetch"
    (fetch-reviews-for-organization ..test-user.. 1 {:id "org-id" :krysp {:R {:url "url"}}} [:R] nil {})
    => ..test-result..


    (provided (#'lupapalvelu.batchrun/organization-applications-for-review-fetching "org-id" :R anything)
              => [{:id "LP-ORG-2000-00001" :permitType "R"}
                  {:id "LP-ORG-2000-00002" :permitType "R"}
                  {:id "LP-ORG-2000-00003" :permitType "R"}])

    (provided (#'lupapalvelu.batchrun/fetch-reviews-for-organization-permit-type
               ..test-user.. {:id "org-id" :krysp {:R {:url "url"}}} :R [{:id "LP-ORG-2000-00001" :permitType "R"}
                                                                         {:id "LP-ORG-2000-00002" :permitType "R"}
                                                                         {:id "LP-ORG-2000-00003" :permitType "R"}])
              => [[{:id "LP-ORG-2000-00001" :permitType "R"} ..xml1..]
                  [{:id "LP-ORG-2000-00003" :permitType "R"} ..xml3..]] :times 1)

    (provided (#'lupapalvelu.batchrun/organization-applications-for-review-fetching "org-id" "R" anything "LP-ORG-2000-00001")
              => [{:id "LP-ORG-2000-00001" :permitType "R"}])

    (provided (#'lupapalvelu.batchrun/organization-applications-for-review-fetching "org-id" "R" anything "LP-ORG-2000-00003")
              => [{:id "LP-ORG-2000-00003" :permitType "R"}])

    (provided (#'lupapalvelu.batchrun/read-reviews-for-application
               ..test-user.. 1 {:id "LP-ORG-2000-00001" :permitType "R"} ..xml1.. nil)
              => {:result "read result"} :times 1)

    (provided (#'lupapalvelu.batchrun/read-reviews-for-application
               ..test-user.. 1 {:id "LP-ORG-2000-00003" :permitType "R"} ..xml3.. nil)
              => {:new-faulty-tasks ..faulty-tasks..} :times 1)

    (provided (#'lupapalvelu.batchrun/save-reviews-for-application
               ..test-user.. {:id "LP-ORG-2000-00001" :permitType "R"} {:result "read result"})
              => {:ok true} :times 1)

    (provided (#'lupapalvelu.batchrun/save-reviews-for-application
               ..test-user.. {:id "LP-ORG-2000-00003" :permitType "R"} {:new-faulty-tasks ..faulty-tasks..})
              => {:ok true} :times 1)

    (provided (#'lupapalvelu.batchrun/mark-reviews-faulty-for-application
               {:id "LP-ORG-2000-00001" :permitType "R"} {:result "read result"})
              => irrelevant :times 1)

    (provided (#'lupapalvelu.batchrun/mark-reviews-faulty-for-application
               {:id "LP-ORG-2000-00003" :permitType "R"} {:new-faulty-tasks ..faulty-tasks..})
              => irrelevant :times 1)

    (provided (#'lupapalvelu.logging/log-event :info {:run-by "Automatic review checking"
                                                      :event "Review checking finished for organization"
                                                      :organization-id "org-id"
                                                      :application-count 2
                                                      :faulty-tasks {"LP-ORG-2000-00001" nil
                                                                     "LP-ORG-2000-00003" ..faulty-tasks..}})
              => ..test-result.. :times 1))

  (fact "application becomes irrelevant during review fetch - nothing is done"
    (fetch-reviews-for-organization ..test-user.. 1 {:id "org-id" :krysp {:R {:url "url"}}} [:R] nil {})
    => ..test-result..


    (provided (#'lupapalvelu.batchrun/organization-applications-for-review-fetching "org-id" :R anything)
              => [{:id "LP-ORG-2000-00001" :permitType "R"}])


    (provided (#'lupapalvelu.batchrun/fetch-reviews-for-organization-permit-type
               ..test-user.. {:id "org-id" :krysp {:R {:url "url"}}} :R [{:id "LP-ORG-2000-00001" :permitType "R"}])
              => [[{:id "LP-ORG-2000-00001" :permitType "R"} ..xml1..]] :times 1)

    (provided (#'lupapalvelu.batchrun/organization-applications-for-review-fetching "org-id" "R" anything "LP-ORG-2000-00001")
              => [])

    (provided (#'lupapalvelu.logging/log-event :info {:run-by "Automatic review checking"
                                                      :event "Review checking finished for organization"
                                                      :organization-id "org-id"
                                                      :application-count 0
                                                      :faulty-tasks {}})
              => ..test-result.. :times 1))

  (fact "application changed while reading reviews - changes not saved"
    (fetch-reviews-for-organization ..test-user.. 1 {:id "org-id" :krysp {:R {:url "url"}}} [:R] nil {})
    => ..test-result..


    (provided (#'lupapalvelu.batchrun/organization-applications-for-review-fetching "org-id" :R anything)
              => [{:id "LP-ORG-2000-00001" :permitType "R" :modified 0}])

    (provided (#'lupapalvelu.organization/get-organization anything) => {:only-use-inspection-from-backend true})

    (provided (#'lupapalvelu.batchrun/fetch-reviews-for-organization-permit-type
               ..test-user.. {:id "org-id" :krysp {:R {:url "url"}}} :R [{:id "LP-ORG-2000-00001" :permitType "R" :modified 0}])
              => [[{:id "LP-ORG-2000-00001" :permitType "R"} ..xml1..]] :times 1)

    (provided (#'lupapalvelu.batchrun/organization-applications-for-review-fetching "org-id" "R" anything "LP-ORG-2000-00001")
              => [{:id "LP-ORG-2000-00001" :permitType "R" :modified 1}])

    (provided (#'lupapalvelu.batchrun/read-reviews-for-application
               ..test-user.. 1 {:id "LP-ORG-2000-00001" :permitType "R" :modified 1} ..xml1.. nil)
              => {:ok true :updates ..updates..} :times 1)

    (provided (#'lupapalvelu.action/update-application anything {:modified 1} ..updates.. :return-count? true)
              => 0)

    (provided (#'lupapalvelu.domain/get-application-no-access-checking "LP-ORG-2000-00001")
              => {:modified 123} :times 1)

    (provided (#'lupapalvelu.logging/log-event :info {:run-by "Automatic review checking"
                                                      :event  "Failed to save review updates for application"
                                                      :reason "Application modified does not match (was: 1, now: 123)"
                                                      :application-id "LP-ORG-2000-00001"
                                                      :result {:ok true :updates ..updates..}})
              => irrelevant :times 1)

    (provided (#'lupapalvelu.logging/log-event :info {:run-by "Automatic review checking"
                                                      :event "Review checking finished for organization"
                                                      :organization-id "org-id"
                                                      :application-count 0
                                                      :faulty-tasks {}})
              => ..test-result.. :times 1)))

(defmethod lupapalvelu.xml.krysp.common-reader/get-tunnus-xml-path :TEST [& _]
  [:tunnus])

(def result (atom []))

(defmethod lupapalvelu.permit/fetch-xml-from-krysp :TEST [permit-type url creds ids st & _]
  (Thread/sleep (+ (* (count ids) 30) 1))
  (swap! result conj ids)
  (->> (map (fn [id] {:tag :app-xml :content [{:tag :tunnus :content [id]}]}) ids)
       (hash-map :tag :xml :content)))

(facts poll-verdicst-for-reviews
  (fact "single application"
    (batchrun/poll-verdicts-for-reviews :application-ids ["LP-ORG-2000-00001"]) => nil

    (provided (#'lupapalvelu.mongo/select :applications {:_id {"$in" ["LP-ORG-2000-00001"]}})
              => [{:id "LP-ORG-2000-00001" :permitType "TEST" :organization "org-id"}])

    (provided (#'lupapalvelu.batchrun/orgs-for-review-fetch "org-id")
              => [{:id "org-id" :krysp {:TEST {:url "url"}}}])

    (provided (lupapalvelu.user/batchrun-user ["org-id"])
              => ..test-user..)

    (provided (#'lupapalvelu.batchrun/organization-applications-for-review-fetching "org-id" "TEST" anything "LP-ORG-2000-00001")
              => [{:id "LP-ORG-2000-00001" :permitType "TEST"}])

    (provided (#'lupapalvelu.batchrun/read-reviews-for-application
               ..test-user.. anything {:id "LP-ORG-2000-00001" :permitType "TEST"} anything nil)
              => {:result "read result"} :times 1)

    (provided (#'lupapalvelu.batchrun/save-reviews-for-application
               ..test-user.. {:id "LP-ORG-2000-00001" :permitType "TEST"} {:result "read result"})
              => {:ok true} :times 1)

    (provided (#'lupapalvelu.batchrun/mark-reviews-faulty-for-application
               {:id "LP-ORG-2000-00001" :permitType "TEST"} {:result "read result"})
              => irrelevant :times 1)

    (provided (#'lupapalvelu.logging/log-event :info {:run-by "Automatic review checking"
                                                      :event "Start fetching xmls"
                                                      :organization-id "org-id"
                                                      :application-count 1
                                                      :applications ["LP-ORG-2000-00001"]})
              => ..test-result.. :times 1)

    (provided (#'lupapalvelu.logging/log-event :info {:run-by "Automatic review checking"
                                                      :event "Review checking finished for organization"
                                                      :organization-id "org-id"
                                                      :application-count 1
                                                      :faulty-tasks {"LP-ORG-2000-00001" nil}})
              => ..test-result.. :times 1))

  (fact "multiple applications"
    (reset! result [])

    (fact
        (batchrun/poll-verdicts-for-reviews :application-ids ["lots-of-applications-ids"]) => nil

        (provided (#'lupapalvelu.mongo/select :applications {:_id {"$in" ["lots-of-applications-ids"]}})
                  => [{:id "LP-ORA-2000-00001" :permitType "TEST" :organization "org-id"}
                      {:id "LP-ORB-2000-00001" :permitType "TEST" :organization "org-id2"}
                      {:id "LP-ORB-2000-00002" :permitType "TEST" :organization "org-id2"}
                      {:id "LP-ORA-2000-00001" :permitType "TEST" :organization "org-id"}
                      {:id "LP-ORB-2000-00001" :permitType "TEST" :organization "org-id2"}
                      {:id "LP-ORB-2000-00002" :permitType "TEST" :organization "org-id2"}
                      {:id "LP-ORA-2000-00001" :permitType "TEST" :organization "org-id"}
                      {:id "LP-ORA-2000-00001" :permitType "TEST" :organization "org-id"}
                      {:id "LP-ORA-2000-00001" :permitType "TEST" :organization "org-id"}
                      {:id "LP-ORA-2000-00001" :permitType "TEST" :organization "org-id"}])

        (provided (#'lupapalvelu.batchrun/orgs-for-review-fetch "org-id" "org-id2")
                  => [{:id "org-id" :krysp {:TEST {:url "url"  :fetch-chunk-size 1}}}
                      {:id "org-id2" :krysp {:TEST {:url "url"  :fetch-chunk-size 2}}}])

        (provided (lupapalvelu.user/batchrun-user ["org-id" "org-id2"])
                  => ..test-user..)

        (provided (#'lupapalvelu.batchrun/organization-applications-for-review-fetching anything "TEST" anything "LP-ORA-2000-00001")
                  => [{:id "LP-ORA-2000-00001" :permitType "TEST"}])

        (provided (#'lupapalvelu.batchrun/organization-applications-for-review-fetching anything "TEST" anything "LP-ORB-2000-00001")
                  => [{:id "LP-ORB-2000-00001" :permitType "TEST"}])

        (provided (#'lupapalvelu.batchrun/organization-applications-for-review-fetching anything "TEST" anything "LP-ORB-2000-00002")
                  => [{:id "LP-ORB-2000-00002" :permitType "TEST"}])

        (provided (#'lupapalvelu.batchrun/read-reviews-for-application
                   ..test-user.. anything anything anything nil)
                  => {:result "read result"} :times 10)

        (provided (#'lupapalvelu.batchrun/save-reviews-for-application
                   ..test-user.. anything {:result "read result"})
                  => {:ok true} :times 10)


        (provided (#'lupapalvelu.batchrun/mark-reviews-faulty-for-application
                   anything {:result "read result"})
                  => irrelevant :times 10)

        (provided (#'lupapalvelu.logging/log-event :info {:run-by "Automatic review checking"
                                                          :event "Start fetching xmls"
                                                          :organization-id "org-id"
                                                          :application-count 6
                                                          :applications ["LP-ORA-2000-00001"
                                                                         "LP-ORA-2000-00001"
                                                                         "LP-ORA-2000-00001"
                                                                         "LP-ORA-2000-00001"
                                                                         "LP-ORA-2000-00001"
                                                                         "LP-ORA-2000-00001"]})
                  => ..test-result.. :times 1)

        (provided (#'lupapalvelu.logging/log-event :info {:run-by "Automatic review checking"
                                                          :event "Start fetching xmls"
                                                          :organization-id "org-id2"
                                                          :application-count 4
                                                          :applications ["LP-ORB-2000-00001"
                                                                         "LP-ORB-2000-00002"
                                                                         "LP-ORB-2000-00001"
                                                                         "LP-ORB-2000-00002"]})
                  => ..test-result.. :times 1)

        (provided (#'lupapalvelu.logging/log-event :info {:run-by "Automatic review checking"
                                                          :event "Review checking finished for organization"
                                                          :organization-id "org-id"
                                                          :application-count 6
                                                          :faulty-tasks {"LP-ORA-2000-00001" nil}})
                  => ..test-result.. :times 1)

        (provided (#'lupapalvelu.logging/log-event :info {:run-by "Automatic review checking"
                                                          :event "Review checking finished for organization"
                                                          :organization-id "org-id2"
                                                          :application-count 4
                                                          :faulty-tasks {"LP-ORB-2000-00001" nil
                                                                         "LP-ORB-2000-00002" nil}})
                  => ..test-result.. :times 1))

    #_(fact "result - sometimes fails on timing issue"
      @result => [["LP-ORA-2000-00001"] ["LP-ORB-2000-00001" "LP-ORB-2000-00002"] ["LP-ORA-2000-00001"] ["LP-ORA-2000-00001"] ["LP-ORB-2000-00001" "LP-ORB-2000-00002"] ["LP-ORA-2000-00001"] ["LP-ORA-2000-00001"] ["LP-ORA-2000-00001"]])))
