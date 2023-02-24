(ns lupapalvelu.ident.ad-login-test
  (:require [lupapalvelu.user :as usr]
            [midje.util :refer [testable-privates]]
            [midje.sweet :refer :all]))

(testable-privates lupapalvelu.ident.ad-login
                   process-authz resolve-user!)

(facts "process-authz"
  (process-authz nil nil) => nil
  (process-authz {} {}) => nil
  (process-authz {:123-R ["a"]} {:456-R ["b"]}) => {:123-R ["a"] :456-R ["b"]}
  (process-authz {:123-R ["a"]} {:123-R ["b"]}) => {:123-R ["a"]}
  (process-authz {:123-R #{"a"}} {:123-R #{"b"}}) => {:123-R #{"a"}}
  (process-authz {:123-R #{}} {:123-R ["b"]}) => nil
  (process-authz {:123-R #{}} {:123-R ["b"]}) => nil
  (process-authz {:123-R #{} :123-YA ["a" "b"]}
                 {:123-R ["b"] :456-R ["c"]})
  => {:123-YA ["a" "b"] :456-R ["c"] })

(defn make-user
  ([& [authz]]
   (cond-> {:firstName "First"    :lastName "Last"
            :email     "my@email" :username "my@email"
            :enabled   true
            :role      (if authz "authority" "applicant")}
     authz (assoc :orgAuthz authz))))

(against-background
  [(usr/update-user-by-email "my@email" anything) => {:ok true}]
  (facts "resolve-user!"
   (fact "User disabled"
     (resolve-user! "First" "Last" "my@email" {:609-R ["reader"]}) => nil
     (provided (usr/get-user-by-email anything)
               => {:enabled false :role "authority"})
     (resolve-user! "First" "Last" "my@email" {:609-R ["reader"]}) => nil
     (provided (usr/get-user-by-email anything)
               => {:enabled false :role "applicant"}))
   (fact "Dummy user"
     (resolve-user! "First" "Last" "my@email" {:609-R ["reader"]}) => nil
     (provided (usr/get-user-by-email anything)
               => {:enabled true :role "dummy"}))
   (let [applicant    (make-user)
         adlicant     (assoc applicant :personIdSource "AD")
         hetulicant   (assoc applicant :personIdSource "identification-service")
         company-user (assoc-in (make-user) [:company :id] "yeah")
         authority    (make-user {:609-R ["reader"]})]
     (facts "Applicant"
       (fact "AD personIdSource"
         (resolve-user! "First" "Last" "My@Email" nil) => adlicant
         (provided
           (usr/get-user-by-email anything)
           => {:enabled true :role "applicant" :firstName "Hello"}))
       (fact "Existing personIdSource"
         (resolve-user! "First" "Last" "My@Email" nil) => hetulicant
         (provided
           (usr/get-user-by-email anything)
           => {:enabled        true :role "applicant" :firstName "Hello"
               :personIdSource "identification-service"})))
     (fact "Applicant, prmoted to authority"
       (resolve-user! "First" "Last" "My@Email" {:609-R ["reader"]})
       => authority
       (provided
         (usr/get-user-by-email anything)
         => {:enabled true :role "applicant" :firstName "Hello"}))
     (fact "Company user cannot be promoted to authority"
       (resolve-user! "First" "Last" "My@Email" {:609-R ["reader"]})
       => company-user
       (provided
         (usr/get-user-by-email anything)
         => {:enabled true :role "applicant" :firstName "Hello"
             :company {:id "yeah"}})))
   (let [authority         (make-user {:609-R  ["reader"]
                                       :753-YA ["authority"]})
         applicant         (make-user)
         adlicant          (assoc applicant :personIdSource "AD")
         hetulicant        (assoc applicant :personIdSource "identification-service")
         company-authority (assoc-in authority [:company :id] "yeah")
         company-user      (assoc-in (make-user) [:company :id] "yeah")
         sipoonly          (make-user {:753-YA ["authority"]})]
     (fact "Authority"
       (resolve-user! "First" "Last" "My@Email" {:609-R ["reader"]})
       => authority
       (provided
         (usr/get-user-by-email anything)
         => {:enabled  true :role "authority" :firstName "Hello"
             :orgAuthz {:609-R  ["approver"]
                        :753-YA ["authority"]}}))
     (fact "Authority, demoted to applicant, AD"
       (resolve-user! "First" "Last" "My@Email" nil) => adlicant
       (provided
         (usr/get-user-by-email anything)
         => {:enabled true :role "authority" :firstName "Hello"}))
     (fact "Authority, demoted to applicant, identification-service"
       (resolve-user! "First" "Last" "My@Email" nil) => hetulicant
       (provided
         (usr/get-user-by-email anything)
         => {:enabled true :role "authority" :firstName "Hello"
             :personIdSource "identification-service"}))
     (fact "Authority, no authz changes"
       (resolve-user! "First" "Last" "My@Email" nil) => authority
       (provided
         (usr/get-user-by-email anything)
         => {:enabled  true :role "authority" :firstName "Hello"
             :orgAuthz {:609-R  ["reader"]
                        :753-YA ["authority"]}}))
     (fact "Authority, remove authz"
       (resolve-user! "First" "Last" "My@Email" {:609-R []}) => sipoonly
       (provided
         (usr/get-user-by-email anything)
         => {:enabled  true :role "authority" :firstName "Hello"
             :orgAuthz {:609-R  ["reader"]
                        :753-YA ["authority"]}}))
     (fact "Company authority, remove authz"
       (resolve-user! "First" "Last" "My@Email" {:609-R []})
       => (merge company-authority sipoonly)
       (provided
         (usr/get-user-by-email anything)
         => {:enabled  true :role "authority" :firstName "Hello"
             :orgAuthz {:609-R  ["reader"]
                        :753-YA ["authority"]}
             :company  {:id "yeah"}}))
     (fact "Company authority, demoted to applicant"
       (resolve-user! "First" "Last" "My@Email"
                      {:609-R [] :753-YA #{}})
       => company-user
       (provided
         (usr/get-user-by-email anything)
         => {:enabled  true :role "authority" :firstName "Hello"
             :orgAuthz {:609-R  ["reader"]
                        :753-YA ["authority"]}
             :company  {:id "yeah"}})))
   (let [applicant (assoc (make-user) :id "new" :personIdSource "AD")
         authority (assoc (make-user {:609-R ["reader"]}) :id "new")]
     (fact "New authority"
       (resolve-user! "First" "Last" "My@Email" {:609-R ["reader"]})
       => authority
       (provided
         (usr/get-user-by-email anything) => nil
         (usr/create-new-user {:role :admin}
                              {:email "my@email" :role :dummy})
         => {:id "new"}))
     (fact "New applicant"
       (resolve-user! "First" "Last" "My@Email" {})
       => applicant
       (provided
         (usr/get-user-by-email anything) => nil
         (usr/create-new-user {:role :admin}
                              {:email "my@email" :role :dummy})
         => {:id "new"})))
   (fact "Mongo update fails (should not happen)"
     (resolve-user! "First" "Last" "Foo@Email" nil) => nil
     (provided
       (usr/get-user-by-email anything) => {:enabled true
                                            :role    "applicant"}
       (usr/update-user-by-email "foo@email" anything) => {:ok false}))))
