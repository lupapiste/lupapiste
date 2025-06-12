(ns lupapalvelu.comment-api
  (:require [clojure.set :refer [union]]
            [lupapalvelu.action :refer [defcommand defquery defraw update-application] :as action]
            [lupapalvelu.application-state :as app-state]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.comment :as comment]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.open-inforequest :as open-inforequest]
            [lupapalvelu.permissions :as permissions]
            [lupapalvelu.states :as states]
            [lupapalvelu.system.util-components :as components]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer [fail fail! ok]]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]))

;;
;; Emails
;;

(defn- create-model [{{id :id :as app}      :application
                      {:keys [target text]} :data
                      command-ts            :created
                      commenter             :user
                      system                :system
                      :as                   command}
                     _
                     {name :firstName :as recipient}]
  (let [subpage (when (= (:type target) "verdict")
                  "verdict")
        host    (components/system->host system)]
    (merge (notifications/create-app-model command nil recipient)
           {:name         name
            :link         #(if subpage
                             (notifications/get-subpage-link host {:id id :subpage-id (:id target)} subpage % recipient)
                             (notifications/get-application-link system app "conversation" % recipient))
            :comment-from (usr/full-name commenter)
            :comment-time (date/finnish-datetime command-ts :zero-pad)
            :comment-text text})))

(notifications/defemail :new-comment
  {:pred-fn (fn [{user :user {target :target} :data app :application}]
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

(defn- to-must-be-string-if-given [{{to :to} :data}]
  (when (some? to)
    (when-not (string? to)
      (fail :error.invalid-type))))

(defn applicant-cant-set-to [{{:keys [to]} :data :as command}]
  (when (and (ss/not-blank? to)
             (not (permissions/permissions? command [:comment/set-target])))
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
   :states      (disj commenting-states :draft)}
  [_]
  (ok))

(defcommand can-mark-answered
  {:description "Dummy command for UI logic"
   :permissions [{:required [:comment/mark-answered]}]
   :states      #{:info}}
  [_]
  (ok))

(defquery comments
  {:parameters [id]
   :permissions [{:required [:comment/read]}]
   :states states/all-states}
  [{:keys [application]}]
  (ok :comments (comment/enrich-comments application)))

(defcommand add-comment
  {:parameters          [id text target roles]
   :optional-parameters [to mark-answered openApplication]
   :contexts            [foreman/foreman-app-context]
   :permissions         [{:context  {:application {:state #{:draft}}}
                          :required [:application/edit-draft :comment/add]}
                         {:required [:comment/add]}]
   :states              commenting-states
   :pre-checks          [applicant-cant-set-to]
   :input-validators    [validate-comment-target
                         to-must-be-string-if-given
                         (partial action/map-parameters [:target])
                         (partial action/vector-parameters [:roles])]
   :notified            true
   :on-success          [(fn [{data :data :as command} _]
                           (let [to-user (some-> (:to data) ss/blank-as-nil usr/get-user-by-id)]
                             (when (and (ss/not-blank? (:text data)) (nil? to-user))
                               (notifications/notify! :new-comment command))
                             (when to-user
                               ;; LUPA-407
                               (notifications/notify! :application-targeted-comment (assoc command :to-user [to-user])))))
                         open-inforequest/notify-on-comment]}
  [{{:keys [to mark-answered openApplication] :or {mark-answered true}} :data :keys [user created application] :as command}]
  (let [to                 (ss/blank-as-nil to)
        to-user            (and to (or (usr/get-user-by-id to) (fail! :to-is-not-id-of-any-user-in-system)))
        ensured-visibility (if (seq roles)
                             (remove nil? (conj (set roles) (:role user) (:role to-user)))
                             #{:authority :applicant :oirAuthority})]
    (or (update-application command
                            (util/deep-merge
                              (comment/comment-mongo-update application text target
                                                            (domain/user-comment-type user application)
                                                            mark-answered user to-user created
                                                            ensured-visibility)
                              (when (and openApplication (= (:state application) "draft"))
                                (app-state/state-transition-update :open created application user))))
        (ok))))

(defraw download-conversation-pdf
  {:parameters       [:id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :permissions      [{:context  {:application {:state #{:draft}}}
                       :required [:application/edit-draft :comment/add]}
                      {:required [:comment/add]}]
   :user-roles       #{:applicant :authority}}
  [{:keys [user application system]}]
  (let [lang    (:language user)
        pdf     (comment/get-comments-as-pdf system lang application)
        filename (format "%s-%s.pdf" (:id application) (i18n/localize lang :conversation.title))]
    (if (:ok pdf)
      {:status  200
       :headers {"Content-Type"        "application/pdf"
                 "Content-Disposition" (format "attachment;filename=\"%s\"" filename)}
       :body    (:pdf-file-stream pdf)}
      {:error :conversation.pdf.error})))
