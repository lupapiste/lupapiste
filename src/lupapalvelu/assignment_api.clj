(ns lupapalvelu.assignment-api
  (:require [lupapalvelu.action :refer [defcommand defquery non-blank-parameters vector-parameters parameters-matching-schema]]
            [lupapalvelu.assignment :as assignment]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [sade.core :refer :all]
            [sade.schemas :as ssc]))

;; Helpers and validators

(defn- username->summary [username]
  (-> {:username username} (usr/get-user) (usr/summary)))

(defn- username->session-summary [username]
  (-> {:username username} (usr/get-user) (usr/session-summary)))

(defn- validate-receiver [{{:keys [organization]} :application
                           {:keys [recipient]}    :data}]
  (when (and recipient
             (not (usr/user-is-authority-in-organization? (username->session-summary recipient)
                                                          organization)))
    (fail :error.invalid-assignment-receiver)))


;;
;; Queries
;;

(defquery assignments
  {:description "Return all the assignments the user is allowed to see"
   :user-roles #{:authority}
   :feature :assignments}
  [{user :user}]
  (ok :assignments (assignment/get-assignments user)))

(defquery assignments-for-application
  {:description "Return the assignments for the current application"
   :user-roles #{:authority}
   :feature :assignments}
  [{user     :user
    {id :id} :application}]
  (ok :assignments (assignment/get-assignments-for-application user id)))

(defquery assignment
  {:description "Return a single assignment"
   :user-roles #{:authority}
   :parameters [assignmentId]
   :input-validators [(partial parameters-matching-schema [:assignmentId] ssc/ObjectIdStr)]
   :feature :assignments}
  [{user :user}]
  (ok :assignment (assignment/get-assignment user assignmentId)))

(defquery assignments-search
  {:description "Service point for attachment search component"
   :parameters []
   :user-roles #{:authority}
   :feature :assignments}
  [{user :user data :data}]
  (ok :data (assignment/assignments-search user (assignment/search-query data))))
;;
;; Commands
;;

(defcommand create-assignment
  {:description      "Create an assignment"
   :user-roles       #{:authority}
   :parameters       [recipient target description]
   :input-validators [(partial non-blank-parameters [:recipient :description])
                      (partial vector-parameters [:target])]
   :pre-checks       [validate-receiver]
   :states           states/all-application-states-but-draft-or-terminal
   :feature          :assignments}
  [{user         :user
    created      :created
    application  :application}]
  (ok :id (assignment/insert-assignment {:application    (select-keys application
                                                                      [:id :organization :address :municipality])
                                         :creator        (usr/summary user)
                                         :recipient      (username->summary recipient)
                                         :target         target
                                         :description    description}
                                        created)))

(defcommand complete-assignment
  {:description "Complete an assignment"
   :user-roles #{:authority}
   :parameters [assignmentId]
   :input-validators [(partial non-blank-parameters [:assignmentId])]
   :feature :assignments}
  [{user    :user
    created :created}]
  (if (> (assignment/complete-assignment assignmentId user created) 0)
    (ok)
    (fail :error.assignment-not-completed)))
