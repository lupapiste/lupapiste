(ns lupapalvelu.notifications
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error fatal]]
            [clojure.set :as set]
            [lupapalvelu.i18n :refer [loc] :as i18n]
            [sade.util :refer [future*]]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.email :as email]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]))

;;
;; Helpers
;;

(defn- get-application-link [{:keys [infoRequest id]} suffix lang & [host]]
  (let [permit-type-path (if infoRequest "/inforequest" "/application")
        full-path        (str permit-type-path "/" id suffix)]
    (str (or host (env/value :host)) "/app/" lang "/applicant?hashbang=!" full-path "#!" full-path)))

(defn- send-mail-to-recipients! [recipients subject msg]
  (future*
    (doseq [recipient recipients]
      (if (email/send-email-message recipient subject msg)
        (error "email could not be delivered." recipient subject msg)
        (info "email was sent successfully." recipient subject))))
  nil)

(defn- get-email-subject [{title :title} & [title-key]]
  (let [title-postfix (when title-key (if (i18n/has-term? "fi" "email.title" title-key)
                                        (i18n/localize "fi" "email.title" title-key)
                                        (i18n/localize "fi" title-key)))]
    (str "Lupapiste.fi: " title (when title " - ") (when title-key title-postfix))))

(defn- url-to [to]
  (str (env/value :host) (when-not (ss/starts-with to "/") "/") to))

; emails are sent to everyone in auth array except statement persons
(defn- get-email-recipients-for-application [{:keys [auth statements]} included-roles excluded-roles]
  (let [included-users   (if (seq included-roles)
                           (filter (fn [user] (some #(= (:role user) %) included-roles)) auth)
                           auth)
        auth-user-emails (->> included-users
                           (filter (fn [user] (not (some #(= (:role user) %) excluded-roles))))
                           (map #(:email (mongo/by-id :users (:id %) {:email 1}))))]
    (if (some #(= "statementGiver" %) excluded-roles)
      (set/difference
        (set auth-user-emails)
        (map #(-> % :person :email) statements))
      auth-user-emails)))

(defn- create-app-model [application tab]
  {:link-fi (get-application-link application tab "fi")
   :link-sv (get-application-link application tab "sv")
   :state-fi (i18n/localize :fi (str (:state application)))
   :state-sv (i18n/localize :sv (str (:state application)))})

;;
;; Statement person
;;

(defn- statement-person-model [{{:keys [text organization]} :data}]
  {:text text
   :organization-fi (:fi (:name organization))
   :organization-sv (:sv (:name organization))})

;;
;; Neighbor
;;

(defn- neighbor-invite-model [{{token :token neighbor-id :neighborId} :data {:keys [id address municipality neighbors]} :application}]
  (letfn [(link-fn [lang] (str (env/value :host) "/app/" (name lang) "/neighbor/" id "/" neighbor-id "/" token))]
    {:name    (get-in neighbors [(keyword neighbor-id) :neighbor :owner :name])
     :address address
     :city-fi (i18n/localize :fi "municipality" municipality)
     :city-sv (i18n/localize :sv "municipality" municipality)
     :link-fi (link-fn :fi)
     :link-sv (link-fn :sv)}))

;;
;; Open Inforequest
;;

(defn- get-message-for-open-inforequest-invite [token & [host]]
  (let  [link-fn (fn [lang] (str (or host (env/value :host)) "/api/raw/openinforequest?token-id=" token "&lang=" (name lang)))
         info-fn (fn [lang] (env/value :oir :wanna-join-url))]
    (email/apply-template "open-inforequest-invite.html"
      {:link-fi (link-fn :fi)
       :link-sv (link-fn :sv)
       :info-fi (info-fn :fi)
       :info-sv (info-fn :sv)})))

(defn- send-open-inforequest-invite! [email token application-id]
  (let [subject "Uusi neuvontapyynt\u00F6"
        msg     (get-message-for-open-inforequest-invite token)]
    (send-mail-to-recipients! [email] subject msg)))

;;
;; Configuration for generic notifications
;;

(defn- default-recipients-fn [{application :application}]
  (get-email-recipients-for-application application nil ["statementGiver"]))

(defn- from-user [{user :user}] [(:email user)])
(defn- from-data [{data :data}] [(:email data)])

(def ^:private mail-config
  {:application-targeted-comment {:recipients-fn  from-user
                                  :subject-key    "new-comment"
                                  :tab            "/conversation"}
   :application-state-change     {:subject-key    "state-change"
                                  :application-fn (fn [{id :id}] (mongo/by-id :applications id))}
   :application-verdict          {:subject-key    "verdict"
                                  :tab            "/verdict"}
   :new-comment                  {:tab            "/conversation"
                                  :pred-fn        (fn [{user :user}] (user/authority? user))}
   :invite                       {:recipients-fn  from-data}
   :add-statement-person         {:recipients-fn  from-user
                                  :subject-key     "application.statements"
                                  :model-fn        statement-person-model}
   :request-statement            {:recipients-fn  (fn [{{users :users} :data}] (map :email users))
                                  :subject-key     "statement-request"}
   :neighbor                     {:recipients-fn  from-data
                                  :model-fn       neighbor-invite-model}
   :invite-authority             {:subject-key    "authority-invite.title"}
   :reset-password               {:subject-key    "reset.email.title"}})

;;
;; Public API
;;

(defn notify! [template {:keys [user created application data] :as command}]
  (if-let [conf (template mail-config)]
    (when ((get conf :pred-fn (constantly true)) command)
      (let [application-fn (get conf :application-fn identity)
            application    (application-fn application)
            recipients-fn  (get conf :recipients-fn default-recipients-fn)
            recipients     (recipients-fn command)
            subject        (get-email-subject application (get conf :subject-key (name template)))
            model          (if (:model-fn conf)
                             ((:model-fn conf) command)
                             (create-app-model application (:tab conf)))
            template-file  (get conf :template (str (name template) ".md"))
            msg            (email/apply-template template-file model)]
        (send-mail-to-recipients! recipients subject msg)))
    (case template
      :open-inforequest-invite (send-open-inforequest-invite! (:email data) (:token-id data) (:id application)))))

(defn send-token! [template to token]
  (let [conf    (template mail-config)
        template-file  (get conf :template (str (name template) ".md"))
        link-fi (url-to (str "/app/fi/welcome#!/setpw/" token))
        link-sv (url-to (str "/app/sv/welcome#!/setpw/" token))
        msg (email/apply-template template-file {:link-fi link-fi :link-sv link-sv})]
    (send-mail-to-recipients! [to] (loc (:subject-key conf)) msg)))
