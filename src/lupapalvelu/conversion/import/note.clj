(ns lupapalvelu.conversion.import.note
  "Import notes to application authority notices. The target applications _are not limited_
  to converted applications."
  (:require [com.climate.claypoole :as cp]
            [lupapalvelu.conversion.import.shared :as shared]
            [lupapalvelu.conversion.util :refer [with-sheriff-note]]
            [lupapalvelu.db :as db ]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.core :refer [now]]))

(defn- import-note
  "Appends given note `text` to the target applications' authority notices. If the notice
  has already been updated, does nothing."
  [organization-id {:keys [backend-id text]}]
  (doseq [;; It is possible that the same backend-id is shared by two applications even
          ;; in the same organisation. For example, aiemmalla-luvalla-hakeminen
          ;; vs. facta-imported.
          app-id (db/application-ids-by-kuntalupatunnus organization-id
                                                        backend-id)
          :let   [new-note (-> (mongo/by-id :applications
                                            app-id
                                            [:authorityNotice])
                               :authorityNotice
                               (shared/append-text-if-needed text))
                  ts (now)]
          :when  new-note]
    (log/with-logging-context {:applicationId app-id}
      (mongo/update-by-id :applications app-id
                          (with-sheriff-note "Import note" ts
                            {$set {:authorityNotice       new-note
                                   :authorityNoticeEdited ts}}
                            :text text))
      (log/debugf "Imported note for %s."  backend-id))))

(defmethod shared/import--items :notes
  [{:keys [organization-id items]}]
  (cp/pdoseq 8 [item items]
             (try
               (import-note organization-id item)
               (catch Exception e
                 (log/error e)))))
