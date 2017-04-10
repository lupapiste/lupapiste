(ns lupapalvelu.copy-application
  (:require [clojure.set :as set]
            [taoensso.timbre :refer [error]]
            [lupapalvelu.application :as app]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
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
                        (let [doc (assoc doc :id (mongo/create-id))]
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

(defn- new-application-overrides
  [{:keys [address auth infoRequest location municipality primaryOperation schema-version state title tosFunction] :as application}
   user organization created manual-schema-datas]
  {:pre [(not-empty primaryOperation) (not-empty location) municipality]}
  (let [org-id (:id organization)
        op-name (:name primaryOperation)]
    (-> (merge application
               {:auth             (or (not-empty auth)  (app/application-auth user op-name))
                :created          created
                :id               (app/make-application-id municipality)
                :schema-version   (or schema-version    (schemas/get-latest-schema-version))
                :state            (or state             (app/application-state user org-id infoRequest))
                :title            (or (not-empty title) address)
                :tosFunction      (or tosFunction       (tos-function org-id op-name))}
               (app/location-map  location))
        (merge-in app/application-timestamp-map)
        (merge-in app/application-history-map user)
        (merge-in app/application-attachments-map organization)
        (merge-in copy-application-documents-map user organization manual-schema-datas))))

(def  default-copy-options
  {:blacklist [:comments :history :statements :attachments :auth] ; copy everything except these
   })

(defn new-application-copy [source-application user organization created copy-options & [manual-schema-datas]]
  (let [options (merge default-copy-options copy-options)]
    (-> domain/application-skeleton
        (merge (copied-keys source-application options))
        (merge-in new-application-overrides user organization created manual-schema-datas)
        (merge-in updated-operation-and-document-ids source-application))))
