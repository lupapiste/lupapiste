(ns lupapalvelu.xml.disk-writer-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.linked-files :as linked-files]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.xml.disk-writer]))

(def ^:private download-attachment-content
  @#'lupapalvelu.xml.disk-writer/download-attachment-content)

(deftest download-attachment-content-test
  (let [application {:id "LP-753-2024-00001"}]

    (testing "downloads regular attachment via storage/download"
      (let [regular-attachment {:fileId   "regular-file-id-123"
                                :filename "document.pdf"}]
        (with-redefs [storage/download (fn [app file-id]
                                         (is (= application app))
                                         (is (= "regular-file-id-123" file-id))
                                         {:content "mock-content" :filename "document.pdf"})]
          (is (= {:content "mock-content" :filename "document.pdf"}
                 (download-attachment-content application regular-attachment))))))

    (testing "downloads linked file via linked-files/get-linked-attachment-version-file"
      (let [bim-linked-file    {:target-entity {:target-type    "bim-model-file"
                                                :file-id        "bim-file-id-456"
                                                :application-id "LP-753-2024-00001"}
                                :versions      [{:file-version-id "version-id-789"
                                                 :metadata        {:name "model.ifc"}}]}
            linked-attachment  {:fileId      "version-id-789"
                                :filename    "model.ifc"
                                :linked-file bim-linked-file}]
        (with-redefs [linked-files/get-linked-attachment-version-file
                      (fn [linked-file version-id]
                        (is (= bim-linked-file linked-file))
                        (is (= "version-id-789" version-id))
                        {:content "mock-ifc-content" :filename "model.ifc"})]
          (is (= {:content "mock-ifc-content" :filename "model.ifc"}
                 (download-attachment-content application linked-attachment))))))

    (testing "linked file with nil fileId returns nil content"
      (let [bim-linked-file   {:target-entity {:target-type    "bim-model-file"
                                               :file-id        "bim-file-id-456"
                                               :application-id "LP-753-2024-00001"}
                               :versions      []}
            linked-attachment {:fileId      nil
                               :filename    nil
                               :linked-file bim-linked-file}]
        (with-redefs [linked-files/get-linked-attachment-version-file
                      (fn [linked-file version-id]
                        (is (= bim-linked-file linked-file))
                        (is (nil? version-id))
                        nil)]
          (is (nil? (download-attachment-content application linked-attachment))))))

    (testing "does not call linked-files for regular attachments"
      (let [regular-attachment {:fileId   "regular-file-id-123"
                                :filename "document.pdf"}
            linked-files-called? (atom false)]
        (with-redefs [storage/download (constantly {:content "mock-content"})
                      linked-files/get-linked-attachment-version-file
                      (fn [& _]
                        (reset! linked-files-called? true)
                        nil)]
          (is (= {:content "mock-content"}
                 (download-attachment-content application regular-attachment)))
          (is (false? @linked-files-called?)
              "linked-files/get-linked-attachment-version-file should not be called"))))

    (testing "does not call storage/download for linked files"
      (let [bim-linked-file   {:target-entity {:target-type    "bim-model-file"
                                               :file-id        "bim-file-id-456"
                                               :application-id "LP-753-2024-00001"}
                               :versions      [{:file-version-id "version-id-789"}]}
            linked-attachment {:fileId      "version-id-789"
                               :filename    "model.ifc"
                               :linked-file bim-linked-file}
            storage-called? (atom false)]
        (with-redefs [linked-files/get-linked-attachment-version-file
                      (constantly {:content "mock-ifc-content"})
                      storage/download
                      (fn [& _]
                        (reset! storage-called? true)
                        nil)]
          (is (= {:content "mock-ifc-content"}
                 (download-attachment-content application linked-attachment)))
          (is (false? @storage-called?)
              "storage/download should not be called"))))))
