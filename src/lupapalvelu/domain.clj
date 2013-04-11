(ns lupapalvelu.domain
  (:use [monger.operators]
        [clojure.tools.logging])
  (:require [lupapalvelu.mongo :as mongo]))

;;
;; application mongo querys
;;

;; TODO: test me!
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

;;
;; authorization
;;

(defn get-auths-by-role
  "returns vector of all auth-entries in an application with the given role. Role can be a keyword or a string."
  [{auth :auth} role]
  (filter #(-> % :role (= (name role))) auth))

(defn has-auth? [{auth :auth} user-id]
  (or (some (partial = user-id) (map :id auth)) false))

(defn has-auth-role? [{auth :auth} user-id role]
  (has-auth? {:auth (get-auths-by-role {:auth auth} role)} user-id))

(defn is-owner-or-writer? [application user-id]
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
  (filter (comp (partial = schema-name) :name :info :schema) (:documents application)))

(defn get-document-by-name
  "returns first document from application by schema name"
  [application schema-name]
  (first (get-documents-by-name application schema-name)))

(defn invites [{auth :auth}]
  (map :invite (filter :invite auth)))

(defn invite [application email]
  (first (filter #(-> % :email (= email)) (invites application))))

(defn invited? [{invites :invites} email]
  (or (some #(= email (-> % :user :username)) invites) false))

;;
;; Conversion between Lupapiste and documents
;;

(defn user2henkilo [{:keys [id firstName lastName email phone street zip city]}]
  {:userId                        {:value id}
   :henkilotiedot {:etunimi       {:value firstName}
                   :sukunimi      {:value lastName}}
   :yhteystiedot {:email          {:value email}
                  :puhelin        {:value phone}}
   :osoite {:katu                 {:value street}
            :postinumero          {:value zip}
            :postitoimipaikannimi {:value city}}})
