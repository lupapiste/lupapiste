(ns lupapalvelu.idf.idf-client
  "Identity federation client: create users to partner applications"
  (:require [taoensso.timbre :as timbre :refer [debug debugf info infof warn warnf error errorf]]
            [clojure.set :refer [rename-keys]]
            [sade.http :as http]
            [lupapalvelu.core :refer [now]]
            [lupapalvelu.user :as user]
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
        form-params (assoc params :ts ts :mac (calculate-mac query-params "lupapiste" ts))
        _  (debugf "Send user %s data to %s" (:email user) url)
        resp (http/post url {:form-params form-params, :follow-redirects false, :throw-exceptions false})]
    (if (= 200 (:status resp))
      (do
        )
      (errorf "Unable link %s to %s: %s" (:email user) partner-name)
      )


    )
  )