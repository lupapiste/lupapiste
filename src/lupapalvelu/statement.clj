(ns lupapalvelu.statement
  (:require [clojure.set]
            [taoensso.timbre :refer [warnf]]
            [monger.operators :refer :all]
            [schema.core :refer [defschema] :as sc]
            [sade.core :refer :all]
            [sade.common-reader :as cr]
            [sade.util :as util]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [lupapalvelu.action :as action]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.integrations.ely :as ely]
            [lupapalvelu.integrations.messages :as msgs]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.user :as usr]
            [lupapalvelu.xml.krysp.mapping-common :as mapping-common]
            [sade.dns :as dns]))

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
  {:userId                          usr/Id          ;; id for user
   (sc/optional-key :id)            sc/Str          ;; 'official' statement giver id in organization
   :text                            sc/Str          ;; text field describing statement giver role or job
   :email                           ssc/Email       ;; email
   :name                            sc/Str})        ;; full name of the statement giver

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
  {:id                              ssc/ObjectIdStr ;; statement id
   :state                           (apply sc/enum statement-states) ;; handling state of the statement
   :requested                       ssc/Timestamp   ;; when requested
   (sc/optional-key :saateText)     sc/Str          ;; cover note for statement, written by authority
   (sc/optional-key :status)        (apply sc/enum statement-statuses) ;; status indicator
   (sc/optional-key :text)          sc/Str          ;; statement text written by statement giver
   (sc/optional-key :dueDate)       ssc/Timestamp   ;; due date for statement to be given
   (sc/optional-key :given)         ssc/Timestamp   ;; when given
   (sc/optional-key :reminder-sent) ssc/Timestamp   ;; for remiders sent week after the request
   (sc/optional-key :modified)      ssc/Timestamp   ;; last modified
   (sc/optional-key :duedate-reminder-sent) ssc/Timestamp ;; for reminders sent after due date exceeded
   (sc/optional-key :modify-id)     sc/Str          ;; id for restrict overlapping modifications
   (sc/optional-key :editor-id)     usr/Id          ;; id of the user last edited the statement
   (sc/optional-key :reply)         Reply
   :person                          StatementGiver
   (sc/optional-key :external)      ExternalData
   (sc/optional-key :metadata)      {sc/Any sc/Any}
   (sc/optional-key :in-attachment) sc/Bool})

(defn create-statement [now saate-text due-date person & [metadata external]]
  (sc/validate Statement
               (cond-> {:id        (mongo/create-id)
                        :person    person
                        :requested now
                        :state     :requested}
                 saate-text     (assoc :saateText saate-text)
                 due-date       (assoc :dueDate due-date)
                 (seq metadata) (assoc :metadata metadata)
                 (seq external) (assoc :external external))))

(defn get-statement [{:keys [statements]} id]
  (util/find-by-id id statements))

