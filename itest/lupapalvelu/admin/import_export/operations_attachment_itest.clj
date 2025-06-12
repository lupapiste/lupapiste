(ns lupapalvelu.admin.import-export.operations-attachment-itest
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.itest-util :refer [admin command err local-fixture-factory local-raw query]]
            [lupapalvelu.mongo :as mongo]
            [muuntaja.core :as m]))

(use-fixtures :each (local-fixture-factory))

(deftest import-export-operations-attachment-test
  (let [fetch-orgs-args {:mode   "export"
                         :target "operations-attachment"}]

    (testing "minimal fixture has quite a few orgs with operations attachments"
      (is (= {:ok true :organizations {"091-R"  {:fi "Helsingin rakennusvalvontavirasto"
                                                 :sv "Helsingfors byggnadstillsynsverket"
                                                 :en "Helsingin rakennusvalvontavirasto"}
                                       "091-YA" {:fi "Helsingin yleiset alueet"
                                                 :sv "Helsingfors yleiset alueet"
                                                 :en "Helsingin yleiset alueet"}
                                       "092-R"  {:fi "Vantaa - Rakennusvalvonta"
                                                 :sv "Vanda - Byggnadstillsyn"
                                                 :en "Vantaa - Rakennusvalvonta"}
                                       "092-YA" {:fi "Vantaa - Yleiset alueet"
                                                 :sv "Vanda - Yleiset alueet"
                                                 :en "Vantaa - Yleiset alueet"}
                                       "186-R"  {:fi "Järvenpään rakennusvalvonta"
                                                 :sv "Järvenpään rakennusvalvonta"
                                                 :en "Järvenpään rakennusvalvonta"}
                                       "297-R"  {:fi "Kuopio rakennusvalvonta"
                                                 :sv "Kuopio byggnadstillsyn"
                                                 :en "Kuopio rakennusvalvonta"}
                                       "297-YA" {:fi "Kuopio yleisten alueiden kaytto"
                                                 :sv "Kuopio yleisten alueiden kaytto"
                                                 :en "Kuopio yleisten alueiden kaytto"}
                                       "564-YA" {:fi "Oulun yleiset alueet"
                                                 :sv "Oulun yleiset alueet"
                                                 :en "Oulun yleiset alueet"}
                                       "609-R"  {:fi "Pori - Rakennusvalvonta"
                                                 :sv "Björneborg - Byggnadstilsyn"
                                                 :en "Pori - Rakennusvalvonta"}
                                       "753-R"  {:fi "Sipoon rakennusvalvonta"
                                                 :sv "Sipoon rakennusvalvonta"
                                                 :en "Sipoon rakennusvalvonta"}
                                       "753-YA" {:fi "Sipoon yleisten alueiden rakentaminen"
                                                 :sv "Sipoon yleisten alueiden rakentaminen"
                                                 :en "Sipoon yleisten alueiden rakentaminen"}
                                       "837-R"  {:fi "Tampereen rakennusvalvonta"
                                                 :sv "Tampereen rakennusvalvonta"
                                                 :en "Tampereen rakennusvalvonta"}
                                       "837-YA" {:fi "Tampere yleiset alueet"
                                                 :sv "Tammerfors yleiset alueet"
                                                 :en "Tampere yleiset alueet"}}}
             (query admin :import-export-fetch-organizations fetch-orgs-args))))

    (let [fetch-items-args {:target "operations-attachment"
                            :org-id "753-R"}]
      (testing "get item(s) from sipoo"
        (let [{:keys [ok items]} (query admin :import-export-fetch-items fetch-items-args)]
          (is ok)
          (is (= {"kerrostalo-rivitalo"      [[:paapiirustus :asemapiirros]
                                              [:paapiirustus :pohjapiirustus]
                                              [:hakija :valtakirja]
                                              [:pelastusviranomaiselle_esitettavat_suunnitelmat :vaestonsuojasuunnitelma]]
                  "vapaa-ajan-asuinrakennus" [[:paapiirustus :pohjapiirustus]
                                              [:hakija :ote_kauppa_ja_yhdistysrekisterista]
                                              [:pelastusviranomaiselle_esitettavat_suunnitelmat :vaestonsuojasuunnitelma]
                                              [:suunnitelmat :valaistussuunnitelma]]
                  "poikkeamis"               [[:paapiirustus :asemapiirros]]
                  "meluilmoitus"             [[:kartat :kartta-melun-ja-tarinan-leviamisesta]
                                              [:muut :muu]]
                  "yl-uusi-toiminta"         [[:muut :muu]]
                  "maa-aineslupa"            [[:muut :muu]]
                  "vvvl-vesijohdosta"        [[:muut :muu]]}
                 items))

          (testing "export item(s) from sipoo"
            (let [export-args {:target   "operations-attachment"
                               :org-id   "753-R"
                               :item-ids "kerrostalo-rivitalo"}]

              (testing "an empty export is technically ok"
                (let [{:keys [status headers] :as resp} (local-raw admin :import-export-export-items (assoc export-args :org-id "564-R"))
                      {:strs [Content-Type
                              Content-Disposition]}     headers
                      edn-body                          (m/decode-response-body resp)]
                  (is (= 200 status))
                  (is (= "application/transit+json" Content-Type))
                  (is (re-matches #"attachment; filename=\"export_\d{8}_\d{6}_operations-attachment_564-R.json\""
                                  Content-Disposition))
                  (is (= {:target :operations-attachment
                          :org-id "564-R"
                          :env    "local"
                          :items  {}}
                         edn-body))))

              (testing "single item"
                (let [{:keys [status headers] :as resp} (local-raw admin :import-export-export-items export-args)
                      {:strs [Content-Type
                              Content-Disposition]}     headers
                      edn-body                          (m/decode-response-body resp)]
                  (is (= 200 status))
                  (is (= "application/transit+json" Content-Type))
                  (is (re-matches #"attachment; filename=\"export_\d{8}_\d{6}_operations-attachment_753-R.json\""
                                  Content-Disposition))
                  (is (= {:target :operations-attachment
                          :org-id "753-R"
                          :env    "local"
                          :items  {"kerrostalo-rivitalo" [[:paapiirustus :asemapiirros]
                                                          [:paapiirustus :pohjapiirustus]
                                                          [:hakija :valtakirja]
                                                          [:pelastusviranomaiselle_esitettavat_suunnitelmat
                                                           :vaestonsuojasuunnitelma]]}}
                         edn-body))))

              (testing "multiple items"
                (let [{:keys [status headers] :as resp} (local-raw admin :import-export-export-items (assoc export-args :item-ids "poikkeamis,meluilmoitus"))
                      {:strs [Content-Type
                              Content-Disposition]}     headers
                      edn-body                          (m/decode-response-body resp)]
                  (is (= 200 status))
                  (is (= "application/transit+json" Content-Type))
                  (is (re-matches #"attachment; filename=\"export_\d{8}_\d{6}_operations-attachment_753-R.json\""
                                  Content-Disposition))
                  (is (= {:target :operations-attachment
                          :org-id "753-R"
                          :env    "local"
                          :items  {"poikkeamis"   [[:paapiirustus :asemapiirros]]
                                   "meluilmoitus" [[:kartat :kartta-melun-ja-tarinan-leviamisesta]
                                                   [:muut :muu]]}}
                         edn-body))))))

          (testing "import"
            (let [import-args {:org-id   "753-R"
                               :filename "some-file.json"
                               :document {:target "operations-attachment"
                                          :org-id "123-R"
                                          :env    "whatever"
                                          :items  {}}}]
              (testing "invalid import args"
                (is (= {:ok       false
                        :text     "error.unknown-operations"
                        :loc-args ["foo._group_label"
                                   "bar._group_label"]}
                       (command admin :import-export-import-items (-> import-args
                                                                      (assoc-in [:document :items] {"foo" [[:muut :muu]]
                                                                                                    "bar" [[:muut :muu]]}))))
                    "unknown operations are rejected")
                (is ((err :error.illegal-value:schema-validation)
                     (command admin :import-export-import-items (-> import-args
                                                                    (assoc-in [:document :items] {"meluilmoitus" [[:foo :bar]]}))))
                    "unknown attachment values are detected through schema validation"))

              (testing "happy path imports"
                (testing "an empty import is technically ok"
                  (is (= {:ok true}
                         (command admin :import-export-import-items import-args))))
                (testing "merging attachments to existing operation is ok"
                  (is (= {:ok true}
                         (command admin :import-export-import-items (-> import-args
                                                                        (assoc :replace? false)
                                                                        (assoc-in [:document :items] {"poikkeamis" [[:paapiirustus :asemapiirros]
                                                                                                                    [:muut :muu]]})))))
                  (is (= {:kerrostalo-rivitalo      [["paapiirustus" "asemapiirros"]
                                                     ["paapiirustus" "pohjapiirustus"]
                                                     ["hakija" "valtakirja"]
                                                     ["pelastusviranomaiselle_esitettavat_suunnitelmat"
                                                      "vaestonsuojasuunnitelma"]]
                          :vapaa-ajan-asuinrakennus [["paapiirustus" "pohjapiirustus"]
                                                     ["hakija" "ote_kauppa_ja_yhdistysrekisterista"]
                                                     ["pelastusviranomaiselle_esitettavat_suunnitelmat"
                                                      "vaestonsuojasuunnitelma"]
                                                     ["suunnitelmat" "valaistussuunnitelma"]]
                          :poikkeamis               [["paapiirustus" "asemapiirros"]
                                                     ["muut" "muu"]]
                          :meluilmoitus             [["kartat" "kartta-melun-ja-tarinan-leviamisesta"]
                                                     ["muut" "muu"]]
                          :yl-uusi-toiminta         [["muut" "muu"]]
                          :maa-aineslupa            [["muut" "muu"]]
                          :vvvl-vesijohdosta        [["muut" "muu"]]}
                         (-> (mongo/by-id :organizations "753-R" [:operations-attachments])
                             :operations-attachments))))
                (testing "merging new operations is ok"
                  (is (= {:ok true}
                         (command admin :import-export-import-items (-> import-args
                                                                        (assoc :replace? false)
                                                                        (assoc-in [:document :items] {"aloitusoikeus" [[:paapiirustus :asemapiirros]]})))))
                  (is (= {:kerrostalo-rivitalo      [["paapiirustus" "asemapiirros"]
                                                     ["paapiirustus" "pohjapiirustus"]
                                                     ["hakija" "valtakirja"]
                                                     ["pelastusviranomaiselle_esitettavat_suunnitelmat"
                                                      "vaestonsuojasuunnitelma"]]
                          :vapaa-ajan-asuinrakennus [["paapiirustus" "pohjapiirustus"]
                                                     ["hakija" "ote_kauppa_ja_yhdistysrekisterista"]
                                                     ["pelastusviranomaiselle_esitettavat_suunnitelmat"
                                                      "vaestonsuojasuunnitelma"]
                                                     ["suunnitelmat" "valaistussuunnitelma"]]
                          :poikkeamis               [["paapiirustus" "asemapiirros"]
                                                     ["muut" "muu"]]
                          :meluilmoitus             [["kartat" "kartta-melun-ja-tarinan-leviamisesta"]
                                                     ["muut" "muu"]]
                          :yl-uusi-toiminta         [["muut" "muu"]]
                          :maa-aineslupa            [["muut" "muu"]]
                          :vvvl-vesijohdosta        [["muut" "muu"]]
                          :aloitusoikeus            [["paapiirustus" "asemapiirros"]]}
                         (-> (mongo/by-id :organizations "753-R" [:operations-attachments])
                             :operations-attachments))))
                (testing "can replace all"
                  (is (= {:ok true}
                         (command admin :import-export-import-items (-> import-args
                                                                        (assoc :replace? true)
                                                                        (assoc-in [:document :items] {"aloitusoikeus" [[:paapiirustus :asemapiirros]]})))))
                  (is (= {:aloitusoikeus [["paapiirustus" "asemapiirros"]]}
                         (-> (mongo/by-id :organizations "753-R" [:operations-attachments])
                             :operations-attachments))))))))))))
