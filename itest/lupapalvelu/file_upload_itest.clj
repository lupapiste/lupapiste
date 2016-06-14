(ns lupapalvelu.file-upload-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.application-bulletins-itest-util :as bulletin-util]
            [cheshire.core :as json]
            [sade.util :as util]
            [lupapalvelu.vetuma-itest-util :as vetuma-util]))

(apply-remote-minimal)

(facts "Upload and remove attachment for a bulletin comment"
  (let [store         (atom {})
        cookie-store  (doto (->cookie-store store)
                        (.addCookie test-db-cookie))
        upload-resp   (-> (bulletin-util/send-file cookie-store)
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
        (command pena :remove-uploaded-file :attachmentId (:fileId uploaded-file) :cookie-store cookie-store) => ok?)
      (fact "Attachment can not be deleted after linking it to post"
        (let [bulletin (bulletin-util/create-application-and-bulletin :cookie-store cookie-store)
              upload-resp   (-> (bulletin-util/send-file cookie-store)
                                :body
                                (json/decode keyword))
              uploaded-file (first (:files upload-resp))]

          upload-resp => ok?

          (vetuma-util/authenticate-to-vetuma! cookie-store)

          ;attach to comment
          (command sonja :add-bulletin-comment :bulletinId (:id bulletin) :bulletinVersionId (:versionId bulletin) :comment "foobar" :files [uploaded-file] :cookie-store cookie-store) => ok?

          ;try to remove
          (command pena :remove-uploaded-file :attachmentId (:fileId uploaded-file) :cookie-store cookie-store) => (partial expected-failure? :error.file-upload.already-linked))))))
