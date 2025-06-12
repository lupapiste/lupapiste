(ns lupapalvelu.attachment.conversion-itest
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [lupapalvelu.attachment.conversion :as conversion]
            [lupapalvelu.laundry.client :as laundry-client]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.storage.file-storage :as storage]
            [matcher-combinators.test :refer [match?]]))

;; NOTE: Mongo connection is not running during this test, all calls are mocked.

(def application-id "LP-999-2021-12345")

(def metadata {:application application-id})

(defn delete-file [file-id]
  {:pre [(string? file-id)]}
  (storage/delete-app-file-from-storage application-id
                                        file-id
                                        :gcs))

(deftest conversion-test
  (testing "invalid mime"
    (is (= {:result {:archivable         false
                     :archivabilityError :invalid-mime-type}}
           (conversion/archivability-conversion metadata
                                                {:organization "123-R"}
                                                {:contentType "image/gif"
                                                 :filename    "foo.foo"
                                                 :fileId      (str (random-uuid))}))))

  (testing "TXT conversion"
    (with-redefs [conversion/pdf-a-required? (constantly true)]
      (let [file-id      (str (random-uuid))
            _            (storage/upload file-id
                                         "test-attachment.txt"
                                         "text/plain"
                                         (io/file "dev-resources/test-attachment.txt")
                                         metadata
                                         :gcs)
            result       (conversion/archivability-conversion metadata
                                                              {:organization "123-R"}
                                                              {:filename    "test-attachment.txt"
                                                               :contentType "text/plain"
                                                               :fileId      file-id})
            converted-id (-> result :file :fileId)]
        (is (match? {:result {:archivabilityError nil
                              :archivable         true
                              :autoConversion     true}
                     :file   {:filename    "test-attachment.pdf"
                              :contentType "application/pdf"
                              :fileId      string?
                              :size        pos-int?}}
                    result))
        (delete-file file-id)
        (delete-file converted-id))))

  (testing "PDF conversion - no archive => not-validated"
    (with-redefs [conversion/pdf-a-required? (constantly false)]
      (is (= {:result {:archivable         false
                       :archivabilityError :permanent-archive-disabled}}
             (conversion/archivability-conversion metadata
                                                  {:organization "123-R"}
                                                  {:filename    "foo.pdf"
                                                   :contentType "application/pdf"
                                                   :fileId      (str (random-uuid))})))))

  (testing "PDF conversion - with archive => archivable"
    (with-redefs [conversion/pdf-a-required? (constantly true)]
      (let [file-id      (str (random-uuid))
            _            (storage/upload file-id
                                         "foo.pdf"
                                         "application/pdf"
                                         (io/file "dev-resources/invalid-pdfa.pdf")
                                         metadata
                                         :gcs)
            result       (conversion/archivability-conversion metadata
                                                              {:organization "123-R"}
                                                              {:filename    "foo.pdf"
                                                               :contentType "application/pdf"
                                                               :fileId      file-id})
            converted-id (-> result :file :fileId)]
        (is (match? {:result {:archivabilityError nil
                              :archivable         true
                              :autoConversion     true
                              :conversionLog      vector?}
                     :file   {:filename    "foo.pdf"
                              :contentType "application/pdf"
                              :fileId      string?
                              :size        pos-int?}}
                    result))
        (delete-file file-id)
        (delete-file converted-id))))

  (testing "TIFF conversion"
    (with-redefs [mongo/any?                                (fn [_coll query]
                                                              (condp = query
                                                                {:_id "000-R" :tiff-archiving-enabled true} false
                                                                {:_id "001-R" :tiff-archiving-enabled true} true
                                                                false))
                  conversion/pdf-a-required?                (constantly true)
                  laundry-client/do-check-image-properties! (constantly {:properties {:width        100
                                                                                      :height       100
                                                                                      :content-type "image/tiff"}})]
      (let [file-id         (str (random-uuid))
            _               (storage/upload file-id
                                            "foo.tif"
                                            "image/tiff"
                                            (io/file "dev-resources/test-tiff.tif")
                                            metadata
                                            :gcs)
            attachment      {:filename    "foo.tif"
                             :contentType "image/tiff"
                             :fileId      file-id}
            convert-fn      #(conversion/archivability-conversion metadata % attachment)
            disabled-result (-> {:organization "000-R"} convert-fn)
            enabled-result  (-> {:organization "001-R"} convert-fn)]
        (testing "TIFF archival disabled"
          (is (match? {:result {:archivabilityError nil
                                :archivable         true
                                :autoConversion     true}
                       :file   {:filename    "foo.pdf"
                                :contentType "application/pdf"
                                :fileId      string?
                                :size        pos-int?}}
                      disabled-result)))
        (testing "TIFF archival enabled"
          (is (= {:result {:archivabilityError :tiff-archiving
                           :archivable         false}}
                 enabled-result)))
        (delete-file file-id)
        (delete-file (-> disabled-result :file :fileId)))))

  (testing "JPEG conversion"
    (with-redefs [mongo/any?                                (fn [_coll query]
                                                              (condp = query
                                                                {:_id "000-R" :jpeg-archiving-enabled true} false
                                                                {:_id "001-R" :jpeg-archiving-enabled true} true
                                                                false))
                  conversion/pdf-a-required?                (constantly true)
                  laundry-client/do-check-image-properties! (constantly {:properties {:width        100
                                                                                      :height       100
                                                                                      :content-type "image/jpeg"}})]
      (let [file-id         (str (random-uuid))
            _               (storage/upload file-id
                                            "foo.jpg"
                                            "image/jpeg"
                                            (io/file "dev-resources/cake.jpg")
                                            metadata
                                            :gcs)
            attachment      {:filename    "foo.jpg"
                             :contentType "image/jpeg"
                             :fileId      file-id}
            convert-fn      #(conversion/archivability-conversion metadata % attachment)
            disabled-result (-> {:organization "000-R"} convert-fn)
            enabled-result  (-> {:organization "001-R"} convert-fn)]
        (testing "JPEG archival disabled => converted to PDF/A"
          (is (match? {:result {:archivabilityError nil
                                :archivable         true
                                :autoConversion     true}
                       :file   {:fileId      string?
                                :contentType "application/pdf"
                                :size        pos-int?
                                :filename    "foo.pdf"}}
                      disabled-result)))
        (testing "JPEG archival enabled"
          (is (= {:result {:archivabilityError :jpeg-archiving
                           :archivable         false}}
                 enabled-result)))
        (delete-file file-id)
        (delete-file (-> disabled-result :file :fileId)))))

  (testing "JPEG conversion, but laundry throws error"
    (with-redefs [mongo/any?                                (fn [_coll query]
                                                              (condp = query
                                                                {:_id "001-R" :jpeg-archiving-enabled true} true
                                                                false))
                  conversion/pdf-a-required?                (constantly true)
                  laundry-client/do-check-image-properties! (constantly {:error {:status 400
                                                                                 :body   "Failed to parse file according to: image/jpeg"}})]
      (let [file-id    (str (random-uuid))
            attachment {:filename    "foo.jpg"
                        :contentType "image/jpeg"
                        :fileId      file-id}
            convert-fn #(conversion/archivability-conversion metadata % attachment)]
        (testing "Error from laundry"
          (is (thrown-with-msg? Exception #"Failed to verify image"
                                (-> {:organization "001-R"} convert-fn :result)))))))

  (testing "JPEG conversion, but we don't accept results"
    (with-redefs [mongo/any?                                (fn [_coll query]
                                                              (condp = query
                                                                {:_id "001-R" :jpeg-archiving-enabled true} true
                                                                false))
                  conversion/pdf-a-required?                (constantly true)
                  laundry-client/do-check-image-properties! (constantly {:properties {:width        999999
                                                                                      :height       100
                                                                                      :content-type "image/jpeg"}})]
      (let [file-id    (str (random-uuid))
            attachment {:filename    "foo.jpg"
                        :contentType "image/jpeg"
                        :fileId      file-id}
            convert-fn #(conversion/archivability-conversion metadata % attachment)]
        (testing "Laundry parsed image but we don't accept it"
          (is (thrown-with-msg? Exception #"Invalid image"
                                (-> {:organization "001-R"} convert-fn :result))))))))
