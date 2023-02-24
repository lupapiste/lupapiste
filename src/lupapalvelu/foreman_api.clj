(ns lupapalvelu.foreman-api
  (:require [lupapalvelu.action :refer [defquery defcommand update-application] :as action]
            [lupapalvelu.application :as application]
            [lupapalvelu.backing-system.core :as bs]
            [lupapalvelu.company :as company]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.organization :as org]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as user]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.util :as util]
            [sade.validators :as v]
            [slingshot.slingshot :refer [try+]]
            [taoensso.timbre :refer [errorf]]))

(defn foreman-app-check [{application :application}]
  (when-not (foreman/foreman-app? application)
    (fail :error.not-foreman-app)))

(defn send-foreman-termination-msgs!
  "Sends emails and KuntaGML messages related to the termination.
   Has to be in the API namespace due to cyclic dependencies."
  [{:keys [application organization] :as command} foreman-app confirmation? lang]
  ;; Send emails
  (notifications/notify! :foreman-termination {:application application
                                               :foreman-app foreman-app})
  ;; Send KRYSP
  (let [permit-type (-> foreman-app :permitType keyword)]
    (when (and (boolean (:foreman-termination-krysp-enabled @organization))
               (org/krysp-write-integration? @organization permit-type))
      (try+ (bs/terminate-foreman! command (name permit-type) foreman-app confirmation? (name lang))
            (catch [:ok false] _ (errorf "KuntaGML failed for foreman termination %s" (:id foreman-app)))))))

(defcommand create-foreman-application
  {:parameters [id taskId foremanRole foremanEmail]
   :input-validators [(partial action/email-validator :foremanEmail)]
   :permissions [{:context  {:application {:state states/pre-verdict-states}}
                  :required [:application/create-foreman-app-in-pre-verdict-states]}
                 {:required [:application/create-foreman-app-in-post-verdict-states]}]
   :states (apply states/all-application-states-but :draft states/terminal-states)
   :notified   true
   :description "Creates foreman application based on current application. Foreman email can be nil."}
  [{:keys [created user application] :as command}]
  (when (->> (get-in (user/get-user-by-email foremanEmail) [:company :id])
             (company/company-denies-invitations? application))
    (fail! :invite.company-denies-invitation))
  (let [foreman-user   (when (v/valid-email? foremanEmail)
                         (user/get-or-create-user-by-email foremanEmail user))
        foreman-app    (-> (foreman/new-foreman-application command)
                           (foreman/update-foreman-docs application foremanRole)
                           (foreman/copy-auths-from-linked-app application user created)
                           (foreman/add-foreman-invite-auth foreman-user user created))]
    (application/do-add-link-permit foreman-app (:id application))
    (application/insert-application foreman-app)
    (foreman/update-foreman-task-on-linked-app! application foreman-app taskId command)
    (foreman/send-invite-notifications! foreman-app foreman-user application command)
    (ok :id (:id foreman-app) :auth (:auth foreman-app))))

(defcommand update-foreman-other-applications
  {:parameters [:id]
   :contexts    [foreman/foreman-app-context]
   :permissions [{:context  {:application {:state #{:draft}}}
                  :required [:application/edit-draft]}
                 {:required [:application/edit]}]
   :states     states/all-states
   :pre-checks [foreman-app-check]}
  [{:keys [application created] :as command}]
  (let [foreman-applications (seq (foreman/get-foreman-project-applications application))
        other-applications (map #(foreman/other-project-document % created) foreman-applications)
        tyonjohtaja-doc (domain/get-document-by-name application "tyonjohtaja-v2")
        muut-hankkeet (get-in tyonjohtaja-doc [:data :muutHankkeet])
        muut-hankkeet-new (->> (vals muut-hankkeet)
                               (remove #(get-in % [:autoupdated :value]))
                               (concat other-applications)
                               (zipmap (map str (range))))]
    (if tyonjohtaja-doc
      (do
        (update-application command {:documents {$elemMatch {:id (:id tyonjohtaja-doc)}}} {$set {:documents.$.data.muutHankkeet muut-hankkeet-new}})
        (ok))
      (fail :error.document-not-found))))

(defcommand link-foreman-task
  {:parameters [id taskId foremanAppId]
   :input-validators [(partial action/non-blank-parameters [:id :taskId])]
   :permissions [{:context  {:application {:state #{:draft}}}
                  :required [:application/edit-draft]}
                 {:required [:application/edit]}]
   :states states/all-states
   :pre-checks [foreman/ensure-foreman-not-linked]}
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
   :parameters       [:id all]
   :input-validators [(partial action/non-blank-parameters [:all])]
   :permissions      [{:required [:application/show-foreman-history]}]
   :pre-checks       [foreman-app-check]}
  [{:keys [application]}]
  (if application
    (let [history-data (foreman/get-foreman-history-data application)]
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
   :parameters  [id]}
  [{:keys [user application]}]
  (->> (foreman/get-linked-foreman-applications application)
       (foreman/add-required-roles-to-foreman-info application)
       (foreman/filter-termination-reason user)
       (ok :applications)))

(defcommand request-foreman-termination
  {:description      "The applicant or a foreman can request the termination of the foreman.
   This has to then be confirmed by an authority with the command confirm-foreman-termination.
   Foreman email is used to identify if the user is the given foreman without a database query in the pre-check."
   :states           states/post-verdict-states
   :parameters       [id foreman-app-id foreman-email reason]
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

(defcommand confirm-foreman-termination
  {:description       "An authority can confirm the termination of a foreman once it has been requested by somebody."
   :states            states/post-verdict-states
   :parameters        [id foreman-app-id]
   :input-validators  [(partial action/non-blank-parameters [:id :foreman-app-id])]
   :permissions       [{:required [:application/confirm-foreman-termination]}]
   :pre-checks        [foreman/user-is-authority-for-foreman-app]}
  [{:keys [lang] :as command}]
  (let [foreman-app (mongo/by-id :applications foreman-app-id)]
    (foreman/commit-foreman-termination-changes command
                                                foreman-app
                                                (foreman/confirm-foreman-termination command foreman-app true))
    (foreman/remove-foreman-assignment-target command foreman-app-id)
    (send-foreman-termination-msgs! command foreman-app true lang)
    (ok)))

(defcommand terminate-foreman
  {:description       "An authority can terminate a foreman by skipping the request phase altogether."
   :states            states/post-verdict-states
   :parameters        [id foreman-app-id reason]
   :input-validators  [(partial action/non-blank-parameters [:id :foreman-app-id])]
   :permissions       [{:required [:application/confirm-foreman-termination]}]
   :pre-checks        [foreman/user-is-authority-for-foreman-app]}
  [{:keys [lang] :as command}]
  (let [foreman-app (mongo/by-id :applications foreman-app-id)]
    (foreman/commit-foreman-termination-changes command
                                                foreman-app
                                                (foreman/terminate-foreman command foreman-app reason))
    (send-foreman-termination-msgs! command foreman-app false lang)
    (ok)))

(defcommand remove-foreman-from-list
  {:description      "An authority can remove a rejected foreman from the list of foremen and requirements"
   :states           states/post-verdict-states
   :parameters       [id foreman-app-id]
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
