(ns lupapalvelu.statement-schemas
  (:require [lupapalvelu.integrations.ely :as ely]
            [lupapalvelu.user :as usr]
            [sade.core :refer :all]
            [sade.schemas :as ssc]
            [schema.core :refer [defschema] :as sc]))

;;
;; Common
;;

(def statement-states #{:requested :draft :given :replyable :replied})
(def post-given-states #{:given :replyable :replied})
(def pre-given-states (clojure.set/difference statement-states post-given-states))
(def post-repliable-states #{:replyable :replied})
(def pre-repliable-states (clojure.set/difference statement-states post-repliable-states))

;; Krysp Yhteiset 2.1.5+ and if no organization backend system.
(def statement-statuses
  ["ei-huomautettavaa" "ehdollinen" "puollettu" "ei-puollettu" "ei-lausuntoa"
   "lausunto" "kielteinen" "palautettu" "poydalle" "ei-tiedossa"])


(defschema StatementGiver
  "Statement giver user summary"
  {:userId                 usr/Id    ;; id for user
   (sc/optional-key :id)   sc/Str    ;; 'official' statement giver id in organization
   (sc/optional-key :text) sc/Str    ;; text field describing statement giver role or job
   :email                  ssc/Email ;; email
   :name                   sc/Str})  ;; full name of the statement giver

(defschema Reply
  "Statement reply"
  {:editor-id                       usr/Id          ;; id of the user last edited the reply
   (sc/optional-key :saateText)     sc/Str          ;; cover note for statement reply, written by authority
   :nothing-to-add                  sc/Bool         ;; indicator that user has read the statement and has nothing to add
   (sc/optional-key :text)          sc/Str})        ;; reply text that user has written

(defschema ExternalData
  "Identification data for external statement from integrations"
  {:partner                      (sc/eq "ely")
   :subtype                      (apply sc/enum ely/all-statement-types)
   (sc/optional-key :externalId) sc/Str
   (sc/optional-key :messageId)  sc/Str
   (sc/optional-key :acknowledged) ssc/Timestamp})

(defschema Statement
  {:id                                      ssc/ObjectIdStr                    ;; statement id
   :state                                   (apply sc/enum statement-states)   ;; handling state of the statement
   (sc/optional-key :requested)             ssc/Timestamp                      ;; when requested
   (sc/optional-key :saateText)             sc/Str                             ;; cover note for statement, written by authority
   (sc/optional-key :status)                (apply sc/enum statement-statuses) ;; status indicator
   (sc/optional-key :text)                  sc/Str                             ;; statement text written by statement giver
   (sc/optional-key :dueDate)               ssc/Timestamp                      ;; due date for statement to be given
   (sc/optional-key :given)                 ssc/Timestamp                      ;; when given
   (sc/optional-key :reminder-sent)         ssc/Timestamp                      ;; for reminders sent week after the request
   (sc/optional-key :modified)              ssc/Timestamp                      ;; last modified
   (sc/optional-key :duedate-reminder-sent) ssc/Timestamp                      ;; for reminders sent after due date exceeded
   (sc/optional-key :modify-id)             sc/Str                             ;; id for restrict overlapping modifications
   (sc/optional-key :editor-id)             usr/Id                             ;; id of the user last edited the statement
   (sc/optional-key :reply)                 Reply
   :person                                  StatementGiver
   (sc/optional-key :external)              ExternalData
   (sc/optional-key :metadata)              {sc/Any sc/Any}
   (sc/optional-key :in-attachment)         sc/Bool})
