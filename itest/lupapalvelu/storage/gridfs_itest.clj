(ns lupapalvelu.storage.gridfs-itest
  (:require [babashka.fs :as fs]
            [lupapalvelu.itest-util :refer [with-test-system]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.storage.gridfs :as gfs]
            [midje.sweet :refer :all]))

(set! *warn-on-reflection* true)

(with-test-system {}
  (facts "upload"
    (fact "empty file"
      (let [file-count (count (mongo/select :fs.files))
            filename   "mongo-upload-test-file.txt"
            file       (fs/file (fs/create-temp-file))]
        (fact "file gets uploaded"
          (gfs/upload (mongo/create-id) filename "plain/text" file) => truthy)
        (fact "file can be deleted"
          (fs/delete-if-exists file) => true)
        (fact "file count is increased"
          (count (mongo/select :fs.files)) => (inc file-count))))

    (fact "file with content"
      (let [file-count (count (mongo/select :fs.files))
            filename   "mongo-upload-test-file-with-content.txt"
            file       (fs/file (fs/create-temp-file))]
        (spit file (repeat 10000 "Some repeating dummy content"))
        (fact "file gets uploaded"
          (gfs/upload (mongo/create-id) filename "plain/text" file) => truthy)
        (fact "file can be deleted"
          (fs/delete-if-exists file) => true)
        (fact "file count is increased"
          (count (mongo/select :fs.files)) => (inc file-count))))))
