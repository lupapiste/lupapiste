(ns lupapalvelu.foreman-api
  (:require [taoensso.timbre :as timbre :refer [error]]
            [lupapalvelu.action :refer [defquery defcommand update-application] :as action]
            [lupapalvelu.application :as application]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.company :as company]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notif]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as user]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as v]
            [monger.operators :refer :all]))

(defn invites-to-auths [inv app-id inviter timestamp]
  (if (:company-id inv)
    (foreman/create-company-auth (:company-id inv))
    (when-let [invited (user/get-or-create-user-by-email (:email inv) inviter)]
      (auth/create-invite-auth inviter invited app-id (:role inv) timestamp))))

(defcommand create-foreman-application
  {:parameters [id taskId foremanRole foremanEmail]
   :user-roles #{:applicant :authority}
   :input-validators [(partial action/email-validator :foremanEmail)]
   :states states/all-application-states
   :pre-checks [application/validate-authority-in-drafts]}
  [{:keys [created user application] :as command}]
  (let [foreman-app          (foreman/new-foreman-application command)
        new-application-docs (foreman/create-foreman-docs application foreman-app foremanRole)
        foreman-app          (assoc foreman-app :documents new-application-docs)
        task                 (util/find-by-id taskId (:tasks application))

        foreman-user   (when (v/valid-email? foremanEmail) (user/get-or-create-user-by-email foremanEmail user))
        foreman-invite (when foreman-user
                         (auth/create-invite-auth user foreman-user (:id foreman-app) "foreman" created))
        invite-to-original? (and
                              foreman-user
                              (not (domain/has-auth? application (:id foreman-user))))

        applicant-invites (foreman/applicant-invites new-application-docs (:auth application))
        auths             (remove nil?
                                 (conj
                                   (map #(invites-to-auths % (:id foreman-app) user created) applicant-invites)
                                   foreman-invite))
        grouped-auths (group-by #(if (not= "company" (:type %))
                                   :other
                                   :company) auths)

        foreman-app (update-in foreman-app [:auth] (partial apply conj) auths)]

    (application/do-add-link-permit foreman-app (:id application))
    (application/insert-application foreman-app)

    (when task
      (let [updates [[[:asiointitunnus] (:id foreman-app)]]]
        (doc-persistence/persist-model-updates application "tasks" task updates created)))

    ; Send notifications for authed
    (try
      (when invite-to-original?
        (update-application command
          {:auth {$not {$elemMatch {:invite.user.username (:email foreman-user)}}}}
          {$push {:auth (auth/create-invite-auth user foreman-user (:id application) "foreman" created)}
           $set  {:modified created}})
        (notif/notify! :invite {:application application :recipients [foreman-user]}))

      (notif/notify! :invite {:application foreman-app :recipients (map :invite (:other grouped-auths))})
      (doseq [auth (:company grouped-auths)
              :let [company-id (-> auth :invite :user :id)
                    token-id (company/company-invitation-token user company-id (:id foreman-app))]]
        (notif/notify! :accept-company-invitation {:admins     (company/find-company-admins company-id)
                                                   :caller     user
                                                   :link-fi    (str (env/value :host) "/app/fi/welcome#!/accept-company-invitation/" token-id)
                                                   :link-sv    (str (env/value :host) "/app/sv/welcome#!/accept-company-invitation/" token-id)}))
      (catch Exception e
        (error "Error when inviting to foreman application:" e)))

    (ok :id (:id foreman-app) :auth (:auth foreman-app))))

(defcommand update-foreman-other-applications
  {:user-roles #{:applicant :authority}
   :states states/all-states
   :parameters [:id foremanHetu]
   :pre-checks [application/validate-authority-in-drafts]}
  [{application :application user :user :as command}]
  (when-let [foreman-applications (seq (foreman/get-foreman-project-applications application foremanHetu))]
    (let [other-applications (map #(foreman/other-project-document % (:created command)) foreman-applications)
          tyonjohtaja-doc (update-in (domain/get-document-by-name application "tyonjohtaja-v2") [:data :muutHankkeet] 
                                     (fn [muut-hankkeet]                      
                                       (->> (vals muut-hankkeet)
                                            (remove #(get-in % [:autoupdated :value]))
                                            (concat other-applications)
                                            (map vector (map (comp keyword str) (range)))
                                            (into {}))))
          documents (map (fn [doc] (if (= (:id doc) (:id tyonjohtaja-doc)) tyonjohtaja-doc doc)) (:documents application))]
      (update-application command {$set {:documents documents}}))
    (ok)))

(defcommand link-foreman-task
  {:user-roles #{:applicant :authority}
   :states states/all-states
   :parameters [id taskId foremanAppId]
   :pre-checks [application/validate-authority-in-drafts]}
  [{:keys [created application] :as command}]
  (let [task (util/find-by-id taskId (:tasks application))]
    (if task
      (let [updates [[[:asiointitunnus] foremanAppId]]]
        (doc-persistence/persist-model-updates application "tasks" task updates created))
      (fail :error.not-found))))

(defn foreman-app-check [_ application]
  (when-not (foreman/foreman-app? application)
    (fail :error.not-foreman-app)))

(defquery foreman-history
  {:user-roles #{:authority}
   :states           states/all-states
   :user-authz-roles action/all-authz-roles
   :org-authz-roles  action/reader-org-authz-roles
   :parameters       [:id]
   :pre-checks       [foreman-app-check]}
  [{application :application user :user :as command}]
  (if application
    (ok :projects (foreman/get-foreman-history-data application))
    (fail :error.not-found)))

(defquery reduced-foreman-history
  {:user-roles #{:authority}
   :states           states/all-states
   :user-authz-roles action/all-authz-roles
   :org-authz-roles  action/reader-org-authz-roles
   :parameters       [:id]
   :pre-checks       [foreman-app-check]}
  [{application :application user :user :as command}]
  (if application
    (ok :projects (foreman/get-foreman-reduced-history-data application))
    (fail :error.not-found)))

(defquery foreman-applications
  {:user-roles #{:applicant :authority :oirAuthority}
   :states           states/all-states
   :user-authz-roles action/all-authz-roles
   :org-authz-roles  action/reader-org-authz-roles
   :parameters       [id]}
  [{application :application user :user :as command}]
  (let [app-link-resp (mongo/select :app-links {:link {$in [id]}})
        apps-linking-to-us (filter #(= (:type ((keyword id) %)) "linkpermit") app-link-resp)
        foreman-application-links (filter #(= (:apptype (first (:link %)) "tyonjohtajan-nimeaminen")) apps-linking-to-us)
        foreman-application-ids (map (fn [link] (first (:link link))) foreman-application-links)
        applications (mongo/select :applications {:_id {$in foreman-application-ids}})
        mapped-applications (map (fn [app] (foreman/foreman-application-info app)) applications)]
    (ok :applications (sort-by :id mapped-applications))))
