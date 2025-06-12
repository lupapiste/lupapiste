(ns lupapalvelu.application-search-elastic-test
  (:require [clojure.set :as set]
            [clojure.test :refer :all]
            [lupapalvelu.application-search-elastic :as search-es]
            [sade.date :as date]
            [sade.env :as env]
            [sade.util :as util]))

(def user {:email     "sonja.sibbo@sipoo.fi"
           :firstName "Sonja"
           :id        "777777777777777777000023"
           :lastName  "Sibbo"
           :orgAuthz  {:753-R #{:authority}}
           :role      "authority"
           :username  "sonja"})

(def source-es-documents
  [{:_id     "LP-753-2025-00001"
    :_source {:_attachment_indicator_reset 1731638539595
              :_comments-seen-by           [{:key "user-id-3" :value 1741596212853}
                                            {:key "user-id-5" :value 1741155674642}
                                            {:key "user-id-2" :value 1741600060021}
                                            {:key "user-id-1" :value 1741595602516}
                                            {:key "user-id-6" :value 1741264740407}
                                            {:key "user-id-4" :value 1741938539595}]
              :_statements-seen-by         [{:key "user-id-2" :value 1741600060021}
                                            {:key "user-id-4" :value 1741938539595}]
              :_stats                      {:processing {:status   1 :complexity 3
                                                         :used     1 :remaining  60
                                                         :delays   3 :requests   0
                                                         :deadline 1715864400000}}
              :_verdicts-seen-by           [{:key "user-id-2" :value 1741778606224}
                                            {:key "user-id-4" :value 1741938539595}
                                            {:key "user-id-1" :value 1741784725993}
                                            {:key "user-id-7" :value 1741866814256}]
              :address                     "Testikatu 4-6"
              :applicant                   "Testihakija"
              :authorityNotice             "Authority notices!!"
              :attachments                 [{:approvals     [{:key "file-id-1" :value {:state "ok"}}
                                                             {:key "file-id-2" :value {:state "requires_authority_action"}}]
                                             :auth          [{:id "user-id-1"} {:id "user-id-2"}]
                                             :id            "attachment-id-1"
                                             :latestVersion {:created        1741778706603
                                                             :fileId         "file-id-2"
                                                             :originalFileId "file-id-2"
                                                             :stamped        true
                                                             :user           {:id "user-id-2" :role "authority"}}
                                             :metadata      {:julkisuusluokka "julkinen" :nakyvyys "julkinen"}
                                             :versions      [{:created        1739792896601
                                                              :fileId         "file-id-3"
                                                              :originalFileId "file-id-1"
                                                              :user           {:id "user-id-1" :role "applicant"}}
                                                             {:created        1741778706603
                                                              :fileId         "file-id-2"
                                                              :originalFileId "file-id-2"
                                                              :stamped        true
                                                              :user           {:id "user-id-2" :role "authority"}}]}]
              :auth                        [{:id "user-id-1" :role "writer"} {:id "user-id-2" :role "writer"} {:id "user-id-3" :role "writer"}]
              :comments                    [{:created 1739792847001
                                             :roles   ["applicant" "authority"]
                                             :target  {:id "attachment-id-1" :type "attachment"}
                                             :user    {:id "user-id-1"}}
                                            {:created 1741162693135
                                             :roles   ["applicant" "authority"]
                                             :target  {:type "application"}
                                             :text    "Kommenttiteksti"
                                             :user    {:id "user-id-2"}}]
              :created                     1739789888092
              :creator                     {:firstName "Testi" :lastName "Testinen"}
              :doc-version                 {:major 2 :minor 0}
              :foreman                     "Taneli Työnjohtaja"
              :foremanRole                 "KVV-työnjohtaja"
              :id                          "LP-753-2025-00001"
              :indexed-ts                  1751615560785
              :indicator-data              {:document-modifications 4}
              :infoRequest                 false
              :kind                        "applications.application"
              :location                    {:x 404369.304 :y 6693806.957}
              :location-wgs84              [24.93306 60.16859]
              :modified                    1741785491825
              :municipality                "753"
              :organization                "753-R"
              :pate-verdicts               [{:category  "r"
                                             :published {:published (date/timestamp "10.3.2025 15.00")}}]
              :permitType                  "R"
              :primaryOperation            {:created 1741346806671 :id "67cad7f6a0fdae0037403fc3" :name "perus-tai-kant-rak-muutos"}
              :propertyId                  "0000000000000001"
              :state                       "verdictGiven"
              :submitted                   1741078264867 ;; 4.3.2025 10.51
              :tags                        ["63ece17e4a79f4077a703e46"]
              :urgency                     "normal"
              :verdictDate                 1741730400000 ;; "12.3.2025 0.00"
              :verdicts                    [{:draft true :id "verdict-id-1"}]}}])

(deftest source-document-fields
  (testing "Source documents contain all necessary fields"
    (is (= (set/difference
             (->> search-es/projection (map (comp first util/split-kw-path)) set)
             (->> source-es-documents
                  (map (comp set keys :_source))
                  (apply set/union)))
           ;; TODO: add source-es-documents containing fields below
           #{:archived :permitSubtype :kuntalupatunnus :handlers :statements}))))

(deftest search-result-xf-test
  (let [result-doc {:address                     "Testikatu 4-6"
                    :applicant                   "Testihakija"
                    :attachmentsRequiringAction  1
                    :authorityNotice             "Authority notices!!"
                    :creator                     {:firstName "Testi"
                                                  :lastName  "Testinen"}
                    :documentModifications       4
                    :documentModificationsPerDoc nil
                    :foreman                     "Taneli Työnjohtaja"
                    :foremanRole                 "KVV-työnjohtaja"
                    :fullyArchived               false
                    :id                          "LP-753-2025-00001"
                    :indicators                  4
                    :infoRequest                 false
                    :kind                        "applications.application"
                    :location                    {:x 404369.304 :y 6693806.957}
                    :modified                    1741785491825
                    :municipality                "753"
                    :organization                "753-R"
                    :permitType                  "R"
                    :primaryOperation            {:id      "67cad7f6a0fdae0037403fc3"
                                                  :name    "perus-tai-kant-rak-muutos"
                                                  :created 1741346806671}
                    :state                       "verdictGiven"
                    :statementsSummary           nil
                    :submitted                   1741078264867
                    :unseenComments              1
                    :unseenStatements            0
                    :unseenVerdicts              0
                    :urgency                     "normal"
                    :verdictDate                 1741730400000}]
    (testing "Example of elastic applications search result post processing (xf)"
      ;; NOTE! This is by no means a comprehensive test but merely an example of what the post-processing does
      (env/with-feature-value :statistics-processing true
        (is (= [result-doc]
               (into [] (search-es/make-xf user {}) source-es-documents))
            "No :processing")
        (is (= [(assoc result-doc :processing {:status   :running :complexity :large
                                               :used     1        :remaining  60
                                               :delays   3        :requests   0
                                               :deadline 1715864400000})]
               (into [] (search-es/make-xf user {:applicationType "processing"}) source-es-documents)))))))
