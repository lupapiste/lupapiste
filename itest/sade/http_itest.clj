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
        filtered (dissoc secured "Accept-Encoding" "Content-Security-Policy"
                   "Content-Security-Policy-Report-Only" "Date" "User-Agent"
                   "Strict-Transport-Security" "Transfer-Encoding")]

    (doseq [h (keys (:headers request))]
      (fact {:midje/description (str h " is echoed")} (contains? insecure h) => true))

    (doseq [h ["Date" "User-Agent"]]
      (fact {:midje/description (str h " is kept")} (contains? insecure h) => true))

    (doseq [h ["Server" "Access-Control-Allow-Headers" "Access-Control-Allow-Origin" "Set-Cookie"
               "X-Frame-Options" "X-XSS-Protection" "x-copyright" "X-Contact" "X-UA-Compatible"]]
      (fact {:midje/description (str h " is dropped")} (contains? secured h) => false))

    filtered => empty?))

(facts "status 404"
  (let [url (str (server-address) "/dev/404")]
    (fact "by default an exception is thrown"
      (http/get url) => (throws clojure.lang.ExceptionInfo))
    (fact "404 response is returned"
      (http/get url {:throw-exceptions false}) => (contains {:status 404}))
    (fact "fail! is thrown"
      (http/get url {:throw-exceptions false, :throw-fail! true}) => (throws clojure.lang.ExceptionInfo))))

(facts "connection error"
  ; Port 9 is designated to the Discard Protocol.
  ; Connection should fail because the port is blocked or there is no service listenign,
  ; or timeout occurs because TCP packages are being discarded.
  (let [url "http://localhost:9/"]
    (fact "by default an exception is thrown"
      (http/get url) => (throws java.io.IOException))
    (fact "proxy error response is returned"
      (http/get url {:throw-exceptions false}) => (contains {:status 502}))
    (fact "fail! is thrown"
      (http/get url {:throw-exceptions false, :throw-fail! true}) => (throws clojure.lang.ExceptionInfo))))
