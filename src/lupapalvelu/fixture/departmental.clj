(ns lupapalvelu.fixture.departmental
  "Tokens and organizations for departmental itest."
  (:require [lupapalvelu.fixture.core :refer [deffixture]]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.core :refer [now]]))

(def CLIENT "Both the client id and secret (from minimal)" "docdepartmental")

(defn make-token
  ([user-id token-id minutes-from-now client-id]
   (let [token {:id           token-id
                :token-type   :oauth-access
                :data         {:client-id client-id
                               :scopes    [:read]}
                :auto-consume false
                :created      (now)
                :expires      (+ (now) (* 1000 60 minutes-from-now))
                :user-id      user-id}]
     (mongo/insert :token token)
     token))
  ([apikey token-id]
   (make-token apikey token-id 1 CLIENT)))

(def bella
  {:id        "bella-id"
   :email     "bella.belastaja@example.org"
   :enabled   true
   :role      "authority"
   :language  "fi"
   :username  "bella"
   :orgAuthz  {:753-YA  #{:departmental}
               :753-R   #{:departmental}
               :123-YMP #{:departmental}}
   :firstName "Bella"
   :lastName  "Belastaja"
   :phone     "112"
   :street    "Paloasema 313"
   :zip       "33100"
   :city      "Tampere"
   :private   {:password "$2a$10$BG/RVbBIMF.Bv08u8QcRXeQP7MlTpeYJZsl4YCnGSd/AdQ0qorfyO"
               :apikey   "bella"}})

(deffixture "departmental" {}
  (mongo/clear!)
  (mongo/insert-batch :users (conj minimal/users bella))
  (mongo/insert-batch :companies minimal/companies)
  (mongo/insert-batch :organizations minimal/organizations)

  (make-token "51112424c26b7342d92acf3c" "dummy1") ; Not enabled
  (make-token "777777777777777777000020" "pena1")  ; Applicant
  (make-token "777777777777777777000023" "sonja1") ; Authority
  (make-token "laura-laskuttaja" "laura1" ) ; Biller
  (make-token "bella-id" "bella1") ; Departmental

  (mongo/update-by-id :organizations "753-YA"
                      {$push {:scope {$each [{:municipality            "007"
                                              :permitType              "YA"
                                              :inforequest-enabled     false
                                              :new-application-enabled false
                                              :open-inforequest        false}
                                             {:municipality            "008"
                                              :permitType              "KT"
                                              :inforequest-enabled     false
                                              :new-application-enabled false
                                              :open-inforequest        false}]}}}))
