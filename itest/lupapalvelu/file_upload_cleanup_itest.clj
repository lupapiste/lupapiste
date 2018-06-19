(ns lupapalvelu.file-upload-cleanup-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.file-upload :as file-upload]
            [lupapalvelu.mongo :as mongo]
            [clojure.java.io :as io]
            [sade.util :as util]))

(mongo/connect!)

(defn- upload-file [file linked]
  (let [file-id (mongo/create-id)]
    (mongo/upload file-id "test-pdf.pdf" "application/pdf" file :linked linked)
    file-id))

(facts "Uploaded files cleanup"
  (let [file (io/file "dev-resources/test-pdf.pdf")]
    (facts "files uploaded over 2 hours ago"
      (against-background [(#'file-upload/two-hours-ago) => (util/get-timestamp-from-now :hour 1)]
        (fact "unlinked upload is removed"
          (let [file-id (upload-file file false)]
            (mongo/select :fs.files {:_id file-id}) => util/not-empty-or-nil?
            (file-upload/cleanup-uploaded-files)
            (mongo/select :fs.files {:_id file-id}) => empty?))

        (fact "linked upload is not removed"
          (let [file-id (upload-file file true)]
            (mongo/select :fs.files {:_id file-id}) => util/not-empty-or-nil?
            (file-upload/cleanup-uploaded-files)
            (mongo/select :fs.files {:_id file-id}) => util/not-empty-or-nil?))))

    (facts "files uploaded under 2 hours ago"
      (fact "unliked upload is not removed"
        (let [file-id (upload-file file false)]
          (mongo/select :fs.files {:_id file-id}) => util/not-empty-or-nil?
          (file-upload/cleanup-uploaded-files)
          (mongo/select :fs.files {:_id file-id}) => util/not-empty-or-nil?))
      (fact "linked upload is not removed"
        (let [file-id (upload-file file true)]
          (mongo/select :fs.files {:_id file-id}) => util/not-empty-or-nil?
          (file-upload/cleanup-uploaded-files)
          (mongo/select :fs.files {:_id file-id}) => util/not-empty-or-nil?)))))
