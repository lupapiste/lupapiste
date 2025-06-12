(ns lupapalvelu.fixture.departmental
  "Tokens and organizations for departmental and bi-app itest."
  (:require [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.system.util-components :as components]
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
   :oauth-roles [{:client-type "docdepartmental" :organization "753-YA" :role "departmental"}
                 {:client-type "docdepartmental" :organization "753-R" :role "departmental"}
                 {:client-type "docdepartmental" :organization "123-YMP" :role "departmental"}]
   :firstName "Bella"
   :lastName  "Belastaja"
   :phone     "112"
   :street    "Paloasema 313"
   :zip       "33100"
   :city      "Tampere"
   :private   {:password "$2a$10$BG/RVbBIMF.Bv08u8QcRXeQP7MlTpeYJZsl4YCnGSd/AdQ0qorfyO"
               :apikey   "bella"}})

(def oauth-clients
  [;; Bi-app
   {:id        "bi-app"
    :username  "bi-app"
    :email     "bi-app@example.com"
    :firstName "bi-app"
    :lastName  "API-user"
    :enabled   true
    :language  "fi"
    :role      "rest-api"
    :private   {:password "$2a$10$LqhU/xPaLEsiPYkIJlT3UuBkzZ0wJyLr.0NBcOAlaP4/DW7AHbeGy"} ; basicauth
    :oauth     {:client-id     "bi-app"
                :client-secret "bi-app"
                :client-version "bi-app"
                :scopes        ["read"]
                :display-name  {:fi "Lupapiste bi-app"
                                :sv "Lupapiste bi-app"
                                :en "Lupapiste bi-app"}
                :callback-url  "http://localhost:8014"}}
   ;; Bi-app2
   {:id        "bi-app2"
    :username  "bi-app2"
    :email     "bi-app2@example.com"
    :firstName "bi-app2"
    :lastName  "API-user"
    :enabled   true
    :language  "fi"
    :role      "rest-api"
    :private   {:password "$2a$10$LqhU/xPaLEsiPYkIJlT3UuBkzZ0wJyLr.0NBcOAlaP4/DW7AHbeGy"} ; basicauth
    :oauth     {:client-id     "bi-app2-this-can-be-anything"
                :client-type "bi-app"
                :client-version "bi-app2"
                :client-secret "bi-app2"
                :scopes        ["read"]
                :display-name  {:fi "Lupapiste bi-app 2"
                                :sv "Lupapiste bi-app 2"
                                :en "Lupapiste bi-app 2"}
                :callback-url  "http://localhost:8014"}}])

(defmethod fixture/fixture :departmental
  [_ system]
  (mongo/clear!)
  (mongo/insert-batch :users (-> minimal/users
                                 (conj bella)
                                 (concat oauth-clients)))
  (mongo/insert-batch :companies minimal/companies)
  (mongo/insert-batch :organizations (minimal/organizations {:http-port (components/system->http-port system)}))

  (make-token "51112424c26b7342d92acf3c" "dummy1") ; Not enabled
  (make-token "777777777777777777000020" "pena1")  ; Applicant
  (make-token "777777777777777777000023" "sonja1") ; Authority
  (make-token "laura-laskuttaja" "laura1" ) ; Biller
  (make-token "bella-id" "bella1") ; Departmental

  (make-token "777777777777777777000023" "sonja-bi-app" 1 "bi-app")  ; bi-app access-token for sonja
  (make-token "777777777777777777000023" "sonja-bi-app2" 1 "bi-app2-this-can-be-anything")  ; bi-app2 access-token for sonja

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
