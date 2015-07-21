(ns lupapalvelu.tiedonohjaus-api
  (:require [lupapalvelu.action :refer [defquery defcommand non-blank-parameters]]
            [sade.core :refer [ok fail]]
            [lupapalvelu.tiedonohjaus :as t]
            [lupapalvelu.organization :as o]
            [lupapalvelu.organization-api :as oa]
            [lupapalvelu.user :as user]
            [monger.operators :refer :all]
            [lupapalvelu.action :as action]
            [lupapiste-commons.tos-metadata-schema :as tms]
            [schema.core :as s]
            [clojure.walk :refer [postwalk]]
            [clojure.string :as string]))

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
   :states     (action/all-states-but [:draft :closed :canceled])}
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

(defn keywordize-keys-and-some-values [m]
  (let [f (fn [[k v]] (let [new-key (if (string? k) (keyword k) k)
                            new-value (if (and (string? v) (not= :tila k) (= (count (string/split v #"\s")) 1)) (keyword v) v)]
                        [new-key new-value]))]
    (postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))

(defcommand store-tos-metadata-for-attachment
  {:parameters [:id attachmentId metadata]
   :user-roles #{:authority}
   :states     (action/all-states-but [:draft :closed :canceled])}
  [{:keys [application created] :as command}]
  (try
    (let [attachments (:attachments application)
          metadata (keywordize-keys-and-some-values metadata)]
      (println metadata)
      (if-let [attachment (first (filter #(= (:id %) attachmentId) attachments))]
        (let [updated-attachment (assoc attachment :metadata (s/validate tms/AsiakirjaMetaDataMap metadata))
              updated-attachments (-> (remove #(= % attachment) attachments)
                                    (conj updated-attachment))]
          (action/update-application command
            {$set {:modified created
                   :attachments updated-attachments}}))))
    (catch RuntimeException e
      (.printStackTrace e)
      (fail "Invalid metadata"))))
