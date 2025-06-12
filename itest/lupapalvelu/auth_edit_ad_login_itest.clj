(ns lupapalvelu.auth-edit-ad-login-itest
  (:require [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [sade.core :refer [fail ok]]))

;; This namespace tests whether an authorityAdmin can or cannot edit the roles
;; and information of an authority that uses AD login.

(def ^:private sipoo-R-org-id "753-R")
(def ^:private pori-R-org-id "609-R")

(def terttu-email "terttu.panaani@pori.fi")
(def priscilla-email "priscilla.panaani@pori.fi")

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)

  ;; Create a scenario in which Terttu has already logged in and received
  ;; permissions from AD. Actual SSO login is tested in
  ;; lupapalvelu.ident.ad-login-itest
  (let [terttu-id (:id (command admin :create-user
                                :email "terttu.panaani@pori.fi"
                                :role "authority"
                                :orgAuthz {:609-R ["authority" "reader"]}))]
    (command admin :set-user-enabled :email terttu-email :enabled true)

    ;; Set Rami2 enabled for pori and sipoo
    (command admin :set-organization-reporting-system :org-id sipoo-R-org-id :reporting-system "bi-app2")
    (command admin :set-organization-reporting-system :org-id pori-R-org-id :reporting-system "bi-app2")

    (facts "User's domain is forced to ad-login"
                (fact "User's roles and information CANNOT be modified in organization belonging to ad configuration"
                  (query pori :user-for-edit-authority
                         :organizationId pori-R-org-id
                         :authority-id terttu-id)
                  => (contains {:ok   true
                                :data (contains {:username                            "terttu.panaani@pori.fi"
                                                 :ad-login?                           true
                                                 :current-org-roles-maintained-in-ad? true})})

                  (command pori :update-user-roles :organizationId pori-R-org-id
                           :email terttu-email :roles ["authority"] :oauth-roles [])
                  => (fail :error.user-roles-maintained-in-ad)

                  (command pori :update-auth-info :organizationId pori-R-org-id
                           :email terttu-email :firstName "Terttu" :lastName "Panaani" :new-email "terttu@pori.fi")
                  => (fail :error.user-information-maintained-in-ad))

                (fact "User's roles CAN be modified in organization NOT belonging to ad configuration"
                  (command sipoo :upsert-organization-user :organizationId sipoo-R-org-id
                           :email "terttu.panaani@pori.fi" :firstName "Terttu" :lastName "Panaani" :roles ["authority" "approver"])
                  => (ok :operation "add")

                  (let [result (query sipoo :user-for-edit-authority
                                      :organizationId sipoo-R-org-id
                                      :authority-id terttu-id)]
                    result => (contains {:ok   true
                                         :data (contains {:username  "terttu.panaani@pori.fi"
                                                          :ad-login? true})})
                    (-> result :data :current-org-roles-maintained-in-ad?) => false)

                  (command sipoo :update-user-roles :organizationId sipoo-R-org-id
                           :email terttu-email :roles ["authority" "reader"] :oauth-roles ["reporter"])
                  => (ok)))

    (facts "User's domain is enabled but not forced to ad-login"
      (update-pori-ad-login {:forced false})

           (fact "User's roles CAN be modified when user has no ad enabled"
             (let [result (query pori :user-for-edit-authority
                                 :organizationId pori-R-org-id
                                 :authority-id terttu-id)]
               result => (contains
                           {:ok   true
                            :data (contains {:username "terttu.panaani@pori.fi"})})
               (-> result :data :ad-login?) => false
               (-> result :data :current-org-roles-maintained-in-ad?) => false)

             (command pori :update-user-roles :organizationId pori-R-org-id
                      :email terttu-email :roles ["authority"] :oauth-roles ["reporter"])
             => (ok))

           (fact "User's roles CANNOT be modified when user has ad enabled"
             ;; In the minimal fixture priscilla's username is "priscilla" but for the AD to work properly, the username is
             ;; required to be same as email. Command :update-auth-info also changes the username to "priscilla.panaani@pori.fi"
             (command pori :update-auth-info :organizationId pori-R-org-id
                      :email priscilla-email :firstName "Priscilla" :lastName "Panaani" :new-email priscilla-email)
             => (ok)

             (command pori :update-user-roles :organizationId pori-R-org-id
                      :email priscilla-email :roles ["authority"] :oauth-roles ["reporter"])
             => (ok)

             (command priscilla :connect-sso) => (contains {:ok true})

             (query pori :user-for-edit-authority
                    :organizationId pori-R-org-id
                    :authority-id priscilla-id)
             => (contains {:ok   true
                           :data (contains {:username                            "priscilla.panaani@pori.fi"
                                            :ad-login?                           true
                                            :current-org-roles-maintained-in-ad? true})})

             (command pori :update-user-roles :organizationId pori-R-org-id
                      :email priscilla-email :roles ["authority"] :oauth-roles [])
             => (fail :error.user-roles-maintained-in-ad)

             (command pori :update-auth-info :organizationId pori-R-org-id
                      :email priscilla-email :firstName "Terttu" :lastName "Panaani" :new-email "terttu@pori.fi")
             => (fail :error.user-information-maintained-in-ad))))

  (facts "Oauth users"
       (query pori :get-oauth-users :organizationId pori-R-org-id)
       => (just {:ok    true
                 :users (just [(contains {:email                               "terttu.panaani@pori.fi"
                                          :current-org-roles-maintained-in-ad? false})
                               (contains {:email                               "priscilla.panaani@pori.fi"
                                          :current-org-roles-maintained-in-ad? true})] :in-any-order)})

       (fact "User's that have ad-login enabled cannot be given oauth-roles in org that belongs to ad conf"
         (command pori :update-user-oauth-roles :organizationId pori-R-org-id
                  :email priscilla-email :oauth-roles ["reporter" "director"])
         => (fail :error.user-roles-maintained-in-ad)

         (command pori :update-user-oauth-roles :organizationId pori-R-org-id
                  :email terttu-email :oauth-roles ["reporter" "director"])
         => (ok))

       (fact "Oauth roles can be edited in organization that does not belong to ad configuration"
         (command sipoo :update-user-oauth-roles :organizationId sipoo-R-org-id
                  :email priscilla-email :oauth-roles ["reporter" "director"])
         => (ok)))

  (facts "Upsert organization user (Add authority)"
     (fact "User using AD login cannot be added"
       (command pori :remove-user-organization :organizationId pori-R-org-id :email priscilla-email) => (ok)
       (command pori :upsert-organization-user :organizationId pori-R-org-id
                :email priscilla-email :firstName "Priscilla" :lastName "Panaani" :roles ["authority" "approver"])
       => (fail :error.user-roles-maintained-in-ad))

     (fact "User using AD can be added to organization not belonging to ad configuration"
       (command sipoo :upsert-organization-user :organizationId sipoo-R-org-id
                :email priscilla-email :firstName "Priscilla" :lastName "Panaani" :roles ["authority" "approver"])
       => (ok :operation "add"))

     (fact "User NOT using AD login can be added"
       (command pori :remove-user-organization :organizationId pori-R-org-id :email terttu-email) => (ok)
       (command pori :upsert-organization-user :organizationId pori-R-org-id
                :email terttu-email :firstName "Terttu" :lastName "Panaani" :roles ["authority" "approver"])
       => (ok :operation "add"))

     (fact "New user that would use AD login cannot be added"
       (update-pori-ad-login {:forced true})
       (command pori :upsert-organization-user :organizationId pori-R-org-id
                :email "pertti.panaani@pori.fi" :firstName "Pertti" :lastName "Panaani" :roles ["authority" "approver"])
       => (fail :error.user-roles-maintained-in-ad))))
