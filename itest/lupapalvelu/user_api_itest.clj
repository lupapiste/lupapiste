(ns lupapalvelu.user-api-itest
  (:require [cheshire.core :as json]
            [monger.operators :refer :all]
            [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer [fact* facts*]]
            [lupapalvelu.user :as user]
            [sade.schema-generators :as ssg]))

;;
;; ==============================================================================
;; Getting user and users:
;; ==============================================================================
;;

(apply-remote-minimal)

(def invite-authority-email-subject "Lupapiste: Tervetuloa Lupapisteen viranomaisk\u00e4ytt\u00e4j\u00e4ksi!")

(fact "user query"
  (let [response (query pena :user)]
    response => ok?
    (get-in response [:user :email]) => "pena@example.com"))

(facts "Getting users"

  (fact "applicants are not allowed to call this"
    (query pena :users) =not=> ok?)

  ; It's not nice to test the number of users, but... well, this is relly easy:
  (fact (-> (query admin :users :role "admin") :users count) => 1)
  (fact (-> (query admin :users :organization "753-R") :users count) => 6)
  (fact (-> (query admin :users :role "authority" :organization "753-R") :users count) => 5))

(facts "users-for-datatables"
  (fact (datatables admin :users-for-datatables :params {:length 6 :start 0 :draw "123" :enabled "true" :organizations ["753-R"]})
    => (contains {:ok   true
                  :data (contains {:rows    (comp (partial = 6) count)
                                   :total   6
                                   :display 6
                                   :draw    "123"})}))
  (fact (datatables admin :users-for-datatables :params {:length 6 :start 0 :draw "123" :enabled "true" :organizations ["753-R"] :filter-search "Suur"})
    => (contains {:ok   true
                  :data (contains {:rows    (comp (partial = 1) count)
                                   :total   6
                                   :display 1
                                   :draw    "123"})}))
  (fact (datatables admin :users-for-datatables :params {:length 6 :start 0 :draw "123" :enabled "true" :organizations ["753-R"] :filter-search "SoNJa"})
    => (contains {:ok   true
                  :data (contains {:rows    (comp (partial = 1) count)
                                   :total   6
                                   :display 1
                                   :draw    "123"})})))

(facts "User for edit authority"
  (fact (query sonja :user-for-edit-authority :authority-id "777777777777777777000024") =not=> ok?)
  (fact (query jarvenpaa :user-for-edit-authority :authority-id "777777777777777777000024") =not=> ok?)
  (fact (query sipoo :user-for-edit-authority :authority-id "777777777777777777000024")
    => {:ok   true
        :data {:email     "ronja.sibbo@sipoo.fi"
               :username  "ronja"
               :firstName "Ronja"
               :lastName  "Sibbo"
               :orgAuthz  {:753-R ["authority"]}}}))

(facts "Check passwords"
  (fact "Good password"
    (command pena :check-password :password "pena") => ok?)
  (fact "Bad password"
    (command pena :check-password :password "bad") => {:ok false :text "error.password"})
  (fact "Empty password"
    (command pena :check-password :password "") => fail?))

;;
;; ==============================================================================
;; Creating users:
;; ==============================================================================
;;

(facts "create-user"
  (apply-remote-minimal)
  (fact
    (command pena :create-user
             :email "x@example.com"
             :role "dummy"
             :password "foobarbozbiz")
    => fail?)
  (fact
    (command admin :create-user
             :email "x@example.com"
             :role "authorityAdmin"
             :enabled true
             :organization "753-R"
             :password "foobarbozbiz")
    => ok?)
  ; Check that user was created
  (fact
    (-> (query admin :users :email "x@example.com") :users first)
    => (contains {:role     "authority"
                  :email    "x@example.com"
                  :enabled  true
                  :orgAuthz {:753-R ["authorityAdmin"]}}))

  ; Inbox zero
  (last-email)

  (fact "authorityAdmin can't call create-user (anymore)"
    (command sipoo :create-user
             :email "foo@example.com"
             :role "authority"
             :enabled true)
    => unauthorized?))

