(ns lupapalvelu.archive.archiving-itest
  (:require [clojure.test :refer :all]
            [lupapalvelu.archive.archiving-api :as arch-api]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.linked-files :as linked-files]
            [lupapalvelu.mongo :as mongo]
            [sade.core :refer [now]]))

(def db-name "test_archiving_linked_file_itest")

(use-fixtures :once (local-fixture-factory :db-name db-name))

(deftest permanent-archive-enabled-test
  (testing "query without parameters"
    (testing "normal authority"
      (testing "enabled"
        (is (ok? (query raktark-jarvenpaa :permanent-archive-enabled))))
      (testing "disabled"
        (is (unauthorized? (query sonja :permanent-archive-enabled)))))

    (testing "authorityAdmin"
      (testing "enabled"
        (is (ok? (query jarvenpaa :permanent-archive-enabled))))
      (testing "disabled"
        (is (unauthorized? (query sipoo :permanent-archive-enabled)))))

    (testing "applicant"
      (testing "disabled"
        (is (unauthorized? (query pena :permanent-archive-enabled)))))

    (testing "tos-editor and tos-publisher, but not authority in organization"
      (is (ok? (query torsti :permanent-archive-enabled)))))

  (testing "document-search-access-allowed"
    (testing "allowed for authority with permission when archive is enabled"
      (is (ok? (query raktark-jarvenpaa :document-search-access-allowed))))
    (testing "not allowed if archive is disabled"
      (is (unauthorized? (query sonja :document-search-access-allowed))))
    (testing "not allowed for applicant"
      (is (unauthorized? (query pena :document-search-access-allowed)))))

  (testing "toj-access-allowed"
    (testing "allowed for authority with permission when archive is enabled"
      (is (ok? (query raktark-jarvenpaa :toj-access-allowed))))
    (testing "not allowed if archive is disabled"
      (is (unauthorized? (query sonja :toj-access-allowed))))
    (testing "not allowed for applicant"
      (is (unauthorized? (query pena :toj-access-allowed)))))

  (testing "with application - archive enabled in municipality"
    (let [app-id (create-app-id pena :propertyId jarvenpaa-property-id :address "Arkistotie 13")]
      (testing "normal authority"
        (testing "enabled"
          (is (ok? (query raktark-jarvenpaa :permanent-archive-enabled :id app-id))))
        (testing "wrong municipality"
          (is (not-accessible? (query sonja :permanent-archive-enabled :id app-id)))))

      (testing "authorityAdmin"
        (testing "not allowed an application query"
          (is (not-accessible? (query jarvenpaa :permanent-archive-enabled :id app-id))))
        (testing "application not accessible"
          (is (not-accessible? (query sipoo :permanent-archive-enabled :id app-id)))))

      (testing "applicant"
        (testing "enabled"
          (is (ok? (query pena :permanent-archive-enabled :id app-id)))))))

  (testing "with application - archive not enabled in municipality"
    (let [app-id (create-app-id pena :propertyId sipoo-property-id :address "Arkistotie 13")]
      (testing "normal authority"
        (testing "wrong municipality"
          (is (not-accessible? (query raktark-jarvenpaa :permanent-archive-enabled :id app-id))))
        (testing "unauthorized"
          (is (unauthorized? (query sonja :permanent-archive-enabled :id app-id)))))

      (testing "authorityAdmin"
        (testing "application not accessible"
          (is (not-accessible? (query jarvenpaa :permanent-archive-enabled :id app-id))))
        (testing "application not accessible"
          (is (not-accessible? (query sipoo :permanent-archive-enabled :id app-id)))))

      (testing "applicant"
        (testing "not enabled"
          (is (unauthorized? (query pena :permanent-archive-enabled :id app-id))))))))

(defn make-org [enabled? in-use-since]
  (delay {:permanent-archive-enabled      enabled?
          :permanent-archive-in-use-since in-use-since}))

(def conv-app {:facta-imported true :created 1000 :submitted 100})
(def ark-app {:created 1000 :submitted 100 :permitType "ARK"})
(def app1 {:created 1000 :submitted 2000})
(def app2 {:created 1000})

(defn awtl [enabled? in-use-since app]
  (arch-api/application-within-time-limit {:organization (make-org enabled? in-use-since)
                                           :application  app}))
(def error (err :error.application-too-old-for-archival))

