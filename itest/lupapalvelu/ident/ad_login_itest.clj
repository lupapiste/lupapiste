(ns lupapalvelu.ident.ad-login-itest
  "Integration tests for AD-login."
  (:require [clojure.walk :refer [keywordize-keys]]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.ident.ad-login :refer [log-user-in!]]
            [lupapalvelu.ident.ad-login-util :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [mount.core :as mount]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as sxml]
            [saml20-clj.shared :refer [str->base64]]
            [taoensso.nippy :refer [freeze-to-string]]))

(testable-privates lupapalvelu.ident.ad-login
                   resolve-user!)

(defn mock-response [k]
  (->> (name k)
       (format "dev-resources/saml/response-%s.xml")
       slurp
       ss/trim
       str->base64))

(def response (mock-response :with-groups))
(def response-wo-groups (mock-response :no-groups))
(def response-unsigned (mock-response :unsigned))
(def response-only-assertions-signed (mock-response :only-assertions-signed))
(def response-encrypted (mock-response :encrypted))
(def response-cannot-decrypt (mock-response :unknown-encrypted))

(defn- parse-route [domain & [metadata?]]
  (format "%s/api/saml/%s/%s"
          (server-address)
          (if metadata? "metadata" "ad-login")
          domain))

(def terttu-email    "terttu.panaani@pori.fi")
(def priscilla-email "priscilla.panaani@pori.fi")
(def priscilla       (apikey-for "priscilla"))

(defn check-location [response path]
  (fact {:midje/description (str "-> " path)}
    (:status response) => 302
    (get-in response [:headers "Location"])
    => (str (server-address) path)))

(defn authority-page? [response]
  (check-location response "/app/fi/authority")
  true)

(defn applicant-page? [response]
  (check-location response "/app/fi/applicant")
  true)

(defn login-page? [response]
  (check-location response "/login/fi")
  true)

