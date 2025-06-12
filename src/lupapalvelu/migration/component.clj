(ns lupapalvelu.migration.component
  (:require [integrant.core :as ig]
            [lupapalvelu.email :as email]
            [lupapalvelu.lockdown :as lockdown]
            [lupapalvelu.logging :refer [info infof warn]]
            [lupapalvelu.migration.migration :as migration]
            [lupapalvelu.migration.migrations]
            [lupapalvelu.mongo :as mongo]
            [sade.env :as env]
            [sade.strings :as ss]))

(defmethod ig/init-key ::migrations [k {:keys [conn skip?]}]
  (infof "Starting %s..." k)
  (if skip?
    (info "Skipping migrations..")
    (binding [mongo/*connection* conn]
      (migration/update!)
      (when (lockdown/lockdown-from-db?)
        ;; Might theoretically happen if
        ;; - earlier unlocking failed due to crash during migration
        ;; - another instance is running migrations right now
        (warn "Lockdown enabled yet migrations finished -> unlocking!")
        (lockdown/unlock!))
      (when-let [failures (seq (migration/failing-migrations))]
        (let [msg (format "%s build %s (%s)\nFailing migration(s): %s"
                          env/target-env
                          env/build-number
                          (:git-branch env/buildinfo)
                          (ss/join failures))]
          (email/send-email-message (env/value :technical-contact)
                                    "Critical: Migration failure!"
                                    [msg msg]))))))
