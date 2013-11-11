(ns lupapalvelu.notifications
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error fatal]]
            [monger.operators]
            [clojure.set :as set]
            [lupapalvelu.i18n :refer [loc] :as i18n]
            [sade.util :refer [future*]]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.email :as email]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]))

;;
;; Helpers
;;

(defn- get-application-link [{:keys [infoRequest id]} suffix host lang]
  (let [permit-type-path (if infoRequest "/inforequest" "/application")
        full-path        (str permit-type-path "/" id suffix)]
    (str host "/app/" lang "/applicant?hashbang=!" full-path "#!" full-path)))

(defn- send-mail-to-recipients! [recipients subject msg]
  (future*
    (doseq [recipient recipients]
      (if (email/send-email-message recipient subject msg)
        (error "email could not be delivered." recipient subject msg)
        (info "email was sent successfully." recipient subject))))
  nil)

(defn- get-email-subject [{title :title} & [title-key]]
  (let [title-postfix (when title-key (str " - " (i18n/localize "fi" "email.title" title-key)))]
    (str "Lupapiste.fi: " title title-postfix)))

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

(defn- create-app-model [application tab host]
  {:link-fi (get-application-link application tab host "fi")
   :link-sv (get-application-link application tab host "sv")
   :state-fi (i18n/localize :fi (str (:state application)))
   :state-sv (i18n/localize :sv (str (:state application)))})

;;
;; Statement person
;;

(defn- send-create-statement-person! [email text organization]
  (let [subject (get-email-subject {:title "Lausunnot"})
        msg   (email/apply-template "add-statement-person.md"
                                    {:text text
                                     :organization-fi (:fi (:name organization))
                                     :organization-sv (:sv (:name organization))})]
    (send-mail-to-recipients! [email] subject msg)))

;;
;; Neighbor
;;

(defn- send-neighbor-invite! [to-address token neighbor-id application host]
  (let [neighbor-name  (get-in application [:neighbors (keyword neighbor-id) :neighbor :owner :name])
        address        (get application :address)
        municipality   (get application :municipality)
        subject        (get-email-subject application "neighbor")
        page           (str "#!/neighbor-show/" (:id application) "/" neighbor-id "/" token)
        link-fn        (fn [lang] (str host "/app/" (name lang) "/neighbor/" (:id application) "/" neighbor-id "/" token))
        msg            (email/apply-template "neighbor.md" {:name neighbor-name
                                                      :address address
                                                      :city-fi (i18n/localize :fi "municipality" municipality)
                                                      :city-sv (i18n/localize :sv "municipality" municipality)
                                                      :link-fi (link-fn :fi)
                                                      :link-sv (link-fn :sv)})]
    (send-mail-to-recipients! [to-address]  subject msg)))

;;
;; Open Inforequest
;;

(defn- get-message-for-open-inforequest-invite [host token]
  (let  [link-fn (fn [lang] (str host "/api/raw/openinforequest?token-id=" token "&lang=" (name lang)))
         info-fn (fn [lang] (env/value :oir :wanna-join-url))]
    (email/apply-template "open-inforequest-invite.html"
      {:link-fi (link-fn :fi)
       :link-sv (link-fn :sv)
       :info-fi (info-fn :fi)
       :info-sv (info-fn :sv)})))


(defn- send-open-inforequest-invite! [email token application-id host]
  (let [subject "Uusi neuvontapyynt\u00F6"
        msg     (get-message-for-open-inforequest-invite host token)]
    (send-mail-to-recipients! [email] subject msg)))

;;
;; Generic
;;

(def ^:private mail-config
  {:new-comment       {:template    "new-comment.md"
                       :tab          "/conversation"}
   :targetted-comment {:template    "application-targeted-comment.md"
                       :subject-key "new-comment"
                       :tab          "/conversation"}
   :invite            {:template    "invite.md"}
   :state-change      {:template    "application-state-change.md"}
   :verdict           {:template    "application-verdict.md"
                       :tab          "/verdict"}
;:new-statement-person
   :request-statement {:template    "add-statement-request.md"}
;:neighbor-invite
;:open-inforequest-invite
   })

(defn- subject-and-message [message-type application host]
  {:pre (contains? mail-config message-type)}
  (let [conf (message-type mail-config)]
    [(get-email-subject application (get conf :subject-key (name message-type)))
     (email/apply-template (:template conf) (create-app-model application (:tab conf) host))]))


(defn- send-on-request-for-statement! [persons application user host]
  (let [[subject msg] (subject-and-message :request-statement application host)]
    (send-mail-to-recipients! (map :email persons) subject msg)))

(defn- send-notifications-on-new-comment! [application user host]
  (when (user/authority? user)
    (let [recipients   (get-email-recipients-for-application application nil ["statementGiver"])
          [subject msg] (subject-and-message :new-comment application host)]
      (send-mail-to-recipients! recipients subject msg))))

(defn- send-notifications-on-new-targetted-comment! [application to-email host]
  (let [[subject msg] (subject-and-message :targetted-comment application host)]
    (send-mail-to-recipients! [to-email]  subject msg)))

(defn- send-invite! [email text application host]
  (let [[subject msg] (subject-and-message :invite application host)]
    (send-mail-to-recipients! [email] subject msg)))

(defn- send-notifications-on-application-state-change! [{:keys [id]} host]
  (let [application (mongo/by-id :applications id) ; Load new state from DB
        recipients  (get-email-recipients-for-application application nil ["statementGiver"])
        [subject msg] (subject-and-message :state-change application host)]
    (send-mail-to-recipients! recipients subject msg)))

(defn- send-notifications-on-verdict! [application host]
  (let [recipients    (get-email-recipients-for-application application nil ["statementGiver"])
        [subject msg] (subject-and-message :verdict application host)]
    (send-mail-to-recipients! recipients subject msg)))

;;
;; Public API
;;
(defn notify! [template {:keys [user created application data] :as command}]
  (let [host (env/value :host)]
    (case (keyword template)
      :new-comment  (send-notifications-on-new-comment! application user host)
      :targetted-comment (send-notifications-on-new-targetted-comment! application (:email user) host)
      :invite       (send-invite! (:email data) (:text data) application host)
      :state-change (send-notifications-on-application-state-change! application host)
      :verdict      (send-notifications-on-verdict! application host)
      :new-statement-person (send-create-statement-person! (:email user) (:text data) (:organization data))
      :request-statement (send-on-request-for-statement! (:users data) application user host)
      :neighbor-invite (send-neighbor-invite! (:email data) (:token data) (:neighborId data) application host)
      :open-inforequest-invite (send-open-inforequest-invite! (:email data) (:token-id data) (:id application) host)
      )))

(def ^:private token-mail-config
  {:invite-authority {:template "authority-invite.md" :subject-key "authority-invite.title"}
   :reset-password   {:template "password-reset.md"   :subject-key "reset.email.title"}})

(defn send-token! [template to token]
  {:pre (contains? token-mail-config template)}
  (let [conf    (template token-mail-config)
        link-fi (url-to (str "/app/fi/welcome#!/setpw/" token))
        link-sv (url-to (str "/app/sv/welcome#!/setpw/" token))
        msg (email/apply-template (:template conf) {:link-fi link-fi :link-sv link-sv})]
    (send-mail-to-recipients! [to] (loc (:subject-key conf)) msg)))
