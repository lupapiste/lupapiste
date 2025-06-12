(ns lupapalvelu.integrations-api-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.backing-system.security :as bs-sec]
            [lupapalvelu.integrations-api]
            [lupapalvelu.integrations.document-export :as doc-export]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.test-util :refer [err expected-failure?]]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [monger.operators :refer [$inc $set]]))

(testable-privates lupapalvelu.integrations-api
                   ensure-general-handler-is-set building-extinction-db-updates
                   ensure-bulletin-op-description-is-set-if-needed)

(facts ensure-general-handler-is-set
  (fact "empty handlers"
    (ensure-general-handler-is-set []
                                   {:id ..user-id.. :firstName ..firstName.. :lastName ..lastName..}
                                   {:handler-roles [{:id ..role-id.. :name ..handler.. :general true}]})
    => [{:id ..handler-id.. :userId ..user-id.. :firstName ..firstName.. :lastName ..lastName.. :roleId ..role-id..}]
    (provided (mongo/create-id) => ..handler-id..))

  (fact "nil handlers"
    (ensure-general-handler-is-set nil
                                   {:id ..user-id.. :firstName ..firstName.. :lastName ..lastName..}
                                   {:handler-roles [{:id ..role-id.. :name ..handler.. :general true}]})
    => [{:id ..handler-id.. :userId ..user-id.. :firstName ..firstName.. :lastName ..lastName.. :roleId ..role-id..}]
    (provided (mongo/create-id) => ..handler-id..))

  (fact "existing general handler"
    (ensure-general-handler-is-set [{:id ..handler-id.. :roleId ..role-id.. :userId ..user-id.. :firstName ..firstName.. :lastName ..lastName..}]
                                   {:id ..other-user-id.. :firstName ..other-firstName.. :lastName ..other-lastName..}
                                   {:handler-roles [{:id ..role-id.. :name ..handler.. :general true}]})
    => [{:id ..handler-id.. :userId ..user-id.. :firstName ..firstName.. :lastName ..lastName.. :roleId ..role-id..}])

  (fact "existing non-general handler"
    (ensure-general-handler-is-set [{:id ..other-handler-id.. :roleId ..other-role-id.. :userId ..other-user-id.. :firstName ..other-firstName.. :lastName ..other-lastName..}]
                                   {:id ..user-id.. :firstName ..firstName.. :lastName ..lastName..}
                                   {:handler-roles [{:id ..other-role-id.. :name ..other-handler..}
                                                    {:id ..role-id.. :name ..handler.. :general true}]})
    => [{:id ..other-handler-id.. :roleId ..other-role-id.. :userId ..other-user-id.. :firstName ..other-firstName.. :lastName ..other-lastName..}
        {:id ..handler-id.. :roleId ..role-id.. :userId ..user-id.. :firstName ..firstName.. :lastName ..lastName..}]
    (provided (mongo/create-id) => ..handler-id..))

  (fact "empty handlers - no general handler in organization"
    (ensure-general-handler-is-set []
                                   {:id ..user-id.. :firstName ..firstName.. :lastName ..lastName..}
                                   {:handler-roles [{:id "role-id" :name ..handler..}]})
    => []))


(facts "building-extinction-db-updates"
  (let [command {:created 123
                 :application {:primaryOperation {:id "primary-op"}
                               :secondaryOperations [{:id "secondary-op-1"}
                                                     {:id "secondary-op-2"}]}}]
    (fact "invalid operation id"
      (building-extinction-db-updates command "invalid-id" 1575158400000)
      => (partial expected-failure? "error.invalid-request"))

    (facts "primary operation"
      (fact "$set"
        (building-extinction-db-updates command "primary-op" 1575158400000) ;; 1.12.2019
        => {"$set" {:modified 123
                    :primaryOperation.extinct 1575158400000}})

      (fact "$unset"
        (building-extinction-db-updates command "primary-op" nil) ;; 1.12.2019
        => {"$set" {:modified 123}
            "$unset" {:primaryOperation.extinct true}}))

    (fact "secondary operation"
      (fact "$set"
        (building-extinction-db-updates command "secondary-op-1" 1575158400000) ;; 1.12.2019
        => {"$set" {:modified 123
                    "secondaryOperations.0.extinct" 1575158400000}})

      (fact "$unset"
        (building-extinction-db-updates command "secondary-op-2" nil) ;; 1.12.2019
        => {"$set" {:modified 123}
            "$unset" {"secondaryOperations.1.extinct" true}}))))


