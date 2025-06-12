(ns sade.email
  (:require [clojure.set :as set]
            [lupapalvelu.logging :as log :refer [error warnf]]
            [sade.env :as env]
            [sade.http :as http]
            [sade.strings :as ss]
            [sade.util :as util]))

(set! *warn-on-reflection* true)

;;
;; Configuration:
;; ----------------

(def defaults
  (merge
    {:from     "\"Lupapiste\" <no-reply@mail.lupapiste.fi>"
     :reply-to "\"Lupapiste\" <no-reply@mail.lupapiste.fi>"}
    (select-keys (env/value :email) [:from :reply-to :bcc :user-agent])))

(def config (select-keys (env/value :email) [:mailgun-base-url :mailgun-api-key]))

(def ^:dynamic *blacklist* (when-let [p (env/value :email :blacklist)] (re-pattern p)))

(defn blacklisted? [email]
  (boolean (when (and *blacklist* email) (re-matches *blacklist* email))))

;;
;; Delivery:
;; ---------
;;

(defn create-multipart-params
  "When sending email with attachments via Mailgun
   the attachments should be maps like:
   {:name        filename (str)
    :content     file data or a fn that returns file data (InputStream, File etc.)
    :part-name   'attachment' (the actual word/string attachment)}
   Note that if (temp) Files are used, the attachment filename is always taken from the file."
  [msg]
  (let [create-param (fn [[param-name content part-name]]
                       (cond-> {:name     (name param-name)
                                :content (if (fn? content) (content) content)}
                         part-name         (assoc :part-name part-name)
                         (string? content) (assoc :encoding "UTF-8")))]
    {:multipart (concat (map #(create-param [(:file-name %) (:content %) "attachment"])
                             (:attachments msg))
                        (map create-param (dissoc msg :attachments)))}))

(defn create-form-params [msg]
  {:form-params msg})

(defn- mailgun-payload
  [{{:keys [attachments plain html]} :body :as msg}]
  (let [attachments (some->> (seq attachments)
                             (map #(-> (set/rename-keys % {:filename :file-name})
                                       (select-keys [:content :file-name])
                                       (assoc :type :attachment))))]
    (-> msg
        (dissoc :body)
        (dissoc :attachments)
        (util/assoc-when :html html :text plain :attachments attachments))))

(defn- actually-send [{:keys [mailgun-base-url mailgun-api-key]} msg]
  (let [payload (mailgun-payload msg)]
    (-> (http/post (str mailgun-base-url "/messages")
                   (merge {:basic-auth ["api" mailgun-api-key]}
                          (if (seq (:attachments payload))
                            (create-multipart-params payload)
                            (create-form-params (dissoc payload :attachments)))))
        (select-keys [:body :status]))))

(declare deliver-email)

(when-not (env/value :email :dummy-server)
  (defn deliver-email [to subject body]
    (assert (string? to) "must provide 'to'")
    (assert (string? subject) "must provide 'subject'")
    (assert body "must provide 'body'")
    (let [message (merge defaults {:to to :subject subject :body body})
          error   (actually-send config message)]
      (when-not (= (:error error) :SUCCESS)
        error))))

;;
;; Sending 'raw' email messages:
;; =============================
;;

(defn send-mail
  "Send raw email message. Consider using send-email-message instead."
  [to subject & {:keys [plain html] :as msg}]
  (assert (or plain html) "must provide some content")
  (log/debugf "Sending email to %s: %s" to subject)
  (deliver-email to subject msg))

;;
;; Sending emails with templates:
;; ==============================
;;

(defn send-email-message
  "Sends email message using a template. Returns true if there is an error, false otherwise.
   Attachments as sequence of maps with keys :file-name and :content. :content as File object."
  [to subject msg & [attachments]]
  {:pre [subject msg (or (nil? attachments) (and (sequential? attachments) (every? map? attachments)))]}
  (cond
    (ss/blank? to) (do
                     (error "Email could not be sent because of missing To field. Subject being: " subject)
                     true)

    (blacklisted? to) (do
                        (warnf "Ignoring message to %s because address matches blacklist %s" to *blacklist*)
                        false) ; false = pretend that the message was send

    :else (let [[plain html] msg]
            (try
              (send-mail to subject :plain plain :html html :attachments attachments)
              false
              (catch Exception e
                (error "Email failure:" e)
                (.printStackTrace e)
                true)))))
