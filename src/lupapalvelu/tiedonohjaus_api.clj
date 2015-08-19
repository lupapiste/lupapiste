(ns lupapalvelu.tiedonohjaus-api
  (:require [lupapalvelu.action :refer [defquery defcommand non-blank-parameters]]
            [sade.core :refer [ok fail]]
            [lupapalvelu.tiedonohjaus :as t]
            [lupapalvelu.organization :as o]
            [lupapalvelu.organization-api :as oa]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as user]
            [monger.operators :refer :all]
            [lupapalvelu.action :as action]
            [lupapiste-commons.tos-metadata-schema :as tms]
            [schema.core :as s]
            [taoensso.timbre :as timbre]
            [sade.env :as env])
  (:import (schema.core EnumSchema)))

(defquery available-tos-functions
  {:user-roles       #{:anonymous}
   :parameters       [organizationId]
   :input-validators [(partial non-blank-parameters [:organizationId])]}
  (let [functions (t/available-tos-functions organizationId)]
    (ok :functions functions)))

(defn- store-function-code [operation function-code user]
  (let [orgId (user/authority-admins-organization-id user)
        organization (o/get-organization orgId)
        operation-valid? (some #{operation} (:selected-operations organization))
        code-valid? (some #{function-code} (map :code (t/available-tos-functions orgId)))]
    (if (and operation-valid? code-valid?)
      (do (o/update-organization orgId {$set {(str "operations-tos-functions." operation) function-code}})
          (ok))
      (fail "Invalid organization or operation"))))

(defcommand set-tos-function-for-operation
  {:parameters       [operation functionCode]
   :user-roles       #{:authorityAdmin}
   :input-validators [(partial non-blank-parameters [:functionCode :operation])]}
  [{user :user}]
  (store-function-code operation functionCode user))

(defcommand set-tos-function-for-application
  {:parameters [:id functionCode]
   :user-roles #{:authority}
   :states     states/all-but-draft-or-terminal}
  [{:keys [application created] :as command}]
  (let [orgId (:organization application)
        code-valid? (some #{functionCode} (map :code (t/available-tos-functions orgId)))]
    (if code-valid?
      (let [updated-attachments (map #(t/document-with-updated-metadata % orgId functionCode) (:attachments application))
            updated-metadata (t/metadata-for-document orgId functionCode "hakemus")]
        (action/update-application command
                                   {$set {:modified created
                                          :tosFunction functionCode
                                          :metadata updated-metadata
                                          :attachments updated-attachments}}))
      (fail "Invalid TOS function code"))))

(def schema-to-input-type-map
  {s/Str   "text"
   tms/NonEmptyStr "text"
   tms/Vuodet "number"})

(defn metadata-schema-for-ui [field]
  (cond-> field
    (:dependencies field) (->> (:dependencies)
                               (map (fn [[k v]] {k (map metadata-schema-for-ui v)}))
                               (into {})
                               (assoc field :dependencies))
    (:subfields field) (->> (:subfields)
                            (map metadata-schema-for-ui)
                            (assoc field :subfields))
    (:schema field) (-> (assoc :inputType (get schema-to-input-type-map (:schema field)))
                        (dissoc :schema))))

(defquery tos-metadata-schema
  {:user-roles #{:anonymous}}
  (ok :schema (map metadata-schema-for-ui tms/common-metadata-fields)))

(defn get-in-metadata-map [map ks]
  (let [k (first ks)
        value (or (get map k)
                (second (first (filter (fn [[key-in-map _]] (= k (:k key-in-map))) map))))]
    (if (and (map? value) (next ks))
      (get-in-metadata-map value (next ks))
      value)))

(defn convert-value-to-schema-type [ks v]
  (when-let [schema (get-in-metadata-map tms/AsiakirjaMetaDataMap ks)]
    (if (= EnumSchema (type schema)) (keyword v) v)))

(defn keywordize-keys-and-some-values [m ks]
  (->> m
       (map (fn [[k v]] (let [new-k (if (string? k) (keyword k) k)
                              new-ks (conj ks new-k)
                              new-v (if (map? v)
                                      (keywordize-keys-and-some-values v new-ks)
                                      (convert-value-to-schema-type new-ks v))]
                          [new-k new-v])))
       (into {})))

(defcommand store-tos-metadata-for-attachment
  {:parameters [:id attachmentId metadata]
   :user-roles #{:authority}
   :states     states/all-but-draft-or-terminal}
  [{:keys [application created] :as command}]
  (when (env/feature? :tiedonohjaus)
    (try
      (if-let [attachment (first (filter #(= (:id %) attachmentId) (:attachments application)))]
        (let [metadata (->> (keywordize-keys-and-some-values metadata [])
                            (tms/sanitize-metadata)
                            (#(assoc % :tila (get-in attachment [:metadata :tila])))
                            (s/validate tms/AsiakirjaMetaDataMap))
              updated-attachment (assoc attachment :metadata metadata)
              updated-attachments (-> (remove #(= % attachment) (:attachments application))
                                      (conj updated-attachment))]
          (action/update-application command {$set {:modified created
                                                    :attachments updated-attachments}}))
        (fail "error.attachment.id"))
      (catch RuntimeException e
        (timbre/error e)
        (fail "error.invalid.metadata")))))

(defcommand store-tos-metadata-for-application
  {:parameters [:id metadata]
   :user-roles #{:authority}
   :states     states/all-but-draft-or-terminal}
  [{:keys [application created] :as command}]
  (when (env/feature? :tiedonohjaus)
    (try
      (let [metadata (->> (keywordize-keys-and-some-values metadata [])
                          (tms/sanitize-metadata)
                          (#(assoc % :tila (get-in application [:metadata :tila])))
                          (s/validate tms/AsiakirjaMetaDataMap))]
        (action/update-application command {$set {:modified created
                                                  :metadata metadata}}))
      (catch RuntimeException e
        (timbre/error e)
        (fail "error.invalid.metadata")))))
