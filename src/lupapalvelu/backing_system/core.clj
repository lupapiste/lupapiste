(ns lupapalvelu.backing-system.core
  "A Facade for backing system integrations."
  (:require [schema.core :as sc]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.backing-system.allu :as allu]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.organization :as org :refer [Organization PermitType]]
            [lupapalvelu.permit :as permit]))

;;; TODO: asianhallinta
;;; TODO: More methods

;;; `get-backing-system` could act as a multimethod dispatcher but the protocol lets us enforce and document the
;;; backing system interface better than separate multimethods.

(defprotocol BackingSystem
  (-submit-application! [self command]
    "Send application submit message to backing system. Returns [backing-system-name integration-key-data] or nil.")
  (-update-application! [self command-with-updated-application]
    "Update application in backing system if supported. Returns true if supported, false if not.")
  (-cancel-application! [self command] "Cancel application in backing system. Returns nil.")
  (-return-to-draft! [self command] "Return application to draft in backing system. Returns nil.")
  (-approve-application! [self command submitted-application lang]
    "Send approval message to backing system.
    Returns [backing-system-in-use sent-file-ids] where sent-file-ids is nil if backing system is not in use.")
  (-send-attachments! [self command attachments lang]
    "Send `attachments` of `application` to backing system. Returns a seq of attachment file IDs that were sent."))

(deftype NoopBackingSystem []
  BackingSystem
  (-submit-application! [_ _] nil)
  (-update-application! [_ _] false)
  (-return-to-draft! [_ _] nil)
  (-cancel-application! [_ _] nil)
  (-approve-application! [_ _ _ _] [false nil])
  (-send-attachments! [_ _ _ _] ()))

(deftype ALLUBackingSystem []
  BackingSystem
  (-submit-application! [_ command] [:ALLU (allu/submit-application! command)])
  (-update-application! [_ command-with-updated-application]
    (allu/update-application! command-with-updated-application)
    true)
  (-return-to-draft! [_ command] (allu/cancel-application! command))
  (-cancel-application! [_ command] (allu/cancel-application! command))
  (-approve-application! [_ command _ _]
    ;; TODO: Non-placement-contract ALLU applications
    (allu/lock-placement-contract! command)
    ;; TODO: Send comments
    [true (allu/send-attachments! command (filter attachment/unsent? (-> command :application :attachments)))])
  (-send-attachments! [_ command attachments _] (allu/send-attachments! command attachments)))

(deftype KRYSPBackingSystem []
  BackingSystem
  (-submit-application! [_ _] nil)
  (-update-application! [_ _] false)
  (-return-to-draft! [_ _] nil)
  (-cancel-application! [_ _] nil)
  (-approve-application! [_ {{:keys [state]} :application :as command} submitted-application lang]
    [true (mapping-to-krysp/save-application-as-krysp command lang submitted-application :current-state state)])
  (-send-attachments! [_ {:keys [user organization application]} attachments lang]
    (mapping-to-krysp/save-unsent-attachments-as-krysp user @organization application attachments lang)))

(sc/defn ^{:always-validate true, :private true} get-backing-system :- (sc/protocol BackingSystem)
  [organization :- Organization, permit-type :- PermitType]
  (cond
    (allu/allu-application? organization permit-type) (->ALLUBackingSystem)
    (org/krysp-integration? organization permit-type) (->KRYSPBackingSystem)
    :else (->NoopBackingSystem)))

(defn- with-implicit-backing-system [method {:keys [organization application] :as command} & args]
  (apply method (get-backing-system @organization (permit/permit-type application)) command args))

;;;; API
;;;; ===================================================================================================================

(def submit-application! (partial with-implicit-backing-system -submit-application!))

(defn- update-application! [{{:keys [id]} :application :as command}]
  (with-implicit-backing-system -update-application!
                                (assoc command :application (domain/get-application-no-access-checking id))))

(defn update-callback [command _]
  (update-application! command)
  nil)

(def return-to-draft! (partial with-implicit-backing-system -return-to-draft!))

(def cancel-application! (partial with-implicit-backing-system -cancel-application!))

(def approve-application! (partial with-implicit-backing-system -approve-application!))

(def send-attachments! (partial with-implicit-backing-system -send-attachments!))
