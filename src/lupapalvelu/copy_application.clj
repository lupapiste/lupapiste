(ns lupapalvelu.copy-application
  (:require [clojure.set :as set]
            [taoensso.timbre :refer [error]]
            [lupapalvelu.application :as app]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [sade.util :refer [merge-in]]))

(defn- copied-keys
  "Copy keys from source application. The options must include either a
  whitelist of keys to copy, or a blacklist of keys to NOT copy"
  [source-application copy-options]
  {:pre [(or (contains? copy-options :whitelist)
             (contains? copy-options :blacklist))]}
  (if (contains? copy-options :whitelist)
    (select-keys source-application (:whitelist copy-options))
    (apply dissoc source-application (:blacklist copy-options))))

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

(defn- new-operation-and-document-ids
  "give new ids to operations and documents if they were copied"
  [application source-application]
  (let [operations-copied? (and (= (:primaryOperation application)
                                   (:primaryOperation source-application))
                                (= (:secondaryOperations application)
                                   (:secondaryOperations source-application)))
        documents-copied? (= (:documents application)
                             (:documents source-application))]
    (cond (and operations-copied? documents-copied?)
            (updated-operation-and-document-ids application)
          (not (or operations-copied? documents-copied?))
            nil
          :else
            (error "documents were " (when (not documents-copied?) "not ")
                   "copied, but operations " (when operations-copied? "were" "were not")))))

(defn- tos-function [organization-id operation-name]
  (app/tos-function (org/get-organization organization-id) operation-name))

(defn- new-application-overrides
  [{:keys [address auth infoRequest location municipality organization primaryOperation schema-version state title tosFunction] :as application}
   {:keys [overrides]} user created]
  {:pre [(or (-> overrides :primaryOperation) (not-empty primaryOperation))]}
  (let [info-request? (get overrides :infoRequest infoRequest)
        org-id (get overrides :organization organization)
        primary-operation (or (-> overrides :primaryOperation) primaryOperation)
        op-name (:name primary-operation)]
    (-> (merge application
               {:auth             (or (:auth overrides)           (not-empty auth)  (app/application-auth user op-name))
                :created          created
                :id               (or (:id overrides)             (app/make-application-id (get overrides :municipality municipality)))
                :primaryOperation primary-operation
                :schema-version   (or (:schema-version overrides) schema-version    (schemas/get-latest-schema-version))
                :state            (or (:state overrides)          state             (app/application-state user org-id info-request?))
                :title            (or (:title overrides)          (not-empty title) (:address overrides) address)
                :tosFunction      (or (:tosFunction overrides)    tosFunction       (tos-function org-id op-name))}
               (app/location-map  (or (:location overrides) location))
               overrides)
        (merge-in app/application-timestamp-map)
        (merge-in app/application-history-map))))

(def ^:private default-copy-options
  {:blacklist [:comments :history :statements] ; copy everything except these
   })

(defn new-application-copy [source-application user created copy-options & [manual-schema-datas]]
  (let [options (merge default-copy-options copy-options)]
    (-> domain/application-skeleton
        (merge (copied-keys source-application options))
        (merge-in new-application-overrides options user created)
        (merge-in updated-operation-and-document-ids source-application))))