(def ats #'arch-api/application-ts)

(deftest application-ts-test
  (doseq [[a ts] [[nil nil] [{} nil] [conv-app 1000] [ark-app 1000]
                  [app1 2000] [app2 1000]]]
    (testing (str a " -> " ts)
      (is (= ts (ats a))))))

(deftest application-within-time-limit-test
  (testing "bad"
    (is (error (awtl false 1 app1)))
    (is (error (awtl true 1 nil)))

    (is (error (awtl true 5000 ark-app)))
    (is (error (awtl true 5000 app1)))
    (is (error (awtl true 5000 app2))))

  (testing "converted"
    (is (error (awtl true 5000 conv-app)))
    (is (nil? (awtl true 1000 conv-app))))

  (testing "digitized"
    (is (error (awtl true 5000 ark-app)))
    (is (nil? (awtl true 1000 ark-app))))

  (testing "regular: submitted"
    (is (error (awtl true 5000 app1)))
    (is (nil? (awtl true 2000 app1))))

  (testing "regular: created"
    (is (error (awtl true 5000 app2)))
    (is (error (awtl true 2000 app2)))
    (is (nil? (awtl true 1000 app2)))))

(deftest store-tos-metadata-for-linked-file-test
  (let [application    (create-and-submit-local-application raktark-jarvenpaa
                                                             :propertyId jarvenpaa-property-id
                                                             :address "TOS Metadata Test")
        application-id (:id application)
        operation-id   (-> application :primaryOperation :id)
        bim-file-id    (str (random-uuid))
        bim-version-id (str (random-uuid))
        bim-linked-file {:target-entity {:target-type    "bim-model-file"
                                         :file-id        bim-file-id
                                         :application-id application-id
                                         :operation-id   operation-id}
                         :metadata      {:name            "test-model.ifc"
                                         :model-type      "arkkitehti"
                                         :model-phase     "submission-time"
                                         :julkisuusluokka "julkinen"
                                         :henkilotiedot   "sisaltaa"
                                         :nakyvyys        "julkinen"}
                         :versions      [{:storage-system   "gcs"
                                          :file-version-id  bim-version-id
                                          :version          {:major 0 :minor 1}
                                          :bucket           "application-files"
                                          :target-file-path (str application-id "/" bim-version-id)
                                          :uploaded-at      (now)}]}]

    (try
      (mongo/insert linked-files/linked-file-metadata-collection bim-linked-file)

      (testing "BIM linked file was inserted"
        (is (some? (linked-files/find-one-linked-file-with-metadata {:file-id bim-file-id}))))

      (testing "TOS metadata can be stored for linked file"
        (is (ok? (local-command raktark-jarvenpaa :store-tos-metadata-for-linked-file
                                :id application-id
                                :fileId bim-file-id
                                :metadata {:julkisuusluokka "julkinen"
                                           :henkilotiedot   "ei-sisalla"
                                           :sailytysaika    {:arkistointi "ikuisesti"
                                                             :perustelu   "updated reason"}
                                           :myyntipalvelu   true
                                           :nakyvyys        "asiakas-ja-viranomainen"
                                           :kieli           "sv"}))))

      (testing "Metadata given by TOS can be updated and is stored correctly"
        (let [updated-file (linked-files/find-one-linked-file-with-metadata {:file-id bim-file-id})
              metadata     (:metadata updated-file)]
          (is (= "julkinen" (:julkisuusluokka metadata)))
          (is (= "ei-sisalla" (:henkilotiedot metadata)))         ; was "sisaltaa"
          (is (= "asiakas-ja-viranomainen" (:nakyvyys metadata))) ; was "julkinen"
          (is (= "sv" (:kieli metadata)))                         ; was nil
          (is (= true (:myyntipalvelu metadata)))                  ; was nil
          (is (= "updated reason" (get-in metadata [:sailytysaika :perustelu])))
          ;; File-specific fields should be preserved
          (is (= "test-model.ifc" (:name metadata)))
          (is (= "arkkitehti" (:model-type metadata)))
          (is (= "submission-time" (:model-phase metadata)))))

      (testing "File-specific fields are preserved"
        (is (ok? (local-command raktark-jarvenpaa :store-tos-metadata-for-linked-file
                                :id application-id
                                :fileId bim-file-id
                                :metadata {:julkisuusluokka "julkinen"
                                           :henkilotiedot   "ei-sisalla"
                                           :sailytysaika    {:arkistointi "ikuisesti"
                                                             :perustelu   "test"}
                                           :myyntipalvelu   false
                                           :nakyvyys        "julkinen"
                                           :kieli           "fi"
                                           ;; Attempt to overwrite file-specific fields
                                           :name            "hacked-name.ifc"
                                           :model-type      "rakenne"
                                           :model-phase     "construction-time"})))
        (let [updated-file (linked-files/find-one-linked-file-with-metadata {:file-id bim-file-id})
              metadata     (:metadata updated-file)]
          ;; File-specific fields should NOT be changed
          (is (= "test-model.ifc" (:name metadata)))
          (is (= "arkkitehti" (:model-type metadata)))
          (is (= "submission-time" (:model-phase metadata)))))

      (testing "Salassapito metadata can be stored"
        (is (ok? (local-command raktark-jarvenpaa :store-tos-metadata-for-linked-file
                                :id application-id
                                :fileId bim-file-id
                                :metadata {:julkisuusluokka     "salainen"
                                           :salassapitoaika     10
                                           :salassapitoperuste  "JulkL 24 § 1 mom"
                                           :suojaustaso         "ei-luokiteltu"
                                           :turvallisuusluokka  "ei-turvallisuusluokkaluokiteltu"
                                           :kayttajaryhma       "viranomaisryhma"
                                           :kayttajaryhmakuvaus "muokkausoikeus"
                                           :henkilotiedot       "sisaltaa"
                                           :sailytysaika        {:arkistointi "ikuisesti"
                                                                 :perustelu   "test"}
                                           :myyntipalvelu       false
                                           :nakyvyys            "viranomainen"
                                           :kieli               "fi"}))))

      (testing "Salassapito metadata is persisted correctly"
        (let [updated-file (linked-files/find-one-linked-file-with-metadata {:file-id bim-file-id})
              metadata     (:metadata updated-file)]
          (is (= 10 (:salassapitoaika metadata)))
          (is (= "JulkL 24 § 1 mom" (:salassapitoperuste metadata)))
          (is (= "ei-luokiteltu" (:suojaustaso metadata)))
          (is (= "ei-turvallisuusluokkaluokiteltu" (:turvallisuusluokka metadata)))
          (is (= "viranomaisryhma" (:kayttajaryhma metadata)))
          (is (= "muokkausoikeus" (:kayttajaryhmakuvaus metadata)))))

      (testing "Returns error for non-existent linked file"
        (is ((partial expected-failure? :error.linked-file-not-found)
             (local-command raktark-jarvenpaa :store-tos-metadata-for-linked-file
                            :id application-id
                            :fileId "non-existent-file-id"
                            :metadata {:julkisuusluokka "julkinen"}))))

      (finally
        (mongo/remove-many linked-files/linked-file-metadata-collection
                           {:target-entity.application-id application-id})))))
