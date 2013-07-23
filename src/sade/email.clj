(ns sade.email
  (:use [sade.core]
        [clojure.tools.logging])
  (:require [postal.core :as postal]
            [sade.env :as env]
            [sade.strings :as s]))

(defn send-mail [& {:keys [from to subject plain html] :or {from "lupapiste@lupapiste.fi"}}]
  (assert (or plain html) "must provide at least one body")
  (assert (and to subject) "must provide both 'to' and 'subject'")
  (let [plain-body (when plain {:content plain :type "text/plain; charset=utf-8"})
        html-body  (when html {:content html :type "text/html; charset=utf-8"})
        body       (if (and plain-body html-body)
                     [:alternative plain-body html-body]
                     [(or plain-body html-body)])
        error      (postal/send-message
                     (env/value :email)
                     {:from     from
                      :to       to
                      :subject  subject
                      :body     body})]
    (when-not (= (:error error) :SUCCESS)
      error)))
