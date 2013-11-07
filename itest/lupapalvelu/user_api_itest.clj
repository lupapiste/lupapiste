(ns lupapalvelu.user-api-itest
  (:require [clojure.java.io :as io]
            [clojure.walk :refer [keywordize-keys]]
            [monger.operators :refer :all]
            [cheshire.core :as json]
            [midje.sweet :refer :all]
            [sade.http :as http]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]
            [lupapalvelu.user-api :as user-api]))

;;
;; ==============================================================================
;; Getting user and users:
;; ==============================================================================
;;

(facts "Getting user"
  (fact (query pena :user) => (contains {:user (contains {:email "pena@example.com"})})))

(facts "Getting users"
  (apply-remote-minimal)
  
  (fact "applicants are not allowed to call this"
    (query pena :users) =not=> ok?)
  
  ; It's not nice to test the number of users, but... well, this is relly easy:
  (fact (-> (query admin :users :role "admin") :users count) => 2)
  (fact (-> (query admin :users :organization "753-R") :users count) => 3)
  (fact (-> (query admin :users :role "authority" :organization "753-R") :users count) => 2))

(facts users-for-datatables
 (fact (command admin :users-for-datatables :params {:iDisplayLength 5 :iDisplayStart 0 :sEcho "123" :enabled "true" :organizations ["753-R"]})
   => (contains {:ok true
                 :data (contains {:rows (comp (partial = 3) count)
                                  :total 3
                                  :display 3
                                  :echo "123"})}))
 (fact (command admin :users-for-datatables :params {:iDisplayLength 5 :iDisplayStart 0 :sEcho "123" :enabled "true" :organizations ["753-R"] :filter-search "Suur"})
   => (contains {:ok true
                 :data (contains {:rows (comp (partial = 1) count)
                                  :total 3
                                  :display 1
                                  :echo "123"})})))
;;
;; ==============================================================================
;; Creating users:
;; ==============================================================================
;;
 
(facts create-user
  (apply-remote-minimal)
  (fact (command pena :create-user :email "x" :role "dummy" :password "foobarbozbiz") => fail?)
  (fact (command admin :create-user :email "x" :role "dummy" :password "foobarbozbiz") => ok?)
  ; Check that user was created
  (fact (-> (query admin :users :email "x") :users first) => (contains {:role "dummy" :email "x" :enabled false})))

;;
;; ==============================================================================
;; Updating user data:
;; ==============================================================================
;;

(facts update-user
  (apply-remote-minimal)
  (fact (command admin :create-user :email "foo" :role "applicant" :enabled "true" :apikey "xyz") => ok?)
  (fact (command "xyz" :update-user :firstName "f" :lastName "l") => ok?)
  (fact (-> (query "xyz" :user) :user) => (contains {:firstName "f" :lastName "l"})))

(facts update-user-organization
  (apply-remote-minimal)
  (fact (command admin :create-user :email "foo" :role "applicant" :enabled "true" :apikey "xyz") => ok?)
  (fact (-> (query "xyz" :user) :user :organizations) => nil?)
  (fact (command sipoo :update-user-organization :email "foo" :organization "753-R" :operation "add") => ok?)
  (fact (-> (query "xyz" :user) :user :organizations) = ["753-R"])
  (fact (command sipoo :update-user-organization :email "foo" :organization "753-R" :operation "remove") => ok?)
  (fact (-> (query "xyz" :user) :user :organizations) = [])
  
  (fact (command sipoo :update-user-organization :email "foo" :organization "123" :operation "add") => (contains {:ok false :text "forbidden"}))
  (fact (command sipoo :update-user-organization :email "foo" :organization "753-R" :operation "xxx") => (contains {:ok false :text "bad-request"})))

(fact "changing user info"
  (apply-remote-minimal)
  (fact (query teppo :user) => (every-checker ok? (contains
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
                                                        :zip "33560"})})))
  (fact
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
      (apply command teppo :update-user (flatten (seq data))) => ok?
      (query teppo :user) => (contains {:user (contains data)}))))

;;
;; historical tests, dragons be here...
;;

(defn upload-user-attachment [apikey attachment-type expect-to-succeed]
  (let [filename    "dev-resources/test-attachment.txt"
        uploadfile  (io/file filename)
        uri         (str (server-address) "/api/upload/user-attachment")
        resp        (http/post uri
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

