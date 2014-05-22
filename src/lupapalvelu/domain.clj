(ns lupapalvelu.domain
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn warnf error fatal]]
            [monger.operators :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.xml.krysp.verdict :as verdict]
            [sade.strings :refer [lower-case]]
            [sade.env :as env]))

;;
;; application mongo querys
;;

(defn basic-application-query-for [user]
  (case (keyword (:role user))
    :applicant {:auth.id (:id user)}
    :authority {$or [{:organization {$in (:organizations user)}} {:auth.id (:id user)}]}
    (do
      (warnf "invalid role to get applications: user-id: %s, role: %s" (:id user) (:role user))
      {:_id nil}))) ; should not yield any results

(defn application-query-for [user]
  (merge
    (basic-application-query-for user)
    (case (keyword (:role user))
      :applicant {:state {$ne "canceled"}}
      :authority {$and [{:state {$ne "draft"}} {:state {$ne "canceled"}}]}
      {})))

(defn get-application-as [application-id user]
  (when user (mongo/select-one :applications {$and [{:_id application-id} (application-query-for user)]})))

(defn get-application-no-access-checking [application-id]
  (mongo/select-one :applications {:_id application-id}))

;;
;; authorization
;;

(defn get-auths-by-role
  "returns vector of all auth-entries in an application with the given role. Role can be a keyword or a string."
  [{auth :auth} role]
  (filter #(= (name (:role %)) (name role)) auth))

(defn has-auth? [{auth :auth} user-id]
  (or (some (partial = user-id) (map :id auth)) false))

(defn has-auth-role? [{auth :auth} user-id role]
  (has-auth? {:auth (get-auths-by-role {:auth auth} role)} user-id))

(defn owner-or-writer? [application user-id]
  (or (has-auth-role? application user-id "owner")
      (has-auth-role? application user-id "writer")))

;;
;; documents
;;

(defn get-document-by-id
  "returns first document from application with the document-id"
  [{documents :documents} document-id]
  (first (filter #(= document-id (:id %)) documents)))

(defn get-documents-by-name
  "returns document from application by schema name"
  [application schema-name]
  (filter (comp (partial = schema-name) :name :schema-info) (:documents application)))

(defn get-document-by-name
  "returns first document from application by schema name"
  [application schema-name]
  (first (get-documents-by-name application schema-name)))

(defn get-applicant-documents
  "returns applicant documents from application"
  [application]
  (filter (comp (partial = "hakija") :subtype :schema-info) (:documents application)))

(defn invites [{auth :auth}]
  (map :invite (filter :invite auth)))

(defn invite [application email]
  (first (filter #(= (lower-case email) (:email %)) (invites application))))

(defn invite-accepted-by-user [application user-id]
  (and
    (has-auth? application user-id)
    (not-any? #(= user-id (-> % :user :id)) (invites application))))

;;
;; Verdict model
;;

(defn ->paatos
  "Returns a verdict data structure, compatible with KRYSP schema"
  [{:keys [id timestamp name given status official]}]
  {:kuntalupatunnus id
   :timestamp timestamp
   :paatokset [{:paivamaarat {:anto             given
                              :lainvoimainen    official}
                :poytakirjat [{:paatoksentekija name
                               :status          status
                               :paatospvm       given
                               :paatoskoodi     (verdict/verdict-name status)}]}]})

;;
;; Application skeleton with default values
;;

(def application-skeleton
  {:_statements-seen-by      {}
   :_verdicts-seen-by        {}
   :_comments-seen-by        {}
   :_applicantIndex          []
   :address                  ""
   :applicant                ""
   :attachments              []
   :auth                     []
   :authority                {}
   :buildings                []
   :closed                   nil
   :closedBy                 {}
   :comments                 []
   :created                  nil
   :documents                []
   :drawings                 []
   :infoRequest              false
   :location                 {}
   :modified                 nil
   :municipality             ""
   :neighbors                []
   :opened                   nil
   :openInfoRequest          false
   :operations               []
   :organization             ""
   :propertyId               ""
   :permitSubtype            ""
   :permitType               ""
   :reminder-sent            nil
   :schema-version           nil
   :sent                     nil
   :started                  nil ; construction started
   :startedBy                {}
   :state                    ""
   :statements               []
   :submitted                nil
   :tasks                    []
   :title                    ""
   :verdicts                 []})


