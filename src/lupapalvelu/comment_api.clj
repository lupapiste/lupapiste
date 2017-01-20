(ns lupapalvelu.comment-api
  (:require [clojure.set :refer [union]]
            [monger.operators :refer :all]
            [sade.env :as env]
            [sade.util :as util]
            [sade.core :refer [ok fail fail!]]
            [sade.strings :as ss]
            [lupapalvelu.action :refer [defquery defcommand update-application notify] :as action]
            [lupapalvelu.application :as application]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.comment :as comment]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.open-inforequest :as open-inforequest]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]))

;;
;; Emails
;;

(defn- create-model [{{id :id  :as app} :application
                      {:keys [target text]}               :data
                      command-ts                          :created
                      commenter                           :user :as command}
                     _
                     {name :firstName :as recipient}]
  (let [subpage   (when (= (:type target) "verdict")
                    "verdict")]
    (merge (notifications/new-email-app-model command nil recipient)
           {:name         name
            :link         #(if subpage
                             (notifications/get-subpage-link {:id id :subpage-id (:id target)} subpage % recipient)
                             (notifications/get-application-link app "conversation" % recipient))
            :comment-from (usr/full-name commenter)
            :comment-time (util/to-local-datetime command-ts)
            :comment-text text})))

(notifications/defemail :new-comment
  {:pred-fn (fn [{user :user {roles :roles target :target} :data}]
              (and
                (not= (:type target) "verdict") ; target might be comment target or attachment target
                (usr/authority? user)))
   :recipients-fn notifications/comment-recipients-fn
   :model-fn create-model})

(notifications/defemail :application-targeted-comment
  {:recipients-fn  :to-user
   :subject-key    "new-comment"
   :model-fn create-model})

;;
;; Validation
;;

(defn applicant-cant-set-to [{{:keys [to]} :data user :user}]
  (when (and to (not (usr/authority? user)))
    (fail :error.to-settable-only-by-authority)))

(defn- validate-comment-target [{{:keys [target]} :data}]
  (when-not (#{"application", "attachment", "statement", "verdict"} (:type target))
    (fail :error.unknown-type)))

;;
;; API
;;

(def commenting-states (union states/all-inforequest-states (states/all-application-states-but states/terminal-states)))

(defcommand can-target-comment-to-authority
  {:description "Dummy command for UI logic"
   :user-roles #{:authority}
   :org-authz-roles auth/commenter-org-authz-roles
   :states      (disj commenting-states :draft)})

(defcommand can-mark-answered
  {:description "Dummy command for UI logic"
   :user-roles #{:authority :oirAuthority}
   :states      #{:info}})

(defquery comments
  {:parameters [id]
   :user-roles #{:applicant :authority :oirAuthority}
   :user-authz-roles auth/comment-user-authz-roles
   :org-authz-roles auth/commenter-org-authz-roles
   :states states/all-states}
  [{{app-id :id :as application} :application}]
  (ok {:id app-id :comments (comment/enrich-comments application)}))

(defcommand add-comment
  {:parameters [id text target roles]
   :optional-parameters [to mark-answered openApplication]
   :user-roles #{:applicant :authority :oirAuthority}
   :states     commenting-states
   :user-authz-roles auth/comment-user-authz-roles
   :org-authz-roles auth/commenter-org-authz-roles
   :pre-checks [applicant-cant-set-to
                foreman/allow-foreman-only-in-foreman-app
                application/validate-authority-in-drafts]
   :input-validators [validate-comment-target
                      (partial action/map-parameters [:target])
                      (partial action/vector-parameters [:roles])]
   :notified   true
   :on-success [(fn [{data :data :as command} _]
                  (when-not (ss/blank? (:text data))
                    (notifications/notify! :new-comment command))
                  (when-let [to-user (and (:to data) (usr/get-user-by-id (:to data)))]
                    ;; LUPA-407
                    (notifications/notify! :application-targeted-comment (assoc command :to-user [to-user]))))
                open-inforequest/notify-on-comment]}
  [{{:keys [to mark-answered openApplication] :or {mark-answered true}} :data :keys [user created application] :as command}]
  (let [to-user   (and to (or (usr/get-user-by-id to) (fail! :to-is-not-id-of-any-user-in-system)))
        ensured-visibility (if (seq roles)
                             (remove nil? (conj (set roles) (:role user) (:role to-user)))
                             #{:authority :applicant :oirAuthority})]
    (update-application command
      (util/deep-merge
        (comment/comment-mongo-update (:state application) text target (application/user-role user application) mark-answered user to-user created ensured-visibility)
        (when (and openApplication (= (:state application) "draft"))
          (application/state-transition-update :open created application user))))))
