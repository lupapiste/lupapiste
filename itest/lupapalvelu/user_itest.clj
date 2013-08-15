(ns lupapalvelu.user-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [midje.sweet :refer :all]
            [clojure.pprint :refer [pprint]]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [clojure.walk :refer [keywordize-keys]]
            [clj-http.client :as c]))

(fact "changing user info"
  (apply-remote-minimal)
  (let [resp (query teppo :user)]
    resp => ok?
    resp => (contains
              {:user
               (just
                 {:city "Tampere"
                  :email "teppo@example.com"
                  :enabled true
                  :firstName "Teppo"
                  :id "5073c0a1c2e6c470aef589a5"
                  :lastName "Nieminen"
                  :personId "210281-0001"
                  :phone "0505503171"
                  :postalCode "33200"
                  :role "applicant"
                  :street "Mutakatu 7"
                  :username "teppo@example.com"
                  :zip "33560"})}))

  (let [data {:firstName "Seppo"
              :lastName "Sieninen"
              :street "Sutakatu 7"
              :city "Sampere"
              :zip "33200"
              :phone "0505503171"
              :architect true
              :degree "d"
              :experience 5
              :fise "f"
              :qualification "q"
              :companyName "cn"
              :companyId "cid"
              :companyStreet "cs"
              :companyZip "cz"
              :companyCity "cc"}]

  (apply command teppo :save-user-info (flatten (seq data))) => ok?
  (query teppo :user) => (contains {:user (contains data)})))

(defn upload-user-attachment [apikey attachment-type expect-to-succeed]
  (let [filename    "dev-resources/test-attachment.txt"
        uploadfile  (io/file filename)
        uri         (str (server-address) "/api/upload/user-attachment")
        resp        (c/post uri
                      {:headers {"authorization" (str "apikey=" apikey)}
                       :multipart [{:name "attachmentType"  :content attachment-type}
                                   {:name "files[]"         :content uploadfile}]})
        body        (-> resp :body json/parse-string keywordize-keys)]
    (if expect-to-succeed
      (facts "successful"
        (:status resp) => 200
        body => (contains {:ok true}))
      (facts "should fail"
        (:status resp) =not=> 200
        body => (contains {:ok false})))
    body))

(defn current-user [apikey]
  (let [resp (c/get
               (str (server-address) "/dev/user")
               {:headers {"authorization" (str "apikey=" apikey)}})]
    (fact (:status resp) => 200)
    (-> resp :body json/parse-string keywordize-keys)))

(facts "uploading user attachment"
  (apply-remote-minimal)

  ;
  ; Initially pena does not have examination?
  ;

  (fact "Initially pena does not have examination?" (:examination (current-user pena)) => nil?)

  ;
  ; Pena uploads an examination:
  ;

  (upload-user-attachment pena "examination" true)
  (let [file-1 (get-in (current-user pena) [:attachment :examination])
        att-1 (mongo/download (:file-id file-1))]
    (fact "filename of file 1"    file-1  => (contains {:filename "test-attachment.txt"}))
    (fact "db has same filename"  att-1   => (contains {:file-name "test-attachment.txt"}))
    (fact "file 1 metadata"       att-1   => (contains {:metadata (contains {:user-id pena-id :attachment-type "examination"})}))

    ;
    ; Pena updates the examination:
    ;

    (upload-user-attachment pena "examination" true)
    (let [file-2 (get-in (current-user pena) [:attachment :examination])
          att-2 (mongo/download (:file-id file-2))]
      (fact "filename of file 2"   file-2  => (contains {:filename "test-attachment.txt"}))
      (fact "db has same filename" att-2   => (contains {:file-name "test-attachment.txt"}))
      (fact "file 2 metadata"      att-2   => (contains {:metadata (contains {:user-id pena-id :attachment-type "examination"})})))

    (fact "old file is deleted"  (mongo/download (:file-id file-1)) => nil?)))