(facts "ensure-bulletin-op-description-is-set-if-needed"
  (fact "description required"
    (ensure-bulletin-op-description-is-set-if-needed
      {:application  {:permitType "R" :municipality "753"}
       :organization (delay {:scope [{:permitType   "R"
                                      :municipality "753"
                                      :bulletins    {:enabled true}}]})})
    => (err :error.invalid-value))
  (fact "not required"
    (fact "not enabled"
      (ensure-bulletin-op-description-is-set-if-needed
        {:application  {:permitType "R" :municipality "753"}
         :organization (delay {:scope [{:permitType   "R"
                                        :municipality "753"}]})})
      => nil)
    (fact "no application"
      (ensure-bulletin-op-description-is-set-if-needed
        {:organization (delay {:scope [{:permitType   "R"
                                        :municipality "753"
                                        :bulletins    {:enabled true}}]})})
      => nil)
    (fact "descriptions are taken from the backing system"
      (ensure-bulletin-op-description-is-set-if-needed
        {:application  {:permitType "R" :municipality "753"}
         :organization (delay {:scope [{:permitType   "R"
                                        :municipality "753"
                                        :bulletins    {:enabled                          true
                                                       :descriptions-from-backend-system true}}]})})
      => nil)
    (fact "description has been given"
      (ensure-bulletin-op-description-is-set-if-needed
        {:application  {:permitType "R" :municipality "753"
                        :bulletinOpDescription "Whatever"}
         :organization (delay {:scope [{:permitType   "R"
                                        :municipality "753"
                                        :bulletins    {:enabled true}}]})})
      => nil)
    (fact "YA"
      (ensure-bulletin-op-description-is-set-if-needed
        {:application  {:permitType "YA" :municipality "753"}
         :organization (delay {:scope [{:permitType   "YA"
                                        :municipality "753"
                                        :bulletins    {:enabled true}}]})})
      => nil)

    (doseq [op ["tyonjohtajan-nimeaminen-v2" "suunnittelijan-nimeaminen"
                "kiinteistonmuodostus"]]
      (fact {:midje/description (str "Primary operation is " op)}
        (ensure-bulletin-op-description-is-set-if-needed
          {:application  {:permitType       "R" :municipality "753"
                          :primaryOperation {:name op}}
           :organization (delay {:scope [{:permitType   "R"
                                          :municipality "753"
                                          :bulletins    {:enabled true}}]})})
        => nil))
    (fact "dmcity enabled"
      (ensure-bulletin-op-description-is-set-if-needed
        {:application  {:permitType "R" :municipality "753"}
         :organization (delay {:scope  [{:permitType   "R"
                                         :municipality "753"
                                         :bulletins    {:enabled true}}]
                               :dmcity (merge {:url     "https.//example.net"
                                               :vault   "test-vault"
                                               :targets {:R     {:enabled true}
                                                         :state {:enabled true}}}
                                              (bs-sec/encode-credentials "test-user" "test-secret"))})})
      => nil))
  (fact "dmcity not enabled"
      (ensure-bulletin-op-description-is-set-if-needed
        {:application  {:permitType "R" :municipality "753"}
         :organization (delay {:scope  [{:permitType   "R"
                                         :municipality "753"
                                         :bulletins    {:enabled true}}]
                               :dmcity (merge {:url     "https.//example.net"
                                               :vault   "test-vault"
                                               :targets {:P     {:enabled true}
                                                         :state {:enabled true}}}
                                              (bs-sec/encode-credentials "test-user" "test-secret"))})})
      => (err :error.invalid-value)))


