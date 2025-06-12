(ns lupapalvelu.document.document-api
  (:require [clojure.set :as set]
            [lupapalvelu.action :refer [defcommand defquery] :as action]
            [lupapalvelu.application :as application]
            [lupapalvelu.application.designer :as designer-app]
            [lupapalvelu.assignment :as assignment]
            [lupapalvelu.backing-system.core :as bs]
            [lupapalvelu.building-site :as bsite]
            [lupapalvelu.document.approval :as approval]
            [lupapalvelu.document.document :refer :all]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain :refer [get-document-by-name]]
            [lupapalvelu.domain-attachment :as domain-att]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.states :as states]
            [lupapalvelu.statistics.db :as stats-db]
            [lupapalvelu.task-util :as task-util]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer :all]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

;; Action category: documents & tasks

(defn- build-document-params [{application-id :id} {document-id :id info :schema-info}]
  {:id  application-id
   :doc document-id
   :docId document-id
   :documentId document-id
   ;; NOTE: temporary before foreman-consent is updated (maybe the whole foreman-api?)
   :foreman-doc-id document-id
   :schemaName (:name info)
   :collection "documents"})

(defn- build-task-params [{application-id :id} {document-id :id}]
  {:id  application-id
   :doc document-id
   :docId document-id
   :documentId document-id
   :collection "tasks"})

(defmethod action/allowed-actions-for-category :documents
  [{:keys [application data] :as command}]
  (if-let [docId (:docId data)]
    (action/allowed-actions-for-collection [(util/find-by-id docId (:documents application))]
                                           :documents build-document-params command)
    (action/allowed-actions-for-collection :documents build-document-params command)))

(defmethod action/allowed-actions-for-category :tasks
  [command]
  (action/allowed-actions-for-collection :tasks build-task-params command))

(defn addable-by-state?
  [default-states]
  (fn [{{schema-name :schemaName} :data {state :state schema-version :schema-version} :application}]
    (when (ss/not-blank? schema-name)
      (when-not (-> (schemas/get-schema schema-version schema-name)
                    (state-valid-by-schema? :addable-in-states default-states state))
        (fail :error.document.post-verdict-addition)))))

(defquery document
  {:parameters       [:id doc collection]
   :categories       #{:documents :tasks}
   :states           states/all-states
   :input-validators [doc-persistence/validate-collection]
   :permissions      [{:required [:application/read]}]}
  [{:keys [application user] :as command}]
  (if-let [document (tools/by-id application collection doc)]
    (ok :document (application/process-document-or-task command document))
    (fail :error.document-not-found)))

(defquery document-by-name
  {:parameters       [:id documentName]
   :categories       #{:documents :tasks}
   :states           states/all-states
   :input-validators [(partial action/non-blank-parameters [:id :documentName])]
   :permissions      [{:required [:application/read]}]}
  [{:keys [application user]}]
  (if-let [document (get-document-by-name application documentName)]
    (ok :document document)
    (fail :error.document-not-found)))

;;
;; CRUD
;;

