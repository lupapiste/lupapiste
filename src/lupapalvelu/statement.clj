(ns lupapalvelu.statement
  (:require [clojure.set]
            [schema.core :refer [defschema] :as sc]
            [sade.core :refer :all]
            [sade.util :as util]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [lupapalvelu.xml.krysp.mapping-common :as mapping-common]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.user :as user]))

;;
;; Common
;;

(def statement-states #{:requested :draft :given :replyable :replied})
(def post-given-states #{:given :replyable :replied})
(def pre-given-states (clojure.set/difference statement-states post-given-states))
(def post-repliable-states #{:replyable :replied})
(def pre-repliable-states (clojure.set/difference statement-states post-repliable-states))

(def- statement-statuses ["puoltaa" "ei-puolla" "ehdoilla"])
;; Krysp Yhteiset 2.1.5+ and if no organization backend system.
(def- statement-statuses-more-options
  (vec (concat statement-statuses
               ["ei-huomautettavaa" "ehdollinen" "puollettu" "ei-puollettu" "ei-lausuntoa"
                "lausunto" "kielteinen" "palautettu" "poydalle"])))

(defschema StatementGiver
  "Statement giver user summary"
  {:userId                          ssc/ObjectIdStr ;; id for user
   (sc/optional-key :id)            sc/Str          ;; 'official' statement giver id in organization
   :text                            sc/Str          ;; text field describing statement giver role or job
   :email                           ssc/Email       ;; email
   :name                            sc/Str})        ;; full name of the statement giver

(defschema Reply
  "Statement reply"
  {:editor-id                       ssc/ObjectIdStr ;; id of the user last edited the reply
   (sc/optional-key :saateText)     sc/Str          ;; cover note for statement reply, written by authority
   :nothing-to-add                  sc/Bool         ;; indicator that user has read the statement and has nothing to add
   (sc/optional-key :text)          sc/Str})        ;; reply text that user has written

(defschema Statement
  {:id                              ssc/ObjectIdStr ;; statement id
   :state                           (apply sc/enum statement-states) ;; handling state of the statement
   (sc/optional-key :saateText)     sc/Str          ;; cover note for statement, written by authority
   (sc/optional-key :status)        (apply sc/enum statement-statuses-more-options) ;; status indicator
   (sc/optional-key :text)          sc/Str          ;; statement text written by statement giver
   (sc/optional-key :dueDate)       ssc/Timestamp   ;; due date for statement to be given
   (sc/optional-key :requested)     ssc/Timestamp   ;; when requested
   (sc/optional-key :given)         ssc/Timestamp   ;; when given
   (sc/optional-key :reminder-sent) ssc/Timestamp   ;; for remiders sent week after the request
   (sc/optional-key :modified)      ssc/Timestamp   ;; last modified
   (sc/optional-key :duedate-reminder-sent) ssc/Timestamp ;; for reminders sent after due date exceeded
   (sc/optional-key :modify-id)     sc/Str          ;; id for restrict overlapping modifications
   (sc/optional-key :editor-id)     sc/Str          ;; id of the user last edited the statement
   (sc/optional-key :reply)         Reply
   :person                          StatementGiver
   (sc/optional-key :metadata)      {sc/Any sc/Any}})

(defn create-statement [now metadata saate-text due-date person]
  (sc/validate Statement
               (cond-> {:id        (mongo/create-id)
                        :person    person
                        :requested now
                        :state     :requested}
                 saate-text     (assoc :saateText saate-text)
                 due-date       (assoc :dueDate due-date)
                 (seq metadata) (assoc :metadata metadata))))

(defn get-statement [{:keys [statements]} id]
  (util/find-by-id id statements))

(defn statement-owner [{{:keys [statementId]} :data {user-email :email} :user} application]
  (let [{{statement-email :email} :person} (get-statement application statementId)]
    (when-not (= (user/canonize-email statement-email) (user/canonize-email user-email))
      (fail :error.not-statement-owner))))

(defn authority-or-statement-owner-applicant [{{role :role} :user :as command} application]
  (when-not (or
              (= :authority (keyword role))
              (and (= :applicant (keyword role)) (nil? (statement-owner command application))))
    (fail :error.not-authority-or-statement-owner-applicant)))

(defn statement-replyable [{{statement-id :statementId} :data :as command} application]
  (when-not (->> (get-statement application statement-id) :state keyword #{:replyable})
    (fail :error.statement-is-not-replyable)))

(defn reply-visible [{{statement-id :statementId} :data :as command} application]
  (when-not (->> (get-statement application statement-id) :state keyword post-repliable-states)
    (fail :error.statement-reply-is-not-visible)))

(defn reply-not-visible [{{statement-id :statementId} :data :as command} application]
  (when-not (->> (get-statement application statement-id) :state keyword pre-repliable-states)
    (fail :error.statement-reply-is-already-visible)))

(defn statement-not-given [{{:keys [statementId]} :data} application]
  (when-not (->> statementId (get-statement application) :state keyword pre-given-states)
    (fail :error.statement-already-given)))

(defn statement-given [{{:keys [statementId]} :data} application]
  (when-not (->> statementId (get-statement application) :state keyword post-given-states)
    (fail :error.statement-not-given)))

(defn replies-enabled [command {permit-type :permitType :as application}]
  (when-not (#{"YM" "YL" "VVVL" "MAL" "YI"} permit-type) ; FIXME set in permit meta data
    (fail :error.organization-has-not-enabled-statement-replies)))

(defn- update-statement [statement modify-id prev-modify-id & updates]
  (if (or (= prev-modify-id (:modify-id statement)) (nil? (:modify-id statement)))
    (->> (apply assoc statement :modified (now) :modify-id modify-id updates)
         (util/strip-nils)
         (sc/validate Statement))
    (fail! :error.statement-updated-after-last-save :statementId (:id statement))))

(defn update-draft [statement text status modify-id prev-modify-id editor-id]
  (update-statement statement modify-id prev-modify-id :state :draft :text text :status status :editor-id editor-id))

(defn give-statement [statement text status modify-id prev-modify-id editor-id]
  (update-statement statement modify-id prev-modify-id :state :given :text text :status status :given (now) :editor-id editor-id))

(defn update-reply-draft [{reply :reply :as statement} text nothing-to-add modify-id prev-modify-id editor-id]
  (->> (assoc reply :text text :nothing-to-add (boolean nothing-to-add) :editor-id editor-id)
       (update-statement statement modify-id prev-modify-id :state :replyable :reply)))

(defn reply-statement [{reply :reply :as statement} text nothing-to-add modify-id prev-modify-id editor-id]
  (->> (assoc reply :text (when-not nothing-to-add text) :nothing-to-add (boolean nothing-to-add) :editor-id editor-id)
       (update-statement statement modify-id prev-modify-id :state :replied :reply)))

(defn request-for-reply [{reply :reply modify-id :modify-id :as statement} text user-id]
  (->> (assoc reply :saateText text :nothing-to-add false :editor-id user-id)
       (update-statement statement modify-id modify-id :state :replyable :reply)))

;;
;; Statement givers
;;

(defn fetch-organization-statement-givers [org-id]
  (let [organization (organization/get-organization org-id)
        permitPersons (or (:statementGivers organization) [])]
    (ok :data permitPersons)))

;;
;; Statuses
;;


(defn possible-statement-statuses [application]
  (let [{permit-type :permitType municipality :municipality} application
        extra-statement-statuses-allowed? (permit/get-metadata permit-type :extra-statement-selection-values)
        organization (organization/resolve-organization municipality permit-type)
        {:keys [version url]} (get-in organization [:krysp (keyword permit-type)])
        yht-version (if version (mapping-common/get-yht-version permit-type version) "0.0.0")]
    ;; Even without url the KRYSP version might exist.
    (if (and extra-statement-statuses-allowed?
             (or (util/version-is-greater-or-equal yht-version {:major 2 :minor 1 :micro 5})
                 (ss/blank? url)))
      (set statement-statuses-more-options)
      (set statement-statuses))))
