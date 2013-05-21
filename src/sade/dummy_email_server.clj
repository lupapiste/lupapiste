(ns sade.dummy-email-server
  (:use [clojure.java.io :only [input-stream]]
        [clojure.tools.logging]
        [clojure.pprint :only [pprint]]
        [noir.core :only [defpage]]
        [lupapalvelu.core :only [defquery ok]])
  (:require [clojure.string :as s]
            [sade.env :as env]
            [net.cgrand.enlive-html :as enlive])
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

(env/in-dev

  (defquery "sent-emails"
    {}
    [{{reset :reset} :data}]
    (ok :messages (messages :reset reset)))

  (defpage "/api/last-email" []
    (if-let [msg (last (messages))]
      (let [html     (first (re-find #"(?ms)<html>(.*)</html>" (:body msg)))
            subject  (get-in msg [:headers :Subject])
            to       (get-in msg [:headers :To])]
        (debug (get-in msg [:headers]))
        (enlive/emit* (-> (enlive/html-resource (input-stream (.getBytes html "UTF-8")))
                        (enlive/transform [:head] (enlive/append {:tag :title :content subject}))
                        (enlive/transform [:body] (enlive/prepend [{:tag :dl :content [{:tag :dt :content "To"}
                                                                                       {:tag :dd :attrs {:id "to"} :content to}
                                                                                       {:tag :dt :content "Subject"}
                                                                                       {:tag :dd :attrs {:id "subject"} :content subject}]}
                                                                   {:tag :hr}])))))
      {:response 404 :body "No emails"})))
