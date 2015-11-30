(ns lupapalvelu.mongo-itest
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [monger.collection :as mc]
            [lupapalvelu.mongo :as mongo]
            [sade.core :refer :all]
            [me.raynes.fs :as fs]))

(def test-db-name (str "test_" (now)))

(mongo/connect!)

(fact get-db
  (mongo/with-db test-db-name (.getName (mongo/get-db))) => test-db-name)

(def upload-test-db-name (str "upload-" test-db-name))

(facts "upload"
  (fact "empty file"
    (mongo/with-db upload-test-db-name
      (let [file-count (count (mongo/select :fs.files))
            filename   "mongo-upload-test-file.txt"
            file (fs/temp-file filename)]
        (fact "file gets uploaded"
          (mongo/upload (mongo/create-id) filename "plain/text" file) => truthy)
        (fact "file can be deleted"
          (fs/delete file) => true)
        (fact "file count is increased"
          (count (mongo/select :fs.files)) => (inc file-count)))))

  (fact "file with content"
    (mongo/with-db upload-test-db-name
      (let [file-count (count (mongo/select :fs.files))
            filename   "mongo-upload-test-file-with-content.txt"
            file (fs/temp-file filename)]
        (spit file (repeat 10000 "Some repeating dummy content"))
        (fact "file gets uploaded"
          (mongo/upload (mongo/create-id) filename "plain/text" file) => truthy)
        (fact "file can be deleted"
          (fs/delete file) => true)
        (fact "file count is increased"
          (count (mongo/select :fs.files)) => (inc file-count))))))
