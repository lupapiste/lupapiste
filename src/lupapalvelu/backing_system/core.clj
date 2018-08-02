(ns lupapalvelu.backing-system.core
  (:require [schema.core :as sc]
            [lupapalvelu.backing-system.allu :as allu]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org :refer [Organization PermitType]]))

(defprotocol BackingSystem
  (approve-application! [self command application current-state lang]
    "Send approval message to backing system.
    Returns [backing-system-in-use sent-file-ids] where sent-file-ids is nil if backing system is not in use."))

(deftype NoopBackingSystem []
  BackingSystem
  (approve-application! [_ _ _ _ _] [false nil]))

(deftype ALLUBackingSystem []
  BackingSystem
  (approve-application! [_ _ {:keys [id]} _ _]
    [true (allu/approve-application! (mongo/by-id :submitted-applications id))]))

(deftype KRYSPBackingSystem []
  BackingSystem
  (approve-application! [_ command {:keys [id]} current-state lang]
    (let [submitted-application (mongo/by-id :submitted-applications id)]
      [true (mapping-to-krysp/save-application-as-krysp command lang submitted-application
                                                        :current-state current-state)])))

(sc/defn get-backing-system :- (sc/protocol BackingSystem) [organization :- Organization, permit-type :- PermitType]
  (cond
    (allu/allu-application? organization permit-type) (->ALLUBackingSystem)
    (org/krysp-integration? organization permit-type) (->KRYSPBackingSystem)
    :else (->NoopBackingSystem)))
