(ns lupapalvelu.ident.suomifi
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error errorf fatal]]
            [noir.core :refer [defpage]]
            [noir.response :as response]
            [noir.request :as request]
            [sade.strings :as str]
            [sade.util :as util]
            [hiccup.form :as form]
            [hiccup.core :as core]
            [sade.env :as env]
            [lupapalvelu.ident.session :as ident-session]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer [$set]]
            [lupapalvelu.security :as security]))

(defn session-id [] (get-in (request/ring-request) [:session :id]))

(def header-translations
  {:suomifi-nationalidentificationnumber                      :personId
   :suomifi-cn                                                :fullName
   :suomifi-firstname                                         :firstName
   :suomifi-givenname                                         :givenName
   :suomifi-sn                                                :lastName
   :suomifi-mail                                              :email
   :suomifi-vakinainenkotimainenlahiosoites                   :street
   :suomifi-vakinainenkotimainenlahiosoitepostinumero         :zip
   :suomifi-vakinainenkotimainenLahiosoitepostitoimipaikkas   :city})

(defpage "/from-shib/login/:trid" {trid :trid}
  (let [headers  (->> (request/ring-request)
                      :headers
                      (comp keyword str/lower-case))
        ident    (-> (select-keys headers (keys header-translations))
                     (clojure.set/rename-keys header-translations))]
    (info ident)
    (mongo/update :vetuma {:sessionid (session-id) :trid trid} {$set {:user ident}})
    (response/json {:trid trid
                    :user (ident-session/get-user trid)
                    :ident ident})))

(defpage [:get "/api/saml/login"] {:keys [success error cancel]}
  (let [sessionid (session-id)
        trid      (security/random-password)
        paths     {:success success
                   :error   error
                   :cancel  cancel}]
    (mongo/update :vetuma {:sessionid sessionid :trid trid} {:sessionid sessionid :trid trid :paths paths :created-at (java.util.Date.)} :upsert true)
    (response/redirect (str "/saml/login/" trid))))

(defpage "/api/saml/error" {relay-state :RelayState status :statusCode status2 :statusCode2 message :statusMessage}
  (if (str/contains? status2 "AuthnFailed")
    (warn "SAML endpoint rejected authentication")
    (error "SAML endpoint encountered an error:" status status2 message))
  (if-let [trid (re-find #"\d+$" relay-state)]
    (let [url (or (some-> (ident-session/get-user trid) (get-in [:paths :cancel])) "/")]
      (response/redirect url))))