(facts "doc-export/linked-file->pseudo-attachment"
  (let [bim-linked-file {:target-entity {:target-type    "bim-model-file"
                                         :file-id        "test-file-id-123"
                                         :application-id "LP-753-2024-00001"
                                         :operation-id   "op-id-456"}
                         :metadata      {:name       "test-model.ifc"
                                         :model-type "architectural"}
                         :versions      [{:storage-system   "gcs"
                                          :file-version-id  "version-id-789"
                                          :version          {:major 0 :minor 1}
                                          :bucket           "application-files"
                                          :target-file-path "LP-753-2024-00001/version-id-789"
                                          :uploaded-by      {:id        "user-id"
                                                             :username  "sonja"
                                                             :firstName "Sonja"
                                                             :lastName  "Sibbo"
                                                             :role      "authority"}
                                          :metadata         {:name         "test-model.ifc"
                                                             :size         10000
                                                             :content-type "application/step"}
                                          :uploaded-at      1234567890}]}]

    (fact "converts linked file to pseudo-attachment format"
      (let [pseudo-att (doc-export/linked-file->pseudo-attachment bim-linked-file)]
        (fact "id is the file-id from target-entity"
          (:id pseudo-att) => "test-file-id-123")

        (fact "latestVersion contains file-version-id as fileId"
          (get-in pseudo-att [:latestVersion :fileId]) => "version-id-789")

        (fact "latestVersion contains filename from version metadata"
          (get-in pseudo-att [:latestVersion :filename]) => "test-model.ifc")

        (fact "preserves original linked-file data"
          (:linked-file pseudo-att) => bim-linked-file)

        (fact "type is set based on target-type and model-type"
          (:type pseudo-att) => truthy)

        (fact "metadata is empty when no metadata is set"
          (:metadata pseudo-att) => {})))

    (fact "handles multiple versions - uses latest by uploaded-at"
      (let [multi-version-file (assoc bim-linked-file
                                      :versions [{:storage-system   "gcs"
                                                  :file-version-id  "old-version-id"
                                                  :version          {:major 0 :minor 1}
                                                  :uploaded-at      1000000000
                                                  :metadata         {:name "old-model.ifc"}}
                                                 {:storage-system   "gcs"
                                                  :file-version-id  "new-version-id"
                                                  :version          {:major 0 :minor 2}
                                                  :uploaded-at      2000000000
                                                  :metadata         {:name "new-model.ifc"}}])
            pseudo-att (doc-export/linked-file->pseudo-attachment multi-version-file)]
        (fact "uses the latest version's file-version-id"
          (get-in pseudo-att [:latestVersion :fileId]) => "new-version-id")
        (fact "uses the latest version's filename"
          (get-in pseudo-att [:latestVersion :filename]) => "new-model.ifc")))

    (fact "handles empty versions array - returns nil fileId and filename"
      ;; This documents current behavior. In practice, the frontend filters out
      ;; BIM files without versions (see ::sendable-bim-files subscription),
      ;; so this case should not occur in normal usage.
      (let [no-versions-file (assoc bim-linked-file :versions [])
            pseudo-att       (doc-export/linked-file->pseudo-attachment no-versions-file)]
        (fact "id is still set from target-entity"
          (:id pseudo-att) => "test-file-id-123")
        (fact "latestVersion has nil fileId"
          (get-in pseudo-att [:latestVersion :fileId]) => nil)
        (fact "latestVersion has nil filename"
          (get-in pseudo-att [:latestVersion :filename]) => nil)
        (fact "still has linked-file reference"
          (:linked-file pseudo-att) => no-versions-file)))

    (fact "handles nil versions - returns nil fileId and filename"
      (let [nil-versions-file (assoc bim-linked-file :versions nil)
            pseudo-att        (doc-export/linked-file->pseudo-attachment nil-versions-file)]
        (fact "latestVersion has nil fileId"
          (get-in pseudo-att [:latestVersion :fileId]) => nil)
        (fact "latestVersion has nil filename"
          (get-in pseudo-att [:latestVersion :filename]) => nil)))))

(deftest model-phase->application-state-test
  (testing "construction-time maps to verdictGiven (post-verdict)"
    (is (= "verdictGiven" (doc-export/model-phase->application-state "construction-time"))))
  (testing "completion-time maps to verdictGiven (post-verdict)"
    (is (= "verdictGiven" (doc-export/model-phase->application-state "completion-time"))))
  (testing "submission-time maps to submitted (pre-verdict)"
    (is (= "submitted" (doc-export/model-phase->application-state "submission-time"))))
  (testing "nil returns nil"
    (is (nil? (doc-export/model-phase->application-state nil)))))

;; clojure.test tests for build-document-export-updates

(def ^:private build-document-export-updates
  doc-export/build-document-export-updates)

