(ns lupapalvelu.notifications
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error fatal]]
            [schema.core :as sc]
            [clojure.set :as set]
            [clojure.string :as s]
            [sade.util :refer [future* to-local-date fn->]]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.email :as email]
            [sade.util :as util]
            [lupapalvelu.i18n :refer [loc] :as i18n]
            [lupapalvelu.user :as u]
            [lupapalvelu.authorization :as auth]))

;;
;; Helpers
;;

(defn get-subpage-link [{:keys [id subpage-id]} subpage lang {role :role :or {role "applicant"}}]
  (assert (#{"applicant" "authority" "dummy"} role) (str "Unsupported role " role))
  (assert (#{"attachment" "statement" "neighbors"} subpage) (str "Unsupported subpage"))
  (let [full-path (ss/join "/" (remove nil? [subpage id subpage-id]))]
    (str (env/value :host) "/app/" lang "/" (u/applicationpage-for role) "#!/" full-path)))

(defn get-application-link [{:keys [infoRequest id]} tab lang {role :role :or {role "applicant"}}]
  (assert (#{"applicant" "authority" "dummy"} role) (str "Unsupported role " role))
  (let [suffix (if (and (not (ss/blank? tab)) (not (ss/starts-with tab "/"))) (str "/" tab) tab)
        permit-type-path (if infoRequest "/inforequest" "/application")
        full-path        (str permit-type-path "/" id suffix)]
    (str (env/value :host) "/app/" lang "/" (u/applicationpage-for role) "#!" full-path)))

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



(defn- get-email-subject [{title :title, municipality :municipality} lang
                          & [subject-key show-municipality-in-subject]]
  (let [title-postfix (when subject-key
                        (i18n/localize-fallback lang [["email.title" subject-key] subject-key]))
        title-begin   (str (when show-municipality-in-subject
                             (str (i18n/localize lang "municipality" municipality) ", ")) title)]
    (str "Lupapiste: " title-begin
         (when (and title subject-key) " - ")
         (when subject-key title-postfix))))

(defn- get-email-recipients-for-application
  "Emails are sent to everyone in auth array except those who haven't accepted invite or have unsubscribed emails.
   More specific set recipients can be defined by user roles."
  [{:keys [auth statements]} included-roles excluded-roles]
  {:post [every? map? %]}
  (let [recipient-roles (set/difference (set (or (seq included-roles) auth/all-authz-roles))
                                        (set excluded-roles))]
    (->> (filter (comp recipient-roles keyword :role) auth)
         (remove :invite)
         (remove :unsubscribed)
         (map (comp u/non-private u/get-user-by-id :id)))))

;;
;; Model creation functions
;;

;; Application (the default)
(defn create-app-model [{application :application} {tab :tab} recipient]
  {:link-fi (get-application-link application tab "fi" recipient)
   :link-sv (get-application-link application tab "sv" recipient)
   :state-fi (i18n/localize :fi (name (:state application)))
   :state-sv (i18n/localize :sv (name (:state application)))
   :modified (to-local-date (:modified application))
   :name (:firstName recipient)
   :lang (:language recipient)})

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
            (sc/optional-key :tab)             sc/Str
            (sc/optional-key :show-municipality-in-subject) sc/Bool})

;;
;; Public API
;;

(defn defemail [template-name m]
  {:pre [(keyword? template-name) (sc/validate Email m)]}
  (swap! mail-config assoc template-name m))

(def non-notified-roles
  #{"rest-api" "trusted-etl"})

; template types which are always sent regardless of user state
(def always-sent? 
  (let [special-cases (set [:invite-company-user :reset-password])]
    (fn [template-name] (get special-cases template-name))))

(defn invalid-recipient? [for-template]
   "Notifications are not sent to certain roles, users who do not 
    have a valid email address, and registered but removed users 
    receive only specific message types defined above."
  (fn [rec]
     (or (ss/blank? (:email rec))
        (if (always-sent? for-template)
          false
          (not (u/email-recipient? rec)))
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
            calendar-fn    (get conf :calendar-fn)]
        (doseq [recipient recipients]
          (let [model   (model-fn command conf recipient)
                subject (get-email-subject application
                                           (:language recipient)
                                           (get conf :subject-key (name template-name))
                                           (get conf :show-municipality-in-subject false))
                calendar (when (some? calendar-fn)
                           (calendar-fn command recipient))
                msg     (email/apply-template template-file model)
                msg     (if (some? calendar)
                          (conj msg calendar)
                          msg)]
            (send-mail-to-recipient! recipient subject msg)))))))
