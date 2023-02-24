(ns lupapalvelu.attachment.attach-itest
  "Tests for a scenario where the file linking step (from unlinked bucket to the application
  bucket) fails for the typical attachment/file operations."
  (:require [lupapalvelu.attachment :as att]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.storage.file-storage :as storage]
            [midje.sweet :refer :all]
            [mount.core :as mount]
            [sade.core :refer [now]]
            [sade.util :as util]))

(defn stamp-attachment [apikey application-id attachment-id]
  (let [stamp {:id         "123456789012345678901234"
               :name       "Stampy McStampface"
               :position   {:x 10 :y 200}
               :background 0
               :page       "all"
                         :qrCode     true
               :rows       [[{:type "custom-text" :value "Stampy!"}]]}
        {job :job} (command apikey :stamp-attachments :id application-id
                            :timestamp (now)
                            :files [attachment-id]
                            :lang :fi
                            :stamp stamp)]
    (when-not (= (:status job) "done")
      (poll-job apikey :stamp-attachments-job (:id job) (:version job) 25))))

(defn latest-version [application-id attachment-id]
  (->> (mongo/by-id :applications application-id [:attachments])
       :attachments
       (util/find-by-id attachment-id)
       :latestVersion))

(defn downloadable [apikey app-id & attachment-ids]
  (let [user           (find-user-from-minimal-by-apikey apikey)
        application    (mongo/by-id :applications app-id)
        attachment-ids (set attachment-ids)
        attachments    (filter (comp attachment-ids :id)
                               (:attachments application))]

    (doseq [{:keys [latestVersion]} attachments
            :let                    [{:keys [fileId originalFileId]} latestVersion
                                     file-ids (set [fileId originalFileId])]]
      (fact "Attachment files can be downloaded"
        (every? #(att/get-attachment-file-as! user application %) file-ids)
        => true))))

(defn deposit-file [apikey application-id]
  (let [file-id (get-in (upload-file apikey "dev-resources/cake.png")
                        [:files 0 :fileId])
        {job :job} (command apikey :bind-filebank-files
                            :id application-id
                            :filedatas [{:fileId file-id :keywords []}])]
    (when-not (= (:status job) "done")
      (poll-job apikey :bind-filebank-job (:id job) (:version job) 25))
    file-id))


(defn attachment-suite [archive?]
  (facts {:midje/description (format "Attachment tests. Archive/conversion %s enabled"
                                     (if archive? "IS" "NOT"))}
    (command admin :set-organization-boolean-attribute
                   :organizationId "753-R"
                   :attribute "permanent-archive-enabled"
                   :enabled archive?)
    (let [{app-id :id}          (create-local-app pena
                                                  :propertyId sipoo-property-id
                                                  :operation "kerrostalo-rivitalo")
          {:keys [attachments]} (mongo/by-id :applications app-id [:attachments])
          [att1 att2]           attachments]
      (facts "Linking files"
        att1 => truthy
        att2 => truthy
        (fact "Upload and bind successfully"
          (upload-file-and-bind pena app-id nil :attachment-id (:id att1))
          (upload-file-and-bind pena app-id {:type     {:type-group "muut" :type-id "muu"}
                                             :contents "New attachment"})
          (let [{:keys [attachments]} (query-application pena app-id)
                last-id               (-> attachments last :id)]
            (-> attachments first :latestVersion :version)
            => {:major 1 :minor 0}
            (last attachments) => (contains {:contents      "New attachment"
                                             :latestVersion truthy})
            (downloadable pena app-id (:id att1) last-id)
            (against-background
              [;; This is called in `lupapalvelu.attachment.bind-single-attachment!`
               (lupapalvelu.storage.file-storage/link-files-to-application
                 anything app-id anything) =throws=> (Exception. "Link failed!")]
              (facts "Linking fails: No attachment/version"
                (upload-file-and-bind pena app-id nil :attachment-id (:id att1))
                (upload-file-and-bind pena app-id nil :attachment-id (:id att2))
                (upload-file-and-bind pena app-id
                                      {:type     {:type-group "muut" :type-id "muu"}
                                       :contents "Failing attachment"})))
            (fact "No changes"
              (:attachments (query-application pena app-id))
              => attachments)
            (downloadable pena app-id (:id att1) last-id)
            (fact "Submit application"
              (command pena :submit-application :id app-id) => ok?)
            (fact "Stamping success"
              (stamp-attachment sonja app-id (:id att1))
              (latest-version app-id (:id att1))
              => (contains {:stamped true
                            :version {:major 1 :minor 1}})
              (downloadable pena app-id (:id att1)))
            (against-background
              [;; This is called in `lupapalvelu.attachment.attach!`
               (lupapalvelu.storage.file-storage/link-files-to-application
                 anything app-id anything anything) =throws=> (Exception. "No stamp for you!")]
              (fact "Restamping fails due to linking error"
                (stamp-attachment sonja app-id (:id att1))
                (latest-version app-id (:id att1))
                => (contains {:stamped true
                              :version {:major 1 :minor 1}}))
              (fact "Stamping fails due to linking error"
                (stamp-attachment sonja app-id last-id)
                (latest-version app-id last-id)
                => (contains {:stamped falsey
                              :version {:major 1 :minor 0}}))
              (downloadable pena app-id (:id att1) last-id))))
        (fact "PNG file"
          (upload-file-and-bind pena app-id {:filename "dev-resources/cake.png"}
                                :attachment-id (:id att2))
          (downloadable pena app-id (:id att2)))))))

(mount/start #'mongo/connection)
(mongo/with-db "test_attach"
  (fixture/apply-fixture "minimal")
  (with-local-actions
    (attachment-suite true)
    (attachment-suite false)
    (let [{app-id :id} (create-local-app pena
                                         :propertyId sipoo-property-id
                                         :operation "kerrostalo-rivitalo")]
      (facts "Filebank"
        (command admin :set-organization-boolean-attribute
                 :organizationId "753-R"
                 :attribute "filebank-enabled"
                 :enabled true) => ok?
        (fact "Successful deposit"
          (let [file-id  (deposit-file ronja app-id)
                file-doc (->> (mongo/by-id :filebank app-id [:files])
                              :files
                              (util/find-by-key :file-id file-id))]
            file-id => truthy
            file-doc => truthy

            (fact "File can be downloaded"
              (storage/download-from-system app-id file-id (:storageSystem file-doc))
              => truthy)

            (against-background
              [;; This is called in `lupapalvelu.attachment.bind-single-filebank-file!`
               (lupapalvelu.storage.file-storage/link-files-to-application
                 anything app-id anything) =throws=> (Exception. "Bank is closed!")]
              (fact "Linking fails -> no deposit"
                (let [gone-id (deposit-file ronja app-id)]
                  gone-id => truthy
                  gone-id =not=> file-id
                  (:files (mongo/by-id :filebank app-id [:files]))
                  => (just (contains {:file-id file-id})))))))))))
