(ns sade.email
  (:use [sade.core]
        [clojure.tools.logging])
  (:require [postal.core :as postal]
            [sade.env :as env]
            [sade.strings :as s]))

(defn send-mail [& {:keys [from to subject html text] :or {from "lupapiste@lupapiste.fi"}}]
  (assert (or html text) "must provide at least one body")
  (assert (and to subject) "must provide both 'to' and 'subject'")
  (let [html-body (when html {:content html :type "text/html; charset=utf-8"})
        text-body (when text {:content text :type "text/plain; charset=utf-8"})
        body      (if (and html-body text-body)
                    [:alternative text-body html-body]
                    [(or html-body text-body)])
        error     (postal/send-message
                    (env/value :email)
                    {:from     from
                     :to       to
                     :subject  subject
                     :body     body})]
    (when-not (= (:error error) :SUCCESS)
      error)))
