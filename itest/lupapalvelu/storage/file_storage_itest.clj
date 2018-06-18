(ns lupapalvelu.storage.file-storage-itest
  (:require [midje.sweet :refer :all]
            [clojure.java.io :as io]
            [lupapalvelu.storage.file-storage :as fs]
            [clj-uuid :as uuid]
            [pandect.core :as pandect]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.itest-util :refer :all]
            [sade.env :as env])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(def filename "test-pdf.pdf")
(def test-file (io/file (io/resource filename)))
(def content-type "application/pdf")
(def file-sha (pandect/sha1 test-file))
(def file-length (.length test-file))

(def s3-enabled? (env/feature? :s3))

(defn- verify-content [{:keys [content]} & [ref-sha]]
  (with-open [is (content)]
    (fact "SHA1 matches after upload & download"
      (pandect/sha1 is) => (or ref-sha file-sha))))

(facts "File upload and download works"
  (mongo/with-db test-db-name
    (mongo/connect!)

    (doseq [storage-system (if s3-enabled?
                             [:s3 :mongodb]
                             [:mongodb])]

      ; When S3 is enabled by default, test also the mongodb storage by turning S3 off
      (when (and s3-enabled? (= storage-system :mongodb))
        (env/disable-feature! :s3))

      (facts {:midje/description (str "when storage system is " (name storage-system))}

        (facts "upload is linked to an application"
        (let [id (str (uuid/v1))
              app-id "LP-091-2018-99999"
              attachment {:versions [{:fileId        id
                                      :storageSystem storage-system}]}]
          (fact "upload returns content length"
            (fs/upload id filename content-type test-file {:application app-id :linked true}) => (contains {:length file-length}))

          (let [dl (fs/download app-id id attachment)]
            (fact "download returns correct info about the file"
              dl => (contains {:application app-id
                               :contentType content-type
                               :content fn?
                               :fileId id
                               :filename filename
                               :metadata (just {:application app-id :linked true :uploaded pos-int?})
                               :size file-length})
              (verify-content dl)))

          (fact "multiple files may be downloaded at the same time"
            (let [filename2 "cake.jpg"
                  file2 (io/file (io/resource filename2))
                  id2 (str (uuid/v1))
                  content-type2 "image/jpeg"
                  _ (fs/upload id2 filename2 content-type2  file2 {:application app-id :linked true})
                  application {:id app-id
                               :attachments [attachment
                                             {:versions [{:fileId        id2
                                                          :storageSystem storage-system}]}]}
                  dl (fs/download-many application [id id2])]
              dl => (just [(contains {:application app-id
                                      :contentType content-type
                                      :content fn?
                                      :fileId id
                                      :filename filename
                                      :metadata (just {:application app-id :linked true :uploaded pos-int?})
                                      :size file-length})
                           (contains {:application app-id
                                      :contentType content-type2
                                      :content fn?
                                      :fileId id2
                                      :filename filename2
                                      :metadata (just {:application app-id :linked true :uploaded pos-int?})
                                      :size (.length file2)})])
              (verify-content (first dl))
              (verify-content (last dl) (pandect/sha1 file2))

              (fs/delete application id2)))

          (fact "application file can be deleted"
            (fs/delete {:id app-id :attachments [attachment]} id) => nil
            (fs/download app-id id attachment) => nil)))

      (facts "upload belongs to a user's personal files"
        (let [id (str (uuid/v1))
              user-id (str (uuid/v1))]
          (fact "upload returns content length"
            (fs/upload id filename content-type test-file {:user-id user-id}) => (contains {:length file-length}))

          (let [dl (fs/download-user-attachment user-id id storage-system)]
            (fact "download returns correct info about the file"
              dl => (contains {:contentType content-type
                               :content fn?
                               :fileId id
                               :filename filename
                               :metadata (just {:user-id user-id :uploaded pos-int?})
                               :size file-length})
              (verify-content dl)))

          (fact "user file can be deleted"
            (fs/delete-user-attachment user-id id storage-system) => nil
            (fs/download-user-attachment user-id id storage-system) => nil)))

      (facts "unlinked files can be uploaded and operated with"
        (let [id (str (uuid/v1))
              id2 (str (uuid/v1))
              user-id (str (uuid/v1))
              session-id (str (uuid/v1))]
          (fact "upload with user id works"
            (fs/upload id filename content-type test-file {:uploader-user-id user-id}) => (contains {:length file-length}))

          (fact "upload with session id works"
            (fs/upload id2 filename content-type test-file {:sessionId session-id}) => (contains {:length file-length}))

          (fact "unlinked files' existance can be checked"
            (fs/unlinked-files-exist? user-id [id]) => true
            (fs/unlinked-files-exist? user-id [id2]) => false
            (fs/unlinked-files-exist? session-id [id2]) => true)

          (let [dl (fs/download-unlinked-file user-id id)]
            (fact "download returns correct info about the file linked to a user id"
              dl => (contains {:contentType content-type
                               :content fn?
                               :fileId id
                               :filename filename
                               :metadata (just {:uploader-user-id user-id :uploaded pos-int?})
                               :size file-length})
              (verify-content dl)))

          (let [dl (fs/download-unlinked-file session-id id2)]
            (fact "download returns correct info about the file linked to a session id"
              dl => (contains {:contentType content-type
                               :content fn?
                               :fileId id2
                               :filename filename
                               :metadata (just {:sessionId session-id :uploaded pos-int?})
                               :size file-length})
              (verify-content dl)))

          (fact "unlinked file can be deleted"
            (fs/delete-unlinked-file user-id id) => nil
            (Thread/sleep 1000)
            (fs/download-unlinked-file user-id id) => nil

            (fs/delete-unlinked-file session-id id2) => nil
            (Thread/sleep 1000)
            (fs/download-unlinked-file session-id id2) => nil)))

      (facts "files can be linked"
        (let [id (str (uuid/v1))
              id2 (str (uuid/v1))
              user-id (str (uuid/v1))
              session-id (str (uuid/v1))
              app-id "LP-091-2018-99999"
              bulletin-id "LP-091-2018-97777"
              attachment {:versions [{:fileId        id
                                      :storageSystem storage-system}]}]
          (fs/upload id filename content-type test-file {:uploader-user-id user-id})
          (fs/upload id2 filename content-type test-file {:sessionId session-id})

          (fact "file can be linked to an application"
            (fs/link-files-to-application user-id app-id [id]) => 1)

          (fact "file can be linked to a bulletin"
            (fs/link-files-to-bulletin session-id bulletin-id [id2]) => 1)

          (fact "linked files' existance can be checked"
            (fs/application-file-exists? app-id id) => true)

          (let [dl (fs/download app-id id attachment)]
            (fact "linked file can be downloaded from the application"
              dl => (contains {:application app-id
                               :contentType content-type
                               :content fn?
                               :fileId id
                               :filename filename
                               :metadata (contains {:uploader-user-id user-id :uploaded pos-int?})
                               :size file-length})
              (verify-content dl)))

          (let [dl (fs/download-bulletin-comment-file bulletin-id id2 storage-system)]
            (fact "linked bulletin comment file can be downloaded"
              dl => (contains {:bulletin bulletin-id
                               :contentType content-type
                               :content fn?
                               :fileId id2
                               :filename filename
                               :metadata (contains {:sessionId session-id :uploaded pos-int?})
                               :size file-length})
                (verify-content dl)))

          (fact "linked file cannot be deleted as unlinked"
            (fs/delete-unlinked-file user-id id) => nil
            (verify-content (fs/download app-id id attachment)))

          (fs/delete-from-any-system app-id id)
          (fs/delete-from-any-system bulletin-id id)))

        (facts "process files can be uploaded and manipulated"
          (let [process-id (str (uuid/v1))
                bos (ByteArrayOutputStream.)]
            (io/copy test-file bos)
            (fact "process file can be uploaded"
              (with-open [is (ByteArrayInputStream. (.toByteArray bos))]
                (fs/upload-process-file process-id filename content-type is {:sha256 "foo"}) => (contains {:length file-length})))

            (fact "process file can be downloaded"
              (let [dl (fs/download-process-file process-id)]
                dl => (contains {:content fn?
                                 :contentType content-type
                                 :fileId process-id
                                 :filename filename
                                 :metadata (just {:sha256 "foo" :uploaded pos-int?})
                                 :size file-length})
                (verify-content dl)))

            (fact "process file can be deleted"
              (fs/delete-process-file process-id) => nil
              (fs/download-process-file process-id) => nil))))

      (when s3-enabled?
        (env/enable-feature! :s3)))))
