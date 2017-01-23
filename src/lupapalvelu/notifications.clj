(ns lupapalvelu.notifications
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error fatal]]
            [schema.core :as sc]
            [clojure.set :as set]
            [clojure.string :as s]
            [clostache.parser :as clostache]
            [sade.util :refer [future* to-local-date fn->]]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.email :as email]
            [lupapalvelu.i18n :refer [loc] :as i18n]
            [lupapalvelu.user :as usr]))

;;
;; Helpers
;;

(defn get-subpage-link [{:keys [id subpage-id]} subpage lang {role :role :or {role "applicant"}}]
  (assert (#{"applicant" "authority" "dummy"} role) (str "Unsupported role: " role))
  (assert (#{"attachment" "statement" "neighbors" "verdict"} subpage) (str "Unsupported subpage: " subpage))
  (let [full-path (ss/join "/" (remove nil? [subpage id subpage-id]))]
    (str (env/value :host) "/app/" lang "/" (usr/applicationpage-for role) "#!/" full-path)))

(defn get-application-link [{:keys [infoRequest id]} tab lang {role :role :or {role "applicant"}}]
  (assert (#{"applicant" "authority" "dummy"} role) (str "Unsupported role " role))
  (let [suffix (if (and (not (ss/blank? tab)) (not (ss/starts-with tab "/"))) (str "/" tab) tab)
        permit-type-path (if infoRequest "/inforequest" "/application")
        full-path        (str permit-type-path "/" id suffix)]
    (str (env/value :host) "/app/" lang "/" (usr/applicationpage-for role) "#!" full-path)))

(defn- ->to [{:keys [email firstName lastName]}]
  (letfn [(sanit [s] (s/replace s #"[<>]"  ""))]
    (if (or (ss/blank? firstName) (ss/blank? lastName))
      email
      (str (sanit firstName) " " (sanit lastName) " <" (sanit email) ">"))))

(defn- send-mail-to-recipient! [recipient subject msg]
  {:pre [(map? recipient) (:email recipient)]}
  (let [to (->to recipient)]
    (if (env/value :email :dummy)
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
  "Emails are sent to everyone in auth array except those who haven't accepted invite or have unsubscribed emails.
   More specific set recipients can be defined by user roles."
  [{:keys [auth]} included-roles excluded-roles]
  {:post [(every? map? %)]}
  (let [recipient-roles (set/difference (set (or (seq included-roles) auth/all-authz-roles))
                                        (set excluded-roles))]
    (->> (filter (comp recipient-roles keyword :role) auth)
         (remove :invite)
         (remove :unsubscribed)
         (remove (comp (partial = "company") :type))
         (map (comp usr/non-private usr/get-user-by-id :id)))))

;;
;; Model creation functions
;;

(defn create-app-model [{application :application} {tab :tab} recipient]
  {:link         #(get-application-link application tab % recipient)
   :state        #(i18n/localize % (name (:state application)))
   :modified     (to-local-date (:modified application))
   :address      (:address application)
   :municipality #(i18n/localize % "municipality" (:municipality application))
   :operation    #(app-utils/operation-description application %)})

;;
;; Recipient functions
;;

(defn- default-recipients-fn
  "Default recipient roles for notifications are all user roles but 'statementGiver'."
  [{application :application}]
  (get-email-recipients-for-application application nil [:statementGiver]))

(defn from-user [command] [(:user command)])

(defn from-data [{data :data}] (let [email (:email data)
                                     emails (if (sequential? email) email [email])]
                                 (map (fn [addr] {:email addr}) emails)))

(defn comment-recipients-fn
  "Recipients roles for comments are same user roles that can view and add comments."
  [{:keys [application]}]
  (get-email-recipients-for-application application auth/comment-user-authz-roles [:statementGiver]))


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
  #{"rest-api" "trusted-etl"})

 ; email template ids, which are sent regardless of current user state
(def always-sent-templates
  #{:invite-company-user :reset-password :neighbor})

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

(defn notify! [template-name command & [result]]
  {:pre [template-name (map? command) (template-name @mail-config)]}
  (let [conf (template-name @mail-config)]
    (when ((get conf :pred-fn (constantly true)) command)
      (let [application-fn (get conf :application-fn identity)
            application    (application-fn (:application command))
            command        (assoc command :application application)
            command        (assoc command :result result)
            recipients-fn  (get conf :recipients-fn default-recipients-fn)
            recipients     (remove (invalid-recipient? template-name) (recipients-fn command))
            model-fn       (get conf :model-fn create-app-model)
            template-file  (get conf :template (str (name template-name) ".md"))
            calendar-fn    (get conf :calendar-fn)
            conf           (assoc conf :tab ((get conf :tab-fn (constantly nil)) command))]
        (doseq [recipient recipients]
          (let [user-lang (:language recipient)
                model   (assoc (model-fn command conf recipient)
                          :lang (or user-lang "fi")
                          :user (select-keys recipient [:firstName :lastName :email :lang]))
                subject (get-email-subject application model (get conf :subject-key (name template-name)) (or (:language recipient) "fi"))
                calendar (when (some? calendar-fn)
                           (calendar-fn command recipient))
                msg     (email/apply-template template-file model)
                msg     (if (some? calendar)
                          (conj msg calendar)
                          msg)]
            (send-mail-to-recipient! recipient subject msg)))))))
