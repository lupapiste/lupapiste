(ns lupapalvelu.states
  (:require [clojure.set :refer [difference union]]))


(def all-application-states #{:draft :open :submitted :sent :complement-needed
                              :verdictGiven :constructionStarted :closed :canceled})
(def all-inforequest-states #{:info :answered})
(def all-states             (union all-application-states all-inforequest-states))

(def pre-verdict-states #{:draft :info :answered :open :submitted :complement-needed :sent})
(def post-verdict-states (difference all-application-states pre-verdict-states #{:canceled}))

(def pre-sent-application-states #{:draft :open :submitted :complement-needed})

(def post-submitted-states #{:sent :complement-needed :verdictGiven :constructionStarted :closed})

(defn all-states-but [drop-states-array]
  (difference all-states (set drop-states-array)))

(defn all-application-states-but [drop-states-array]
  (difference all-application-states (set drop-states-array)))

(defn all-inforequest-states-but [drop-states-array]
  (difference all-inforequest-states (set drop-states-array)))
