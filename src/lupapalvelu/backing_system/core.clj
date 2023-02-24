(ns lupapalvelu.backing-system.core
  "A Facade for backing system integrations."
  (:require [clojure.core.match :refer [match]]
            [schema.core :as sc :refer [defschema]]
            [sade.core :refer [fail]]
            [sade.strings :as ss]
            [lupapalvelu.allu :refer [clean-up-drawings-by-location-type]]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.backing-system.allu.core :as allu]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]
            [lupapalvelu.backing-system.krysp.application-from-krysp :as krysp-fetch]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.organization :as org :refer [Organization]]
            [lupapalvelu.permit :as permit :refer [PermitType]]))

;;; TODO: asianhallinta
;;; TODO: More methods

(def krysp-exclusive-actions #{"request-for-complement"
                               "undo-cancellation"
                               "confirm-foreman-termination"
                               "terminate-foreman"})

(defschema ApplicationFormat
  {:content-type sc/Str
   :charset sc/Str})

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
    "Send `attachments` of `application` to backing system. Returns a seq of attachment file IDs that were sent.")
  (-application-format [self]
    "What format is used for raw backing system application data as [[ApplicationFormat]]
    e.g. `{:content-type \"application/xml\", :encoding \"UTF-8\"}`. nil if app data is not even available.")
  (-get-application [self command permit-type tagged-id raw?]
    "Load application data from backing system, nil if not found. If `raw?`, don't parse it, just return a string.
    `tagged-id` is either `[:application-id \"LP-...\"]` or `[:kuntalupatunnus \"...\"]`.")
  (-terminate-foreman! [self command terminated-foreman-application confirmation? lang]
    "Sends a message to the backing system indicating that an authority has terminated or accepted the termination
     request of a foreman. The confirmation? flag is true when the termination was requested by a non-authority."))

(deftype NoopBackingSystem []
  BackingSystem
  (-supported-action? [_ _] true)                           ; FIXME: Incorrect in general
  (-submit-application! [_ _] nil)
  (-update-application! [_ _ _] false)
  (-return-to-draft! [_ _] nil)
  (-cancel-application! [_ _] nil)
  (-approve-application! [_ _ _ _] [false nil])
  (-send-attachments! [_ _ _ _] ())
  (-application-format [_] nil)
  (-get-application [_ _ _ _ _] nil)
  (-terminate-foreman! [_ _ _ _ _] nil))

(deftype ALLUBackingSystem []
  BackingSystem
  (-supported-action? [_ {:keys [action]}]
    (not (contains? krysp-exclusive-actions action)))
  (-submit-application! [_ command] (allu/submit-application! command))
  (-update-application! [_ command updated-application]
    (allu/update-application! (assoc command :application updated-application))
    true)
  (-return-to-draft! [_ _] nil)
  (-cancel-application! [_ command] (allu/cancel-application! command))
  (-approve-application! [_ {:keys [application] :as command} _ _]
    (if (-> application :integrationKeys :ALLU :id)
      (do (clean-up-drawings-by-location-type command)
          (allu/lock-application! command)
          (allu/send-application-as-attachment! command)
          (attachment/save-comments-as-attachment command)
          (let [{:keys [attachments] :as application} (domain/get-application-no-access-checking (:id application))]
            [true (allu/send-attachments! (assoc command :application application)
                                          (filter (every-pred attachment/transmittable-to-krysp?
                                                              #(or (-> % :target :type (= "statement"))
                                                                   (attachment/unsent? %)))
                                                  attachments))]))
      (throw (Exception. (str "Missing ALLU integration id in application " (:id application))))))
  (-send-attachments! [_ command attachments _] (allu/send-attachments! command attachments))
  (-application-format [_] {:content-type "application/json", :charset "UTF-8"})
  (-get-application [_ command _ tagged-id raw?]
    (when-let [application (domain/get-application-no-access-checking
                             (match tagged-id
                               [:application-id id] id
                               [:kuntalupatunnus backend-id] {:integrationKeys.ALLU.kuntalupatunnus backend-id}))]
      (allu/load-allu-application-data (assoc command :application application) raw?)))
  (-terminate-foreman! [_ _ _ _ _] nil))

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
    (mapping-to-krysp/save-unsent-attachments-as-krysp user @organization application attachments lang))
  (-application-format [_] {:content-type "application/xml", :charset "UTF-8"})
  (-get-application [_ {:keys [organization]} permit-type tagged-id raw?]
    (match tagged-id
      [:application-id id]
      (krysp-fetch/get-application-xml-by-application-id {:id id, :organization organization, :permitType permit-type}
                                                         raw?)
      [:kuntalupatunnus backend-id]
      (krysp-fetch/get-application-xml-by-backend-id {:organization organization, :permitType permit-type}
                                                     backend-id raw?)))
  (-terminate-foreman! [_ command foreman-app confirmation? lang]
    (mapping-to-krysp/foreman-termination-as-kuntagml command foreman-app confirmation? lang)))

(sc/defn get-backing-system :- (sc/protocol BackingSystem) [organization :- Organization, permit-type :- PermitType]
  (cond
    (allu/allu-application? (:id organization) permit-type) (->ALLUBackingSystem)
    (org/krysp-write-integration? organization permit-type) (->KRYSPBackingSystem)
    :else (->NoopBackingSystem)))

(defn- with-implicit-backing-system [method {:keys [organization application] :as command} & args]
  (apply method (get-backing-system @organization (permit/permit-type application)) command args))

;;;; API
;;;; ===================================================================================================================

(def supported-action? (partial with-implicit-backing-system -supported-action?))
(defn validate-action-support [{:keys [action application organization] :as command}]
  (when (and application organization)
    (when-not (supported-action? command)
      (fail :error.integration.unsupported-action :action action))))

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

(sc/defn application-format :- (sc/maybe ApplicationFormat) [organization :- Organization, permit-type :- PermitType]
  (-application-format (get-backing-system organization permit-type)))

(sc/defn application-format->content-type :- sc/Str [{:keys [content-type charset]} :- ApplicationFormat]
  (str content-type ";charset=" charset))

(sc/defn application-format-ending :- sc/Str [{:keys [content-type]} :- ApplicationFormat]
  (case content-type
    "application/xml" "xml"
    "application/json" "json"))

(sc/defn application-format-content-disposition :- sc/Str
  [app-format :- ApplicationFormat & args]
  (format "attachment;filename=\"%s.%s\"" (ss/join "-" args) (application-format-ending app-format)))

(defn get-application [{:keys [organization] :as command} permit-type tagged-id raw?]
  (-> (get-backing-system organization permit-type)
      (-get-application command permit-type tagged-id raw?)))

(defn terminate-foreman! [{:keys [organization] :as command} permit-type foreman-app confirmation? lang]
  (-> (get-backing-system @organization permit-type)
      (-terminate-foreman! command foreman-app confirmation? lang)))