(deftest build-document-export-updates-test
  (let [app-id  "LP-753-2024-00001"
        created 1700000000000]

    (testing "application PDF only"
      (let [result (build-document-export-updates app-id [(str app-id "-application")] created)]
        (is (= created (get-in result [$set "metadata.sent"])))
        (is (= 1 (get-in result [$inc "metadata.export-version"])))
        (is (nil? (get-in result [$set "processMetadata.sent"])))
        (is (nil? (get-in result [$inc "processMetadata.export-version"])))))

    (testing "case-file PDF only"
      (let [result (build-document-export-updates app-id [(str app-id "-case-file")] created)]
        (is (nil? (get-in result [$set "metadata.sent"])))
        (is (= created (get-in result [$set "processMetadata.sent"])))
        (is (= 1 (get-in result [$inc "processMetadata.export-version"])))))

    (testing "case-file XML only"
      (let [result (build-document-export-updates app-id [(str app-id "-case-file-xml")] created)]
        (is (nil? (get-in result [$set "metadata.sent"])))
        (is (= created (get-in result [$set "processMetadata.sent"])))
        (is (= 1 (get-in result [$inc "processMetadata.export-version"])))))

    (testing "both case-file PDF and XML — processMetadata incremented once"
      (let [result (build-document-export-updates app-id
                                                   [(str app-id "-case-file")
                                                    (str app-id "-case-file-xml")]
                                                   created)]
        (is (= 1 (get-in result [$inc "processMetadata.export-version"])))
        (is (= created (get-in result [$set "processMetadata.sent"])))))

    (testing "all three documents"
      (let [result (build-document-export-updates app-id
                                                   [(str app-id "-application")
                                                    (str app-id "-case-file")
                                                    (str app-id "-case-file-xml")]
                                                   created)]
        (is (= created (get-in result [$set "metadata.sent"])))
        (is (= 1 (get-in result [$inc "metadata.export-version"])))
        (is (= created (get-in result [$set "processMetadata.sent"])))
        (is (= 1 (get-in result [$inc "processMetadata.export-version"])))))

    (testing "empty sent list returns empty map"
      (is (= {} (build-document-export-updates app-id [] created))))))

;; clojure.test tests for build-generated-documents

(def ^:private build-generated-documents
  doc-export/build-generated-documents)

(def ^:private test-application
  {:id          "LP-753-2024-00001"
   :metadata    {:julkisuusluokka :julkinen
                 :henkilotiedot   :ei-sisalla}
   :processMetadata {:julkisuusluokka :julkinen}})

(deftest build-generated-documents-test
  (let [app-id  "LP-753-2024-00001"
        command {:application test-application}]

    (testing "application document"
      (let [docs (build-generated-documents command #{(str app-id "-application")} "fi")]
        (is (= 1 (count docs)))
        (let [{:keys [id filename canonical content-fn]} (first docs)]
          (is (= (str app-id "-application") id))
          (is (= (str app-id "-application.pdf") filename))
          (is (fn? content-fn))
          (let [liite (:Liite canonical)]
            (is (= "Hakemus" (:kuvaus liite)))
            (is (= (str app-id "-application.pdf") (:linkkiliitteeseen liite)))
            (is (= "1.0" (:versionumero liite)))
            (is (= "hakemus" (:tyyppi liite)))
            (is (string? (:muokkausHetki liite)))
            (is (some #(= "julkisuusluokka" (get-in % [:metatieto :metatietoNimi]))
                      (:metatietotieto liite)))
            (is (= "julkinen" (->> (:metatietotieto liite)
                                   (some #(when (= "julkisuusluokka"
                                                   (get-in % [:metatieto :metatietoNimi]))
                                            (get-in % [:metatieto :metatietoArvo]))))))
            (is (= "ei-sisalla" (->> (:metatietotieto liite)
                                     (some #(when (= "henkilotiedot"
                                                     (get-in % [:metatieto :metatietoNimi]))
                                              (get-in % [:metatieto :metatietoArvo]))))))))))

    (testing "case-file document"
      (let [docs (build-generated-documents command #{(str app-id "-case-file")} "fi")]
        (is (= 1 (count docs)))
        (let [{:keys [id filename canonical]} (first docs)]
          (is (= (str app-id "-case-file") id))
          (is (= (str app-id "-case-file.pdf") filename))
          (let [liite (:Liite canonical)]
            (is (= "Käsittelypöytäkirja" (:kuvaus liite)))
            (is (= "käsittelypöytäkirja" (:tyyppi liite)))))))

    (testing "case-file XML document"
      (let [docs (build-generated-documents command #{(str app-id "-case-file-xml")} "fi")]
        (is (= 1 (count docs)))
        (let [{:keys [id filename canonical]} (first docs)]
          (is (= (str app-id "-case-file-xml") id))
          (is (= (str app-id "-case-file.xml") filename))
          (let [liite (:Liite canonical)]
            (is (= "Käsittelypöytäkirja (XML)" (:kuvaus liite)))
            (is (= (str app-id "-case-file.xml") (:linkkiliitteeseen liite)))))))

    (testing "all three documents selected"
      (let [doc-ids #{(str app-id "-application")
                      (str app-id "-case-file")
                      (str app-id "-case-file-xml")}
            docs    (build-generated-documents command doc-ids "fi")]
        (is (= 3 (count docs)))
        (is (= doc-ids (set (map :id docs))))))

    (testing "unknown document IDs are ignored"
      (let [docs (build-generated-documents command #{"bogus-id"} "fi")]
        (is (empty? docs))))

    (testing "empty set returns empty vector"
      (is (empty? (build-generated-documents command #{} "fi"))))))
