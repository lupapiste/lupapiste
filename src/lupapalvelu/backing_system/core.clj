(ns lupapalvelu.backing-system.core
  (:require [lupapalvelu.backing-system.allu :as allu]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permit :as permit]))

(defn approve-application!
  "If a backing system is defined for the application, send approval message there.
  Returns [backing-system-in-use sent-file-ids] where sent-file-ids is nil if backing system is not in use."
  [command {:keys [id] :as application} organization current-state lang]
  (let [use-allu (allu/allu-application? @organization (permit/permit-type application))
        use-krysp (org/krysp-integration? @organization (permit/permit-type application))
        integration-available (or use-allu use-krysp)
        sent-file-ids (if integration-available
                        (let [submitted-application (mongo/by-id :submitted-applications id)]
                          (cond
                            use-allu (allu/approve-application! submitted-application)

                            use-krysp
                            (mapping-to-krysp/save-application-as-krysp command lang submitted-application
                                                                        :current-state current-state)

                            :else (assert false "should have been unreachable")))
                        nil)]
    [integration-available sent-file-ids]))
