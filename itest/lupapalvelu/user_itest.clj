(ns lupapalvelu.user-itest
  (:require [lupapalvelu.itest-util :refer :all]
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
              :fise "f"
              :companyName "cn"
              :companyId "cid"}]

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
  (let [resp (decode-response (c/get (str (server-address) "/dev/user")
                                     {:headers {"authorization" (str "apikey=" apikey)}}))]
    (assert (= 200 (:status resp)))
    (:body resp)))

(defn file-info [id]
  (let [resp (c/get (str (server-address) "/dev/fileinfo/" id) {:throw-exceptions false})]
    (when (= 200 (:status resp))
      (:body (decode-response resp)))))

(facts "uploading user attachment"
  (apply-remote-minimal)

  ;
  ; Initially pena does not have attachments?
  ;

  (fact "Initially pena does not have attachments?" (:attachments (query pena "user-attachments")) => nil?)

  ;
  ; Pena uploads an examination:
  ;

  (let [attachment-id (:attachment-id (upload-user-attachment pena "examination" true))]
    
    ; Now Pena has attachment

    (get-in (query pena "user-attachments") [:attachments (keyword attachment-id)]) => (contains {:attachment-id attachment-id :attachment-type "examination"})

    ; Attachment is in GridFS

    (let [resp (raw pena "download-user-attachment" :attachment-id attachment-id)]
      (:status resp) => 200
      (:body resp) => "This is test file for file upload in itest.")

    ; Sonja can not get attachment
    
    (let [resp (raw sonja "download-user-attachment" :attachment-id attachment-id)]
      (:status resp) => 404)

    ; Sonja can not delete attachment
    
    (command sonja "remove-user-attachment" :attachment-id attachment-id)
    (get-in (query pena "user-attachments") [:attachments (keyword attachment-id)]) =not=> nil?
    
    ; Pena can delete attachment
    
    (command pena "remove-user-attachment" :attachment-id attachment-id)
    (get-in (query pena "user-attachments") [:attachments (keyword attachment-id)]) => nil?))

; Creating duplicate authority adds organization (don't blame me, that's how I found this)

(facts "creating duplicate authority adds organization"
  (apply-remote-minimal)
  
  (fact "User rakennustarkastaja@naantali.fi has organization"
    (-> (query "a0ac77ecd2e6c2ea6e73f840" :user) :user) => (contains {:email "rakennustarkastaja@naantali.fi"
                                                                      :organizations ["529-R"]}))
  
  (fact "User rakennustarkastaja@naantali.fi does not belong to Sipoo rak.val"
    (->> (query sipoo :authority-users)
      :users
      (map :email)) =not=> (contains "rakennustarkastaja@naantali.fi"))
  
  (fact
    (command sipoo :create-authority-user :email "rakennustarkastaja@naantali.fi"
                                          :firstName "xxx"
                                          :lastName "yyy"
                                          :password "zzzzzzzz") => ok?)
  
  (fact "User rakennustarkastaja@naantali.fi does now belong to Sipoo rak.val"
    (->> (query sipoo :authority-users)
      :users
      (map :email)) => (contains "rakennustarkastaja@naantali.fi"))
  
  (fact "User rakennustarkastaja@naantali.fi has organization"
    (-> (query "a0ac77ecd2e6c2ea6e73f840" :user) :user) => (contains {:email "rakennustarkastaja@naantali.fi"
                                                                      :organizations ["529-R" "753-R"]
                                                                      :firstName "Rakennustarkastaja"
                                                                      :lastName "Naantali"})))
