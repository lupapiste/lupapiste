(ns lupapalvelu.backing-system.core
  "A Facade for backing system integrations."
  (:require [schema.core :as sc]
            [sade.core :refer [fail]]
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
  ;; TODO: Call this from action pipeline. NoopBackingSystem.-supported-action? should be fixed first.
  (-supported-action? [self command] "Is `(:action command)` supported by this backing system?")
  (-submit-application! [self command]
    "Send application submit message to backing system. Returns nil.")
  (-update-application! [self command updated-application]
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
  (-supported-action? [_ _] true)                           ; FIXME: Incorrect in general
  (-submit-application! [_ _] nil)
  (-update-application! [_ _ _] false)
  (-return-to-draft! [_ _] nil)
  (-cancel-application! [_ _] nil)
  (-approve-application! [_ _ _ _] [false nil])
  (-send-attachments! [_ _ _ _] ()))

(deftype ALLUBackingSystem []
  BackingSystem
  (-supported-action? [_ {:keys [action]}]
    (println action)
    (not (or (= action "request-for-complement")
             (= action "undo-cancellation"))))
  (-submit-application! [_ command] (allu/submit-application! command))
  (-update-application! [_ command updated-application]
    (allu/update-application! (assoc command :application updated-application))
    true)
  (-return-to-draft! [_ _] nil)
  (-cancel-application! [_ command] (allu/cancel-application! command))
  (-approve-application! [_ {:keys [application] :as command} _ _]
    (allu/lock-application! command)
    (attachment/save-comments-as-attachment command)
    (let [{:keys [attachments] :as application} (domain/get-application-no-access-checking (:id application))]
      [true (allu/send-attachments! (assoc command :application application)
                                    (filter attachment/unsent? attachments))]))
  (-send-attachments! [_ command attachments _] (allu/send-attachments! command attachments)))

(deftype KRYSPBackingSystem []
  BackingSystem
  (-supported-action? [_ _] true)
  (-submit-application! [_ _] nil)
  (-update-application! [_ _ _] false)
  (-return-to-draft! [_ _] nil)
  (-cancel-application! [_ _] nil)
  (-approve-application! [_ {{:keys [state]} :application :as command} submitted-application lang]
    [true (mapping-to-krysp/save-application-as-krysp command lang submitted-application :current-state state)])
  (-send-attachments! [_ {:keys [user organization application]} attachments lang]
    (mapping-to-krysp/save-unsent-attachments-as-krysp user @organization application attachments lang)))

(sc/defn get-backing-system :- (sc/protocol BackingSystem) [organization :- Organization, permit-type :- PermitType]
  (cond
    (allu/allu-application? (:id organization) permit-type) (->ALLUBackingSystem)
    (org/krysp-integration? organization permit-type) (->KRYSPBackingSystem)
    :else (->NoopBackingSystem)))

(defn- with-implicit-backing-system [method {:keys [organization application] :as command} & args]
  (apply method (get-backing-system @organization (permit/permit-type application)) command args))

;;;; API
;;;; ===================================================================================================================

(def supported-action? (partial with-implicit-backing-system -supported-action?))
(defn validate-action-support [{:keys [action] :as command}]
  (when-not (supported-action? command)
    (fail :error.integration.unsupported-action :action action)))

(def submit-application! (partial with-implicit-backing-system -submit-application!))

(defn- update-application! [{{:keys [id]} :application :as command}]
  (with-implicit-backing-system -update-application! command (domain/get-application-no-access-checking id)))

(defn update-callback [command _]
  (update-application! command)
  nil)

(def return-to-draft! (partial with-implicit-backing-system -return-to-draft!))

(def cancel-application! (partial with-implicit-backing-system -cancel-application!))

(def approve-application! (partial with-implicit-backing-system -approve-application!))

(def send-attachments! (partial with-implicit-backing-system -send-attachments!))
