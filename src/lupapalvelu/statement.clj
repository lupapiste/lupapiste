(ns lupapalvelu.statement
  (:require [clojure.set]
            [schema.core :refer [defschema] :as sc]
            [sade.core :refer :all]
            [sade.util :as util]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.validators :as v]
            [lupapalvelu.attachment :as att]
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

(def- statement-statuses-limited-options
  ["puollettu" "ei-puollettu" "ehdollinen"])
;; Krysp Yhteiset 2.1.5+ and if no organization backend system.
(def- statement-statuses
  ["ei-huomautettavaa" "ehdollinen" "puollettu" "ei-puollettu" "ei-lausuntoa"
   "lausunto" "kielteinen" "palautettu" "poydalle"])

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
   (sc/optional-key :status)        (apply sc/enum statement-statuses) ;; status indicator
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

(defn statement-owner [{{:keys [statementId]} :data {user-email :email} :user application :application}]
  (let [{{statement-email :email} :person} (get-statement application statementId)]
    (when-not (= (user/canonize-email statement-email) (user/canonize-email user-email))
      (fail :error.not-statement-owner))))

(defn authority-or-statement-owner-applicant [{{role :role} :user :as command}]
  (when-not (or
              (= :authority (keyword role))
              (and (= :applicant (keyword role)) (nil? (statement-owner command))))
    (fail :error.not-authority-or-statement-owner-applicant)))

(defn statement-replyable [{{statement-id :statementId} :data application :application}]
  (when-not (->> (get-statement application statement-id) :state keyword #{:replyable})
    (fail :error.statement-is-not-replyable)))

(defn reply-visible [{{statement-id :statementId} :data application :application}]
  (when-not (->> (get-statement application statement-id) :state keyword post-repliable-states)
    (fail :error.statement-reply-is-not-visible)))

(defn reply-not-visible [{{statement-id :statementId} :data application :application}]
  (when-not (->> (get-statement application statement-id) :state keyword pre-repliable-states)
    (fail :error.statement-reply-is-already-visible)))

(defn statement-not-given [{{:keys [statementId]} :data application :application}]
  (when-not (->> statementId (get-statement application) :state keyword pre-given-states)
    (fail :error.statement-already-given)))

(defn statement-given [{{:keys [statementId]} :data application :application}]
  (when-not (->> statementId (get-statement application) :state keyword post-given-states)
    (fail :error.statement-not-given)))

(defn replies-enabled [{{permit-type :permitType} :application}]
  (when-not (#{"YM" "YL" "VVVL" "MAL" "YI"} permit-type) ; FIXME set in permit meta data
    (fail :error.organization-has-not-enabled-statement-replies)))

(defn- update-statement [statement modify-id & updates]
  (if (or (= modify-id (:modify-id statement)) (nil? (:modify-id statement)))
    (->> (apply assoc statement :modified (now) :modify-id (mongo/create-id) updates)
         (util/strip-nils)
         (sc/validate Statement))
    (fail! :error.statement-updated-after-last-save :statementId (:id statement))))

(defn statement-in-sent-state-allowed [{:keys [application] :as command}]
  (when (= (keyword (:state application)) :sent)
    (permit/valid-permit-types {:YI :all :YL :all :YM :all :VVVL :all :MAL :all} command)))

(defn upload-attachment-allowed [{{:keys [target]} :data {:keys [state]} :application :as command}]
  (if (and (= (-> target :type keyword) :statement) (= (keyword state) :sent))
    (statement-in-sent-state-allowed command)
    (att/if-not-authority-state-must-not-be #{:sent} command)))

(defn delete-attachment-allowed? [attachment-id {:keys [state statements] :as application}]
  (when (and (= (keyword state) :sent) (not (statement-in-sent-state-allowed {:application application})))
    (let [{target :target} (att/get-attachment-info application attachment-id)]
      (when (= (-> target :type keyword) :statement)
        (some (fn [{stat-id :id stat-state :state}]
                (and (= stat-id (:id target))
                     (not= (keyword stat-state) :given))) statements)))))

(defn update-draft [statement text status modify-id editor-id]
  (update-statement statement modify-id :state :draft :text text :status status :editor-id editor-id))

(defn give-statement [statement text status modify-id editor-id]
  (update-statement statement modify-id :state :given :text text :status status :given (now) :editor-id editor-id))

(defn update-reply-draft [{reply :reply :as statement} text nothing-to-add modify-id editor-id]
  (->> (assoc reply :text text :nothing-to-add (boolean nothing-to-add) :editor-id editor-id)
       (update-statement statement modify-id :state :replyable :reply)))

(defn reply-statement [{reply :reply :as statement} text nothing-to-add modify-id editor-id]
  (->> (assoc reply :text (when-not nothing-to-add text) :nothing-to-add (boolean nothing-to-add) :editor-id editor-id)
       (update-statement statement modify-id :state :replied :reply)))

(defn request-for-reply [{reply :reply modify-id :modify-id :as statement} text user-id]
  (->> (assoc reply :saateText text :nothing-to-add false :editor-id user-id)
       (update-statement statement modify-id :state :replyable :reply)))

(defn attachments-readonly-updates [{app-id :id} statement-id]
  (att/attachment-array-updates app-id (comp #{statement-id} :id :target) :readOnly true))

(defn validate-selected-persons [{{selectedPersons :selectedPersons} :data}]
  (let [non-blank-string-keys (when (some
                                      #(some (fn [k] (or (-> % k string? not) (-> % k ss/blank?))) [:email :name :text])
                                      selectedPersons)
                                (fail :error.missing-parameters))
        has-invalid-email (when (some
                                  #(not (v/email-and-domain-valid? (:email %)))
                                  selectedPersons)
                            (fail :error.email))]
    (or non-blank-string-keys has-invalid-email)))
;;
;; Statement givers
;;

(defn fetch-organization-statement-givers [org-or-id]
  (let [organization (if (map? org-or-id) org-or-id (organization/get-organization org-or-id))
        statement-givers (->> (or (:statementGivers organization) [])
                              (sort-by (juxt :text :name)))]
    (ok :data statement-givers)))

;;
;; Statuses
;;

(defn possible-statement-statuses [{permit-type :permitType municipality :municipality :as application}]
  (let [{:keys [version url]} (-> (organization/resolve-organization municipality permit-type)
                                  (get-in [:krysp (keyword permit-type)]))
        yht-version           (when version (mapping-common/get-yht-version permit-type version))]
    ;; Even without url the KRYSP version might exist.
    (if (and (permit/get-metadata permit-type :extra-statement-selection-values)
             (or (util/compare-version >= yht-version "2.1.5")
                 (ss/blank? url)))
      (set statement-statuses)
      (set statement-statuses-limited-options))))
