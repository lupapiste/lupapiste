(ns lupapalvelu.assignment-api
  (:require [lupapalvelu.action :as action :refer [defcommand defquery parameters-matching-schema]]
            [lupapalvelu.assignment :as assignment]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [sade.core :refer :all]
            [sade.schemas :as ssc]
            [sade.env :as env]))

;; Helpers and validators

(defn- userid->summary [id]
  (usr/summary (usr/get-user-by-id id)))

(defn- userid->session-summary [id]
  (usr/session-summary (usr/get-user-by-id id)))

(defn- validate-receiver [{{:keys [organization]} :application
                           {:keys [recipientId]}    :data}]
  (when (and recipientId
             (not (usr/user-is-authority-in-organization? (userid->session-summary recipientId)
                                                          organization)))
    (fail :error.invalid-assignment-receiver)))

(defn- validate-assignment-id [{{:keys [assignmentId]} :data}]
  (when-not (pos? (assignment/count-for-assignment-id assignmentId))
    (fail :error.invalid-assignment-id)))

(defn- assignments-enabled [{orgs :user-organizations}]
  (when (not-any? :assignments-enabled orgs)
    (fail :error.assignments-not-enabled)))

(defn- assignments-enabled-for-application [{org :organization}]
  (when-not (and org (:assignments-enabled @org))
    (fail :error.assignments-not-enabled)))


;;
;; Queries
;;

(defquery assignments-for-application
  {:description "Return the assignments for the current application"
   :parameters [id]
   :pre-checks [assignments-enabled-for-application]
   :states (conj states/all-application-states-but-draft-or-terminal :acknowledged) ;LPK-2519
   :user-roles #{:authority}
   :categories #{:documents}}
  [{user     :user}]
  (ok :assignments (assignment/get-assignments-for-application user id)))

(defquery assignment-targets
  {:description "Possible assignment targets per application for frontend"
   :parameters [id lang]
   :user-roles #{:authority}
   :pre-checks [assignments-enabled-for-application]
   :input-validators [(partial action/non-blank-parameters [:id :lang])]
   :states   (conj states/all-application-states-but-draft-or-terminal :acknowledged)}
  [{:keys [application]}]
  (ok :targets (assignment/assignment-targets application)))

(defquery assignments-search
  {:description "Service point for attachment search component"
   :parameters []
   :user-roles #{:authority}
   :pre-checks [assignments-enabled]}
  [{user :user data :data}]
  (let [query (assignment/search-query data)]
    (ok :data (assignment/assignments-search user query))))

(defquery assignment-count
  {:description ""
   :parameters []
   :user-roles #{:authority}
   :pre-checks [assignments-enabled]}
  [{user :user}]
  (ok :assignmentCount (assignment/count-active-assignments-for-user user)))

(env/in-dev                                                 ; These are only used in itest
  (defquery assignments
    {:description "Return all the assignments the user is allowed to see"
     :user-roles #{:authority}
     :pre-checks [assignments-enabled]}
    [{user :user}]
    (ok :assignments (assignment/get-assignments user)))

  (defquery assignment
    {:description "Return a single assignment"
     :user-roles #{:authority}
     :parameters [assignmentId]
     :pre-checks [assignments-enabled]
     :input-validators [(partial action/parameters-matching-schema [:assignmentId] ssc/ObjectIdStr)]}
    [{user :user}]
    (ok :assignment (assignment/get-assignment user assignmentId))))

;;
;; Commands
;;

(defcommand create-assignment
  {:description      "Create an assignment"
   :user-roles       #{:authority}
   :parameters       [id recipientId target description]
   :input-validators [(partial action/non-blank-parameters [:recipientId :description])
                      (partial action/map-parameters [:target])]
   :pre-checks       [validate-receiver
                      assignments-enabled-for-application]
   :states           states/all-application-states-but-draft-or-terminal}
  [{user         :user
    created      :created
    application  :application}]
  (ok :id (assignment/insert-assignment {:application    (select-keys application
                                                                      [:id :organization :address :municipality])
                                         :state          (assignment/new-state "created" (usr/summary user) created)
                                         :recipient      (userid->summary recipientId)
                                         :target         target
                                         :description    description})))

(defcommand update-assignment
  {:description      "Updates an assignment"
   :user-roles       #{:authority}
   :parameters       [id assignmentId recipientId description]
   :input-validators [(partial action/non-blank-parameters [:recipientId :description])
                      (partial action/parameters-matching-schema [:assignmentId] ssc/ObjectIdStr)]
   :pre-checks       [validate-receiver
                      validate-assignment-id]
   :states           states/all-application-states-but-draft-or-terminal}
  [_]
  (ok :id (assignment/update-assignment assignmentId {:recipient   (userid->summary recipientId)
                                                      :description description})))

(defcommand complete-assignment
  {:description "Complete an assignment"
   :user-roles #{:authority}
   :parameters [assignmentId]
   :pre-checks [assignments-enabled]
   :input-validators [(partial action/non-blank-parameters [:assignmentId])]
   :categories #{:documents}}
  [{user    :user
    created :created}]
  (if (> (assignment/complete-assignment assignmentId user created) 0)
    (ok)
    (fail :error.assignment-not-completed)))
