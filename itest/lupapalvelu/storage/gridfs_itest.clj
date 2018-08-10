(ns lupapalvelu.storage.gridfs-itest
  (:require [me.raynes.fs :as fs]
            [midje.sweet :refer :all]
            [sade.core :refer [now]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.storage.gridfs :as gfs]))

(def test-db-name (str "test_" (now)))

(mongo/connect!)

(fact get-db
  (mongo/with-db test-db-name (.getName (mongo/get-db))) => test-db-name)

(def upload-test-db-name (str "test_mongo-upload-" (now)))

(facts "upload"
  (fact "empty file"
    (mongo/with-db upload-test-db-name
      (let [file-count (count (mongo/select :fs.files))
            filename "mongo-upload-test-file.txt"
            file (fs/temp-file filename)]
        (fact "file gets uploaded"
          (gfs/upload (mongo/create-id) filename "plain/text" file) => truthy)
        (fact "file can be deleted"
          (fs/delete file) => true)
        (fact "file count is increased"
          (count (mongo/select :fs.files)) => (inc file-count)))))

  (fact "file with content"
    (mongo/with-db upload-test-db-name
      (let [file-count (count (mongo/select :fs.files))
            filename "mongo-upload-test-file-with-content.txt"
            file (fs/temp-file filename)]
        (spit file (repeat 10000 "Some repeating dummy content"))
        (fact "file gets uploaded"
          (gfs/upload (mongo/create-id) filename "plain/text" file) => truthy)
        (fact "file can be deleted"
          (fs/delete file) => true)
        (fact "file count is increased"
          (count (mongo/select :fs.files)) => (inc file-count))))))
