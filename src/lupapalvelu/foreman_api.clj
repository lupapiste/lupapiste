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
            [lupapalvelu.company :as company]
            [sade.core :refer :all]
            [sade.util :as util]
            [sade.validators :as v]
            [monger.operators :refer :all]))

(defn foreman-app-check [{application :application}]
  (when-not (foreman/foreman-app? application)
    (fail :error.not-foreman-app)))

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
  {:parameters [:id foremanHetu]
   :input-validators [(partial action/string-parameters [:foremanHetu])]
   :contexts    [foreman/foreman-app-context]
   :permissions [{:context  {:application {:state #{:draft}}}
                  :required [:application/edit-draft]}
                 {:required [:application/edit]}]
   :states     states/all-states
   :pre-checks [foreman-app-check]}
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
  {:parameters [id taskId foremanAppId]
   :input-validators [(partial action/non-blank-parameters [:id :taskId])]
   :permissions [{:context  {:application {:state #{:draft}}}
                  :required [:application/edit-draft]}
                 {:required [:application/edit]}]
   :states states/all-states
   :pre-checks [foreman/ensure-foreman-not-linked]}
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
   :states           states/all-states
   :parameters       [:id all]
   :input-validators [(partial action/non-blank-parameters [:all])]
   :permissions      [{:required [:application/show-foreman-history]}]
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
  {:states           states/all-states
   :permissions      [{:required [:application/read]}]
   :parameters       [id]}
  [{application :application user :user :as command}]
  (->> (foreman/get-linked-foreman-applications application)
       (sort-by :id)
       (ok :applications)))
