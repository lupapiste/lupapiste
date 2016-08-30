(ns lupapalvelu.idf.idf-client
  "Identity federation client: create users to partner applications"
  (:require [taoensso.timbre :as timbre :refer [debug debugf info infof warn warnf error errorf]]
            [clojure.set :refer [rename-keys]]
            [clojure.string :refer [split-lines]]
            [sade.http :as http]
            [sade.strings :as ss]
            [sade.core :refer [now]]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.idf.idf-core :refer :all]))

(defn- ->params [user app]
  (-> user
    (rename-keys {:firstName :etunimi
                  :lastName  :sukunimi
                  :phone     :puhelin
                  :city      :postitoimipaikka
                  :street    :katuosoite
                  :zip       :postinumero
                  :allowDirectMarketing :suoramarkkinointilupa
                  :architect :ammattilainen})
    (select-keys [:id :etunimi :sukunimi :email :puhelin
                  :katuosoite :postinumero :postitoimipaikka
                  :suoramarkkinointilupa :ammattilainen])
    (assoc :app app)))

(defn send-user-data [user partner-name & opts]
  {:pre [(known-partner? partner-name)]}
  (let [app (send-app-for-partner partner-name)
        url (url-for-partner partner-name)
        params (->params user app)
        ts (now)
        form-params (assoc params :ts ts :mac (calculate-mac params partner-name ts :send))
        req-params {:form-params form-params, :follow-redirects false, :throw-exceptions false}
        opts (apply hash-map opts)
        _  (debugf "Send user %s / %s data to %s (%s)" (:id user) (:email user) partner-name url)
        resp (http/post url (merge opts req-params))
        body (:body resp)]
    (if (= 200 (:status resp))
      (let [id (-> (split-lines body) first ss/trim)]
        (link-account! (:email user) partner-name id ts false)
        true)
      (errorf "Unable link %s to %s: request: %s, status=%s, body=%s" (:email user) partner-name (logging/sanitize 1000 (str form-params)) (:status resp) (logging/sanitize 1000 body)))))
