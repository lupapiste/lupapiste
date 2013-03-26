(ns lupapalvelu.notifications
  (:use [monger.operators]
        [clojure.tools.logging]
        [sade.strings :only [suffix]]
        [lupapalvelu.core])
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
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
            [lupapalvelu.components.core :as c]))

(def mail-agent (agent nil))

(defn get-styles []
  (slurp (io/resource "email-templates/styles.css")))

(defn get-application-link [{:keys [infoRequest id]} lang suffix host]
  (let [permit-type-path (if infoRequest "/inforequest" "/application")
        full-path        (str permit-type-path "/" id suffix)]
    (str host "/app/" lang "/applicant?hashbang=!" full-path "#!" full-path)))

(defn replace-style [e style]
  (enlive/transform e [:style] (enlive/content style)))

(defn replace-application-link [e selector application lang suffix host]
  (enlive/transform e [(keyword (str selector lang))] (fn [e] (assoc-in e [:attrs :href] (get-application-link application lang suffix host)))))

(defn send-mail-to-recipients [recipients title msg]
  (doseq [recipient (flatten [recipients])]
    (send-off mail-agent (fn [_]
                           (if (email/send-mail recipient title msg)
                             (info "email was sent successfully")
                             (error "email could not be delivered."))))))

(defn get-email-title [{:keys [title]} title-key]
  (i18n/with-lang "fi"
    (str
      "Lupapiste: "
      title
      " - "
      (i18n/loc (s/join "." ["email" "title" title-key])))))

; new comment
(defn get-message-for-new-comment [application host]
  (let [e (enlive/html-resource "email-templates/application-new-comment.html")]
    (apply str (enlive/emit* (-> e
                               (replace-style (get-styles))
                               (replace-application-link "#conversation-link-" application "fi" "/conversation" host)
                               (replace-application-link "#conversation-link-" application "sv" "/conversation" host))))))

(defn get-email-recipients-for-application [application]
  (map (fn [user] (:email (mongo/by-id :users (:id user)))) (:auth application)))

(defn get-email-recipients-for-new-comment [application]
  (get-email-recipients-for-application application))

(defn send-notifications-on-new-comment [application user-commenting comment-text host]
  (when (= :authority (keyword (:role user-commenting)))
    (let [recipients (get-email-recipients-for-new-comment application)
          msg        (get-message-for-new-comment application host)
          title      (get-email-title application "new-comment")]
      (send-mail-to-recipients recipients title msg))))

;; invite
(defn send-invite [email text application user host]
  (let [title (get-email-title application "invite")
        msg   (apply str (enlive/emit* (-> (enlive/html-resource "email-templates/invite.html")
                                         (replace-style (get-styles))
                                         (enlive/transform [:.name] (enlive/content (str (:firstName user) " " (:lastName user))))
                                         (replace-application-link "#link-" application "fi" "" host)
                                         (replace-application-link "#link-" application "sv" "" host)
                                         )))]
    (send-mail-to-recipients email title msg)))

; application opened
(defn get-message-for-application-state-change [application host]
  (let [application-id (:id application)
        e (enlive/html-resource "email-templates/application-state-change.html")]
    (apply str (enlive/emit* (-> e
                               (replace-style (get-styles))
                               (replace-application-link "#application-link-" application "fi" "" host)
                               (replace-application-link "#application-link-" application "sv" "" host)
                               (enlive/transform [:#state-fi] (enlive/content (i18n/with-lang "fi" (i18n/loc (str (:state application))))))
                               (enlive/transform [:#state-sv] (enlive/content (i18n/with-lang "sv" (i18n/loc (str (:state application)))))))))))

(defn get-email-recipients-for-application-state-change [application]
  (get-email-recipients-for-application application))

(defn send-notifications-on-application-state-change [application-id host]
  (let [application (mongo/by-id :applications application-id)
        recipients  (get-email-recipients-for-application application)
        msg         (get-message-for-application-state-change application host)
        title       (get-email-title application "state-change")]
    (send-mail-to-recipients recipients title msg)))

; verdict given
(defn get-message-for-verdict [application host]
  (let [e (enlive/html-resource "email-templates/application-verdict.html")]
    (apply str (enlive/emit* (-> e
                               (replace-style (get-styles))
                               (replace-application-link "#verdict-link-" application "fi" "/verdict" host)
                               (replace-application-link "#verdict-link-" application "sv" "/verdict" host))))))

(defn send-notifications-on-verdict [application-id host]
  (let [application (mongo/by-id :applications application-id)
        recipients  (get-email-recipients-for-application application)
        msg         (get-message-for-verdict application host)
        title       (get-email-title application "verdict")]
    (send-mail-to-recipients recipients title msg)))
