(ns lupapalvelu.copy-application
  (:require [clojure.set :as set]
            [taoensso.timbre :refer [error]]
            [lupapalvelu.application :as app]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.company :as company]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.operations :as op]
            [lupapalvelu.organization :as org]
            [lupapalvelu.user :as usr]
            [sade.core :refer :all]
            [sade.property :as prop]
            [sade.util :refer [merge-in]]))

;;; Copying source application keys

(defn- copied-keys
  "Copy keys from source application. The options must include either a
  whitelist of keys to copy, or a blacklist of keys to NOT copy"
  [source-application copy-options]
  {:pre [(or (contains? copy-options :whitelist)
             (contains? copy-options :blacklist))]}
  (if (contains? copy-options :whitelist)
    (select-keys source-application (:whitelist copy-options))
    (apply dissoc source-application (:blacklist copy-options))))


;;; Updating ids

(defn- new-app-id [application]
  {:id (app/make-application-id (:municipality application))})

(defn- operation-id-map [source-application]
  (into {}
        (map #(vector (:id %) (mongo/create-id))
             (conj (:secondaryOperations source-application)
                   (:primaryOperation source-application)))))

(defn- updated-operation-and-document-ids [application source-application]
  (let [op-id-mapping (operation-id-map source-application)]
    {:primaryOperation (update (:primaryOperation application) :id
                               op-id-mapping)
     :secondaryOperations (mapv #(assoc % :id (op-id-mapping (:id %)))
                                (:secondaryOperations application))
     :documents (mapv (fn [doc]
                        (let [doc (assoc doc
                                         :id (mongo/create-id)
                                         :created (:created application))]
                          (if (-> doc :schema-info :op)
                            (update-in doc [:schema-info :op :id] op-id-mapping)
                            doc)))
                      (:documents application))}))

;;; Handling noncopied and nonoverridden keys similarly to creating new application

(defn- tos-function [organization-id operation-name]
  (app/tos-function (org/get-organization organization-id) operation-name))

(defn- copy-application-documents-map
  "If the application contains no documents, create new ones similarly
  to a new application"
  [{:keys [documents] :as copied-application} user organization manual-schema-datas]
  (if (empty? documents)
    (app/application-documents-map copied-application user organization manual-schema-datas)))

(defn- create-company-auth [old-company-auth]
  (when-let [company-id (or (not-empty (:id old-company-auth))
                            (-> old-company-auth :invite :user :id))]
    (when-let [company (company/find-company-by-id company-id)]
      (assoc
       (company/company->auth company)
       :id "" ; prevents access to application before accepting invite
       :role (:role old-company-auth)
       :invite {:user {:id company-id}}))))

(defn create-user-auth [old-user-auth inviter application-id timestamp]
  (when-let [user (usr/get-user-by-id (:id old-user-auth))]
    (auth/create-invite-auth inviter user application-id
                             (:role old-user-auth)
                             timestamp)))

(defn new-auth [old-auth inviter application-id timestamp]
  (if (= (:type old-auth) "company")
    (create-company-auth old-auth)
    (create-user-auth old-auth inviter application-id timestamp )))

(defn new-auth-map [{auth            :auth
                     id              :id
                     {op-name :name} :primaryOperation
                     created          :created}
                    inviter]
  {:auth (concat (app/application-auth inviter op-name)
                 (->> auth
                      (remove #(= (:id inviter) (:id %)))
                      (map #(if (= (keyword (:role %)) :owner)
                              (assoc % :role :writer)
                              %))
                      (mapv #(new-auth % inviter id created))))})


(def  default-copy-options
  {:blacklist [:comments :history :statements :attachments] ; copy everything except these
   })

(defn- new-application-overrides
  [{:keys [address auth infoRequest location municipality primaryOperation schema-version state title tosFunction] :as application}
   user organization created manual-schema-datas]
  {:pre [(not-empty primaryOperation) (not-empty location) municipality]}
  (let [org-id (:id organization)
        op-name (:name primaryOperation)]
    (-> (merge application
               {:created          created
                :id               (app/make-application-id municipality)
                :schema-version   (or schema-version    (schemas/get-latest-schema-version))
                :state            (or state             (app/application-state user org-id infoRequest))
                :title            (or (not-empty title) address)
                :tosFunction      (or tosFunction       (tos-function org-id op-name))}
               (app/location-map  location))
        (merge-in new-auth-map user)
        (merge-in app/application-timestamp-map)
        (merge-in app/application-history-map user)
        (merge-in app/application-attachments-map organization)
        (merge-in copy-application-documents-map user organization manual-schema-datas))))

(defn new-application-copy [source-application user organization created copy-options & [manual-schema-datas]]
  (let [options (merge default-copy-options copy-options)]
    (-> domain/application-skeleton
        (merge (copied-keys source-application options))
        (merge-in new-application-overrides user organization created manual-schema-datas)
        (merge-in updated-operation-and-document-ids source-application))))

(defn copy-application
  [{{:keys [source-application-id x y address propertyId municipality auth-invites]} :data :keys [user created]} & [manual-schema-datas]]
  (if-let [source-application (domain/get-application-as source-application-id user :include-canceled-apps? true)]
    (let [municipality (prop/municipality-id-by-property-id propertyId)
          operation    (-> source-application :primaryOperation :name)
          permit-type  (op/permit-type-of-operation operation)
          organization (org/resolve-organization municipality permit-type)]
      (when-not organization
        (fail! :error.missing-organization :municipality municipality :permit-type permit-type :operation operation))
      (new-application-copy (assoc source-application
                                   :auth         (filter #((set auth-invites) (:id %))
                                                         (:auth source-application))
                                   :state        :draft
                                   :propertyId   propertyId
                                   :location     (app/->location x y)
                                   :municipality municipality
                                   :organization (:id organization))
                            user organization created
                            default-copy-options
                            manual-schema-datas))
    (fail! :error.no-source-application :id source-application-id)))
