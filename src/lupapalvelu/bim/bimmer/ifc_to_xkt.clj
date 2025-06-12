(ns lupapalvelu.bim.bimmer.ifc-to-xkt
  "Namespace for converting IFC files to XKT by handling PubSub message contents to/from the bimmer microservice"
  (:require [lupapalvelu.conversion.util :as conv-util]
            [lupapalvelu.job :as job]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.storage.gcs :as gcs]
            [monger.operators :refer :all]
            [sade.util :as util]))

(defn request
  "Returns the request data for a pubsub message requesting an .XKT file be made from an .IFC file"
  [context]
  (-> context
      (select-keys [:application-id :operation-id :bucket :object-key])
      (assoc :topic-id :bim/to-bim-service)))

(defn store-xkt-response
  "Handles the response message and attaches the returned XKT file to the application"
  [_system
   {{:keys [application-id
            operation-id
            ifc-object-key
            xkt-bucket
            xkt-object-key
            ifc-metadata]} :data
    :keys                  [response-to data]}]
  (let [job (job/find-job response-to)]
    (job/merge-by-id response-to
                     "xkt-generation"
                     {:status           :done
                      :target-file-path ifc-object-key
                      :user             (:user job)}))
  (if (seq data)
    (when-not (pos? (mongo/update-by-query
                      :linked-file-metadata
                      {:target-entity.application-id application-id
                       :target-entity.operation-id   operation-id
                       :target-entity.target-type    "bim-model-file"
                       :versions                     {$elemMatch {:target-file-path ifc-object-key}}}
                      {$set (util/assoc-when
                              {:versions.$.bim.xkt-file.bucket     (gcs/generic-bucket-name xkt-bucket)
                               :versions.$.bim.xkt-file.object-key xkt-object-key}
                              :versions.$.bim.ifc-metadata (-> ifc-metadata
                                                               (update :createdAt conv-util/valid-timestamp-or-nil)
                                                               util/strip-nils))}))
      (log/error "Could not find IFC file to link generated XKT for" application-id operation-id ifc-object-key))
    (log/error "XKT generation pubsub response to message" response-to "was empty")))
