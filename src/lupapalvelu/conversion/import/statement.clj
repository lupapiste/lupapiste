(ns lupapalvelu.conversion.import.statement
  (:require [com.climate.claypoole :as cp]
            [lupapalvelu.conversion.document :as conv-doc]
            [lupapalvelu.conversion.import.shared :as shared]
            [lupapalvelu.conversion.util :as conv-util]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.statement-schemas :as statement-sc]
            [monger.operators :refer :all]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [swiss.arrows :refer :all]))

(defn- pack-statement [{:keys [given status person]}]
  (let [m (-> {:given       given
               :status      status
               :person-name (ss/lower-case (:name person))}
              util/strip-blanks
              ss/trimwalk)]
    (when (= 3 (count m))
      m)))

(defn- statement-exists?
  [item statements]
  (boolean (when-let [p-item (pack-statement item)]
             (some #(= p-item (pack-statement %)) statements))))

(defn- make-statement
  "Valid statement or nil."
  [item]
  (try
    (->> (dissoc item :backend-id)
         (util/deep-merge {:id       (mongo/create-id)
                           :state    :given
                           ;; We only set `:modified` timestamp for bookkeeping
                           ;; purposes. The actual `:given` timestamp can be empty if it
                           ;; is not defined in `item`.
                           :modified (date/timestamp (date/now))
                           :person   {:userId "batchrun-user"
                                      :email  "eraajo@lupapiste.fi"
                                      :name   "Lupapiste Eräajo"}})
         statement-sc/valid-statement!)
    (catch Exception e
      (log/error "No statement for item" item ":" (ex-message e)))))

(defn- import-statement
  "Adds statement to the _converted_ target application. If the application already has a
  matching statement, does nothing."
  [organization-id {:keys [backend-id] :as item}]
  (when-let [{:keys  [statements]
              app-id :id} (some-<>> (conv-doc/document {:organization organization-id
                                                        :backend-id   backend-id
                                                        :converted    true})
                                    :LP-id
                                    (mongo/by-id :applications <> [:statements]))]
    (log/with-logging-context {:applicationId app-id}
      (if (statement-exists? item statements)
        (log/debug "Statement already in the application:" item)
        (when-let [{stat-id :id ts :modified
                    :as     statement} (make-statement item)]
          (mongo/update-by-id :applications app-id
                              (conv-util/with-reporting-override ts
                                (conv-util/with-sheriff-note
                                  "Import statement" ts
                                  {$push {:statements statement}}
                                  :statement-id stat-id)))
          (log/debugf "Imported statement %s for %s." stat-id backend-id))))))

(defmethod shared/import--items :statements
  [{:keys [organization-id items]}]
  (cp/pdoseq 8 [[backend-id xs] (group-by :backend-id items)]
             (log/with-logging-context {:backendId backend-id}
               ;; In order to avoid mongo conflicts we add every statement for the same
               ;; application in the same thread.
               (doseq [item xs]
                 (try
                   (->> (-> item
                            (update :given date/timestamp)
                            (update :requested date/timestamp))
                        ss/trimwalk
                        util/strip-blanks
                        (import-statement organization-id))
                   (catch Exception e
                     (log/error e)))))))