(defn statement-owner [{{:keys [statementId target]} :data {user-email :email} :user application :application}]
  (let [{{statement-email :email} :person} (get-statement application (or statementId (:id target)))]
    (when-not (= (ss/canonize-email statement-email) (ss/canonize-email user-email))
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

(defn statement-not-given [{{:keys [statementId target]} :data application :application}]
  (when-not (->> (or statementId (:id target)) (get-statement application) :state keyword pre-given-states)
    (fail :error.statement-already-given)))

(defn statement-given [{{:keys [statementId]} :data application :application}]
  (when-not (->> statementId (get-statement application) :state keyword post-given-states)
    (fail :error.statement-not-given)))

(defn not-ely-statement [{{:keys [statementId]} :data application :application}]
  (when (= "ely" (->> statementId (get-statement application) :external :partner))
    (fail :error.ely-statement)))

(defn text-or-attachment-provided [{{:keys [text in-attachment statementId]} :data application :application}]
  (cond
    (and in-attachment
         (->> (att/get-attachments-by-target-type-and-id application {:type "statement" :id statementId})
              (not-any? #(= (att/attachment-type-coercer (:type %))
                            {:type-group :ennakkoluvat_ja_lausunnot :type-id :lausunto}))))
    (fail :error.statement-attachment-missing)

    (and (not in-attachment)
         (ss/blank? text))
    (fail :error.statement-text-or-attachment-required)))

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

(defmethod att/upload-to-target-allowed :statement [command]
  (or (statement-in-sent-state-allowed command)
      (statement-not-given command)
      (statement-owner command)))

(defmethod att/edit-allowed-by-target :statement [{{attachment-id :attachmentId} :data user :user {:keys [statements] :as application} :application}]
  (cond (not (or (auth/application-authority? application user)
                 (auth/has-auth-role? application (:id user) :statementGiver)))
        (fail :error.unauthorized)

        (-> (att/get-attachment-info application attachment-id) (get-in [:target :id]) (util/find-by-id statements) :state #{:given})
        (fail :error.statement-already-given)))

(defmethod att/delete-allowed-by-target :statement [{{attachment-id :attachmentId} :data :keys [statements] :as application}]
  (when (-> (att/get-attachment-info application attachment-id) (get-in [:target :id]) (util/find-by-id statements) :state #{:given})
    (fail :error.statement-already-given)))

(defn update-draft [statement text status modify-id editor-id in-attachment]
  (update-statement statement modify-id
                    :state :draft
                    :text text
                    :status status
                    :editor-id editor-id
                    :in-attachment in-attachment))

(defn give-statement [statement text status modify-id editor-id in-attachment]
  (update-statement statement modify-id
                    :state :given
                    :text text
                    :status status
                    :given (now)
                    :editor-id editor-id
                    :in-attachment in-attachment))

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
  {$set (att/attachment-array-updates app-id (comp #{statement-id} :id :target) :readOnly true)})

(defn validate-selected-persons [{{selectedPersons :selectedPersons} :data}]
  (let [non-blank-string-keys (when (some
                                      #(some (fn [k] (or (-> % k string? not) (-> % k ss/blank?))) [:email :name :text])
                                      selectedPersons)
                                (fail :error.missing-parameters))
        has-invalid-email (when (some
                                  #(not (dns/email-and-domain-valid? (ss/canonize-email (:email %))))
                                  selectedPersons)
                            (fail :error.email))]
    (or non-blank-string-keys has-invalid-email)))

;;
;; Asianhallinta statement handling
;;

(defn validate-external-statement-update!
  "Ensure statement is external and has correct statement giver, compared to ftp-user.
  In ELY case lupapalvelu.integrations.ely/ely-statement-giver id should match with ftp-user."
  [statement ftp-user]
  (when-not (= ftp-user (get-in statement [:person :id]))
    (fail! :error.unauthorized :source ::validate-external-statement-update)))

(sc/defn handle-ah-response-message
  "LPK-3126 handler for :statement target IntegrationMessages"
  [responded-message :- msgs/IntegrationMessage
   xml-edn
   ftp-user
   created]
  (let [application-id (get-in xml-edn [:AsianTunnusVastaus :HakemusTunnus])
        partners-id (get-in xml-edn [:AsianTunnusVastaus :AsianTunnus])
        received-ts (-> xml-edn (get-in [:AsianTunnusVastaus :VastaanotettuPvm]) (cr/to-timestamp))

        statement-id (get-in responded-message [:target :id])
        application (domain/get-application-no-access-checking application-id [:statements])
        statement   (get-statement application statement-id)
        command (action/application->command {:id application-id})]
    (if statement
      (do
        (validate-external-statement-update! statement ftp-user)
        (when-let [updated (update-statement
                             (update statement :state keyword) ; Schema...
                             (:modify-id statement)
                             :external
                             (assoc (:external statement) :externalId partners-id :acknowledged received-ts))]
          (action/update-application command
                                     {:statements {$elemMatch {:id statement-id}}}
                                     {$set {:statements.$ updated :modified created}})))
      (warnf "No statement found for ah response-message (%s), statementId in original message was: %s", (:id responded-message) statement-id))))

(defn- get-giver [statement-data name]
  (if (ss/not-blank? (:giver statement-data))
    (str name " (" (:giver statement-data) ")")
    name))

(defn save-ah-statement-response
  "Save statement response data from asianhallinta LausuntoVastaus to application statement.
   Validation of required data has been made on reader side.
   Returns updated statement on success"
  [app ftp-user statement-data created]
  (when-let [statement (get-statement app (:id statement-data))]
    (validate-external-statement-update! statement ftp-user)
    (when-let [updated (update-statement
                         (update statement :state keyword)
                         (:modify-id statement)
                         :status (:status statement-data)
                         :state  :given
                         :given  (:given statement-data)
                         :text   (or (:text statement-data) "Lausunto liitteen\u00e4")
                         :person (update (:person statement) :name (partial get-giver statement-data))
                         :external (util/assoc-when (:external statement) :externalId (:externalId statement-data)))]
      (action/update-application (action/application->command app)
                                 {:statements {$elemMatch {:id (:id statement)}}}
                                 {$set {:statements.$ updated :modified created}})
      updated)))

;;
;; Statement givers
;;

(defn fetch-organization-statement-givers [org-or-id]
  (let [organization (if (map? org-or-id) org-or-id (organization/get-organization org-or-id))
        statement-givers (sort-by (juxt (comp ss/trim ss/lower-case :text)
                                        (comp ss/trim ss/lower-case :name))
                                  (or (:statementGivers organization) []))]
    (ok :data statement-givers)))

;;
;; Statuses
;;

(defn possible-statement-statuses [{{permit-type :permitType} :application org :organization}]
  (let [{:keys [version url]} (get-in @org [:krysp (keyword permit-type)])
        yht-version           (when (ss/not-blank? version) (mapping-common/get-yht-version permit-type version))]
    ;; Even without url the KRYSP version might exist.
    (if (and (permit/get-metadata permit-type :extra-statement-selection-values)
             (or (util/compare-version >= yht-version "2.1.5")
                 (ss/blank? url)))
      (set statement-statuses)
      (set statement-statuses-limited-options))))
