(ns lupapalvelu.bim.api-itest
  (:require [clojure.edn :as edn]
            [clojure.test :as t]
            [lupapalvelu.bim.bim-extracted-data :as bim-extracted-data]
            [lupapalvelu.bim.bimmer.bimmer-to-lp :as btl]
            [lupapalvelu.bim.core :as bim]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.itest-util :as itest]
            [lupapalvelu.linked-files :as linked-files]
            [lupapalvelu.mongo :as mongo]
            [matcher-combinators.test :refer [match?]]))

(t/use-fixtures :once (itest/local-fixture-factory))

(def ryhti-start-ts 1000000000000)
(def file-uploaded-ts (+ ryhti-start-ts 1000))

(def org-id "753-R")
(def admin itest/admin)
(def authority itest/sonja)
(def property-id itest/sipoo-property-id)
(def mocked-job {:id "mocked-job"})

(defn- set-org-flag [flag value]
  (t/is (itest/ok? (itest/command admin :set-organization-boolean-attribute
                                  :enabled value
                                  :organizationId org-id
                                  :attribute (name flag)))))

(defn- check-model-command [id check-type]
  (itest/command authority :check-model
                 :id id
                 :check-type check-type
                 :operation-id "operation-id"
                 :file-id "file-id"
                 :bucket "bucket"
                 :target-file-path "target-file-path"
                 :model-type "architectural"))

(t/deftest check-model-organization-flags-test
  ;; This does not test the actual model check, only whether the endpoint can be
  ;; called or not with certain parameters and organization flags
  (with-redefs [bim/make-checking-job (constantly mocked-job)]
    (let [{:keys [id]} (itest/create-app authority
                                         :propertyId property-id
                                         :operation "kerrostalo-rivitalo")]
      (t/is (string? id))
      (t/testing "Check-model cannot be called with incorrect check-type"
        (t/is (itest/expected-failure? :error.illegal-value:schema-validation
                                       (check-model-command id "validation"))))
      (t/testing "Validation test can be run, but compliance test will fail"
        (set-org-flag :bim-compliance-check-enabled false)
        (t/is (= {:ok true :job mocked-job} (check-model-command id "ids")))
        (t/is (itest/expected-failure? :error.bim-compliance-check-enabled.incorrect-value
                                       (check-model-command id "compliance"))))
      (t/testing "Both checks can be run"
        (set-org-flag :bim-compliance-check-enabled true)
        (t/is (= {:ok true :job mocked-job} (check-model-command id "ids")))
        (t/is (= {:ok true :job mocked-job} (check-model-command id "compliance")))))))

(def example-bimmer-response
  (-> (slurp "dev-resources/bim/example-bimmer-parsed-ifc-response.edn")
      edn/read-string))

(def test-schema
  (schemas/get-schema (schemas/get-latest-schema-version) "uusiRakennuskohde"))