;;
;; ==============================================================================
;; Updating user data:
;; ==============================================================================
;;

(facts "Veikko updates his name"
  (fact (command veikko :update-user :firstName "f" :lastName "l") => ok?)
  (fact (-> (query veikko :user) :user) => (contains {:firstName "f" :lastName "l"})))

(def UpdateableUser (select-keys user/User [:firstName :lastName :street :city :zip :phone :language]))

(def invalid-user-data (partial expected-failure? :error.invalid-user-data))

(facts update-user
  (facts "update person-id"
    (fact "basic user"
      (command pena :update-user (assoc (ssg/generate UpdateableUser) :personId "010203+040A"))
      => (partial expected-failure? :error.user.trying-to-update-verified-person-id))

    (fact "company user without id"
      (command kaino :update-user (assoc (ssg/generate UpdateableUser) :personId "010203+040A")) => ok?)

    (fact "company user without id - invalid id"
      (command kaino :update-user (assoc (ssg/generate UpdateableUser) :personId "010203+040B"))
      => invalid-user-data)

    (fact "company user with personId from identification service"
      (command erkki :update-user (assoc (ssg/generate UpdateableUser) :personId "010203+040A"))
      => (partial expected-failure? :error.user.trying-to-update-verified-person-id)))
  (facts "Pena can't edit too much"
    (fact "own info ok"
      (command pena :update-user :firstName "PenTest" :lastName "Penttinen") => ok?
      (let [users (:data (datatables admin :users-for-datatables :params {:filter-search (email-for-key pena)}))]
        (count (:rows users)) => 1
        (get-in users [:rows 0 :firstName]) => "PenTest"))
    (fact "email is not accepted"
      (command pena :update-user :email (email-for-key pena) :firstName "PenTest" :lastName "Penttinen") => invalid-user-data
      (command pena :update-user :email (email-for-key sonja) :firstName "PenTest" :lastName "Penttinen") => invalid-user-data)
    (fact "orgAuthz"
      (command pena :update-user :firstName "Pena" :lastName "Of Sipoo" :orgAuthz {:753-R [:authority]}) => ok?
      (fact "but key is not actually updated"
        (let [user-data (get-in (get-by-id :users pena-id) [:body :data])]
          (:lastName user-data) => "Of Sipoo"
          (contains? (set (keys user-data)) :orgAuthz) => false)))))

(facts "save-application-filter"
  (apply-remote-minimal)

  (fact "fails with invalid filter type"
    (command ronja :save-application-filter :title "titteli" :filter {} :sort {:field "applicant" :asc true} :filterId "beefcace" :filterType "a") => fail?)

  (fact (command ronja :save-application-filter :title "titteli" :filter {} :sort {:field "applicant" :asc true} :filterId "beefcace" :filterType "application") => ok?)
  (fact (command ronja :save-application-filter :title "TJt" :filter {} :sort {:field "foremanRole" :asc true} :filterId "abbacace" :filterType "foreman") => ok?)

  (let [{:keys [applicationFilters foremanFilters defaultFilter]} (query ronja :saved-application-filters)]

    (fact "Application filter is saved"
      (get-in applicationFilters [0 :title]) => "titteli"
      (fact "as default"
        (:id defaultFilter) => "beefcace"))

    (fact "Foreman filter is saved"
      (get-in foremanFilters [0 :title]) => "TJt"
      (fact "as default"
        (:foremanFilterId defaultFilter) => "abbacace"))))

