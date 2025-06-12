(ns lupapalvelu.conversion.import.description
  "Import descriptions to application 'hankkeen-kuvaus' documents. The target applications
  _are limited_ to converted applications."
  (:require [com.climate.claypoole :as cp]
            [lupapalvelu.conversion.document :as conv-doc]
            [lupapalvelu.conversion.import.shared :as shared]
            [lupapalvelu.conversion.util :refer [with-sheriff-note]]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.core :refer [now]]
            [sade.util :as util]))

(defn- import-description
  "Appends given description `text` to the _converted_ target applications'
  'hankkeen-kuvaus' documents. If the document has already been updated, does nothing."
  [organization-id {:keys [backend-id text]}]
  (when-let [{app-id :LP-id} (conv-doc/document {:organization organization-id
                                                 :backend-id   backend-id
                                                 :converted    true})]
    (log/with-logging-context {:applicationId app-id}
      (let [doc (->> (mongo/by-id :applications app-id [:documents])
                     :documents
                     (util/find-first (util/fn-> :schema-info :subtype
                                                 (= "hankkeen-kuvaus"))))
            ts  (now)]
        (when-let [new-desc (-> doc :data :kuvaus :value
                                (shared/append-text-if-needed text))]
          (mongo/update-by-query :applications
                                 {:_id       app-id
                                  :documents {$elemMatch {:id (:id doc)}}}
                                 (with-sheriff-note "Import description" ts
                                   {$set {:documents.$.data.kuvaus {:value    new-desc
                                                                    :modified ts}}}
                                   :text text))
          (log/debugf "Imported description for %s."  backend-id))))))

(defmethod shared/import--items :descriptions
  [{:keys [organization-id items]}]
  (cp/pdoseq 8 [item items]
             (try
               (import-description organization-id item)
               (catch Exception e
                 (log/error e)))))
