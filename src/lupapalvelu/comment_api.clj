(ns lupapalvelu.comment-api
  (:require [clojure.set :refer [union]]
            [monger.operators :refer :all]
            [sade.util :as util]
            [sade.core :refer [ok fail fail!]]
            [sade.strings :as ss]
            [lupapalvelu.action :refer [defquery defcommand update-application notify defraw] :as action]
            [lupapalvelu.application :as application]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.comment :as comment]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.open-inforequest :as open-inforequest]
            [lupapalvelu.permissions :as permissions]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [lupapalvelu.i18n :as i18n]))

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
    (merge (notifications/create-app-model command nil recipient)
           {:name         name
            :link         #(if subpage
                             (notifications/get-subpage-link {:id id :subpage-id (:id target)} subpage % recipient)
                             (notifications/get-application-link app "conversation" % recipient))
            :comment-from (usr/full-name commenter)
            :comment-time (util/to-local-datetime command-ts)
            :comment-text text})))

(notifications/defemail :new-comment
  {:pred-fn (fn [{user :user {roles :roles target :target} :data app :application}]
              (and
                (not= (:type target) "verdict") ; target might be comment target or attachment target
                (or (usr/authority? user)
                    (auth/has-auth-role? app (:id user) :statementGiver)))) ; Statement giver actions send email
   :recipients-fn notifications/comment-recipients-fn
   :model-fn create-model})

(notifications/defemail :application-targeted-comment
  {:recipients-fn  :to-user
   :subject-key    "new-comment"
   :model-fn create-model})

;;
;; Validation
;;

(defn applicant-cant-set-to [{{:keys [to]} :data :as command}]
  (when (and to (not (permissions/permissions? command [:comment/set-target])))
    (fail :error.to-settable-only-by-authority)))

(defn- validate-comment-target [{{:keys [target]} :data}]
  (when-not (#{"application", "attachment", "statement", "verdict"} (:type target))
    (fail :error.unknown-type)))

;;
;; API
;;

(def commenting-states (union states/all-inforequest-states (states/all-application-or-archiving-project-states-but states/terminal-states)))

(defcommand can-target-comment-to-authority
  {:description "Dummy command for UI logic"
   :parameters [id]
   :permissions [{:required [:comment/set-target]}]
   :states      (disj commenting-states :draft)})

(defcommand can-mark-answered
  {:description "Dummy command for UI logic"
   :permissions [{:required [:comment/mark-answered]}]
   :states      #{:info}})

(defquery comments
  {:parameters [id]
   :permissions [{:required [:comment/read]}]
   :states states/all-states}
  [{{app-id :id :as application} :application}]
  (ok :comments (comment/enrich-comments application)))

(defcommand add-comment
  {:parameters [id text target roles]
   :optional-parameters [to mark-answered openApplication]
   :contexts [foreman/foreman-app-context]
   :permissions [{:context  {:application {:state #{:draft}}}
                  :required [:application/edit-draft :comment/add]}
                 {:required [:comment/add]}]
   :states     commenting-states
   :pre-checks [applicant-cant-set-to]
   :input-validators [validate-comment-target
                      (partial action/map-parameters [:target])
                      (partial action/vector-parameters [:roles])]
   :notified   true
   :on-success [(fn [{data :data :as command} _]
                  (when-not (or (ss/blank? (:text data)) (some? (:to data)))
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
          (app-state/state-transition-update :open created application user))))))

(defraw download-conversation-pdf
  {:parameters       [id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :permissions      [{:context  {:application {:state #{:draft}}}
                       :required [:application/edit-draft :comment/add]}
                      {:required [:comment/add]}]
   :user-roles       #{:applicant :authority}}
  [{:keys [user application] :as command}]
  (let [lang    (:language user)
        pdf     (comment/get-comments-as-pdf lang application)
        filename (format "%s-%s.pdf" (:id application) (i18n/localize lang :conversation.title))]
    (if (:ok pdf)
      {:status  200
       :headers {"Content-Type"        "application/pdf"
                 "Content-Disposition" (format "attachment;filename=\"%s\"" filename)}
       :body    (:pdf-file-stream pdf)}
      {:error :conversation.pdf.error})))