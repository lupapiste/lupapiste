(ns sade.dummy-email-server
  (:use [clojure.tools.logging]
        [clojure.pprint :only [pprint]]
        [lupapalvelu.core :only [defquery ok]])
  (:import [com.dumbster.smtp SimpleSmtpServer SmtpMessage]))

(defonce server (atom nil))

(defn stop []
  (swap! server (fn [s] (when s (.stop s)) nil)))

(defn start []
  (stop)
  (swap! server (constantly (SimpleSmtpServer/start 1025))))

(defn- message-header [message headers header-name]
  (assoc headers (keyword header-name) (.getHeaderValue message header-name)))

(defn- parse-message [message]
  (when message
    {:body (.getBody message)
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