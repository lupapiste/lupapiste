(ns lupapalvelu.file-upload-cleanup-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.file-upload :as file-upload]
            [lupapalvelu.storage.file-storage :as fs]
            [clojure.java.io :as io]
            [sade.util :as util]
            [sade.env :as env]
            [clj-uuid :as uuid])
  (:import [java.util Date]
           [java.time Instant]))

(def session-id (str (uuid/v1)))

(def app-id (str (uuid/v1)))

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
      (against-background [(#'fs/ts-two-hours-ago) => (util/get-timestamp-from-now :hour 1)
                           (#'fs/date-two-hours-ago) => (Date/from (.plusSeconds (Instant/now) 3600))]
        (fact "unlinked upload is removed"
          (let [file-id (upload-file file false)]
            (fs/download-unlinked-file session-id file-id) => util/not-empty-or-nil?
            (fs/delete-old-unlinked-files)
            (fs/download-unlinked-file session-id file-id) => empty?))

        (fact "linked upload is not removed"
          (let [file-id (upload-file file app-id)
                attachment {:versions [{:fileId        file-id
                                        :storageSystem (if (env/feature? :s3) :s3 :mongodb)}]}]
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
                                      :storageSystem (if (env/feature? :s3) :s3 :mongodb)}]}]
          (fs/download app-id file-id attachment) => util/not-empty-or-nil?
          (fs/delete-old-unlinked-files)
          (fs/download app-id file-id attachment) => util/not-empty-or-nil?
          (fs/delete-from-any-system app-id file-id))))))
