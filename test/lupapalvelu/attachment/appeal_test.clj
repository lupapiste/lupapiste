(ns lupapalvelu.attachment.appeal-test
  (:require [schema.core :as sc]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.attachment.appeal :refer :all]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.attachment.preview :as preview]
            [clj-uuid :as uuid]
            [lupapalvelu.storage.file-storage :as storage]
            [clojure.java.io :as io]))

(testable-privates lupapalvelu.attachment.appeal create-appeal-attachment-data!)

(facts "appeal attachment updates"
  (let [file-id  (str (uuid/v1))
        file-obj {:content     (fn [] (io/input-stream (io/resource "test-pdf.pdf")))
                  :contentType "application/pdf",
                  :size        123,
                  :filename    "test-pdf.pdf",
                  :metadata    {:uploaded 12344567, :linked false},
                  :application nil
                  :fileId      file-id}
        app-id "LP-186-2018-90001"
        command {:application {:state :verdictGiven
                               :id app-id}
                 :created 12345
                 :user {:id "foo" :username "tester" :role "authority" :firstName "Tester" :lastName "Testby"}}]
    (against-background
      [(lupapalvelu.attachment.conversion/archivability-conversion anything anything anything) => {:archivable false
                                                                                                   :archivabilityError :not-validated}
       (preview/preview-image anything anything) => nil
       (storage/link-files-to-application "foo" app-id [(:fileId file-obj)]) => 1]
      (facts "appeal-attachment-data"
        (let [result-attachment (create-appeal-attachment-data!
                                  command
                                  (mongo/create-id)
                                  :appeal
                                  file-obj)]
          (fact "Generated attachment data is valid (no PDF/A generation)"
            (sc/check att/Attachment result-attachment) => nil)
          (fact "Version has correct keys"
            (:latestVersion result-attachment) => (contains {:size (:size file-obj)
                                                             :filename (:filename file-obj)
                                                             :contentType (:contentType file-obj)
                                                             :fileId (:fileId file-obj)})))))))

(facts "appeal attachments"
  (let [attachments [{:id "foo"
                      :target {:id "appeal1"
                               :type "appeal"}}
                     {:id "faa"
                      :target {:id "test2"
                               :type "verdict"}}
                     {:id "fuu"
                      :target nil}
                     {:id "bar"
                      :target {:id "appeal2"
                               :type "appeal"}}
                     {:id "baf"
                      :target {:id "appeal2"
                               :type "appealVerdict"}}
                     {:id "bof"
                      :target {:id "appeal2"
                               :type "rectification"}}]]
    (appeals-attachments {:attachments attachments} nil) => empty?
    (appeals-attachments {:attachments attachments} []) => empty?
    (appeals-attachments {:attachments attachments} "foo") => empty?
    (appeals-attachments {:attachments attachments} ["foo" "faa"]) => empty?
    (appeals-attachments {:attachments attachments} ["appeal1" "faa"]) => (just (nth attachments 0))
    (appeals-attachments {:attachments attachments} ["appeal1" "appeal2" nil]) => (just [(nth attachments 0)
                                                                                         (nth attachments 3)
                                                                                         (nth attachments 4)
                                                                                         (nth attachments 5)])))
