(ns lupapalvelu.archive.archiving-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.attachment :as at]
            [lupapalvelu.archive.archiving :refer :all]
            [lupapalvelu.permit :as permit]
            [schema.core :as sc]))

(testable-privates lupapalvelu.archive.archiving generate-archive-metadata)

(facts "Should archiving valid YA applications"
  (fact "Valid YA application with valid state - should be archived"
    (valid-ya-state? {:id "LP-XXX-2017-00001" :permitType "YA" :state "verdictGiven"}) => true
    (valid-ya-state? {:id "LP-XXX-2017-00002" :permitType "YA" :state "finished"}) => true
    (valid-ya-state? {:id "LP-XXX-2017-00003" :permitType "YA" :state "extinct"}) => true
    (valid-ya-state? {:id "LP-XXX-2017-00004" :permitType "YA" :state "appealed"}) => true
    (valid-ya-state? {:id "LP-XXX-2017-00005" :permitType "YA" :state "closed"}) => true
    (valid-ya-state? {:id "LP-XXX-2017-00005" :permitType "YA" :state "agreementSigned"}) => true)
  (fact "Valid YA application with invalid state - should not be archived"
    (valid-ya-state? {:id "LP-XXX-2017-00006" :permitType "YA" :state "open"}) => false
    (valid-ya-state? {:id "LP-XXX-2017-00007" :permitType "YA" :state "submitted"}) => false
    (valid-ya-state? {:id "LP-XXX-2017-00008" :permitType "YA" :state "sent"}) => false
    (valid-ya-state? {:id "LP-XXX-2017-00009" :permitType "YA" :state "draft"}) => false
    (valid-ya-state? {:id "LP-XXX-2017-00010" :permitType "YA" :state "complementNeeded"}) => false
    (valid-ya-state? {:id "LP-XXX-2017-00005" :permitType "YA" :state "agreementPrepared"}) => false)
  (fact "Valid R application with valid R state - should not be in valid YA state"
    (valid-ya-state? {:id "LP-XXX-2017-00011" :permitType "R" :state "verdictGiven"}) => false
    (valid-ya-state? {:id "LP-XXX-2017-00012" :permitType "R" :state "closed"}) => false
    (valid-ya-state? {:id "LP-XXX-2017-00013" :permitType "R" :state "foremanVerdictGiven"}) => false)
  (fact "Invalid R application with invalid YA state - should not be in valid YA state"
    (valid-ya-state? {:id "LP-XXX-2017-00014" :permitType "R" :state "finished"}) => false))

(facts "Archiving project"
  (fact "Should set attachment specific backendId as first permit id"
    (let [verdicts [{:kuntalupatunnus "LX-0001"} {:kuntalupatunnus "LX-0002"} {:kuntalupatunnus "LX-0003"}]]
      (permit-ids-for-archiving {:verdicts verdicts} {:backendId "LX-0002"} permit/ARK) => ["LX-0002" "LX-0001" "LX-0003"]
      (permit-ids-for-archiving {:verdicts verdicts} {:backendId "LX-0002"} permit/R) => ["LX-0001" "LX-0002" "LX-0003"]))
  (fact "Should use attachment specific verdict for verdict date"
    (let [verdicts [{:kuntalupatunnus "LX-0001" :paatokset [{:poytakirjat [{:paatospvm 1482530400000}]}]}
                    {:kuntalupatunnus "LX-0002" :paatokset [{:poytakirjat [{:paatospvm 1512597600000}]}]}
                    {:kuntalupatunnus "LX-0003" :paatokset [{:poytakirjat [{:paatospvm 1510057163483}]}]}]]
      (get-ark-paatospvm {:verdicts verdicts} {:backendId "LX-0002"}) => "2017-12-07T00:00:00+02:00")))


(def application {:id "LP-000-2018-00001"
                  :closed 1539030370458
                  :address "Street 12"
                  :_applicantIndex ["Applicant 1"]
                  :municipality "753"
                  :permitType "R"
                  :submitted 1537030370458
                  :organization "753-R"
                  :handlers [{:id "5bac7b2af4dfa392c827c3a2"
                              :roleId "abba1111111111111111acdc"
                              :userId "777777777777777777000023"
                              :firstName "Sonja"
                              :lastName "Sibbo"}]
                  :tosFunction "10 03 00 01"
                  :propertyId "75347800050140"
                  :drawings []
                  :documents []})


(def attachment {:groupType nil
                 :type {:type-id :karttaote :type-group :rakennuspaikka}
                 :op nil
                 :auth [{:id "777777777777777777000023" :username "sonja" :firstName "Sonja" :lastName "Sibbo" :role "uploader"}]
                 :modified 1538030348972
                 :sent 1538030378975
                 :requestedByAuthority true
                 :applicationState :open
                 :readOnly false
                 :locked false
                 :id "5bac7b0df4dfa392c827c396"
                 :latestVersion {:storageSystem :mongodb
                                 :created 1538030348972
                                 :size 130575
                                 :filename "file.pdf"
                                 :originalFileId "032db7f0-c220-11e8-bace-28134fe06fa5"
                                 :autoConversion true
                                 :contentType "application/pdf"
                                 :archivable true
                                 :version {:major 0 :minor 1}
                                 :stamped false
                                 :user {:id "777777777777777777000023"
                                        :username "sonja"
                                        :firstName "Sonja"
                                        :lastName "Sibbo"
                                        :role "authority"}
                                 :fileId "09b1cf30-c220-11e8-bace-28134fe06fa5"}
                 :notNeeded false
                 :signatures []
                 :backendId nil
                 :forPrinting false
                 :contents "Karttaote"
                 :target nil
                 :versions [{:storageSystem :mongodb
                             :created 1538030348972
                             :size 130575
                             :filename "file.pdf"
                             :originalFileId "032db7f0-c220-11e8-bace-28134fe06fa5"
                             :autoConversion true
                             :contentType "application/pdf"
                             :archivable true
                             :version {:major 0 :minor 1}
                             :stamped false
                             :user {:id "777777777777777777000023"
                                    :username "sonja"
                                    :firstName "Sonja"
                                    :lastName "Sibbo"
                                    :role "authority"}
                             :fileId "09b1cf30-c220-11e8-bace-28134fe06fa5"}]
                 :ramLink "5acb61e7e7f6046cc2f1991e"
                 :metadata {:nakyvyys "julkinen" :tila "valmis"}
                 :required false})

