(ns lupapalvelu.linked-files-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.linked-files :as target]
            [lupapalvelu.storage.file-storage :as storage]
            [schema.core :as sc]
            [sade.core :refer [now]]))


(facts "linked-file-target->file-link-strategy maps target-entity to target bucket"
  (fact "operations-location-file are stored into application file bucket"
    (target/linked-file-target->file-link-strategy {:target-type    "operations-location-file"
                                                    :application-id "LP-753-2022-90012"})
    => {:target-bucket "application-files" :target-file-dir "LP-753-2022-90012"}))

(def test-user {:id        "777777777777777777000023",
                :username  "sonja",
                :firstName "Sonja",
                :lastName  "Sibbo",
                :role      "authority"})
(def old-file-version-id "844308d0-5525-11ed-b0bf-45f61ad3c9a3")
(def test-file-doc
  {:_id           "635920efef7f20e726529034",
   :metadata      {:name       "model-1mt-test.ifc"
                   :model-type "arkkitehtimalli"},
   :target-entity {:application-id "LP-753-2022-90130",
                   :file-id        "8442e1c0-5525-11ed-b0bf-45f61ad3c9a3",
                   :operation-id   "632b1b0a957ed9019ea68e10",
                   :target-type    "operations-location-file"}
   :versions      [{:storage-system   "gcs",
                    :file-version-id  "844308d0-5525-11ed-b0bf-45f61ad3c9a3",
                    :version          {:major 0 :minor 1}
                    :bucket           "application-files",
                    :target-file-path "LP-753-2022-90130/844308d0-5525-11ed-b0bf-45f61ad3c9a3",
                    :uploaded-by      {:id        "777777777777777777000023",
                                       :username  "sonja",
                                       :firstName "Sonja",
                                       :lastName  "Sibbo",
                                       :role      "authority"}
                    :metadata         {:name         "model-1mt-test.ifc"
                                       :size         10000
                                       :content-type "application/step"}
                    :uploaded-at      1666785519159}]})

(fact "test-file is valid linked-file-metadata"
  (sc/validate target/LinkedFileEntity test-file-doc) => test-file-doc)

(fact "version-exists?"
  (target/version-exists? test-file-doc "844308d0-5525-11ed-b0bf-45f61ad3c9a3") => true
  (target/version-exists? test-file-doc "844308d0-5525-11ed-b0bf-45f61ad3c000") => nil)

(def new-version-metadata
  {:name         "model-1mt-test-2.ifc"
   :size         10000
   :content-type "application/step"})

(def new-file-version-id "844308d0-5525-11ed-b0bf-45f61ad3c000")
(def expected-new-file-version {:storage-system   :gcs,
                                :file-version-id  new-file-version-id,
                                :version          {:major 0 :minor 2}
                                :bucket           "mapped-application-files",
                                :target-file-path "mapped-path",
                                :uploaded-by      {:id        "777777777777777777000023",
                                                   :username  "sonja",
                                                   :firstName "Sonja",
                                                   :lastName  "Sibbo",
                                                   :role      "authority"}
                                :metadata         new-version-metadata
                                :uploaded-at      1666785519200})

(def expected-new-test-file-doc (update test-file-doc :versios conj expected-new-file-version))

(fact "->file-version generates new version"
  (target/->file-version new-file-version-id
                         test-file-doc
                         "mapped-path"
                         "mapped-application-files"
                         test-user
                         {:name         "model-1mt-test-2.ifc"
                          :size         10000
                          :content-type "application/step"})
  => expected-new-file-version
  (provided (now) => 1666785519200))

(facts "gen-linked-file-mongo-query"
  (let [test-cases [{:case     "with file-id"
                     :input    {:file-id "635920efef7f20e726529034"}
                     :expected {"$and" [{:target-entity.file-id "635920efef7f20e726529034"}]}}
                    {:case     "with file-id and application-id"
                     :input    {:file-id "635920efef7f20e726529034" :application-id "LP-753-2022-90130"}
                     :expected {"$and" [{:target-entity.file-id "635920efef7f20e726529034"}
                                        {:target-entity.application-id "LP-753-2022-90130"}]}}
                    {:case     "with file-id and application-id and version-id"
                     :input    {:file-id                  "635920efef7f20e726529034" :application-id "LP-753-2022-90130"
                                :versions/file-version-id "844308d0-5525-11ed-b0bf-45f61ad3c9a3"}
                     :expected {"$and" [{"$and" [{:target-entity.file-id "635920efef7f20e726529034"}
                                                 {:target-entity.application-id "LP-753-2022-90130"}]}
                                        {"$elemMatch" {:file-version-id "844308d0-5525-11ed-b0bf-45f61ad3c9a3"}}]}}]]
    (doseq [{:keys [case input expected]} test-cases]
      (fact {:midje/description case}
        (target/gen-linked-file-mongo-query input) => expected))))

