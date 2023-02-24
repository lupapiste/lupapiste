(ns lupapalvelu.ident.suomifi
  (:require [lupapalvelu.ident.ident-util
             :refer [session-initiator-by-lang default-session-initiator session-id]
             :as ident-util]
            [lupapalvelu.ident.session :as ident-session]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.oauth.schemas :refer [RootPath]]
            [lupapalvelu.security :as security]
            [monger.operators :refer [$set]]
            [noir.core :refer [defpage]]
            [noir.request :as request]
            [noir.response :as response]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [taoensso.timbre :refer [info warn error] :as timbre]))

(def header-translations
  {:suomifi-nationalidentificationnumber                      :userid
   :suomifi-cn                                                :fullName
   :suomifi-firstname                                         :firstName
   :suomifi-givenname                                         :givenName
   :suomifi-sn                                                :lastName
   :suomifi-mail                                              :email
   :suomifi-vakinainenkotimainenlahiosoites                   :street
   :suomifi-vakinainenkotimainenlahiosoitepostinumero         :zip
   :suomifi-vakinainenkotimainenlahiosoitepostitoimipaikkas   :city
   :eidas-personidentifier                                    :eidasId
   :eidas-firstname                                           :eidasFirstName
   :eidas-familyname                                          :eidasFamilyName
   :eidas-birthdate                                           :eidasBirthDate})

(defn select-headers [m]
  (select-keys m (keys header-translations)))

(def proxy-user-and-pass
  {"saml-user" (-> (env/get-config) :shibboleth :proxy-key)})

(defpage "/from-shib/login/:trid" {trid :trid}
  (let [sessionid (session-id)
        request (request/ring-request)
        headers (->> request
                     :headers
                     (util/map-keys (comp keyword ss/lower-case))
                     select-headers
                     (util/map-values #(String. (.getBytes ^String % "ISO-8859-1"))))
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

(sc/defschema Paths
  {:success                  RootPath
   :error                    RootPath
   (sc/optional-key :cancel) RootPath})

(defpage [:get "/api/saml/login"] {:keys [success error cancel language] :as params}
  (try
    (let [paths      (->> {:success success
                           :error   error
                           :cancel  cancel}
                          ss/trimwalk
                          util/strip-blanks
                          (sc/validate Paths))
          sessionid  (session-id)
          trid       (security/random-password)
          login-path (get session-initiator-by-lang language default-session-initiator)]
      (mongo/update :vetuma
                    {:sessionid sessionid :trid trid}
                    {:sessionid sessionid :trid       trid
                     :paths     paths     :created-at (java.util.Date.)}
                    :upsert true)
      (response/redirect (str login-path "?target=/saml/login/" trid)))
    (catch Exception e
      (timbre/error "Bad login paths." (ex-message e))
      (response/status 400 "Bad request"))))

(defpage "/api/saml/error" {relay-state :RelayState status :statusCode status2 :statusCode2 message :statusMessage}
  (if (ss/contains? status2 "AuthnFailed")
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
