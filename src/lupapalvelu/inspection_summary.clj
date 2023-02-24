(ns lupapalvelu.inspection-summary
  (:require [clojure.string :as s]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.attachment.bind :as att-bind]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as operations]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pdf.html-template :as pdf-html]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer [now unauthorized fail ok]]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc :refer [defschema]]
            [taoensso.timbre :refer [trace debug debugf info infof warn error errorf fatal]]
            [clj-uuid :as uuid]
            [lupapalvelu.storage.file-storage :as storage]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.pdf.html-template-common :as common]
            [lupapalvelu.bulletin-report.page :as page]
            [lupapalvelu.pdf.html-templates.inspection-summary-template :as inspection-summary-template])
  (:import [java.io InputStream]))

(defschema InspectionSummaryTargets
  {:target-name                     sc/Str   ;Tarkastuskohde
   :id                              ssc/ObjectIdStr
   :finished                        sc/Bool
   (sc/optional-key :finished-by)   usr/SummaryUser
   (sc/optional-key :finished-date) ssc/Timestamp
   sc/Keyword                       sc/Any})

(defschema InspectionSummary
  {:id ssc/ObjectIdStr
   :name sc/Str
   :op {:id ssc/ObjectIdStr
        (sc/optional-key :name) sc/Str
        (sc/optional-key :description) sc/Str}
   :targets [InspectionSummaryTargets]
   (sc/optional-key :locked) sc/Bool})

(defn- split-into-template-items [text]
  (remove ss/blank? (map ss/trim (s/split-lines text))))

(defn inspection-summary-api-auth-admin-pre-check
  [{{:keys [organizationId]} :data organizations :user-organizations}]
  (when organizationId
    (when-not (:inspection-summaries-enabled (util/find-by-id organizationId organizations))
      unauthorized)))

(defn inspection-summaries-enabled
  [{organization :organization}]
  (when-not (some-> organization force :inspection-summaries-enabled)
    unauthorized))

(defn application-has-R-permit-type-pre-check
  [{{:keys [permitType]} :application}]
  (when-not (= (keyword permitType) :R)
    (fail :error.inspection-summary.invalid-permit-type)))

(defn operation-has-R-permit-type
  [{{:keys [operationId]} :data}]
  (when-not (= (keyword (operations/permit-type-of-operation operationId)) :R)
    (fail :error.inspection-summary.invalid-permit-type)))

(def attachment-target-type "inspection-summary-item")

(defn summary-target-attachment-predicate
  "Returns function, which can be used as predicate for filter.
  Predicate returns true on those attachments, who are targets of given target-id"
  [target-id]
  (fn [{{:keys [id type]} :target}]
    (and (= type attachment-target-type)
         (= id target-id))))

