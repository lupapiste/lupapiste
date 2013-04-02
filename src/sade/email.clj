(ns sade.email
  (:use [sade.core]
        [clojure.tools.logging])
  (:require [postal.core :as postal]
            [sade.env :as env]
            [sade.strings :as s]))

(defn send-mail
  "Sends HTML email and returns a sade.core.ok/fail with :reason telling weather is was ok"
  ([to subject body]
    (send-mail to (-> env/config :email :from) subject body))
  ([to from subject body]
    (when-let [domain (s/suffix to "@")]
      (if (or (s/starts-with domain "example.") (= to domain))
        (do
          (warn "Not sending email to" to)
          (ok))
        (try
          (let [status (postal/send-message
                         (:email env/config)
                         {:from    from
                          :to      to
                          :subject subject
                          :body    [{:type "text/html; charset=utf-8"
                                     :content body}]})]
            (if (= (:error status) :SUCCESS) (ok) (fail :reason (:msg status))))
          (catch Exception e
            (error e (.getMessage e))
            (fail :reason (:msg "exeption"))))))))

(defn send-mail? [to subject body] (ok? (send-mail to subject body)))
