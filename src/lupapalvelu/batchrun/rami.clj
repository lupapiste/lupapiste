(ns lupapalvelu.batchrun.rami
  "Rami campaign batchrun. The batchrun enables Rami 2.0 for the target organizations and
  gives reporting access rights to the given users (and also creates users, if
  needed). The target users/organizations are given with a configuration EDN file.

  After the campaign the same batchrun can be used to rollback (most of) the changes done
  in the start step. However, created users are not removed. The rollback also takes a
  configuration file in the same format."
  (:require [lupapalvelu.html-email.core :as html-email]
            [lupapalvelu.ident.ad-login :as ad-login]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.password-reset :as pw-reset]
            [lupapalvelu.permissions :as perm]
            [lupapalvelu.system.util-components :as components]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer [now]]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(def ^:private start-text "LPK-7027 Rami 2.0 campaign start.")
(def ^:private end-text   "LPK-7027 Rami 2.0 campaign end.")

(sc/defschema ^:private EdnUser
  {:organization-id ssc/NonBlankStr
   :email           ssc/Email
   :firstname       ssc/NonBlankStr
   :lastname        ssc/NonBlankStr})

(sc/defschema ^:private User
  {:organization {:id   ssc/NonBlankStr
                  ;; Not in edn, queried from mongo (Finnish name).
                  :name ssc/NonBlankStr}
   :email        ssc/Email
   ;; Key names to follow `lupapalvelu.user` conventions.
   :firstName    ssc/NonBlankStr
   :lastName     ssc/NonBlankStr})

(sc/defn ^:private ^:always-validate read-configuration :- [User]
  [filename]
  (->> (util/read-edn-file filename)
       ss/trimwalk
       (group-by :organization-id)
       (mapcat (fn [[org-id xs]]
                 (let [s (-> (mongo/by-id :organizations org-id [:name])
                             :name :fi ss/trim)]
                   (for [m xs]
                     {:organization {:id   (:organization-id m)
                                     :name s}
                      :email        (:email m)
                      :firstName    (:firstname m)
                      :lastName     (:lastname m)}))))))

#_{:clj-kondo/ignore [:unused-private-var]}
(sc/defn ^:private ^:always-validate write-configuration
  [filename users :- [EdnUser]]
  (util/write-edn-file filename users))

(defn- make-note [note-text & extra-fields]
  (->> (apply hash-map extra-fields)
       util/strip-nils
       (merge {:note    note-text
               :created (now)})))

(defn- with-bookkeeping-note
  "Sheriff note with extra fields"
  [m note-text & extra-fields]
  (assoc-in m [$push :_sheriff-notes] (apply make-note note-text extra-fields)))

(defn- find-start-note [m]
  (some->> m :_sheriff-notes
           (util/find-by-key :note start-text)))

(defn- promote-reporting-system-for-organizations
  "Enables Rami 2.0 (`:bi-app2`) for given organizations. The old `reporting-system` value
  is stored as part of the sheriff note, if needed."
  [org-ids]
  (doseq [{:keys [reporting-system
                  id]} (mongo/select :organizations
                                     {:_id              {$in org-ids}
                                      :reporting-system {$ne :bi-app2}}
                                     [:reporting-system])]
    (mongo/update-by-id :organizations id
                        (with-bookkeeping-note
                          {$set {:reporting-system :bi-app2}}
                          start-text
                          :reporting-system reporting-system))))

(defn- rollback-reporting-system-for-organizations
  "Undo the reporting system promotion"
  [org-ids]
  (doseq [{id  :id
           :as org} (mongo/select :organizations
                                  {:_id                 {$in org-ids}
                                   :reporting-system    :bi-app2
                                   :_sheriff-notes.note start-text}
                                  [:_sheriff-notes])
          :let      [old-system (:reporting-system (find-start-note org))]]
    (mongo/update-by-id :organizations id
                        (with-bookkeeping-note
                          (if old-system
                            {$set {:reporting-system old-system}}
                            {$unset {:reporting-system true}})
                          end-text))))

(sc/defn ^:private ^:always-validate send-email
  ([system user :- User reset-link :- (sc/maybe ssc/HttpUrl)]
   (html-email/send-template-email {:to          user
                                    :template-id :rami-invite
                                    :lang        :fi}
                                   {:user          user
                                    :organization  (-> user :organization :name)
                                    :reset-link    reset-link
                                    :lupapiste-url (components/system->host system)})
   (log/infof "Invite %s user %s to Rami."
              (if reset-link "new" "old")
              (:email user)))
  ([system user] (send-email system user nil)))

