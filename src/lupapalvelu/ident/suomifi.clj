(ns lupapalvelu.ident.suomifi
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error errorf fatal]]
            [noir.core :refer [defpage]]
            [noir.response :as response]
            [noir.request :as request]
            [sade.strings :as str]
            [lupapalvelu.ident.session :as ident-session]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer [$set]]
            [lupapalvelu.security :as security]
            [sade.util :as util]))

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

(defpage "/from-shib/login/:trid" {trid :trid}
  (let [sessionid (session-id)
        headers  (->> (request/ring-request)
                      :headers
                      (util/map-keys (comp keyword str/lower-case)))
        ident    (-> (select-keys headers (keys header-translations))
                     (clojure.set/rename-keys header-translations)
                     (assoc :stamp trid))]
    (info ident)
    (let [data (mongo/update-one-and-return :vetuma {:sessionid sessionid :trid trid} {$set {:user ident}})]
      (response/redirect (get-in data [:paths :success])))))

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
  (if-let [trid (re-find #"\d+$" relay-state)]
    (let [url (or (some-> (ident-session/get-user trid) (get-in [:paths :cancel])) "/")]
      (response/redirect url))))


