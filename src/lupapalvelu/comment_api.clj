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
            [lupapalvelu.user :as user]))

;;
;; Emails
;;

(defn- application-link [lang role full-path]
  (str (env/value :host) "/app/" lang "/" (user/applicationpage-for role) "#!" full-path))

(defn- create-model [{{id :id info-request? :infoRequest} :application
                      {target :target}                    :data}
                     _
                     {role :role
                      name :firstName}]
  (let [permit-type-path (if info-request? "/inforequest" "/application")
        full-path (if (= (:type target) "verdict")
                     (str "/verdict/" id "/" (:id target))
                     (str permit-type-path "/" id "/conversation"))]
    {:name    name
     :link-fi (application-link "fi" role full-path)
     :link-sv (application-link "sv" role full-path)}))

(notifications/defemail :new-comment
  {:pred-fn (fn [{user :user {roles :roles target :target} :data}]
              (and
                (not= (:type target) "verdict") ; target might be comment target or attachment target
                (user/authority? user)))
   :model-fn create-model})

(notifications/defemail :application-targeted-comment
  {:recipients-fn  notifications/from-user
   :subject-key    "new-comment"
   :model-fn create-model})

;;
;; Validation
;;

(defn applicant-cant-set-to [{{:keys [to]} :data user :user}]
  (when (and to (not (user/authority? user)))
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
   :user-authz-roles (conj auth/all-authz-writer-roles :foreman)
   :org-authz-roles auth/commenter-org-authz-roles
   :states states/all-states}
  [{{app-id :id :as application} :application}]
  (ok {:id app-id :comments (comment/enrich-comments application)}))

(defcommand add-comment
  {:parameters [id text target roles]
   :optional-parameters [to mark-answered openApplication]
   :user-roles #{:applicant :authority :oirAuthority}
   :states     commenting-states
   :user-authz-roles (conj auth/all-authz-writer-roles :foreman)
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
                  (when-let [to-user (and (:to data) (user/get-user-by-id (:to data)))]
                    ;; LUPA-407
                    (notifications/notify! :application-targeted-comment (assoc command :user to-user))))
                open-inforequest/notify-on-comment]}
  [{{:keys [to mark-answered openApplication] :or {mark-answered true}} :data :keys [user created application] :as command}]
  (let [to-user   (and to (or (user/get-user-by-id to) (fail! :to-is-not-id-of-any-user-in-system)))
        ensured-visibility (if (seq roles)
                             (remove nil? (conj (set roles) (:role user) (:role to-user)))
                             #{:authority :applicant :oirAuthority})]
    (update-application command
      (util/deep-merge
        (comment/comment-mongo-update (:state application) text target (application/user-role user application) mark-answered user to-user created ensured-visibility)
        (when (and openApplication (= (:state application) "draft"))
          (application/state-transition-update :open created user))))))
