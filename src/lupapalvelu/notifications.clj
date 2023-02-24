(ns lupapalvelu.notifications
  (:require [cljstache.core :as clostache]
            [clojure.set :as set]
            [clojure.string :as s]
            [hiccup.util :refer [escape-html]]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.email :as email]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util :refer [future* contains-value?]]
            [schema.core :as sc]
            [taoensso.timbre :refer [info error]]))

;;
;; Helpers
;;

(defn get-subpage-link [{:keys [id subpage-id]} subpage lang recipient]
  (assert (#{"attachment" "statement" "neighbors" "verdict"} subpage) (str "Unsupported subpage: " subpage))
  (let [full-path (ss/join "/" (remove nil? [subpage id subpage-id]))]
    (str (env/value :host) "/app/" lang "/" (usr/applicationpage-for recipient) "#!/" full-path)))

(defn get-application-link [{:keys [infoRequest id]} tab lang recipient]
  (let [suffix            (if (and (not (ss/blank? tab)) (not (ss/starts-with tab "/"))) (str "/" tab) tab)
        permit-type-path  (if infoRequest "/inforequest" "/application")
        full-path         (str permit-type-path "/" id suffix)]
    (str (env/value :host) "/app/" lang "/" (usr/applicationpage-for recipient) "#!" full-path)))

(defn- ->to [{:keys [email firstName lastName]}]
  (letfn [(sanit [s] (s/replace s #"[<>,]"  ""))]
    (if (or (ss/blank? firstName) (ss/blank? lastName))
      email
      (str (sanit firstName) " " (sanit lastName) " <" (sanit email) ">"))))

(defn send-mail-to-recipient!
  "Note that recipient can include :firstName and :lastName but they are not required"
  [recipient subject msg]
  {:pre [(map? recipient) (string? (:email recipient))]}
  (let [to (->to recipient)]
    (if (env/value :email :dummy-server)
     (email/send-email-message to subject msg)
     (future*
       (if (email/send-email-message to subject msg)
         (error "email could not be delivered." to subject msg)
         (info "email was sent successfully." to subject)))))
  nil)




(defn- get-email-subject
  "Renders an email subject with clostache against given model context.
   Subject-key defines localization key to be used. Localization value can hold placeholders,
   such as {{municipality}}"
  [application model subject-key lang]
  (let [subject (i18n/localize-fallback lang [["email.title" subject-key] subject-key])
        context (merge (select-keys application [:address :title :state :municipality]) model)
        subject-text (if (ss/blank? subject)
                       (str (or (:address application) (:title application))) ; fallback
                       (clostache/render subject (email/prepare-context-for-language lang context)))]
    (str "Lupapiste: " subject-text)))

(defn- get-email-recipients-for-application
  "Emails are sent to everyone in auth array except those who haven't
  accepted invite or have unsubscribed emails. More specific set
  recipients can be defined by user roles. For company auths the
  recipients include every admin user. Locked companies are also
  included."
  [{:keys [auth]} included-roles excluded-roles]
  {:post [(every? map? %)]}
  (let [recipient-roles            (set/difference (set (or (seq included-roles) roles/all-authz-roles))
                                                   (set excluded-roles))
        {:keys [companies others]} (->> (filter (comp recipient-roles keyword :role) auth)
                                        (remove :invite)
                                        (remove :unsubscribed)
                                        (group-by #(if (util/=as-kw (:type %) :company)
                                                     :companies
                                                     :others))
                                        (reduce-kv (fn [acc k v]
                                                     (assoc acc k (map :id v)))
                                                   {}))]
    (usr/get-users (if (seq companies)
                     {$or [{:company.id   {$in (or companies [])}
                           :company.role :admin}
                           {:_id {$in (or others [])}}]}
                     {:_id {$in (or others [])}}))))

;;
;; Model creation functions
;;

(defn create-app-model [{application :application} {tab :tab} recipient]
  {:link         #(get-application-link application tab % recipient)
   :state        #(i18n/localize % (name (:state application)))
   :modified     (date/finnish-date (:modified application) :zero-pad)
   :address      (:address application)
   :municipality #(i18n/localize % "municipality" (:municipality application))
   :operation    #(app-utils/operation-description application %)})

;;
;; Recipient functions
;;

(defn- default-recipients-fn
  "Default recipient roles for notifications are all user roles but 'statementGiver'."
  [{application :application}]
  (get-email-recipients-for-application application nil [:statementGiver :financialAuthority]))

(defn from-user [command] [(:user command)])

(defn from-data [{data :data}] (let [email (:email data)
                                     emails (if (sequential? email) email [email])]
                                 (map (fn [addr] {:email addr}) emails)))

(defn comment-recipients-fn
  "Recipients roles for comments are same user roles that can view and add comments."
  [{:keys [application]}]
  (get-email-recipients-for-application application roles/comment-user-authz-roles [:statementGiver :financialAuthority]))

(defn application-state-reminder-recipients-fn [{:keys [application]}]
  (get-email-recipients-for-application application #{:writer} #{}))

;;
;; Configuration for generic notifications
;;

(defonce ^:private mail-config (atom {}))

(def Email {(sc/optional-key :template)        sc/Str
            (sc/optional-key :subject-key)     sc/Str
            (sc/optional-key :calendar-fn)     util/Fn

            ; Recipients function takes command map as parameter and
            ; returs a sequence of recipients.
            ; Each recipient is a map that must contain :email key,
            ; optionally :firstName and :lastName keys (as users do).
            (sc/optional-key :recipients-fn)   util/IFn

            ; Model function takes 3 parameters: command map, configuration map given to defemail and recipient.
            ; It must return a map, that will merged to email template.
            (sc/optional-key :model-fn)        util/Fn

            (sc/optional-key :pred-fn)         util/Fn
            (sc/optional-key :application-fn)  util/IFn
            (sc/optional-key :tab-fn)          util/IFn})

;;
;; Public API
;;

(defn defemail [template-name m]
  {:pre [(keyword? template-name) (sc/validate Email m)]}
  (swap! mail-config assoc template-name m))

;; roles which do not receive email notifications
(def non-notified-roles
  #{"rest-api" "trusted-etl" "salesforce-etl" "docstore-api" "onkalo-api"})

 ; email template ids, which are sent regardless of current user state
(def always-sent-templates
  #{:invite-company-user :reset-password :neighbor :foreman-termination})

(defn invalid-recipient?
  "Notifications are not sent to certain roles, users who do not
   have a valid email address, and registered but removed users
   receive only specific message types defined above."
  [for-template]
  (fn [rec]
     (or (ss/blank? (:email rec))
        (if (contains? always-sent-templates for-template)
          false
          (not (usr/email-recipient? rec)))
        (contains? non-notified-roles (:role rec)))))

(defn mark-mail-as-sent!
  "Adds the sent mail's template id to the list of the application's sent authority admin authored emails.
   Note that this means that a given email is only sent once in the application's lifespan;
   if there are multiple states specified in the template, the email is sent only when the application first enters one
   of those states."
  [application-id template-id]
  (mongo/update-by-id :applications
                      application-id
                      {$push {:automatic-emails-sent template-id}}))

(defn authority-admin-notify!
  "Send the authority admin authored emails on application-state-change to chosen parties.
   The authority admined notes are sent as plaintext (for now).
   The sent email's template id is stored on the application so the same email is not sent more than once.
   Note that lupapiste-notify! handles the static emails generated as part of the Lupapiste experience."
  [command]
  (let [organization (if (:organization command) ; ah-verdicts don't provide the organization
                       @(:organization command)
                       (mongo/by-id :organizations (get-in command [:application :organization])))]
    (when (:automatic-emails-enabled organization)
      (let [application-id  (get-in command [:application :id])
            application     (mongo/by-id :applications ; (:application command) is already outdated by this point
                                         application-id
                                         [:primaryOperation.name :state :documents.schema-info :documents.data
                                          :automatic-emails-sent])
            app-state       (get application :state)
            app-sent        (get application :automatic-emails-sent)
            app-operation   (get-in application [:primaryOperation :name])
            app-parties     (app-utils/get-application-email-parties application)
            add-parties     (fn [template] (->> app-parties
                                                (filter #(contains-value? (:parties template) (:category %)))
                                                (filter #(ss/not-blank? (:email %)))
                                                (assoc template :recipients)))]
        (->> organization
             :automatic-email-templates
             (filter #(and (contains-value? (:states %) app-state)
                           (contains-value? (:operations %) app-operation)
                           (not (contains-value? app-sent (:id %)))))
             (map add-parties)
             (remove #(empty? (:recipients %)))
             (mapv #(do (doseq [recipient (:recipients %)]
                          ;; 1st contents is plaintext, 2nd is HTML
                          (send-mail-to-recipient! recipient (:title %)
                                                   [(:contents %)
                                                    (-> (:contents %)
                                                        (escape-html)
                                                        (s/replace #"\n" "<br>"))]))
                        (mark-mail-as-sent! application-id (:id %)))))))))

(defn lupapiste-notify!
  "Sends the static emails generated by Lupapiste.
   The email configurations are generated with `defemail`.
   Note that authority-admin-notify! handles the automatic emails authored by the authority admin."
  [template-name conf command result]
  (let [application-fn (get conf :application-fn identity)
        application    (application-fn (:application command))
        command        (assoc command :application application :result result)
        recipients-fn  (get conf :recipients-fn default-recipients-fn)
        recipients     (remove (invalid-recipient? template-name) (recipients-fn command))
        model-fn       (get conf :model-fn create-app-model)
        template-file  (get conf :template (str (name template-name) ".md"))
        calendar-fn    (get conf :calendar-fn)
        conf           (assoc conf :tab ((get conf :tab-fn (constantly nil)) command))]
    (doseq [recipient recipients]
      (let [user-lang (:language recipient)
            model     (assoc (model-fn command conf recipient)
                        :lang user-lang
                        :user (select-keys recipient [:firstName :lastName :email :language]))
            subject   (get-email-subject application model (get conf :subject-key (name template-name)) (or user-lang "fi"))
            calendar  (when (some? calendar-fn) (calendar-fn command recipient))
            msg       (email/apply-template template-file model)
            msg       (if (some? calendar)
                        (conj msg calendar)
                        msg)]
        (send-mail-to-recipient! recipient subject msg)))))

(defn notify!
  "Sends emails to appropriate people on application state change, invite, password reset, etc.
   There are two types of emails sent:
   1) The static templates used by Lupapiste for e.g. password changes, verdict notifications, etc.
   2) The freeform emails authored by authority admins triggered by application state changes (LPK-4242).

   Only this function should ever be called any time an email must be sent.
   The email-event is the same as the template-name for the appropriate static email configuration."
  [email-event command & [result]]
  {:pre [(keyword? email-event) (map? command) (get @mail-config email-event)]}
  (let [conf (get @mail-config email-event)]
    (when ((get conf :pred-fn (constantly true)) (assoc command :result result))
      (lupapiste-notify! email-event conf command result))
    (when (= :application-state-change email-event)
      (authority-admin-notify! command))))