(def bad-certificate ;; Does not match mock responses
  "-----BEGIN CERTIFICATE-----
MIIDCTCCAnKgAwIBAgIBATANBgkqhkiG9w0BAQUFADBvMRQwEgYDVQQDEwtjYXBy
aXphLmNvbTELMAkGA1UEBhMCVVMxETAPBgNVBAgTCFZpcmdpbmlhMRMwEQYDVQQH
EwpCbGFja3NidXJnMRAwDgYDVQQKEwdTYW1saW5nMRAwDgYDVQQLEwdTYW1saW5n
MB4XDTIxMDExNTE4MzQ1NFoXDTIyMDExNTE4MzQ1NFowbzEUMBIGA1UEAxMLY2Fw
cml6YS5jb20xCzAJBgNVBAYTAlVTMREwDwYDVQQIEwhWaXJnaW5pYTETMBEGA1UE
BxMKQmxhY2tzYnVyZzEQMA4GA1UEChMHU2FtbGluZzEQMA4GA1UECxMHU2FtbGlu
ZzCBnzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEAloCRD60NOfu1PsUYQDqzLD84
rdhTXbJrqkkKa6psRdtA6gD8t7mMh3C9K73w9BMzcWJfFCYMIW6RckkRQ3cGL2et
8uFXxfu6TEVZ0B0Lys9IT8kDzTrOLvSVdFSAiRjf6QnAjbKcTYjTWrNMgdDAZVko
ZJmduDWOg8fXcUahkJUCAwEAAaOBtDCBsTAMBgNVHRMEBTADAQH/MAsGA1UdDwQE
AwIC9DA7BgNVHSUENDAyBggrBgEFBQcDAQYIKwYBBQUHAwIGCCsGAQUFBwMDBggr
BgEFBQcDBAYIKwYBBQUHAwgwEQYJYIZIAYb4QgEBBAQDAgD3MCUGA1UdEQQeMByG
Gmh0dHA6Ly9jYXByaXphLmNvbS9zYW1saW5nMB0GA1UdDgQWBBQtL9imeTHvGCD/
o1DuhOhOusbpRzANBgkqhkiG9w0BAQUFAAOBgQBAbbrWk0FhARjs2BlIA4Do/b/y
CBsxeUH7CRTXs/YBRyDcQczaTCdYIY3IxkbMW+4BMGskMnnGyQD1PISy2GejSM14
MHCUdumVId+36OOjfBA7wQFJOVhU6LDnyLuGw7g7CwhO5FCbF0al0Gkobba89H0Z
8QiK82UiP5Nzkp2TGg==
-----END CERTIFICATE-----")

(def good-certificate
  "-----BEGIN CERTIFICATE-----
MIICpzCCAhACCQDuFX0Db5iljDANBgkqhkiG9w0BAQsFADCBlzELMAkGA1UEBhMC
VVMxEzARBgNVBAgMCkNhbGlmb3JuaWExEjAQBgNVBAcMCVBhbG8gQWx0bzEQMA4G
A1UECgwHU2FtbGluZzEPMA0GA1UECwwGU2FsaW5nMRQwEgYDVQQDDAtjYXByaXph
LmNvbTEmMCQGCSqGSIb3DQEJARYXZW5naW5lZXJpbmdAY2Fwcml6YS5jb20wHhcN
MTgwNTE1MTgxMTEwWhcNMjgwNTEyMTgxMTEwWjCBlzELMAkGA1UEBhMCVVMxEzAR
BgNVBAgMCkNhbGlmb3JuaWExEjAQBgNVBAcMCVBhbG8gQWx0bzEQMA4GA1UECgwH
U2FtbGluZzEPMA0GA1UECwwGU2FsaW5nMRQwEgYDVQQDDAtjYXByaXphLmNvbTEm
MCQGCSqGSIb3DQEJARYXZW5naW5lZXJpbmdAY2Fwcml6YS5jb20wgZ8wDQYJKoZI
hvcNAQEBBQADgY0AMIGJAoGBAJEBNDJKH5nXr0hZKcSNIY1l4HeYLPBEKJLXyAno
FTdgGrvi40YyIx9lHh0LbDVWCgxJp21BmKll0CkgmeKidvGlr3FUwtETro44L+Sg
mjiJNbftvFxhNkgA26O2GDQuBoQwgSiagVadWXwJKkodH8tx4ojBPYK1pBO8fHf3
wOnxAgMBAAEwDQYJKoZIhvcNAQELBQADgYEACIylhvh6T758hcZjAQJiV7rMRg+O
mb68iJI4L9f0cyBcJENR+1LQNgUGyFDMm9Wm9o81CuIKBnfpEE2Jfcs76YVWRJy5
xJ11GFKJJ5T0NEB7txbUQPoJOeNoE736lF5vYw6YKp8fJqPW0L2PLWe9qTn8hxpd
njo3k6r5gXyl8tk=
-----END CERTIFICATE-----")

(defn post-route [domain response & [relay-state]]
  (http-post (parse-route domain)
             {:form-params      {:SAMLResponse response
                                 :RelayState   relay-state}
              :content-type     :json
              :throw-exceptions false}))

(def post-pori (partial post-route "pori.fi"))
(def post-sipoo (partial post-route "sipoo.fi"))

;;;; Local tests

(mount/start #'mongo/connection)
(mongo/with-db test-db-name
  (fixture/apply-fixture "minimal")

  (fact "resolve-user! can create or update users"
    (let [user (resolve-user! "  Pedro " "  Banana  " " PedrO@bAnana.FI "
                              {:609-R ["reader"]})]
      (fact "User creation works as expected"
        (= user (usr/get-user-by-email "pedro@banana.fi")) => true
        user => (just {:id        truthy
                       :firstName "Pedro"           :lastName "Banana"
                       :email     "pedro@banana.fi" :username "pedro@banana.fi"
                       :enabled   true
                       :role      "authority"
                       :orgAuthz  {:609-R ["reader"]}}))
      (facts "New user is created via SAM"
        (:role user) => "authority"
        (:orgAuthz user) => {:609-R ["reader"]})
      (facts "Updating users should work as well"
        (resolve-user! "Pablo" "Banana" " PedRo@bANANa.fi " {:753-YA ["authority"]})
        => {:id        (:id user)
            :firstName "Pablo"           :lastName "Banana"
            :email     "pedro@banana.fi" :username "pedro@banana.fi"
            :enabled   true              :role     "authority"
            :orgAuthz  {:609-R ["reader"] :753-YA ["authority"]}}
        (resolve-user! "Pablo" "Banana" " PedRo@bANANa.fi "
                       {:609-R [] :753-YA ["authority"]})
        => {:id        (:id user)
            :firstName "Pablo"           :lastName "Banana"
            :email     "pedro@banana.fi" :username "pedro@banana.fi"
            :enabled   true              :role     "authority"
            :orgAuthz  {:753-YA ["authority"]}})))

  (fact "log-user-in! should... Log user in"
    (let [user                             (usr/get-user-by-email "pedro@banana.fi")
          {:keys [headers session status]} (log-user-in! {} user)]
      (facts "User is redirected to authority landing page"
        (= 302 status) => true
        (= (str (env/value :host) "/app/fi/authority") (get headers "Location")) => true)
      (fact "User is inserted into the session"
        (= "pedro@banana.fi" (get-in session [:user :email])) => true)))

  (fact "The metadata route works"
    (let [res  (http-get (parse-route "pori.fi" true) {:as :stream})
          body (-> res :body sxml/parse sxml/xml->edn)
          cert (-> body
                   (get-in [:md:EntityDescriptor :md:SPSSODescriptor :md:KeyDescriptor])
                   first
                   (get-in [:ds:KeyInfo :ds:X509Data :ds:X509Certificate]))]
      (fact "Status code is 200"
        (:status res) => 200
        (fact "Content-Type is XML"
          (-> res :headers keywordize-keys :Content-Type) => "text/xml; charset=UTF-8")
        (facts "The response contains the Service Provider certificate"
          (string? cert) => true
          (count cert) => 1224
          (= cert (env/value :sso :cert)) => false
          (= cert (parse-certificate (env/value :sso :cert))) => true))))

  (fact "update-ad-login-role-mapping"
    (with-local-actions
      (fact "AD login not enabled for Sipoo-R"
        (command sipoo :update-ad-login-role-mapping :organizationId "753-R"
                 :role-map {:approver "Foo"})
        => fail?)
      (fact "Enable AD for Sipoo-R"
        (command admin :update-ad-login-settings :org-id "753-R"
                 :enabled true
                 :idp-cert "Sertti"
                 :idp-uri "Urtti"
                 :trusted-domains ["example.net"]) => ok?)
      (fact "Bad roles for Sipoo-R"
        (command sipoo :update-ad-login-role-mapping :organizationId "753-R"
                 :role-map {:authority "Good"
                            :badrole   "Bad"})
        => (partial expected-failure? :error.illegal-role))
      (fact "Role map cannot be empty"
        (command sipoo :update-ad-login-role-mapping :organizationId "753-R"
                 :role-map {})
        => (partial expected-failure? :error.empty-role-map))
      (fact "Success!"
        (command sipoo :update-ad-login-role-mapping :organizationId "753-R"
                 :role-map {:reader    "  Lukija  "
                            :commenter "Trolli"
                            :authority ""
                            :approver  "   "})
        => ok?)
      (fact "Roles are trimmed"
        (:ad-login (mongo/by-id :organizations "753-R" [:ad-login]))
        => {:enabled         true
            :idp-cert        "Sertti"
            :idp-uri         "Urtti"
            :trusted-domains ["example.net"]
            :role-mapping    {:reader    "Lukija" :commenter "Trolli"
                              :authority ""       :approver  ""}})
      (fact "Roles can be updated in parts"
        (command sipoo :update-ad-login-role-mapping :organizationId "753-R"
                 :role-map {:authority      "  Boss "
                            :authorityAdmin " Head honcho "})
        => ok?
        (-> (mongo/by-id :organizations "753-R" [:ad-login])
            :ad-login :role-mapping)
        => {:reader         "Lukija" :commenter "Trolli"
            :authority      "Boss"   :approver  ""
            :authorityAdmin "Head honcho"})

      (fact "Enable archive in Sipoo"
        (command admin :set-organization-boolean-attribute
                 :organizationId "753-R"
                 :attribute "permanent-archive-enabled"
                 :enabled true)
        => ok?)
      (fact "Digitization project user is now listed"
        (-> (query sipoo :organization-by-user
                   :organizationId "753-R")
            :organization
            :allowedRoles)
        => (contains :digitization-project-user))
      (fact "... but not allowed for role-mapping"
        (command sipoo :update-ad-login-role-mapping :organizationId "753-R"
                 :role-map {:authority                 "Good"
                            :digitization-project-user "Bad"})
        => (partial expected-failure? :error.illegal-role)))))

;;;; Remote tests

(apply-remote-minimal)

(facts "Testing the main login route"
  (fact "If the endpoint receives POST request without :SAMLResponse map, it fails"
    (http-post (parse-route "pori.fi") {:throw-exceptions false})
    => login-page?)

  (fact "Login attempt with a valid response works."
    (command admin :create-user
             :email "terttu@panaani.fi"
             :role "authority"
             :orgAuthz {:609-R ["reader"]}) => ok?
    (post-pori response) => authority-page?)

  (fact "The user is created from the SAML data"
    (:users (query admin :users :email terttu-email))
    => (just [(just {:email     terttu-email
                     :enabled   true
                     :firstName "Terttu"
                     :id        truthy
                     :lastName  "Panaani"
                     :orgAuthz  {:609-R ["reader"]}
                     :role      "authority"
                     :username  "terttu.panaani@pori.fi"})]))

  (fact "Disable Terttu's account"
    (command admin :set-user-enabled :email terttu-email :enabled false) => ok?)

  (fact "Login fails for disabled account"
    (post-pori response) => login-page?)

  (fact "Erase Terttu's account"
    (command admin :erase-user :email terttu-email) => ok?)

  (fact "Create Terttu as applicant by adding her to company"
    (command erkki :company-add-user
             :admin false
             :submit true
             :email terttu-email
             :firstName "Bunch"
             :lastName "Banana") => ok?
    (http-token-call (token-from-email terttu-email (last-email))))

  (fact "Terttu is logged on as applicant"
    (post-pori response) => applicant-page?)

  (fact "Terttu has no authz, but her details have been udpated"
    (:users (query admin :users :email terttu-email))
    => (just [(contains {:email     terttu-email
                         :enabled   true
                         :firstName "Terttu"
                         :id        truthy
                         :lastName  "Panaani"
                         :orgAuthz  empty?
                         :role      "applicant"
                         :username  "terttu.panaani@pori.fi"})]))

  (fact "Erase Terttu again"
    (command admin :erase-user :email terttu-email))

  (fact "Login attempt with an invalid response fails"
    (post-pori (apply str (drop-last response))) => login-page?)

  (fact "Priscilla is an authority in minimal"
    (:users (query admin :users :email priscilla-email))
    => (just [(contains {:email     priscilla-email
                         :enabled   true
                         :firstName "Priscilla"
                         :id        truthy
                         :lastName  "Panaani"
                         :orgAuthz  {:609-R ["authority" "approver"]}
                         :role      "authority"
                         :username  "priscilla"})]))

  (fact "Priscilla demoted to applicant on login, since no groups"
    (post-pori response-wo-groups) => applicant-page?)

  (fact "Priscilla is an applicant without orgAuthz"
    (let [{user :user} (query priscilla :user)]
      user => (contains {:email     priscilla-email
                         :enabled   true
                         :firstName "Priscilla"
                         :id        truthy
                         :lastName  "Panaani"
                         :role      "applicant"
                         :username  "priscilla.panaani@pori.fi"})
      (keys user) =not=> (contains :orgAuthz)))

  (fact "Erase Priscilla (applicant)"
    (command admin :erase-user :email priscilla-email))

  (fact "Priscilla logged in and created as applicant"
    (post-pori response-wo-groups) => applicant-page?)

  (facts "Response email must match trusted-domains"
    (command admin :update-ad-login-settings
             (-> (util/find-by-id "609-R" minimal/organizations)
                 :ad-login
                 (assoc :trusted-domains ["sipoo.fi"]
                        :org-id "753-R"))) => ok?
    (fact "New authority"
      (post-sipoo response) => login-page?)

    (fact "Applicant"
      (post-sipoo response-wo-groups) => login-page?))

  (fact "Response must be signed"
    (post-pori response-unsigned) => login-page?)

  (fact "It is enough if only assertions are signed"
    (post-pori response-only-assertions-signed) => authority-page?)

  (fact "Only asssertions are signed and also encrypted"
    (post-pori response-encrypted) => authority-page?)

  (fact "Response is signed but assertions are encrypted with unknown key"
    (post-pori response-cannot-decrypt) => login-page?)

  (fact "Wrong certificate"
    (command admin :update-ad-login-settings
             (-> (util/find-by-id "609-R" minimal/organizations)
                 :ad-login
                 (assoc :idp-cert bad-certificate
                        :org-id "609-R"))) => ok?
    (post-pori response) => login-page?
    (post-pori response-only-assertions-signed) => login-page?
    (post-pori response-encrypted) => login-page?)

  (fact "Correct certificate"
    (command admin :update-ad-login-settings
             (-> (util/find-by-id "609-R" minimal/organizations)
                 :ad-login
                 (assoc :idp-cert good-certificate
                        :org-id "609-R"))) => ok?
    (post-pori response) => authority-page?)

  (fact "Unsupported domain"
    (post-route "bad.fi" response) => login-page?)

  (fact "Bad RelayState param"
    (post-pori response "cannot be parsed") => login-page?)

  (fact "Good format, but unsupported RelayState -> login is default"
    (post-pori response
               (freeze-to-string {:target :unsupported :hello "world"}
                                 (:nippy-opts ad-config)))
    => authority-page?)

  (fact "Initially supported but now disabled"
    (command admin :update-ad-login-settings
             (-> (util/find-by-id "609-R" minimal/organizations)
                 :ad-login
                 (assoc :idp-cert good-certificate
                        :enabled false
                        :org-id "609-R"))) => ok?
    (post-pori response) => login-page?))