(facts "ensure-file-version
         1. It moves first unlinked file to the linked file folder.
         2. Then it upserts new linked file into mongo.

       Ensure-file-version won't generate any new id's. Currently, the needed file-id and file-version-id are generated by
       init-resumable-upload command."
  (let [fake-query {:some-fake-query true}]
    (fact "Happy case 1: If a linked-file-metadata document is no found, create new and insert it to mongo.
           The uploaded file will be it's first version."
      (target/ensure-file-version new-file-version-id
                                  (:target-entity test-file-doc)
                                  test-user
                                  {:version-metadata new-version-metadata})
      => {:ok true :result :added-doc-and-version}
      (provided (now) => 1666785519200
                (target/find-one-linked-file-with-metadata (:target-entity test-file-doc) :include-query true)
                => {:query  fake-query :result nil}
                (storage/move-unlinked-file-to "application-files" (:id test-user) "LP-753-2022-90130" new-file-version-id)
                => {:result :did-move :bucket "mapped-bucket" :file-path "mapped-path"}
                (target/upsert-linked-file anything anything) => expected-new-test-file-doc))

    (fact "Happy case 2: When an existing document is found, append new version into it"
      (target/ensure-file-version new-file-version-id
                                  (:target-entity test-file-doc)
                                  test-user
                                  {:version-metadata new-version-metadata})
      => {:ok true :result :added-version-to-existing-doc}
      (provided (now) => 1666785519200
                (target/find-one-linked-file-with-metadata (:target-entity test-file-doc) :include-query true)
                => {:query  fake-query :result test-file-doc}
                (storage/move-unlinked-file-to "application-files" (:id test-user) "LP-753-2022-90130" new-file-version-id)
                => {:result :did-move :bucket "mapped-bucket" :file-path "mapped-path"}
                (target/upsert-linked-file anything anything) => expected-new-test-file-doc))

    (fact "Happy case 3: When the version with file-version-id exist already in linked-file-metadata document,
           do nothing. This is potentially a kind of error, but not a fatal one. In this case, we assume that the
           ensure-file-version was mistakenly called twice with same paramaters and everyhing is done."
      (target/ensure-file-version old-file-version-id
                                  (:target-entity expected-new-test-file-doc)
                                  test-user
                                  {:version-metadata new-version-metadata})
      => {:ok true :result :version-was-already-linked}
      (provided (now) => 1666785519200
                (target/find-one-linked-file-with-metadata (:target-entity expected-new-test-file-doc) :include-query true)
                => {:query  fake-query :result test-file-doc}
                (storage/move-unlinked-file-to "application-files" (:id test-user) "LP-753-2022-90130" old-file-version-id)
                => {:result :already-moved}))

    (fact "Error case 1: When file is not return file-with-id-not-found error"
      (target/ensure-file-version new-file-version-id
                                  (:target-entity test-file-doc)
                                  test-user
                                  {:version-metadata new-version-metadata})
      => {:ok false :text "error.file-with-id-not-found"}
      (provided (now) => 1666785519200
                (target/find-one-linked-file-with-metadata (:target-entity test-file-doc) :include-query true)
                => {:query  fake-query :result test-file-doc}
                (storage/move-unlinked-file-to "application-files" (:id test-user) "LP-753-2022-90130" new-file-version-id)
                => {:result :not-found}))

    (fact "Error case 2: When file is moved but metadata does not exist, something has gone badly wrong earlier.
           Most likely data in mongo is corrupted. Return file-was-already-linked-but-metadata-not-found error."
      (target/ensure-file-version new-file-version-id
                                  (:target-entity test-file-doc)
                                  test-user
                                  {:version-metadata new-version-metadata})
      => {:ok false :text "error.file-was-already-linked-but-metadata-not-found"}
      (provided (now) => 1666785519200
                (target/find-one-linked-file-with-metadata (:target-entity test-file-doc) :include-query true)
                => {:query  fake-query :result nil}
                (storage/move-unlinked-file-to "application-files" (:id test-user) "LP-753-2022-90130" new-file-version-id)
                => {:result :already-moved}))))
