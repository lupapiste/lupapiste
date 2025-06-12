(ns lupapalvelu.foreman-api
  (:require [lupapalvelu.action :refer [defcommand defquery update-application] :as action]
            [lupapalvelu.application :as application]
            [lupapalvelu.application.foreman :as foreman-app]
            [lupapalvelu.backing-system.krysp.foreman :as bs-foreman]
            [lupapalvelu.company :as company]
            [lupapalvelu.document.approval :as approval]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.foreman-application-util :as foreman-util]
            [lupapalvelu.logging :as log :refer [errorf]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.organization-api :as org-api]
            [lupapalvelu.states :as states]
            [lupapalvelu.tasks :as tasks]
            [lupapalvelu.user :as usr]
            [monger.operators :refer [$each $elemMatch $ne $push $set $unset]]
            [sade.core :refer :all]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as v]
            [schema.core :as sc]))

(defcommand create-foreman-application
  {:parameters       [:id taskId foremanRole foremanEmail]
   :input-validators [(partial action/email-validator :foremanEmail)]
   :permissions      [{:context  {:application {:state states/pre-verdict-states}}
                       :required [:application/create-foreman-app-in-pre-verdict-states]}
                      {:required [:application/create-foreman-app-in-post-verdict-states]}]
   :states           (apply states/all-application-states-but :draft states/terminal-states)
   :notified         true
   :description      "Creates foreman application based on current application. Foreman email can be nil."}
  [{:keys [created user application] :as command}]
  (when (->> (get-in (usr/get-user-by-email foremanEmail) [:company :id])
             (company/company-denies-invitations? application))
    (fail! :invite.company-denies-invitation))
  (let [foreman-user (when (v/valid-email? foremanEmail)
                       (usr/get-or-create-user-by-email foremanEmail user))
        foreman-app  (-> (foreman/new-foreman-application command)
                         (application/ryhti-data-2026-flag-from-previous-permit application)
                         (foreman/update-foreman-docs application foremanRole)
                         (foreman/copy-auths-from-linked-app application user created)
                         (foreman/add-foreman-invite-auth foreman-user user created))]
    (application/insert-application foreman-app)
    (application/do-add-link-permit foreman-app (:id application))
    (foreman/update-foreman-task-on-linked-app! application foreman-app taskId command)
    (foreman/authorize-and-send-invite-notifications! foreman-app foreman-user application command)
    (ok :id (:id foreman-app) :auth (:auth foreman-app))))

(defn- create-foreman-doc!
  "Insert a new foreman document for given role. Returns the id."
  [{:keys [application] :as command} foremanRole]
  (let [tyonjohtaja-doc (foreman/new-foreman-document foremanRole command)]
    (mongo/update-by-id :applications (:id application)
                        {$push {:documents tyonjohtaja-doc}})
    (:id tyonjohtaja-doc)))

(defcommand create-and-invite-foreman-by-id
  {:parameters          [id foremanUser foremanRole]
   :optional-parameters [taskId]
   :input-validators    [foreman/NewForemanUserInvite]
   :pre-checks          [foreman/validate-foreman-user-params]
   :permissions         [{:context  {:application {:state states/pre-verdict-states}}
                          :required [:application/create-foreman-app-in-pre-verdict-states]}
                         {:required [:application/create-foreman-app-in-post-verdict-states]}]
   :states              (apply states/all-application-states-but :draft states/terminal-states)
   :notified            true
   :description         "Given a user id, creates foreman document on the current application and invites the foreman on the application."}
  [{:keys [created user application] :as command}]
  (if-let [foreman-user (usr/get-user-by-id foremanUser)]
    (let [tyonjohtaja-doc-id (create-foreman-doc! command foremanRole)]
      ;; We set the given user to the document and clear the task
      (doc-persistence/do-set-user-to-document (mongo/by-id :applications id) tyonjohtaja-doc-id foremanUser "" created user)
      ;; NOTE: This `taskId` parameter seems to be only used when `new-foreman-invite` is disabled.
      (when taskId
        (tasks/set-state command taskId :ok))
      ;; Only notify as foreman is already authorized
      (foreman/send-invite-notification! foreman-user command)
      ;; Not sure if this is needed anymore, but some old code might rely on this
      (foreman/update-foreman-task-on-linked-app! application application taskId command)
      (ok :id (:id application) :auth (:auth application)))
    (fail :invite.user-not-found)))

