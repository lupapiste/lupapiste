(ns lupapalvelu.ident.ad-login
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error errorf fatal]]
            [noir.core :refer [defpage]]
            [noir.response :as response]
            [noir.request :as request]
            [lupapalvelu.ident.session :as ident-session]
            [lupapalvelu.ident.ident-util :as ident-util]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]
            [monger.operators :refer [$set]]
            [ring.util.response :refer :all]
            [sade.env :as env]
            [sade.util :as util]
            [sade.strings :as ss]
            [saml20-clj.sp :as saml-sp]
            [saml20-clj.routes :as saml-routes]
            [saml20-clj.shared :as saml-shared])
  (:import (java.util Date)))

;; Headerit idp:stä
(def ad-header-translations
  {:ad-cn        :fullName
   :ad-firstname :firstName
   :ad-givenname :givenName
   :ad-sn        :lastName
   :ad-mail      :email
   :ad-address   :street
   :ad-zip       :zip
   :ad-city      :city
   :ad-role      :role
   :ad-org-authz :orgAuthz})

(defn- parse-certificate
  "Strip the ---BEGIN CERTIFICATE--- and ---END CERTIFICATE--- headers and newlines
  from certificate."
  [certstring]
  (->> (ss/split certstring #"\n") rest drop-last ss/join))

;; Nämä ehkä propertieseihin?
(def config
  {:app-name "Lupapiste"
   :base-uri "http://localhost:8000"
   :idp-uri  "https://localhost:7000" ;; The dockerized, locally running mock-saml instance (https://github.com/lupapiste/mock-saml)
   :idp-cert (parse-certificate (slurp "./idp-public-cert.pem")) ;; This needs to live in Mongo
   :keystore-file "./keystore"
   :keystore-password (System/getenv "KEYSTORE_PASS") ;; The default password from the dev guide, needs to be set in environment variable
   :key-alias "jetty"
   })

(def decrypter
  (saml-sp/make-saml-decrypter (:keystore-file config)
                               (:keystore-password config)
                               (:key-alias config)))

(def sp-cert
  "Service provider certificate, read from keystore file."
  (saml-shared/get-certificate-b64 (:keystore-file config)
                                   (:keystore-password config)
                                   (:key-alias config)))

(def mutables
  (assoc
    (saml-sp/generate-mutables)
    :xml-signer (saml-sp/make-saml-signer (:keystore-file config)
                                          (:keystore-password config)
                                          (:key-alias config)
                                          :algorithm :sha256)))


(defpage "/api/saml/ad-login" []
  (let [sessionid (ident-util/session-id)
        trid      (security/random-password)
        paths     {:success "/from-ad/:trid"
                   :error "/api/saml/ad/error"
                   :cancel "/"}
        login-path "http://localhost:7000"
        req (request/ring-request)]
    (do
      (println "Joo-o")
      (println sessionid)
      (println trid)
      (println "Ja pyyntö seuraa: ")
      ; (util/pspit req "esimerkkipyyntö.edn")
      (println req))
    ; (mongo/update :vetuma {:sessionid sessionid :trid trid} {:sessionid sessionid :trid trid :paths paths :created-at (Date.)} :upsert true)
    (response/redirect (str "/from-ad/" trid))))

(defpage [:post "/from-ad/:trid"] {:keys [trid] :as params}
  (let [valid? params
        _ (println params)
        _ (println trid)
        _ (println "Jeps, eli nyt ollaan from-ad -polussa")]
        (if valid?
          {:status  303 ;; See other
           :headers {"Location" "http://localhost:8000/app/fi/authority"}
           :body ""}
          {:status 500
           :body "The SAML response from IdP does not validate!"})))

(defpage "/api/saml/ad/error" {relay-state :RelayState status :statusCode status2 :statusCode2 message :statusMessage}
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
