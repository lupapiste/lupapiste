(ns lupapalvelu.registration-itest
  (:require [lupapalvelu.dummy-ident-itest-util :refer [dummy-ident-finish dummy-ident-init]]
            [lupapalvelu.factlet :refer [facts*]]
            [lupapalvelu.ident.dummy :as dummy]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer [->cookie-store admin decode-body fail? http-get last-email login ok? query
                                            redirects-to server-address with-test-system]]
            [lupapalvelu.test-util :as test-util]
            [lupapalvelu.vetuma-itest-util :refer :all]
            [midje.sweet :refer :all]
            [ring.util.codec :as codec]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.xml :as xml])
  (:import [org.apache.http.cookie Cookie]))

(set! *warn-on-reflection* true)

;;
;; Other helpers
;;

(defn- verify-new-user [new-user new-user-details person-id]
  (fact "username" (:username new-user) => (:email new-user-details))
  (fact "email" (:email new-user) => (:email new-user-details))
  (fact "address" (:street new-user) => (:street new-user-details))
  (fact "zip" (:zip new-user) => (:zip new-user-details))
  (fact "city" (:city new-user) => (:city new-user-details))
  (fact "personId" (:personId new-user) => person-id)
  (fact "personIdSource" (:personIdSource new-user) => (or (:personIdSource new-user-details)
                                                           "identification-service"))
  (fact "enabled" (:enabled new-user) => false)
  (fact "role" (:role new-user) => "applicant")
  (fact "orgAuthz" (:orgAuthz new-user) => empty?)
  (fact "phone" (:phone new-user) => (:phone new-user-details))
  (fact "architect" (:architect new-user) => (:architect new-user-details))
  (fact "graduatingYear" (:graduatingYear new-user) => (:graduatingYear new-user-details))
  (fact "fise" (:fise new-user) => (:fise new-user-details))
  (fact "fiseKelpoisuus" (:fiseKelpoisuus new-user) => (:fiseKelpoisuus new-user-details))
  (fact "degree" (:degree new-user) => (:degree new-user-details))
  (fact "notification" (:notification new-user) => {:messageI18nkey "user.notification.firstLogin.message"
                                                    :titleI18nkey   "user.notification.firstLogin.title"}))

