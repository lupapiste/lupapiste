(ns lupapalvelu.xml.disk-writer-itest
  (:require [babashka.fs :as fs]
            [clj-uuid :as uuid]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.xml.disk-writer :as writer]
            [midje.sweet :refer :all]
            [mount.core :as mount]
            [sade.core :refer [now]]
            [sade.env :as env]
            [sade.files :as files]
            [sade.strings :as ss]))

(def db-name (str "test_xml-disk-writer-itest_" (now)))

(def test-xml (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>bar</foo>"))

(mount/start #'mongo/connection)
(mongo/with-db db-name
  (fixture/apply-fixture "minimal")
  (with-local-actions
    (let [sftp-test-dir (ss/join-file-path (env/value :outgoing-directory)
                                           "disk_writer_test"
                                           (str (uuid/v1)))]

      (against-background
        [(before :contents (fs/create-dirs sftp-test-dir))
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

        (facts "write-file"
          (let [dir  (ss/join-file-path sftp-test-dir (str (uuid/v1)))
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
