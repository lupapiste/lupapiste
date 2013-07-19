(ns sade.dummy-email-server
  (:use [clojure.java.io :only [input-stream]]
        [clojure.tools.logging]
        [clojure.pprint :only [pprint]]
        [noir.core :only [defpage]]
        [lupapalvelu.core :only [defquery ok]])
  (:require [clojure.string :as s]
            [sade.env :as env]
            [net.cgrand.enlive-html :as enlive])
  (:import [com.icegreen.greenmail.util GreenMail GreenMailUtil ServerSetup]
           [org.apache.commons.mail.util MimeMessageParser]))

(defonce server (atom nil))

(defn stop []
  (swap! server (fn [s] (when s (debug "Stopping dummy mail server") (.stop s)) nil)))

(defn start []
  (stop)
  (let [port (env/value :email :port)
        smtp-server (GreenMail. (ServerSetup. port nil ServerSetup/PROTOCOL_SMTP))]
    (debug "Starting dummy mail server on port" port)
    (.start smtp-server)
    (reset! server smtp-server)))

(defn- parse-message [message]
  (when message
    (let [m (doto (MimeMessageParser. message) (.parse))]
      {:body {:plain (when (.hasPlainContent m) (.getPlainContent m))
              :html (when (.hasHtmlContent m) (.getHtmlContent m))}
     :headers (into {} (map (fn [header] [(keyword (.getName header)) (.getValue header)]) (enumeration-seq (.getAllHeaders message))))})))

(defn messages [& {:keys [reset]}]
  (when-let [s @server]
    (let [messages (map parse-message (.getReceivedMessages s))]
      (when reset
        (start))
      messages)))

#_(env/in-dev

  (defn dump []
    (doseq [message (messages)]
      (pprint message)))

  (defquery "sent-emails"
    {}
    [{{reset :reset} :data}]
    (ok :messages (messages :reset reset)))

  (defquery "last-email"
    {}
    [{{reset :reset} :data}]
    (ok :message (last (messages :reset reset))))

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
