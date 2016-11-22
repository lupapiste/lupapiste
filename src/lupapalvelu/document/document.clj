(ns lupapalvelu.document.document
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info infof warn warnf error]]
            [monger.operators :refer :all]
            [sade.core :refer [ok fail fail! unauthorized! now]]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.action :refer [update-application] :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.assignment :as assignment]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as usr]
            [lupapalvelu.wfs :as wfs]
            [clj-time.format :as tf]))

;;
;; Validators
;;


(defn- created-after-verdict? [document application]
  (if (contains? states/post-verdict-states (keyword (:state application)))
    (let [verdict-history-item (->> (app/state-history-entries (:history application))
                                    (filter #(= (:state %) "verdictGiven"))
                                    (sort-by :ts)
                                    last)]
      (when-not verdict-history-item
        (error "Application in post-verdict, but doesnt have verdictGiven state in history"))
      (> (:created document) (:ts verdict-history-item)))
    false))

(defn approved? [document]
  (= "approved" (get-in document [:meta :_approved :value])))

(defn user-can-be-set? [user-id application]
  (and (auth/has-auth? application user-id) (domain/no-pending-invites? application user-id)))

(defn create-doc-validator [{{documents :documents permit-type :permitType} :application}]
  ;; Hide the "Lisaa osapuoli" button when application contains "party" type documents and more can not be added.
  (when (and
          (not (permit/multiple-parties-allowed? permit-type))
          (some (comp (partial = "party") :type :schema-info) documents))
    (fail :error.create-doc-not-allowed)))

(defn user-can-be-set-validator [{{user-id :userId} :data application :application}]
  (when-not (or (ss/blank? user-id) (user-can-be-set? user-id application))
    (fail :error.application-does-not-have-given-auth)))

(defn- deny-remove-of-non-removable-doc [{schema-info :schema-info}]
  ; removable flag can be overwritten per document
  (not (:removable schema-info)))

(defn- deny-remove-of-primary-operation [document application]
  (= (get-in document [:schema-info :op :id]) (get-in application [:primaryOperation :id])))

(defn- deny-remove-of-last-document [{schema-info :schema-info} {documents :documents}]
  (when schema-info
    (let [info (:info (schemas/get-schema schema-info))
          doc-count (count (domain/get-documents-by-name documents (:name info)))]
      (and (:deny-removing-last-document info) (<= doc-count 1)))))

(defn- deny-remove-for-non-authority-user [user {schema-info :schema-info}]
  (and (not (usr/authority? user))
       schema-info
       (get-in (schemas/get-schema schema-info) [:info :removable-only-by-authority])))

(defn- deny-remove-of-non-post-verdict-document [document {state :state :as application}]
  (and (contains? states/post-verdict-states (keyword state)) (not (created-after-verdict? document application))))

(defn remove-doc-validator [{data :data user :user application :application}]
  (if-let [document (when application (domain/get-document-by-id application (:docId data)))]
    (cond
      (deny-remove-of-non-removable-doc document)             (fail :error.not-allowed-to-remove-document)
      (deny-remove-for-non-authority-user user document)      (fail :error.action-allowed-only-for-authority)
      (deny-remove-of-last-document document application)     (fail :error.removal-of-last-document-denied)
      (deny-remove-of-primary-operation document application) (fail :error.removal-of-primary-document-denied)
      (deny-remove-of-non-post-verdict-document document application) (fail :error.document.post-verdict-deletion))))


(defn validate-post-verdict-party-doc
  "Only non-approved documents that are added after verdict can be edited in post-verdict-states"
  [key {:keys [application data]}]
  (when-let [doc (when (and application (contains? states/post-verdict-states (keyword (:state application))))
                   (domain/get-document-by-id application (get data key)))]
    (when (and (get-in doc [:schema-info :post-verdict-party]) (approved? doc))
      (fail :error.document.post-verdict-update))))

(defn doc-disabled-validator
  "Deny action if document is marked as disabled"
  [key {:keys [application data]}]
  (when-let [doc (and (get data key) (domain/get-document-by-id application (get data key)))]
    (when (:disabled doc)
      (fail :error.document.disabled))))

(defn validate-disableable-schema
  "Checks if document can be disabled"
  [key {:keys [application data]}]
  (when-let [doc (and (get data key) (domain/get-document-by-id application (get data key)))]
    (when-not (get-in doc [:schema-info :disableable])
      (fail :error.document.not-disableable))))


;;
;; KTJ-info updation
;;

(def ktj-format (tf/formatter "yyyyMMdd"))
(def output-format (tf/formatter "dd.MM.yyyy"))

(defn fetch-and-persist-ktj-tiedot [application document property-id time]
  (when-let [ktj-tiedot (wfs/rekisteritiedot-xml property-id)]
    (let [doc-updates [[[:kiinteisto :tilanNimi] (or (:nimi ktj-tiedot) "")]
                       [[:kiinteisto :maapintaala] (or (:maapintaala ktj-tiedot) "")]
                       [[:kiinteisto :vesipintaala] (or (:vesipintaala ktj-tiedot) "")]
                       [[:kiinteisto :rekisterointipvm] (or
                                                          (try
                                                            (tf/unparse output-format (tf/parse ktj-format (:rekisterointipvm ktj-tiedot)))
                                                            (catch Exception e (:rekisterointipvm ktj-tiedot)))
                                                          "")]]
          schema (schemas/get-schema (:schema-info document))
          updates (filter (partial doc-persistence/update-key-in-schema? (:body schema)) doc-updates)]
      (doc-persistence/persist-model-updates application "documents" document updates time))))


;;
;; Document approvals
;;

(defn- validate-approvability [{{:keys [doc path collection]} :data application :application}]
  (let [path-v (if (ss/blank? path) [] (ss/split path #"\."))
        document (doc-persistence/by-id application collection doc)]
    (if document
      (when-not (model/approvable? document path-v)
        (fail :error.document-not-approvable))
      (fail :error.document-not-found))))

(defn- ->approval-mongo-model
  "Creates a mongo update map of approval data.
   To be used within model/with-timestamp."
  [path approval]
  (let [mongo-path (if (ss/blank? path) "documents.$.meta._approved" (str "documents.$.meta." path "._approved"))]
    {$set {mongo-path approval
           :modified (model/current-timestamp)}}))

(defn approve [{{:keys [id doc path collection]} :data user :user created :created :as command} status]
  (or
   (validate-approvability command)
   (model/with-timestamp created
     (let [approval (model/->approved status user)]
       (update-application
        command
        {collection {$elemMatch {:id doc}}}
        (->approval-mongo-model path approval))
       approval))))


;;
;; Assignments
;;

(defn- document-assignment-info
  "Return document info as assignment target"
  [operations {{name :name doc-op :op} :schema-info id :id :as doc}]
  (let [accordion-datas (schemas/resolve-accordion-field-values doc)
        op-description  (:description (util/find-by-id (:id doc-op) operations))]
    (util/assoc-when-pred {:id id :type-key (ss/join "." [name "_group_label"])} ss/not-blank?
                          :description (or op-description (ss/join " " accordion-datas)))))

(defn- describe-parties-assignment-targets [application]
  (->> (domain/get-documents-by-type application :party)
       (remove :disabled)
       (sort-by tools/document-ordering-fn)
       (map (partial document-assignment-info nil))))

(defn- describe-non-party-document-assignment-targets [{:keys [documents primaryOperation secondaryOperations] :as application}]
  (let [party-doc-ids (set (map :id (domain/get-documents-by-type application :party)))
        operations (cons primaryOperation secondaryOperations)]
    (->> (remove (comp party-doc-ids :id) documents)
         (remove :disabled)
         (sort-by tools/document-ordering-fn)
         (map (partial document-assignment-info operations)))))

(assignment/register-assignment-target! :parties describe-parties-assignment-targets)

(assignment/register-assignment-target! :documents describe-non-party-document-assignment-targets)
