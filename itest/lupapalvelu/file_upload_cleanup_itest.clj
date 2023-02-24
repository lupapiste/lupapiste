(ns lupapalvelu.file-upload-cleanup-itest
  (:require [clj-uuid :as uuid]
            [clojure.java.io :as io]
            [lupapalvelu.file-upload :as file-upload]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.storage.file-storage :as fs]
            [midje.sweet :refer :all]
            [mount.core :as mount]
            [sade.date :as date]
            [sade.util :as util])
  (:import [java.time Instant]
           [java.util Date]))

(def session-id (str (uuid/v1)))

(def app-id (str (uuid/v1)))

(mount/start #'mongo/connection)

(defn- upload-file [file link-id]
  (-> (file-upload/save-file {:filename "test-pdf.pdf"
                              :content file
                              :content-type "application/pdf"}
                             (if link-id
                               {:application link-id
                                :linked true}
                               {:sessionId session-id
                                :linked false}))
      :fileId))

(facts "Uploaded files cleanup"
  (let [file (io/file "dev-resources/test-pdf.pdf")]
    (facts "files uploaded over 2 hours ago"
      (against-background [(#'fs/ts-two-hours-ago) => (-> (date/now) (date/plus :hour) (date/timestamp))
                           (#'fs/date-two-hours-ago) => (Date/from (.plusSeconds (Instant/now) 3600))]
        (fact "unlinked upload is removed"
          (let [file-id (upload-file file false)]
            (fs/download-unlinked-file session-id file-id) => util/not-empty-or-nil?
            (fs/delete-old-unlinked-files)
            (fs/download-unlinked-file session-id file-id) => empty?))

        (fact "linked upload is not removed"
          (let [file-id    (upload-file file app-id)
                attachment {:versions [{:fileId        file-id
                                        :storageSystem (fs/default-storage-system-id)}]}]
            (fs/download app-id file-id attachment) => util/not-empty-or-nil?
            (fs/delete-old-unlinked-files)
            (fs/download app-id file-id attachment) => util/not-empty-or-nil?
            (fs/delete-from-any-system app-id file-id)))))

    (facts "files uploaded under 2 hours ago"
      (fact "unliked upload is not removed"
        (let [file-id (upload-file file false)]
          (fs/download-unlinked-file session-id file-id) => util/not-empty-or-nil?
          (fs/delete-old-unlinked-files)
          (fs/download-unlinked-file session-id file-id) => util/not-empty-or-nil?
          (fs/delete-from-any-system app-id file-id)))
      (fact "linked upload is not removed"
        (let [file-id (upload-file file app-id)
              attachment {:versions [{:fileId        file-id
                                      :storageSystem (fs/default-storage-system-id)}]}]
          (fs/download app-id file-id attachment) => util/not-empty-or-nil?
          (fs/delete-old-unlinked-files)
          (fs/download app-id file-id attachment) => util/not-empty-or-nil?
          (fs/delete-from-any-system app-id file-id))))))