(facts update-default-application-filter
  (apply-remote-minimal)

  (fact "Default search returns minimal query since there is no saved default filter"
    (:search (query sonja :applications-search-default)) => {:applicationType "application"
                                                             :skip            0
                                                             :limit           100
                                                             :sort            {:asc   false
                                                                               :field "modified"}}
    (:search (query pena :applications-search-default)) => {:applicationType "all"
                                                            :skip            0
                                                            :limit           100
                                                            :sort            {:asc   false
                                                                              :field "modified"}})

  (fact (command sonja :update-default-application-filter :filterId "foobar" :filterType "application") => ok?)

  (fact (->> (query admin :user-by-email :email "sonja.sibbo@sipoo.fi") :user :defaultFilter :id) => "foobar")

  (fact "Default search returns query"
    (:search (query sonja :applications-search-default)) => {:applicationType "application"
                                                             :skip            0
                                                             :limit           100
                                                             :sort            {:asc   false
                                                                               :field "modified"}
                                                             :handlers        []
                                                             :tags            []
                                                             :operations      []
                                                             :organizations   []
                                                             :areas           []})

  (fact "Overwrite default filter"
    (command sonja :update-default-application-filter :filterId "barfoo" :filterType "application") => ok?)

  (fact "Filter overwritten"
    (->> (query admin :user-by-email :email "sonja.sibbo@sipoo.fi") :user :defaultFilter :id) => "barfoo"))