(defn- new-reporter [system {{org-id :id} :organization email :email :as user}]
  (usr/create-new-user (usr/batchrun-user [org-id])
                       (merge
                         (select-keys user [:email :firstName :lastName])
                         {:_sheriff-notes [(make-note start-text
                                                      :organization-id org-id)]
                          :enabled        true
                          :role           "authority"
                          :oauth-roles    [{:client-type  "bi-app"
                                            :organization org-id
                                            :role         "reporter"}]}))
  (->> (pw-reset/make-reset-token email)
       (pw-reset/reset-link system "fi")
       (send-email system user)))

(defn- rami-oauth?
  ([{:keys [oauth-roles]} org-id role]
   (boolean (and org-id
                 (some->> oauth-roles
                          seq
                          (util/find-by-keys (util/assoc-when {:client-type  "bi-app"
                                                               :organization org-id}
                                                              :role role))))))
  ([user org-id] (rami-oauth? user org-id nil)))

(sc/defn ^:private ^:always-validate user->reporter
  [system {{org-id :id} :organization email :email :as user} :- User]
  (let [{user-id :id role :role
         :as     old-user} (usr/get-user-by-email email)]
    (cond
      ;; This works also for a non-existing user.
      (some-> (ad-login/user-ad-organization-ids :email email)
              (contains? org-id))
      (log/error "Reporting role must be given via AD.")

      (nil? old-user)
      (new-reporter system user)

      (not (#{"authority" "applicant"} role))
      (log/error "Unsupported role:" role)

      (not (:enabled old-user))
      (log/error  "User not enabled.")

      (contains? (perm/get-organization-permissions {:data {:organizationId org-id}
                                                     :user old-user})
                 :organization/reporter)
      (log/info "Already authed as an authority.")

      ;; Do not add reporter, if the user has _any_ reporting role.
      (rami-oauth? old-user org-id)
      (log/info "User already has a reporting role.")

      :else
      (do
        (mongo/update-by-id :users user-id
                            (with-bookkeeping-note
                              {$push {:oauth-roles {:client-type  :bi-app
                                                    :organization org-id
                                                    :role         :reporter}}}
                              start-text
                              :organization-id org-id))
        (send-email system user)))))

(sc/defn ^:private ^:always-validate rollback-users
  [emails :- [ssc/Email]]
  (doseq [{user-id :id email :email
           :as     u} (mongo/select :users
                                    {:email               {$in emails}
                                     :_sheriff-notes.note start-text}
                                    [:_sheriff-notes :email :oauth-roles])
          :let        [org-id (:organization-id (find-start-note u))]
          :when       (rami-oauth? u org-id "reporter")]
    (log/with-logging-context {:email email}
      (try
        (mongo/update-by-id :users user-id (with-bookkeeping-note
                                             {$pull {:oauth-roles {:organization org-id
                                                                   :client-type  :bi-app
                                                                   :role         :reporter}}}
                                             end-text))
        (log/infof "Campaign rollback for the user %s done." email)
        (catch Exception e
          (log/error e "User rollback failed."))))))

(defn- start-campaign [system org-ids users]
  (promote-reporting-system-for-organizations org-ids)
  (doseq [u users]
    (log/with-logging-context {:email (:email u)}
      (try
        (user->reporter system u)
        (catch Exception e
          (log/error e "Making user a reporter failed:" u))))))

(defn- end-campaign [_system org-ids users]
  (rollback-reporting-system-for-organizations org-ids)
  (rollback-users (map :email users)))

(defn campaign [system cmd configuration-filename]
  {:pre [(map? system)]}
  (log/with-logging-context {:userId                "batchrun-user"
                             :rami-campaign-command cmd}
    (try
      (let [cmd     (sc/validate (sc/enum :start :end) (keyword cmd))
            users   (read-configuration configuration-filename)
            org-ids (set (map (comp :id :organization) users))]
        ((case cmd
           :start start-campaign
           :end   end-campaign) system org-ids users))
      (catch Exception e
        (log/error e "Campaign command" cmd "failed.")
        (log/info "Usage: rami-campaign start|end configuration.edn")))))
