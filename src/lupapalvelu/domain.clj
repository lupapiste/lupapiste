(ns lupapalvelu.domain
  (:require [clojure.set :refer [difference]]
            [taoensso.timbre :as timbre :refer [trace debug info warn warnf error fatal]]
            [monger.operators :refer :all]
            [sade.core :refer [unauthorized]]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.env :as env]
            [lupapalvelu.attachment.accessibility :as attachment-access]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]
            [lupapalvelu.xml.krysp.verdict :as verdict]))

;;
;; application mongo querys
;;

(defn basic-application-query-for [user]
  (let [organizations (user/organization-ids-by-roles user #{:authority :reader :approver :commenter})]
    (case (keyword (:role user))
      :applicant    (if-let [company-id (get-in user [:company :id])]
                      {$or [{:auth.id (:id user)} {:auth.id company-id}]}
                      {:auth.id (:id user)})
      :authority    {$or [{:organization {$in organizations}} {:auth.id (:id user)}]}
      :rest-api     {:organization {$in organizations}}
      :oirAuthority {:organization {$in organizations}}
      :trusted-etl {}
      (do
        (warnf "invalid role to get applications: user-id: %s, role: %s" (:id user) (:role user))
        {:_id nil})))) ; should not yield any results

(defn application-query-for [user]
  (merge
    (basic-application-query-for user)
    (case (keyword (:role user))
      :applicant {:state {$nin ["canceled"]}}
      :authority {:state {$nin ["canceled"]}}
      :oirAuthority {:state {$in ["info" "answered"]} :openInfoRequest true}
      {})))

(defn- only-authority-sees [user checker items]
  (filter (fn [m] (or (user/authority? user) (not (checker m)))) items))

(defn- only-authority-sees-drafts [user verdicts]
  (only-authority-sees user :draft verdicts))

(defn- normalize-neighbors [user neighbors]
  (mapv
    (fn [neighbor]
      (-> neighbor
        (update-in [:status]
          #(mapv
             (fn [{vetuma :vetuma :as state}]
               (if (and vetuma (not (user/authority? user)))
                 (assoc-in state [:vetuma :userid] nil)
                 state))
             %))))
    neighbors))

(defn- filter-targeted-attachment-comments
  "If comment target type is attachment, check that attachment exists.
   If not, show only comments with non-blank text related to deleted attachment"
  [application]
  (let [attachments (set (map :id (:attachments application)))]
    (update-in application [:comments]
      #(filter (fn [{:keys [target text]}] (or
                                             (empty? target)
                                             (not= (:type target) "attachment")
                                             (or
                                               (attachments (:id target))
                                               (not (ss/blank? text))))) %))))

(defn- filter-notice-from-application [application user]
  (if (user/authority? user)
    application
    (dissoc application :urgency :authorityNotice)))

(defn- authorized-to-statement? [user statement]
  (or
    (user/authority? user)
    (and
      (user/applicant? user)
      (= (-> statement :person :email user/canonize-email) (-> user :email user/canonize-email)))))

(defn- authorized-to-statement-attachment? [user attachment ids-of-own-statements]
  {:pre [user attachment (set? ids-of-own-statements)]}
  (if (= "statement" (-> attachment :target :type))
    (or (user/authority? user) (ids-of-own-statements (-> attachment :target :id)))
    true))

(defn- only-authority-or-owner-sees-statement-drafts-and-statement-attachments [application user]
  (let [ids-of-own-statements (->> (:statements application)
                                (filter #(or
                                           (:given %)  ;; including given statements as "own" statements
                                           (= (:id user) (-> % :person :userId))))
                                (map :id)
                                set)]
    (-> application
      (assoc :statements (map
                           (fn [statement]
                             (if (or
                                   (:given statement)  ;; including given statements
                                   (authorized-to-statement? user statement))
                               statement
                               (select-keys statement [:id :person :requested :given :state])))
                           (:statements application)))
      (assoc :attachments (filter
                            #(authorized-to-statement-attachment? user % ids-of-own-statements)
                            (:attachments application))))))


