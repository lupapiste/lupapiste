(ns lupapalvelu.ssokeys-api
  (:require [lupapalvelu.ssokeys :as sso]
            [lupapalvelu.action :refer [defquery defcommand]]
            [sade.core :refer [ok]]))

(defcommand add-single-sign-on-key
  {:description "Adds SSO ip and key."
   :parameters [ip key comment]
   :user-roles #{:admin}
   :input-validators [#(sso/validate-ip  (get-in % [:data :ip]))
                      #(sso/validate-key (get-in % [:data :key]))]}
  [_]
  (->> (sso/create-sso-key ip key comment)
       (sso/update-to-db)
       (ok :id)))

(defcommand update-single-sign-on-key
  {:description "Updates SSO ip and comment."
   :parameters [sso-id ip comment]
   :user-roles #{:admin}
   :input-validators [#(sso/validate-ip (get-in % [:data :ip]))
                      #(sso/validate-id (get-in % [:data :sso-id]))]}
  [_]
  (-> (sso/get-sso-key-by-id sso-id)
      (sso/update-sso-key ip comment)
      (sso/update-to-db))
  (ok))

(defcommand remove-single-sign-on-key
  {:description "Remove SSO ip."
   :parameters [sso-id]
   :user-roles #{:admin}
   :input-validators [#(sso/validate-id (get-in % [:data :sso-id]))]}
  [_]
  (-> (sso/get-sso-key-by-id sso-id) :id sso/remove-from-db sso-id)
  (ok))

(defquery get-single-sign-on-keys
  {:description "Get full list of SSO ips"
   :user-roles #{:admin}}
  [_]
  (ok :ssoKeys (sso/get-all-sso-keys)))

