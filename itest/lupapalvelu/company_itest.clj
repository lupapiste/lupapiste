(ns lupapalvelu.company-itest
  (:require [lupapalvelu.authorization :as auth]
            [lupapalvelu.dummy-ident-itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.ident.dummy :as dummy]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.json :as json]
            [lupapalvelu.test-util :as test-util]
            [lupapalvelu.vetuma-itest-util :as vetuma]
            [midje.sweet :refer :all]
            [ring.util.codec :as codec]
            [sade.core :refer [now]]
            [sade.strings :as ss]
            [sade.util :as util]))

(apply-remote-minimal)

(defn accept-invitation [email]
  (http-token-call (token-from-email email)))

(defn register-user [email password]
  (http-token-call (token-from-email email) {:password password}))

(defn check-invitation-details [apikey company email & kv]
  (fact {:midje/description (str "Check invitation for " email)}
        (let [{invitations :invitations :as res} (query apikey :company :company company :users true)
              [details] (filter #(= email (:email %)) invitations)]
          res => ok?
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

(defn user-query [username password]
  (let [store        (atom {})
        params     {:cookie-store (->cookie-store store)}
        login-resp (login username password params)
        anticsrf   (get-anti-csrf store)
        params     (-> params
                       (assoc :headers {"x-anti-forgery-token" anticsrf
                                        "accepts"              "application/json;charset=utf-8"})
                       (assoc :follow-redirects false)
                       (assoc :throw-exceptions false))]
    (fact "Login OK"
      login-resp => ok?
      anticsrf => truthy)

    (get-in (decoded-get
             (str (server-address) "/api/query/user")
             params)
            [:body :user])))

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
              (check-invitation-details kaino "solita" (email-for-key teppo) :role "user" :submit false :firstName "Teppo" :lastName "Nieminen"))

        (fact "Invitation is accepted"
              (let [email-address (email-for-key teppo)
                    invitation (last-email)]
                (http-token-call (token-from-email email-address invitation))

                (fact "Invitation can only be accepted once"
                      (let [response (http-token-call (token-from-email email-address invitation) {:ok true})]
                        (:status response) => 404
                        (-> response :body json/decode) => {"ok" false, "text" "error.token-used"}))))

        (fact "User is seen in company query"
              (let [company (query kaino :company :company "solita" :users true)]
                (count (:invitations company)) => 0
                (count (:users company)) => 2))

        ;; LPK-3699
        (fact "User can change email after being invited to company"
              (let [new-email "teppo2@example.com"]
                (command teppo :change-email-init :email new-email) => ok?
                (let [token (token-from-email new-email)]
                  (command teppo :change-email-simple :tokenId token) => ok?)

                ;; Revert back to original email
                (command teppo :change-email-init :email (email-for-key teppo)) => ok?
                (let [token (token-from-email (email-for-key teppo))]
                  (command teppo :change-email-simple :tokenId token) => ok?))))

(fact "User details are correct"
      (check-user-details (email-for-key teppo) :company {:role "user" :submit false} :firstName "Teppo" :lastName "Nieminen"))

(fact "User details are correct"
  (check-user-details (email-for-key teppo) :company {:role "user" :submit false} :firstName "Teppo" :lastName "Nieminen")
  (-> (query teppo :user)
      (get :user)
      (select-keys [:companyId :companyName]))
  => {:companyId "1060155-5" :companyName "Solita Oy"})

(fact "Invitation is sent and cancelled"
      (fact "Invite is sent"
            (command kaino :company-invite-user :email "pena@example.com" :admin false :submit true) => ok?
            (let [company (query kaino :company :company "solita" :users true)]
              (count (:invitations company)) => 1
              (count (:users company)) => 2))

      (fact "Invitation is cancelled"
            (let [email (last-email)
                  [_ token] (re-find #"http.+/app/fi/welcome#!/invite-company-user/ok/([A-Za-z0-9-]+)" (:plain (:body email)))]
              (command kaino :company-cancel-invite :tokenId token) => ok?
              (let [company (query kaino :company :company "solita" :users true)]
                (count (:invitations company)) => 0
                (count (:users company)) => 2))))

(facts "Not-supported user roles for company"
  (doseq [email (map email-for-key [sipoo sonja admin])]
    (fact {:midje/description (str "Invite " email)}
      (command kaino :company-invite-user :email email :admin false :submit true)
      => (partial expected-failure? "not-applicant"))))

(fact "Non-existing user cannot be invited"
  (command kaino :company-invite-user :email "no@such.usr" :admin false :submit true)
  => (partial expected-failure? "not-found"))

(fact "Existing user cannot be added"
  (command erkki :company-add-user :email (email-for-key pena)
           :firstName "Pena" :lastName "Panaani"
           :admin false :submit true)
  => (partial expected-failure? "error.user-cannot-be-added-to-company"))


(facts "Teppo cannot submit even his own applications"
       (let [application-id (create-app-id teppo :propertyId sipoo-property-id :address "Xi Dawang Lu 8")]
         (fact "Query says can't submit"
           (query teppo :application-submittable :id application-id) => {:ok false
                                                                         :text "error.cannot-submit-application"
                                                                         :errors [{:ok false :text "company.user.cannot.submit"}]})
         (fact "Submit application fails"
               (command teppo :submit-application :id application-id) => fail?)))

(facts* "Company is added to application"

  (let [application-id (create-app-id mikko :propertyId sipoo-property-id :address "Kustukatu 13")
        auth (:auth (query-application mikko application-id))]
    (count auth) => 1
    (fact "Can submit"
          (query mikko :application-submittable :id application-id) => ok?)
    (fact "Applicant invites company"
      (command mikko :company-invite :id application-id :company-id "solita") => ok?
      (fact "Email is sent"                                 ; testing new email templates - accept-company-invitation
        (let [email (last-email false)
              plain-body (get-in email [:body :plain])]
          plain-body => (contains "Hei Kaino")
          plain-body => (contains "Mikko Intonen haluaa valtuuttaa yrityksenne Solita Oy")
          plain-body => (contains "osoitteessa Kustukatu 13"))))

    (fact "Company cannot be invited twice"
      (command mikko :company-invite :id application-id :company-id "solita") => (partial expected-failure? "company.already-invited"))

    (fact "Company is only invited to the application"
      (let [auth (:auth (query-application mikko application-id))
            company-auth (util/find-by-id "solita" auth)]
        (count auth) => 2
        (:type company-auth) => "company"
        (:role company-auth) => "reader"
        (:company-role company-auth) => "admin"
        (:invite company-auth) => map?))

    ; LPK-5357
    (fact "Kaino has invitation bcs admin"
      (:invites (query kaino :invites))
      => (just [(contains {:application (contains {:address "Kustukatu 13"})
                           :inviter (contains {:firstName "Mikko"})})])

      (fact "can also query"
        (query kaino :application :id application-id) => ok?)
      (fact "does see it in applications-search"
        (-> (datatables kaino :applications-search :searchText "")
            (get-in [:data :applications]))
        => (contains [(contains {:address "Kustukatu 13"})])))

    (fact "No invites for regular company guy Teppo" ; Teppo is invited to company earlier in this test file
      (:invites (query teppo :invites))
      => empty?

      (fact "can't query application"
        (query teppo :application :id application-id) => unauthorized?)
      (fact "doesn't see it in applications-search"
        (-> (datatables teppo :applications-search :searchText "")
            (get-in [:data :applications]))
        =not=> (contains [(contains {:address "Kustukatu 13"})])))

    (fact "Invitation is accepted"
      (command kaino :approve-invite :id application-id :invite-type :company) => ok?)

    (fact "Company still cannot be invited twice"
      (command mikko :company-invite :id application-id :company-id "solita") => (partial expected-failure? "company.already-invited"))

    (fact "Company is fully authorized to the application after accept"
      (let [auth (:auth (query-application mikko application-id))
            company-auth (util/find-by-id "solita" auth)]
        (count auth) => 2
        (:type company-auth) => "company"
        (:role company-auth) => "writer"
        (:company-role company-auth) => nil
        (:invite company-auth) => nil))

    (fact "Company users are available for person selectors in party documents"
      (map :id (:users (query mikko :company-users-for-person-selector :id application-id)))
        => (just [teppo-id kaino-id]))

    (fact "Kaino and Mikko could submit application"
      (query mikko :application-submittable :id application-id) => ok?
      (query kaino :application-submittable :id application-id) => ok?)

    (facts "Teppo cannot submit application"
           (fact "Query says can't submit"
                 (query teppo :application-submittable :id application-id) => {:ok false
                                                                               :text "error.cannot-submit-application"
                                                                               :errors [{:ok false :text "company.user.cannot.submit"}]})
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
       (fact "Sonja is not an applicant"
         (query kaino :company-search-user :email (email-for-key sonja)) => (result :not-applicant))
       (fact "Sipoo admin is not an applicant"
         (query kaino :company-search-user :email (email-for-key sipoo)) => (result :not-applicant))
       (fact "Admin is not an applicant"
         (query kaino :company-search-user :email (email-for-key admin)) => (result :not-applicant))
       (fact "Foobar is not a known user"
             (query kaino :company-search-user :email foobar) => (result :not-found))
       (fact "Kaino adds user foobar"
             (command kaino :company-add-user :firstName "Foo" :lastName "Bar" :email foobar :admin true :submit true) => ok?)
       (fact "Now foobar is already invited"
             (query kaino :company-search-user :email foobar) => (result :already-invited))
       (fact "Invitation details"
             (check-invitation-details kaino "solita" foobar :firstName "Foo" :lastName "Bar" :role "admin" :submit true))
       (facts "Foobar accepts invitation and registers"
              (register-user foobar test-util/strong-password))
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
    (facts "billingType"
      (fact "Solita admin can change"
        (command admin :company-update :company company-id :updates {:billingType "yearly"}) => ok?)
      (fact "Kaino can't change"
        (command kaino :company-update :company company-id :updates {:billingType "monthly"}) => unauthorized?))

    (fact "When company has max count of users, new member can't be invited"
      (command kaino :company-invite-user :email "pena@example.com" :admin false :submit true) => (partial expected-failure? "error.company-user-limit-exceeded"))))
; Reset
(last-email)

(facts "Identified user deletion"
  (fact "Before kickban, Teppo sees company application"
    (let [apps (-> (datatables teppo :applications-search :searchText "")
                   (get-in [:data :applications]))]
      (count apps) => 2))

  (fact "Teppo creates default filter"
    (command teppo :save-application-filter
             :filterType "company"
             :filter {:companyTags [] :operations ["aita"]}
             :sort {:asc false :field "modified"}
             :title "My Default Filter") => ok?
    (user-query "teppo@example.com" "teppo69")
    => (contains {:defaultFilter truthy}))

  (fact "Kaino deletes Teppo from company"
    (command kaino :company-user-delete :user-id teppo-id) => ok?
    (fact "Teppo is NOT notified via email"
      (last-email) => nil))

  (fact "Teppo is no longer in the company and has dummy role"
    (query kaino :company-search-user :email (email-for-key teppo)) => (result :found :firstName "Teppo" :lastName "Nieminen" :role "applicant"))
  (facts "Teppo is authed, he is not set to dummy"
    (fact "Teppo can login"
      (command teppo :login :username "teppo@example.com" :password "teppo69") => ok?)
    teppo => (allowed? :reset-password)
    (fact "Teppo can't no longer see company applications"
      (let [apps (-> (datatables teppo :applications-search :searchText "")
                     (get-in [:data :applications]))]
        (count apps) => 0)))

  (facts "Teppo's data is correct after getting kicked of from company"
    (-> (query teppo :user)
        (get :user)
        (select-keys [:companyId :companyName :company]))
    => {})

  (fact "Teppo no longer has default filter"
    (:defaultFilter (user-query "teppo@example.com" "teppo69"))
    => nil))

(facts "Authed dummy into company"
  (apply-remote-minimal)
  (let [application-id (create-app-id mikko :propertyId sipoo-property-id :address "Kustukatu 13")
        foo-email      "foo@example.com"
        foo-pw         test-util/strong-password]
    (command mikko :invite-with-role
             :id application-id
             :email foo-email
             :text  ""
             :documentName ""
             :documentId ""
             :path ""
             :role "writer") => ok?

    (fact "Dummy 'foo' is found, but not in the company"
      (query kaino :company-search-user :email foo-email) => (result :found :firstName "" :lastName "foo@example.com" :role "dummy"))
    (fact "Dummy gets invite to company"
      (command kaino :company-invite-user :email foo-email :admin false :submit true :firstName "Foo" :lastName "Bar") => ok?
      (accept-invitation foo-email))
    (fact "Dummy is in the company"
      (query kaino :company-search-user :email foo-email) => (result :already-in-company))
    (fact "Foo can set pw from email token"
          (let [email (last-email)
                token (token-from-email foo-email email)]
            (:subject email) => (contains "Uusi salasana")
            (http-token-call token {:password foo-pw}) => (contains {:status 200})))

    (let [store (atom {})
          params {:cookie-store (->cookie-store store)}
          login-resp (login foo-email foo-pw params)
          anticsrf (get-anti-csrf store)
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
                                                          :role "applicant"
                                                          :firstName "Foo"
                                                          :lastName "Bar"
                                                          :companyName "Solita Oy"
                                                          :companyId "1060155-5"})))

      (fact "Original invite is visible for foo"
        (let [invites (get-in (decoded-get
                                (str (server-address) "/api/query/invites")
                                params) [:body :invites])]
          (count invites) => 1
          (get-in (first invites) [:application :id]) => application-id))

      (fact "Foo can create application"
        (:body
          (http-post (str (server-address) "/api/command/create-application")
                     (-> params
                         (update :headers assoc "content-type" "application/json;charset=utf-8")
                         (assoc :body (json/encode (merge create-app-default-args {:municipality "753"}))
                                :as :json)))) => ok?)
      (fact "Foo sees company applications"
        (->> (http-post (str (server-address) "/api/datatables/applications-search")
                        (-> params
                            (update :headers assoc "content-type" "application/json;charset=utf-8")
                            (assoc :body (json/encode {:searchText ""})
                                   :as :json)))
             :body :data :applications
             count) => 2))))

(def locked-err {:ok false :text "error.company-locked"})

(fact "Company back to regular account"
      (command admin :company-update :company "solita" :updates {:accountType "account5"}) => ok?)

(facts "Company locking"
       (fact "Admin locks Solita"
             (command admin :company-lock :company "solita" :timestamp (- (now) 10000)) => ok?)
       (fact "Locked pseudo-query succeeds"
             (query kaino :user-company-locked) => ok?)
       (fact "Companies listing no longer includes Solita"
         (:companies (query pena :companies))
         => (just [{:id       "esimerkki"
                    :name     "Esimerkki Oy"
                    :y        "7208863-8"
                    :address1 "Merkintie 88"
                    :zip      "12345"
                    :po       "Humppila"}]))
       (fact "Erkki updates Esimerkki company contact info"
         (command erkki :company-update
                  :company "esimerkki"
                  :updates {:contactAddress "Street"
                            :contactZip     "88888"
                            :contactPo      "Town"}) => ok?)
       (fact "Companies listing changes accordingly"
         (:companies (query pena :companies))
         => (just [{:id       "esimerkki"
                    :name     "Esimerkki Oy"
                    :y        "7208863-8"
                    :address1 "Street"
                    :zip      "88888"
                    :po       "Town"}]))
       (fact "Company is not authed to new applications"
         (let [{auth :auth}
               (create-application kaino
                                   :propertyId sipoo-property-id
                                   :address "Sanyuanqiao") => ok?]
           (count auth) => 1
           (:type auth) => nil
           (map :role auth) => ["writer"]))
       (fact "Company can be queried"
             (query kaino :company :company "solita" :users true) => ok?)
       (fact "Company cannot be updated"
         (command kaino :company-update :company "solita" :updates {:po "Beijing"})
             => locked-err)
       (fact "Users cannot be added to the company"
             (command kaino :company-add-user :firstName "Hii" :lastName "Hoo"
                      :email "hii.hoo@example.com" :admin true :submit true)
             => locked-err)
       (let [{solitans :users} (query kaino :company :company "solita" :users true)
             {foo-id :id}      (util/find-by-key :email "foo@example.com" solitans)]
         (fact "User details cannot be changed"
               (command kaino :company-user-update
                        :user-id foo-id
                        :role "admin"
                        :submit false) => locked-err)
         (facts "Unverified user deletion"
           (fact "User can be deleted"
             (command kaino :company-user-delete :user-id foo-id) => ok?)
           (fact "Unverified user is set to dummy-user"       ; LPK-3034
             (query kaino :company-search-user :email "foo@example.com") => (result :found :firstName "Foo" :lastName "Bar" :role "dummy"))
           (fact "Login not possible for dummy"
             (let [{body :body :as login} (-> (str (server-address) "/api/login")
                                              (http-post {:form-params {:username "foo@example.com" :password test-util/strong-password}
                                                          :as          :json}))]
               body => fail?
               (:text body) => "error.login"
               login => http200?))
           (fact "User is notified via email"
             (let [email (last-email)
                   body  (get-in email [:body :plain])]
               email => truthy
               (:subject email) => (contains "Yritystilist\u00e4 poisto")
               body => (contains #"Solita Oy Yritystilin.+on poistanut")
               (fact "register link exists"
                 body => (contains #"#!/register"))))))
       (fact "Unlock company"
         (command admin :company-lock :company "solita" :timestamp 0) => ok?)
       (fact "Locked pseudo-query fails"
         (query kaino :user-company-locked) => fail?)
       (fact "Solita is listed in the companies query"
         (:companies (query pena :companies))
         => (just [{:id       "esimerkki"
                    :name     "Esimerkki Oy"
                    :y        "7208863-8"
                    :address1 "Street"
                    :zip      "88888"
                    :po       "Town"}
                   {:id       "solita"
                    :name     "Solita Oy"
                    :y        "1060155-5"
                    :address1 "\u00c5kerlundinkatu 11"
                    :zip      "33100"
                    :po       "Tampere"}] :in-any-order))
       (fact "Company is authed to new applications"
         (let [{auth :auth}
               (create-application kaino
                                   :propertyId sipoo-property-id
                                   :address "Dongzhimen") => ok?]
           (map :type auth) => ["company"]))
       (fact "Company can now be updated"
         (command kaino :company-update :company "solita" :updates {:po "Beijing"}) => ok?)
       (fact "Nuking is not an option for unlocked company"
         (command kaino :company-user-delete-all) => (contains {:text "error.company-not-locked"}))
       (fact "Admin locks Solita in the future"
             (command admin :company-lock :company "solita" :timestamp (+ (now) 10000)) => ok?)
       (fact "Company can now also be updated"
         (command kaino :company-update :company "solita" :updates {:po "Chaoyang"}) => ok?)
       (fact "Erkki invites Teppo to Esimerkki"
             (command erkki :company-invite-user :firstName "Teppo" :lastName "Nieminen"
                      :email "teppo@example.com" :admin false :submit true))
       (let [teppo-token (token-from-email "teppo@example.com" (last-email))]
         (facts "Kaino invites Pena and Unknown to Solita"
           (let [_             (command kaino :company-invite-user
                                        :firstName "Pena" :lastName "Panaani"
                                        :email "pena@example.com" :admin false :submit true)
                 =>            ok?
                 pena-token    (token-from-email "pena@example.com" (last-email))
                 _             (command kaino :company-add-user :firstName "Bu" :lastName "Zhi Dao"
                                        :email "unknown@example.com" :admin false :submit true)
                 =>            ok?
                 unknown-token (token-from-email "unknown@example.com" (last-email))]
                  (fact "Admin locks Solita again"
                        (command admin :company-lock :company "solita" :timestamp (- (now) 10000)) => ok?)
                  (fact "Kaino nukes locked company"
                        (command kaino :company-user-delete-all) => ok?)
                  (fact "Pena's invitation has been rescinded"
                    (http-token-call pena-token {:ok true}) => (contains {:status 404}))
                  (fact "Pena can still login"
                        (command pena :login :username "pena" :password "pena") => ok?)
                  (fact "Unknown's invitation has been rescinded"
                    (http-token-call unknown-token {:ok true}) => (contains {:status 404}))))
         (fact "Teppo's invitation is untouched"
           (http-token-call teppo-token {:ok true}) => (contains {:status 200}))
         (fact "Erkki can still login"
               (command erkki :login :username "erkki@example.com" :password "esimerkki") => ok?))
       (fact "Kaino cannot login"
         (command kaino :login :username "kaino@solita.fi" :password "kaino123") => fail?)
       (fact "Kaino can't reset, he is now dummy"
         (last-email) ; Empty inbox
         (let [reset-result (:body (decoded-simple-post (str (server-address) "/api/reset-password")
                                                        {:form-params      {:email "kaino@solita.fi"}
                                                         :content-type     :json
                                                         :follow-redirects false
                                                         :throw-exceptions false}))]
           ;; Always OK for any (valid) email
           reset-result => ok?
           ;; Reset email not sent
           (last-email) => nil)))

(facts "Return of the Foo"                                  ; Kickbanned from company, Foo can return via identification service. LPK-3034
  (let [store       (atom {})
        params      (vetuma/default-vetuma-params (->cookie-store store))
        trid        (dummy-ident-init params vetuma/default-token-query)
        _           (dummy-ident-finish params {:personId dummy/dummy-person-id} trid)
        vetuma-data (decode-body (http-get (str (server-address) "/api/vetuma/user") params))
        stamp       (:stamp vetuma-data)
        params      (update params :headers assoc "x-anti-forgery-token" (-> (get @store "anti-csrf-token") .getValue codec/url-decode))
        old-details (first (:users (query admin :users :email "foo@example.com")))
        password    test-util/strong-password
        reg-resp    (vetuma/register params {:email                "foo@example.com"
                                             :stamp                stamp
                                             :password             password
                                             :street               "Testikatu 3" :zip "33500"
                                             :city                 "Tre" :phone "123"
                                             :allowDirectMarketing false
                                             :rakentajafi          false})
        email       (last-email)]
    (fact "Before registering was disalbed"
      (:enabled old-details) => false)
    (fact "Vetuma data OK"
      vetuma-data => (contains {:stamp string?
                                :userid dummy/dummy-person-id}))
    (fact "Registering OK"
      reg-resp => ok?)
    (fact "Got email"
      (:subject email) => "Lupapiste: Tervetuloa Lupapisteeseen!")
    (fact "Can NOT log in before activation"
      (login "foo@example.com" password params) => (partial expected-failure? :error.login))
    (fact "Email body exists"
      (get-in email [:body :plain]) => truthy)
    (fact "Activate"
      (when-let [body (get-in email [:body :plain])]
        (-> (re-find  #".+/app/security/activate/([a-zA-Z0-9]+)" body)
            first
            (http-get {:follow-redirects false})) => http302?))
    (fact "Can NOT log in yet"
      (login "foo@example.com" password params) => ok?)
    (fact "Is enabled"
      (-> (query admin :users :email "foo@example.com")
          :users first) => (contains {:enabled true}))
    (fact "Foo still sees own application from company time, but no company applications"
      (->> (http-post (str (server-address) "/api/datatables/applications-search")
                      (-> params
                          (update :headers assoc "content-type" "application/json;charset=utf-8")
                          (assoc :body (json/encode {:searchText ""})
                                 :as :json)))
           :body :data :applications
           count) => 1)))

(apply-remote-minimal)

(facts "query company tags"
  (fact "tags are returned for company user"
    (let [resp (query kaino "company-tags" :company "solita")]
      resp => ok?
      (-> resp :company :id) => "solita"
      (-> resp :company :name) => "Solita Oy"
      (get-in resp [:company :tags 0 :id]) => "7a67a67a67a67a67a67a67a6"
      (get-in resp [:company :tags 0 :label]) => "Projekti1"))

  (fact "fail for non company user"
    (query pena "company-tags" :company "solita") => (partial expected-failure? :error.unauthorized)))

(facts "edit company tags"

  (fact "add new tag"
    (command kaino "save-company-tags" :tags [{:id "7a67a67a67a67a67a67a67a6"
                                               :label "  Projekti1 "}
                                              {:label "  Uusi tagi  "}]) => ok?)

  (fact "tag is added"
    (let [resp (query kaino "company-tags" :company "solita")]
      resp => ok?
      (-> resp :company :tags count) => 2
      (get-in resp [:company :tags 0 :id]) => "7a67a67a67a67a67a67a67a6"
      (get-in resp [:company :tags 0 :label]) => "Projekti1"

      (get-in resp [:company :tags 1 :id]) => truthy
      (get-in resp [:company :tags 1 :label]) => "Uusi tagi"))

  (fact "fail for non company user"
    (command pena "save-company-tags" :tags [{:id "7a67a67a67a67a67a67a67a6" :label "  "}])
    => (partial expected-failure? :error.illegal-value:schema-validation))

  (fact "Blank tags are not allowed"
    (command kaino "save-company-tags" :tags [{:id "7a67a67a67a67a67a67a67a6" :label "Projekti1"} {:label "  Uusi tagi  "}]))


  (fact "remove tag"
    (command kaino "save-company-tags" :tags [{:id "7a67a67a67a67a67a67a67a6" :label "Projekti1"}]) => ok?)

  (fact "tag is removed"
    (let [resp (query kaino "company-tags" :company "solita")]
      resp => ok?
      (-> resp :company :tags count) => 1
      (get-in resp [:company :tags 0 :id]) => "7a67a67a67a67a67a67a67a6"
      (get-in resp [:company :tags 0 :label]) => "Projekti1"))

  (fact "edit existing tag"
    (command kaino "save-company-tags" :tags [{:id "7a67a67a67a67a67a67a67a6" :label "Projekti666"}]) => ok?)

  (fact "tag is removed"
    (let [resp (query kaino "company-tags" :company "solita")]
      resp => ok?
      (-> resp :company :tags count) => 1
      (get-in resp [:company :tags 0 :id]) => "7a67a67a67a67a67a67a67a6"
      (get-in resp [:company :tags 0 :label]) => "Projekti666")))

(facts "company tags and custom account type (LPK-3762)"
  (fact "Esimerkki Oy to custom"
    (let [esimerkki (:company (command admin :company-update
                                       :company "esimerkki"
                                       :updates {:accountType        :custom
                                                 :customAccountLimit 88}))]
      esimerkki => (contains {:accountType "custom"})
      (fact "Set a tag"
        (command erkki :save-company-tags :tags (conj (:tags esimerkki) {:label "bugfix"}))
        => ok?))))

(facts "adding company tags to application"

  (fact "set company tags for solita"
    (command kaino "save-company-tags" :tags [{:label "Projekti1"} {:label "Projekti2"} {:label "Projekti3"} {:label "Projekti4"}]) => ok?)

  (fact "set company tags for esimerkki"
    (command erkki "save-company-tags" :tags [{:label "Raksa"} {:label "Paksa"}]) => ok?)

  (let [app  (create-and-open-application kaino)
        solita-tags (get-in (query kaino "company-tags" :company "solita") [:company :tags])
        tag-ids (map :id solita-tags)
        esimerkki-tags (get-in (query erkki "company-tags" :company "esimerkki") [:company :tags])]
    (count solita-tags) => 4

    (fact "add tags to application"
      (->> (take 2 tag-ids)
           (command kaino :update-application-company-notes :id (:id app) :tags)) => ok?)

    (fact "cannot add another company's tags"
      (->> [(:id (last esimerkki-tags))]
           (command kaino :update-application-company-notes :id (:id app) :tags)) => (partial expected-failure? :error.unknown-tag))

    (fact "tags are added"
      (let [app (query-application kaino (:id app))]
        (:company-notes app) => [{:companyId "solita" :tags (take 2 tag-ids)}]))

    (fact "add note to application"
      (command kaino :update-application-company-notes :id (:id app) :note "foo my app") => ok?)

    (fact "note is added - tags still exist"
      (let [app (query-application kaino (:id app))]
        (:company-notes app) => [{:companyId "solita" :tags (take 2 tag-ids) :note "foo my app"}]))

    (fact "company notes are not visible for non-company user"
      (let [app (query-application sonja (:id app))]
        (:company-notes app) => []))

    (fact "kaino invites another company"
      (command kaino :company-invite :id (:id app) :company-id "esimerkki") => ok?)

    (fact "Invitation is accepted"
      (command erkki :approve-invite :id (:id app) :invite-type :company) => ok?)

    (fact "solita company notes are not visible for another company user"
      (let [app (query-application erkki (:id app))]
        (:company-notes app) => []))

    (fact "add tags to application for esimerkki"
      (->> [(:id (last esimerkki-tags))]
           (command erkki :update-application-company-notes :id (:id app) :tags)) => ok?)

    (fact "erkki sees tag he just added"
      (let [app (query-application erkki (:id app))]
        (:company-notes app) => [{:companyId "esimerkki" :tags [(:id (last esimerkki-tags))]}]))))

(facts "company user change-email"                          ; LPK-2641 & LP-365695
  (apply-remote-fixture "company-application")
  (let [store (atom {})
        cookie (->cookie-store store)
        params {:cookies      {"anti-csrf-token" {:value "my-token"}}
                :headers      {"x-anti-forgery-token" "my-token"}
                :cookie-store cookie
                :as :json}
        new-email "foo2@example.com"
        raw-ok? #(= true (get-in % [:body :ok]))]
    (fact "Erkki adds user foobar"
      (command erkki :company-add-user :firstName "Foo" :lastName "Bar" :email foobar :admin false :submit true) => ok?)
    (fact "Invitation details"
      (check-invitation-details erkki "esimerkki" foobar :firstName "Foo" :lastName "Bar" :role "user" :submit true)
      (register-user foobar "password1234"))
    (fact "foobar has working credentials"
      (http-post (str (server-address) "/api/login")
                 (assoc params
                   :form-params
                   {:username foobar :password "password1234"})) => raw-ok?)
    (fact "foobar can init email-change"
      (http-post (str (server-address) "/api/command/change-email-init")
                 (-> params
                     (assoc-in [:headers "content-type"] "application/json;charset=utf-8")
                     (assoc :body (json/encode {:email new-email})))) => raw-ok?)
    (let [token (token-from-email "foo2@example.com")]
      (fact "foobar can change email with change-email-simple, no need for identification service"
        (http-post (str (server-address) "/api/command/change-email-simple")
                   (-> params
                       (assoc-in [:headers "content-type"] "application/json;charset=utf-8")
                       (assoc :body (json/encode {:tokenId token})))) => raw-ok?)
      (fact "foobar can't login with old email"
        (http-post (str (server-address) "/api/login")
                   (assoc params
                     :form-params
                     {:username foobar :password "password1234"})) =not=> raw-ok?)
      (fact "foobar can login with new email"
        (http-post (str (server-address) "/api/login")
                   (assoc params
                     :form-params
                     {:username "foo2@example.com" :password "password1234"})) => raw-ok?))))

(facts "Removing user from company and document"
  (apply-remote-fixture "company-application")
  (let [app-id (create-app-id teppo)
        app (query-application teppo app-id)
        designer-doc (util/find-first #(= "suunnittelija" (-> % :schema-info :subtype)) (:documents app))]
    (fact "Teppo can open company authed application" app => map?)
    (fact "Teppo does not have auth" (auth/has-auth? app teppo-id) => false)
    (fact "Solita is authed" (count (:auth app)) => 1 (-> app :auth first :id) => "solita")
    (fact "Kaino invites Tebi"
      (command kaino :invite-with-role :id app-id :documentId (:id designer-doc) :email (email-for-key teppo) :documentName "" :role "writer" :text "lol" :path "") => ok?)
    ; Teppo's ID would be retained in document if he would accept invite here (he gets writer auth).
    ; But we will test that userId is cleared from doc when he is removed from company,
    #_(fact "Teppo accepts invite"
      (command teppo :approve-invite :id app-id :email (email-for-key teppo)) => ok?)
    (fact "Kaino sets user to doc"
      (command kaino :set-user-to-document :id app-id :documentId (:id designer-doc) :userId teppo-id :path "") => ok?)
    (let [app (query-application teppo app-id)
          designer-doc (util/find-first #(= "suunnittelija" (-> % :schema-info :subtype)) (:documents app))]
      (fact "Now Teppo has auth"
        (auth/has-auth? app teppo-id) => true)
      (fact "Teppo's id is in document"
        (get-in designer-doc [:data :userId :value]) => teppo-id)
      (fact "Kaino removes Teppo from company"
        (command kaino :company-user-delete :user-id teppo-id) => ok?)
      (let [app (query-application kaino app-id)
            designer-doc (util/find-first #(= "suunnittelija" (-> % :schema-info :subtype)) (:documents app))]
        (fact "Teppo can query app still, because he has auth"
          (query teppo :application :id app-id) => ok?)
        (fact "Kaino sees that Teppo's userId is not in document anymore"
          (get-in designer-doc [:data :userId :value]) => ss/blank?)))
    ))

(defn interim-registration [activate]
  (facts "Non-user is invited to company, registers and THEN uses token (LPK-3759)"
         (apply-remote-minimal)

         (let [new-user-email (:email vetuma/new-user-details)]
           (fact "Invite is sent"
                 (command kaino :company-add-user :firstName "Jukka" :lastName "Palmu" :email new-user-email :admin true :submit true) => ok?)

           (fact "User registers"
                 (let [params (vetuma/default-vetuma-params (->cookie-store (atom {})))
                       trid (vetuma/vetuma-init params vetuma/default-token-query)]
                   (vetuma/vetuma-finish params trid)
                   (let [vetuma-data (decode-body (http-get (str (server-address) "/api/vetuma/user") params))
                         cmd-opts {:cookies {"anti-csrf-token" {:value "123"}}
                                   :headers {"x-anti-forgery-token" "123"}}
                         details (vetuma/stamped-new-user-details (:stamp vetuma-data))]
                     (vetuma/register cmd-opts details)

                     (let [[activation invitation & _] (reverse (sent-emails))]
                       (when activate
                         (let [token (activation-email->token new-user-email activation)]
                           (http-get (str (server-address) "/app/security/activate/" token)
                                     {:follow-redirects false}) => #(redirects-to "/app/fi/applicant" %)))

                       (fact "User accepts invitation, new-company-user token is treated as invite-company-user token"
                             (http-token-call (token-from-email new-user-email invitation)))))))

           (fact "User is seen in company query"
                 (let [company (query kaino :company :company "solita" :users true)]
                   (count (:invitations company)) => 0
                   (count (:users company)) => 2))

           (fact "User details"
                 (check-user-details new-user-email :company {:role "admin" :submit true})))))

(interim-registration true)
(interim-registration false)
