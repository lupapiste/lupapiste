(ns lupapalvelu.file-upload-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.application-bulletins :as bulletins]
            [lupapalvelu.application-bulletins-itest-util :as bulletin-util]
            [lupapalvelu.cookie :as c]
            [cheshire.core :as json]
            [lupapalvelu.vetuma-itest-util :as vetuma-util]))

(apply-remote-minimal)

(facts "User not authenticated in Vetuma"
  (let [store         (atom {})
        cookie-store  (doto (->cookie-store store)
                        (.addCookie test-db-cookie))]

    (fact "Upload is not allowed"
      (bulletin-util/send-file cookie-store) => http404?)))

(facts "Upload and remove attachment for a bulletin comment"
  (let [store         (atom {})
        cookie-store  (doto (->cookie-store store)
                        (.addCookie test-db-cookie))
        _ (vetuma-util/authenticate-to-vetuma! cookie-store)
        upload-resp   (-> (bulletin-util/send-file cookie-store)
                          :body
                          (json/decode keyword))
        uploaded-file (first (:files upload-resp))]

    (fact "Uploaded file is ok"
      upload-resp => ok?)

    (facts "Deleting"
      (set-anti-csrf! false)
      (fact "Must define attachment id"
        (command nil :remove-uploaded-file :cookie-store cookie-store) => (partial expected-failure? :error.missing-parameters))
      (fact "Attachment id must match with uploaded file"
        (command nil :remove-uploaded-file :attachmentId "asdasd" :cookie-store cookie-store) => (partial expected-failure? :error.file-upload.not-found))
      (fact "Attachment can be deleted"
        (command nil :remove-uploaded-file :attachmentId (:fileId uploaded-file) :cookie-store cookie-store) => ok?)
      (facts "Attachment can not be deleted after linking it to post"
        (let [bulletin (bulletin-util/create-application-and-bulletin :cookie-store cookie-store)
              upload-resp   (-> (bulletin-util/send-file cookie-store)
                                :body
                                (json/decode keyword))
              uploaded-file (-> (first (:files upload-resp))
                                (select-keys (keys bulletins/CommentFile)))]

          upload-resp => ok?

          ;attach to comment
          (set-anti-csrf! false)
          (fact "file can be attached to a comment"
            (command nil
                     :add-bulletin-comment
                     :bulletinId (:id bulletin)
                     :bulletinVersionId (:versionId bulletin)
                     :comment "foobar"
                     :files [uploaded-file]
                     :cookie-store cookie-store) => ok?)

          ;try to remove
          (fact "attached uploaded file cannot be removed"
            (command nil
                     :remove-uploaded-file
                     :attachmentId (:fileId uploaded-file)
                     :cookie-store cookie-store) => (partial expected-failure? :error.file-upload.not-found))))
      (set-anti-csrf! true))))
