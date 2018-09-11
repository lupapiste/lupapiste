(ns lupapalvelu.ident.ident-util
  (:require [clj-http.client :as http]
            [noir.request :as request]))

(def session-initiator-by-lang
  {"fi" "/Shibboleth.sso/Login"
   "sv" "/Shibboleth.sso/LoginSV"
   "en" "/Shibboleth.sso/LoginEN"})

(def default-session-initiator "/Shibboleth.sso/Login")

(defn session-id [] (get-in (request/ring-request) [:session :id]))

(defn resolve-redirect [command]
  (let [; jos sessio niin sis\u00e4\u00e4n vaan jos ei sessiota niin k\u00e4yd\u00e4\u00e4n kirjautumassa
       ]
    "/api/saml/ad-login"))
