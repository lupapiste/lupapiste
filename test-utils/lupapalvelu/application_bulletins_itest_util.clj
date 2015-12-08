(ns lupapalvelu.application-bulletins-itest-util
  (:require [midje.sweet :refer :all]
            [clojure.java.io :as io]
            [lupapalvelu.itest-util :refer :all]
            [sade.util :as util]))

(defn send-file [cookie-store & [filename]]
  (set-anti-csrf! false)
  (let [filename    (or filename "dev-resources/sipoon_alueet.zip")
        uploadfile  (io/file filename)
        uri         (str (server-address) "/api/upload/file")
        resp        (http-post uri
                               {:multipart [{:name "files[]" :content uploadfile}]
                                :throw-exceptions false
                                :cookie-store cookie-store})]
    (set-anti-csrf! true)
    resp))
