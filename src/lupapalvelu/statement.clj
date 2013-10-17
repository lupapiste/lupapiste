(ns lupapalvelu.statement
  (:require [sade.strings :as ss]
            [lupapalvelu.core :refer [fail]]))

;;
;; Common
;;

(defn get-statement [{:keys [statements]} id]
  (first (filter #(= id (:id %)) statements)))

(defn statement-exists [{{:keys [statementId]} :data} application]
  (when-not (get-statement application statementId)
    (fail :error.no-statement :statementId statementId)))

(defn statement-owner [{{:keys [statementId]} :data {user-email :email} :user} application]
  (let [{{statement-email :email} :person} (get-statement application statementId)]
    (when-not (= (ss/lower-case statement-email) (ss/lower-case user-email))
      (fail :error.not-statement-owner))))

(defn statement-given? [application statementId]
  (boolean (->> statementId (get-statement application) :given)))

(defn statement-not-given [{{:keys [statementId]} :data} application]
  (when (statement-given? application statementId)
    (fail :error.statement-already-given)))

