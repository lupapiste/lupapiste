(ns lupapalvelu.neighbors-api
  (:require [clojure.set :refer [rename-keys]]
            [lupapalvelu.action :refer [defcommand defquery defraw update-application] :as action]
            [lupapalvelu.application-utils :refer [location->object]]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.attachment.metadata :as metadata]
            [lupapalvelu.child-to-attachment :as child-to-attachment]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]
            [lupapalvelu.token :as token]
            [lupapalvelu.ttl :as ttl]
            [lupapalvelu.user :as usr]
            [lupapalvelu.vetuma :as vetuma]
            [monger.operators :refer :all]
            [sade.core :refer [ok fail now]]
            [sade.date :as date]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :refer [fn->] :as util]
            [sade.validators :as v]))

(defn- valid-token? [token statuses ts-now]
  {:pre [(number? ts-now) (pos? ts-now)]}
  (if-let [{status-created :created} (util/find-first #(and (= token (:token %)) (= "email-sent" (:state %))) statuses)]
    (and
      (< status-created ts-now)
      (>= (+ status-created ttl/neighbor-token-ttl) ts-now)
      (= "email-sent" (->> statuses  (remove #(= "reminder-sent" (:state %))) last :state)))
    false))

(defn- params->neighbor [{:keys [propertyId email] :as params}]
  {:pre [(every? #(or (string? %) (nil? %)) (vals params))]}
  (util/deep-merge domain/neighbor-skeleton
    {:propertyId propertyId
     :owner      (merge
                   (select-keys params [:type :name :businessID :nameOfDeceased])
                   {:email (ss/canonize-email email)
                    :address (select-keys params [:street :city :zip])})}))

(defn- params->new-neighbor [params user]
  (merge
    (params->neighbor params)
    {:id     (mongo/create-id)
     :status [{:state   :open
               :created (now)
               :user    user}]}))

(defn- valid-neighbor? [m]
  (and (map? m) (every? #(or (string? %) (nil? %)) (vals m)) (v/kiinteisto-ja-maara-alatunnus? (:propertyId m))))

(defn- neighbor-exists
  "Pre-check that fails if the given `neighborId` is not found in the application."
  [{:keys [application data]}]
  (when-let [nid (:neighborId data)]
    (when-not (util/find-by-id nid (:neighbors application))
      (fail :error.neighbor-not-found))))

(defcommand neighbor-add
  {:parameters       [id]
   :user-roles       #{:authority}
   :input-validators [(fn [command]
                        (when-not (valid-neighbor? (:data command))
                          (fail :error.invalid-type)))]
   :states           states/all-application-states-but-draft-or-terminal}
  [command]
  (let [new-neighbor (params->new-neighbor (:data command) (:user command))]
    (update-application command {$push {:neighbors new-neighbor}})
    (ok :neighborId (:id new-neighbor))))

(defcommand neighbor-add-owners
  {:parameters       [id owners]
   :input-validators [(partial action/vector-parameter-of :owners valid-neighbor?)]
   :user-roles       #{:authority}
   :states           states/all-application-states-but-draft-or-terminal}
  [command]
  (let [new-neighbors (map (fn [data] (params->new-neighbor data (:user command))) owners)]
    (update-application command {$push {:neighbors {$each new-neighbors}}})
    (ok)))

(defcommand neighbor-update
  {:parameters       [id neighborId]
   :input-validators [(partial action/non-blank-parameters [:id :neighborId])]
   :pre-checks       [neighbor-exists]
   :user-roles       #{:authority}
   :states           states/all-application-states-but-draft-or-terminal}
  [command]
  (update-application command
                      {:neighbors {$elemMatch {:id neighborId}}}
                      {$set (->
                              (params->neighbor (:data command))
                              (rename-keys {:propertyId :neighbors.$.propertyId :owner :neighbors.$.owner}))}))

(defcommand neighbor-remove
  {:parameters       [id neighborId]
   :input-validators [(partial action/non-blank-parameters [:id :neighborId])]
   :pre-checks       [neighbor-exists]
   :user-roles       #{:authority}
   :states           states/all-application-states-but-draft-or-terminal}
  [{:keys [application] :as command}]
  (update-application command {$pull {:neighbors {:id neighborId}}})
  (when-let [attachment-ids (->> (:attachments application)
                                 (filter (fn [{:keys [source]}]
                                           (= source {:id neighborId :type "neighbors"})))
                                 (map :id)
                                 seq)]
    (attachment/delete-attachments! application attachment-ids)))

(defn- neighbor-invite-model [{{token :token neighbor-id :neighborId expires :expires} :data
                               {:keys [id neighbors]} :application :as command}
                              _
                              recipient]
  (merge (notifications/create-app-model command nil recipient)
         {:name    (get-in (util/find-by-id neighbor-id neighbors) [:owner :name])
          :expires expires
          :link    #(str (env/value :host) "/app/" % "/neighbor#!/neighbor/" id "/" neighbor-id "/" token)}))

(def email-conf {:recipients-fn notifications/from-user
                 :model-fn neighbor-invite-model})

(notifications/defemail :neighbor email-conf)

(defn neighbor-marked-done? [{{neighbor-id :neighborId} :data {:keys [neighbors]} :application}]
  (when (->> neighbors
             (some #(when (= (:id %) neighbor-id) %))
             :status
             (map :state)
             (some (partial re-find #"done|response-given-")))
    (fail :error.neighbor-marked-done)))

(defcommand neighbor-send-invite
  {:parameters       [id neighborId email]
   :description      "Send invite for neighbor. If neighbor is existing user, it's language
   choice is used. If unknown email, language of command initator is used"
   :input-validators [(partial action/non-blank-parameters [:email :neighborId])
                      action/email-validator]
   :notified         true
   :user-roles       #{:applicant :authority}
   :states           states/all-application-states-but-draft-or-terminal
   :pre-checks       [neighbor-exists
                      neighbor-marked-done?
                      (fn [{user :user {:keys [options]} :application}]
                        (when (and (:municipalityHearsNeighbors options) (not (usr/authority? user)))
                          (fail :error.unauthorized)))
                      (permit/validate-permit-type-is permit/R permit/P)]}
  [{:keys [user created] :as command}]
  (let [token   (token/make-token-id)
        email   (ss/canonize-email email)
        expires (+ ttl/neighbor-token-ttl created)]
    (update-application command
                        {:neighbors {$elemMatch {:id neighborId}}}
                        {$push {:neighbors.$.status {:state   :email-sent
                                                     :email   email
                                                     :token   token
                                                     :user    user
                                                     :created created}}})
    (notifications/notify! :neighbor (assoc command :data {:inviter    user
                                                           :token      token,
                                                           :neighborId neighborId,
                                                           :expires    (date/finnish-datetime expires
                                                                                              :zero-pad)}
                                            :user (or (usr/get-user-by-email email) {:email email}),))))

(defcommand neighbor-mark-done
  {:parameters       [id neighborId]
   :input-validators [(partial action/non-blank-parameters [:id :neighborId])]
   :pre-checks       [neighbor-exists]
   :user-roles       #{:authority}
   :states           states/all-application-states-but-draft-or-terminal}
  [{:keys [user created] :as command}]
  (let [new-state {:state :mark-done :user user :created created}]
    (update-application command {:neighbors {$elemMatch {:id neighborId}}} {$push {:neighbors.$.status new-state}})))

(defn- append-doc-schemas [{schema-info :schema-info :as document}]
  (assoc document :schema (schemas/get-schema schema-info)))

(defn- strip-document [doc]
  (-> doc
      (model/strip-blacklisted-data :neighbor)
      model/strip-turvakielto-data
      append-doc-schemas))

(def neighbor-attachment-public-keys
  [:latestVersion :type :modified])

(defn ->public [{documents :documents :as application}]
  (-> application
      (select-keys [:state
                    :location
                    :organization
                    :title
                    :primaryOperation
                    :secondaryOperations
                    :infoRequest
                    :opened
                    :created
                    :propertyId
                    :modified
                    :address
                    :permitType
                    :id
                    :municipality])
      location->object
      (assoc :attachments (->> application
                               :attachments
                               (filter (fn-> :type :type-group (= "paapiirustus")))
                               (filter (fn-> :versions empty? not))
                               (filter metadata/public-attachment?)
                               (map #(select-keys % neighbor-attachment-public-keys))))
      (assoc :documents (map
                          strip-document
                          (remove (fn-> :schema-info schemas/get-schema :info :blacklist set :neighbor)
                                  (concat
                                    (filter (fn-> :schema-info :subtype (= "hakija")) documents)
                                    (filter (fn-> :schema-info :type (not= "party")) documents)))))))

(defquery neighbor-application
  {:parameters [applicationId neighborId token]
   :input-validators [(partial action/non-blank-parameters [:applicationId :neighborId :token])]
   :user-roles #{:anonymous}}
  [{:keys [created]}]
  (let [application (domain/get-application-no-access-checking applicationId)
        neighbor (util/find-by-id neighborId (:neighbors application))]
    (if (valid-token? token (:status neighbor) created)
      (ok :application (->public application))
      (fail :error.token-not-found))))

(defcommand neighbor-response
  {:parameters [applicationId neighborId token response message stamp]
   :input-validators [(partial action/non-blank-parameters [:applicationId :neighborId :token :stamp])
                      (fn [command]
                        (when-not (#{"ok" "comments"} (get-in command [:data :response]))
                          (fail :error.invalid-response)))]
   :user-roles #{:anonymous}}
  [{:keys [user created lang]}]
  (if-let [vetuma-user (rename-keys (vetuma/get-user stamp) {:firstname :firstName :lastname :lastName})]
    (let [application  (domain/get-application-no-access-checking applicationId)
          neighbor     (util/find-by-id neighborId (:neighbors application))]
      (if-not (valid-token? token (:status neighbor) created)
        (fail :error.token-not-found)
        (let [new-state {:state (str "response-given-" response)
                         :message message
                         :user (usr/summary user) ; Most likely nil
                         :vetuma vetuma-user
                         :created created}]
          (do
            (update-application (action/application->command application)
                                {:neighbors {$elemMatch {:id neighborId}}}
                                {$push {:neighbors.$.status new-state}})
            (vetuma/consume-user stamp)
            (when (or (= response "comments")
                      (-> application :organization org/get-organization :no-comment-neighbor-attachment-enabled not))
              (child-to-attachment/create-attachment-from-children
                vetuma-user
                (domain/get-application-no-access-checking (:id application)) :neighbors neighborId lang))
            (ok)))))
    (fail :error.invalid-vetuma-user)))

;;
;; Loading attachments
;;

; http://localhost:8000/api/raw/neighbor/download-attachment?neighbor-id=51b1b6bfaa24d5fcab8a3239&token=G7s1enGjJrHcwHYOzpJ60wDw3JoIfqGhCW74ZLQhKUSiD7wZ&file-id=51b1b86daa24d5fcab8a32d7
(defraw neighbor-download-attachment
        {:parameters [neighborId token fileId applicationId]
         :input-validators [(partial action/non-blank-parameters [:neighborId :token :fileId :applicationId])]
         :user-roles #{:anonymous}}
        [{created :created}]
        (let [application (domain/get-application-no-access-checking applicationId)
              neighbor (util/find-by-id neighborId (:neighbors application))
              attachment (attachment/get-attachment-info-by-file-id application fileId)
              att-type (-> attachment :type :type-group)]
          (if (and
                (valid-token? token (:status neighbor) created)
                (= att-type "paapiirustus"))
            (attachment/output-attachment fileId true (partial attachment/get-attachment-file! application))
            {:status 401
             :headers {"Content-Type" "text/plain"}
             :body "401 Unauthorized"})))