(defn filter-application-content-for [application user]
  (when (seq application)
    (let [draft-verdict-ids (->> application :verdicts (filter :draft) (map :id) set)
          relates-to-draft (fn [m]
                             (let [reference (or (:target m) (:source m))]
                               (and (= (:type reference) "verdict") (draft-verdict-ids (:id reference)))))]
      (-> application
        (update-in [:comments] #(filter (fn [comment] ((set (:roles comment)) (name (:role user)))) %))
        (update-in [:verdicts] (partial only-authority-sees-drafts user))
        (update-in [:attachments] (partial only-authority-sees user relates-to-draft))
        (update-in [:attachments] (partial attachment-access/filter-attachments-for user application))
        (update-in [:neighbors] (partial normalize-neighbors user))
        filter-targeted-attachment-comments
        (update-in [:tasks] (partial only-authority-sees user relates-to-draft))
        (filter-notice-from-application user)
        (only-authority-or-owner-sees-statement-drafts-and-statement-attachments user)))))

(defn get-application-as [query-or-id user & {:keys [include-canceled-apps?] :or {include-canceled-apps? false}}]
  {:pre [query-or-id (map? user)]}
  (let [query-id-part (if (map? query-or-id) query-or-id {:_id query-or-id})
        query-user-part (if include-canceled-apps?
                          (update-in (application-query-for user) [:state $nin] #(difference (set %) #{"canceled"}))
                          (application-query-for user))]

    (some-> (mongo/select-one :applications {$and [query-id-part query-user-part]})
            (update :documents (partial map schemas/with-current-schema-info))
            (update :tasks (partial map schemas/with-current-schema-info))
            (filter-application-content-for user))))

(defn get-application-no-access-checking
  ([query-or-id]
   {:pre [query-or-id]}
   (get-application-no-access-checking query-or-id {}))
  ([query-or-id projection]
   (let [query (if (map? query-or-id) query-or-id {:_id query-or-id})]
     (some-> (mongo/select-one :applications query projection)
             (update :documents (partial map schemas/with-current-schema-info))
             (update :tasks (partial map schemas/with-current-schema-info))))))

;;
;; authorization
;;

(def owner-or-write-roles ["owner" "writer" "foreman"])

(defn owner-or-write-access? [application user-id]
  (boolean (some (partial auth/has-auth-role? application user-id) owner-or-write-roles)))

(defn company-access? [application company-id]
  (boolean (auth/has-auth-role? application company-id "writer")))

(defn validate-owner-or-write-access
  "Validator: current user must be owner or have write access.
   To be used in commands' :pre-checks vector."
  [command application]
  (when-not (or
              (owner-or-write-access? application (-> command :user :id))
              (company-access? application (-> command :user :company :id)))
    unauthorized))

;;
;; assignee
;;

(defn assigned? [{authority :authority :as application}]
  {:pre [(map? authority)]}
  (-> authority :id nil? not))

;;
;; documents
;;

(defn- docs-from [application-or-documents]
  {:post [(sequential? %)]}
  (if (map? application-or-documents) (:documents application-or-documents) application-or-documents))

(defn get-document-by-id
  "returns first document from application with the document-id"
  [application-or-documents document-id]
  (let [documents (docs-from application-or-documents)]
    (util/find-by-id document-id documents)))

(defn- documents-by-schema-info [application-or-documents k v]
  (let [documents (docs-from application-or-documents)]
    (filter (comp (partial = (keyword v)) keyword k :schema-info) documents)))

(defn get-documents-by-name
  "returns document from application by schema name"
  [application-or-documents schema-name]
  (documents-by-schema-info application-or-documents :name schema-name))

(defn get-documents-by-type
  "returns document from application by schema type"
  [application-or-documents schema-type]
  (documents-by-schema-info application-or-documents :type schema-type))

(defn get-document-by-name
  "returns first document from application by schema name"
  [application-or-documents schema-name]
  (first (documents-by-schema-info application-or-documents :name schema-name)))

(defn get-document-by-type
  "returns first document from application by schema type"
  [application-or-documents schema-type]
  (first (documents-by-schema-info application-or-documents :type schema-type)))

(defn get-document-by-operation
  "returns first document from application that is associated with the operation"
  [application-or-documents operation]
  (let [op-id (if (map? operation) (:id operation) operation)
        documents (docs-from application-or-documents)]
    (first (filter #(= op-id (get-in % [:schema-info :op :id])) documents))))

(defn get-subtype [{schema-info :schema-info :as doc}]
  (when (:subtype schema-info)
    (name (:subtype schema-info))))

(defn get-documents-by-subtype
  "Returns documents of given subtype"
  [documents subtype]
  {:pre [(sequential? documents)]}
  (filter (comp (partial = (name subtype)) get-subtype) documents))

(defn get-applicant-documents
  "returns applicant documents from given application documents"
  [documents]
  {:pre [(sequential? documents)]}
  (get-documents-by-subtype documents "hakija"))

(defn get-applicant-document
  "returns first applicant document from given application documents"
  [documents]
  (first (get-applicant-documents documents)))

(defn invites [{auth :auth}]
  (map :invite (filter :invite auth)))

(defn invite [application email]
  (first (filter #(= (ss/lower-case email) (:email %)) (invites application))))

(defn no-pending-invites? [application user-id]
  (not-any? #(= user-id (-> % :user :id)) (invites application)))

;;
;; Verdict model
;;

(defn ->paatos
  "Returns a verdict data structure, compatible with KRYSP schema"
  [{:keys [verdictId backendId timestamp name given status official text section draft agreement metadata paatos-id]}]
  (let [verdict-id (or verdictId (mongo/create-id))]
    {:id verdict-id
    :kuntalupatunnus backendId
    :draft (if (nil? draft) false draft)
    :timestamp timestamp
    :sopimus agreement ; not in KRYSP
    :metadata metadata ; not in KRYSP?
    :paatokset [{:id          (or paatos-id (mongo/create-id))
                 :paivamaarat {:anto             given
                               :lainvoimainen    official}
                 :poytakirjat [{:paatoksentekija name
                                :urlHash         verdict-id
                                :status          status
                                :paatos          text ; Only in rakennusvalvonta KRYSP
                                :paatospvm       given
                                :pykala          section
                                :paatoskoodi     (when status (verdict/verdict-name status))}]}]}))

;;
;; Comment model
;;

(defn ->comment [text target type user to-user timestamp roles]
  {:pre [(or (nil? text) (string? text)) (map? target)
         type (map? user) (or (nil? to-user) (:role to-user))
         (number? timestamp) (or (sequential? roles) (set? roles))]}

  {:text    text
   ; target key order seems to be significant in MongoDB updates
   :target  (if (:id target) {:type (:type target), :id (:id target)} {:type (:type target)})
   :type    type
   :created timestamp
   :roles   (if to-user (conj (set roles) (:role to-user)) roles)
   :to      (user/summary to-user)
   :user    (user/summary user)})

;;
;; Neighbors
;;
(def neighbor-skeleton
  {:id ""
   :propertyId ""
   :owner {:type nil
           :name nil
           :businessID nil
           :nameOfDeceased nil
           :address {:street nil :zip nil :city nil}}
   :status []})

;;
;; Application skeleton with default values
;;

(def application-skeleton
  {:_applicantIndex          []
   :_attachment_indicator_reset nil ; timestamp
   :_comments-seen-by        {}
   :_statements-seen-by      {}
   :_verdicts-seen-by        {}
   :acknowledged             nil ; timestamp
   :address                  ""
   :applicant                ""
   :attachments              []
   :auth                     []
   :authority                {:firstName "", :lastName "", :id nil}
   :authorityNotice          ""
   :buildings                []
   :closed                   nil ; timestamp
   :closedBy                 {}
   :convertedToApplication   nil ; timestamp
   :comments                 []
   :complementNeeded         nil ; timestamp
   :created                  nil ; timestamp
   :documents                []
   :drawings                 []
   :foreman                  ""
   :foremanRole              ""
   :history                  [] ; state transition audit log
   :infoRequest              false
   :location                 {}
   :modified                 nil ; timestamp
   :municipality             ""
   :neighbors                []
   :opened                   nil ; timestamp
   :openInfoRequest          false
   :primaryOperation         nil
   :secondaryOperations      []
   :options                  {}
   :organization             ""
   :propertyId               ""
   :permitSubtype            ""
   :permitType               ""
   :reminder-sent            nil ; timestamp
   :schema-version           nil ; Long
   :sent                     nil ; timestamp
   :started                  nil ; construction started
   :startedBy                {}
   :state                    ""
   :statements               []
   :submitted                nil ; timestamp
   :tasks                    []
   :title                    ""
   :transfers                []
   :urgency                  "normal"
   :verdicts                 []
   :tosFunction              nil
   :metadata                 {}
   :processMetadata          {}
   :appeals                  []
   :appealVerdicts           []
   :archived                 {:application nil
                              :completed   nil}})

(def operation-skeleton
  {:name ""
   :description nil
   :created nil})
