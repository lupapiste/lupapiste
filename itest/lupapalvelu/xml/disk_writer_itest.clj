(ns lupapalvelu.xml.disk-writer-itest
  (:require [babashka.fs :as fs]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.linked-files :as linked-files]
            [lupapalvelu.sftp.context :as sftp-ctx]
            [lupapalvelu.xml.disk-writer :as writer]
            [midje.sweet :refer :all]
            [sade.env :as env]
            [sade.files :as files]
            [sade.strings :as ss])
  (:import [java.io ByteArrayInputStream]))

(set! *warn-on-reflection* true)

(def test-xml "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>bar</foo>")

(with-test-system {:fixture "minimal"}
  (with-local-actions
    (let [sftp-test-dir (ss/join-file-path (env/value :outgoing-directory)
                                           "disk_writer_test"
                                           (str (random-uuid)))]

      (against-background
          [(before :contents (sftp-ctx/create-non-existing-dirs sftp-test-dir))
           (after :contents (fs/delete-tree sftp-test-dir))]
        (facts "write-attachments"
          (let [{application-id :id} (create-and-submit-local-application sonja
                                                                          :propertyId sipoo-property-id
                                                                          :address "submitted 16")
                file-id              (upload-file-and-bind sonja application-id
                                                           {:type     {:type-group "paapiirustus"
                                                                       :type-id    "aitapiirustus"}
                                                            :filename "dev-resources/test-pdf.pdf"
                                                            :contents "Fences"
                                                            :group    {}})
                application          (query-application sonja application-id)
                att-export-name      (str file-id "_test-pdf.pdf")
                att-result           (ss/join-file-path sftp-test-dir att-export-name)]

            (fact "Attachment not written yet"
              (fs/exists? att-result) => false)

            (writer/write-attachments application
                                      [{:fileId file-id :filename att-export-name}]
                                      sftp-test-dir)

            (fact "Attachment is written"
              (fs/exists? att-result) => true)

            (fact "Non-existent attachments cannot be written"
              (writer/write-attachments application
                                        [{:fileId (str file-id "-bad") :filename att-export-name}]
                                        sftp-test-dir)
              => (throws))))

        (facts "write-attachments with BIM linked files"
          (let [{application-id :id} (create-and-submit-local-application sonja
                                                                          :propertyId sipoo-property-id
                                                                          :address "BIM test address")
                application          (query-application sonja application-id)
                bim-file-id          (str (random-uuid))
                bim-version-id       (str (random-uuid))
                bim-export-name      (str bim-file-id "_test-model.ifc")
                bim-result           (ss/join-file-path sftp-test-dir bim-export-name)
                ;; Create a mock BIM linked file structure
                bim-linked-file      {:target-entity {:target-type    "bim-model-file"
                                                      :file-id        bim-file-id
                                                      :application-id application-id}
                                      :versions      [{:file-version-id bim-version-id
                                                       :metadata        {:name "test-model.ifc"}}]}
                ;; Create pseudo-attachment as would be created by linked-file->pseudo-attachment
                bim-pseudo-att       {:fileId      bim-version-id
                                      :filename    bim-export-name
                                      :linked-file bim-linked-file}
                ifc-content          "ISO-10303-21; HEADER; ... mock IFC content ..."]

            (fact "BIM linked file not written yet"
              (fs/exists? bim-result) => false)

            ;; Mock the linked-files download to return our test content
            (fact "BIM linked file is written correctly"
              (writer/write-attachments application
                                        [bim-pseudo-att]
                                        sftp-test-dir)
              => nil
              (provided
                (linked-files/get-linked-attachment-version-file bim-linked-file bim-version-id)
                => {:content  (fn [] (ByteArrayInputStream. (.getBytes ifc-content)))
                    :filename "test-model.ifc"}))

            (fact "BIM file exists with correct content"
              (fs/exists? bim-result) => true
              (slurp bim-result) => ifc-content)

            (fact "BIM linked file with nil content throws error"
              (let [empty-bim-att {:fileId      "empty-version-id"
                                   :filename    "empty-model.ifc"
                                   :linked-file {:target-entity {:target-type "bim-model-file"}
                                                 :versions      []}}]
                (writer/write-attachments application
                                          [empty-bim-att]
                                          sftp-test-dir)
                => (throws)
                (provided
                  (linked-files/get-linked-attachment-version-file anything anything)
                  => nil)))))

        (facts "write-file"
          (let [dir  (ss/join-file-path sftp-test-dir (str (random-uuid)))
                path (ss/join-file-path dir "test_file.txt")]
            (fact "Directory and file do not exist"
              (fs/exists? dir) => false
              (fs/exists? path) => false)
            (writer/write-file path "hello world")
            (fact "File exists"
              (fs/exists? dir) => true
              (fs/exists? path) => true
              (slurp path) => "hello world")
            (files/with-temp-file f
              (spit f "File contents")
              (fact "File as input"
                (fs/regular-file? f) => true
                (writer/write-file path f)
                (slurp path) => "File contents"))
            (fact "Bad paths"
              (let [input "hello world"]
                (writer/write-file dir input) => (throws)
                (writer/write-file (str dir "/   ") input) => (throws)
                (writer/write-file (str dir "/") input) => (throws)
                (writer/write-file "" input) => (throws)
                (writer/write-file "   " input) => (throws)
                (writer/write-file nil input) => (throws)))
            (let [xml-path (ss/join-file-path dir "LP-1234_test.xml")]
              (writer/write-file xml-path test-xml)
              (facts "krysp-xml-files"
                (writer/krysp-xml-files "foo" dir) => empty?
                (writer/krysp-xml-files "LP-1234" dir) => [xml-path])
              (facts "cleanup-output-dir"
                (fact "Initial state"
                  (map str (fs/list-dir dir)) => (just path xml-path :in-any-order))
                (fact "No matches, no cleanup"
                  (writer/cleanup-output-dir "LP-FOO" dir (constantly true))
                  (writer/cleanup-output-dir "LP-1234" dir (constantly false))
                  (map str (fs/list-dir dir)) => (just path xml-path :in-any-order))
                (fact "Only XML files are removed"
                  (writer/cleanup-output-dir "lp-1234" dir (constantly true))
                  (map str (fs/list-dir dir)) => [path])))))))))
