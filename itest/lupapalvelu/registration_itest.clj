(ns lupapalvelu.registration-itest
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [net.cgrand.enlive-html :as e]
            [cheshire.core :as json]
            [sade.http :as http]
            [sade.util :as util]
            [sade.xml :as xml]
            [lupapalvelu.factlet :refer [fact* facts*]]
            [lupapalvelu.itest-util :refer [->cookie-store server-address decode-response
                                            admin query command
                                            last-email apply-remote-minimal
                                            ok? fail? http200? http302?
                                            ]]
            [lupapalvelu.vetuma :as vetuma]
            ))

(testable-privates lupapalvelu.vetuma mac-of)

(def vetuma-endpoint (str (server-address) "/api/vetuma"))

(apply-remote-minimal)

(defn- decode-body [resp] (:body (decode-response resp)))

(def token-query {:query-params {:success "/success"
                                 :cancel "/cancel"
                                 :error "/error"}})

(defn- vetuma-init [request-opts]
  (http/get vetuma-endpoint (merge request-opts token-query)))

(defn- vetuma-finish [request-opts]
  (let [base-post (assoc (zipmap vetuma/response-mac-keys (repeat "0"))
                    :trid "123456"
                    :subjectdata "etunimi=Jukka, sukunimi=Palmu"
                    :extradata "HETU=123456-7890"
                    :userid "123456-7890"
                    :vtjdata "<VTJHenkiloVastaussanoma/>")
         mac (mac-of (assoc base-post :key (:key (vetuma/config))) vetuma/response-mac-keys)
         vetuma-post (assoc base-post :mac mac)]
    (http/post vetuma-endpoint (assoc request-opts :form-params vetuma-post))))

(defn- register [base-opts user]
  (decode-body
    (http/post
      (str (server-address) "/api/command/register-user")
      (util/deep-merge
        base-opts
        {;:debug true
         :headers {"content-type" "application/json;charset=utf-8"}
         :body (json/encode user)}))))

(defn- login [u p]
  (decode-body
    (http/post (str (server-address) "/api/login")
      {:follow-redirects false
       :throw-exceptions false
       :form-params {:username u :password p}})))

(facts* "Registration"
 (let [store (atom {})
       params {:cookie-store (->cookie-store store)
               :follow-redirects false
               :throw-exceptions false}
       resp (vetuma-init params) => http200?
       body (:body resp) => (contains "***REMOVED***1")
       form (xml/parse body)]

   (fact "Form contains standard error url" (xml/select1-attribute-value form [(e/attr= :id "ERRURL")] :value) => (contains "/api/vetuma/error"))
   (fact "Form contains standard cancel url" (xml/select1-attribute-value form [(e/attr= :id "CANURL")] :value) => (contains "/api/vetuma/cancel"))

   (fact "Vetuma redirect"
     (let [resp (vetuma-finish params)  => http302?]
         (get-in resp [:headers "location"]) => (contains (get-in token-query [:query-params :success]))))

   (last-email) ; Inbox zero

   (let [vetuma-data (decode-body (http/get (str (server-address) "/api/vetuma/user")))
         stamp (:stamp vetuma-data) => string?
         person-id (:userid vetuma-data) => string?
         new-user-email "jukka@example.com"
         new-user-pw "salasana"
         new-user-phone "0500"
         cmd-opts {:cookies {"anti-csrf-token" {:value "123"}}
                   :headers {"x-anti-forgery-token" "123"}}
         resp (register cmd-opts {:stamp stamp :phone new-user-phone, :city "Tampere", :zip "0", :street "street", :password new-user-pw, :email new-user-email, :personId "inject!"})
         user-id (:id resp) => string?
         email (last-email)
         body (:body email)]

     (fact "Registration OK" resp => ok?)

     (facts "New user data"
       (let [new-user (first (:users (query admin :users :userId user-id)))]
        (fact "username" (:username new-user) => new-user-email)
        (fact "email" (:email new-user) => new-user-email)
        (fact "personId" (:personId new-user) => person-id)
        (fact "enabled" (:enabled new-user) => false)
        (fact "role" (:role new-user) => "applicant")
        (fact "organizations" (:organizations new-user) => empty?)
        (fact "phone" (:phone new-user) => new-user-phone)))

     (fact "New user got email"
       (:to email) => new-user-email
       (:subject email) => "Lupapiste.fi: K\u00e4ytt\u00e4j\u00e4tunnuksen aktivointi")

     (fact "Email has body"
       body => map?
       (fact "html" (:html body) => string?)
       (fact "plain text" (:plain body) => string?))

     (fact "Register again with the same email"
       (last-email) ; Inbox zero
       (swap! store (constantly {})) ; clear cookies
       (vetuma-init params)
       (vetuma-finish params)

       (let [stamp (:stamp (decode-body (http/get (str (server-address) "/api/vetuma/user")))) => string?
             new-user-email "jukka@example.com"
             new-user-pw "salasana"
             new-user-phone2 "046"
             cmd-opts {:cookies {"anti-csrf-token" {:value "123"}}
                   :headers {"x-anti-forgery-token" "123"}}
             resp (register cmd-opts {:stamp stamp :phone new-user-phone2, :city "Tampere", :zip "0", :street "street", :password new-user-pw, :email new-user-email, :personId "inject!"}) => ok?
             user-id (:id resp) => string?
             email (last-email)
             body (:body email)]

         (facts "New user data is overwritten"
           (let [new-user (first (:users (query admin :users :userId user-id)))]
             (fact "phone" (:phone new-user) => new-user-phone2)))

         (fact "New user got email"
           (:to email) => new-user-email
           (:subject email) => "Lupapiste.fi: K\u00e4ytt\u00e4j\u00e4tunnuksen aktivointi")

         (fact "Can NOT log in"
           (login new-user-email new-user-pw) => fail?)

         (let [mail-html (xml/parse (:html body))
               links (xml/select mail-html [:a])
               first-href (-> links first xml/attr :href)]

           (fact "First link contains activation token" first-href =>
             (contains "/app/security/activate/"))

           (fact "Activate account"
             (http/get first-href {:follow-redirects false}) => http302?)

           (fact "Log in"
             (login new-user-email new-user-pw) => ok?)))))))
