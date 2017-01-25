(ns lupapalvelu.file-upload-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.file-upload :refer :all]
            [clojure.java.io :as io]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.attachment.muuntaja-client :as muuntaja]))

(facts "about uploading a zipped attachment collection with index file"
  (let [op1 "maverick"
        op2 "goose"
        op3 "iceman"
        op4 "merlin"
        application {:primaryOperation {:id op1}
                     :secondaryOperations [{:id op2}
                                           {:id op3}
                                           {:id op4}]
                     :documents [{:schema-info {:op {:id op1}}
                                  :data {:tunnus {:value "A"}}}
                                 {:schema-info {:op {:id op2}}
                                  :data {:valtakunnallinenNumero {:value "B"}}}]
                     :buildings [{:operationId op3
                                  :nationalId "C"}
                                 {:operationId op4
                                  :buildingId "D"}]}
        session-id "abc"
        file-id "badger"
        length 54321
        content (io/file (io/resource "test-pdf.pdf"))
        file1 {:filename "foobar.pdf"
               :content  content}
        file2 {:filename "foobar.zip"
               :tempfile content}
        contents "test contents"]

    (against-background
      [(mongo/create-id) => file-id
       (mongo/upload anything anything anything anything anything) => {:length length}]

      (fact "non-zip is saved normally"
        (save-files application [file1] session-id) => [{:fileId file-id
                                                         :filename "foobar.pdf"
                                                         :size length
                                                         :contentType "application/pdf"
                                                         :metadata {:linked false, :sessionId "abc"}}])

      (against-background
        [(muuntaja/unzip-attachment-collection content) => nil]

        (fact "non-attachment-collection zip is saved normally"
          (save-files application [file2] session-id) => [{:fileId file-id
                                                           :filename "foobar.zip"
                                                           :size length
                                                           :contentType "application/zip"
                                                           :metadata {:linked false, :sessionId "abc"}}]))

      (against-background
        [(muuntaja/unzip-attachment-collection content) => {:attachments [{:file "/path/to/foobar.pdf"
                                                                           :filename "foobar1.pdf"
                                                                           :localizedType "Pohjapiirustus"
                                                                           :contents contents
                                                                           :drawingNumber "1"
                                                                           :operation "A"}
                                                                          {:file "/path/to/foobar.pdf"
                                                                           :filename "foobar2.pdf"
                                                                           :localizedType "Pohjapiirustus"
                                                                           :contents contents
                                                                           :drawingNumber "2"
                                                                           :operation "B"}
                                                                          {:file "/path/to/foobar.pdf"
                                                                           :filename "foobar3.pdf"
                                                                           :localizedType "Julkisivupiirustus"
                                                                           :contents contents
                                                                           :drawingNumber "3"
                                                                           :operation "C,D"}
                                                                          {:file "/path/to/foobar.pdf"
                                                                           :filename "foobar4.pdf"
                                                                           :localizedType "Asemapiirros"
                                                                           :contents contents
                                                                           :drawingNumber "4"}
                                                                          {:file "/path/to/foobar.pdf"
                                                                           :filename "foobar5.pdf"
                                                                           :localizedType "CV"
                                                                           :contents contents}]}
         (muuntaja/download-file "/path/to/foobar.pdf") => (io/input-stream content)]

        (fact "attachment collection zip is parsed and stored as files"
          (save-files application [file2] session-id) => [{:filename "foobar1.pdf"
                                                           :contentType "application/pdf"
                                                           :contents contents
                                                           :drawingNumber "1"
                                                           :fileId file-id
                                                           :size length
                                                           :type {:metadata {:grouping :operation}
                                                                  :type-group :paapiirustus
                                                                  :type-id :pohjapiirustus}
                                                           :metadata {:linked false, :sessionId "abc"}
                                                           :group {:groupType :operation
                                                                   :operations [{:groupType :operation
                                                                                 :id op1}]}}
                                                          {:filename "foobar2.pdf"
                                                           :contentType "application/pdf"
                                                           :contents contents
                                                           :drawingNumber "2"
                                                           :fileId file-id
                                                           :size length
                                                           :type {:metadata {:grouping :operation}
                                                                  :type-group :paapiirustus
                                                                  :type-id :pohjapiirustus}
                                                           :metadata {:linked false, :sessionId "abc"}
                                                           :group {:groupType :operation
                                                                   :operations [{:groupType :operation
                                                                                 :id op2}]}}
                                                          {:filename "foobar3.pdf"
                                                           :contentType "application/pdf"
                                                           :contents contents
                                                           :drawingNumber "3"
                                                           :fileId file-id
                                                           :size length
                                                           :type {:metadata {:grouping :operation}
                                                                  :type-group :paapiirustus
                                                                  :type-id :julkisivupiirustus}
                                                           :metadata {:linked false, :sessionId "abc"}
                                                           :group {:groupType :operation
                                                                   :operations [{:groupType :operation
                                                                                 :id op3}
                                                                                {:groupType :operation
                                                                                 :id op4}]}}
                                                          {:filename "foobar4.pdf"
                                                           :contentType "application/pdf"
                                                           :contents contents
                                                           :drawingNumber "4"
                                                           :fileId file-id
                                                           :size length
                                                           :type {:metadata {:grouping :operation
                                                                             :multioperation true}
                                                                  :type-group :paapiirustus
                                                                  :type-id :asemapiirros}
                                                           :metadata {:linked false, :sessionId "abc"}
                                                           :group {:groupType :operation
                                                                   :operations (map #(hash-map :groupType :operation :id %) [op1 op2 op3 op4])}}
                                                          {:filename "foobar5.pdf"
                                                           :contentType "application/pdf"
                                                           :contents contents
                                                           :fileId file-id
                                                           :size length
                                                           :drawingNumber nil
                                                           :type {:metadata {:grouping :parties
                                                                             :for-operations #{:tyonjohtajan-nimeaminen-v2 :tyonjohtajan-nimeaminen}}
                                                                  :type-group :osapuolet
                                                                  :type-id :cv}
                                                           :metadata {:linked false, :sessionId "abc"}
                                                           :group {:groupType :parties}}])))))
