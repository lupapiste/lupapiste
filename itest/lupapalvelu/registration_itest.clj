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
                                            ;pena query command
                                            last-email apply-remote-minimal
                                            ok? fail? http200?
                                            ]]
            [lupapalvelu.vetuma :as vetuma]
            ))

(testable-privates lupapalvelu.vetuma mac-of)

(def vetuma-endpoint (str (server-address) "/api/vetuma"))

(apply-remote-minimal)

(defn decode-body [resp] (:body (decode-response resp)))

(defn register [base-opts user]
  (decode-body
    (http/post
      (str (server-address) "/api/command/register-user")
      (util/deep-merge
           base-opts
           {
            ;:debug true
            :headers {"content-type" "application/json;charset=utf-8"}
            :body (json/encode user)}))))

(defn login [u p]
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
       token-query (assoc params :query-params {:success "/app/fi/welcome#%23!/register2"
                                                :cancel "/app/fi/welcome#!/register/cancel"
                                                :error "/app/fi/welcome#!/register/error"})
       resp (http/get vetuma-endpoint token-query) => http200?
       body (:body resp) => (contains "***REMOVED***1")
       form (xml/parse body)]

   (fact "Form contains standard error url" (xml/select1-attribute-value form [(e/attr= :id "ERRURL")] :value) => (contains "/api/vetuma/error"))
   (fact "Form contains standard cancel url" (xml/select1-attribute-value form [(e/attr= :id "CANURL")] :value) => (contains "/api/vetuma/cancel"))

   (let [base-post (assoc (zipmap vetuma/response-mac-keys (repeat ""))
                     :subjectdata "etunimi=Jukka, sukunimi=Palmu"
                     :vtjdata "<VTJHenkiloVastaussanoma/>")
         mac (mac-of (assoc base-post :key (:key (vetuma/config))) vetuma/response-mac-keys)
         vetuma-post (assoc base-post :mac mac)
         resp (http/post vetuma-endpoint (assoc params :form-params vetuma-post))]

     (fact "Vetuma redirect"
       (:status resp) => 302
       (get-in resp [:headers "location"]) => (contains (get-in token-query [:query-params :success]))))

   (last-email) ; Inbox zero

   (let [new-user-email "jukka@example.com"
         new-user-pw "salasana"
         cmd-opts {:cookies {"anti-csrf-token" {:value "123"}}
                   :headers {"x-anti-forgery-token" "123"}}
         resp (register cmd-opts {:phone "", :city "", :zip "", :street "", :password new-user-pw, :email new-user-email, :stamp ""})
         email (last-email)
         body (:body email)]

     (fact "Registration OK" resp => ok?)

     (fact "Got email"
       (:to email) => new-user-email
       (:subject email) => "Lupapiste.fi: K\u00e4ytt\u00e4j\u00e4tunnuksen aktivointi")

     (fact "Email has body"
       body => map?
       (fact "html" (:html body) => string?)
       (fact "plain text" (:plain body) => string?))

     (let [mail-html (xml/parse (:html body))
           links (xml/select mail-html [:a])
           first-href (-> links first xml/attr :href)]

       (fact "First link contains activation token" first-href =>
         (contains "/app/security/activate/"))

       (fact "Can NOT log in"
         (login new-user-email new-user-pw) => fail?)

       (fact "Activate account"
         (http/get first-href) => http200?)

       (fact "Log in"
         (login new-user-email new-user-pw) => ok?)

       ;(println (:plain body))
       )



     )
  )
 )
