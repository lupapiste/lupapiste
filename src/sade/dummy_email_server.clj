(ns sade.dummy-email-server
  (:require [taoensso.timbre :refer [info]]
            [clojure.java.io :as io]
            [clojure.pprint]
            [noir.core :refer [defpage]]
            [net.cgrand.enlive-html :as enlive]
            [sade.env :as env]
            [sade.core :refer [ok now]]
            [sade.date :as date]
            [lupapalvelu.action :refer [defquery]]
            [lupapalvelu.mongo :as mongo]
            [lupacrypto.core :as crypt])
  (:import [org.apache.commons.io IOUtils]))

;;
;; Dummy email server:
;;

(defmacro with-queue [& body]
  `(let [~'queue (or mongo/*db-name* mongo/default-db-name)]
     ~@body))

(when (env/value :email :dummy-server)

  (info "Initializing dummy email server")

  (defonce sent-messages (atom {}))

  (defn attachment-as-base64-str [file]
    (with-open [i (io/input-stream file)]
      (-> i
        (IOUtils/toByteArray)
        (crypt/base64-encode)
        (crypt/bytes->str))))

  (defn parse-body [body {content-type :type content :content fname :file-name}]
    (if (and content-type content)
      (let [attachment (when (= :attachment content-type)
                         {:file-name fname :content (attachment-as-base64-str content)})
            content    (or attachment content)]
        (assoc body (condp = content-type
                      "text/plain; charset=utf-8"                    :plain
                      "text/html; charset=utf-8"                     :html
                      "text/calendar; charset=utf-8; method=REQUEST" :calendar
                      "text/calendar; charset=utf-8; method=REPLY"   :calendar
                      "text/calendar; charset=utf-8; method=CANCEL"  :calendar
                      content-type) content))
      body))

  (defn deliver-email [to subject body]
    (assert (string? to) "must provide 'to'")
    (assert (string? subject) "must provide 'subject'")
    (assert body "must provide 'body'")
    (with-queue
      (swap! sent-messages update queue conj
             {:to to, :subject subject, :body (reduce parse-body {} body), :time (now)}))
    nil)

  (alter-var-root (var sade.email/deliver-email) (constantly deliver-email))

  (defn reset-sent-messages []
    (with-queue
      (swap! sent-messages assoc queue nil)))

  (defn messages [& {reset :reset :or {reset false}}]
    (with-queue
      (let [m (@sent-messages queue)]
        (when reset (reset-sent-messages))
        (reverse m))))

  (defn dump-sent-messages []
    (doseq [message (messages)]
      (clojure.pprint/pprint message)))

  (defquery "sent-emails"
    {:user-roles #{:anonymous}}
    [{{reset :reset :or {reset false}} :data}]
    (ok :messages (messages :reset reset)))

  (defquery "last-email"
    {:user-roles #{:anonymous}}
    [{{reset :reset :or {reset true}} :data}]
    (ok :message (last (messages :reset (if (boolean? reset) reset (Boolean/valueOf ^String reset))))))

  (defn msg-header [msg]
    {:tag     :dl
     :attrs   {:class "msg-header"}
     :content [{:tag :dt :content "To"}
               {:tag :dd :attrs {:data-test-id "to"} :content [(:to msg)]}
               {:tag :dt :content "Subject"}
               {:tag :dd :attrs {:data-test-id "subject"} :content [(:subject msg)]}
               {:tag :dt :content "Time"}
               {:tag :dd :attrs {:data-test-id "time"} :content [(date/finnish-datetime (:time msg)
                                                                                        :zero-pad)]}]})

  (defn emit-as-html [msg]
    (enlive/emit* (-> (enlive/html-resource (io/input-stream (.getBytes ^String (get-in msg [:body :html]) "UTF-8")))
                      (enlive/transform [:head] (enlive/append {:tag :title :content (:subject msg)}))
                      (enlive/transform [:body] (enlive/prepend [(msg-header msg)
                                                                 {:tag :hr}]))
                      (enlive/transform [:body] (enlive/append [{:tag :hr} {:tag :pre :content (get-in msg [:body :calendar])}])))))

  (defpage "/api/last-email" {reset :reset}
    (if-let [msg (last (messages :reset reset))]
      (emit-as-html msg)
      {:status 404 :body "No emails"}))


  (defpage "/api/last-emails" {reset :reset}
    (if-let [msgs (seq (messages :reset reset))]
      (enlive/emit*
        {:tag     :html
         :content [{:tag :head :content [{:tag :title, :content "Latest emails"}
                                         {:tag :style, :content "* {font-family: sans-serif}\npre {font-family: courier; font-size: 10pt}\ndl.msg-header {background-color: #f7f9fd}"}]}
                   {:tag     :body
                    :content (map (fn [msg]
                                    {:tag   :div
                                     :attrs {:style "border-bottom: 4px dashed black;margin: 2em"}
                                     :content
                                     (vector
                                       (msg-header msg)
                                       (first (enlive/select (enlive/html-resource (io/input-stream (.getBytes ^String (get-in msg [:body :html]) "UTF-8"))) [:body]))
                                       {:tag :pre :content (get-in msg [:body :calendar])})}
                                    ) msgs)}]
         })
      {:status 404 :body "No emails"}))

  (info "Dummy email server initialized"))