(t/deftest ifc-data-lifecycle-test
  ;; Ensure ryhti is enabled
  (itest/command admin :set-organization-ryhti-data-2026-start-ts
                 :organizationId org-id
                 :ryhti-data-2026-start-ts ryhti-start-ts)
  (let [app-id          (:id (itest/create-app authority
                                               :propertyId property-id
                                               :operation "kerrostalo-rivitalo"))
        operation-id    (-> (itest/query authority :application :id app-id)
                            :application :primaryOperation :id)
        file-version-id (str (random-uuid))
        linked-ifc-file {:target-entity {:target-type    "bim-model-file"
                                         :file-id        (str (random-uuid))
                                         :application-id app-id
                                         :operation-id   operation-id}
                         :metadata      {:name            "test-model.ifc"
                                         :model-type      "arkkitehti"
                                         :model-phase     "submission-time"
                                         :julkisuusluokka "julkinen"
                                         :henkilotiedot   "ei-sisalla"
                                         :nakyvyys        "julkinen"}
                         :versions      [{:storage-system   "gcs"
                                          :file-version-id  file-version-id
                                          :version          {:major 0 :minor 1}
                                          :bucket           "application-files"
                                          :target-file-path (str app-id "/" file-version-id)
                                          :uploaded-at      file-uploaded-ts}]}]
    (mongo/insert linked-files/linked-file-metadata-collection linked-ifc-file)

    (t/testing "returns empty set when no ifc-data snapshot exists for the linked file"
      (t/is (= {:ok true :file-version-ids #{}}
               (itest/query authority :ifc-data-file-version-ids :id app-id))))

    (t/testing "fetch-ifc-doc returns error when no ifc-data snapshot exists"
      (t/is (itest/expected-failure? :error.not-found
                                     (itest/query authority :fetch-ifc-doc
                                                  :id app-id
                                                  :file-version-id file-version-id))))

    (bim-extracted-data/save-ifc-data-snapshot! test-schema
                                                {:linked-metadata-file-version-id file-version-id
                                                 :query                           (btl/psets-query)
                                                 :properties                      (:properties example-bimmer-response)})

    (t/testing "returns the file-version-id once snapshot has been saved"
      (t/is (= {:ok true :file-version-ids #{file-version-id}}
               (itest/query authority :ifc-data-file-version-ids :id app-id))))

    (let [{:keys [ok ifc-doc app-doc]} (itest/query authority :fetch-ifc-doc
                                                    :id app-id
                                                    :file-version-id file-version-id)
          app-doc-id                   (:id app-doc)]
      (t/testing "fetch-ifc-doc returns ifc-doc with schema and app-doc when snapshot exists"
        (t/is (true? ok))
        (t/is (match? {:schema-info any?
                       :data        any?
                       :schema      {:info {:name "uusiRakennuskohde"}
                                     :body any?}}
                      ifc-doc))
        (t/is (match? {:schema-info {:name "uusiRakennuskohde"}
                       :data        any?}
                      app-doc)))

      (t/testing "apply-ifc-model-data fails when docId does not exist in the application"
        (t/is (itest/expected-failure? :error.document-not-found
                                       (itest/command authority :apply-ifc-model-data
                                                      :id app-id
                                                      :docId (str (random-uuid))
                                                      :file-version-id file-version-id))))

      (t/testing "apply-ifc-model-data fails with document-not-found when linked file has no operation-id"
        (let [no-op-version-id  (str (random-uuid))
              no-op-linked-file {:target-entity {:target-type    "bim-model-file"
                                                 :file-id        (str (random-uuid))
                                                 :application-id app-id}
                                 ;; no :operation-id
                                 :metadata      {:name            "no-op-model.ifc"
                                                 :model-type      "arkkitehti"
                                                 :model-phase     "submission-time"
                                                 :julkisuusluokka "julkinen"
                                                 :henkilotiedot   "ei-sisalla"
                                                 :nakyvyys        "julkinen"}
                                 :versions      [{:storage-system   "gcs"
                                                  :file-version-id  no-op-version-id
                                                  :version          {:major 0 :minor 1}
                                                  :bucket           "application-files"
                                                  :target-file-path (str app-id "/" no-op-version-id)
                                                  :uploaded-at      file-uploaded-ts}]}]
          (mongo/insert linked-files/linked-file-metadata-collection no-op-linked-file)
          (bim-extracted-data/save-ifc-data-snapshot! test-schema
                                                      {:linked-metadata-file-version-id no-op-version-id
                                                       :query                           (btl/psets-query)
                                                       :properties                      (:properties example-bimmer-response)})
          (t/is (itest/expected-failure? :error.document-not-found
                                         (itest/command authority :apply-ifc-model-data
                                                        :id app-id
                                                        :docId app-doc-id
                                                        :file-version-id no-op-version-id)))
          ;; Clean up so the orphan snapshot doesn't affect subsequent tests
          (mongo/remove linked-files/linked-file-metadata-collection {:versions.file-version-id no-op-version-id})
          (bim-extracted-data/delete-ifc-data-snapshots! [no-op-version-id])))

      (t/testing "apply-ifc-model-data fails when docId refers to a different document than the BIM-linked one"
        (let [other-doc-id (->> (itest/query authority :application :id app-id)
                                :application
                                :documents
                                (remove #(= app-doc-id (:id %)))
                                first
                                :id)]
          (t/is (itest/expected-failure? :error.bim-ifc-doc-does-not-match-app-doc
                                         (itest/command authority :apply-ifc-model-data
                                                        :id app-id
                                                        :docId other-doc-id
                                                        :file-version-id file-version-id)))))

      (t/testing "apply-ifc-model-data fails when file-version-id has no snapshot"
        (t/is (itest/expected-failure? :error.not-found
                                       (itest/command authority :apply-ifc-model-data
                                                      :id app-id
                                                      :docId app-doc-id
                                                      :file-version-id "non-existent-version-id"))))

      (t/testing "apply-ifc-model-data succeeds and updates the application document"
        (let [get-current-rakennuskohde-doc-fn (fn []
                                                 (->> (itest/query authority :application :id app-id)
                                                      :application
                                                      :documents
                                                      (filter #(= "uusiRakennuskohde" (-> % :schema-info :name)))
                                                      first
                                                      :data))
              doc-data-before                  (get-current-rakennuskohde-doc-fn)]
          (t/is (itest/ok? (itest/command authority :apply-ifc-model-data
                                          :id app-id
                                          :docId app-doc-id
                                          :file-version-id file-version-id)))
          (let [doc-data-after (get-current-rakennuskohde-doc-fn)]
            (t/is (not= doc-data-before doc-data-after))

            (t/testing "readonly fields (e.g. postitoimipaikannimi) do not cause error-trying-to-update-readonly-field"
              ;; The IFC data includes Postitoimipaikka which maps to the :readonly postitoimipaikannimi
              ;; field. The command must succeed (not throw error-trying-to-update-readonly-field).
              ;; The field should be absent from the stored data since it was filtered before writing.
              (t/is (nil? (get-in doc-data-after [:sijaintitiedot :osoite :postitoimipaikannimi :value]))
                    "readonly postitoimipaikannimi should not be written to the document"))

            (t/testing "writable IFC fields are applied to the document"
              (t/is (= "Myllytie 7" (get-in doc-data-after [:sijaintitiedot :osoite :katu :value]))
                    "writable katu field should be written from IFC Katuosoite")
              (t/is (= "04410" (get-in doc-data-after [:sijaintitiedot :osoite :postinumero :value]))
                    "writable postinumero field should be written from IFC Postinumero"))))))

    (t/testing "deleting the linked ifc file also deletes the ifc-data snapshot"
      (itest/command authority :delete-linked-file
                     :id app-id
                     :file-id (-> linked-ifc-file :target-entity :file-id))
      (t/is (= {:ok true :file-version-ids #{}}
               (itest/query authority :ifc-data-file-version-ids :id app-id)))
      (t/is (itest/expected-failure? :error.not-found
                                     (itest/query authority :fetch-ifc-doc
                                                  :id app-id
                                                  :file-version-id file-version-id))))))
