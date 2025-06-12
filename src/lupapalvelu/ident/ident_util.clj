(ns lupapalvelu.ident.ident-util
  (:require [noir.request :as request]))

(def session-initiator-by-lang
  {"fi" "/Shibboleth.sso/Login"
   "sv" "/Shibboleth.sso/LoginSV"
   "en" "/Shibboleth.sso/LoginEN"})

(def default-session-initiator "/Shibboleth.sso/Login")

(defn session-id [] (get-in (request/ring-request) [:session :id]))

(def resolve-redirect "/api/saml/ad-login")
