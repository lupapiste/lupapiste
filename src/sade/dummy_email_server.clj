(ns sade.dummy-email-server
  (:use [clojure.java.io :only [input-stream]]
        [clojure.tools.logging]
        [clojure.pprint :only [pprint]]
        [lupapalvelu.core :only [defquery ok]])
  (:require [clojure.string :as s]
            [sade.env :as env])
  (:import [javax.mail.internet MimeUtility]
           [com.dumbster.smtp SimpleSmtpServer SmtpMessage]))

(defonce server (atom nil))

(defn stop []
  (swap! server (fn [s] (when s (debug "Stopping dummy mail server") (.stop s)) nil)))

(defn start []
  (stop)
  (let [port (env/value :email :port)]
    (debug "Starting dummy mail server on port" port)
    (swap! server (constantly (SimpleSmtpServer/start port)))))

(defn- message-header [message headers header-name]
  (assoc headers (keyword header-name) (.getHeaderValue message header-name)))

(defn- parse-message [message]
  (when message
    {:body    (-> (.getBody message) (s/replace #"=([^A-Z]{2})" "$1" ) ; strip extra '=' chars that are not part of quotation
                (.getBytes "US-ASCII") (input-stream) (MimeUtility/decode "quoted-printable") (slurp))
     :headers (reduce (partial message-header message) {} (iterator-seq (.getHeaderNames message)))}))

(defn messages [& {:keys [reset]}]
  (when-let [s @server]
    (let [messages (map parse-message (iterator-seq (.getReceivedEmail s)))]
      (when reset
        (start))
      messages)))

(defn dump []
  (doseq [message (messages)]
    (pprint message)))

(defquery "sent-emails"
  {}
  [{{reset :reset} :data}]
  (ok :messages (messages :reset reset)))
