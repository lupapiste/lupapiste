(ns lupapalvelu.file-upload-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [cheshire.core :as json]))

(apply-remote-minimal)

(facts "Upload and remove attachment for a bulletin comment"
  (let [store         (atom {})
        cookie-store  (doto (->cookie-store store)
                        (.addCookie test-db-cookie))
        upload-resp   (-> (send-file cookie-store)
                          :body
                          (json/decode keyword))
        uploaded-file (first (:files upload-resp))]

    (fact "Uploaded file is ok"
      upload-resp => ok?)

    (facts "Deleting"
      (fact "Must define attachment id"
        (command pena :remove-uploaded-file :cookie-store cookie-store) => (partial expected-failure? :error.missing-parameters))
      (fact "Attachment id must match with uploaded file"
        (command pena :remove-uploaded-file :attachmentId "asdasd" :cookie-store cookie-store) => (partial expected-failure? :error.file-upload.not-found))
      (fact "Attachment can be deleted"
        (command pena :remove-uploaded-file :attachmentId (:id uploaded-file) :cookie-store cookie-store) => ok?)
      )))