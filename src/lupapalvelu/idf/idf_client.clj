(ns lupapalvelu.idf.idf-client
  "Identity federation client: create users to partner applications"
  (:require [taoensso.timbre :as timbre :refer [debug debugf info infof warn warnf error errorf]]
            [clojure.set :refer [rename-keys]]
            [clojure.string :refer [split-lines]]
            [sade.http :as http]
            [sade.strings :as ss]
            [lupapalvelu.core :refer [now]]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.idf.idf-core :refer :all]))

(defn send-user-data [user partner-name]
  {:pre [(known-partner? partner-name)]}
  (let [url (url-for-partner partner-name)
        params (-> user
                 (rename-keys {:firstName :etunimi
                               :lastName  :sukunimi
                               :phone     :puhelin
                               :city      :postinumero
                               :street    :katuosoite
                               :zip       :postitoimipaikka
                               :allowDirectMarketing :suoramarkkinointilupa
                               :architect :ammattilainen})
                 (select-keys [:id :etunimi :sukunimi :email :puhelin
                               :katuosoite :postinumero :postitoimipaikka
                               :suoramarkkinointilupa :ammattilainen])
                 (assoc :app "lupapiste"))
        ts (now)
        form-params (assoc params :ts ts :mac (calculate-mac params "lupapiste" ts))
        _  (debugf "Send user %s data to %s" (:email user) url)
        resp (http/post url {:form-params form-params, :follow-redirects false, :throw-exceptions false})
        body (:body resp)]
    (if (= 200 (:status resp))
      (let [id (-> (split-lines body) first ss/trim)]
        (link-account! (:email user) partner-name id))
      (errorf "Unable link %s to %s: status=%s, body=%s" (:email user) partner-name (:status resp) (logging/sanitize 1000 body)))))
