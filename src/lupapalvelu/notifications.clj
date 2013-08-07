(ns lupapalvelu.notifications
  (:use [monger.operators]
        [clojure.tools.logging]
        [sade.strings :only [suffix]]
        [lupapalvelu.i18n :only [loc]])
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [sade.env :as env]
            [sade.strings :as ss]
            [net.cgrand.enlive-html :as enlive]
            [sade.security :as sadesecurity]
            [sade.client :as sadeclient]
            [sade.email :as email]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.security :as security]
            [lupapalvelu.client :as client]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.components.core :as c]
            [noir.request :as request]))

;;
;; Helpers
;;

(defn emit [xml] (apply str (enlive/emit* xml)))

(defmacro message [& xml] `(emit (-> ~@xml)))

(defn get-styles []
  (slurp (io/resource "email-templates/styles.css")))

(defn get-application-link [{:keys [infoRequest id]} suffix host lang]
  (let [permit-type-path (if infoRequest "/inforequest" "/application")
        full-path        (str permit-type-path "/" id suffix)]
    (str host "/app/" lang "/applicant?hashbang=!" full-path "#!" full-path)))

(defn replace-style [e style]
  (enlive/transform e [:style] (enlive/content style)))

(defn replace-application-link [e selector lang f]
  (enlive/transform e [(keyword (str selector lang))]
    (fn [e] (assoc-in e [:attrs :href] (f lang)))))

(defn replace-links-in-fi-sv [e selector f]
  (-> e
    (replace-application-link (str selector "-") "fi" f)
    (replace-application-link (str selector "-") "sv" f)))

(defn replace-application-links [e selector application suffix host]
  (replace-links-in-fi-sv e selector (partial get-application-link application suffix host)))

(defn send-mail-to-recipients! [recipients title msg]
  (future
    (doseq [recipient recipients]
      (if (email/send-mail recipient title :html msg)
        (error "email could not be delivered." recipient title msg)
        (info "email was sent successfully." recipient title))))
  nil)

(defn get-email-title [{title :title} & [title-key]]
  (let [title-postfix (when title-key (str " - " (i18n/localize "fi" "email.title" title-key)))]
    (str "Lupapiste.fi: " title title-postfix)))

(defn- url-to [to]
  (str (env/value :host) (when-not (ss/starts-with to "/") "/") to))

(defn get-email-recipients-for-application [application]
  (map (fn [user] (:email (mongo/by-id :users (:id user)))) (:auth application)))

(defn template [s]
  (->
    (str "email-templates/" s)
    enlive/html-resource
    (replace-style (get-styles))))

;;
;; Sending
;;

(defn send-create-statement-person! [email text organization]
  (let [title (get-email-title {:title "Lausunnot"})
        msg   (message
                (template "add-statement-person.html")
                (enlive/transform [:.text] (enlive/content text))
                (enlive/transform [:#organization-fi] (enlive/content (:fi (:name organization))))
                (enlive/transform [:#organization-sv] (enlive/content (:sv (:name organization)))))]
    (send-mail-to-recipients! [email] title msg)))

(defn send-on-request-for-statement! [persons application user host]
  (doseq [{:keys [email text]} persons]
    (let [title (get-email-title application "statement-request")
          msg   (message
                  (template "add-statement-request.html")
                  (replace-application-links "#link" application "" host))]
      (send-mail-to-recipients! [email] title msg))))

(defn send-password-reset-email! [to token]
  (let [link-fi (url-to (str "/app/fi/welcome#!/setpw/" token))
        link-sv (url-to (str "/app/sv/welcome#!/setpw/" token))
        msg (message
              (template "password-reset.html")
              (enlive/transform [:#link-fi] (fn [a] (assoc-in a [:attrs :href] link-fi)))
              (enlive/transform [:#link-sv] (fn [a] (assoc-in a [:attrs :href] link-sv))))]
    (send-mail-to-recipients! [to] (loc "reset.email.title") msg)))

;;
;; New stuff
;;

(defn send-neighbor-invite! [email token neighbor-id application host]
  (let [neighbor-name  (get-in application [:neighbors neighbor-id :neighbor :owner :name])
        address        (get application :address)
        municipality   (get application :municipality)
        subject        (get-email-title application "neighbor")
        page           (str "#!/neighbor-show/" (:id application) "/" neighbor-id "/" token)
        link-fn        (fn [lang] (str host "/app/" (name lang) "/neighbor/" (:id application) "/" (name neighbor-id) "/" token))]
    (email/send-email-message email subject "neighbor.md" {:name neighbor-name
                                                           :address address
                                                           :city-fi (i18n/localize :fi "municipality" municipality)
                                                           :city-sv (i18n/localize :sv "municipality" municipality)
                                                           :link-fi (link-fn :fi)
                                                           :link-sv (link-fn :sv)})))

(defn get-message-for-application-state-change [application host]
  (message
    (template "application-state-change.html")
    (replace-application-links "#application-link" application "" host)
    (enlive/transform [:#state-fi] (enlive/content (i18n/localize :fi (str (:state application)))))
    (enlive/transform [:#state-sv] (enlive/content (i18n/localize :sv (str (:state application)))))))

(defn get-message-for-new-comment [application host]
  (message
    (template "application-new-comment.html")
    (replace-application-links "#conversation-link" application "/conversation" host)))

(defn send-notifications-on-new-comment! [application user host]
  (when (security/authority? user)
    (let [recipients (get-email-recipients-for-application application)
          msg        (get-message-for-new-comment application host)
          title      (get-email-title application "new-comment")]
      (send-mail-to-recipients! recipients title msg))))

(defn send-notifications-on-new-targetted-comment! [application to-email host]
  (println "******" to-email)
  (let [msg        (get-message-for-new-comment application host)
        title      (get-email-title application "new-comment")]
    (send-mail-to-recipients! [to-email] title msg)))

(defn send-invite! [email text application host]
  (let [title (get-email-title application "invite")
        msg   (message
                (template "invite.html")
                (replace-application-links "#link" application "" host))]
    (send-mail-to-recipients! [email] title msg)))

(defn send-notifications-on-application-state-change! [{:keys [id]} host]
  (let [application (mongo/by-id :applications id)
        recipients  (get-email-recipients-for-application application)
        msg         (get-message-for-application-state-change application host)
        title       (get-email-title application "state-change")]
    (send-mail-to-recipients! recipients title msg)))

(defn send-notifications-on-verdict! [application host]
  (let [recipients  (get-email-recipients-for-application application)
        msg         (message
                      (template "application-verdict.html")
                      (replace-application-links "#verdict-link" application "/verdict" host))
        title       (get-email-title application "verdict")]
    (send-mail-to-recipients! recipients title msg)))

;;
;; Da notify
;;

(defn notify! [template {{:keys [host]} :web :keys [user created application data] :as command}]
  (condp = (keyword template)
    :new-comment  (send-notifications-on-new-comment! application user host)
    :invite       (send-invite! (:email data) (:text data) application host)
    :state-change (send-notifications-on-application-state-change! application host)
    :verdict      (send-notifications-on-verdict! application host)))
