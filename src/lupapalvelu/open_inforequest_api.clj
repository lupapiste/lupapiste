(ns lupapalvelu.open-inforequest-api
  (:require [monger.operators :refer :all]
            [noir.session :as session]
            [noir.response :as resp]
            [sade.core :refer [fail!]]
            [sade.env :as env]
            [lupapalvelu.action :refer [defraw]]
            [lupapalvelu.mongo :as mongo]))

(defn- make-user [token]
  (let [organization-id (:organization-id token)
        email (:email token)]
    {:id (str "oir-" organization-id "-" email)
     :email email
     :enabled true
     :role "authority"
     :oir true
     :organizations [organization-id]
     :firstName ""
     :lastName email
     :phone ""
     :username email}))

(defraw openinforequest
  {:user-roles #{:anonymous}}
  [{{token-id :token-id} :data lang :lang created :created}]
  (assert created)
  (let [token (mongo/by-id :open-inforequest-token token-id)
        url   (str (env/value :host) "/app/" (name lang) "/oir#!/inforequest/" (:application-id token))]
    (when-not token (fail! :error.unknown-open-inforequest-token))
    (mongo/update-by-id :open-inforequest-token token-id {$set {:last-used created}})
    (session/put! :user (make-user token))
    (resp/redirect url)))
