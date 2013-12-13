(ns lupapalvelu.domain
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn warnf error fatal]]
            [monger.operators :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.xml.krysp.verdict :as verdict]
            [sade.strings :refer [lower-case]]
            [sade.common-reader :refer [strip-nils strip-empty-maps]]))

;;
;; application mongo querys
;;

;; TODO: test me!
(defn basic-application-query-for [user]
  (case (keyword (:role user))
    :applicant {:auth.id (:id user)}
    :authority {$or [{:organization {$in (:organizations user)}} {:auth.id (:id user)}]}
    (do
      (warnf "invalid role to get applications: user-id: %s, role: %s" (:id user) (:role user))
      {:_id nil}))) ; should not yield any results

;; TODO: test me!
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
  (filter #(-> % :role (= (name role))) auth))

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

(defn get-applicant-document
  "returns first applicant document from application"
  [application]
  (first (filter (comp (partial = "hakija") :subtype :schema-info) (:documents application))))

(defn invites [{auth :auth}]
  (map :invite (filter :invite auth)))

(defn invite [application email]
  (first (filter #(-> % :email (= (lower-case email))) (invites application))))

(defn invited? [{invites :invites} email]
  (or (some #(= (lower-case email) (-> % :user :username)) invites) false))

;;
;; Conversion between Lupapiste and documents
;;

(defn has-hetu?
  ([schema]
    (has-hetu? schema [:henkilo]))
  ([schema-body base-path]
    (let [full-path (apply conj base-path [:henkilotiedot :hetu])]
      (boolean (model/find-by-name schema-body full-path)))))

(defn ->henkilo [{:keys [id firstName lastName email phone street zip city personId
                         companyName companyId
                         fise degree graduatingYear]} & {:keys [with-hetu]}]
  (letfn [(merge-hetu [m] (if with-hetu (assoc-in m [:henkilotiedot :hetu :value] personId) m))]
    (->
      {:userId                        {:value id}
       :henkilotiedot {:etunimi       {:value firstName}
                       :sukunimi      {:value lastName}}
       :yhteystiedot {:email          {:value email}
                      :puhelin        {:value phone}}
       :osoite {:katu                 {:value street}
                :postinumero          {:value zip}
                :postitoimipaikannimi {:value city}}
       :yritys {:yritysnimi           {:value companyName}
                :liikeJaYhteisoTunnus {:value companyId}}
       :patevyys {:koulutus           {:value degree}
                  :valmistumisvuosi   {:value graduatingYear}
                  :fise               {:value fise}
                  }}
      merge-hetu
      strip-nils
      strip-empty-maps)))

(defn ->yritys [{:keys [id firstName lastName email phone street zip city]}]
  (->
    {;:userId                        {:value id}
     :yhteyshenkilo {:henkilotiedot {:etunimi       {:value firstName}
                                     :sukunimi      {:value lastName}}
                     :yhteystiedot {:email          {:value email}
                                    :puhelin        {:value phone}}}
     :osoite {:katu                 {:value street}
              :postinumero          {:value zip}
              :postitoimipaikannimi {:value city}}}
    strip-nils
    strip-empty-maps))

(defn ->paatos
  "Returns a verdict data structure, compatible with KRYSP schema"
  [{:keys [id timestamp name given status official]}]
  {:kuntalupatunnus id
   :timestamp timestamp
   :paatokset [{:paivamaarat {:anto          given
                              :lainvoimainen official}
                :poytakirjat [{:paatoksentekija name
                               :status          status
                               :paatoskoodi     (verdict/verdict-name status)}]}]})

;;
;; Software version metadata
;;

(defn set-software-version [m]
  (assoc m :_software_version "1.0.5"))

;;
;; Application skeleton with default values
;;

(defn application-skeleton []
  {:_statements-seen-by      {}
   :_verdicts-seen-by        {}
   :_comments-seen-by        {}
   :address                  ""
   :allowedAttachmentTypes   []
   :attachments              []
   :auth                     []
   :authority                {}
   :closed                   nil
   :comments                 []
   :created                  nil
   :documents                []
   :infoRequest              false
   :location                 {}
   :modified                 nil
   :municipality             ""
   :neighbors                {}
   :opened                   nil
   :openInfoRequest          false
   :operations               []
   :organization             ""
   :propertyId               ""
   :permitSubtype            ""
   :permitType               ""
   :schema-version           nil
   :sent                     nil
   :state                    ""
   :statements               []
   :submitted                nil
   :tasks                    []
   :title                    ""
   :verdicts                 []})


