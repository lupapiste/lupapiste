(ns lupapalvelu.attachment.multi-attachment-itest
  "Local mongo tests for `lupapalvelu.attachment.bind-attachments-api/resolve-multi-attachment-updates.`"
  (:require [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [mount.core :as mount]
            [sade.core :refer [now]]))

(def db-name (str "test_multi_attachment_" (now)))

(defn make-att [id & kvs]
  (merge {:id            id
          :latestVersion {:filename (str id "-file.pdf")}
          :auth [{:id pena-id
                  :role "uploader"}]
          :versions [{:filename (str id "-file.pdf")}]}
         (apply hash-map kvs)))


(mount/start #'mongo/connection)
(mongo/with-db db-name
  (fixture/apply-fixture "minimal")
  (with-local-actions
    (let [app-id     (:id (create-and-submit-local-application pena
                                                               :address "Multi Mansion"
                                                               :operation "pientalo"
                                                               :propertyId sipoo-property-id))
          good       (make-att "good" :applicationState "submitted")
          locked     (make-att "locked" :locked true)
          read-only  (make-att "read-only" :readOnly true)
          ;; Not realistic, but allowed by the data model
          not-needed (make-att "not-needed" :notNeeded true)
          dup1       (make-att "duplicate")
          dup2       (assoc (make-att "duplicate") :id "duplicate2")
          secret     (assoc (make-att "secret")
                            :auth [{:id sonja-id :role "uploader"}]
                            :metadata {:nakyvyys "viranomainen"})]
      (mongo/update-by-id :applications app-id
                          {$set {:attachments [good locked read-only not-needed
                                               dup1 dup2 secret]}})
      (fact "Only one suitable candidate"
        (command pena :resolve-multi-attachment-updates :id app-id
                 :files [{:fileId "f1" :filename "good-file.pdf"}
                         {:fileId "f2" :filename "missing-file.pdf"}
                         {:fileId "f3" :filename "locked-file.pdf"}
                         {:fileId "f4" :filename "read-only-file.pdf"}
                         {:fileId "f5" :filename "not-needed-file.pdf"}
                         {:fileId "f6" :filename "duplicate-file.pdf"}])
        => {:ok      true
            :updates [{:attachmentId "good" :fileId "f1"}]})
      (fact "Same filenames"
        (command pena :resolve-multi-attachment-updates :id app-id
                 :files [{:fileId "f1" :filename "good-file.pdf"}
                         {:fileId "f2" :filename "good-file.pdf"}
                         ])
        => {:ok      true
            :updates []})
      (facts "Pre-verdict attachment in post-verdict application"
        (mongo/update-by-id :applications app-id {$set {:state "constructionStarted"}})
        (fact "Authority"
          (command sonja :resolve-multi-attachment-updates :id app-id
                   :files [{:fileId "f1" :filename "good-file.pdf"}])
          => {:ok      true
              :updates [{:attachmentId "good" :fileId "f1"}]})
        (fact "Applicant"
          (command pena :resolve-multi-attachment-updates :id app-id
                   :files [{:fileId "f1" :filename "good-file.pdf"}])
          => {:ok      true
              :updates []}))

      (facts "Attachment visibility"
        (fact "Not visible to Pena"
          (command pena :resolve-multi-attachment-updates :id app-id
                   :files [{:fileId "f1" :filename "secret-file.pdf"}])
          => {:ok      true
              :updates []})
        (fact "Visible to authority"
          (command ronja :resolve-multi-attachment-updates :id app-id
                   :files [{:fileId "f1" :filename "secret-file.pdf"}])
          => {:ok      true
              :updates [{:attachmentId "secret" :fileId "f1"}]}))

      (fact "Bad params"
        (command pena :resolve-multi-attachment-updates :id app-id
                 :files [{:foo "bar"}])
        => {:ok false :text "error.illegal-value:schema-validation"}))))
