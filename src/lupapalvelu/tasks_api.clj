(ns lupapalvelu.tasks-api
  (:require [taoensso.timbre :as timbre :refer [trace debug info infof warn warnf error fatal]]
            [lupapalvelu.core :refer :all]
            [lupapalvelu.action :refer [defquery defcommand defraw non-blank-parameters update-application]]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]))

(defcommand delete-task
  {:parameters [id taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   ; TODO :validators [task-state...]]
   :roles [:authority]}
  [{created :created :as command}]
  ; TODO otetaan huomioon taskin tila: jos siirretty, ei saa poistaa
  (update-application command
    {$pull {:tasks {:id taskId}}
     $set  {:modified  created}}))