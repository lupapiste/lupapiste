(ns lupapalvelu.file-upload-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [cheshire.core :as json]))

(facts "Upload and remove attachment for a bulletin comment"
  (let [store        (atom {})
        cookie-store (doto (->cookie-store store)
                       (.addCookie test-db-cookie))]

    (fact "Upload file"
      (let [upload-resp (send-file cookie-store)]
        (json/decode (:body upload-resp) keyword) => ok?))))