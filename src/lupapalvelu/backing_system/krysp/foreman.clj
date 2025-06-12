(ns lupapalvelu.backing-system.krysp.foreman
  "Convenience namespace for avoiding cyclic dependencies."
  (:require [lupapalvelu.backing-system.core :as bs]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.foreman-application-util :as foreman-util]
            [lupapalvelu.logging :as log]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [sade.core :refer :all]
            [slingshot.slingshot :refer [try+]]))

(defn send-foreman-termination-msgs!
  "Sends KuntaGML messages related to the termination."
  [{:keys [organization] :as command} foreman-app-id confirmation? lang & [foreman-doc]]
  (let [organization (force organization)
        foreman-app  (-> (mongo/by-id :applications foreman-app-id)
                         domain/enrich-application-handlers)
        permit-type  (-> foreman-app :permitType keyword)]
    (when (and (boolean (:foreman-termination-krysp-enabled organization))
               (org/krysp-write-integration? organization permit-type))
      (try+ (bs/terminate-foreman! command (name permit-type) foreman-app confirmation? (name lang) foreman-doc)
            (catch [:ok false] _ (log/errorf "KuntaGML failed for foreman termination %s" (:id foreman-app)))))))

(defn send-foreman-msgs!
  "Sends KuntaGML messages related to the foreman."
  [{:keys [organization system] :as command} app-id lang foreman-doc]
  {:pre [(map? system)]}
  (let [organization (force organization)
        app          (-> (mongo/by-id :applications app-id)
                         domain/enrich-application-handlers
                         ;; update the foreman document in the application so the canonical context is correct
                         (update :documents #(mapv (fn [doc] (if (= (:id doc) (:id foreman-doc)) foreman-doc doc)) %)))
        permit-type  (-> app :permitType keyword)]
    (when (org/krysp-write-integration? organization permit-type)
      (if (foreman-util/foreman-terminated? foreman-doc)
        (send-foreman-termination-msgs! command app-id
                                        (foreman-util/foreman-termination-requested-phase? foreman-doc)
                                        (name lang) foreman-doc)
        (bs/declare-foreman! command (name permit-type) app (name lang) foreman-doc)))))
