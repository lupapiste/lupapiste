(ns lupapalvelu.file-upload-itest
  (:require [lupapalvelu.application-bulletins :as bulletins]
            [lupapalvelu.application-bulletins-itest-util :as bulletin-util]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.json :as json]
            [lupapalvelu.vetuma-itest-util :as vetuma-util]
            [midje.sweet :refer :all]))

(set! *warn-on-reflection* true)

(with-test-system (-> {:fixture            "minimal"
                       :schema-validation? false}
                      test-system/with-remote-components)

  ;; Disable KuntaGML integration, so we don't have to add required fields for every application;
  ;; the KuntaGML generation for YM is already tested in krysp-itest
  (fact "Disable YM KuntaGML integration"
    (command sipoo :set-krysp-endpoint
             :organizationId "753-R"
             :permitType "YM"
             :username ""
             :password ""
             :url ""
             :version "") => ok?)

  (facts "User not authenticated in Vetuma"
    (let [store        (atom {})
          cookie-store (doto (->cookie-store store)
                         (.addCookie test-db-cookie))]

      (fact "Upload is not allowed"
        (bulletin-util/send-file cookie-store) => http404?)))

  (facts "Upload and remove attachment for a bulletin comment"
    (let [store         (atom {})
          cookie-store  (doto (->cookie-store store)
                          (.addCookie test-db-cookie))
          _             (vetuma-util/authenticate-to-vetuma! cookie-store)
          upload-resp   (-> (bulletin-util/send-file cookie-store)
                            :body
                            (json/decode true))
          uploaded-file (first (:files upload-resp))]

      (fact "Uploaded file is ok"
        upload-resp => ok?)

      (facts "Deleting"
        (without-anti-csrf
          (fact "Must define attachment id"
            (command nil :remove-uploaded-file :cookie-store cookie-store) => (partial expected-failure? :error.missing-parameters))
          (fact "Attachment id must match with uploaded file"
            (command nil :remove-uploaded-file :attachmentId "asdasd" :cookie-store cookie-store) => (partial expected-failure? :error.file-upload.not-found))
          (fact "Attachment can be deleted"
            (command nil :remove-uploaded-file :attachmentId (:fileId uploaded-file) :cookie-store cookie-store) => ok?)
          (facts "Attachment can not be deleted after linking it to post"
            (let [bulletin      (bulletin-util/create-application-and-bulletin :cookie-store cookie-store)
                  upload-resp   (-> (bulletin-util/send-file cookie-store)
                                    :body
                                    (json/decode true))
                  uploaded-file (-> (first (:files upload-resp))
                                    (select-keys (keys bulletins/CommentFile)))]

              upload-resp => ok?

              ;; attach to comment
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
                         :cookie-store cookie-store) => (partial expected-failure? :error.file-upload.not-found)))))))))
