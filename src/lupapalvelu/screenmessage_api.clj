(ns lupapalvelu.screenmessage-api
  (:require [taoensso.timbre :refer [trace debug debugf info warn error errorf fatal]]
            [lupapalvelu.action :refer [defcommand defquery] :as action]
            [lupapalvelu.mongo :as mongo]
            [sade.core :refer [ok]]))


(defquery screenmessages
  {:user-roles #{:anonymous}}
  [_]
  (ok :screenmessages (mongo/select :screenmessages)))

(defcommand screenmessages-add
  {:parameters [fi sv]
   :input-validators [(partial action/non-blank-parameters [:fi])
                      (partial action/string-parameters [:sv])]
   :user-roles #{:admin}}
  [{created :created}]
  (mongo/insert :screenmessages {:id (mongo/create-id)
                                 :added created
                                 :fi fi
                                 :sv (if-not (empty? sv) sv fi)}))

(defcommand screenmessages-reset
  {:user-roles #{:admin}}
  [_]
  (mongo/drop-collection :screenmessages))