(defcommand create-and-invite-foreman-by-email
  {:parameters          [:id foremanRole foremanEmail]
   :optional-parameters [taskId]
   :input-validators    [foreman/NewForemanEmailInvite]
   :pre-checks          [foreman/validate-foreman-email-params]
   :permissions         [{:context  {:application {:state states/pre-verdict-states}}
                          :required [:application/create-foreman-app-in-pre-verdict-states]}
                         {:required [:application/create-foreman-app-in-post-verdict-states]}]
   :states              (apply states/all-application-states-but :draft states/terminal-states)
   :notified            true
   :description         "Given an email, creates foreman document on the current application and invites the foreman on the application."}
  [{:keys [user application] :as command}]
  (let [foremanEmail (ss/canonize-email foremanEmail)]
    (if-let [foreman-user (usr/get-or-create-user-by-email foremanEmail user)]
      (do
        (create-foreman-doc! command foremanRole)
        ;; Authorize new user and send notification
        (foreman/authorize-and-send-invite-notification! foreman-user application command)
        ;; Not sure if this is needed anymore, but some old code might rely on this
        (foreman/update-foreman-task-on-linked-app! application application taskId command)
        (ok :id (:id application) :auth (:auth application)))
      (fail :invite.user-not-found))))

(defcommand update-foreman-other-applications
  {:parameters  [:id docId]
   :input-validators [(partial action/non-blank-parameters [:id :docId])]
   :permissions [{:context  {:application {:state #{:draft}}}
                  :required [:application/edit-draft]}
                 {:required [:application/edit]}]
   :states      states/all-states}
  [{:keys [application created] :as command}]
  (let [tyonjohtaja-doc      (domain/get-document-by-id application docId)
        foreman-applications (seq (foreman/get-foreman-project-applications tyonjohtaja-doc (:id application)))
        other-applications   (map #(foreman/other-project-document % created) foreman-applications)
        muut-hankkeet        (get-in tyonjohtaja-doc [:data :muutHankkeet])
        muut-hankkeet-new    (->> (vals muut-hankkeet)
                                  (remove #(get-in % [:autoupdated :value]))
                                  (concat other-applications)
                                  (zipmap (map str (range))))]
    (if tyonjohtaja-doc
      (do
        (update-application command {:documents {$elemMatch {:id (:id tyonjohtaja-doc)}}} {$set {:documents.$.data.muutHankkeet muut-hankkeet-new}})
        (ok))
      (fail :error.document-not-found))))

(defcommand link-foreman-task
  {:parameters       [:id taskId foremanAppId]
   :input-validators [(partial action/non-blank-parameters [:id :taskId])]
   :permissions      [{:context  {:application {:state #{:draft}}}
                       :required [:application/edit-draft]}
                      {:required [:application/edit]}]
   :states           states/all-states
   :pre-checks       [foreman/ensure-foreman-not-linked]}
  [{:keys [created application]}]
  (let [task (util/find-by-id taskId (:tasks application))]
    (if task
      (let [updates [[[:asiointitunnus] foremanAppId]]]
        (doc-persistence/persist-model-updates application "tasks" task updates created))
      (fail :error.task-not-found))))

(defquery foreman-history
  {:description      "Foreman application target history in Lupapiste. If
   all parameter is true, then the whole history is returned otherwise
   just non-redundant highlights."
   :states           states/all-states
   :parameters       [:id all docId]
   :input-validators [(partial action/non-blank-parameters [:all :docId])]
   :permissions      [{:required [:application/show-foreman-history]}]}
  [{:keys [application]}]
  (if application
    (let [history-data (foreman/get-foreman-history-data (domain/get-document-by-id application docId) (:id application))]
      (if (= all "true")
        (ok :projects history-data :all true)
        (let [reduced-data (foreman/reduce-foreman-history-data history-data)]
          (ok :projects reduced-data
              :all (= (count history-data) (count reduced-data))))))
    (fail :error.application-not-found)))

(defquery foreman-applications
  {:description "Returns all foreman (supervisor) applications linked to the given application"
   :states      states/all-states
   :permissions [{:required [:application/read]}]
   :parameters  [:id]}
  [{:keys [user application]}]
  (->> (foreman-util/get-linked-foreman-applications application)
       (foreman/add-required-roles-to-foreman-info application)
       (foreman/filter-termination-reason user)
       (into [])
       (ok :applications)))

(defquery foreman-documents
  {:description "Returns all foreman documents (tyonjohtaja-v3) on the given application"
   :states      states/all-states
   :permissions [{:required [:application/read]}]
   :parameters  [:id]}
  [{:keys [application]}]
  (->> application
       :documents
       ;; :state is empty until foreman is declared
       (filter (every-pred :tj-document :state))
       (ok :documents)))

(defquery application-has-foreman
  {:description    "Pseudo query that checks that an application has at least one 'initiated' (declared/etc.) foreman"
   :permissions    [{:required [:application/read]}]
   :request-schema {:id ssc/ApplicationId}
   :pre-checks     [foreman/application-has-initiated-foreman]}
  [_]
  (ok))

(defcommand request-foreman-termination
  {:description      "The applicant or a foreman can request the termination of the foreman.
   This has to then be confirmed by an authority with the command confirm-foreman-termination.
   Foreman email is used to identify if the user is the given foreman without a database query in the pre-check."
   :states           states/post-verdict-states
   :parameters       [:id foreman-app-id :foreman-email reason]
   :input-validators [(partial action/non-blank-parameters [:id :foreman-app-id])]
   :permissions      [{:required [:application/request-foreman-termination]}]
   :pre-checks       [foreman/user-can-request-foreman-termination
                      (org/organization-flag-pre-check :foreman-termination-request-enabled true)]}
  [command]
  (let [foreman-app (mongo/by-id :applications foreman-app-id)]
    (foreman/commit-foreman-termination-changes command
                                                foreman-app
                                                (foreman/request-foreman-termination command foreman-app reason))
    (foreman/upsert-foreman-assignments command foreman-app)
    (ok)))

(defcommand request-foreman-termination-v3
  {:description      "The applicant or a foreman can request the termination of the foreman.
   This has to then be confirmed by an authority with the command confirm-foreman-termination.
   Foreman email is used to identify if the user is the given foreman without a database query in the pre-check."
   :states           states/post-verdict-states
   :parameters       [:id foreman-doc-id :foreman-email reason]
   :input-validators [(partial action/non-blank-parameters [:id :foreman-doc-id])]
   :permissions      [{:required [:application/request-foreman-termination]}]
   :pre-checks       [foreman/user-can-request-foreman-termination
                      (org/organization-flag-pre-check :foreman-termination-request-enabled true)
                      foreman/foreman-doc-id-is-foreman-v3-document]}
  [{:keys [application] :as command}]
  (let [foreman-doc (domain/get-document-by-id application foreman-doc-id)]
    (foreman/commit-foreman-v3-termination-changes command
                                                   application
                                                   foreman-doc-id
                                                   (foreman/request-foreman-termination command foreman-doc reason))
    ;; TODO LPK-7297 refactor foreman assignments for v3
    #_(foreman/upsert-foreman-assignments command foreman-app)
    (ok)))

(defcommand cancel-foreman-termination-request
  {:description         "The applicant, a foreman or authority can cancel the termination of the foreman.
                      Foreman email is used to identify if the user is the given foreman without a database query in the pre-check."
   :states              states/post-verdict-states
   :parameters          [:id :foreman-email foreman-app-id]
   :input-validators    [(partial action/non-blank-parameters [:id :foreman-app-id])
                         (partial action/email-validator :foreman-email)]
   :permissions         [{:required [:application/cancel-foreman-termination-request]}]
   :pre-checks          [(action/some-pre-check foreman/user-can-request-foreman-termination
                                                foreman/user-is-authority-for-foreman-app)
                         foreman/termination-requested?
                         (org/organization-flag-pre-check :foreman-termination-request-enabled true)]}
  [{:keys [created]}]
  (mongo/update-by-id :applications foreman-app-id {$unset {:foremanTermination true}
                                                    $set   {:modified created}})
  (ok))

(defcommand cancel-foreman-termination-request-v3
  {:description         "The applicant, a foreman or authority can cancel the termination of the foreman.
                      Foreman email is used to identify if the user is the given foreman without a database query in the pre-check."
   :states              states/post-verdict-states
   :parameters          [:id :foreman-email foreman-doc-id]
   :input-validators    [(partial action/non-blank-parameters [:id :foreman-doc-id])
                         (partial action/email-validator :foreman-email)]
   :permissions         [{:required [:application/cancel-foreman-termination-request]}]
   :pre-checks          [(action/some-pre-check foreman/user-can-request-foreman-termination
                                                foreman/user-is-authority-for-foreman-app)
                         foreman/termination-requested-v3?
                         foreman/foreman-doc-id-is-foreman-v3-document
                         (org/organization-flag-pre-check :foreman-termination-request-enabled true)]}
  [{:keys [created application]}]
  (update-application (action/application->command application)
    {:documents {$elemMatch {:id foreman-doc-id}}}
    {$unset {:documents.$.foremanTermination true}
     $set   {:modified created}})
  (ok))

(defcommand confirm-foreman-termination
  {:description      "An authority can confirm the termination of a foreman once it has been requested by somebody."
   :states           states/post-verdict-states
   :parameters       [:id foreman-app-id]
   :input-validators [(partial action/non-blank-parameters [:id :foreman-app-id])]
   :permissions      [{:required [:application/confirm-foreman-termination]}]
   :pre-checks       [foreman/user-is-authority-for-foreman-app]
   :notified         true
   :on-success       [foreman/send-termination-email!]}
  [{:keys [lang] :as command}]
  (let [foreman-app (mongo/by-id :applications foreman-app-id)]
    (foreman/commit-foreman-termination-changes command
                                                foreman-app
                                                (foreman/confirm-foreman-termination command foreman-app true))
    (foreman/remove-foreman-assignment-target command foreman-app-id)
    (bs-foreman/send-foreman-termination-msgs! command foreman-app-id true lang)
    (ok)))

(defcommand confirm-foreman-termination-v3
  {:description      "An authority can confirm the termination of a foreman once it has been requested by somebody."
   :states           states/post-verdict-states
   :parameters       [:id foreman-doc-id]
   :input-validators [(partial action/non-blank-parameters [:id :foreman-doc-id])]
   :permissions      [{:required [:application/confirm-foreman-termination]}]
   :notified         true
   :pre-checks       [foreman/foreman-doc-id-is-foreman-v3-document]
   :on-success       [foreman/send-termination-email!]}
  [{:keys [lang application] :as command}]
  (let [foreman-doc (domain/get-document-by-id application foreman-doc-id)
        app-id      (:id application)]
    (foreman/commit-foreman-v3-termination-changes command
                                                   application
                                                   foreman-doc-id
                                                   (foreman/confirm-foreman-termination command foreman-doc true))
    ;; should this be removed once the target is the main application and not the foreman application?
    (foreman/remove-foreman-assignment-target command app-id)
    (bs-foreman/send-foreman-termination-msgs! command app-id true lang foreman-doc)
    (ok)))

(defcommand terminate-foreman
  {:description      "An authority can terminate a foreman by skipping the request phase altogether."
   :states           states/post-verdict-states
   :parameters       [:id foreman-app-id reason ended]
   :input-validators [(partial action/non-blank-parameters [:id :foreman-app-id :reason])
                      (partial action/positive-number-parameters [:ended])]
   :permissions      [{:required [:application/confirm-foreman-termination]}]
   :pre-checks       [foreman/user-is-authority-for-foreman-app
                      (partial foreman/termination-date-after-start true)]
   :notified         true
   :on-success       [foreman/send-termination-email!]}
  [{:keys [lang organization] :as command}]
  (let [foreman-app (mongo/by-id :applications foreman-app-id)]
    (log/with-logging-context
      {:applicationId foreman-app-id}
      (foreman/commit-foreman-termination-changes command
                                                  foreman-app
                                                  (foreman/terminate-foreman command foreman-app reason ended))
      (when (:foreman-termination-document-enabled (force organization))
        (foreman/create-and-attach-termination-document! command foreman-app))
      (bs-foreman/send-foreman-termination-msgs! command foreman-app-id false lang)
      (ok))))

(defcommand terminate-foreman-v3
  {:description      "An authority can terminate a foreman-v3 by skipping the request phase altogether."
   :states           states/post-verdict-states
   :parameters       [id foreman-doc-id reason ended]
   :input-validators [(partial action/non-blank-parameters [:id :foreman-doc-id :reason])
                      (partial action/positive-number-parameters [:ended])]
   :permissions      [{:required [:application/confirm-foreman-termination]}]
   :pre-checks       [(partial foreman/termination-date-after-start true)
                      foreman/foreman-doc-id-is-foreman-v3-document]
   :notified         true
   :on-success       [foreman/send-termination-email!]}
  [{:keys [lang organization application] :as command}]
  (let [foreman-doc (domain/get-document-by-id application foreman-doc-id)
        termination-changes (foreman/terminate-foreman command foreman-doc reason ended)]
    (foreman/commit-foreman-v3-termination-changes command
                                                   application
                                                   foreman-doc-id
                                                   termination-changes)
    (when (:foreman-termination-document-enabled (force organization))
      (let [updated-foreman-doc (-> (mongo/by-id :applications id)
                                    (domain/get-document-by-id foreman-doc-id))]
        (foreman/create-and-attach-termination-document! command updated-foreman-doc)))
    (bs-foreman/send-foreman-termination-msgs! command id false lang (assoc foreman-doc :foremanTermination termination-changes))
    (ok)))

(defcommand remove-foreman-from-list
  {:description      "An authority can remove a rejected foreman from the list of foremen and requirements"
   :states           states/post-verdict-states
   :parameters       [:id foreman-app-id]
   :input-validators [(partial action/non-blank-parameters [:id :foreman-app-id])]
   :permissions      [{:required [:application/remove-foreman-from-list]}]
   :pre-checks       [(partial foreman/foreman-state-is #{"rejected"})
                      foreman/user-is-authority-for-foreman-app]}
  [{:keys [created]}]
  (let [foreman-app (mongo/by-id :applications foreman-app-id)]
    (update-application (action/application->command foreman-app)
                        {$set {:_non-listed-foreman true
                               :modified            created}})
    (ok)))

(defcommand set-foreman-start-date
  {:description      "An authority can reset the foreman start date."
   :states           states/post-verdict-states
   :parameters       [:id foreman-app-id started]
   :input-validators [(partial action/non-blank-parameters [:id :foreman-app-id])
                      (partial action/positive-number-parameters [:started])]
   :permissions      [{:required [:application/edit]}]
   :pre-checks       [foreman/user-is-authority-for-foreman-app
                      (partial foreman/termination-date-after-start false)]}
  [{:keys [created user]}]
  (let [foreman-app (mongo/by-id :applications foreman-app-id)]
    (update-application (action/application->command foreman-app)
                        {$set  (merge {:started started}
                                      (when (:foremanTermination foreman-app)
                                        {:foremanTermination.started started}))
                         $push {:changeLog {:field :started
                                            :value started
                                            :user  (usr/summary user)
                                            :ts    created}}})
    (ok)))

(defcommand set-foreman-start-date-v3
  {:description      "An authority can reset the foreman start date."
   :states           states/post-verdict-states
   :parameters       [:id foreman-doc-id started]
   :input-validators [(partial action/non-blank-parameters [:id :foreman-doc-id])
                      (partial action/positive-number-parameters [:started])]
   :permissions      [{:required [:application/approve-foreman]}]
   :pre-checks       [(partial foreman/termination-date-after-start false)
                      foreman/foreman-doc-id-is-foreman-v3-document]}
  [{:keys [created user application] :as command}]
  (let [foreman-doc (domain/get-document-by-id application foreman-doc-id)]
    (update-application command
      {:documents {$elemMatch {:id foreman-doc-id}}}
      {$set  (merge {:documents.$.started started}
                    (when (:foremanTermination foreman-doc)
                      {:documents.$.foremanTermination.started started}))
       $push {:documents.$.changeLog {:field :started
                                      :value started
                                      :user  (usr/summary user)
                                      :ts    created}}}))
  (ok))

(defcommand acknowledge-foreman
  {:description      "An authority can acknowledge the new type of foreman (tyonjohtaja-v3)."
   :states           states/post-verdict-states
   :categories       #{:documents}
   :parameters       [id foreman-doc-id]
   :input-validators [{:id             ssc/NonBlankStr
                       :foreman-doc-id ssc/NonBlankStr}]
   :permissions      [{:required [:application/approve-foreman]}]
   :pre-checks       [(foreman/foreman-v3-state-is #{"declared"})
                      (partial foreman/is-setting-enabled? :acknowledge)
                      foreman/foreman-doc-id-is-foreman-v3-document]}
  [{:keys [application created user lang] :as command}]
  (let [change-log-item {:field :state
                         :value "acknowledged"
                         :user  (usr/summary user)
                         :ts    created}]
    (bs-foreman/send-foreman-msgs! command id lang (-> (domain/get-document-by-id application foreman-doc-id)
                                                       (assoc :state "acknowledged")
                                                       (update :changeLog conj change-log-item)))
    (update-application command
      {:documents {$elemMatch {:id foreman-doc-id}}}
      (util/deep-merge
        {$set  {:documents.$.state "acknowledged"
                :modified          created}
         $push {:documents.$.changeLog change-log-item}}
        ;; approving also sets "OK" to document
        (model/with-timestamp created
          (approval/->approval-mongo-model "" (approval/->approved "approved" user))))))
  (ok))

(defcommand approve-foreman
  {:description      "An authority can approve the new type of foreman (tyonjohtaja-v3)
                      which sends the foreman application to backing system."
   :states           states/post-verdict-states
   :categories       #{:documents}
   :parameters       [id foreman-doc-id]
   :input-validators [{:id             ssc/NonBlankStr
                       :foreman-doc-id ssc/NonBlankStr}]
   :permissions      [{:required [:application/approve-foreman]}]
   :pre-checks       [(foreman/foreman-v3-state-is #{"declared" "submitted"})
                      (partial foreman/is-setting-enabled? :approve)
                      foreman/foreman-doc-id-is-foreman-v3-document]}
  [{:keys [application created user lang] :as command}]
  (let [change-log-item {:field :state
                         :value "approved"
                         :user  (usr/summary user)
                         :ts    created}]
    (bs-foreman/send-foreman-msgs! command id lang (-> (domain/get-document-by-id application foreman-doc-id)
                                            (assoc :state "approved")
                                            (update :changeLog conj change-log-item)))
    (update-application command
      {:documents {$elemMatch {:id foreman-doc-id}}}
      (util/deep-merge
        {$set  {:documents.$.state "approved"
                :modified          created}
         $push {:documents.$.changeLog change-log-item}}
        ;; approving also sets "OK" to document
        (model/with-timestamp created
          (approval/->approval-mongo-model "" (approval/->approved "approved" user))))))
  (ok))

(defcommand declare-foreman
  {:description      "User can declare a new type of foreman (tyonjohtaja-v3)."
   :states           states/post-verdict-states
   :categories       #{:documents}
   :parameters       [:id foreman-doc-id]
   :input-validators [{:id             ssc/NonBlankStr
                       :foreman-doc-id ssc/NonBlankStr}]
   :permissions      [{:required [:application/edit]}]
   :pre-checks       [foreman/foreman-v3-state-missing
                      foreman/foreman-v3-consent-given
                      foreman/foreman-doc-id-is-foreman-v3-document
                      foreman/foreman-v3-document-is-valid?]}
  [{:keys [created user] :as command}]
  (update-application command
    {:documents {$elemMatch {:id foreman-doc-id}}}
    ;; foreman responsibility starts once the foreman has been declared
    (util/deep-merge
      {$set  {:documents.$.type    "notification"
              :documents.$.state   "declared"
              :documents.$.started created
              :modified            created}
       $push {:documents.$.changeLog {:field :state
                                      :value "declared"
                                      :user  (usr/summary user)
                                      :ts    created}}}
      ;; lock document
      (model/with-timestamp created
        (approval/->approval-mongo-model "" (approval/->approved "locked" user)))))
  (ok))

(defcommand submit-foreman
  {:description      "User can submit a new type of foreman (tyonjohtaja-v3)."
   :states           states/post-verdict-states
   :categories       #{:documents}
   :parameters       [:id foreman-doc-id]
   :input-validators [{:id             ssc/NonBlankStr
                       :foreman-doc-id ssc/NonBlankStr}]
   :permissions      [{:required [:application/edit]}]
   :pre-checks       [foreman/foreman-v3-state-missing
                      foreman/foreman-v3-consent-given
                      foreman/foreman-doc-id-is-foreman-v3-document]}
  [{:keys [created user application] :as command}]
  (let [foreman-doc (domain/get-document-by-id application foreman-doc-id)
        errors      (model/validate application foreman-doc)]
    (when-not (empty? errors) (fail! :error.document-not-valid))
    (update-application command
                        {:documents {$elemMatch {:id foreman-doc-id}}}
                        (util/deep-merge
                         {$set  {:documents.$.type  "application"
                                 :documents.$.state "submitted"
                                 :modified          created}
                          $push {:documents.$.changeLog {:field :state
                                                         :value "submitted"
                                                         :user  (usr/summary user)
                                                         :ts    created}}}
      ;; lock document
                         (model/with-timestamp created
                           (approval/->approval-mongo-model "" (approval/->approved "locked" user)))))
    (ok)))

(defcommand foreman-consent
  {:description      "Foreman can give a consent for the role (tyonjohtaja-v3)."
   :states           states/post-submitted-states
   :parameters       [:id foreman-doc-id consent]
   :categories       #{:documents}
   :input-validators [{:id             ssc/NonBlankStr
                       :foreman-doc-id ssc/NonBlankStr
                       :consent        sc/Bool}]
   :permissions      [{:required [:application/edit]}]
   :pre-checks       [(action/some-pre-check foreman/foreman-v3-state-missing
                                             ;; LPK-7779 consent has to be given again if foreman is changed during the process
                                             (foreman/foreman-v3-state-is #{"declared" "submitted"}))
                      foreman/is-foreman-user?
                      foreman/foreman-doc-id-is-foreman-v3-document]}
  [command]
  (update-application command
    {:documents {$elemMatch {:id foreman-doc-id}}}
    {$set {:documents.$.data.foremanConsent.value consent}})
  (ok {:authModel (action/maybe-update-document-auth-model command)}))

(defquery foreman-consent-given
  {:description      "Pseudo query to tell if document has the foremanConsent field set"
   :parameters       [:id :documentId]
   :categories       #{:documents}
   :input-validators [{:id             ssc/NonBlankStr
                       :documentId     ssc/NonBlankStr}]
   :permissions      [{:required [:application/read]}]
   :pre-checks       [(fn [{:keys [application]
                            {:keys [documentId]} :data}]
                        (when-let [doc (domain/get-document-by-id application documentId)]
                          (when-not (foreman-app/tj-document->foreman-consent doc)
                            (fail :no-consent))))]}
  [_]
  (ok))

(defquery foreman-responsibility-timestamps
  {:description      "Returns foreman (tyonjohtaja-v3) responsibility timestamps."
   :states           states/all-states
   :parameters       [:id foreman-doc-id]
   :input-validators [{:id             ssc/NonBlankStr
                       :foreman-doc-id ssc/NonBlankStr}]
   :permissions      [{:required [:application/read]}]
   :pre-checks       [foreman/foreman-doc-id-is-foreman-v3-document]}
  [{:keys [application]}]
  (ok :timestamps (foreman-util/get-foreman-responsibility-timestamps
                    (domain/get-document-by-id application foreman-doc-id)
                    application)))

(defcommand send-foreman-message
  {:description      "An authority can resend approved foreman application to backing system."
   :states           states/post-verdict-states
   :categories       #{:documents}
   :parameters       [id foreman-doc-id]
   :input-validators [{:id             ssc/NonBlankStr
                       :foreman-doc-id ssc/NonBlankStr}]
   :permissions      [{:required [:application/approve-foreman]}]
   :pre-checks       [(foreman/foreman-v3-state-is #{"approved" "acknowledged"})
                      foreman/foreman-doc-id-is-foreman-v3-document]}
  [{:keys [application lang] :as command}]
  (bs-foreman/send-foreman-msgs! command id (or lang :fi) (domain/get-document-by-id application foreman-doc-id))
  (ok))

(defcommand change-foreman-doc-type
  {:description      "An authority can change the foreman application type notification/application."
   :states           states/post-verdict-states
   :categories       #{:documents}
   :parameters       [:id foreman-doc-id foreman-type]
   :input-validators [{:id             ssc/NonBlankStr
                       :foreman-doc-id ssc/NonBlankStr
                       :foreman-type   (sc/enum "notification" "application")}]
   :permissions      [{:required [:application/approve-foreman]}]
   :pre-checks       [(foreman/foreman-v3-state-is #{"declared" "submitted"})
                      foreman/foreman-doc-id-is-foreman-v3-document]}
  [{:keys [created user] :as command}]
  (let [updates {$set  {:documents.$.type  foreman-type
                        :documents.$.state (if (= foreman-type "application")
                                             "submitted"
                                             "declared")
                        :modified          created}
                 $push {:documents.$.changeLog {$each [{:field :type
                                                        :value foreman-type
                                                        :user  (usr/summary user)
                                                        :ts    created}
                                                       {:field :state
                                                        :value (if (= foreman-type "application")
                                                                 "submitted"
                                                                 "declared")
                                                        :user  (usr/summary user)
                                                        :ts    created}]}}}
        updates (if (= foreman-type "notification")
                  (assoc-in updates [$set :documents.$.started] created)
                  (assoc updates $unset {:documents.$.started true}))]
    (update-application command
                        {:documents {$elemMatch {:id   foreman-doc-id
                                                 :type {$ne foreman-type}}}}
      updates))
  (ok))

(defcommand set-foreman-submit-setting
  {:description      "Sets which settings are active for submitting foreman documents"
   :parameters       [:organizationId path]
   :permissions      [{:required [:organization/admin]}]
   :input-validators [{:organizationId ssc/NonBlankStr
                       :path           org/ForemanSubmitSettings
                       :enabled        sc/Bool}]}
  [command]
  (org-api/toggle-organization-flag command
                                    (util/kw-path :foreman-submit-settings
                                                  (ffirst (util/kw-flatten-map path)))))

(defquery get-foreman-submit-settings
  {:description      "Gets settings related to submitting foreman documents"
   :parameters       [:id]
   :input-validators [(partial action/non-blank-parameters [:id])]
   :permissions      [{:required [:application/read]}]}
  [command]
  (ok (foreman-app/with-submit-settings-defaults (-> command :organization force :foreman-submit-settings))))
