(ns lupapalvelu.domain
  (:require [clojure.set :refer [difference]]
            [taoensso.timbre :as timbre :refer [trace debug info warn warnf error fatal]]
            [monger.operators :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]
            [lupapalvelu.xml.krysp.verdict :as verdict]
            [sade.core :refer [unauthorized]]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.env :as env]))

;;
;; application mongo querys
;;

(defn basic-application-query-for [user]
  (let [organizations (user/organization-ids-by-roles user #{:authority :reader})]
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
      :authority {:state {$nin ["draft" "canceled"]}}
      :oirAuthority {:state {$in ["info" "answered"]} :openInfoRequest true}
      {})))

(defn- only-authority-sees [user checker items]
  (filter (fn [m] (not (and (not (user/authority? user)) (checker m)))) items))

(defn- only-authority-sees-drafts [user verdicts]
  (only-authority-sees user :draft verdicts))

(defn- commented-attachment-exists [application]
  (let [attachments (set (map :id (:attachments application)))]
    (update-in application [:comments]
      #(filter (fn [{target :target}] (or (empty? target) (not= (:type target) "attachment") (attachments (:id target)))) %))))

(defn- filter-notice-from-application [application user]
  (if (user/authority? user)
    application
    (dissoc application :urgency :authorityNotice)))

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
        commented-attachment-exists
        (update-in [:tasks] (partial only-authority-sees user relates-to-draft))
        (filter-notice-from-application user)))))

(defn get-application-as [query-or-id user & {:keys [include-canceled-apps?] :or {include-canceled-apps? false}}]
  {:pre [query-or-id (map? user)]}
  (let [query-id-part (if (map? query-or-id) query-or-id {:_id query-or-id})
        query-user-part (if include-canceled-apps?
                          (update-in (application-query-for user) [:state $nin] #(difference (set %) #{"canceled"}))
                          (application-query-for user))]
   (filter-application-content-for
     (mongo/select-one :applications {$and [query-id-part query-user-part]})
     user)))

(defn get-application-no-access-checking [query-or-id]
  {:pre [query-or-id]}
  (let [query (if (map? query-or-id) query-or-id {:_id query-or-id})]
    (mongo/select-one :applications query)))

;;
;; authorization
;;

(defn get-auths-by-role
  "returns vector of all auth-entries in an application with the given role. Role can be a keyword or a string."
  [{auth :auth} role]
  (filter #(= (name (get % :role "")) (name role)) auth))

(defn has-auth? [{auth :auth} user-id]
  (or (some (partial = user-id) (map :id auth)) false))

(defn get-auths [{auth :auth} user-id]
  (filter #(= (:id %) user-id) auth))

(defn get-auth [application user-id]
  (first (get-auths application user-id)))

(defn has-auth-role? [{auth :auth} user-id role]
  (has-auth? {:auth (get-auths-by-role {:auth auth} role)} user-id))

(defn owner-or-write-access? [application user-id]
  (or (has-auth-role? application user-id "owner")
      (has-auth-role? application user-id "writer")
      (has-auth-role? application user-id "foreman")))

(defn validate-owner-or-write-access
  "Validator: current user must be owner or have write access.
   To be used in commands' :pre-checks vector."
  [command application]
  (when-not (owner-or-write-access? application (-> command :user :id))
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

(defn get-document-by-id
  "returns first document from application with the document-id"
  [{documents :documents} document-id]
  (first (filter #(= document-id (:id %)) documents)))

(defn get-documents-by-name
  "returns document from application by schema name"
  [{documents :documents} schema-name]
  (filter (comp (partial = (keyword schema-name)) keyword :name :schema-info) documents))

(defn get-documents-by-type
  "returns document from application by schema type"
  [{documents :documents} schema-type]
  (filter (comp (partial = (keyword schema-type)) keyword :type :schema-info) documents))

(defn get-document-by-name
  "returns first document from application by schema name"
  [application schema-name]
  (first (get-documents-by-name application schema-name)))

(defn get-document-by-type
  "returns first document from application by schema name"
  [application type-to-find]
  (first (get-documents-by-type application type-to-find)))

(defn get-document-by-operation
  "returns first document from application that is associated with the operation"
  [{documents :documents} operation]
  (let [op-id (if (map? operation) (:id operation) operation)]
    (first (filter #(= op-id (get-in % [:schema-info :op :id])) documents))))

(defn get-applicant-documents
  "returns applicant documents from application"
  [application]
  (filter (comp (partial = "hakija") :subtype :schema-info) (:documents application)))

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
  [{:keys [verdictId backendId timestamp name given status official text section draft agreement]}]
  (let [verdict-id (or verdictId (mongo/create-id))]
    {:id verdict-id
    :kuntalupatunnus backendId
    :draft (if (nil? draft) false draft)
    :timestamp timestamp
    :sopimus agreement ; not in KRYSP
    :paatokset [{:paivamaarat {:anto             given
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
;; Application skeleton with default values
;;

(def application-skeleton
  {:_applicantIndex          []
   :_attachment_indicator_reset nil ; timestamp
   :_comments-seen-by        {}
   :_statements-seen-by      {}
   :_verdicts-seen-by        {}
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
   :created                  nil ; timestamp
   :documents                []
   :drawings                 []
   :infoRequest              false
   :location                 {}
   :modified                 nil ; timestamp
   :municipality             ""
   :neighbors                []
   :opened                   nil ; timestamp
   :openInfoRequest          false
   :primaryOperation         nil
   :secondaryOperations      []
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
   :metadata                 {}})


