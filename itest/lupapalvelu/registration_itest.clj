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
                                            pena query command apply-remote-minimal ok?]]
            [lupapalvelu.vetuma :as vetuma]
            ))

(testable-privates lupapalvelu.vetuma mac-of)

(def vetuma-endpoint (str (server-address) "/api/vetuma"))

(apply-remote-minimal)

(defn register [base-opts user]
  (:body
    (decode-response
      (http/post
        (str (server-address) "/api/command/register-user")
        (util/deep-merge
             base-opts
             {
              ;:debug true
              :headers {"content-type" "application/json;charset=utf-8"}
              :body (json/encode user)})))))

(facts* "Registration"
 (let [store (atom {})
       params {:cookie-store (->cookie-store store)
               :follow-redirects false
               :throw-exceptions false}
       token-query (assoc params :query-params {:success "/app/fi/welcome#%23!/register2"
                                                :cancel "/app/fi/welcome#!/register/cancel"
                                                :error "/app/fi/welcome#!/register/error"})
       resp (http/get vetuma-endpoint token-query)
       body (:body resp) => (contains "***REMOVED***1")
       form (xml/parse body)
       ]

   (fact "Form contains standard error url" (xml/select1-attribute-value form [(e/attr= :id "ERRURL")] :value) => (contains "/api/vetuma/error"))
   (fact "Form contains standard cancel url" (xml/select1-attribute-value form [(e/attr= :id "CANURL")] :value) => (contains "/api/vetuma/cancel"))

  (:status resp) => 200

  (let [base-post (assoc
                    (zipmap vetuma/response-mac-keys (repeat ""))
                    :subjectdata "etunimi=Jukka, sukunimi=Palmu"
                    :vtjdata "<VTJHenkiloVastaussanoma/>")
        mac (mac-of (assoc base-post :key (:key (vetuma/config))) vetuma/response-mac-keys)
        vetuma-post (assoc base-post :mac mac)
        resp (http/post vetuma-endpoint (assoc params :form-params vetuma-post))]

    (fact "Vetuma redirect"
      (:status resp) => 302
      (get-in resp [:headers "location"]) => (contains (get-in token-query [:query-params :success]))))

  (let [cmd-opts {:cookies {"anti-csrf-token" {:value "123"}}
                  :headers {"x-anti-forgery-token" "123"}}
        resp (register cmd-opts {:phone "", :city "", :zip "", :street "", :password "salasana", :email "jukka@example.com", :stamp ""})]

    resp => ok?

    (println resp)
    )

    ;



  )
 )
