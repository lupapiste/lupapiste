(ns lupapalvelu.ssokeys-api
  (:require [lupapalvelu.ssokeys :as sso]
            [lupapalvelu.action :refer [defquery defcommand non-blank-parameters]]
            [sade.core :refer [ok]]))

(defcommand add-single-sign-on-key
  {:description "Adds SSO ip and key."
   :parameters [ip key comment]
   :user-roles #{:admin}
   :input-validators [(partial non-blank-parameters [:ip :key])
                      (comp sso/validate-ip :ip :data)
                      (comp sso/validate-key :key :data)]}
  [_]
  (->> (sso/create-sso-key ip key comment)
       (sso/update-to-db)
       (ok :id)))

(defcommand update-single-sign-on-key
  {:description "Updates SSO ip and comment."
   :parameters [sso-id ip key comment]
   :user-roles #{:admin}
   :input-validators [(partial non-blank-parameters [:sso-id :ip])
                      (comp sso/validate-id :sso-id :data)
                      (comp sso/validate-ip :ip :data)
                      (comp sso/validate-key :key :data)]}
  [_]
  (-> (sso/get-sso-key-by-id sso-id)
      (sso/update-sso-key ip key comment)
      (sso/update-to-db))
  (ok))

(defcommand remove-single-sign-on-key
  {:description "Remove SSO ip."
   :parameters [sso-id]
   :user-roles #{:admin}
   :input-validators [(comp sso/validate-id :sso-id :data)]}
  [_]
  (-> (sso/get-sso-key-by-id sso-id) :id sso/remove-from-db)
  (ok))

(defquery get-single-sign-on-keys
  {:description "Get full list of SSO ips"
   :user-roles #{:admin}}
  [_]
  (ok :ssoKeys (sso/get-all-sso-keys)))

