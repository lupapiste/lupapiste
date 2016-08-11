(ns lupapalvelu.attachment.appeal-test
  (:require [schema.core :as sc]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.attachment.appeal :refer :all]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.mongo :as mongo]))

(testable-privates lupapalvelu.attachment.appeal create-appeal-attachment-data!)

(facts "appeal attachment updates"
       (against-background
         [(lupapalvelu.attachment.conversion/archivability-conversion anything anything) => {:archivable false
                                                                                             :archivabilityError :not-validated}]
         (fact "appeal-attachment-data"
               (let [file-id  (mongo/create-id)
                     file-obj {:content (constantly nil),
                               :content-type "application/pdf",
                               :size 123,
                               :file-name "test-pdf.pdf",
                               :metadata {:uploaded 12344567, :linked false},
                               :application nil
                               :fileId file-id}
                     command {:application {:state :verdictGiven}
                              :created 12345
                              :user {:id "foo" :username "tester" :role "authority" :firstName "Tester" :lastName "Testby"}}
                     result-attachment (create-appeal-attachment-data!
                                         command
                                         (mongo/create-id)
                                         :appeal
                                         file-obj)]
                 (fact "Generated attachment data is valid (no PDF/A generation)"
                       (sc/check att/Attachment result-attachment) => nil)
                 (fact "Version has correct keys"
                       (:latestVersion result-attachment) => (contains {:size (:size file-obj)
                                                                        :filename (:file-name file-obj)
                                                                        :contentType (:content-type file-obj)
                                                                        :fileId (:fileId file-obj)}))))))
