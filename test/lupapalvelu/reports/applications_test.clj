(ns lupapalvelu.reports.applications-test
  (:require [lupapalvelu.organization :as organization]
            [lupapalvelu.reports.applications :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(testable-privates lupapalvelu.reports.applications
                   row-data get-latest-verdict-ts post-verdict-applications applicants
                   applicants-emails)

(def application {:id                  "LP-753-2017-00001"
                  :title               "Application title"
                  :created             1508326547449
                  :documents           [{:schema-info {:subtype "hakija" :version 1}
                                         :data        {:_selected {:value "henkilo"}
                                                       :henkilo   {:henkilotiedot {:etunimi  {:value "Firstname"}
                                                                                   :sukunimi {:value "Lastname"}}
                                                                   :yhteystiedot {:email {:value "firstname.lastname@example.com"}}}}
                                         :meta {:_approved {:value "rejected"}}}
                                        {:schema-info {:op {:id "59dcb8ebcfd3a952669de178" :name "kerrostalo-rivitalo"}
                                                       :name "uusiRakennus"}
                                         :data {:tunnus {:value "123456"}
                                                :valtakunnallinenNumero {:value "ABCDEF"}
                                                :kaytto {:kayttotarkoitus {:value "021 rivitalot"}
                                                         :rakennusluokka {:value "0110 omakotitalot"}}}
                                         :meta {:_approved {:value "rejected"}}}]
                  :infoRequest         false
                  :linkPermitData      [{:id "LP-638-2013-00099" :type "lupapistetunnus"} {:id "kuntalupa-123" :type "kuntalupatunnus"}]
                  :location            [428195.77099609 6686701.3931274]
                  :history             [{:state "draft"
                                         :ts 1508316461369}
                                        {:state "inUse"
                                         :ts 1508326561369}]
                  :modified            1508326561369
                  :municipality        "753"
                  :primaryOperation    {:id "59dcb8ebcfd3a952669de178" :name "kerrostalo-rivitalo" :created 1508326561369}
                  :secondaryOperations []
                  :openInfoRequest     false
                  :opened              1508326547449
                  :organization        "753-R"
                  :permitType          "R"
                  :propertyId          "63844900010004"
                  :schema-version      1
                  :state               "inUse"
                  :submitted           1508326561369
                  :attachments        [{:applicationState "constructionStarted"
                                        :latestVersion {:version {:major 0 :minor 1}
                                                        :originalFileId "59e0b1a6b7fd3e8ec3e26a48"
                                                        :fileId "59e0b1a6b7fd3e8ec3e26a48"
                                                        :created 1508326561369}
                                        :approvals {:59e0b1a6b7fd3e8ec3e26a48 {:state "requires_authority_action"}}
                                        :versions [{:fileId "59e0b1a6b7fd3e8ec3e26a48"
                                                    :originalFileId "59e0b1a6b7fd3e8ec3e26a48"}]}
                                       {:applicationState "constructionStarted"}
                                       {:applicationState "draft"
                                        :latestVersion {:version {:major 0 :minor 1}
                                                        :originalFileId "59e0b1a6b7fd3e8ec3e26a48"
                                                        :fileId "59e0b1a6b7fd3e8ec3e26a48"
                                                        :created 1508326561369}
                                        :versions [{:fileId "59e0b1a6b7fd3e8ec3e26a48"
                                                    :originalFileId "59e0b1a6b7fd3e8ec3e26a48"}]
                                        :approvals {:59e0b1a6b7fd3e8ec3e26a48 {:state "requires_authority_action"}}}]
                  :tasks              [{:schema-info {:name "task-katselmus"}
                                        :taskname "Aloitus"}
                                       {:schema-info {:name "task-katselmus"}
                                        :taskname "Loppukatselmus"
                                        :data {:katselmus {:pitoPvm {:value "19.10.2017"}}}}]})

(def digi-report-rows [{:date "10.12.2017" :id "LP-753-2017-00001" :attachments 5}
                       {:date "10.12.2017" :id "LP-753-2017-00002" :attachments 7}
                       {:date "12.12.2017" :id "LP-753-2017-00003" :attachments 15}
                       {:date "14.12.2017" :id "LP-753-2017-00004" :attachments 3}
                       {:date "19.12.2017" :id "LP-753-2017-00005" :attachments 12}
                       {:date "19.12.2017" :id "LP-753-2017-00006" :attachments 6}])

(facts "Company applications"
  (against-background [(organization/get-organization "753-R") => {:name {:fi "Sipoon rakennusvalvonta"}}])

  (fact "Row data is composed from application"
    (row-data application (:primaryOperation application) :fi {:role :authority}) =>   {:building-id                   "123456 - ABCDEF"
                                                                                        :operation                     "Asuinkerrostalon tai rivitalon rakentaminen"
                                                                                        :usage                         "021 rivitalot"
                                                                                        :building-type                 "0110 omakotitalot"
                                                                                        :required-actions              2
                                                                                        :attachment-required-actions   2
                                                                                        :inuse-date                    "18.10.2017"
                                                                                        :final-review-date             "19.10.2017"
                                                                                        :reviews-count                 2
                                                                                        :id                            "LP-753-2017-00001"
                                                                                        :title                         "Application title"
                                                                                        :state                         "K\u00e4ytt\u00f6\u00f6notettu"
                                                                                        :organization                  "Sipoon rakennusvalvonta"
                                                                                        :applicant                     "Firstname Lastname"
                                                                                        :attachments-count             3
                                                                                        :pre-verdict-attachments       1
                                                                                        :post-verdict-attachments      2
                                                                                        :permit-type                   "R"
                                                                                        :propertyId                    "638-449-1-4"})

  (fact "Every operation should have own data row"
    (let [secondary-operations [{:id "59dcb8ebcfd3a952669de177" :name "kerrostalo-rivitalo" :created 1508327561369}
                                {:id "59dcb8ebcfd3a952669de179" :name "kerrostalo-rivitalo" :created 1508328561369}]
          application-with-multiple-operations (assoc application :secondaryOperations secondary-operations)]
      (count (report-data-by-operations [application-with-multiple-operations] :fi {:role :authority})) => 3)))

(facts "Digitized applications"

  (fact "Row data is composed from application"
    (digi-report-data application) => {:date "18.10.2017"
                                       :id "LP-753-2017-00001"
                                       :attachments 3})

  (fact "Sum data is composed from row data"
    (digi-report-sum digi-report-rows) => [{:date "10.12.2017" :attachments 12}
                                           {:date "12.12.2017" :attachments 15}
                                           {:date "14.12.2017" :attachments 3}
                                           {:date "19.12.2017" :attachments 18}]))

(facts "Post verdict applications"
  (let [updated-app (update application :documents conj {:schema-info {:subtype "hakija" :version 1}
                                                        :data {:_selected {:value "yritys"}
                                                               :yritys {:yritysnimi {:value "Firma"}
                                                                        :yhteyshenkilo {:yhteystiedot {:email {:value "anders.anderson@firma.com"}}}}}})]

    (fact "Applicants"
      (applicants updated-app) => "Firstname Lastname; Firma")

    (fact "Applicant emails"
      (applicants-emails updated-app) => "firstname.lastname@example.com; anders.anderson@firma.com")))
