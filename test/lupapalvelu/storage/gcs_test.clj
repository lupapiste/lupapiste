(ns lupapalvelu.storage.gcs-test
  (:require [midje.sweet :refer :all]
            [clj-uuid :as uuid]
            [clj-http.client :as http]
            [lupapalvelu.storage.gcs :as target]))

(facts "init-resumable-upload does following things

  1) It generates file-id and file-version-id when needed.
  2) It initalize resumable upload by making a POST to a signed-url

  It returns generated identifier and the upload url."
  (let [;; Pass string as credentials in unit test. This works as long as function call scheme is not validated.
        ;; NOTE: In reality GoogleCredentials is used!
        crededentials "fake-credential"
        bucket "test-bucket"
        origin "http://localhost:8000"
        new-file-input {:content-type "application/step"
                        :md5-digest   "diggest"
                        :user-id      "user-id"
                        :file-id      nil}
        new-file-version-input {:content-type "application/step"
                                :md5-digest   "diggest"
                                :user-id      "user-id"
                                :file-id      "exising-file-id"}
        signed-url "signed-url"
        access-token "jwt"
        upload-url "upload-url"
        expected-path "/test-bucket/user-id/generated-id"
        generated-id "generated-id"]
    (fact "Case 1: new file.
           It automatically generates file-id and file-version-id when file-id is not given"
      (target/init-resumable-upload crededentials bucket origin new-file-input)
      => {:content-type    "application/step"
          ;; the file-id and file-version-id will be differnt. They are same just because of the mock
          :file-id         generated-id
          :file-version-id generated-id
          :upload-url      upload-url}
      (provided (uuid/v1) => "generated-id"
                (target/create-signed-url crededentials expected-path new-file-input) => signed-url
                (target/get-access-token anything) => access-token
                (http/post signed-url anything)
                => {:headers {"Location" upload-url}}))
    (fact "Case 2: New version to and existing file
           When file-id is passed to the init-resumable-upload it is returned. Version id is generated always."
      (target/init-resumable-upload crededentials bucket origin new-file-version-input)
      => {:content-type    "application/step"
          :file-id         "exising-file-id"
          :file-version-id generated-id
          :upload-url      upload-url}
      (provided (uuid/v1) => "generated-id"
                (target/create-signed-url crededentials expected-path new-file-version-input) => signed-url
                (target/get-access-token anything) => access-token
                (http/post signed-url anything)
                => {:headers {"Location" upload-url}}))))
