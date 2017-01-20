(ns sade.email
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn warnf error fatal]]
            [clojure.set :as set]
            [postal.core :as postal]
            [sade.env :as env]
            [sade.strings :as ss]))

;;
;; Configuration:
;; ----------------

(def defaults
  (merge
    {:from     "\"Lupapiste\" <no-reply@lupapiste.fi>"
     :reply-to "\"Lupapiste\" <no-reply@lupapiste.fi>"}
    (select-keys (env/value :email) [:from :reply-to :bcc :user-agent])))

;; Timeout info need to be of Integer type or javax.mail api will not follow them.
;; ks. com.sun.mail.util.PropUtil.getIntProperty
(def config (-> (env/value :email)
              (select-keys [:host :port :user :pass :sender :ssl :tls :connectiontimeout :timeout])
              (update-in [:connectiontimeout] int)
              (update-in [:timeout] int)))

;; Dynamic for testing purposes only, thus no earmuffs
(def ^:dynamic blacklist (when-let [p (env/value :email :blacklist)] (re-pattern p)))

(defn blacklisted? [email]
  (boolean  (when (and blacklist email) (re-matches blacklist email))))

;;
;; Delivery:
;; ---------
;;

(declare deliver-email)

(when-not (env/value :email :dummy-server)
  (def deliver-email (fn [to subject body]
                      (assert (string? to) "must provide 'to'")
                      (assert (string? subject) "must provide 'subject'")
                      (assert body "must provide 'body'")
                      (let [message (merge defaults {:to to :subject subject :body body})
                            error (postal/send-message config message)]
                        (when-not (= (:error error) :SUCCESS)
                          error)))))

;;
;; Sending 'raw' email messages:
;; =============================
;;

(defn send-mail
  "Send raw email message. Consider using send-email-message instead."
  [to subject & {:keys [plain html calendar attachments] :as args}]
  (assert (or plain html calendar) "must provide some content")
  (let [plain-body (when plain {:content plain :type "text/plain; charset=utf-8"})
        html-body  (when html {:content html :type "text/html; charset=utf-8"})
        calendar-body (when calendar {:content (:content calendar) :type (str "text/calendar; charset=utf-8; method=" (:method calendar))})
        body       (remove nil? [:alternative plain-body html-body calendar-body])
        body       (if (= (count body) 2)
                     [(second body)]
                     (vec body))
        attachments (when attachments
                      (for [attachment attachments]
                        (-> (set/rename-keys attachment {:filename :file-name})
                            (select-keys [:content :file-name])
                            (assoc :type :attachment))))
        body       (if attachments
                     (into body attachments)
                     body)]
    (deliver-email to subject body)))

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
                        (warnf "Ignoring message to %s bacause address matches blacklist %s" to blacklist)
                        false) ; false = pretend that the message was send

    :else (let [[plain html calendar] msg]
            (try
              (send-mail to subject :plain plain :html html :calendar calendar :attachments attachments)
              false
              (catch Exception e
                (error "Email failure:" e)
                (.printStackTrace e)
                true)))))
