(ns lupapalvelu.sftp.context-itest
  (:require [babashka.fs :as fs]
            [clj-uuid :as uuid]
            [clojure.java.io :as io]
            [lupapalvelu.sftp.context :as sftp-ctx]
            [lupapalvelu.sftp-itest-util :refer [gcs-remove-test-folder]]
            [lupapalvelu.xml.disk-writer :as diskw]
            [lupapalvelu.xml.gcs-writer :as gcsw]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.env :as env]
            [sade.files :refer [with-temp-file]]
            [sade.strings :as ss]))

(testable-privates lupapalvelu.sftp.context
                   fs-make-dirs gcs-make-dirs good-input?)

(def rootdir "sftp_context_itest")

(defn fs-write [dir filename source]
  {:pre [(good-input? source)]}
  (diskw/write-file (ss/join-file-path dir filename)
                    source))

(let [test-dir (ss/join-file-path (env/value :outgoing-directory)
                                  rootdir (str (uuid/v1) "_test"))
      foo-dir  (ss/join-file-path test-dir "foo")
      cm-out   (ss/join-file-path test-dir sftp-ctx/CASE-MANAGEMENT-OUT)
      cm-in    (ss/join-file-path test-dir sftp-ctx/CASE-MANAGEMENT-IN)]
  (against-background
    [(before :contents (fs-make-dirs test-dir))
     (after :contents (fs/delete-tree test-dir))]
    (facts "File system"
      (fact "Directory now exists but is empty"
        (fs/exists? test-dir) => true
        (sftp-ctx/fs-list-files test-dir #".*") => empty?)
      (fact "Foo with default subdirs"
        (fs-make-dirs foo-dir :subdirs? true)
        (fs/exists? (ss/join-file-path foo-dir sftp-ctx/ARCHIVE)) => true
        (fs/exists? (ss/join-file-path foo-dir sftp-ctx/ERROR)) => true)
      (fact "Case management and subdirs"
        (fs-make-dirs cm-out :subdirs? true :cm? true)
        (fs/exists? (ss/join-file-path cm-out sftp-ctx/ARCHIVE)) => true
        (fs/exists? (ss/join-file-path cm-out sftp-ctx/ERROR)) => true
        (fs/exists? (ss/join-file-path cm-in sftp-ctx/ARCHIVE)) => true
        (fs/exists? (ss/join-file-path cm-in sftp-ctx/ERROR)) => true)
      (fact "Write files to foo"
        (with-open [is (io/input-stream (io/resource "public/dev/sample-rtf-verdict.rtf"))]
          (fs-write foo-dir "sample.rtf" is))
        (with-temp-file f
          (spit f "I am a file.")
          (fs-write foo-dir "filee.txt" f))
        (fs-write foo-dir "kuntagml.xml" "<xml>hello world</xml>")
        (fs-write foo-dir "bad.jpg" 10) => (throws))
      (fact "List files"
        (sftp-ctx/fs-list-files foo-dir #".*")
        => (just (just {:content-type "application/rtf" :name     "sample.rtf"
                        :size         pos?              :modified pos?})
                 (just {:content-type "text/plain" :name     "filee.txt"
                        :size         pos?         :modified pos?})
                 (just {:content-type "application/xml" :name     "kuntagml.xml"
                        :size         pos?              :modified pos?})
                 :in-any-order)
        (sftp-ctx/fs-list-files foo-dir #".+txt") => (just (contains {:name "filee.txt"}))
        (sftp-ctx/fs-list-files foo-dir #".*rtf") => (just (contains {:name "sample.rtf"}))
        (sftp-ctx/fs-list-files foo-dir #"xml$") => empty?
        (sftp-ctx/fs-list-files foo-dir #".+xml$") => (just (contains {:name "kuntagml.xml"})))
      (fact "Read file"
        (let [entry (sftp-ctx/fs-read-file (ss/join-file-path foo-dir "filee.txt"))]
          entry => (contains {:content-type "text/plain" :name     "filee.txt"
                              :size         pos?         :modified pos?})
          (slurp (:stream entry)) => "I am a file.")))))

(defn gcs-write [dir filename source]
  {:pre [(good-input? source)]}
  (gcsw/write-file (ss/join-file-path dir filename)
                   source))

(when (env/feature? :gcs)
  (let [test-dir (ss/join-file-path rootdir (str (uuid/v1) "_test"))
        foo-dir  (ss/join-file-path test-dir "foo")
        cm-out   (ss/join-file-path test-dir sftp-ctx/CASE-MANAGEMENT-OUT)
        cm-in    (ss/join-file-path test-dir sftp-ctx/CASE-MANAGEMENT-IN)]
    (against-background
      [(before :contents (gcs-make-dirs test-dir))
       (after :contents (gcs-remove-test-folder test-dir 13))]
      (facts "Google Cloud Storage"
        (fact "Directory now exists but is empty"
          (gcsw/file-exists? (str test-dir "/")) => true
          (sftp-ctx/gcs-list-files test-dir #".*") => empty?)
        (fact "Directory name without end-slash does not exist"
          (gcsw/file-exists? test-dir) => false)
        (fact "Foo with default subdirs"
          (gcs-make-dirs foo-dir :subdirs? true)
          (gcsw/file-exists? (str (ss/join-file-path foo-dir sftp-ctx/ARCHIVE) "/")) => true
          (gcsw/file-exists? (str (ss/join-file-path foo-dir sftp-ctx/ERROR) "/")) => true)
        (fact "Case management and subdirs"
          (gcs-make-dirs cm-out :subdirs? true :cm? true)
          (gcsw/file-exists? (str (ss/join-file-path cm-out sftp-ctx/ARCHIVE) "/")) => true
          (gcsw/file-exists? (str (ss/join-file-path cm-out sftp-ctx/ERROR) "/")) => true
          (gcsw/file-exists? (str (ss/join-file-path cm-in sftp-ctx/ARCHIVE) "/")) => true
          (gcsw/file-exists? (str (ss/join-file-path cm-in sftp-ctx/ERROR) "/")) => true)
        (fact "Write files to foo"
          (with-open [is (io/input-stream (io/resource "public/dev/sample-rtf-verdict.rtf"))]
            (gcs-write foo-dir "sample.rtf" is))
          (with-temp-file f
            (spit f "I am a file.")
            (gcs-write foo-dir "filee.txt" f))
          (gcs-write foo-dir "kuntagml.xml" "<xml>hello world</xml>")
          (gcs-write foo-dir "bad.jpg" 10) => (throws))
        (fact "List files"
          (sftp-ctx/gcs-list-files foo-dir #".*")
          => (just (just {:content-type "application/rtf" :name     "sample.rtf"
                          :size         pos?              :modified pos?})
                   (just {:content-type "text/plain" :name     "filee.txt"
                          :size         pos?         :modified pos?})
                   (just {:content-type "application/xml" :name     "kuntagml.xml"
                          :size         pos?              :modified pos?})
                   :in-any-order)
          (sftp-ctx/gcs-list-files foo-dir #".+txt") => (just (contains {:name "filee.txt"}))
          (sftp-ctx/gcs-list-files (str foo-dir "/") #".+txt") => (just (contains {:name "filee.txt"}))
          (sftp-ctx/gcs-list-files foo-dir #".*rtf") => (just (contains {:name "sample.rtf"}))
          (sftp-ctx/gcs-list-files foo-dir #"xml$") => empty?
          (sftp-ctx/gcs-list-files foo-dir #".+xml$") => (just (contains {:name "kuntagml.xml"})))
        (fact "Read file"
          (let [entry (sftp-ctx/gcs-read-file (ss/join-file-path foo-dir "filee.txt"))]
            entry => (contains {:content-type "text/plain" :name     "filee.txt"
                                :size         pos?         :modified pos?})
            (slurp (:stream entry)) => "I am a file."))))))
