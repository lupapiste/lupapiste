(ns sade.http-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer [server-address]]
            [sade.http :as http]))

(facts "secure-headers"
  (let [request {:headers {"User-Agent" "sade.http-itest"
                           "Cookie" "greeting=hello"
                           "Access-Control-Allow-Headers" "Origin, X-Requested-With, Content-Type, Accept"
                           "Access-Control-Allow-Origin" "*"
                           "X-Frame-Options" "sameorigin"
                           "X-XSS-Protection" "1; mode=block"
                           "x-copyright" "Solita Oy"
                           "X-Contact" "info@example.com"
                           "X-UA-Compatible" "IE=edge,chrome=1"}}
        url (str (server-address) "/dev/header-echo")
        response (http/get url request)
        insecure (:headers response)
        secured (:headers (http/secure-headers response))
        filtered (dissoc secured "Accept-Encoding" "Content-Security-Policy" "Content-Security-Policy-Report-Only" "Date" "User-Agent")]

    (doseq [h (keys (:headers request))]
      (fact {:midje/description (str h " is echoed")} (contains? insecure h) => true))

    (doseq [h ["Date" "User-Agent"]]
      (fact {:midje/description (str h " is kept")} (contains? insecure h) => true))

    (doseq [h ["Server" "Access-Control-Allow-Headers" "Access-Control-Allow-Origin" "Set-Cookie"
               "X-Frame-Options" "X-XSS-Protection" "x-copyright" "X-Contact" "X-UA-Compatible"]]
      (fact {:midje/description (str h " is dropped")} (contains? secured h) => false))

    filtered => empty?))
