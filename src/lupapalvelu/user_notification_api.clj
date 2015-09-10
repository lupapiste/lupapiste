(ns lupapalvelu.user-notification-api
  (:require [lupapalvelu.action :refer [defcommand defquery]]
            [monger.operators :refer :all]
            [lupapalvelu.mongo :as mongo]
            [sade.core :refer [ok]]))

(defcommand notifications-update
  {:parameters [applicants authorities title-fi message-fi]
   :user-roles #{:admin}}
  [_]
  (let [to (->> []
                (#(if applicants
                   (conj % :applicant)
                   %))
                (#(if authorities
                   (conj % :authority)
                   %)))
        notification {:title title-fi
                      :message message-fi}]
    (let [n (mongo/update-by-query :users {:role {$in to}} {$set {:notification notification}})]
      (ok {:updates n}))))
