(ns lupapalvelu.backing-system.core
  (:require [schema.core :as sc]
            [lupapalvelu.backing-system.allu :as allu]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]
            [lupapalvelu.organization :as org :refer [Organization PermitType]]))

;;; `get-backing-system` could act as a multimethod dispatcher but the protocol lets us enforce and document the
;;; backing system interface better than separate multimethods.

(defprotocol BackingSystem
  (approve-application! [self command submitted-application lang]
    "Send approval message to backing system.
    Returns [backing-system-in-use sent-file-ids] where sent-file-ids is nil if backing system is not in use."))

(deftype NoopBackingSystem []
  BackingSystem
  (approve-application! [_ _ _ _ _] [false nil]))

(deftype ALLUBackingSystem []
  BackingSystem
  (approve-application! [_ _ submitted-application _]
    [true (allu/approve-application! submitted-application)]))

(deftype KRYSPBackingSystem []
  BackingSystem
  (approve-application! [_ {{:keys [state]} :application :as command} submitted-application lang]
    [true (mapping-to-krysp/save-application-as-krysp command lang submitted-application :current-state state)]))

(sc/defn get-backing-system :- (sc/protocol BackingSystem) [organization :- Organization, permit-type :- PermitType]
  (cond
    (allu/allu-application? organization permit-type) (->ALLUBackingSystem)
    (org/krysp-integration? organization permit-type) (->KRYSPBackingSystem)
    :else (->NoopBackingSystem)))
