(ns lupapalvelu.statement
  (:require [lupapalvelu.action :as action]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.backing-system.krysp.kuntagml-yht-version :as yht-version]
            [lupapalvelu.child-to-attachment :as child-to-attachment]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.integrations.messages :as msgs]
            [lupapalvelu.logging :as logging]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.statement-schemas :as statement-schemas]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.dns :as dns]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [taoensso.timbre :refer [warnf error]]))

(def Statement statement-schemas/Statement)
(def StatementGiver statement-schemas/StatementGiver)

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
  (when-let [statement-id (or statementId (:id target))]
    (let [{{statement-email :email} :person} (get-statement application statement-id)]
      (when-not (= (ss/canonize-email statement-email) (ss/canonize-email user-email))
        (fail :error.not-statement-owner)))))

(defn authority-or-statement-owner-applicant [{{role :role} :user :as command}]
  (when-not (or
              (= :authority (keyword role))
              (and (= :applicant (keyword role)) (nil? (statement-owner command))))
    (fail :error.not-authority-or-statement-owner-applicant)))

(defn statement-replyable [{{statement-id :statementId} :data application :application}]
  (when statement-id
    (when-not (->> (get-statement application statement-id) :state keyword #{:replyable})
      (fail :error.statement-is-not-replyable))))

(defn reply-visible [{{statement-id :statementId} :data application :application}]
  (when statement-id
    (when-not (->> (get-statement application statement-id)
                   :state
                   keyword
                   statement-schemas/post-repliable-states)
      (fail :error.statement-reply-is-not-visible))))

(defn reply-not-visible [{{statement-id :statementId} :data application :application}]
  (when statement-id
    (when-not (->> (get-statement application statement-id)
                   :state
                   keyword
                   statement-schemas/pre-repliable-states)
      (fail :error.statement-reply-is-already-visible))))

(defn statement-not-given [{{:keys [statementId target]} :data application :application}]
  (when-let [statement-id (or statementId (:id target))]
    (when-not (->> statement-id
                   (get-statement application)
                   :state
                   keyword
                   statement-schemas/pre-given-states)
      (fail :error.statement-already-given))))

(defn statement-given [{{:keys [statementId]} :data application :application}]
  (when statementId
    (when-not (->> statementId
                   (get-statement application)
                   :state
                   keyword
                   statement-schemas/post-given-states)
      (fail :error.statement-not-given))))

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

(defn give-statement [statement text status modify-id editor-id in-attachment given]
  (update-statement statement modify-id
                    :state :given
                    :text text
                    :status status
                    :given given
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

(defn validate-due-date
  "If duedate is given it cannot be in the past."
  [{:keys [data created]}]
  (when-let [duedate (:dueDate data)]
    (let [today-starts (-> (date/zoned-date-time created)
                           (date/with-time 0)
                           date/timestamp)]
      (when-not (> duedate today-starts)
        (fail :email.due-date-cant-be-in-the-past)))))

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
        received-ts (-> xml-edn (get-in [:AsianTunnusVastaus :VastaanotettuPvm]) date/timestamp)

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

(def- statement-statuses-limited-options
  ["puollettu" "ei-puollettu" "ehdollinen"])

(defn possible-statement-statuses [{{permit-type :permitType} :application org :organization}]
  (let [{:keys [version url]} (get-in @org [:krysp (keyword permit-type)])
        yht-version           (when (ss/not-blank? version) (yht-version/get-yht-version permit-type version))]
    ;; Even without url the KRYSP version might exist.
    (if (and (permit/get-metadata permit-type :extra-statement-selection-values)
             (or (util/compare-version >= yht-version "2.1.5")
                 (ss/blank? url)))
      (set statement-schemas/statement-statuses)
      (set statement-statuses-limited-options))))

(defn generate-statement-attachment
  ([user application statement-id lang]
   (child-to-attachment/create-attachment-from-children user
                                                        application
                                                        :statements
                                                        statement-id
                                                        lang))
  ([user application statement-id]
   (generate-statement-attachment user application statement-id "fi")))


(defn fix-missing-statement-attachments
  "Checks if the any of the application statements misses their attachments and generates
  them. Called from `fix-missing-statements` batchrun."
  [application-id]
  (if-let [{:keys [organization statements attachments]
            :as   application} (some->> application-id (mongo/by-id :applications))]
    (when (seq statements)
      (let [user     (usr/batchrun-user [organization])
            good-ids (some->> attachments
                              (keep (fn [{:keys [source]}]
                                      (when (util/=as-kw (:type source) :statements)
                                        (:id source))))
                              set)]
        (logging/with-logging-context {:applicationId application-id}
          (doseq [{:keys [id in-attachment]} (filter :given statements)
                  :when                      (not (or in-attachment
                                                      (contains? good-ids id)))]
            (generate-statement-attachment user application id)))))
    (error "Application" application-id "not found.")))
