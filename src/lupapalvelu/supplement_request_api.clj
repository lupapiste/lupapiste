(ns lupapalvelu.supplement-request-api
  (:require [lupapalvelu.action :refer [defcommand defquery update-application] :as action]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.quill :as quill]
            [lupapalvelu.supplement-request :as supplement-request]
            [monger.operators :refer :all]
            [sade.core :refer [fail ok]]
            [sade.util :as util]))

(def valid-application-states #{:submitted :sent :complementNeeded})

(defn- supplement-request-in-state [& allowed-states]
  (fn [{{:keys [request-id]}          :data
        {:keys [supplement-requests]} :application}]
    (when-let [{:keys [state]} (some-> request-id (util/find-by-id supplement-requests))]
      (when-not (util/includes-as-kw? allowed-states state)
        (fail :error.illegal-state)))))

(defn- mongo-query [request-id]
  {:supplement-requests {$elemMatch {:id request-id}}})

(defn- mongo-set [request]
  {$set {:supplement-requests.$ request}})

(defn- inflate-text [request]
  (update request :text quill/inflate))

(defn- deflate-text [request]
  (update request :text quill/deflate))

(defn- persist-supplement-request [command request]
  (update-application command
                      (mongo-query (:id request))
                      (mongo-set (deflate-text request))))

(defn- get-supplement-request [request-id application]
  (-> (util/find-by-id request-id (:supplement-requests application))
      (update :state keyword)
      inflate-text))

(defn- supplement-request-reminder-email-model [command _ recipient]
  (notifications/create-app-model command {:tab "process"} recipient))

(notifications/defemail :new-supplement-request
                        {:subject-key   "new-supplement-request"
                         :recipients-fn notifications/application-state-reminder-recipients-fn
                         :model-fn      supplement-request-reminder-email-model})

(defquery get-supplement-requests
  {:parameters       [:id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :permissions      [{:required [:application/read]}]
   :user-roles       #{:authority :applicant}
   :pre-checks       [permit/is-ymp-permit-type]}
  [{:keys [application user]}]
  (ok :supplement-requests (->> application
                                :supplement-requests
                                (filter #(or (auth/application-authority? application user)
                                             (-> % :state keyword (not= :draft))))
                                (mapv inflate-text))))

(defcommand create-supplement-request
  {:description      "Create draft request visible only to authority."
   :parameters       [id due-date text]
   :input-validators [(partial action/non-blank-parameters [:id])
                      (partial action/timestamp-parameters [:due-date])
                      (quill/input-validator :text)]
   :states           valid-application-states
   :user-roles       #{:authority}
   :pre-checks       [permit/is-ymp-permit-type]}
  [{{:keys [due-date text]} :data user :user created :created :as command}]
  (let [request (supplement-request/create-supplement-request created user
                                                              due-date
                                                              (quill/coerce-operations! text))]
    (update-application command {$push {:supplement-requests (deflate-text request)}})
    (ok :request-id (:id request))))

(defcommand update-supplement-request
  {:description      "Update request content."
   :parameters       [id request-id due-date text]
   :input-validators [(partial action/non-blank-parameters [:id :request-id])
                      (partial action/timestamp-parameters [:due-date])
                      (quill/input-validator :text)]
   :states           valid-application-states
   :user-roles       #{:authority}
   :pre-checks       [permit/is-ymp-permit-type
                      (supplement-request-in-state :draft)]}
  [{{:keys [due-date text]} :data application :application created :created user :user :as command}]
  (let [request (-> (get-supplement-request request-id application)
                    (supplement-request/update-supplement-request created user
                                                                  due-date
                                                                  (quill/coerce-operations! text)))]
    (persist-supplement-request command request)
    (ok)))

(defcommand delete-supplement-request
  {:description      "Delete request."
   :parameters       [id request-id]
   :input-validators [(partial action/non-blank-parameters [:id :request-id])]
   :states           valid-application-states
   :user-roles       #{:authority}
   :pre-checks       [permit/is-ymp-permit-type
                      (supplement-request-in-state :draft)]}
  [command]
  (update-application command {$pull {:supplement-requests {:id request-id}}})
  (ok))

(defcommand send-supplement-request
  {:description      "Send supplement request to applicant. Notify all writer parties."
   :parameters       [id request-id]
   :input-validators [(partial action/non-blank-parameters [:id :request-id])]
   :states           valid-application-states
   :notified         true
   :on-success       [(fn [command _] (notifications/notify! :new-supplement-request command))]
   :user-roles       #{:authority}
   :pre-checks       [permit/is-ymp-permit-type
                      (supplement-request-in-state :draft)]}
  [{:keys [application created user] :as command}]
  (let [request (-> (get-supplement-request request-id application)
                    (supplement-request/send-supplement-request created user))]
    (persist-supplement-request command request)
    (ok)))

(defcommand withdraw-supplement-request
  {:description      "Withdraw request back to draft."
   :parameters       [id request-id]
   :input-validators [(partial action/non-blank-parameters [:id :request-id])]
   :states           valid-application-states
   :user-roles       #{:authority}
   :pre-checks       [permit/is-ymp-permit-type
                      (supplement-request-in-state :requested)]}
  [{:keys [application created user] :as command}]
  (let [request (-> (get-supplement-request request-id application)
                    (supplement-request/withdraw-supplement-request created user))]
    (persist-supplement-request command request)
    (ok)))

(defcommand reply-supplement-request
  {:description      "Reply that the request has been fulfilled."
   :parameters       [id request-id]
   :input-validators [(partial action/non-blank-parameters [:id :request-id])]
   :states           valid-application-states
   :notified         true
   :permissions      [{:required [:application/edit]}]
   :pre-checks       [permit/is-ymp-permit-type
                      (supplement-request-in-state :requested)]}
  [{:keys [application created user] :as command}]
  (let [request (-> (get-supplement-request request-id application)
                    (supplement-request/reply-supplement-request created user))]
    (persist-supplement-request command request)
    (ok)))

(defcommand mark-supplement-request-done
  {:description      "Mark request as done when requirements are deemed to have been met. Authority may do this even
  if request has not been replied."
   :parameters       [id request-id]
   :input-validators [(partial action/non-blank-parameters [:id :request-id])]
   :states           valid-application-states
   :notified         true
   :user-roles       #{:authority}
   :pre-checks       [permit/is-ymp-permit-type
                      (supplement-request-in-state :requested :replied)]}
  [{:keys [application created user] :as command}]
  (let [request (-> (get-supplement-request request-id application)
                    (supplement-request/mark-supplement-request-done created user))]
    (persist-supplement-request command request)
    (ok)))
