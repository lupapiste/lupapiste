(ns lupapalvelu.tasks-api
  (:require [taoensso.timbre :as timbre :refer [trace debug info infof warn warnf error fatal]]
            [lupapalvelu.core :refer :all]
            [lupapalvelu.action :refer [defquery defcommand defraw non-blank-parameters update-application]]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]))

(defcommand delete-task
  {:parameters [id taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :roles [:authority]}
  [{created :created :as command}]
  (update-application command
    {$pull {:tasks {:id taskId}}
     $set  {:modified  created}}))