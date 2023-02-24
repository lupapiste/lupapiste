(ns lupapalvelu.xml.gcs-writer-itest
  (:require [babashka.fs :as fs]
            [clj-uuid :as uuid]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.sftp-itest-util :refer [gcs-remove-test-folder]]
            [lupapalvelu.storage.gcs :refer [blob->file-data-map]]
            [lupapalvelu.xml.gcs-writer :as writer]
            [midje.sweet :refer :all]
            [mount.core :as mount]
            [sade.core :refer [now]]
            [sade.env :as env]
            [sade.files :as files]
            [sade.strings :as ss]))

(def db-name (str "test_xml-gcs-writer-itest_" (now)))

(def test-xml "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>bar</foo>")
(def test-gif "dev-resources/test-gif-attachment.gif")

(when (env/feature? :gcs) ; Just in case
  (mount/start #'mongo/connection)
  (mongo/with-db db-name
    (fixture/apply-fixture "minimal")
    (with-local-actions
      (let [sftp-test-dir (ss/join-file-path "gcs_writer_test" (str (uuid/v1) "_test"))]
        (against-background
          [(before :contents (writer/create-directory sftp-test-dir true))
           (after :contents (gcs-remove-test-folder sftp-test-dir 9))]
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
                  att-export-name      (str file-id "_test-pdf.pdf")
                  att-result           (ss/join-file-path sftp-test-dir att-export-name)]

              (fact "Attachment not written yet"
                (writer/file-exists? att-result) => false)

              (writer/write-attachments application-id
                                        [{:fileId file-id :filename att-export-name}]
                                        sftp-test-dir)

              (fact "Attachment is written"
                (writer/file-exists? att-result) => true)

              (fact "Non-existent attachments cannot be written"
                (writer/write-attachments application-id
                                          [{:fileId (str file-id "-bad") :filename att-export-name}]
                                          sftp-test-dir)
                => (throws))

              (facts "write-file"
                (let [dir  (ss/join-file-path sftp-test-dir (str (uuid/v1)))
                      path (ss/join-file-path dir "test_file.txt")]
                  (fact "Directory and file do not exist"
                    (writer/file-exists? (str dir "/")) => false
                    (writer/file-exists? path) => false)
                  (writer/write-file path "hello world")
                  (fact "In GCS subdir is not automatically created (flat file system)"
                    (writer/file-exists? dir) => false
                    (writer/file-exists? (str dir "/")) => false)
                  (fact "The actual file exists"
                    (writer/file-exists? path) => true
                    (let [fun (some-> (writer/get-file path) :content)]
                      (slurp (fun)) => "hello world"))
                  (files/with-temp-file f
                    (spit f "File contents")
                    (fact "File as input"
                      (fs/regular-file? f) => true
                      (writer/write-file path f)
                      (let [fun (some-> (writer/get-file path) :content)]
                        (slurp (fun)) => "File contents")))
                  (fact "Bad paths"
                    (let [input "hello world"]
                      (writer/write-file (str dir "/   ") input) => (throws)
                      (writer/write-file (str dir "/") input) => (throws)
                      (writer/write-file "/absolut/path" input) => (throws)
                      (writer/write-file "" input) => (throws)
                      (writer/write-file "   " input) => (throws)
                      (writer/write-file nil input) => (throws)))
                  (let [xml-path (ss/join-file-path dir "LP-1234_test.xml")]
                    (writer/write-file xml-path test-xml)
                    (facts "krysp-xml-files"
                      (writer/krysp-xml-files "foo" dir) => empty?
                      (map :filename (writer/krysp-xml-files "LP-1234" dir))
                      => [(fs/file-name xml-path)])

                    (facts "cleanup-output-dir"
                      (fact "Initial state"
                        (map :fileId (writer/list-files dir))
                        => (just (fs/file-name path) (fs/file-name xml-path)
                                 :in-any-order))
                      (fact "No matches, no cleanup"
                        (writer/cleanup-output-dir "LP-FOO" dir (constantly true))
                        (writer/cleanup-output-dir "LP-1234" dir (constantly false))
                        ;; GCS list-files is case-sensitive
                        (writer/cleanup-output-dir "lp-1234" dir (constantly true))
                        (map :fileId (writer/list-files dir))
                        => (just (fs/file-name path) (fs/file-name xml-path)
                                 :in-any-order))
                      (fact "Only XML files are removed"
                        (writer/cleanup-output-dir "LP-1234" dir (constantly true))
                        (map :fileId (writer/list-files dir)) => [(fs/file-name path)])))
                  (facts "Move file"
                    (let [target (ss/join-file-path dir att-export-name)]
                      (writer/file-exists? target) => false
                      (writer/file-exists? att-result) => true
                      (writer/move-file att-result dir)
                      (writer/file-exists? att-result) => false
                      (writer/file-exists? target) => true))
                  (facts "Directorify"
                    (writer/directorify "hello") => "hello/"
                    (writer/directorify "  hello  ") => "hello/"
                    (writer/directorify "//hello//world//") => "/hello/world/"
                    (writer/directorify "//hello // world//") => "/hello / world/"
                    (writer/directorify nil) => (throws)
                    (writer/directorify "") => (throws)
                    (writer/directorify "  ") => (throws)
                    (writer/directorify "/") => "/")
                  (facts "Create directory"
                    (let [one (ss/join-file-path sftp-test-dir "one")
                          two (ss/join-file-path one "two")]
                      (writer/file-exists? two) => false
                      (writer/file-exists? (writer/directorify two)) => false
                      (fact "No parents"
                        (let [data (-> (writer/create-directory two)
                                       (blob->file-data-map)
                                       (select-keys [:contentType :size :filename
                                                     :fileId :modified]))]
                          data => (just {:contentType "text/plain"
                                         :size        0
                                         :filename    nil
                                         :fileId      "two"
                                         :modified    pos?})
                          (writer/file-exists? two) => false
                          (writer/file-exists? (writer/directorify two)) => true
                          (writer/file-exists? one) => false
                          (writer/file-exists? (writer/directorify one)) => false
                          (fact "Again, but with parents"
                            (let [data2 (-> (writer/create-directory two true)
                                            (blob->file-data-map)
                                            (select-keys [:contentType :size :filename
                                                          :fileId :modified]))]
                              data2 => data
                              (writer/file-exists? two) => false
                              (writer/file-exists? (writer/directorify two)) => true
                              (writer/file-exists? one) => false
                              (writer/file-exists? (writer/directorify one)) => true))))
                      (facts "List files"
                        (writer/write-file (ss/join-file-path one "tiny.txt") "o")
                        (writer/write-file (ss/join-file-path one "meme.gif") (fs/file test-gif))
                        (writer/write-file (ss/join-file-path one "kuntagml.xml") test-xml)
                        (writer/write-file (ss/join-file-path two "other.md")
                                           "In the subdir, not listed.")
                        (fact "All"
                          (map :fileId (writer/list-files one))
                          => (just "tiny.txt" "meme.gif" "kuntagml.xml" :in-any-order))
                        (fact "Filter by regex"
                          (map :fileId (writer/list-files one #".*xml"))
                          => ["kuntagml.xml"]
                          (map :fileId (writer/list-files one #".*m.*"))
                          => (just "meme.gif" "kuntagml.xml" :in-any-order)
                          (writer/list-files one #"NOT-FOUND")
                          => [])
                        (fact "Filter by function"
                          (map :fileId (writer/list-files one #(< (.getSize %) 5)))
                          => ["tiny.txt"]
                          (map :fileId (writer/list-files one #(= (.getContentType %)
                                                                  "image/gif")))
                          => ["meme.gif"]
                          (map :fileId (writer/list-files one #(pos? (.getUpdateTime %))))
                          => (just "tiny.txt" "meme.gif" "kuntagml.xml" :in-any-order)
                          (map :fileId (writer/list-files one (constantly false)))
                          => [])))))))))))))