;;
;; *** FACTS ***
;;

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (let [orig-feature-dummy-ident (env/feature? :dummy-ident)]
    (facts* "Registration VETUMA"
      (env/set-feature! false [:dummy-ident])
      (let [store  (atom {})
            params (default-vetuma-params (->cookie-store store))
            trid   (vetuma-init params default-token-query)]

        (fact "trid" trid =not=> ss/blank?)

        (fact "Vetuma redirect"
          (let [resp (vetuma-finish params trid)]
            resp => (partial redirects-to (get-in default-token-query [:query-params :success]))))

        (last-email) ; Inbox zero

        (let [vetuma-data (decode-body (http-get (str (server-address) "/api/vetuma/user") params))
              stamp       (:stamp vetuma-data)  => string?
              person-id   (:userid vetuma-data) => string?
              cmd-opts    {:cookies {"anti-csrf-token" {:value "123"}}
                           :headers {"x-anti-forgery-token" "123"}}
              details     (stamped-new-user-details stamp)
              resp        (register cmd-opts details)
              user-id     (:id resp)            => string?
              email       (last-email)
              body        (:body email)]

          (fact "Registration OK" resp => ok?)

          (facts "New user data"
            (let [new-user (first (:users (query admin :users :userId user-id)))]
              (verify-new-user new-user details person-id)))

          (fact "New user got email"
            (:to email) => (str "Jukka Palmu" " <" (:email details) ">")
            (:subject email) => "Lupapiste: Tervetuloa Lupapisteeseen!")

          (fact "Email has body"
            body => map?
            (fact "html" (:html body) => string?)
            (fact "plain text" (:plain body) => string?))

          (fact "Register again with the same email"
            (last-email) ; Inbox zero
            (reset! store {}) ; clear cookies
            (let [trid (vetuma-init params default-token-query)]
              (vetuma-finish params trid))

            (let [stamp           (-> (str (server-address) "/api/vetuma/user")
                                      (http-get params)
                                      decode-body
                                      :stamp)
                  new-user-email  "jukka@example.com"
                  new-user-pw     test-util/strong-password
                  new-user-phone2 "046"
                  cmd-opts        {:cookies {"anti-csrf-token" {:value "123"}}
                                   :headers {"x-anti-forgery-token" "123"}}
                  register-opts   {:stamp                stamp
                                   :phone                new-user-phone2
                                   :city                 "Tampere"
                                   :zip                  "33100"
                                   :street               "street"
                                   :password             new-user-pw
                                   :email                new-user-email
                                   :allowDirectMarketing true}
                  ;; First attempt with poor password
                  resp-inv        (register cmd-opts (assoc register-opts :password new-user-email))
                  _               (fact "accidentally using email as password causes low score"
                                    (-> resp-inv :text keyword) => :error.password.minscore)
                  resp            (register cmd-opts register-opts)
                  user-id         (:id resp)
                  email           (last-email)
                  body            (:body email)]

              (fact "register"
                stamp => string?
                resp => ok?
                user-id => string?)

              (facts "New user data is overwritten"
                (let [new-user (first (:users (query admin :users :userId user-id)))]
                  (fact "phone" (:phone new-user) => new-user-phone2)))

              (fact "New user got email"
                (:to email) => (str "Jukka Palmu" " <" new-user-email ">")
                (:subject email) => "Lupapiste: Tervetuloa Lupapisteeseen!")

              (fact "Can NOT log in"
                (login new-user-email new-user-pw) => fail?)

              (let [mail-html  (xml/parse (:html body))
                    links      (xml/select mail-html [:a])
                    first-href (-> links first xml/attr :href)]

                (fact "First link contains activation token" first-href =>
                      (contains "/app/security/activate/"))

                (fact "Activate account"
                  (let [resp (http-get first-href {:redirect-strategy :none})]
                    resp => (partial redirects-to "/app/fi/applicant")))

                (fact "Logged in flag is set"
                  (-> (http-get (str (server-address) "/dev/by-id/users/" user-id) (merge cmd-opts params))
                      decode-body
                      :data) => (contains {:firstLogin true}))

                (fact "Second activation attempt leads to login page"
                  (let [resp (http-get first-href {:redirect-strategy :none})]
                    resp => (partial redirects-to "/login/fi")))

                (fact "Log in"
                  (login new-user-email new-user-pw params) => ok?)

                (let [^Cookie cookie (get @store "anti-csrf-token")
                      csrf           (-> cookie .getValue codec/url-decode)
                      params         (assoc params :headers {"x-anti-forgery-token" csrf})]
                  (fact "1st user query has firstLogin"
                    (-> (http-get
                          (str (server-address) "/api/query/user")
                          params)
                        decode-body
                        :user) => (contains {:firstLogin true}))
                  (fact "2nd user query does not have firstLogin flag"
                    (-> (http-get
                          (str (server-address) "/api/query/user")
                          params)
                        decode-body
                        :user
                        keys) =not=> (contains :firstLogin)))))))))

    (facts* "Registration using dummy ident"
      (env/set-feature! true [:dummy-ident])
      (let [store  (atom {})
            params (default-vetuma-params (->cookie-store store))
            trid   (dummy-ident-init params default-token-query)]

        (fact "trid" trid =not=> ss/blank?)

        (fact "redirect"
          (let [resp (dummy-ident-finish params {:personId dummy/dummy-person-id} trid)]
            resp => (partial redirects-to (get-in default-token-query [:query-params :success]))))

        (last-email) ; Inbox zero

        (let [vetuma-data (decode-body (http-get (str (server-address) "/api/vetuma/user") params))
              stamp       (:stamp vetuma-data)
              person-id   (:userid vetuma-data)
              cmd-opts    {:cookies {"anti-csrf-token" {:value "123"}}
                           :headers {"x-anti-forgery-token" "123"}}
              details     (assoc (stamped-new-user-details stamp) :email "jukka2@example.com")
              resp        (register cmd-opts details)
              user-id     (:id resp)
              email       (last-email)
              body        (:body email)]

          (fact "Registration OK"
            stamp => string?
            person-id => string?
            user-id => string?
            resp => ok?)

          (facts "New user data"
            (let [new-user (first (:users (query admin :users :userId user-id)))]
              (verify-new-user new-user details person-id)))

          (fact "New user got email"
            (:to email) => (:email details)
            (:subject email) => "Lupapiste: Tervetuloa Lupapisteeseen!")

          (fact "Email has body"
            body => map?
            (fact "html" (:html body) => string?)
            (fact "plain text" (:plain body) => string?))

          (fact "Register again with the same email"
            (last-email) ; Inbox zero
            (reset! store {}) ; clear cookies
            (let [trid (dummy-ident-init params default-token-query)]
              (dummy-ident-finish params {:personId dummy/dummy-person-id} trid))

            (let [stamp           (-> (str (server-address) "/api/vetuma/user")
                                      (http-get params)
                                      decode-body
                                      :stamp)
                  new-user-email  "jukka2@example.com"
                  new-user-pw     test-util/strong-password
                  new-user-phone2 "046"
                  cmd-opts        {:cookies {"anti-csrf-token" {:value "123"}}
                                   :headers {"x-anti-forgery-token" "123"}}
                  resp            (register cmd-opts {:stamp                stamp
                                                      :phone                new-user-phone2
                                                      :city                 "Tampere"
                                                      :zip                  "33100"
                                                      :street               "street"
                                                      :password             new-user-pw
                                                      :email                new-user-email
                                                      :allowDirectMarketing true})
                  user-id         (:id resp)
                  email           (last-email)
                  body            (:body email)]

              (fact "register"
                stamp => string?
                resp => ok?
                user-id => string?)

              (facts "New user data is overwritten"
                (let [new-user (first (:users (query admin :users :userId user-id)))]
                  (fact "phone" (:phone new-user) => new-user-phone2)))

              (fact "New user got email"
                (:to email) => new-user-email
                (:subject email) => "Lupapiste: Tervetuloa Lupapisteeseen!")

              (fact "Can NOT log in"
                (login new-user-email new-user-pw) => fail?)

              (let [mail-html  (xml/parse (:html body))
                    links      (xml/select mail-html [:a])
                    first-href (-> links first xml/attr :href)]

                (fact "First link contains activation token" first-href =>
                      (contains "/app/security/activate/"))

                (fact "Activate account"
                  (let [resp (http-get first-href {:redirect-strategy :none})]
                    resp => (partial redirects-to "/app/fi/applicant")))

                (fact "Logged in flag is set"
                  (-> (http-get (str (server-address) "/dev/by-id/users/" user-id) (merge cmd-opts params))
                      decode-body
                      :data) => (contains {:firstLogin true}))

                (fact "Second activation attempt leads to login page"
                  (let [resp (http-get first-href {:redirect-strategy :none})]
                    resp => (partial redirects-to "/login/fi")))

                (fact "Log in"
                  (login new-user-email new-user-pw params) => ok?)

                (let [^Cookie cookie (get @store "anti-csrf-token")
                      csrf           (-> cookie .getValue codec/url-decode)
                      params         (assoc params :headers {"x-anti-forgery-token" csrf})]
                  (fact "1st user query has firstLogin"
                    (-> (http-get
                          (str (server-address) "/api/query/user")
                          params)
                        decode-body
                        :user) => (contains {:firstLogin true}))
                  (fact "2nd user query does not have firstLogin flag"
                    (-> (http-get
                          (str (server-address) "/api/query/user")
                          params)
                        decode-body
                        :user
                        keys) =not=> (contains :firstLogin)))))))))

    (facts* "Registration using eIDAS for EU citizens outside Finland"
      (env/set-feature! true [:dummy-ident])
      (let [store        (atom {})
            params       (default-vetuma-params (->cookie-store store))
            trid         (dummy-ident-init params default-token-query)
            eidas-fields {:eidasId         "12345-TEST"
                          :firstName       "Jukka"
                          :eidasFamilyName "Bryssel"
                          :eidasBirthDate  "1948-03-17"}]

        (fact "trid" trid =not=> ss/blank?)

        (fact "redirect"
          (let [resp (dummy-ident-finish params eidas-fields trid)]
            resp => (partial redirects-to (get-in default-token-query [:query-params :success]))))

        (last-email) ; Inbox zero

        (let [vetuma-data (decode-body (http-get (str (server-address) "/api/vetuma/user") params))
              stamp       (:stamp vetuma-data)        => string?
              person-id   (:userid vetuma-data)       => nil?
              cmd-opts    {:cookies {"anti-csrf-token" {:value "123"}}
                           :headers {"x-anti-forgery-token" "123"}}
              details     (assoc (stamped-new-user-details stamp)
                                 :email "jukka3@example.com")
              resp        (register cmd-opts details) => ok?
              user-id     (:id resp)                  => string?
              email       (last-email)
              body        (:body email)]

          (fact "Registration OK" resp => ok?)

          (facts "New user data"
            (let [new-user (first (:users (query admin :users :userId user-id)))]
              (verify-new-user new-user (assoc details :personIdSource "eIDAS") person-id)))

          (fact "New user got email"
            (:to email) => (str "Jukka Bryssel <" (:email details) ">")
            (:subject email) => "Lupapiste: Tervetuloa Lupapisteeseen!")

          (fact "Email has body"
            body => map?
            (fact "html" (:html body) => string?)
            (fact "plain text" (:plain body) => string?))

          (fact "Register again with the same email"
            (last-email) ; Inbox zero
            (reset! store {}) ; clear cookies
            (let [trid (dummy-ident-init params default-token-query)]
              (dummy-ident-finish params eidas-fields trid))

            (let [stamp           (-> (str (server-address) "/api/vetuma/user")
                                      (http-get params)
                                      decode-body
                                      :stamp)
                  new-user-email  "jukka3@example.com"
                  new-user-pw     test-util/strong-password
                  new-user-phone2 "046"
                  cmd-opts        {:cookies {"anti-csrf-token" {:value "123"}}
                                   :headers {"x-anti-forgery-token" "123"}}
                  resp            (register cmd-opts {:stamp                stamp
                                                      :phone                new-user-phone2
                                                      :city                 "Tampere"
                                                      :zip                  "33100"
                                                      :street               "street"
                                                      :password             new-user-pw
                                                      :email                new-user-email
                                                      :allowDirectMarketing true})
                  user-id         (:id resp)
                  email           (last-email)
                  body            (:body email)]

              (fact "register"
                stamp => string?
                resp => ok?
                user-id => string?)

              (facts "New user data is overwritten"
                (let [new-user (first (:users (query admin :users :userId user-id)))]
                  (fact "phone" (:phone new-user) => new-user-phone2)))

              (fact "New user got email"
                (:to email) => (str "Jukka Bryssel <" new-user-email ">")
                (:subject email) => "Lupapiste: Tervetuloa Lupapisteeseen!")

              (fact "Can NOT log in"
                (login new-user-email new-user-pw) => fail?)

              (let [mail-html  (xml/parse (:html body))
                    links      (xml/select mail-html [:a])
                    first-href (-> links first xml/attr :href)]

                (fact "First link contains activation token" first-href =>
                      (contains "/app/security/activate/"))

                (fact "Activate account"
                  (let [resp (http-get first-href {:redirect-strategy :none})]
                    resp => (partial redirects-to "/app/fi/applicant")))

                (fact "Logged in flag is set"
                  (-> (http-get (str (server-address) "/dev/by-id/users/" user-id) (merge cmd-opts params))
                      decode-body
                      :data) => (contains {:firstLogin true}))

                (fact "Second activation attempt leads to login page"
                  (let [resp (http-get first-href {:redirect-strategy :none})]
                    resp => (partial redirects-to "/login/fi")))

                (fact "Log in"
                  (login new-user-email new-user-pw params) => ok?)

                (let [^Cookie cookie (get @store "anti-csrf-token")
                      csrf           (-> cookie .getValue codec/url-decode)
                      params         (assoc params :headers {"x-anti-forgery-token" csrf})]
                  (fact "1st user query has firstLogin"
                    (-> (http-get
                          (str (server-address) "/api/query/user")
                          params)
                        decode-body
                        :user) => (contains {:firstLogin true}))
                  (fact "2nd user query does not have firstLogin flag"
                    (-> (http-get
                          (str (server-address) "/api/query/user")
                          params)
                        decode-body
                        :user
                        keys) =not=> (contains :firstLogin)))))))))

    (env/set-feature! orig-feature-dummy-ident [:dummy-ident])))
