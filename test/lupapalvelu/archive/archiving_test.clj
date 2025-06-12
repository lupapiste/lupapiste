(ns lupapalvelu.archive.archiving-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.archive.archiving :as archiving]
            [lupapalvelu.attachment :as at]
            [lupapalvelu.foreman-application-util :as foreman-app-util]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.tiedonohjaus :as tiedonohjaus]
            [matcher-combinators.test :refer [match?]]
            [sade.date :as date]
            [sade.env :as env]
            [schema.core :as sc])
  (:import [java.util Date]))

(set! *warn-on-reflection* true)

(deftest valid-ya-state-test
  (testing "Valid YA application with valid state - should be archived"
    (is (true? (archiving/valid-ya-state? {:id "LP-XXX-2017-00001" :permitType "YA" :state "verdictGiven"})))
    (is (true? (archiving/valid-ya-state? {:id "LP-XXX-2017-00002" :permitType "YA" :state "finished"})))
    (is (true? (archiving/valid-ya-state? {:id "LP-XXX-2017-00003" :permitType "YA" :state "extinct"})))
    (is (true? (archiving/valid-ya-state? {:id "LP-XXX-2017-00004" :permitType "YA" :state "appealed"})))
    (is (true? (archiving/valid-ya-state? {:id "LP-XXX-2017-00005" :permitType "YA" :state "closed"})))
    (is (true? (archiving/valid-ya-state? {:id "LP-XXX-2017-00005" :permitType "YA" :state "agreementSigned"}))))
  (testing "Valid YA application with invalid state - should not be archived"
    (is (false? (archiving/valid-ya-state? {:id "LP-XXX-2017-00006" :permitType "YA" :state "open"})))
    (is (false? (archiving/valid-ya-state? {:id "LP-XXX-2017-00007" :permitType "YA" :state "submitted"})))
    (is (false? (archiving/valid-ya-state? {:id "LP-XXX-2017-00008" :permitType "YA" :state "sent"})))
    (is (false? (archiving/valid-ya-state? {:id "LP-XXX-2017-00009" :permitType "YA" :state "draft"})))
    (is (false? (archiving/valid-ya-state? {:id "LP-XXX-2017-00010" :permitType "YA" :state "complementNeeded"})))
    (is (false? (archiving/valid-ya-state? {:id "LP-XXX-2017-00005" :permitType "YA" :state "agreementPrepared"}))))
  (testing "Valid R application with valid R state - should not be in valid YA state"
    (is (false? (archiving/valid-ya-state? {:id "LP-XXX-2017-00011" :permitType "R" :state "verdictGiven"})))
    (is (false? (archiving/valid-ya-state? {:id "LP-XXX-2017-00012" :permitType "R" :state "closed"})))
    (is (false? (archiving/valid-ya-state? {:id "LP-XXX-2017-00013" :permitType "R" :state "foremanVerdictGiven"}))))
  (testing "Invalid R application with invalid YA state - should not be in valid YA state"
    (is (false? (archiving/valid-ya-state? {:id "LP-XXX-2017-00014" :permitType "R" :state "finished"})))))

(deftest archiving-project-test
  (testing "Should set attachment specific backendId as first permit id"
    (let [verdicts [{:kuntalupatunnus "LX-0001"}
                    {:kuntalupatunnus "LX-0002"}
                    {:kuntalupatunnus "LX-0003"}]]
      (is (= ["LX-0002" "LX-0001" "LX-0003"] (archiving/permit-ids-for-archiving {:verdicts verdicts} {:backendId "LX-0002"} permit/ARK)))
      (is (= ["LX-0001" "LX-0002" "LX-0003"] (archiving/permit-ids-for-archiving {:verdicts verdicts} {:backendId "LX-0002"} permit/R)))))
  (testing "Should use attachment specific verdict for verdict date"
    (let [verdicts [{:kuntalupatunnus "LX-0001" :paatokset [{:poytakirjat [{:paatospvm 1482530400000}]}]}
                    {:kuntalupatunnus "LX-0002" :paatokset [{:poytakirjat [{:paatospvm 1512597600000}]}]}
                    {:kuntalupatunnus "LX-0003" :paatokset [{:poytakirjat [{:paatospvm 1510057163483}]}]}]]
      (is (= 1512597600000 (archiving/get-ark-paatospvm {:verdicts verdicts} {:backendId "LX-0002"}))))))

