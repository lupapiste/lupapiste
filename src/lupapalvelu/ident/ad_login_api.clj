(ns lupapalvelu.ident.ad-login-api
  "AD login endpoints for the default login flow. OAuth AD flow endpoints are part of the
  OAuth API"
  (:require [lupapalvelu.ident.ad-login :as ad-login]
            [lupapalvelu.ident.ad-login-util :as ad-util]
            [noir.core :refer [defpage]]
            [noir.request :as request]
            [noir.response :as resp]
            [ring.util.response :refer :all]
            [taoensso.timbre :as timbre]))

(defpage [:get "/api/saml/metadata/:domain"] {domain :domain}
  (let [{:keys [app-name sp-cert]} ad-util/ad-config]
    (resp/status 200
                 (resp/xml
                   (ad-util/metadata
                     app-name
                     (ad-util/make-acs-uri domain)
                     (ad-util/parse-certificate sp-cert)
                     true)))))

;; Send the SAML request to the IdP
(defpage [:get "/api/saml/ad-login/:domain"] {domain :domain}
  (let [{:keys [error] :as redirect} (ad-login/saml-request-redirect domain)]
    (if error
      (do
        (timbre/error "AD login SAML request failed:" error)
        ad-login/login-redirect)
      redirect)))

(defpage [:post "/api/saml/ad-login/:domain"] {domain :domain}
  (let [request (request/ring-request)]
    (ad-login/process-relay-state (assoc (ad-login/process-saml-response domain request)
                                         :domain domain
                                         :request request))))
