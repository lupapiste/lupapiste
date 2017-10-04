(ns lupapalvelu.batchrun-test
  (:require [midje.sweet :refer :all]
            [clojure.core.async :as async]
            [midje.util :refer [testable-privates]]
            [taoensso.timbre :refer [debug]]
            [lupapalvelu.krysp-test-util :as krysp-test-util]
            [lupapalvelu.batchrun :as batchrun]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.krysp.application-from-krysp :as krysp-fetch]
            [sade.util :as util]))

(testable-privates lupapalvelu.batchrun fetch-reviews-for-organization fetch-reviews-for-organization-permit-type)

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
              => irrelevant :times 1)

    (provided (#'lupapalvelu.batchrun/mark-reviews-faulty-for-application
               {:id "LP-ORG-2000-00001" :permitType "R"} {:result "read result"})
              => irrelevant :times 1)

    (provided (#'lupapalvelu.logging/log-event :info {:run-by "Automatic review checking"
                                                      :event "Review checking finished for organization"
                                                      :organization-id "org-id"
                                                      :application-count 1
                                                      :faulty-tasks {"LP-ORG-2000-00001" nil}
                                                      :not-saved []})
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
              => irrelevant :times 1)

    (provided (#'lupapalvelu.batchrun/save-reviews-for-application
               ..test-user.. {:id "LP-ORG-2000-00003" :permitType "R"} {:new-faulty-tasks ..faulty-tasks..})
              => irrelevant :times 1)

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
                                                                     "LP-ORG-2000-00003" ..faulty-tasks..}
                                                      :not-saved []})
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
                                                      :faulty-tasks {}
                                                      :not-saved []})
              => ..test-result.. :times 1))

  (fact "application changed while reading reviews - changes not saved"
    (fetch-reviews-for-organization ..test-user.. 1 {:id "org-id" :krysp {:R {:url "url"}}} [:R] nil {})
    => ..test-result..


    (provided (#'lupapalvelu.batchrun/organization-applications-for-review-fetching "org-id" :R anything)
              => [{:id "LP-ORG-2000-00001" :permitType "R" :modified 0}])


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
              => 0)

    (provided (#'lupapalvelu.logging/log-event :info {:run-by "Automatic review checking"
                                                      :event "Review checking finished for organization"
                                                      :organization-id "org-id"
                                                      :application-count 1
                                                      :faulty-tasks {"LP-ORG-2000-00001" nil}
                                                      :not-saved ["LP-ORG-2000-00001"]})
              => ..test-result.. :times 1)))