(facts "upsert-organization-user"
  (apply-remote-minimal)

  (fact "adminAdmin creates authority to org"
    (command admin :create-user
             :email "foo@example.com"
             :role "authority"
             :enabled "true"
             :organization "529-R")
    => ok?)

  (fact
    (->> (query admin :user-by-email :email "foo@example.com")
         :user
         :orgAuthz
         keys)
    => [:529-R])

  (fact
    (command sipoo :upsert-organization-user
             :email "foo@example.com"
             :firstName "bar"
             :lastName "har"
             :roles ["authority"])
    => ok?)

  (fact
    (->> (query admin :user-by-email :email "foo@example.com")
         :user
         :orgAuthz
         keys
         set)
    => #{:529-R :753-R})

  (fact
    (command sipoo :upsert-organization-user
             :email "foo@example.com"
             :firstName "bar"
             :lastName "har"
             :roles ["authority"])
    => ok?)

  (facts "roles param"
    (fact "roles required"
      (command sipoo :upsert-organization-user
               :email "foo@example.com"
               :firstName "bar"
               :lastName "har"
               :roles [])
      => (contains {:ok         false,
                    :parameters ["roles"]
                    :text       "error.vector-parameters-with-items-missing-required-keys"}))
    (fact "roles are validated"
      (command sipoo :upsert-organization-user
               :email "foo@example.com"
               :firstName "bar"
               :lastName "har"
               :roles ["foobar-role"])
      => (contains {:ok         false,
                    :text       "error.invalid-role"})))

  (fact "Not authority email"
    (command sipoo :upsert-organization-user
             :email (email-for-key teppo)
             :firstName "Teppo"
             :lastName "Example"
             :roles ["authority"])
    => (contains {:ok false :text "error.user-not-found"}))

  (fact
    (command sipoo :upsert-organization-user
             :email "tonja.sibbo@sipoo.fi"
             :firstName "bar"
             :roles ["authority"])
    => (contains {:ok         false
                  :parameters ["lastName"]
                  :text       "error.missing-parameters"}))

  (fact "invite new user Tonja to Sipoo"
    (command sipoo :upsert-organization-user
             :email "tonja.sibbo@sipoo.fi"
             :firstName "bar"
             :lastName "har"
             :operation "add"
             :roles ["authority"])
    => ok?
    (fact "newly created authority receives mail"
      (let [email (last-email)]
        (:to email) => (contains "tonja.sibbo@sipoo.fi")
        (:subject email) => invite-authority-email-subject
        (get-in email [:body :plain]) => (contains #"/app/fi/welcome#!/setpw/[A-Za-z0-9-]+"))))

  (fact "add existing authority to new organization"

    (command naantali :upsert-organization-user :email "tonja.sibbo@sipoo.fi" :firstName "bar" :lastName "har" :operation "add" :roles ["authority"]) => ok?
    (let [email (last-email)]
      (:to email) => (contains "tonja.sibbo@sipoo.fi")
      (:subject email) => invite-authority-email-subject
      (get-in email [:body :plain]) => (contains "Naantalin rakennusvalvonta"))))

(facts remove-user-organization
  (apply-remote-minimal)

  (fact (command naantali :create-user :email "foo@example.com" :role "authority" :enabled "true" :organization "529-R") => ok?)
  (fact (command sipoo :upsert-organization-user :email "foo@example.com" :firstName "bar" :lastName "har" :operation "add" :roles ["authority"]) => ok?)
  (fact (->> (query admin :user-by-email :email "foo@example.com") :user :orgAuthz keys (map name)) => (just ["529-R" "753-R"] :in-any-order))
  (fact (command sipoo :remove-user-organization :email "foo@example.com") => ok?)
  (fact (->> (query admin :user-by-email :email "foo@example.com") :user :orgAuthz keys (map name)) => ["529-R"]))

(fact update-user-roles
  (apply-remote-minimal)
  (fact "Meta: check current roles" (-> (query admin :user-by-email :email "sonja.sibbo@sipoo.fi") :user :orgAuthz :753-R) => ["authority" "approver"])
  (fact (command sipoo :update-user-roles :email "sonja.sibbo@sipoo.fi" :roles ["authority" "foobar"]) => fail?)
  (fact (-> (query admin :user-by-email :email "sonja.sibbo@sipoo.fi") :user :orgAuthz :753-R) => ["authority" "approver"])

  (fact "Sipoo does not have permanent achive, can not set TOS roles but reader is OK"
    (command sipoo :update-user-roles :email "sonja.sibbo@sipoo.fi" :roles ["authority" "tos-editor" "tos-publisher" "reader"]) => ok?
    (-> (query admin :user-by-email :email "sonja.sibbo@sipoo.fi") :user :orgAuthz :753-R) => ["authority" "reader"])

  (fact "Jarvenpaa has permanent achive, can set TOS roles"
    (fact "Meta: check current roles"
      (-> (query admin :user-by-email :email "rakennustarkastaja@jarvenpaa.fi") :user :orgAuthz :186-R) => ["authority" "approver" "archivist"])
    (command jarvenpaa :update-user-roles :email "rakennustarkastaja@jarvenpaa.fi" :roles ["authority" "tos-editor" "tos-publisher" "archivist"]) => ok?
    (-> (query admin :user-by-email :email "rakennustarkastaja@jarvenpaa.fi") :user :orgAuthz :186-R) => ["authority" "tos-editor" "tos-publisher" "archivist"]))

(fact "changing user info"
  (apply-remote-minimal)
  (fact
    (query teppo :user) => (contains {:ok   true,
                                      :user (contains {:role                 "applicant"
                                                       :email                "teppo@example.com"
                                                       :username             "teppo@example.com"
                                                       :firstName            "Teppo"
                                                       :lastName             "Nieminen"
                                                       :street               "Mutakatu 7"
                                                       :city                 "Tampere"
                                                       :zip                  "33560"
                                                       :phone                "0505503171"
                                                       :allowDirectMarketing true})}))
  (fact
    (let [data {:firstName            "Seppo"
                :lastName             "Sieninen"
                :street               "Sutakatu 7"
                :city                 "Sampere"
                :zip                  "33200"
                :phone                "0505503171"
                :architect            true
                :allowDirectMarketing true
                :degree               "kirvesmies"
                :graduatingYear       "2000"
                :fise                 "f"
                :fiseKelpoisuus       "tavanomainen p\u00e4\u00e4suunnittelu (uudisrakentaminen)"
                :companyName          "cn"
                :companyId            "1060155-5"}]
      (apply command teppo :update-user (flatten (seq data))) => ok?
      (query teppo :user) => (contains {:user (contains data)}))))

(facts "Auth admin edits authority info"
  (apply-remote-minimal)

  (let [command-data {:firstName "Tonja"
                      :lastName  "Tibbo"
                      :email     "ronja.sibbo@sipoo.fi"
                      :new-email "tonja.tibbo@sipoo.fi"}]

    (fact "Sipoo can edit authority info"
      (command sipoo :update-auth-info command-data) => ok?)

    (fact "Sipoo can't change email if it's already in use"
      (command sipoo :update-auth-info (assoc command-data :new-email "sonja.sibbo@sipoo.fi")) =not=> ok?)

    (fact "Sipoo can give authz to Pekka Borga but can not edit info"
      (let [pekka {:firstName "Pekka"
                   :lastName  "Borga"
                   :email     "pekka.borga@porvoo.fi"
                   :roles     ["commenter"]}]
        (command sipoo :upsert-organization-user pekka) => ok?
        (command sipoo :update-auth-info (-> pekka
                                             (dissoc :roles)
                                             (assoc :new-email "pekka.porvoo@porvoo.fi")))
        =not=> ok?))
    (fact "Sonja can not edit authority info"
      (command sonja :update-auth-info command-data) =not=> ok?)
    (fact "Jarvenpaa can not edit info of authority in Sipoo"
      (command jarvenpaa :update-auth-info command-data) =not=> ok?)))

;;
;; historical tests, dragons be here...
;;

(facts* "uploading user attachment"
  (apply-remote-minimal)

  ;
  ; Initially pena does not have attachments?
  ;

  (fact "Initially pena does not have attachments?"
    (:attachments (query pena "user-attachments")) => nil?)

  ;
  ; Pena uploads a tutkintotodistus:
  ;

  (fact "Applicant Pena can upload user attachment"
    (query pena :add-user-attachment-allowed) => ok?)

  (fact "Auhtority Sonja can't upload user attachment"
    (query sonja :add-user-attachment-allowed) => unauthorized?)

  (let [attachment-id (:attachment-id (upload-user-attachment pena "osapuolet.tutkintotodistus" true))
        filename      "test-attachment.txt"]

    ; Now Pena has attachment
    (get (:attachments (query pena "user-attachments")) 0)
    => (contains {:attachment-id   attachment-id
                  :attachment-type {:type-group "osapuolet"
                                    :type-id    "tutkintotodistus"}
                  :file-name       filename})

    ; Attachment is in GridFS

    (let [resp (raw pena "download-user-attachment" :attachment-id attachment-id) => http200?]
      (:body resp) => "This is test file for file upload in itest."
      (-> resp :headers :content-disposition) => (format "attachment;filename=\"%s\"" filename))

    (fact "Sonja can not get attachment"
      (raw sonja "download-user-attachment" :attachment-id attachment-id) => http401?)

    (fact "Sonja can not delete attachment"
      (command sonja "remove-user-attachment" :attachment-id attachment-id)
      (get (:attachments (query pena "user-attachments")) 0) =not=> nil?)

    ; Pena can delete attachment

    (command pena "remove-user-attachment" :attachment-id attachment-id) => ok?
    (:attachments (query pena "user-attachments")) => empty?))

(facts* "upload errors"
  (fact "illegal attachment type"
    (upload-user-attachment pena "foo" false) => (partial expected-failure? :error.illegal-attachment-type))
  (fact "illegal mime"
    (upload-user-attachment pena "osapuolet.tutkintotodistus" false "dev-resources/krysp/verdict-r.xml") => (partial expected-failure? :error.file-upload.illegal-file-type)))

;;;; Erase user data

(facts "Admin erases user data"
  (fact "erase sven's data"
    (let [{{:keys [id email]} :user} (query admin :user-by-email :email "sven@example.com") => ok?
          obfuscated-username (str "poistunut_" id "@example.com")]
      (command admin :erase-user :email email) => ok?
      (query admin :user-by-email :email email) => {:ok true, :user nil}
      (query admin :user-by-email :email obfuscated-username)
      => {:ok true
          :user {:id id
                 :firstName "Poistunut"
                 :lastName "K\u00e4ytt\u00e4j\u00e4"
                 :role "applicant"
                 :email obfuscated-username
                 :username obfuscated-username
                 :enabled false
                 :state "erased"}}))

  (fact "cannot erase already erased user"
    (command admin :erase-user :email "sven@example.com") => {:ok false, :text "not-found"})

  (fact "cannot erase nonexistent user"
    (command admin :erase-user :email "fubar@example.com") => {:ok false, :text "not-found"}))

;;
;; ==============================================================================
;; Admin impersonates an authority
;; ==============================================================================
;;

(facts* "impersonating"
  (let [store        (atom {})
        params       {:cookie-store (->cookie-store store)
                      :follow-redirects false
                      :throw-exceptions false}
        login        (http-post
                       (str (server-address) "/api/login")
                       (assoc params :form-params {:username "admin" :password "admin"})) => http200?
        csrf-token   (get-anti-csrf store) => truthy
        params       (assoc params :headers {"x-anti-forgery-token" csrf-token})
        sipoo-rakval (-> "sipoo" find-user-from-minimal :orgAuthz keys first name)
        impersonate  (fn [password]
                       (-> (http-post
                             (str (server-address) "/api/command/impersonate-authority")
                             (assoc params
                               :form-params (merge {:organizationId sipoo-rakval :role "approver"} (when password {:password password}))
                               :content-type :json))
                           decode-response :body))
        role         (fn [] (-> (http-get (str (server-address) "/api/query/user") params) decode-response :body :user :role))
        actions      (fn [] (-> (http-get (str (server-address) "/api/query/allowed-actions") params) decode-response :body :actions))]

    (fact "impersonation action is available"
      (:impersonate-authority (actions)) => ok?)

    (fact "admin can not query property owners"
      (-> (http-get (str (server-address) "/api/query/owners?propertyId=0") params) decode-response :body) => unauthorized?)

    (fact "fails without password"
      (impersonate nil) => fail?)

    (fact "fails with wrong password"
      (impersonate "nil") => fail?)

    (fact "role remains admin"
      (role) => "admin")

    (let [application (create-and-submit-application pena :propertyId sipoo-property-id) => truthy
          application-id (:id application)
          query-as-admin (http-get (str (server-address) "/api/query/application?id=" application-id) params) => http200?]

      (fact "sonja sees the application"
        (query-application sonja application-id) => truthy)

      (fact "but admin doesn't"
        (-> query-as-admin decode-response :body) => not-accessible?)

      (fact "succeeds with correct password"
        (impersonate "admin") => ok?)

      (fact "but not again (as we're now impersonating)"
        (impersonate "admin") => fail?)

      (fact "instead, application is visible"
        (let [query-as-imposter (http-get (str (server-address) "/api/query/application?id=" application-id) params) => http200?
              body (-> query-as-imposter decode-response :body) => ok?
              application (:application body)]
          (:id application) => application-id)))

    (fact "role has changed to authority"
      (role) => "authority")

    (fact "every available action is a query or raw, i.e. not a command
          (or any other mutating action type we might have in the future)"
      (let [action-names (keys (filter (fn [[name ok]] (ok? ok)) (actions)))]
        ; Make sure we have required all the actions
        (require 'lupapalvelu.server)
        (map #(:type (% @lupapalvelu.action/actions)) action-names) => (partial every? #{:query :raw})))

    (fact "still can not query property owners"
      (let [req (-> params
                    (assoc-in [:headers "content-type"] "application/json;charset=utf-8")
                    (assoc :body (json/encode {:propertyIds ["12312312341234"]})))]
        (-> (http-post (str (server-address) "/api/datatables/owners") req) decode-response :body)) => unauthorized?)))

(facts* "reset password email"
  (last-email) ; Inbox zero

  (let [params {:form-params {:email (email-for "pena")}
                :content-type :json
                :follow-redirects false
                :throw-exceptions false}
        resp (http-post (str (server-address) "/api/reset-password") params) => http200?
        email  (last-email)]
    (-> resp decode-response :body) => ok?
    (:to email) => (contains (email-for "pena"))
    (:to email) => #"Pena Panaani <.+@.+>"
    (:subject email) => "Lupapiste: Uusi salasana"
    (get-in email [:body :plain]) => (contains "/app/fi/welcome#!/setpw/")))
