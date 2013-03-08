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
            [lupapalvelu.security :as security]
            [lupapalvelu.client :as client]
            [lupapalvelu.email :as email]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.components.core :as c]))

(defn get-application-link [host application lang]
  (let [permit-type-path (if (= (:permitType application) "infoRequest") "/inforequest/" "/application/")]
    (str host "/" lang "/applicant#!" permit-type-path (:id application))))

(defn replace-with-selector [e host application lang]
  (enlive/transform e [(keyword (str "#application-link-" lang))] (fn [e] (assoc e :content (get-application-link host application lang)))))

(defn send-mail-to-recipients [recipients title msg]
  (doseq [recipient recipients]
    (send-off mail-agent (fn [_]
                           (if (email/send-email recipient title msg)
                             (info "email was sent successfully")
                             (error "email could not be delivered."))))))

; new comment
(defn get-message-for-new-comment [application host]
  (let [application-id (:id application)
        e (enlive/html-resource "email-templates/application-new-comment.html")]
    
    (apply str (enlive/emit* (-> e
                               (replace-with-selector host application "fi")
                               (replace-with-selector host application "sv"))))))

(defn get-users-in-role [users roles]
  ; todo return only users in the roles
  users)

(defn get-email-recipients-for-application-roles [application roles]
  (map (fn [user] (:email (mongo/by-id :users (:id user)))) ((get-users-in-role (:auth application) roles))))

(defn get-email-recipients-for-new-comment [application]
  (get-email-recipients-for-application-roles application [:owner :writer]))
    
(defn send-notifications-on-new-comment [host application user-commenting comment-text]
  (if (= :authority (keyword (:role user-commenting)))
    (let [recipients (get-email-recipients-for-new-comment application)
          msg (get-message-for-new-comment application host)]
      (send-mail-to-recipients recipients (:title application) msg))))

; application opened
(defn get-emails-for-application-state-change [application]
  (map (fn [user] (:email (mongo/by-id :users (:id user)))) (:auth application)))

(defn send-notifications-on-application-opened [application-id state]
  (let [application (mongo/by-id :applications (application-id))]
  (get-email-recipients-for-application-roles application [:owner :writer])
  (println "notification sent on app" application-id " now with state" state)))
