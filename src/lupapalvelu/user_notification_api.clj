(ns lupapalvelu.user-notification-api
  (:require [lupapalvelu.action :refer [defcommand defquery]]
            [lupapalvelu.mongo :as mongo]
            [sade.core :refer [ok]]))

(defcommand notifications-update
  {:parameters [applicants authorities message]
   :user-roles #{:admin}}
  [_]
  (prn "foo" applicants authorities message))
