(ns lupapalvelu.company-itest
  (:require [midje.sweet  :refer :all]
            [sade.core :refer [now]]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.domain :as domain]
            [cheshire.core :as json]
            [clojure.string :refer [index-of]]
            [sade.util :as util]))

(apply-remote-minimal)

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
            auth-ids (flatten (map (juxt :id) auth))]
        (count auth) => 2
        (some #(= "solita" %) auth-ids) => nil?))

    (fact "Invitation is accepted"
      (let [resp (accept-company-invitation)]
        (:status resp) => 200))

    (fact "Company still cannot be invited twice"
      (command mikko :company-invite :id application-id :company-id "solita") => (partial expected-failure? "company.already-invited"))

    (fact "Company is fully authorized to the application after accept"
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
            (:subject email) => (contains "Uusi salasana")
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

(def locked-err {:ok false :text "error.company-locked"})

(fact "Company back to regular account"
      (command admin :company-update :company "solita" :updates {:accountType "account5"}) => ok?)
(facts "Teppo back into shape"
       (fact "Teppo cannot login"
             (command teppo :login :username "kaino@solita.fi" :password "kaino123") => fail?)
       (fact "Teppo resets password"
             (http-post (str (server-address) "/api/reset-password")
                        {:form-params      {:email "teppo@example.com"}
                         :content-type     :json
                         :follow-redirects false
                         :throw-exceptions false})=> http200?)
       (let [email (last-email)
             token (token-from-email "teppo@example.com" email)]
         (:subject email) => (contains "Uusi salasana")
         (http-token-call token {:password "Teppo rules!"}) => (contains {:status 200})))

(facts "Company locking"
       (fact "Admin locks Solita"
             (command admin :company-lock :company "solita" :timestamp (- (now) 10000)) => ok?)
       (fact "Locked pseudo-query succeeds"
             (query kaino :user-company-locked) => ok?)
       (fact "Companies listing no longer includes Solita"
             (:companies (query pena :companies)) => (just [(contains {:id "esimerkki"})]))
       (fact "Company is not authed to new applications"
             (let [{auth :auth} (create-application kaino
                                         :propertyId sipoo-property-id
                                         :address "Sanyuanqiao") => ok?]
               (count auth) => 1
               auth => (just [(contains {:type "owner"})])))
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
             {foo-id :id} (util/find-by-key :email "foo@example.com" solitans)]
         (fact "User details cannot be changed"
               (command kaino :company-user-update
                        :user-id foo-id
                        :role "admin"
                        :submit false) => locked-err)
         (fact "User can be deleted"
               (command kaino :company-user-delete :user-id foo-id) => ok?))
       (fact "Unlock company"
             (command admin :company-lock :company "solita" :timestamp "unlock") => ok?)
       (fact "Locked pseudo-query fails"
             (query kaino :user-company-locked) => fail?)
       (fact "Company is authed to new applications"
             (let [{auth :auth} (create-application kaino
                                         :propertyId sipoo-property-id
                                         :address "Dongzhimen") => ok?]
               (count auth) => 2
               (map :type auth) => (just ["owner" "company"] :in-any-order)))
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
                (let [_ (command kaino :company-invite-user :firstName "Pena" :lastName "Panaani"
                                 :email "pena@example.com" :admin false :submit true) => ok?
                      pena-token (token-from-email "pena@example.com" (last-email))
                      _ (command kaino :company-add-user :firstName "Bu" :lastName "Zhi Dao"
                                 :email "unknown@example.com" :admin false :submit true) => ok?
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
       (fact "Kaino resets password"
             (http-post (str (server-address) "/api/reset-password")
                        {:form-params      {:email "kaino@solita.fi"}
                         :content-type     :json
                         :follow-redirects false
                         :throw-exceptions false})=> http200?)
       (let [email (last-email)
             token (token-from-email "kaino@solita.fi" email)]
         (:subject email) => (contains "Uusi salasana")
         (http-token-call token {:password "kaino456"}) => (contains {:status 200}))
       (fact "Kaino can now login"
             (command kaino :login :username "kaino@solita.fi" :password "kaino456") => ok?)
       (fact "Kaino is no longer Solitan"
             (query kaino :company :company "solita" :users true) => unauthorized?))

(apply-remote-minimal)

(facts "query company tags"
  (fact "tags are returned for company user"
    (let [resp (query kaino "company-tags" :company "solita")]
      resp => ok?
      (-> resp :tags count) => 1
      (get-in resp [:tags 0 :id]) => "7a67a67a67a67a67a67a67a6"
      (get-in resp [:tags 0 :label]) => "Projekti1"))

  (fact "fail for non company user"
    (query pena "company-tags" :company "solita") => (partial expected-failure? :error.unauthorized)))

(facts "edit company tags"

  (fact "add new tag"
    (command kaino "save-company-tags" :tags [{:id "7a67a67a67a67a67a67a67a6" :label "Projekti1"} {:label "Uusi tagi"}]) => ok?)

  (fact "tag is added"
    (let [resp (query kaino "company-tags" :company "solita")]
      resp => ok?
      (-> resp :tags count) => 2
      (get-in resp [:tags 0 :id]) => "7a67a67a67a67a67a67a67a6"
      (get-in resp [:tags 0 :label]) => "Projekti1"

      (get-in resp [:tags 1 :id]) => truthy
      (get-in resp [:tags 1 :label]) => "Uusi tagi"))

  (fact "fail for non company user"
    (command pena "save-company-tags" :tags [{:id "7a67a67a67a67a67a67a67a6" :label "Projekti1"} {:label "Uusi tagi"}]) => (partial expected-failure? :error.unauthorized))


  (fact "remove tag"
    (command kaino "save-company-tags" :tags [{:id "7a67a67a67a67a67a67a67a6" :label "Projekti1"}]) => ok?)

  (fact "tag is removed"
    (let [resp (query kaino "company-tags" :company "solita")]
      resp => ok?
      (-> resp :tags count) => 1
      (get-in resp [:tags 0 :id]) => "7a67a67a67a67a67a67a67a6"
      (get-in resp [:tags 0 :label]) => "Projekti1"))

  (fact "edit existing tag"
    (command kaino "save-company-tags" :tags [{:id "7a67a67a67a67a67a67a67a6" :label "Projekti666"}]) => ok?)

  (fact "tag is removed"
    (let [resp (query kaino "company-tags" :company "solita")]
      resp => ok?
      (-> resp :tags count) => 1
      (get-in resp [:tags 0 :id]) => "7a67a67a67a67a67a67a67a6"
      (get-in resp [:tags 0 :label]) => "Projekti666")))

(facts "adding company tags to application"

  (fact "set company tags for solita"
    (command kaino "save-company-tags" :tags [{:label "Projekti1"} {:label "Projekti2"} {:label "Projekti3"} {:label "Projekti4"}]) => ok?)

  (fact "set company tags for esimerkki"
    (command erkki "save-company-tags" :tags [{:label "Raksa"} {:label "Paksa"}]) => ok?)

  (let [app  (create-and-open-application kaino)
        solita-tags (:tags (query kaino "company-tags" :company "solita"))
        tag-ids (map :id solita-tags)
        esimerkki-tags (:tags (query erkki "company-tags" :company "esimerkki"))]
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
      (let [resp (accept-company-invitation)]
        (:status resp) => 200))

    (fact "solita company notes are not visible for another company user"
      (let [app (query-application erkki (:id app))]
        (:company-notes app) => []))

    (fact "add tags to application for esimerkki"
      (->> [(:id (last esimerkki-tags))]
           (command erkki :update-application-company-notes :id (:id app) :tags)) => ok?)

    (fact "erkki sees tag he just added"
      (let [app (query-application erkki (:id app))]
        (:company-notes app) => [{:companyId "esimerkki" :tags [(:id (last esimerkki-tags))]}]))))
