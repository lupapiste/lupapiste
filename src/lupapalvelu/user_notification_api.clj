(ns lupapalvelu.user-notification-api
  (:require [lupapalvelu.action :refer [defcommand defquery]]
            [monger.operators :refer :all]
            [lupapalvelu.mongo :as mongo]
            [sade.core :refer [ok]]
            [clojure.set :refer [rename-keys]]))

(defcommand notifications-update
  {:parameters [applicants authorities title-fi message-fi]
   :user-roles #{:admin}}
  [command]
  (let [params (select-keys (:data command) [:applicants :authorities])
        roles  {:applicants :applicant
                :authorities :authority}
        params (rename-keys params roles)
        to     (keys (filter (fn [[_ v]] (true? v)) params))
        notification {:title title-fi
                      :message message-fi}
        n (mongo/update-by-query :users {:role {$in (vec to)}} {$set {:notification notification}})]
    (ok {:updates n})))
