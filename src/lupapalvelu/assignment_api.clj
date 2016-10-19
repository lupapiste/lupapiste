(ns lupapalvelu.assignment-api
  (:require [lupapalvelu.action :refer [defcommand defquery non-blank-parameters vector-parameters]]
            [lupapalvelu.assignment :as assignment]
            [lupapalvelu.user :as usr]
            [sade.core :refer :all]))

(defquery assignments
  {:description "Return the entire collection"
   :user-roles #{:authority}}
  [_]
  (ok :assignments (assignment/get-assignments)))

(defcommand create-assignment
  {:description "Add an assignment"
   :user-roles #{:authority}
   :parameters [recipient target description]
   :input-validators [(partial non-blank-parameters [:recipient :description])
                      (partial vector-parameters [:target])]}
  [{user                      :user
    created                   :created
    {:keys [organization id]} :application}]
  (let [recipient-summary (-> {:username recipient} (usr/get-user) (usr/summary))
        creator-summary (-> user (usr/summary))]
    (ok :id (assignment/insert-assignment {:organizationId organization
                                           :applicationId  id
                                           :creator        creator-summary
                                           :recipient      recipient-summary
                                           :target         target
                                           :description    description}
                                          created))))

(defcommand complete-assignment
  {:description "Complete an assignment"
   :user-roles #{:authority}
   :parameters [assignmentId]
   :input-validators [(partial non-blank-parameters [:assignmentId])]}
  [{user    :user
    created :created}]
  (if (> (assignment/complete-assignment assignmentId user created) 0)
    (ok)
    (fail :error.assignment-not-completed)))
