(ns lupapalvelu.company-itest
  (:require [midje.sweet  :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.domain :as domain]))

(apply-remote-minimal)

(facts* "User is invited to company"
  (let [company (query kaino :company :company "solita" :users true)]
    (count (:invitations company)) => 0
    (count (:users company)) => 1

    (fact "Can not invite with non-ascii email"
      (command kaino :company-invite-user :email "tepp\u00f6@example.com") => fail?)

    (fact "Invite is sent"
      (command kaino :company-invite-user :email "teppo@example.com") => ok?)

    (fact "Sent invitation is seen in company query"
      (let [company (query kaino :company :company "solita" :users true)]
        (count (:invitations company)) => 1
        (count (:users company)) => 1))

    (fact "Invitation is accepted"
      (let [email (last-email)
            [uri token] (re-find #"http.+/app/fi/welcome#!/invite-company-user/ok/([A-Za-z0-9-]+)" (:plain (:body email)))
            params {:follow-redirects false
                    :throw-exceptions false
                    :content-type     :json
                    :body "{\"ok\": true}"}
            resp (http-post (str (server-address) "/api/token/" token) params)
            ]
        (:status resp) => 200
        (:to email) => "Teppo Nieminen <teppo@example.com>")))

  (fact "User is seen in company query"
    (let [company (query kaino :company :company "solita" :users true)]
      (count (:invitations company)) => 0
      (count (:users company)) => 2))

  (fact "Invitation is sent and cancelled"
    (fact "Invite is sent"
      (command kaino :company-invite-user :email "rakennustarkastaja@jarvenpaa.fi") => ok?
      (let [company (query kaino :company :company "solita" :users true)]
        (count (:invitations company)) => 1
        (count (:users company)) => 2))

    (fact "Invitation is cancelled"
      (let [email (last-email)
            [uri token] (re-find #"http.+/app/fi/welcome#!/invite-company-user/ok/([A-Za-z0-9-]+)" (:plain (:body email)))]
        (command kaino :company-cancel-invite :tokenId token) => ok?
        (let [company (query kaino :company :company "solita" :users true)]
          (count (:invitations company)) => 0
          (count (:users company)) => 2)))))


(facts* "Company is added to application"

  (let [application-id (create-app-id mikko :propertyId sipoo-property-id :address "Kustukatu 13")
        auth (:auth (query-application mikko application-id))]
    (count auth) => 1

    (fact "Applicant invites company"
          (command mikko :company-invite :id application-id :company-id "solita") => ok?)

    (fact "Company cannot be invited twice"
          (command mikko :company-invite :id application-id :company-id "solita") => (partial expected-failure? "company.already-invited"))

    (fact "Company is only invited to the application"
      (let [auth (:auth (query-application mikko application-id))
            auth-ids (flatten (map (juxt :id) auth))]
        (count auth) => 2
        (some #(= "solita" %) auth-ids) => nil?))

    (fact "Invitation is accepted"
      (let [resp (accept-company-invitation)]
        (:status resp) => 200))

    (fact "Company is fully authored to the application after accept"
      (let [auth (:auth (query-application mikko application-id))
            auth-ids (flatten (map (juxt :id) auth))]
        (some #(= "solita" %) auth-ids) => true))))

(facts* "Company details"
  (let [company-id "solita"]
    (fact "Query is ok iff user is member of company"
      (query pena :company :company company-id :users true) => unauthorized?
      (query sonja :company :company company-id :users true) => unauthorized?
      (query teppo :company :company company-id :users true) = ok?
      (query kaino :company :company company-id :users true) => ok?)

    (facts "Member can't update details but company admin and solita admin can"
      (fact "Teppo is member, denied"
        (command teppo :company-update :company company-id :updates {:po "Pori"}) => unauthorized?)
      (fact "Kaino is admin, can upgrade account type, but can't use illegal values or downgrade"
        (command kaino :company-update :company company-id :updates {:accountType "account30"}) => ok?
        (command kaino :company-update :company company-id :updates {:accountType "account5"}) => (partial expected-failure? "company.account-type-not-downgradable")
        (command kaino :company-update :company company-id :updates {:accountType "fail"}) => (partial expected-failure? "error.illegal-company-account")
        (command kaino :company-update :company company-id :updates {:accountType "custom" :customAccountLimit 5}) => (partial expected-failure? "error.unauthorized"))
      (fact "Solita admin can set account to be 'custom', customAccountLimit needs to be set"
        (command admin :company-update :company company-id :updates {:accountType "custom"}) => (partial expected-failure? "company.missing.custom-limit")
        (command admin :company-update :company company-id :updates {:accountType "custom" :customAccountLimit 2}) => ok?))

    (fact "When company has max count of users, new member can't be invited"
      (command kaino :company-invite-user :email "pena@example.com") => (partial expected-failure? "error.company-user-limit-exceeded"))))

