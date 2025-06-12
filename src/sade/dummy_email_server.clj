(ns sade.dummy-email-server
  (:require [clojure.java.io :as io]
            [clojure.pprint]
            [lupapalvelu.action :refer [defquery]]
            [lupapalvelu.crypto :as crypt]
            [lupapalvelu.logging :refer [info]]
            [lupapalvelu.mongo :as mongo]
            [net.cgrand.enlive-html :as enlive]
            [noir.core :refer [defpage]]
            [sade.core :refer [now ok]]
            [sade.date :as date]
            [sade.email :as email]
            [sade.env :as env]
            [schema.core :as sc])
  (:import [org.apache.commons.io IOUtils]))

(set! *warn-on-reflection* true)

;;
;; Dummy email server:
;;

(defn get-queue
  []
  (or (and (mongo/connected?)
           (or (mongo/current-db-name)
               (mongo/default-db-name)))
      "no-db"))

(when (env/value :email :dummy-server)

  (info "Initializing dummy email server")

  (defonce sent-messages (atom {}))

  (defn attachment-as-base64-str [file]
    (with-open [i (io/input-stream file)]
      (-> i
          (IOUtils/toByteArray)
          (crypt/base64-encode)
          (crypt/bytes->str))))

  (defn parse-body
    "For testing only one attachment is stored as a string.
    Apparently there has been no need for multiple attachments."
    [body]
    (-> body
        (dissoc :attachments)
        (assoc :attachment (some-> body :attachments first (update :content attachment-as-base64-str)))))

  (defn deliver-email [to subject body]
    (assert (string? to) "must provide 'to'")
    (assert (string? subject) "must provide 'subject'")
    (assert body "must provide 'body'")
    (let [queue (get-queue)]
      (swap! sent-messages update queue conj
             {:to to, :subject subject, :body (parse-body body), :time (now)}))
    nil)

  (alter-var-root (var email/deliver-email) (constantly deliver-email))

  (defn reset-sent-messages []
    (let [queue (get-queue)]
      (swap! sent-messages assoc queue nil)))

  (defn messages [& {reset :reset :or {reset false}}]
    (let [queue (get-queue)
          m     (@sent-messages queue)]
      (when reset (reset-sent-messages))
      (reverse m)))

  (defn dump-sent-messages []
    (doseq [message (messages)]
      (clojure.pprint/pprint message)))

  (defquery "sent-emails"
    {:user-roles     #{:anonymous}
     :request-schema {(sc/optional-key :reset) sc/Bool}}
    [{{reset :reset :or {reset false}} :data}]
    (ok :messages (messages :reset reset)))

  (defquery "last-email"
    {:user-roles     #{:anonymous}
     :request-schema {(sc/optional-key :reset) sc/Bool}}
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
