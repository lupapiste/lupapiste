(ns lupapalvelu.company-itest
  (:require [midje.sweet  :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.domain :as domain]
            [cheshire.core :as json]
            [clojure.string :refer [index-of]]))

(apply-remote-minimal)

(defn http-token-call
  ([token body]
   (let [url (str (server-address) "/api/token/" token)]
     (http-post url {:follow-redirects false
                  :throw-exceptions false
                  :content-type     :json
                  :body (json/encode body)})))
  ([token]
   (fact "Call api/token"
         (http-token-call token {:ok true}) => (contains {:status 200}))))

(defn token-from-email
  ([email]
   (token-from-email email (last-email)))
  ([email email-data]
   (fact {:midje/description (str "Read email for " email)}
     (index-of (:to email-data) email) => pos?)
   (last (re-find #"http.+/app/fi/welcome#!/.+/([A-Za-z0-9-]+)"
                  (:plain (:body email-data))))))

(defn accept-invitation [email]
  (http-token-call (token-from-email email)))

(defn register-user [email password]
  (http-token-call (token-from-email email) {:password password}))

(defn check-invitation-details [email & kv]
  (fact {:midje/description (str "Check invitation for " email)}
        (let [{invitations :invitations} (query kaino :company :company "solita" :users true)
              [details] (filter #(= email (:email %)) invitations)]
          details) => (contains (apply array-map kv))))

(defn check-user-details [email & kv]
  (fact {:midje/description (str "Check user " email)}
        (let [{users :users} (query kaino :company :company "solita" :users true)
              details (-> (filter #(= email (:email %)) users)
                          first
                          (select-keys [:firstName :lastName :company]))
              details (if (-> details :company :id)
                        (update details :company #(dissoc % :id))
                        details)]
          details) => (contains (apply array-map kv))))

(facts* "User is invited to company"
        (let [company (query kaino :company :company "solita" :users true)]
          (count (:invitations company)) => 0
          (count (:users company)) => 1)

        (fact "Can not invite with non-ascii email"
              (command kaino :company-invite-user :email "tepp\u00f6@example.com" :admin false :submit false) => fail?)

        (fact "Invite is sent"
              (command kaino :company-invite-user :email "teppo@example.com" :admin false :submit false) => ok?)

        (fact "Sent invitation is seen in company query"
              (let [company (query kaino :company :company "solita" :users true)]
                (count (:invitations company)) => 1
                (count (:users company)) => 1))

        (fact "Invitation details are correct"
              (check-invitation-details (email-for-key teppo) :role "user" :submit false :firstName "Teppo" :lastName "Nieminen"))

        (fact "Invitation is accepted"
              (accept-invitation (email-for-key teppo)))

        (fact "User is seen in company query"
              (let [company (query kaino :company :company "solita" :users true)]
                (count (:invitations company)) => 0
                (count (:users company)) => 2)))

(fact "User details are correct"
      (check-user-details (email-for-key teppo) :company {:role "user" :submit false} :firstName "Teppo" :lastName "Nieminen"))

(fact "Invitation is sent and cancelled"
      (fact "Invite is sent"
            (command kaino :company-invite-user :email "rakennustarkastaja@jarvenpaa.fi" :admin false :submit true) => ok?
            (let [company (query kaino :company :company "solita" :users true)]
              (count (:invitations company)) => 1
              (count (:users company)) => 2))

      (fact "Invitation is cancelled"
            (let [email (last-email)
                  [uri token] (re-find #"http.+/app/fi/welcome#!/invite-company-user/ok/([A-Za-z0-9-]+)" (:plain (:body email)))]
              (command kaino :company-cancel-invite :tokenId token) => ok?
              (let [company (query kaino :company :company "solita" :users true)]
                (count (:invitations company)) => 0
                (count (:users company)) => 2))))

(facts "Teppo cannot submit even his own applications"
       (let [application-id (create-app-id teppo :propertyId sipoo-property-id :address "Xi Dawang Lu 8")]
         (fact "Query says can't submit"
               (query teppo :application-submittable :id application-id) => (partial expected-failure? :company.user.cannot.submit))
         (fact "Submit application fails"
               (command teppo :submit-application :id application-id) => fail?)))

(facts* "Company is added to application"

        (let [application-id (create-app-id mikko :propertyId sipoo-property-id :address "Kustukatu 13")
        auth (:auth (query-application mikko application-id))]
    (count auth) => 1
    (fact "Can submit"
          (query mikko :application-submittable :id application-id) => ok?)
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
        (some #(= "solita" %) auth-ids) => true))

    (fact "Kaino and Mikko could submit application"
      (query mikko :application-submittable :id application-id) => ok?
      (query kaino :application-submittable :id application-id) => ok?)

    (facts "Teppo cannot submit application"
           (fact "Query says can't submit"
                 (query teppo :application-submittable :id application-id) => (partial expected-failure? :company.user.cannot.submit))
           (fact "Submit application fails"
                 (command teppo :submit-application :id application-id) => fail?))
    (fact "Teppo cannot edit his company user details"
          (command teppo :company-user-update :user-id teppo-id :role "admin" :submit true) => unauthorized?)
    (fact "Kaino cannot give Teppo a bad role"
          (command kaino :company-user-update :user-id teppo-id :role "bad" :submit true) => fail?)
    (fact "Kaino makes Teppo an admin"
          (command kaino :company-user-update :user-id teppo-id :role "admin" :submit false) => ok?)
    (fact "Teppo can now edit his company user details"
          (command teppo :company-user-update :user-id teppo-id :role "user" :submit true) => ok?)
    (facts "Teppo can submit application"
           (fact "Submit application succeeds"
             (query teppo :application-submittable :id application-id) => ok?
             (command teppo :submit-application :id application-id) => ok?))))

(defn result
  ([v]
   {:ok true
    :result (name v)})
  ([v & kv]
   (merge (result v) (apply array-map kv))))

(def foobar "foo@bar.baz")

(facts "Company user management"
       (fact "Teppo is in the company"
             (query kaino :company-search-user :email (email-for-key teppo)) => (result :already-in-company))
       (fact "Teppo cannot search"
             (query teppo :company-search-user :email (email-for-key pena)) => unauthorized?)
       (fact "Pena is not in the company"
             (query kaino :company-search-user :email (email-for-key pena)) => (result :found :firstName "Pena" :lastName "Panaani" :role "applicant"))
       (fact "Foobar is not a known user"
             (query kaino :company-search-user :email foobar) => (result :not-found))
       (fact "Kaino adds user foobar"
             (command kaino :company-add-user :firstName "Foo" :lastName "Bar" :email foobar :admin true :submit true) => ok?)
       (fact "Now foobar is already invited"
             (query kaino :company-search-user :email foobar) => (result :already-invited))
       (fact "Invitation details"
             (check-invitation-details foobar :firstName "Foo" :lastName "Bar" :role "admin" :submit true))
       (facts "Foobar accepts invitation and registers"
              (register-user foobar "password1234"))
       (fact "User details"
             (check-user-details foobar :firstName "Foo" :lastName "Bar" :company {:role "admin" :submit true})))


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
        (command admin :company-update :company company-id :updates {:accountType "custom" :customAccountLimit 3}) => ok?))

    (fact "When company has max count of users, new member can't be invited"
      (command kaino :company-invite-user :email "pena@example.com" :admin false :submit true) => (partial expected-failure? "error.company-user-limit-exceeded"))))

(fact "Kaino deletes Teppo from company"
      (command kaino :company-user-delete :user-id teppo-id) => ok?)
(fact "Teppo is no longer in the company"
      (query kaino :company-search-user :email (email-for-key teppo)) => (result :found :firstName "Teppo" :lastName "Nieminen" :role "applicant"))

(facts "Authed dummy into company"
  (let [application-id (create-app-id mikko :propertyId sipoo-property-id :address "Kustukatu 13")
        foo-email "foo@example.com"
        foo-pw "foofaafoo"]
    (command mikko :invite-with-role
             :id application-id
             :email foo-email
             :text  ""
             :documentName ""
             :documentId ""
             :path ""
             :role "writer") => ok?

    (fact "Dummy 'foo' is found, but not in the company"
      (query kaino :company-search-user :email foo-email) => (result :found :firstName "" :lastName "" :role "dummy"))
    (fact "Dummy gets invite to company"
      (command kaino :company-invite-user :email foo-email :admin false :submit true :firstName "Foo" :lastName "Bar") => ok?
      (accept-invitation foo-email))
    (fact "Dummy is in the company"
      (query kaino :company-search-user :email foo-email) => (result :already-in-company))
    (fact "Foo can set pw from email token"
      (let [email (last-email)
            token (token-from-email foo-email email)]
        (:subject email) => (contains "Salasanan vaihto")
        (http-token-call token {:password foo-pw}) => (contains {:status 200})))

    (let [store (atom {})
          params {:cookie-store (->cookie-store store)}
          login-resp (login foo-email foo-pw params)
          anticsrf (get-anti-csrf-from-store store)
          params (-> params
                     (assoc :headers {"x-anti-forgery-token" anticsrf
                                      "accepts" "application/json;charset=utf-8"})
                     (assoc :follow-redirects false)
                     (assoc :throw-exceptions false))]
      login-resp => ok?
      anticsrf => truthy

      (fact "User query has correct data for ex-dummy foo"
        (let [user-query (decoded-get
                           (str (server-address) "/api/query/user")
                           params)]
          (get-in user-query [:body :user]) => (contains {:company {:id "solita" :role "user" :submit true}
                                 :email foo-email
                                 :firstName "Foo"
                                 :lastName "Bar"})))

      (fact "Original invite is visible for foo"
        (let [invites (get-in (decoded-get
                                (str (server-address) "/api/query/invites")
                                params) [:body :invites])]
          (count invites) => 1
          (get-in (first invites) [:application :id]) => application-id)))))