(def uusi-rakennus-doc
  {:id          "uusi-rakennus"
   :created     2
   :schema-info {:name    "uusiRakennus"
                 :version 1
                 :op      {:name        "kerrostalo-rivitalo"
                           :description "kerrostalo-rivitalo-kuvaus"
                           :id          "kerrostalo-rivitalo-id"}}
   :data        {:tunnus {:value "A"}
                 :kaytto {:kayttotarkoitus {:value "011 yhden asunnon talot"}
                          :rakennusluokka  {:value "0110 omakotitalot"}}}})

(def application {:id              "LP-000-2018-00001"
                  :closed          1539030370458
                  :address         "Street 12"
                  :_applicantIndex ["Applicant 1"]
                  :municipality    "753"
                  :permitType      "R"
                  :submitted       1537030370458
                  :organization    "753-R"
                  :handlers        [{:id        "5bac7b2af4dfa392c827c3a2"
                                     :roleId    "abba1111111111111111acdc"
                                     :userId    "777777777777777777000023"
                                     :firstName "Sonja"
                                     :lastName  "Sibbo"}]
                  :tosFunction     "10 03 00 01"
                  :propertyId      "75347800050140"
                  :drawings        []
                  :documents       [uusi-rakennus-doc]})

(def attachment {:groupType            nil
                 :type                 {:type-id :karttaote :type-group :rakennuspaikka}
                 :op                   nil
                 :auth                 [{:id "777777777777777777000023" :username "sonja" :firstName "Sonja" :lastName "Sibbo" :role "uploader"}]
                 :modified             1538030348972
                 :sent                 1538030378975
                 :requestedByAuthority true
                 :applicationState     :open
                 :readOnly             false
                 :locked               false
                 :id                   "5bac7b0df4dfa392c827c396"
                 :latestVersion        {:storageSystem  :mongodb
                                        :created        1538030348972
                                        :size           130575
                                        :filename       "file.pdf"
                                        :originalFileId "032db7f0-c220-11e8-bace-28134fe06fa5"
                                        :autoConversion true
                                        :contentType    "application/pdf"
                                        :archivable     true
                                        :version        {:major 0 :minor 1}
                                        :stamped        false
                                        :user           {:id        "777777777777777777000023"
                                                         :username  "sonja"
                                                         :firstName "Sonja"
                                                         :lastName  "Sibbo"
                                                         :role      "authority"}
                                        :fileId         "09b1cf30-c220-11e8-bace-28134fe06fa5"}
                 :notNeeded            false
                 :signatures           []
                 :backendId            nil
                 :forPrinting          false
                 :contents             "Karttaote"
                 :target               nil
                 :versions             [{:storageSystem  :mongodb
                                         :created        1538030348972
                                         :size           130575
                                         :filename       "file.pdf"
                                         :originalFileId "032db7f0-c220-11e8-bace-28134fe06fa5"
                                         :autoConversion true
                                         :contentType    "application/pdf"
                                         :archivable     true
                                         :version        {:major 0 :minor 1}
                                         :stamped        false
                                         :user           {:id        "777777777777777777000023"
                                                          :username  "sonja"
                                                          :firstName "Sonja"
                                                          :lastName  "Sibbo"
                                                          :role      "authority"}
                                         :fileId         "09b1cf30-c220-11e8-bace-28134fe06fa5"}]
                 :ramLink              "5acb61e7e7f6046cc2f1991e"
                 :metadata             {:nakyvyys "julkinen" :tila "valmis"}
                 :required             false})

(deftest test-data-valid-test
  (is (nil? (sc/check at/Attachment attachment))))