(facts "Test data is valid"
  (sc/check at/Attachment attachment) => nil)

(facts "Archiving metadata"
  (against-background [(lupapalvelu.foreman/get-linked-foreman-applications-by-id anything) => nil
                       (lupapalvelu.tiedonohjaus/get-from-toj-api anything anything) => nil])
  (fact "For application with legacy verdict"
    (generate-archive-metadata (assoc application :verdicts [{:kuntalupatunnus "18-0370-R"
                                                              :paatokset [{:paivamaarat {:aloitettava 1632787200000
                                                                                         :lainvoimainen 1538092800000
                                                                                         :voimassaHetki 1695859200000
                                                                                         :anto 1536796800000
                                                                                         :viimeinenValitus 1538006400000
                                                                                         :julkipano 1536710400000}
                                                                           :poytakirjat [{:paatoskoodi "myönnetty"
                                                                                          :paatospvm 1536537600000
                                                                                          :pykala 318
                                                                                          :paatoksentekija "Viranhaltija"
                                                                                          :status "1"
                                                                                          :urlHash "9d39671760cf07327678a31973c399f907336d9a"}]
                                                                           :id "5b99e1ace7f6041af7cd7dfd"}]
                                                              :id "5b99e1ace7f6041af7cd7dfb"
                                                              :timestamp 1536811436294}])
                               {:username "tester" :firstName "Archive" :lastName "Tester"}
                               :metadata
                               attachment)
    => (contains {:address             "Street 12"
                  :applicants          ["Applicant 1"]
                  :applicationId       "LP-000-2018-00001"
                  :arkistoija          {:firstName "Archive" :lastName "Tester" :username "tester"}
                  :buildingIds         []
                  :contents            "Karttaote"
                  :kayttotarkoitukset  []
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
                  :closed              "2018-10-08T23:26:10+03:00"}))

  (fact "For application wiht pate verdict"
    (generate-archive-metadata (assoc application :pate-verdicts [{:category "r"
                                                                   :schema-version 1
                                                                   :state {:_value "published" :_user "sonja" :_modified 1538030437064}
                                                                   :modified 1538030437064
                                                                   :id "5bac7b49f4dfa392c827c3ac"
                                                                   :archive {:verdict-date 1538038800000 :lainvoimainen 1538557200000 :anto 1538384400000 :verdict-giver "Sonja Sibbo"}
                                                                   :published {:tags "tags"
                                                                               :published 1538030437064
                                                                               :attachment-id "5bac7b67f4dfa392c827c3b3"}
                                                                   :data {:address "Latokuja 10"
                                                                          :julkipano 1538125200000
                                                                          :verdict-date 1538038800000
                                                                          :buildings {:5bac7af5f4dfa392c827c387 {:show-building true
                                                                                                                 :operation "pientalo"
                                                                                                                 :description ""
                                                                                                                 :building-id ""
                                                                                                                 :tag ""
                                                                                                                 :order "0"}}
                                                                          :verdict-section "76"
                                                                          :verdict-text "Päätös annettu"
                                                                          :muutoksenhaku 1538470800000
                                                                          :anto 1538384400000
                                                                          :attachments [{:type-group "paatoksenteko" :type-id "paatos" :amount 1}]
                                                                          :operation "Talon rakennus"
                                                                          :verdict-code "annettu-lausunto"
                                                                          :language "fi"
                                                                          :foremen-included true
                                                                          :deviations ""
                                                                          :neighbor-states []
                                                                          :lainvoimainen 1538557200000
                                                                          :handler "Sonja Sibbo"
                                                                          :automatic-verdict-dates true
                                                                          :statements []}}])
                               {:username "tester" :firstName "Archive" :lastName "Tester"}
                               :metadata
                               attachment)
    => (contains {:address             "Street 12"
                  :applicants          ["Applicant 1"]
                  :applicationId       "LP-000-2018-00001"
                  :arkistoija          {:firstName "Archive" :lastName "Tester" :username "tester"}
                  :buildingIds         []
                  :contents            "Karttaote"
                  :kayttotarkoitukset  []
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
                  :closed              "2018-10-08T23:26:10+03:00"}))

  (fact "For digitizing application"
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
      (generate-archive-metadata archive-app
                                 {:username "tester" :firstName "Archive" :lastName "Tester"}
                                 :metadata
                                 attachment)
    => (contains {:address             "Street 12"
                  :applicants          ["Applicant 1"]
                  :arkistoija          {:firstName "Archive" :lastName "Tester" :username "tester"}
                  :buildingIds         []
                  :contents            "Karttaote"
                  :kayttotarkoitukset  []
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
                  :closed              "2018-10-08T23:26:10+03:00"})

      (fact "BackendIds are sorted based on attachment"
        (generate-archive-metadata archive-app
                                   {:username "tester" :firstName "Archive" :lastName "Tester"}
                                   :metadata
                                   (assoc attachment :backendId "BI-789")) => (contains {:kuntalupatunnukset ["BI-789" "BI-123" "BI-456"]})))))
