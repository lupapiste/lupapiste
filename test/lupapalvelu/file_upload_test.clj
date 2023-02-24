(ns lupapalvelu.file-upload-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.file-upload :refer :all]
            [clojure.java.io :as io]
            [lupapalvelu.attachment.unzip-collection :as unzip]
            [sade.env :as env]
            [clj-uuid :as uuid]
            [lupapalvelu.storage.file-storage :as storage]))

(when (env/feature? :unzip-attachments)
  (facts "about uploading a zipped attachment collection with index file"
    (let [op1         "maverick"
          op2         "goose"
          op3         "iceman"
          op4         "merlin"
          application {:permitType "R"
                       :primaryOperation    {:id op1}
                       :secondaryOperations [{:id op2}
                                             {:id op3}
                                             {:id op4}]
                       :documents           [{:schema-info {:op {:id op1} :name "kerrostalo-rivitalo"}
                                              :data        {:tunnus {:value "A"}}}
                                             {:schema-info {:op {:id op2} :name "kerrostalo-rivitalo"}
                                              :data        {:valtakunnallinenNumero {:value "B"}}}]
                       :buildings           [{:operationId op3
                                              :nationalId  "C"}
                                             {:operationId op4
                                              :buildingId  "D"}]}
          user-id     "abc"
          file-id     "badger"
          length      54321
          content     (io/file (io/resource "test-pdf.pdf"))
          file1       {:filename "foobar.pdf"
                       :content  content}
          file2       {:filename "foobar.zip"
                       :tempfile content}
          contents    "test contents"]

      (against-background
        [(uuid/v1) => file-id
         (storage/upload anything anything anything anything anything anything) => {:length length}]

        (fact "non-zip is saved normally"
          (save-files application [file1] user-id) => {:ok true
                                                       :files
                                                           [{:fileId      file-id
                                                             :filename    "foobar.pdf"
                                                             :size        length
                                                             :contentType "application/pdf"
                                                             :metadata    {:linked false, :uploader-user-id user-id}}]})
        (against-background
          [(unzip/unzip-attachment-collection content user-id) => {:attachments []
                                                                   :error       "invalid"}]

          (fact "muuntaja error is passed on to front end"
            (save-files application [file2] user-id) => {:ok    false
                                                         :error "invalid"}))

        (against-background
          [(unzip/unzip-attachment-collection content user-id) => {:attachments []}]

          (fact "zero attachments without muuntaja error is still an error"
            (save-files application [file2] user-id) => {:ok    false
                                                         :error "error.unzipping-error"}))

        (against-background
          [(unzip/unzip-attachment-collection content user-id) => {:attachments [{:object-key "file-id1"
                                                                                  :size       1
                                                                                  :mime       "application/pdf"
                                                                                  :filename   "foobar1.pdf"}
                                                                                 {:object-key "file-id2"
                                                                                  :size       2
                                                                                  :mime       "application/pdf"
                                                                                  :filename   "foobar2.pdf"}]}
           (storage/unlinked-file-exists? user-id anything) => true]

          (fact "files are extracted from a zip without index"
            (save-files application [file2] user-id) => {:ok    true
                                                         :files [{:filename    "foobar1.pdf"
                                                                  :contentType "application/pdf"
                                                                  :fileId      "file-id1"
                                                                  :size        1
                                                                  :metadata    {:linked false, :uploader-user-id user-id}}
                                                                 {:filename    "foobar2.pdf"
                                                                  :contentType "application/pdf"
                                                                  :fileId      "file-id2"
                                                                  :size        2
                                                                  :metadata    {:linked false, :uploader-user-id user-id}}]}))

        (against-background
          [(unzip/unzip-attachment-collection content user-id) => {:attachments [{:object-key    "file-id1"
                                                                                  :size          length
                                                                                  :mime          "application/pdf"
                                                                                  :filename      "foobar1.pdf"
                                                                                  :localizedType "Pohjapiirustus"
                                                                                  :contents      contents
                                                                                  :drawingNumber "1"
                                                                                  :operation     "A"}
                                                                                 {:object-key    "file-id2"
                                                                                  :size          length
                                                                                  :mime          "application/pdf"
                                                                                  :filename      "foobar2.pdf"
                                                                                  :localizedType "Pohjapiirustus"
                                                                                  :contents      contents
                                                                                  :drawingNumber "2"
                                                                                  :operation     "B"}
                                                                                 {:object-key    "file-id3"
                                                                                  :size          length
                                                                                  :mime          "application/pdf"
                                                                                  :filename      "foobar3.pdf"
                                                                                  :localizedType "Julkisivupiirustus"
                                                                                  :contents      contents
                                                                                  :drawingNumber "3"
                                                                                  :operation     "C,D"}
                                                                                 {:object-key    "file-id4"
                                                                                  :size          length
                                                                                  :mime          "application/pdf"
                                                                                  :filename      "foobar4.pdf"
                                                                                  :localizedType "Asemapiirros"
                                                                                  :contents      contents
                                                                                  :drawingNumber "4"}
                                                                                 {:object-key    "file-id5"
                                                                                  :size          length
                                                                                  :mime          "application/pdf"
                                                                                  :filename      "foobar5.pdf"
                                                                                  :localizedType "CV"
                                                                                  :contents      contents}]}
           (storage/unlinked-file-exists? user-id anything) => true]

          (fact "attachment collection zip is parsed and stored as files"
            (save-files application [file2] user-id) => {:ok true
                                                         :files
                                                             [{:filename      "foobar1.pdf"
                                                               :contentType   "application/pdf"
                                                               :contents      contents
                                                               :drawingNumber "1"
                                                               :fileId        "file-id1"
                                                               :size          length
                                                               :type          {:metadata   {:grouping :operation}
                                                                               :type-group :paapiirustus
                                                                               :type-id    :pohjapiirustus}
                                                               :metadata      {:linked false, :uploader-user-id user-id}
                                                               :group         {:groupType  :operation
                                                                               :operations [{:groupType :operation
                                                                                             :id        op1}]}}
                                                              {:filename      "foobar2.pdf"
                                                               :contentType   "application/pdf"
                                                               :contents      contents
                                                               :drawingNumber "2"
                                                               :fileId        "file-id2"
                                                               :size          length
                                                               :type          {:metadata   {:grouping :operation}
                                                                               :type-group :paapiirustus
                                                                               :type-id    :pohjapiirustus}
                                                               :metadata      {:linked false, :uploader-user-id user-id}
                                                               :group         {:groupType  :operation
                                                                               :operations [{:groupType :operation
                                                                                             :id        op2}]}}
                                                              {:filename      "foobar3.pdf"
                                                               :contentType   "application/pdf"
                                                               :contents      contents
                                                               :drawingNumber "3"
                                                               :fileId        "file-id3"
                                                               :size          length
                                                               :type          {:metadata   {:grouping :operation}
                                                                               :type-group :paapiirustus
                                                                               :type-id    :julkisivupiirustus}
                                                               :metadata      {:linked false, :uploader-user-id user-id}
                                                               :group         {:groupType  :operation
                                                                               :operations [{:groupType :operation
                                                                                             :id        op3}
                                                                                            {:groupType :operation
                                                                                             :id        op4}]}}
                                                              {:filename      "foobar4.pdf"
                                                               :contentType   "application/pdf"
                                                               :contents      contents
                                                               :drawingNumber "4"
                                                               :fileId        "file-id4"
                                                               :size          length
                                                               :type          {:metadata   {:grouping       :operation
                                                                                            :multioperation true}
                                                                               :type-group :paapiirustus
                                                                               :type-id    :asemapiirros}
                                                               :metadata      {:linked false, :uploader-user-id user-id}
                                                               :group         {:groupType  :operation
                                                                               :operations (map #(hash-map :groupType :operation :id %) [op1 op2 op3 op4])}}
                                                              {:filename    "foobar5.pdf"
                                                               :contentType "application/pdf"
                                                               :contents    contents
                                                               :fileId      "file-id5"
                                                               :size        length
                                                               :type        {:metadata   {:grouping       :parties
                                                                                          :for-operations #{:tyonjohtajan-nimeaminen-v2 :tyonjohtajan-nimeaminen}}
                                                                             :type-group :osapuolet
                                                                             :type-id    :cv}
                                                               :metadata    {:linked false, :uploader-user-id user-id}
                                                               :group       {:groupType :parties}}]}))

        (against-background
          [(unzip/unzip-attachment-collection content user-id) => {:attachments [{:object-key    "file-id1"
                                                                                  :size          length
                                                                                  :mime          "application/pdf"
                                                                                  :filename      "foobar1.pdf"
                                                                                  :localizedType "Pohjapiirustus"
                                                                                  :contents      contents
                                                                                  :drawingNumber "1"
                                                                                  :operation     "A"}
                                                                                 {:object-key    "file-id4"
                                                                                  :size          length
                                                                                  :mime          "application/pdf"
                                                                                  :filename      "foobar4.pdf"
                                                                                  :localizedType "Asemapiirros"
                                                                                  :contents      contents
                                                                                  :drawingNumber "4"}
                                                                                 {:object-key    "file-id5"
                                                                                  :size          length
                                                                                  :mime          "application/pdf"
                                                                                  :filename      "foobar5.pdf"
                                                                                  :localizedType "CV"
                                                                                  :contents      contents}]}
           (storage/unlinked-file-exists? user-id anything) => true]

          (fact "attachment collection zip is parsed and stored as files - without application"
            (save-files nil [file2] user-id) => {:ok true
                                                 :files
                                                     [{:filename      "foobar1.pdf"
                                                       :contentType   "application/pdf"
                                                       :contents      contents
                                                       :drawingNumber "1"
                                                       :fileId        "file-id1"
                                                       :size          length
                                                       :type          {:metadata   {:grouping :operation}
                                                                       :type-group :paapiirustus
                                                                       :type-id    :pohjapiirustus}
                                                       :metadata      {:linked false, :uploader-user-id user-id}
                                                       :group         {:groupType  :operation
                                                                       :operations []}}
                                                      {:filename      "foobar4.pdf"
                                                       :contentType   "application/pdf"
                                                       :contents      contents
                                                       :drawingNumber "4"
                                                       :fileId        "file-id4"
                                                       :size          length
                                                       :type          {:metadata   {:grouping       :operation
                                                                                    :multioperation true}
                                                                       :type-group :paapiirustus
                                                                       :type-id    :asemapiirros}
                                                       :metadata      {:linked false, :uploader-user-id user-id}
                                                       :group         {:groupType  :operation
                                                                       :operations []}}
                                                      {:filename    "foobar5.pdf"
                                                       :contentType "application/pdf"
                                                       :contents    contents
                                                       :fileId      "file-id5"
                                                       :size        length
                                                       :type        {:metadata   {:grouping       :parties
                                                                                  :for-operations #{:tyonjohtajan-nimeaminen-v2 :tyonjohtajan-nimeaminen}}
                                                                     :type-group :osapuolet
                                                                     :type-id    :cv}
                                                       :metadata    {:linked false, :uploader-user-id user-id}
                                                       :group       {:groupType :parties}}]}))))))

(facts "Check if uploaded file already exists in application"

  (fact "should find duplicate files"
    (mark-duplicates {:attachments [{:latestVersion {:filename "file1.jpg"}}
                                    {:latestVersion {:filename "file2.jpg"}}
                                    {:latestVersion {:filename "file3.jpg"}}]}
                     {:ok    true
                      :files [{:filename "file1.jpg"}
                              {:filename "newfile.jpg"}]}) => {:ok    true
                                                               :files [{:filename           "file1.jpg"
                                                                        :existsWithSameName true}
                                                                       {:filename           "newfile.jpg"
                                                                        :existsWithSameName false}]})

  (fact "file type shouldn't matter"
    (mark-duplicates {:attachments [{:latestVersion {:filename "file1.pdf"}}
                                    {:latestVersion {:filename "file2.pdf"}}
                                    ;; Does not match since jpg is not an extension
                                    {:latestVersion {:filename "newfile.jpg.pdf"}}]}
                     {:ok    true
                      :files [{:filename "file1.jpg"}
                              {:filename "newfile.jpg"}]}) => {:ok    true
                                                               :files [{:filename           "file1.jpg"
                                                                        :existsWithSameName true}
                                                                       {:filename           "newfile.jpg"
                                                                        :existsWithSameName false}]}))
