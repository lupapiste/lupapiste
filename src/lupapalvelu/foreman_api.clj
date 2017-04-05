(ns lupapalvelu.foreman-api
  (:require [taoensso.timbre :refer [error]]
            [lupapalvelu.action :refer [defquery defcommand update-application] :as action]
            [lupapalvelu.application :as application]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as user]
            [sade.core :refer :all]
            [sade.util :as util]
            [sade.validators :as v]
            [monger.operators :refer :all]))

(defn foreman-app-check [{application :application}]
  (when-not (foreman/foreman-app? application)
    (fail :error.not-foreman-app)))

(defcommand create-foreman-application
  {:parameters [id taskId foremanRole foremanEmail]
   :user-roles #{:applicant :authority}
   :input-validators [(partial action/email-validator :foremanEmail)]
   :states (states/all-application-states-but states/terminal-states)
   :pre-checks [application/validate-authority-in-drafts
                application/validate-only-authority-before-verdict-given]
   :notified   true
   :description "Creates foreman application based on current application. Foreman email can be nil."}
  [{:keys [created user application] :as command}]
  (let [foreman-user   (when (v/valid-email? foremanEmail)
                         (user/get-or-create-user-by-email foremanEmail user))
        foreman-app    (-> (foreman/new-foreman-application command)
                           (foreman/update-foreman-docs application foremanRole)
                           (foreman/copy-auths-from-linked-app foreman-user application user created)
                           (foreman/add-foreman-invite-auth foreman-user user created))]
    (application/do-add-link-permit foreman-app (:id application))
    (application/insert-application foreman-app)
    (foreman/update-foreman-task-on-linked-app! application foreman-app taskId command)
    (foreman/send-invite-notifications! foreman-app foreman-user application command)
    (ok :id (:id foreman-app) :auth (:auth foreman-app))))

(defcommand update-foreman-other-applications
  {:user-roles #{:applicant :authority}
   :user-authz-roles (conj roles/default-authz-writer-roles :foreman)
   :states     states/all-states
   :parameters [:id foremanHetu]
   :input-validators [(partial action/string-parameters [:foremanHetu])]
   :pre-checks [foreman-app-check
                application/validate-authority-in-drafts]}
  [{:keys [application user created] :as command}]
  (let [foreman-applications (seq (foreman/get-foreman-project-applications application foremanHetu))
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
  {:user-roles #{:applicant :authority}
   :states states/all-states
   :parameters [id taskId foremanAppId]
   :input-validators [(partial action/non-blank-parameters [:id :taskId])]
   :pre-checks [application/validate-authority-in-drafts
                foreman/ensure-foreman-not-linked]}
  [{:keys [created application] :as command}]
  (let [task (util/find-by-id taskId (:tasks application))]
    (if task
      (let [updates [[[:asiointitunnus] foremanAppId]]]
        (doc-persistence/persist-model-updates application "tasks" task updates created))
      (fail :error.task-not-found))))

(defquery foreman-history
  {:description      "Foreman application target history in Lupapiste. If
   all parameter is true, then the whole history is returned otherwise
   just non-redundant highlights."
   :user-roles       #{:authority}
   :states           states/all-states
   :user-authz-roles roles/all-authz-roles
   :org-authz-roles  roles/reader-org-authz-roles
   :parameters       [:id all]
   :input-validators [(partial action/non-blank-parameters [:all])]
   :pre-checks       [foreman-app-check]}
  [{application :application user :user :as command}]
  (if application
    (let [history-data (foreman/get-foreman-history-data application)]
      (if (= all "true")
        (ok :projects history-data :all true)
        (let [reduced-data (foreman/reduce-foreman-history-data history-data)]
          (ok :projects reduced-data
              :all (= (count history-data) (count reduced-data))))))
    (fail :error.application-not-found)))

(defquery foreman-applications
  {:user-roles #{:applicant :authority :oirAuthority}
   :states           states/all-states
   :user-authz-roles roles/all-authz-roles
   :org-authz-roles  roles/reader-org-authz-roles
   :parameters       [id]}
  [{application :application user :user :as command}]
  (->> (foreman/get-linked-foreman-applications application)
       (sort-by :id)
       (ok :applications)))
