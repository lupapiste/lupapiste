(ns lupapalvelu.notifications  
  (:use [monger.operators]
        [clojure.tools.logging]
        [lupapalvelu.strings :only [suffix]]
        [lupapalvelu.core])
  (:require [clojure.java.io :as io]
            [net.cgrand.enlive-html :as enlive]
            [sade.security :as sadesecurity]
            [sade.client :as sadeclient]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.security :as security]
            [lupapalvelu.client :as client]
            [lupapalvelu.email :as email]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.components.core :as c]))

(def mail-default-styles
"
html {
  background-color: #f0f0f0;
}
body {
  font-family: Dosis,sans-serif;
  font-size: 14px;
  padding: 18px;
  margin: 12px;
  background-color: #ffffff;
}

h1 {
  font-size: 16px;
}

p {
  font-size: 14px;
}")

(def mail-agent (agent nil)) 

(defn get-application-link [application lang suffix host]
  (let [permit-type-path (if (= (:permitType application) "infoRequest") "/inforequest/" "/application/")]
    (str host "/" lang "/applicant#!" permit-type-path (:id application) suffix)))

(defn replace-application-link [e selector application lang suffix host]
  (enlive/transform e [(keyword (str selector lang))] (fn [e] (assoc-in e [:attrs :href] (get-application-link application lang suffix host)))))

(defn send-mail-to-recipients [recipients title msg]
  (doseq [recipient recipients]
    (send-off mail-agent (fn [_]
                           (if (email/send-email recipient title msg)
                             (info "email was sent successfully")
                             (error "email could not be delivered."))))))

(defn get-email-title [application title-key]
  (str (i18n/with-lang "fi" (i18n/loc (str "email-title-prefix")))
       (:title application)
       (i18n/with-lang "fi" (i18n/loc (str "email-title-delimiter")))
       (i18n/with-lang "fi" (i18n/loc (str title-key)))))

; new comment
(defn get-message-for-new-comment [application host]
  (let [application-id (:id application)
        e (enlive/html-resource "email-templates/application-new-comment.html")]    
    (apply str (enlive/emit* (-> e
                               (replace-application-link "#conversation-link-" application "fi" "/conversation" host)
                               (replace-application-link "#conversation-link-" application "sv" "/conversation" host))))))

(defn get-email-recipients-for-application [application]
  (map (fn [user] (:username (mongo/by-id :users (:id user)))) (:auth application)))

(defn get-email-recipients-for-new-comment [application]
  (get-email-recipients-for-application application))

(defn send-notifications-on-new-comment [application user-commenting comment-text host]
  (if (= :authority (keyword (:role user-commenting)))
    (let [recipients (get-email-recipients-for-new-comment application)
          msg (get-message-for-new-comment application host)]
      (send-mail-to-recipients recipients 
                               (get-email-title application "new-comment-email-title")
                               msg))))

; application opened
(defn get-message-for-application-state-change [application host]
  (let [application-id (:id application)
        e (enlive/html-resource "email-templates/application-state-change.html")]
    
    (apply str (enlive/emit* (-> e
                               (replace-application-link "#application-link-" application "fi" "" host)
                               (replace-application-link "#application-link-" application "sv" "" host)
                               (enlive/transform [(keyword "#state-fi")] (enlive/content (i18n/with-lang "fi" (i18n/loc (str (:state application))))))
                               (enlive/transform [(keyword "#state-sv")] (enlive/content (i18n/with-lang "sv" (i18n/loc (str (:state application)))))))))))

(defn get-email-recipients-for-application-state-change [application]
  (get-email-recipients-for-application application))

(defn send-notifications-on-application-state-change [application-id state host]
  (let [application (mongo/by-id :applications application-id)]
  (let [recipients (get-email-recipients-for-application application)
        msg (get-message-for-application-state-change application host)]
  (send-mail-to-recipients recipients
                           (get-email-title application "state-change-email-title")
                           msg))))
