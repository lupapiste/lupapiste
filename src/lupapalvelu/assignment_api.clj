(ns lupapalvelu.assignment-api
  (:require [lupapalvelu.action :refer [defcommand defquery non-blank-parameters vector-parameters]]
            [lupapalvelu.assignment :as assignment]
            [lupapalvelu.user :as usr]
            [sade.core :refer :all]))

(defquery assignments
  {:description "Return all the assignments the user is allowed to see"
   :user-roles #{:authority}}
  [{user :user}]
  (ok :assignments (assignment/get-assignments user)))

(defquery assignments-for-application
  {:description "Return the assignments for the current application"
   :user-roles #{:authority}}
  [{user     :user
    {id :id} :application}]
  (ok :assignments (assignment/get-assignments-for-application user id)))

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
