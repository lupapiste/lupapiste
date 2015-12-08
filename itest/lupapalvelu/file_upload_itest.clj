(ns lupapalvelu.file-upload-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [cheshire.core :as json]))

(facts "Upload and remove attachment for a bulletin comment"
  (let [store        (atom {})
        cookie-store (doto (->cookie-store store)
                       (.addCookie test-db-cookie))
        upload-resp  (-> (send-file cookie-store)
                         :body
                         (json/decode keyword))]

    (fact "Uploaded file is ok"
      upload-resp => ok?)))