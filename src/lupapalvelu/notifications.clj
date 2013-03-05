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
            [lupapalvelu.components.core :as c])
  (:import [java.io ByteArrayOutputStream ByteArrayInputStream]
           [java.util.zip GZIPOutputStream]
           [org.apache.commons.io IOUtils]
           [com.yahoo.platform.yui.compressor JavaScriptCompressor CssCompressor]
           [org.mozilla.javascript ErrorReporter EvaluatorException]))

(defn write-header [out n]
    (.write out (format "\n\n/*\n * %s\n */\n" n))
  out)

(defn get-file-content []
  (let [stream (ByteArrayOutputStream.)]
    (with-open [out (io/writer stream)]
      (let [src "foo.html"]
          (.write src)
          (with-open [in (-> src c/path io/resource io/input-stream io/reader)]
              (IOUtils/copy in (write-header out src)))))
    (.toByteArray stream)))

(defn get-application-link [host application lang]
  (let [permit-type-path (if (= (:permitType application) "infoRequest") "/inforequest/" "/application/")]
    (str host "/" lang "/applicant#!" permit-type-path (:id application))))

(defn replace-with-selector [e host application lang]
  (enlive/transform e [(keyword (str "#application-link-" lang))] (fn [e] (assoc e :content (get-application-link host application lang)))))

(defn get-message-for-new-comment [application host]
  (let [application-id (:id application)
        e (enlive/html-resource "email-templates/application-new-comment.html")]
    
    (apply str (enlive/emit* (-> e
                               (replace-with-selector host application "fi")
                               (replace-with-selector host application "sv"))))))

(defn get-emails-for-new-comment [application]
  (map (fn [user] (:email (mongo/by-id :users (:id user)))) (:auth application)))

(def mail-agent (agent nil)) 

(defn send-mail-to-recipients [recipients title msg]
  (doseq [recipient recipients]
    (send-off mail-agent (fn [_]
                           (if (email/send-email recipient title msg)
                             (info "email was sent successfully")
                             (error "email could not be delivered."))))))

(defn send-notifications-on-new-comment [host application user-commenting comment-text]
  (if (= :authority (keyword (:role user-commenting)))
    (let [recipients (get-emails-for-new-comment application)
          msg (get-message-for-new-comment application host)]
      (send-mail-to-recipients recipients (:title application) msg))))
