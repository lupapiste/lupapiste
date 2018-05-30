(ns lupapalvelu.test-ts
  (:require [taoensso.timbre :as timbre]
            [lupapalvelu.integrations.jms :as jms]))

(def test-queue "lupapiste.test")

(defn handle-test-message [name to commit-or-rollback]
  (fn [msg]
    (timbre/info name "handling message:" msg)
    (Thread/sleep to)
    (timbre/info name "done with" msg)
    (commit-or-rollback)))

(def test1-transacted-session (-> (jms/get-default-connection)
                                  (jms/create-transacted-session)
                                  (jms/register-session :consumer)))

(defonce test-consumer1
  (jms/create-consumer test1-transacted-session
                       "lupapiste.#"
                       (handle-test-message "test-consumer1"
                                            1000
                                            #(do (timbre/info "test-consumer1 committing")
                                                 (jms/commit test1-transacted-session)))))


(def test2-transacted-session (-> (jms/get-default-connection)
                                  (jms/create-transacted-session)
                                  (jms/register-session :consumer)))

(defonce test-consumer2
  (jms/create-consumer test2-transacted-session
                       "lupapiste.#"
                       (handle-test-message "test-consumer2"
                                            1000
                                            #(do (timbre/info "test-consumer2 committing")
                                                 (jms/commit test2-transacted-session)))))

(doseq [i (range 30)]
  (jms/produce-with-context "lupapiste.#" (str "msg" i)))
