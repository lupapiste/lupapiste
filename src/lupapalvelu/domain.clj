(ns lupapalvelu.domain
  (:use [monger.operators]
        [clojure.tools.logging])
  (:require [lupapalvelu.mongo :as mongo]))

(defn application-query-for [user]
  (case (keyword (:role user))
    :applicant {:auth.id (:id user)
                :state {$ne "canceled"}}
    :authority {:municipality (:municipality user)
                $and [{:state {$ne "draft"}} {:state {$ne "canceled"}}]}
    :admin     {:state {$ne "canceled"}}
    (do
      (warn "invalid role to get applications")
      {:_id "-1"} ))) ; should not yield any results

(defn get-application-as [application-id user]
  (when user (mongo/select-one :applications {$and [{:_id application-id} (application-query-for user)]})))

(defn role-in-application [{roles :roles} user-id]
  (some (fn [[role {id :id}]] (when (= id user-id) role)) roles))

(defn get-auths-by-role
  "returns vector of all auth-entries in an application with the given role. Role can be a keyword or a string."
  [{auth :auth} role]
  (filter #(-> % :role (= (name role))) auth))

(defn has-role? [application user-id]
  (not (nil? (role-in-application application user-id))))

(defn has-auth? [{auth :auth} user-id]
  (or (some (partial = user-id) (map :id auth)) false))

(defn get-document-by-id
  "returns first document from application with the document-id"
  [{documents :documents} document-id]
  (first (filter #(= document-id (:id %)) documents)))

(defn get-documents-by-name
  "returns document from application by schema name"
  [application schema-name]
  (filter (comp (partial = schema-name) :name :info :schema) (:documents application)))

(defn get-document-by-name
  "returns first document from application by schema name"
  [application schema-name]
  (first (get-documents-by-name application schema-name)))

(defn invited? [{invites :invites} email]
  (or (some #(= email (-> % :user :username)) invites) false))

;;
;; Conversion between Lupapiste and documents
;;

(defn user2henkilo [{:keys [id firstName lastName email phone street zip city]}]
  {:userId                        id
   :henkilotiedot {:etunimi       firstName
                   :sukunimi      lastName}
   :yhteystiedot {:email          email
                  :puhelin        phone}
   :osoite {:katu                 street
            :postinumero          zip
            :postitoimipaikannimi city}})