(def document-edit-permissions
  [{:context  {:application {:state #{:draft}}}
    :required [:application/read :document/edit-draft]}
   {:required [:application/read :document/edit]}])

(defn- some-document-of-name-active? [schema-name documents]
  (->> documents
       (filter #(= schema-name (get-schema-name %)))
       (not-every? :disabled)))

(defcommand create-doc
  {:parameters [:id :schemaName]
   :categories  #{:documents}
   :optional-parameters [updates fetchRakennuspaikka]
   :input-validators [(partial action/non-blank-parameters [:id :schemaName])]
   :pre-checks [(addable-by-state? states/create-doc-states)
                create-doc-validator
                foreman/no-foreman]
   :permissions [{:context  {:application {:state #{:draft}}}
                  :required [:application/edit-draft]}
                 {:required [:application/edit]}]}
  [{{schema-name :schemaName} :data {:keys [documents schema-version]} :application :as command}]

  ;; This only-one-active check would be better to do in the pre-checks.
  ;; But having this in the pre-check causes issues with the party-add button in docgen on the frontend
  (if (and (->> schema-name (schemas/get-schema schema-version) :info :only-one-active)
           (some-document-of-name-active? schema-name documents))
    (fail :error.only-one-active)
    (let [document (doc-persistence/do-create-doc! command schema-name updates)]
      (when fetchRakennuspaikka
        (let [property-id (or (tools/get-update-item-value updates "kiinteisto.kiinteistoTunnus")
                              (get-in command [:application :propertyId]))]
          (bsite/fetch-and-persist-ktj-tiedot (:application command) document property-id (now))))
      (ok :doc (:id document)))))

(defn do-remove-doc! [command document id docId]
  (doc-persistence/remove! command document)
  (assignment/remove-target-from-assignments id docId))

(defcommand remove-doc
  {:parameters       [id docId]
   :categories       #{:documents}
   :input-validators [(partial action/non-blank-parameters [:id :docId])]
   :contexts         [document-context]
   :permissions      document-edit-permissions
   :pre-checks       [(editable-by-state? #{:draft :answered :open :submitted :complementNeeded})
                      (action/some-pre-check usr/validate-authority
                                             doc-disabled-validator)
                      remove-doc-validator]
   :on-success       [(fn [command status]
                        (when (some-> command :document :schema-info :op)
                          (stats-db/post-reset-processing command status)))]}
  [{:keys [document] :as command}]
  (do-remove-doc! command document id docId)
  (ok))

(defn- update-document-assignment-statuses [app-id doc-id doc-status]
  (if (= doc-status "disabled")
    (assignment/update-assignments {$and [{:application.id app-id}
                                          {:targets {$size 1}}
                                          {:targets.id doc-id}]}
                                   {$set {:status "canceled"}})
    (assignment/set-assignment-status app-id doc-id "active")))

(defcommand set-doc-status
  {:description      "Set document status to disabled: true or disabled: false"
   :parameters       [id docId value]
   :categories       #{:documents}
   :input-validators [(partial action/non-blank-parameters [:id :docId])
                      (partial action/select-parameters [:value] #{"enabled" "disabled"})]
   :states           states/post-verdict-states
   :contexts         [document-context]
   :permissions      document-edit-permissions
   :pre-checks       [(editable-by-state? nil)       ; edition defined solely by document schema
                      document-disableable-precheck
                      validate-document-is-pre-verdict-or-approved]}
  [command]
  (doc-persistence/set-disabled-status command docId value)
  (update-document-assignment-statuses id docId value)
  (ok))

(defcommand update-doc
  {:description      "Updates the document contents (form fields)"
   :parameters       [id doc updates]
   :categories       #{:documents}
   :input-validators [(partial action/non-blank-parameters [:id :doc])
                      (partial action/vector-parameters [:updates])]
   :contexts         [document-context]
   :permissions      document-edit-permissions
   :pre-checks       update-doc-pre-checks
   :on-success       bs/update-callback}
  [{:keys [application created document user] :as command}]
  (assoc (doc-persistence/update! command doc updates "documents")
         :authModel (action/maybe-update-document-auth-model command)
         :documentApprovals (approval/documentApprovals document updates created)))

(defcommand update-doc-identifier
  {:description      "Update document identifier (e.g., tunnus). Separate
  command since the allowed states are user role specific."
   :parameters       [:id doc identifier value]
   :categories       #{:documents}
   :input-validators [(partial action/non-blank-parameters [:id :doc :identifier])]
   :contexts         [document-context]
   :permissions      [{:context  {:application {:state #{:draft}}}
                       :required [:application/read :document/edit-draft]}
                      {:context  {:application {:state states/update-doc-states}}
                       :required [:application/read :document/edit]}
                      {:context  {:application {:state states/all-application-or-archiving-project-states}}
                       :required [:application/read :document/edit-identifiers]}]
   :pre-checks       [is-identifier]}
  [command]
  (doc-persistence/update! command doc [[identifier (ss/trim value)]] "documents"))

(defcommand update-task
  {:description      "Edit an individual task details. Typically only non-sent tasks can be
  edited. Converted applications are an exception, since the data may need to be fixed at
  any given state."
   :parameters       [id doc updates]
   :categories       #{:tasks}
   :input-validators [(partial action/non-blank-parameters [:id :doc])
                      (partial action/vector-parameters [:updates])]
   :permissions      [{:required [:application/edit :task/edit]
                       :context  {:application {:state states/all-application-states}}}]
   :pre-checks       [(fn [{:keys [application data] :as command}]
                        ;; When a task view is opened, the `taskId` parameter is part of
                        ;; the `allowed-actions` call.
                        (when-let [task-id (or (:doc data) (:taskId data))]
                          (let [task   (util/find-by-id task-id (:tasks application))
                                states (disj task-util/valid-application-states :sent)
                                state  (-> application :state keyword)]

                            (cond
                              (nil? task) (fail :error.document-not-found :doc-id task-id)

                              (:facta-imported application) nil

                              (not (contains? states state))
                              (fail :error.command-illegal-state :state state)

                              :else
                              (when-not (some-> task
                                                :state
                                                (util/=as-kw :requires_user_action))
                                (fail :error.task-is-not-editable))))))]}
  [command]
  (doc-persistence/update! command doc updates "tasks"))

(defcommand remove-document-data
  "Remove document data under some path (recurring sections).
   This previously supported also tasks, but there are no task schemas that would use it, so the support was removed."
  {:parameters       [:id doc path]
   :input-validators [(partial action/non-blank-parameters [:id :doc])]
   :categories       #{:documents}
   :contexts         [document-context]
   :permissions      [{:context  {:application {:state #{:draft}}}
                       :required [:application/read :document/edit-draft]}
                      {:context  {:application {:state (disj states/create-doc-states :draft)}}
                       :required [:application/read :document/edit]}
                      {:context  {:application {:state states/post-verdict-states}}
                       :required [:application/read :document/edit-post-verdict]}]
   :pre-checks       [(editable-by-state? (set/union states/create-doc-states
                                                     states/post-verdict-but-terminal))
                      doc-disabled-validator]}
  [command]
  (doc-persistence/remove-document-data command doc [path] :documents))

;;
;; Document validation
;;

(defquery fetch-validation-errors
  {:parameters  [:id]
   :permissions [{:required [:application/read]}]
   :states      states/all-states}
  [command]
  (ok :results (into [] (application/pertinent-validation-errors command))))

;;
;; Document approvals
;;

(defn designer-has-consent?
  "If the document is a designer document, check that if the consent is required, it has
  been either given by the designer (designerConsent) or someone has uploaded a consent
  attachment."
  [{:keys [document application data]}]
  (when (and document (tools/consent-required? application))
    ;; Use subtype value here because it is the same for both regular suunnittelija and
    ;; paasuunnittelija
    (when (:use-designer-approval (:info (model/get-document-schema document)))
      (when (not (or (designer-app/designer-document->designer-consent document)
                     (domain-att/get-attachment-by-target application "suunnittelija-suostumus" (:doc data))))
        (fail :error.designer.consent-missing :reason :error.designer.consent-missing)))))

(defn designer-document-is-valid?
  "If the document is a designer document, check that the document is valid
  (the required fields are filled.). If the designer consent is not required, the validity
  is not required either."
  [{:keys [document application]}]
  (when (and document (tools/consent-required? application))
    (when (:use-designer-approval (:info (model/get-document-schema document)))
      (let [errors (model/validate application document)]
        (when-not (empty? errors)
          (fail :error.missing-parameters :reason :error.missing-parameters))))))

(defcommand approve-doc
  {:parameters       [:id :doc :path]
   :categories       #{:documents}
   :input-validators [(partial action/non-blank-parameters [:id :doc :collection])]
   :contexts         [document-context]
   :permissions      [{:required [:application/edit :document/approve]}]
   :pre-checks       [(editable-by-state? states/approve-doc-states)
                      doc-disabled-validator
                      approval/validate-approvability
                      designer-document-is-valid?
                      designer-has-consent?]}
  [command]
  (let [;; Store extra flag to mark if this document used "Hyväksy" instead of regular "OK" action
        extra (when (:use-designer-approval (:info (model/get-document-schema (:document command))))
                {:use-designer-approval true})]
    (ok :documentApprovals (approval/approve command "approved" extra))))

(defcommand reject-doc
  {:parameters       [:id :doc :path]
   :categories       #{:documents}
   :input-validators [(partial action/non-blank-parameters [:id :doc :collection])]
   :contexts         [document-context]
   :permissions      [{:required [:application/edit :document/approve]}]
   :pre-checks       [(editable-by-state? states/approve-doc-states)
                      doc-disabled-validator
                      approval/validate-approvability]}
  [command]
  (ok :documentApprovals (approval/approve command "rejected")))

(defcommand reject-doc-note
  {:description      "Explanatory note regarding the reject reason. Adding
  note updates application modified timestamp but not the approval
  timestamp"
   :parameters       [:id :doc :path note]
   :categories       #{:documents}
   :input-validators [(partial action/non-blank-parameters [:id :doc :collection])]
   :contexts         [document-context]
   :permissions      [{:required [:application/edit :document/approve]}]
   :pre-checks       [(editable-by-state? states/approve-doc-states)
                      approval/validate-approvability]}
  [command]
  (ok :documentApprovals (approval/set-rejection-note command note)))

;;
;; Set party to document
;;

(defcommand set-user-to-document
  {:parameters       [id documentId userId path]
   :categories       #{:documents}
   :contexts         [document-context]
   :permissions      document-edit-permissions
   :input-validators [(partial action/non-blank-parameters [:id :documentId])]
   :pre-checks       [(editable-by-state? states/update-doc-states)
                      user-can-be-set-validator
                      doc-disabled-validator]
   :on-success       bs/update-callback}
  [{:keys [created application user]}]
  (doc-persistence/do-set-user-to-document application documentId userId path created user)
  (ok))

(defcommand set-current-user-to-document
  {:parameters       [id documentId path]
   :categories       #{:documents}
   :contexts         [document-context]
   :permissions      document-edit-permissions
   :input-validators [(partial action/non-blank-parameters [:id :documentId])]
   :pre-checks       [(fn [{user :user}]
                        (when-not (usr/applicant? user)
                          (fail :error.unauthorized)))
                      (editable-by-state? states/update-doc-states)
                      doc-disabled-validator]
   :on-success       bs/update-callback}
  [{:keys [created application user]}]
  (doc-persistence/do-set-user-to-document application documentId (:id user) path created user)
  (ok))

(defcommand set-company-to-document
  {:description      "Updates company information on the document.The
  contact person information is filled only if either the current user
  is a company member or the application has a company member in the
  auth array. Otherwise, left empty."
   :parameters       [id documentId companyId path]
   :categories       #{:documents}
   :contexts         [document-context]
   :permissions      document-edit-permissions
   :input-validators [(partial action/non-blank-parameters [:id :documentId])]
   :pre-checks       [(editable-by-state? states/update-doc-states)
                      doc-disabled-validator]
   :on-success       bs/update-callback}
  [{:keys [user created application document]}]
  (doc-persistence/do-set-company-to-document application
                                              document
                                              companyId
                                              path
                                              (usr/get-user-by-id (:id user))
                                              created)
  (ok))

;; Consent endpoints

(defn get-designer-email [foreman-doc]
  (ss/canonize-email (get-in foreman-doc [:data :yhteystiedot :email :value])))

(defn user-authorized-as-designer? [user-email foreman-doc]
  (boolean (some->> user-email
                    (= (get-designer-email foreman-doc)))))

(defn is-designer-user
  "Pre-check that fails if the selected foreman is not the current user"
  [{:keys [user document]}]
  (when document
    (when-not (user-authorized-as-designer? (:email user) document)
     (fail :error.user-is-not-the-designer))))

(defn is-designer-doc [{:keys [document]}]
  (when document
    (when-not (designer-document? document)
      (fail :error.wrong-document-schema))))

(defcommand designer-consent
  {:description      "Designer can give a consent for the role."
   :parameters       [id documentId consent]
   :categories       #{:documents}
   :input-validators [{:id             ssc/NonBlankStr
                       :documentId     ssc/NonBlankStr
                       :consent        sc/Bool}]
   :contexts         [document-context]
   :permissions      [{:required [:application/edit]}]
   :pre-checks       (into update-doc-pre-checks
                           [is-designer-doc
                            is-designer-user])}
  [{:keys [created] :as command}]
  (action/update-application command
                             {:documents {$elemMatch {:id documentId}}}
                             {$set {:documents.$.data.designerConsent.value consent
                                    ;; Remember to update timestamp, to also cancel approved state on changes
                                    :documents.$.data.designerConsent.modified created}})
  (ok {:authModel (action/maybe-update-document-auth-model command)}))

(defquery designer-consent-given
  {:description      "Pseudo query to tell if document has the designerConsent field set"
   :parameters       [id documentId]
   :categories       #{:documents}
   :input-validators [{:id         ssc/NonBlankStr
                       :documentId ssc/NonBlankStr}]
   :permissions      [{:required [:application/read]}]
   :contexts         [document-context]
   :pre-checks       [(fn [{:keys [document]}]
                        (when document
                          (when-not (designer-app/designer-document->designer-consent document)
                            (fail :no-consent))))]}
  [_]
  (ok))

(defquery designer-applications
  {:description      "Designers for the application"
   :parameters       [id]
   :categories       #{:documents}
   :input-validators [{:id             ssc/NonBlankStr}]
   :permissions      [{:required [:application/read]}]}
  [{:keys [user application]}]
  (->> (for [doc (domain/get-documents-by-subtype (:documents application) :suunnittelija)]
         (let [{:keys [kuntaRoolikoodi henkilotiedot muuSuunnittelijaRooli]} (:data doc)
               schema (model/get-document-schema doc)
               ;; select value i18n key has to be retrived from select schema
               kunta-roolikoodi-field (model/find-by-name (:body schema) [:kuntaRoolikoodi])
               kunta-roolikoodi-option (when-let [v (:value kuntaRoolikoodi)]
                                         (model/find-by-name (:body kunta-roolikoodi-field) [(keyword v)]))]
           {:id (:id doc)
            :state (designer-app/designer-document->approval-state application doc)
            ;; pääsuunnittelija schema doesn't have the role field
            :kuntaRoolikoodi (if (= "paasuunnittelija" (:name (:info schema)))
                               {:value "paasuunnittelija"
                                :i18nkey "osapuoli.suunnittelija.kuntaRoolikoodi.pääsuunnittelija"}
                               (assoc kuntaRoolikoodi :i18nkey (:i18nkey kunta-roolikoodi-option)))
            :muuSuunnittelijaRooli muuSuunnittelijaRooli
            :henkilotiedot (select-keys henkilotiedot [:etunimi :sukunimi])}) )
       (into [])
       (ok :designers)))
