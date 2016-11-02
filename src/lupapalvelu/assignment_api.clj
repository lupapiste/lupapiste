(ns lupapalvelu.assignment-api
  (:require [lupapalvelu.action :as action :refer [defcommand defquery parameters-matching-schema]]
            [lupapalvelu.assignment :as assignment]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [sade.core :refer :all]
            [sade.schemas :as ssc]))

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

(defn- assignments-enabled [{user :user}]
  (when (->> (usr/get-organizations user)
             (not-any? :assignments-enabled))
    (fail :error.assignments-not-enabled)))

(defn- assignments-enabled-for-application [{org :organization}]
  (when-not (:assignments-enabled @org)
    (fail :error.assignments-not-enabled)))

;;
;; Queries
;;

(defquery assignments
  {:description "Return all the assignments the user is allowed to see"
   :user-roles #{:authority}
   :pre-checks [assignments-enabled]
   :feature :assignments}
  [{user :user}]
  (ok :assignments (assignment/get-assignments user)))

(defquery assignments-for-application
  {:description "Return the assignments for the current application"
   :parameters [id]
   :pre-checks [assignments-enabled-for-application]
   :states states/all-application-states-but-draft-or-terminal
   :user-roles #{:authority}
   :categories #{:documents}
   :feature :assignments}
  [{user     :user}]
  (ok :assignments (assignment/get-assignments-for-application user id)))

(defquery assignment
  {:description "Return a single assignment"
   :user-roles #{:authority}
   :parameters [assignmentId]
   :pre-checks [assignments-enabled]
   :input-validators [(partial action/parameters-matching-schema [:assignmentId] ssc/ObjectIdStr)]
   :feature :assignments}
  [{user :user}]
  (ok :assignment (assignment/get-assignment user assignmentId)))

(defquery assignment-targets
  {:description "Possible assignment targets per application for frontend"
   :parameters [id lang]
   :user-roles #{:authority}
   :pre-checks [assignments-enabled-for-application]
   :input-validators [(partial action/non-blank-parameters [:id :lang])]
   :states   states/all-application-states-but-draft-or-terminal
   :feature :assignments}
  [{:keys [application]}]
  (let [party-docs (domain/get-documents-by-type application :party)
        parties    (for [doc party-docs]
                     {:id (:id doc)
                      :displayText (assignment/display-text-for-document doc lang)})]
    (ok :targets [["parties" parties]])))

(defquery assignments-search
  {:description "Service point for attachment search component"
   :parameters []
   :user-roles #{:authority}
   :pre-checks [assignments-enabled]
   :feature :assignments}
  [{user :user data :data}]
  (ok :data (assignment/assignments-search user (assignment/search-query data))))

;;
;; Commands
;;

(defcommand create-assignment
  {:description      "Create an assignment"
   :user-roles       #{:authority}
   :parameters       [recipientId target description]
   :input-validators [(partial action/non-blank-parameters [:recipientId :description])
                      (partial action/vector-parameters [:target])]
   :pre-checks       [validate-receiver
                      assignments-enabled-for-application]
   :states           states/all-application-states-but-draft-or-terminal
   :feature          :assignments}
  [{user         :user
    created      :created
    application  :application}]
  (ok :id (assignment/insert-assignment {:application    (select-keys application
                                                                      [:id :organization :address :municipality])
                                         :state          (assignment/new-state "created" (usr/summary user) created)
                                         :recipient      (userid->summary recipientId)
                                         :target         target
                                         :description    description})))

(defcommand complete-assignment
  {:description "Complete an assignment"
   :user-roles #{:authority}
   :parameters [assignmentId]
   :pre-checks [assignments-enabled]
   :input-validators [(partial action/non-blank-parameters [:assignmentId])]
   :categories #{:documents}
   :feature :assignments}
  [{user    :user
    created :created}]
  (if (> (assignment/complete-assignment assignmentId user created) 0)
    (ok)
    (fail :error.assignment-not-completed)))
