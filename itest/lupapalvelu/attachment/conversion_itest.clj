(ns lupapalvelu.attachment.conversion-itest
  (:require [clj-uuid :as uuid]
            [clojure.java.io :as io]
            [lupapalvelu.attachment.conversion :as conversion]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.storage.file-storage :as storage]
            [midje.sweet :refer :all]
            [mount.core :as mount]))

(mount/start #'mongo/connection)

(def application-id "LP-999-2021-12345")

(defn delete-file [file-id]
  (storage/delete-app-file-from-storage application-id
                                        file-id
                                        :gcs))

(facts "Conversion"
  (fact "invalid mime"
    (conversion/archivability-conversion
      {} {} {:contentType "image/gif"
             :filename    "foo.foo"
             :fileId      (str (uuid/v1))})
    => {:result {:archivable         false
                 :archivabilityError :invalid-mime-type}})
  (facts "TXT conversion"
    (let [file-id      (str (uuid/v1))
          metadata     {:application application-id}
          _            (storage/upload file-id
                                       "test-attachment.txt"
                                       "text/plain"
                                       (io/file "dev-resources/test-attachment.txt")
                                       metadata
                                       :gcs)
          result       (conversion/archivability-conversion
                         metadata {:organization {}} {:filename    "test-attachment.txt"
                                                      :contentType "text/plain"
                                                      :fileId      file-id})
          converted-id (-> result :file :fileId)]
      (:result result) => (just {:archivabilityError nil
                                 :archivable         true
                                 :autoConversion     true})
      (:file result) => (just {:filename    "test-attachment.pdf"
                               :contentType "application/pdf"
                               :fileId      string?
                               :size        pos-int?})
      (delete-file file-id)
      (delete-file converted-id)))

  (fact "PDF conversion - no archive => not-validated"
    (conversion/archivability-conversion
      {} {:organization {}} {:filename    "foo.pdf"
                             :contentType "application/pdf"
                             :fileId      (str (uuid/v1))})
    => {:result {:archivable         false
                 :archivabilityError :permanent-archive-disabled}}
    (provided
      (conversion/pdf-a-required? anything) => false))

  (facts "PDF conversion - with archive => archivable"
    (let [file-id        (str (uuid/v1))
          metadata       {:application application-id}
          _              (storage/upload file-id
                                         "foo.pdf"
                                         "application/pdf"
                                         (io/file "dev-resources/invalid-pdfa.pdf")
                                         metadata
                                         :gcs)
          result         (conversion/archivability-conversion
                           metadata {:organization {}} {:filename    "foo.pdf"
                                                        :contentType "application/pdf"
                                                        :fileId      file-id})
          converted-id   (-> result :file :fileId)]
      (:result result) => (just {:archivabilityError nil
                                 :archivable         true
                                 :autoConversion     true})
      (:file result) => (just {:filename    "foo.pdf"
                               :contentType "application/pdf"
                               :fileId      string?
                               :size        pos-int?})
      (delete-file file-id)
      (delete-file converted-id))
    (against-background
      (conversion/pdf-a-required? anything) => true)))