(deftest archiving-metadata-test
  (with-redefs [foreman-app-util/get-linked-foreman-applications-by-id (constantly nil)
                tiedonohjaus/get-from-toj-api                          (constantly nil)]
    (testing "For application with legacy verdict"
      (let [result (archiving/generate-archive-metadata
                     (assoc application
                            :verdicts [{:kuntalupatunnus "18-0370-R"
                                        :paatokset       [{:paivamaarat {:aloitettava      1632787200000
                                                                         :lainvoimainen    1538092800000
                                                                         :voimassaHetki    1695859200000
                                                                         :anto             1536796800000
                                                                         :viimeinenValitus 1538006400000
                                                                         :julkipano        1536710400000}
                                                           :poytakirjat [{:paatoskoodi     "myönnetty"
                                                                          :paatospvm       1536537600000
                                                                          :pykala          318
                                                                          :paatoksentekija "Viranhaltija"
                                                                          :status          "1"
                                                                          :urlHash         "9d39671760cf07327678a31973c399f907336d9a"}]
                                                           :id          "5b99e1ace7f6041af7cd7dfd"}]
                                        :id              "5b99e1ace7f6041af7cd7dfb"
                                        :timestamp       1536811436294}])
                     {:username "tester" :firstName "Archive" :lastName "Tester"}
                     :metadata
                     {}
                     attachment)]
        (is (match? {:address             "Street 12"
                     :applicants          ["Applicant 1"]
                     :applicationId       "LP-000-2018-00001"
                     :arkistoija          {:firstName "Archive" :lastName "Tester" :username "tester"}
                     :buildingIds         []
                     :contents            "Karttaote"
                     :kayttotarkoitukset  ["011 yhden asunnon talot"]
                     :rakennusluokat      ["0110 omakotitalot"]
                     :kieli               "fi"
                     :kuntalupatunnukset  ["18-0370-R"]
                     :lupapvm             "2018-09-28T03:00:00+03:00"
                     :municipality        "753"
                     :myyntipalvelu       false
                     :nakyvyys            "julkinen"
                     :nationalBuildingIds []
                     :operations          []
                     :organization        "753-R"
                     :paatoksentekija     "Viranhaltija"
                     :paatospvm           "2018-09-10T03:00:00+03:00"
                     :propertyId          "75347800050140"
                     :ramLink             "5acb61e7e7f6046cc2f1991e"
                     :suunnittelijat      []
                     :tiedostonimi        "file.pdf"
                     :tila                "valmis"
                     :versio              "0.1"
                     :jattopvm            "2018-09-15T19:52:50+03:00"
                     :closed              "2018-10-08T23:26:10+03:00"}
                    result))))

    (testing "For application with pate verdict"
      (let [result (archiving/generate-archive-metadata
                     (assoc application
                            :pate-verdicts [{:category       "r"
                                             :schema-version 1
                                             :state          {:_value "published" :_user "sonja" :_modified 1538030437064}
                                             :modified       1538030437064
                                             :id             "5bac7b49f4dfa392c827c3ac"
                                             :archive        {:verdict-date 1538038800000 :lainvoimainen 1538557200000 :anto 1538384400000 :verdict-giver "Sonja Sibbo"}
                                             :published      {:tags          "tags"
                                                              :published     1538030437064
                                                              :attachment-id "5bac7b67f4dfa392c827c3b3"}
                                             :data           {:address                 "Latokuja 10"
                                                              :julkipano               1538125200000
                                                              :verdict-date            1538038800000
                                                              :buildings               {:5bac7af5f4dfa392c827c387 {:show-building true
                                                                                                                   :operation     "pientalo"
                                                                                                                   :description   ""
                                                                                                                   :building-id   ""
                                                                                                                   :tag           ""
                                                                                                                   :order         "0"}}
                                                              :verdict-section         "76"
                                                              :verdict-text            "Päätös annettu"
                                                              :muutoksenhaku           1538470800000
                                                              :anto                    1538384400000
                                                              :attachments             [{:type-group "paatoksenteko" :type-id "paatos" :amount 1}]
                                                              :operation               "Talon rakennus"
                                                              :verdict-code            "annettu-lausunto"
                                                              :language                "fi"
                                                              :foremen-included        true
                                                              :deviations              ""
                                                              :neighbor-states         []
                                                              :lainvoimainen           1538557200000
                                                              :handler                 "Sonja Sibbo"
                                                              :automatic-verdict-dates true
                                                              :statements              []}}])
                     {:username "tester" :firstName "Archive" :lastName "Tester"}
                     :metadata
                     {}
                     attachment)]
        (is (match? {:address             "Street 12"
                     :applicants          ["Applicant 1"]
                     :applicationId       "LP-000-2018-00001"
                     :arkistoija          {:firstName "Archive" :lastName "Tester" :username "tester"}
                     :buildingIds         []
                     :contents            "Karttaote"
                     :kayttotarkoitukset  ["011 yhden asunnon talot"]
                     :rakennusluokat      ["0110 omakotitalot"]
                     :kieli               "fi"
                     :kuntalupatunnukset  []
                     :lupapvm             "2018-10-03T12:00:00+03:00"
                     :municipality        "753"
                     :myyntipalvelu       false
                     :nakyvyys            "julkinen"
                     :nationalBuildingIds []
                     :operations          []
                     :organization        "753-R"
                     :paatospvm           "2018-09-27T12:00:00+03:00"
                     :propertyId          "75347800050140"
                     :ramLink             "5acb61e7e7f6046cc2f1991e"
                     :suunnittelijat      []
                     :tiedostonimi        "file.pdf"
                     :tila                "valmis"
                     :versio              "0.1"
                     :jattopvm            "2018-09-15T19:52:50+03:00"
                     :paatoksentekija     "Sonja Sibbo"
                     :closed              "2018-10-08T23:26:10+03:00"}
                    result))))

    (testing "For extinct application"
      (let [archive-app (assoc application :state "extinct" :extincted 1538082000000)
            test-user   {:username "tester" :firstName "Archive" :lastName "Tester"}
            attachment  (assoc-in attachment [:metadata :myyntipalvelu] true)]

        (testing "With timestamp"
          (let [result (archiving/generate-archive-metadata archive-app test-user :metadata {} attachment)]
            (is (= "julkinen" (:nakyvyys result)))
            (is (true? (:myyntipalvelu result)))
            (is (true? (:permit-expired result)))
            (is (= 1538082000000 (.getTime ^Date (:permit-expired-date result))))))

        (testing "Without timestamp"
          (is (false?
                (-> (archiving/generate-archive-metadata (dissoc archive-app :extincted) test-user :metadata {} attachment)
                    (keys)
                    (set)
                    (contains? :permit-expired-date)))))))

    (testing "For digitizing application"
      (let [archive-app (assoc application :verdicts [{:id              "5badf004da40a6b3fd01262f"
                                                       :kuntalupatunnus "BI-123"
                                                       :timestamp       nil
                                                       :paatokset       [{:poytakirjat [{:paatospvm 1538082000000}]}]
                                                       :draft           false}
                                                      {:id              "5badf013da40a6b3fd012630"
                                                       :kuntalupatunnus "BI-456"
                                                       :timestamp       nil
                                                       :paatokset       [{:poytakirjat [{:paatospvm 1539995600000}]}]
                                                       :draft           true}
                                                      {:id              "5badf01eda40a6b3fd012631"
                                                       :kuntalupatunnus "BI-789"
                                                       :timestamp       nil
                                                       :paatokset       [{:poytakirjat [{:paatospvm 1536872400000}]}]
                                                       :draft           false}]
                               :permitType "ARK")]
        (is (match? {:address             "Street 12"
                     :applicants          ["Applicant 1"]
                     :arkistoija          {:firstName "Archive" :lastName "Tester" :username "tester"}
                     :buildingIds         []
                     :contents            "Karttaote"
                     :kayttotarkoitukset  ["011 yhden asunnon talot"]
                     :rakennusluokat      ["0110 omakotitalot"]
                     :kieli               "fi"
                     :kuntalupatunnukset  ["BI-123" "BI-456" "BI-789"]
                     :lupapvm             "2018-09-28T00:00:00+03:00"
                     :municipality        "753"
                     :myyntipalvelu       false
                     :nakyvyys            "julkinen"
                     :nationalBuildingIds []
                     :operations          []
                     :organization        "753-R"
                     :paatospvm           "2018-09-28T00:00:00+03:00"
                     :propertyId          "75347800050140"
                     :ramLink             "5acb61e7e7f6046cc2f1991e"
                     :suunnittelijat      []
                     :tiedostonimi        "file.pdf"
                     :tila                "valmis"
                     :versio              "0.1"
                     :jattopvm            "2018-09-15T19:52:50+03:00"
                     :closed              "2018-10-08T23:26:10+03:00"}
                    (archiving/generate-archive-metadata archive-app
                                                         {:username "tester" :firstName "Archive" :lastName "Tester"}
                                                         :metadata
                                                         {}
                                                         attachment)))

        (testing "BackendIds are sorted based on attachment"
          (is (match? {:kuntalupatunnukset ["BI-789" "BI-123" "BI-456"]}
                      (archiving/generate-archive-metadata archive-app
                                                           {:username "tester" :firstName "Archive" :lastName "Tester"}
                                                           :metadata
                                                           {}
                                                           (assoc attachment :backendId "BI-789")))))))

    (testing "Retention end date update"
      (let [runeberg      (date/timestamp "5.2.2024")
            mayday        (date/timestamp "1.5.2024")
            application   (assoc application
                                 :verdicts [{:id              "5badf004da40a6b3fd01262f"
                                             :kuntalupatunnus "BI-123"
                                             :timestamp       12345
                                             :paatokset       [{:poytakirjat [{:paatospvm runeberg}]}]
                                             :draft           false}]
                                 :permitType "R")
            gam-test      (fn [metadata & [app]]
                            (->> metadata
                                 (archiving/generate-archive-metadata (or app application)
                                                                      {:username "hello"} :metadata {})
                                 :sailytysaika))
            add-retention #(assoc-in attachment [:metadata :sailytysaika] %)
            ->date        #'tiedonohjaus/->date]

        (is (nil? (gam-test attachment)))
        (is (nil? (gam-test (add-retention {}))))
        (is (= {:foo "bar"} (gam-test (add-retention {:foo "bar"}))))
        (is (= {:pituus               5
                :arkistointi          "määräajan"
                :retention-period-end (->date (date/plus runeberg :years 5))}
               (gam-test (add-retention {:pituus               5
                                         :arkistointi          "määräajan"
                                         :retention-period-end 12345678}))))
        (is (= {:pituus      5
                :arkistointi "ikuisesti"}
               (gam-test (add-retention {:pituus               5
                                         :arkistointi          "ikuisesti"
                                         :retention-period-end 12345678}))))
        (is (= {:pituus               10
                :arkistointi          "määräajan"
                :retention-period-end (->date (date/plus mayday :years 10))}
               (gam-test (add-retention {:pituus 10 :arkistointi "määräajan"})
                         (-> application
                             (assoc :submitted mayday)
                             (assoc-in [:primaryOperation :name] "tyonjohtajan-nimeaminen-v2")))))))))

(deftest project-description-test
  (let [application {:_projectDescriptionIndex "index"}]
    (testing "Only one description in documents"
      (is (= "hello"
             (archiving/project-description application [{:data {:kuvaus {:value "hello"}}}
                                                         {:data {:foo {:value "world"}}}]))))
    (testing "Multiple descriptions"
      (is (= "index"
             (archiving/project-description application [{:data {:kuvaus {:value "hello"}}}
                                                         {:data {:kuvaus {:value "world"}}}]))))
    (testing "Multiple descriptions: previous-permit"
      (is (= "hello"
             (archiving/project-description {:primaryOperation {:name "aiemmalla-luvalla-hakeminen"}}
                                            [{:data {:kuvaus {:value "hello"}}}
                                             {:data {:kuvaus {:value "world"}}}])))
      (is (= "hello"
             (archiving/project-description {:primaryOperation    {:name "varasto-tms"}
                                             :secondaryOperations [{:name "foo"}
                                                                   {:name "aiemmalla-luvalla-hakeminen"}]}
                                            [{:data {:kuvaus {:value "hello"}}}
                                             {:data {:kuvaus {:value "world"}}}]))))
    (testing "No descriptions"
      (is (= "index" (archiving/project-description application [])))
      (is (= "index"
             (archiving/project-description {:primaryOperation         {:name "aiemmalla-luvalla-hakeminen"}
                                             :_projectDescriptionIndex "index"}
                                            []))))))

(deftest tyomaasta-vastaava-test
  (is (nil? (archiving/tyomaasta-vastaava {:documents []})))
  (is (= "Responsible Site"
         (archiving/tyomaasta-vastaava {:documents [{:schema-info {:name    "tyomaasta-vastaava-optional"
                                                                   :subtype "tyomaasta-vastaava"}
                                                     :data        {:_selected {:value "henkilo"}
                                                                   :henkilo   {:henkilotiedot {:etunimi  {:value "Site"}
                                                                                               :sukunimi {:value "Responsible"}}}}}]})))
  (is (= "Responsible"
         (archiving/tyomaasta-vastaava {:documents [{:schema-info {:name    "tyomaasta-vastaava-optional"
                                                                   :subtype "tyomaasta-vastaava"}
                                                     :data        {:_selected {:value "henkilo"}
                                                                   :henkilo   {:henkilotiedot {:etunimi  {:value "   "}
                                                                                               :sukunimi {:value "  Responsible  "}}}}}]})))
  (is (= "Site"
         (archiving/tyomaasta-vastaava {:documents [{:schema-info {:name    "tyomaasta-vastaava-optional"
                                                                   :subtype "tyomaasta-vastaava"}
                                                     :data        {:_selected {:value "henkilo"}
                                                                   :henkilo   {:henkilotiedot {:etunimi {:value "  Site   "}}}}}]})))
  (is (= ""
         (archiving/tyomaasta-vastaava {:documents [{:schema-info {:name    "tyomaasta-vastaava-optional"
                                                                   :subtype "tyomaasta-vastaava"}
                                                     :data        {:_selected {:value "henkilo"}
                                                                   :henkilo   {:henkilotiedot {:etunimi  {:value "  "}
                                                                                               :sukunimi {:value "  "}}}}}]})))
  (is (nil?
        (archiving/tyomaasta-vastaava {:documents [{:schema-info {:name    "tyomaastaVastaava"
                                                                  :subtype "tyomaasta-vastaava"}
                                                    :data        {:_selected {:value "yritys"}
                                                                  :henkilo   {:henkilotiedot {:etunimi  {:value "Site"}
                                                                                              :sukunimi {:value "Responsible"}}}}}]})))
  (is (= "Company Inc."
         (archiving/tyomaasta-vastaava {:documents [{:schema-info {:name    "tyomaastaVastaava"
                                                                   :subtype "tyomaasta-vastaava"}
                                                     :data        {:_selected {:value "yritys"}
                                                                   :yritys    {:yritysnimi {:value "Company Inc."}}}}]})))
  (is (= "Company Inc."
         (archiving/tyomaasta-vastaava {:documents [{:schema-info {:name    "tyomaastaVastaava"
                                                                   :subtype "tyomaasta-vastaava"}
                                                     :data        {:_selected {:value "yritys"}
                                                                   :yritys    {:yritysnimi {:value "  Company Inc.  "}}}}]}))))

(deftest merge-building-metadata-test
  (testing "returns empty map when buildings do not have metadata"
    (is (= {} (archiving/merge-building-metadata [])))
    (is (= {} (archiving/merge-building-metadata [{} {}])))
    (is (= {} (archiving/merge-building-metadata [{} {:metadata {}}]))))
  (testing "return metadata from a single building"
    (is (= {:julkisuusluokka "salainen"}
           (archiving/merge-building-metadata [{:metadata {:julkisuusluokka "salainen"}}]))))
  (testing "return the single metadata when there are multiple buildings but only one has metadata"
    (is (= {:julkisuusluokka "salainen"}
           (archiving/merge-building-metadata [{:metadata {}}
                                               {}
                                               {:metadata {:julkisuusluokka "salainen"}}]))))
  (testing "returns merged metadata when multiple buildings have metadata but no overlap in attributes"
    (is (= {:nakyvyys        "julkinen"
            :myyntipalvelu   true
            :julkisuusluokka "salainen"}
           (archiving/merge-building-metadata [{:metadata {:nakyvyys "julkinen"}}
                                               {:metadata {:myyntipalvelu true}}
                                               {:metadata {:julkisuusluokka "salainen"}}]))))
  (testing "returns only metadata attributes for which we have merge functionality"
    (is (= {:nakyvyys        "julkinen"
            :myyntipalvelu   true
            :julkisuusluokka "salainen"}
           (archiving/merge-building-metadata [{:metadata {:nakyvyys "julkinen" :foo "FOO"}}
                                               {:metadata {:myyntipalvelu true :foo "FOO2"}}
                                               {:metadata {:julkisuusluokka "salainen"} :bar "BAR"}]))))
  (testing "given multiple buildings with overlapping metadata"
    (testing "returns the strictest julkisuusluokka"
      (is (= {:julkisuusluokka "salainen"}
             (archiving/merge-building-metadata [{:metadata {:julkisuusluokka "julkinen"}}
                                                 {:metadata {:julkisuusluokka "salainen"}}
                                                 {:metadata {:julkisuusluokka "osittain-salassapidettava"}}])))
      (is (= {:julkisuusluokka "salainen"}
             (archiving/merge-building-metadata [{:metadata {:julkisuusluokka "julkinen"}}
                                                 {:metadata {:julkisuusluokka "salainen"}}])))
      (is (= {:julkisuusluokka "osittain-salassapidettava"}
             (archiving/merge-building-metadata [{:metadata {:julkisuusluokka "julkinen"}}
                                                 {:metadata {:julkisuusluokka "osittain-salassapidettava"}}])))
      (is (= {:julkisuusluokka "salainen"}
             (archiving/merge-building-metadata [{:metadata {:julkisuusluokka "salainen"}}
                                                 {:metadata {:julkisuusluokka "osittain-salassapidettava"}}]))))
    (testing "returns the strictest nakyvyys"
      (is (= {:nakyvyys "viranomainen"}
             (archiving/merge-building-metadata [{:metadata {:nakyvyys "julkinen"}}
                                                 {:metadata {:nakyvyys "asiakas-ja-viranomainen"}}
                                                 {:metadata {:nakyvyys "viranomainen"}}])))
      (is (= {:nakyvyys "viranomainen"}
             (archiving/merge-building-metadata [{:metadata {:nakyvyys "julkinen"}}
                                                 {:metadata {:nakyvyys "viranomainen"}}])))
      (is (= {:nakyvyys "asiakas-ja-viranomainen"}
             (archiving/merge-building-metadata [{:metadata {:nakyvyys "julkinen"}}
                                                 {:metadata {:nakyvyys "asiakas-ja-viranomainen"}}])))
      (is (= {:nakyvyys "viranomainen"}
             (archiving/merge-building-metadata [{:metadata {:nakyvyys "viranomainen"}}
                                                 {:metadata {:nakyvyys "asiakas-ja-viranomainen"}}]))))
    (testing "returns the strictest myyntipalvelu"
      (is (= {:myyntipalvelu false}
             (archiving/merge-building-metadata [{:metadata {:myyntipalvelu false}}
                                                 {:metadata {:myyntipalvelu true}}])))
      (is (= {:myyntipalvelu false}
             (archiving/merge-building-metadata [{:metadata {:myyntipalvelu true}}
                                                 {:metadata {:myyntipalvelu false}}]))))
    (testing "returns the strictest combination when multiple overlapping fields found"
      (is (= {:julkisuusluokka "salainen"
              :nakyvyys        "viranomainen"
              :myyntipalvelu   false}
             (archiving/merge-building-metadata [{:metadata {:julkisuusluokka "julkinen" :nakyvyys "julkinen" :myyntipalvelu true}}
                                                 {:metadata {:julkisuusluokka "osittain-salassapidettava" :nakyvyys "viranomainen" :myyntipalvelu false}}
                                                 {:metadata {:julkisuusluokka "salainen" :nakyvyys "asiakas-ja-viranomainen" :myyntipalvelu false}}]))))))

(def test-application
  {:buildings           [{:nationalId  "b-a"
                          :operationId "op-a"}
                         {:nationalId  "b-b"
                          :operationId "op-b"}
                         {:nationalId  "b-c"
                          :operationId "op-c"}
                         {:nationalId  "b-e"
                          :operationId "op-e"}
                         {:nationalId  "b-f"
                          :operationId "op-f"}
                         {:nationalId "b-g"}]
   :primaryOperation    {:id "op-a"}
   :secondaryOperations [{:id "op-b"} {:id "op-c"} {:id "op-d"}
                         {:id "op-e"} {:id "op-f1"} {:id "op-f2"}
                         {:id "op-g"}]
   :documents           [;; some ops have nothing to do with buildings
                         {:unrelated "stuff"}
                         {:unrelated   "stuff"
                          :schema-info {:op {:id "unrelated-op"}}}
                         ;; normal case
                         {:data        {:valtakunnallinenNumero {:value "db-a"}}
                          :schema-info {:op {:id "op-a"}}}
                         ;; override building id
                         {:data        {:valtakunnallinenNumero {:value "db-b"}}
                          :schema-info {:op {:id "op-b"}}}
                         ;; op wo/ building at all
                         {:schema-info {:op {:id "op-c"}}}
                         ;; op wo/ associated building in buildings array
                         {:data        {:valtakunnallinenNumero {:value "db-d"}}
                          :schema-info {:op {:id "op-d"}}}
                         ;; multiple ops for one building
                         {:data        {:valtakunnallinenNumero {:value "db-e"}}
                          :schema-info {:op {:id "op-e"}}}
                         {:data        {:valtakunnallinenNumero {:value "db-e"}}
                          :schema-info {:op {:id "op-e2"}}}
                         ;; multiple ops for one building, override id
                         {:data        {:valtakunnallinenNumero {:value "db-f"}}
                          :schema-info {:op {:id "op-f"}}}
                         {:data        {:valtakunnallinenNumero {:value "db-f"}}
                          :schema-info {:op {:id "op-f2"}}}
                         {:data        {:valtakunnallinenNumero {:value "  "}}
                          :schema-info {:op {:id "op-g"}}}]})

(def one-to-one-application
  {:buildings           [{:nationalId "b-one"}]
   :documents           [{:unrelated "stuff"}
                         {:schema-info {:op {:id "hello"}}
                          :data        {:valtakunnallinenNumero {:value " "}}}]
   :primaryOperation    {:id "hello"}
   :secondaryOperations []})

(defn ids-for-attachment
  ([attachment application]
   (->> attachment
        (archiving/op-specific-data-for-attachment application)
        :nationalBuildingIds
        set))
  ([attachment] (ids-for-attachment attachment test-application)))

(deftest building-id-test
  (is (= #{"db-b"}
         (ids-for-attachment {:op [{:id "op-b"}]}))
      "building id override on documents array")
  (is (= #{"b-c"}
         (ids-for-attachment {:op [{:id "op-c"}]}))
      "when missing from docs, id from buildings")
  (is (= #{"db-d"}
         (ids-for-attachment {:op [{:id "op-d"}]}))
      "no building, just op")
  (is (= #{"db-a" "db-b"}
         (ids-for-attachment {:op [{:id "op-a"} {:id "op-b"}]}))
      "building id override on documents array, multiple")
  (is (= #{"db-e"}
         (ids-for-attachment {:op [{:id "op-e"}]}))
      "multiple ops for this building")
  (is (= #{"db-e"}
         (ids-for-attachment {:op [{:id "op-e"} {:id "op-e2"}]}))
      "multiple ops for this building, multiple ops on attachment")
  (is (= #{"db-f"}
         (ids-for-attachment {:op [{:id "op-f"} {:id "op-f2"}]}))
      "multiple ops for this building, multiple ops on attachment, override id")
  (is (= #{"db-a" "db-b" "b-c" "db-d" "db-e" "db-f" "b-g"}
         (ids-for-attachment {}))
      "no op on attachment returns all ids, override + missing id on documents")
  (is (empty? (ids-for-attachment {:op [{:id "op-g"}]}))
      "No matching building for the operation")
  (testing "Only one operation and building"
    (is (= #{"b-one"}
           (ids-for-attachment {:op {:id "hello"}} one-to-one-application)))
    (is (= #{"db-one"}
           (ids-for-attachment {:op {:id "hello"}}
                               (assoc-in one-to-one-application
                                         [:documents 1 :data :valtakunnallinenNumero :value]
                                         "db-one"))))
    (is (= #{"b-one"}
           (ids-for-attachment {:op {:id "what"}} one-to-one-application)))
    (is (empty? (ids-for-attachment {:op {:id "what"}}
                                    (update one-to-one-application :secondaryOperations
                                            conj {:op {:id "world"}}))))
    (is (= #{"b-one"}
           (ids-for-attachment {} one-to-one-application)))))

(deftest bim-archiving-metadata-test
  (env/with-feature-value :bim-archiving true
    (with-redefs [foreman-app-util/get-linked-foreman-applications-by-id (constantly nil)
                  tiedonohjaus/get-from-toj-api                          (constantly nil)]
      (let [linked-file {:target-entity {:target-type    "bim-model-file"
                                         :application-id "LP-753-2025-90002"
                                         :file-id        "e5b24151-a5d2-46a7-96cf-cf708884fd95"
                                         :operation-id   "68c35a1b79e1375cb38b61e3"}
                         :metadata      {:model-type      "architectural"
                                         :julkisuusluokka "julkinen"
                                         :sailytysaika    {:arkistointi "ikuisesti" :perustelu "Oma tarve"}
                                         :henkilotiedot   "ei-sisalla"
                                         :kieli           "fi"
                                         :tila            "arkistoitu"
                                         :myyntipalvelu   false
                                         :nakyvyys        "julkinen"}
                         :versions      [{:storage-system   "gcs"
                                          :file-version-id  "b7aa7425-ca77-4b04-ae04-c73088345461"
                                          :version          {:major 1 :minor 2}
                                          :bucket           "application-files"
                                          :target-file-path "LP-753-2025-90002/b7aa7425-ca77-4b04-ae04-c73088345461"
                                          :metadata         {:name         "riscv-ncg-presentation-1.pdf"
                                                             :size         811497
                                                             :content-type "application/pdf"}}]}
            test-user   {:username "tester" :firstName "Archive" :lastName "Tester"}]
        (testing "Generates metadata for BIM file"
          (is (match? {:applicants          []
                       :arkistoija          {:firstName "Archive" :lastName "Tester" :username "tester"}
                       :buildingIds         []
                       :henkilotiedot       "ei-sisalla"
                       :julkisuusluokka     "julkinen"
                       :kayttotarkoitukset  []
                       :kieli               "fi"
                       :kuntalupatunnukset  []
                       :myyntipalvelu       false
                       :nakyvyys            "julkinen"
                       :nationalBuildingIds ["b-g" "db-a" "db-b" "b-c" "db-d" "db-e" "db-f"]
                       :operations          []
                       :rakennusluokat      []
                       :sailytysaika        {:arkistointi "ikuisesti" :perustelu "Oma tarve"}
                       :suunnittelijat      []
                       :tiedostonimi        "riscv-ncg-presentation-1.pdf"
                       :tila                "arkistoitu"
                       :type                :tietomallit.arkkitehtimalli_BIM
                       :versio              "1.2"}
                      (archiving/generate-archive-metadata test-application test-user :metadata {} linked-file))))))))
