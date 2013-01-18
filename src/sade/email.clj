(ns sade.email
  (:use sade.core)
  (:require [postal.core :as postal]))

(defn send-mail
  "Sends HTML email and returns a sade.core.ok/fail with :reason telling weather is was ok"
  [to from subject body]
  (let [status (postal/send-message
                 {:host "smtp.gmail.com"
                  :user "metosin.tester"
                  :pass "karitapio"
                  :ssl  :yes}
                 {:from    from
                  :to      to
                  :subject subject
                  :body    [{:type "text/html; charset=utf-8"
                             :content body}]})]
    (if (= (:error status) :SUCCESS)
      (ok)
      (fail :reason (:msg status)))))
