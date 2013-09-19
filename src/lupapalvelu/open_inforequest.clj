(ns lupapalvelu.open-inforequest
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as organization]))

(defn new-open-inforequest! [application]
  (println "***: new open inforequest:" (:id application))
  (println "***: send email:" (:open-inforequest-email (organization/get-organization (:organization application)))))
