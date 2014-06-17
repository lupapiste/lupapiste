(ns lupapalvelu.comment-api
  (:require [monger.operators :refer :all]
            [sade.util :as util]
            [lupapalvelu.core :refer [ok fail fail!]]
            [lupapalvelu.action :refer [defquery defcommand update-application notify] :as action]
            [lupapalvelu.comment :as comment]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.open-inforequest :as open-inforequest]
            [lupapalvelu.user :as user]))

(defn applicant-cant-set-to [{{:keys [to]} :data user :user} _]
  (when (and to (not (user/authority? user)))
    (fail :error.to-settable-only-by-authority)))

(defn- validate-comment-target [{{:keys [target]} :data}]
  (when (string? target)
    (fail :error.unknown-type)))

(defquery can-target-comment-to-authority
  {:roles [:authority]
   :pre-checks  [open-inforequest/not-open-inforequest-user-validator]
   :description "Dummy command for UI logic"})

(defcommand add-comment
  {:parameters [id text target roles]
   :roles      [:applicant :authority]
   :extra-auth-roles [:statementGiver]
   :pre-checks [applicant-cant-set-to]
   :input-validators [validate-comment-target
                      (partial action/map-parameters [:target])
                      (partial action/vector-parameters [:roles])]
   :notified   true
   :on-success [(notify :new-comment)
                (fn [{data :data :as command} _]
                  (when-let [to-user (and (:to data) (user/get-user-by-id (:to data)))]
                    ;; LUPA-407
                    (notifications/notify! :application-targeted-comment (assoc command :user to-user))))
                open-inforequest/notify-on-comment]}
  [{{:keys [to mark-answered openApplication] :or {mark-answered true}} :data :keys [user created application] :as command}]
  (let [to-user   (and to (or (user/get-user-by-id to) (fail! :to-is-not-id-of-any-user-in-system)))
        ensured-visibility (if (seq roles)
                             (remove nil? (conj (set roles) (:role user) (:role to-user)))
                             #{:authority :applicant})]
    (update-application command
      (util/deep-merge
        (comment/comment-mongo-update (:state application) text target (:role user) mark-answered user to-user created ensured-visibility)
        (when openApplication {$set {:state :open, :opened created}})))))
