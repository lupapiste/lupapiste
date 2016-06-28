(ns lupapalvelu.registration-itest
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [net.cgrand.enlive-html :as e]
            [cheshire.core :as json]
            [sade.util :as util]
            [sade.strings :as ss]
            [sade.xml :as xml]
            [lupapalvelu.factlet :refer [fact* facts*]]
            [lupapalvelu.itest-util :refer [->cookie-store server-address decode-response
                                            admin query command http-get http-post
                                            last-email apply-remote-minimal
                                            ok? fail? http200? redirects-to
                                            login
                                            ]]
            [lupapalvelu.vetuma :as vetuma]
            [lupapalvelu.vetuma-itest-util :refer :all]
            ))

(testable-privates lupapalvelu.vetuma mac-of keys-as-keywords)

(apply-remote-minimal)

(defn- decode-body [resp] (:body (decode-response resp)))

(defn- vetuma-finish [request-opts trid]
  (vetuma-fake-respose request-opts (default-vetuma-response-data trid)))

(defn- register [base-opts user]
  (decode-body
    (http-post
      (str (server-address) "/api/command/register-user")
      (util/deep-merge
        base-opts
        {;:debug true
         :headers {"content-type" "application/json;charset=utf-8"}
         :body (json/encode user)}))))

(facts* "Registration"
 (let [store (atom {})
       params (default-vetuma-params (->cookie-store store))
       trid (vetuma-init params default-token-query)]

   (fact "trid" trid =not=> ss/blank?)

   (fact "Vetuma redirect"
     (let [resp (vetuma-finish params trid)]
       resp => (partial redirects-to (get-in default-token-query [:query-params :success]))))

   (last-email) ; Inbox zero

   (let [vetuma-data (decode-body (http-get (str (server-address) "/api/vetuma/user") params))
         stamp (:stamp vetuma-data) => string?
         person-id (:userid vetuma-data) => string?
         new-user-email "jukka@example.com"
         new-user-address  "Osootes"
         new-user-zip      "12345"
         new-user-city     "Tammerfors"
         new-user-pw "salasana"
         new-user-phone "0500"
         new-user-architect true
         new-user-graduatingYear "1978"
         new-user-fise "foobar"
         new-user-fise-kelpoisuus "tavanomainen p\u00e4\u00e4suunnittelu (uudisrakentaminen)"
         new-user-degree "diplomi-insin\u00f6\u00f6ri"
         cmd-opts {:cookies {"anti-csrf-token" {:value "123"}}
                   :headers {"x-anti-forgery-token" "123"}}
         resp (register cmd-opts {:stamp stamp
                                  :phone new-user-phone
                                  :city new-user-city
                                  :zip new-user-zip
                                  :street new-user-address
                                  :password new-user-pw
                                  :email new-user-email
                                  :rakentajafi false
                                  :allowDirectMarketing true
                                  :architect new-user-architect
                                  :graduatingYear new-user-graduatingYear
                                  :fise new-user-fise
                                  :fiseKelpoisuus new-user-fise-kelpoisuus
                                  :degree new-user-degree})
         user-id (:id resp) => string?
         email (last-email)
         body (:body email)]

     (fact "Registration OK" resp => ok?)

     (facts "New user data"
       (let [new-user (first (:users (query admin :users :userId user-id)))]
         (fact "username" (:username new-user) => new-user-email)
         (fact "email" (:email new-user) => new-user-email)
         (fact "address" (:street new-user) => new-user-address)
         (fact "zip" (:zip new-user) => new-user-zip)
         (fact "city" (:city new-user) => new-user-city)
         (fact "personId" (:personId new-user) => person-id)
         (fact "enabled" (:enabled new-user) => false)
         (fact "role" (:role new-user) => "applicant")
         (fact "orgAuthz" (:orgAuthz new-user) => empty?)
         (fact "phone" (:phone new-user) => new-user-phone)
         (fact "architect" (:architect new-user) => new-user-architect)
         (fact "graduatingYear" (:graduatingYear new-user) => new-user-graduatingYear)
         (fact "fise" (:fise new-user) => new-user-fise)
         (fact "fiseKelpoisuus" (:fiseKelpoisuus new-user) => new-user-fise-kelpoisuus)
         (fact "degree" (:degree new-user) => new-user-degree)
         (fact "notification" (:notification new-user) => {:messageI18nkey "user.notification.firstLogin.message"
                                                           :titleI18nkey "user.notification.firstLogin.title"})))

     (fact "New user got email"
       (:to email) => new-user-email
       (:subject email) => "Lupapiste: K\u00e4ytt\u00e4j\u00e4tunnuksen aktivointi")

     (fact "Email has body"
       body => map?
       (fact "html" (:html body) => string?)
       (fact "plain text" (:plain body) => string?))

     (fact "Register again with the same email"
       (last-email) ; Inbox zero
       (reset! store {}) ; clear cookies
       (let [trid (vetuma-init params default-token-query)]
         (vetuma-finish params trid))

       (let [stamp (:stamp (decode-body (http-get (str (server-address) "/api/vetuma/user") params))) => string?
             new-user-email "jukka@example.com"
             new-user-pw "salasana"
             new-user-phone2 "046"
             cmd-opts {:cookies {"anti-csrf-token" {:value "123"}}
                   :headers {"x-anti-forgery-token" "123"}}
             resp (register cmd-opts {:stamp stamp
                                      :phone new-user-phone2
                                      :city "Tampere"
                                      :zip "12345"
                                      :street "street"
                                      :password new-user-pw
                                      :email new-user-email
                                      :rakentajafi false
                                      :allowDirectMarketing true}) => ok?
             user-id (:id resp) => string?
             email (last-email)
             body (:body email)]

         (facts "New user data is overwritten"
           (let [new-user (first (:users (query admin :users :userId user-id)))]
             (fact "phone" (:phone new-user) => new-user-phone2)))

         (fact "New user got email"
           (:to email) => new-user-email
           (:subject email) => "Lupapiste: K\u00e4ytt\u00e4j\u00e4tunnuksen aktivointi")

         (fact "Can NOT log in"
           (login new-user-email new-user-pw) => fail?)

         (let [mail-html (xml/parse (:html body))
               links (xml/select mail-html [:a])
               first-href (-> links first xml/attr :href)]

           (fact "First link contains activation token" first-href =>
             (contains "/app/security/activate/"))

           (fact "Activate account"
             (let [resp (http-get first-href {:follow-redirects false})]
               resp => (partial redirects-to "/app/fi/applicant")))

           (fact "Second activation attempt leads to login page"
             (let [resp (http-get first-href {:follow-redirects false})]
               resp => (partial redirects-to "/app/fi/welcome")))

           (fact "Log in"
             (login new-user-email new-user-pw) => ok?)))))))
