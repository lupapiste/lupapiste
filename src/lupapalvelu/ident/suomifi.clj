(ns lupapalvelu.ident.suomifi
  (:require [taoensso.timbre :refer [trace debug info warn error errorf fatal]]
            [noir.core :refer [defpage]]
            [noir.response :as response]
            [noir.request :as request]
            [sade.strings :as str]
            [lupapalvelu.ident.session :as ident-session]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer [$set]]
            [lupapalvelu.security :as security]
            [sade.util :as util]
            [sade.env :as env]))

(def session-initiator-by-lang
  {"fi" "/Shibboleth.sso/Login"
   "sv" "/Shibboleth.sso/LoginSV"
   "en" "/Shibboleth.sso/LoginEN"})

(def default-session-initiator "/Shibboleth.sso/Login")

(defn session-id [] (get-in (request/ring-request) [:session :id]))

(def header-translations
  {:suomifi-nationalidentificationnumber                      :userid
   :suomifi-cn                                                :fullName
   :suomifi-firstname                                         :firstName
   :suomifi-givenname                                         :givenName
   :suomifi-sn                                                :lastName
   :suomifi-mail                                              :email
   :suomifi-vakinainenkotimainenlahiosoites                   :street
   :suomifi-vakinainenkotimainenlahiosoitepostinumero         :zip
   :suomifi-vakinainenkotimainenlahiosoitepostitoimipaikkas   :city})

(defn select-headers [m]
  (select-keys m (keys header-translations)))

(def proxy-user-and-pass
  {"saml-user" (-> (env/get-config) :shibboleth :proxy-key)})

(defpage "/from-shib/login/:trid" {trid :trid}
  (let [sessionid (session-id)
        request (request/ring-request)
        headers (->> request
                     :headers
                     (util/map-keys (comp keyword str/lower-case))
                     select-headers
                     (util/map-values #(String. (.getBytes % "ISO-8859-1"))))
        proxy-key-matches (security/check-credentials-from-basic-auth request proxy-user-and-pass)
        ident (-> (select-keys headers (keys header-translations))
                  (clojure.set/rename-keys header-translations)
                  (assoc :stamp trid))]
    (if proxy-key-matches
      (let [data (mongo/update-one-and-return :vetuma {:sessionid sessionid :trid trid} {$set {:user ident}})]
        (info ident)
        (response/redirect (get-in data [:paths :success])))
      (do
        (error "Shibboleth forwarding proxy request authentication failed!")
        (response/status 403 "unauthorized")))))

(defpage [:get "/api/saml/login"] {:keys [success error cancel language]}
  (let [sessionid (session-id)
        trid      (security/random-password)
        paths     {:success success
                   :error   error
                   :cancel  cancel}
        login-path (get session-initiator-by-lang language default-session-initiator)]
    (mongo/update :vetuma {:sessionid sessionid :trid trid} {:sessionid sessionid :trid trid :paths paths :created-at (java.util.Date.)} :upsert true)
    (response/redirect (str login-path "?target=/saml/login/" trid))))

(defpage "/api/saml/error" {relay-state :RelayState status :statusCode status2 :statusCode2 message :statusMessage}
  (if (str/contains? status2 "AuthnFailed")
    (warn "SAML endpoint rejected authentication")
    (error "SAML endpoint encountered an error:" status status2 message))
  (try
    (if-let [trid (re-find #".*/([0-9A-Za-z]+)$" relay-state)]
      (let [url (or (some-> (ident-session/get-by-trid (last trid)) (get-in [:paths :cancel])) "/")]
        (response/redirect url))
      (response/redirect "/"))
    (catch Exception e
      (error "SAML error endpoint encountered an error:" e)
      (response/redirect "/"))))
