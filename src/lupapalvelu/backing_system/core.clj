(ns lupapalvelu.backing-system.core
  (:require [schema.core :as sc]
            [lupapalvelu.backing-system.allu :as allu]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.organization :as org :refer [Organization PermitType]]
            [lupapalvelu.permit :as permit]))

;;; `get-backing-system` could act as a multimethod dispatcher but the protocol lets us enforce and document the
;;; backing system interface better than separate multimethods.

(defprotocol BackingSystem
  (submit-application! [self application]
    "Send application submit message to backing system. Returns [backing-system-name integration-key-data] or nil.")
  (update-application! [self updated-application]
    "Update application in backing system if supported. Returns true if supported, false if not.")
  (return-to-draft! [self command] "Return application to draft in backing system. Returns nil.")
  (cancel-application! [self command] "Cancel application in backing system. Returns nil.")
  (approve-application! [self command submitted-application lang]
    "Send approval message to backing system.
    Returns [backing-system-in-use sent-file-ids] where sent-file-ids is nil if backing system is not in use.")
  (send-attachments! [self user organization application attachments lang]
    "Send `attachments` of `application` to backing system. Returns a seq of attachment file IDs that were sent."))

(deftype NoopBackingSystem []
  BackingSystem
  (submit-application! [_ _] nil)
  (update-application! [_ _] false)
  (cancel-application! [_ _] nil)
  (return-to-draft! [_ _] nil)
  (approve-application! [_ _ _ _] [false nil])
  (send-attachments! [_ _ _ _ _ _] ()))

(deftype ALLUBackingSystem []
  BackingSystem
  (submit-application! [_ application] [:ALLU (allu/submit-application! application)])
  (update-application! [_ updated-application] (allu/update-application! updated-application) true)
  (cancel-application! [_ application] (allu/cancel-application! application))
  (return-to-draft! [_ application] (allu/cancel-application! application))
  (approve-application! [_ command _ _]
    [true (allu/approve-application! command)])
  (send-attachments! [_ _ _ application attachments _] (allu/send-attachments! application attachments)))

(deftype KRYSPBackingSystem []
  BackingSystem
  (submit-application! [_ _] nil)
  (update-application! [_ _] false)
  (cancel-application! [_ _] nil)
  (return-to-draft! [_ _] nil)
  (approve-application! [_ {{:keys [state]} :application :as command} submitted-application lang]
    [true (mapping-to-krysp/save-application-as-krysp command lang submitted-application :current-state state)])
  (send-attachments! [_ user organization application attachments lang]
    (mapping-to-krysp/save-unsent-attachments-as-krysp user organization application attachments lang)))

(sc/defn get-backing-system :- (sc/protocol BackingSystem) [organization :- Organization, permit-type :- PermitType]
  (cond
    (allu/allu-application? organization permit-type) (->ALLUBackingSystem)
    (org/krysp-integration? organization permit-type) (->KRYSPBackingSystem)
    :else (->NoopBackingSystem)))

(defn update-callback [{{:keys [id] :as application} :application :keys [organization]} _]
  (update-application! (get-backing-system @organization (permit/permit-type application))
                       (domain/get-application-no-access-checking id))
  nil)
