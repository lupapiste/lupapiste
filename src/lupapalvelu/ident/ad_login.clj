(ns lupapalvelu.ident.ad-login
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error errorf fatal]]
            [noir.core :refer [defpage]]
            [noir.response :as response]
            [noir.request :as request]
            [sade.strings :as str]
            [lupapalvelu.ident.session :as ident-session]
            [lupapalvelu.ident.ident-util :as ident-util]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer [$set]]
            [lupapalvelu.security :as security]
            [sade.util :as util]
            [sade.env :as env])
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

;; Nämä ehkä propertieseihin?
(defn config []
  {:app-name "http://app.example.com"
   :base-uri ""
   :idp-uri  "https://localhost:7000"
   :idp-cert "moi"
   ;:idp-cert "MIIDLDCCAhQCCQCs7L8tLvgUeDANBgkqhkiG9w0BAQsFADBYMQswCQYDVQQGEwJGSTEOMAwGA1UECAwFU2lwb28xDjAMBgNVBAcMBVNpcG9vMQ4wDAYDVQQKDAVTaXBvbzEZMBcGA1UEAwwQU2ltbyBTdXVydmlzaWlyaTAeFw0xODA2MjcwODE3MDRaFw0zODA2MjIwODE3MDRaMFgxCzAJBgNVBAYTAkZJMQ4wDAYDVQQIDAVTaXBvbzEOMAwGA1UEBwwFU2lwb28xDjAMBgNVBAoMBVNpcG9vMRkwFwYDVQQDDBBTaW1vIFN1dXJ2aXNpaXJpMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAplmJxb4a96fveEzqTOvuAWHXfVKkG9nyznJv5JsKtuI+uXbzQ0mdEr7TkRovFQBYTzPs4co+daLqgCA+tYv0p09obtaI8o8iXmGDVh7+q8Wg76yAocvpR5VnjoNwYHdYQnnCDcCaaPi2um0gvYmMGmhQ66kWtrmsEkTuJUEtNNM3W6sc7dl7LtrJ5TjMTtRVonxFyv0XamAJ2f6Y37cT17oas0g9ojCBNIHC0KTYBxXOTMn3x1PePKC4gEdDk0CsjJz+UHndwO2PqkYIrgh1COJYZckDEUU32y8Wc6e2k2YVJxn1GG0hNIbCMD4ogo+eI61johcw4ys+1fHk4NKvvwIDAQABMA0GCSqGSIb3DQEBCwUAA4IBAQBfV4071r36NE22CQ4q7GDfBwaUd9ofrf2N40sR4XzO3w9so13KZ2bhusM9/WI/L18bsPeUqZA1qsbEr2wRHdGA8Z1hCRSOZjWUNgvmNQYHX00yATlhzy8gvqCfgs55Dqj2Qo46ylacVLBVPH44p0WS0QPoYUKycZ7emIPVCQq8sEDpvt+B8tQHp3EF4VOrf3Nyb/BfRX0faJNX1wiHjq/neJzEsL7wAB4TgcKqcs/Hf0bHqGiWda57+BuAtiNU+7gIZfrlBBdT40199X5QZ8/OOiECw3NXgE6ZAdHquNOJvoHAIgDwIK2JIHqUfWjBsd4bG82OKIYCBwaJuRpwT4K8"
   ;:keystore-file "keystore.jks"
   ;:keystore-password "changeit"
   ;:key-alias "stelios"
   })

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
      ; (clojure.pprint/pprint (clojure.walk/keywordize-keys req) (clojure.java.io/writer "esimerkkipyyntö.edn"))
      (util/pspit req "esimerkkipyyntö2.edn")
      (println req))
    ; (mongo/update :vetuma {:sessionid sessionid :trid trid} {:sessionid sessionid :trid trid :paths paths :created-at (Date.)} :upsert true)
    (response/redirect (str "/from-ad/" trid))))

(defpage "/from-ad/:trid" {:keys [trid] :as params}
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