(defn- enrich-summary-targets [attachments targets]
  (letfn [(add-attachment-data [{tid :id :as target}]
            (let [target-attachments (filter (summary-target-attachment-predicate tid) attachments)]
              (assoc target :attachments (map #(select-keys % [:type :latestVersion :id]) target-attachments))))]
    (map add-attachment-data targets)))

(defn validate-that-summary-can-be-deleted
  "Inspection summary can be deleted if none of its targets contain attachments or are marked as finished by applicant"
  [{{summaries :inspection-summaries attachments :attachments} :application {:keys [summaryId]} :data action :action}]
  (let [summary-to-be-deleted (-> (util/find-by-id summaryId summaries)
                                  (update :targets (partial enrich-summary-targets attachments)))
        targets               (:targets summary-to-be-deleted)]
    (when (some #(or (-> % :finished true?)
                     (not (empty? (:attachments %)))) targets)
      (fail (util/kw-path :error action :non-empty)))))

(defn validate-summary-not-locked [{{summaries :inspection-summaries} :application {:keys [summaryId]} :data}]
  (when (:locked (util/find-by-id summaryId summaries))
    (fail :error.inspection-summary.locked)))

(defn validate-summary-found-in-application [{{summaries :inspection-summaries} :application {:keys [summaryId]} :data action :action}]
  (when (and summaryId (not (util/find-by-id summaryId summaries)))
    (fail (util/kw-path :error action :not-found))))

(defn validate-summary-target-found-in-application [{{summaries :inspection-summaries} :application {:keys [summaryId targetId]} :data action :action}]
  (when (and summaryId targetId (not (->> (util/find-by-id summaryId summaries)
                                          :targets
                                          (util/find-by-id targetId))))
    (fail (util/kw-path :error action :not-found))))

(defn settings-for-organization [organization]
  (get organization :inspection-summary {}))

(defn settings-for-organization-id [organizationId]
  (settings-for-organization (org/get-organization organizationId)))

(defn create-template-for-organization [organizationId name templateText]
  (let [template-id (mongo/create-id)]
    (org/update-organization organizationId
                             {$push {:inspection-summary.templates {:name     name
                                                                    :modified (now)
                                                                    :id       template-id
                                                                    :items    (split-into-template-items templateText)}}})
    template-id))

(defn update-template [organizationId templateId name templateText]
  (let [query (assoc {:inspection-summary.templates {$elemMatch {:id templateId}}} :_id organizationId)
        changes {$set {:inspection-summary.templates.$.name     name
                       :inspection-summary.templates.$.modified (now)
                       :inspection-summary.templates.$.items    (split-into-template-items templateText)}}]
    (mongo/update-by-query :organizations query changes)))

(defn delete-template
  "Deletes the template and removes all references to it from the operations-templates mapping"
  [organizationId templateId]
  (let [current-settings    (settings-for-organization-id organizationId)
        operations-to-unset (util/filter-map-by-val #{templateId} (:operations-templates current-settings))
        operations-to-unset (util/map-keys (partial util/kw-path :inspection-summary :operations-templates) operations-to-unset)]
    (when (not-empty operations-to-unset)
      (mongo/update-by-query :organizations {:_id organizationId} {$unset operations-to-unset}))
    (mongo/update-by-query :organizations {:_id organizationId} {$pull {:inspection-summary.templates {:id templateId}}})))

(defn set-default-template-for-operation [organizationId operationName templateId]
  (let [field  (str "inspection-summary.operations-templates." operationName)
        update (if (= templateId "_unset")
                 {$unset {field 1}}
                 {$set   {field templateId}})]
    (org/update-organization organizationId update)))

(defn- new-target [name]
  (hash-map :target-name name :finished false :id (mongo/create-id)))

(defn- targets-from-template [template]
  (map new-target (:items template)))

(defn summary-data-for-operation
  [organization operation templateId]
  (when-let [template (util/find-by-id templateId
                                  (:templates (settings-for-organization organization)))]
    (assoc (select-keys template [:name])
           :id      (mongo/create-id)
           :op      (select-keys operation [:id :name :description])
           :targets (targets-from-template template))))

(defn default-template-id-for-operation [organization {opName :name}]
  (get-in organization [:inspection-summary :operations-templates (keyword opName)]))

(defn get-summaries [{:keys [inspection-summaries attachments]}]
  (map #(update % :targets (partial enrich-summary-targets attachments)) inspection-summaries))

(defn verdict-given-updates [{:keys [primaryOperation inspection-summaries]} organization]
  (when (and (:inspection-summaries-enabled organization) (empty? inspection-summaries))
    (when-let [templateId (default-template-id-for-operation organization primaryOperation)]
      {$push {:inspection-summaries (summary-data-for-operation organization primaryOperation templateId)}})))

(defn finalize--inspection-summary
  "Similar to other Pate verdict finalize-- functions (see
  pate/verdict.clj for details)"
  [{:keys [application] {:keys [organization]} :command}]
  (let [{:keys [primaryOperation inspection-summaries]} application
        organization (if (delay? organization)
                       @organization
                       (org/get-organization (:organization application)))]
    (when (and (:inspection-summaries-enabled organization) (empty? inspection-summaries))
      (when-let [templateId (default-template-id-for-operation organization primaryOperation)]
        (let [summary (summary-data-for-operation organization primaryOperation templateId)]
          {:application (update application :inspection-summaries conj summary)
           :updates     {$push {:inspection-summaries summary}}})))))

(defn get-summary-target [target-id summaries]
  (reduce (fn [_ {:keys [targets]}]
            (when-let [target (util/find-by-id target-id targets)]
              (reduced target)))
          nil
          summaries))

(defn- get-inspection-target-id-from-attachment [attachmentId attachments]
  (when-let [{:keys [target]} (util/find-by-id attachmentId attachments)]
    (when (= (:type target) attachment-target-type)
      (:id target))))

(defn deny-if-finished
  "Pre-check to validate that target is not finished"
  [{{:keys [summaryId targetId]} :data {:keys [inspection-summaries]} :application}]
  (when (and summaryId targetId)
    (when (:finished (->> (util/find-by-id summaryId inspection-summaries)
                          :targets
                          (util/find-by-id targetId)))
      (fail :error.inspection-summary-target.finished))))

(defn- fail-when-target-finished [target-id inspection-summaries]
  (when (-> target-id (get-summary-target inspection-summaries) :finished)
    (fail :error.inspection-summary-target.finished)))

(defmethod att/upload-to-target-allowed :inspection-summary-item [{{:keys [inspection-summaries]} :application {{target-id :id} :target} :data :as command}]
  (or (validate-summary-not-locked command)
      (fail-when-target-finished target-id inspection-summaries)))

(defmethod att/delete-allowed-by-target :inspection-summary-item [{{:keys [inspection-summaries attachments]} :application {:keys [attachmentId]} :data :as command}]
  (or (validate-summary-not-locked command)
      (when-let [target-id (get-inspection-target-id-from-attachment attachmentId attachments)]
        (fail-when-target-finished target-id inspection-summaries))))

(defn- elem-match-query [appId summaryId]
  {:_id appId :inspection-summaries {$elemMatch {:id summaryId}}})

(defn remove-target [{appId :id :as application} summaryId targetId]
  (let [target-attachments (filter (summary-target-attachment-predicate targetId)  (:attachments application))]
    (mongo/update-by-query :applications
                           (elem-match-query appId summaryId)
                           {$pull {:inspection-summaries.$.targets {:id targetId}}})
    (att/delete-attachments! application (remove nil? (map :id target-attachments)))))

(defn add-target [appId summaryId targetName]
  (let [new-target (new-target targetName)]
    (mongo/update :applications
                  (elem-match-query appId summaryId)
                  {$push {:inspection-summaries.$.targets new-target}})
    (:id new-target)))

(defn edit-target [{appId :id :as application} summaryId targetId {mset :set munset :unset}]
  (let [summary (->> application :inspection-summaries (util/find-by-id summaryId))
        index   (->> summary :targets (util/position-by-id targetId))
        set-updates   (util/map-keys #(util/kw-path :inspection-summaries.$.targets index %) mset)
        unset-updates (util/map-keys #(util/kw-path :inspection-summaries.$.targets index %) munset)]
    (mongo/update-by-query :applications
                           (elem-match-query appId summaryId)
                           (merge (when (seq set-updates)
                                    {$set set-updates})
                                  (when (seq unset-updates)
                                    {$unset unset-updates})))))

(defn delete-summary-attachment-updates [summary-id]
  {$pull {:attachments {:source.id summary-id} :source.type "inspection-summary"}})

(defn delete-summary [app summaryId]
  (->> (delete-summary-attachment-updates summaryId)
       (util/deep-merge {$pull {:inspection-summaries {:id summaryId}}})
       (mongo/update-by-id :applications (:id app))))

(defn- summary-attachment-type-for-application [{permit-type :permitType}]
  (if (#{:R :P} (keyword permit-type))
    {:type-group :katselmukset_ja_tarkastukset :type-id :tarkastusasiakirjan_yhteeveto}
    {:type-group :muut :type-id :muu}))

(defn delete-summary-attachment [application summary-id]
  (infof "Removing inspection summary '%s' autogenerated attachment from application %s" summary-id (:id application))
  (->> (delete-summary-attachment-updates summary-id)
       (mongo/update-by-id :applications (:id application))))

(defn ^InputStream create-inspection-summary-pdf
  "Creates PDF in muuntaja and returns an input stream."
  [application lang summary-id]
  (-> (pdf-html/html->pdf {:body   (common/apply-page inspection-summary-template/inspection-summary
                                                      application
                                                      (foreman/get-linked-foreman-applications application)
                                                      lang
                                                      summary-id)
                           :header (page/render-template :header {:lang lang})
                           :footer (page/render-template :footer {:lang lang})}
                          {:top    "22mm"
                           :bottom "28mm"
                           :left   "12mm"
                           :right  "12mm"})
      :pdf-file-stream))

(defn toggle-summary-locking [{{app-id :id :as application} :application :keys [lang user] :as command} summary-id locked?]
  (mongo/update-by-query :applications
                         {:_id app-id :inspection-summaries {$elemMatch {:id summary-id}}}
                         {$set {:inspection-summaries.$.locked locked?}})
  (if locked?
    (let [file-id  (str (uuid/v1))
          filename (str (:id application) "_inspection-summary_" summary-id \_ (now) ".pdf")
          filedata {:fileId file-id
                    :type   (summary-attachment-type-for-application application)
                    :group  nil
                    :source {:type "inspection-summary" :id summary-id}}]
      (->> (util/future*
             (with-open [is (create-inspection-summary-pdf application lang summary-id)]
               (ok :filedata (storage/upload file-id filename "application/pdf" is {:uploader-user-id (:id user)}))))
           (att-bind/make-bind-job command :attachment [filedata] :preprocess-ref)))
    (delete-summary-attachment application summary-id)))
