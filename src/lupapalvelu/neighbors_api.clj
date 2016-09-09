(ns lupapalvelu.neighbors-api
  (:require [clojure.string :as s]
            [clojure.set :refer [rename-keys]]
            [monger.operators :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :refer [fn-> fn->> dissoc-in] :as util]
            [sade.core :refer [ok fail now]]
            [sade.validators :as v]
            [lupapalvelu.child-to-attachment :as child-to-attachment]
            [lupapalvelu.action :refer [defcommand defquery defraw update-application] :as action]
            [lupapalvelu.application-utils :refer [location->object]]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.security :as security]
            [lupapalvelu.states :as states]
            [lupapalvelu.token :as token]
            [lupapalvelu.ttl :as ttl]
            [lupapalvelu.user :as user]
            [lupapalvelu.vetuma :as vetuma]
            [lupapalvelu.attachment.metadata :as metadata]))

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
                   {:email (user/canonize-email email)
                    :address (select-keys params [:street :city :zip])})}))

(defn- params->new-neighbor [params user]
  (merge
    (params->neighbor params)
    {:id (mongo/create-id)
     :status [{:state :open
               :created (now)
               :user user}]}))

(defn- valid-neighbor? [m]
  (and (map? m) (every? #(or (string? %) (nil? %)) (vals m)) (v/kiinteistotunnus? (:propertyId m))))

(defcommand neighbor-add
  {:parameters [id]
   :user-roles #{:authority}
   :input-validators [(fn [command]
                        (when-not (valid-neighbor? (:data command))
                          (fail :error.invalid-type)))]
   :states states/all-application-states-but-draft-or-terminal}
  [command]
  (let [new-neighbor (params->new-neighbor (:data command) (:user command))]
    (update-application command {$push {:neighbors new-neighbor}})
    (ok :neighborId (:id new-neighbor))))

(defcommand neighbor-add-owners
  {:parameters [id owners]
   :input-validators [(partial action/vector-parameter-of :owners valid-neighbor?)]
   :user-roles #{:authority}
   :states states/all-application-states-but-draft-or-terminal}
  [command]
  (let [new-neighbors (map (fn [data] (params->new-neighbor data (:user command))) owners)]
    (update-application command {$push {:neighbors {$each new-neighbors}}})
    (ok)))

(defcommand neighbor-update
  {:parameters [id neighborId]
   :input-validators [(partial action/non-blank-parameters [:id :neighborId])]
   :user-roles #{:authority}
   :states states/all-application-states-but-draft-or-terminal}
  [command]
  (update-application command
                      {:neighbors {$elemMatch {:id neighborId}}}
                      {$set (->
                              (params->neighbor (:data command))
                              (rename-keys {:propertyId :neighbors.$.propertyId :owner :neighbors.$.owner}))}))

(defcommand neighbor-remove
  {:parameters [id neighborId]
   :input-validators [(partial action/non-blank-parameters [:id :neighborId])]
   :user-roles #{:authority}
   :states states/all-application-states-but-draft-or-terminal}
  [command]
  (update-application command
                      {$pull {:neighbors {:id neighborId}}}))

(defn- neighbor-invite-model [{{token :token neighbor-id :neighborId expires :expires} :data {:keys [id address municipality neighbors]} :application} _ recipient]
  (letfn [(link-fn [lang] (str (env/value :host) "/app/" (name lang) "/neighbor/" id "/" neighbor-id "/" token))]
    {:name (get-in (util/find-by-id neighbor-id neighbors) [:owner :name])
     :address address
     :expires expires
     :city-fi (i18n/localize :fi "municipality" municipality)
     :city-sv (i18n/localize :sv "municipality" municipality)
     :link-fi (link-fn :fi)
     :link-sv (link-fn :sv)}))

(def email-conf {:recipients-fn notifications/from-data
                 :model-fn neighbor-invite-model})
(notifications/defemail :neighbor email-conf)

(defcommand neighbor-send-invite
  {:parameters [id neighborId email]
   :input-validators [(partial action/non-blank-parameters [:email :neighborId])
                      action/email-validator]
   :notified true
   :user-roles #{:applicant :authority}
   :states states/all-application-states-but-draft-or-terminal
   :pre-checks [(fn [{user :user {:keys [options]} :application}]
                  (when (and (:municipalityHearsNeighbors options) (not (user/authority? user)))
                    (fail :error.unauthorized)))]}
  [{:keys [user created] :as command}]
  (let [token (token/make-token-id)
        email (user/canonize-email email)
        expires (+ ttl/neighbor-token-ttl created)]
    (update-application command
                        {:neighbors {$elemMatch {:id neighborId}}}
                        {$push {:neighbors.$.status {:state :email-sent
                                                     :email email
                                                     :token token
                                                     :user user
                                                     :created created}}})
    (notifications/notify! :neighbor (assoc command :data {:email email, :token token, :neighborId neighborId, :expires (util/to-local-datetime expires)}))))

(defcommand neighbor-mark-done
  {:parameters [id neighborId]
   :input-validators [(partial action/non-blank-parameters [:id :neighborId])]
   :user-roles #{:authority}
   :states states/all-application-states-but-draft-or-terminal}
  [{:keys [application user created lang] :as command}]
  (let [new-state {:state :mark-done :user user :created created}]
    (update-application command {:neighbors {$elemMatch {:id neighborId}}} {$push {:neighbors.$.status new-state}})))

(defn- append-doc-schemas [{schema-info :schema-info :as document}]
  (assoc document :schema (schemas/get-schema schema-info)))

(defn- strip-document [doc]
  (-> doc
      (model/strip-blacklisted-data :neighbor)
      model/strip-turvakielto-data
      append-doc-schemas))

(defn ->public [{documents :documents :as application}]
  (-> application
      (select-keys [#_:auth
                    :state
                    :location
                    #_:attachments
                    :organization
                    :title
                    :primaryOperation
                    :secondaryOperations
                    :infoRequest
                    :opened
                    :created
                    :propertyId
                    #_:documents
                    :modified
                    #_:comments
                    :address
                    :permitType
                    :id
                    :municipality])
      location->object
      (assoc :attachments (->> application
                               :attachments
                               (filter (fn-> :type :type-group (= "paapiirustus")))
                               (filter (fn-> :versions empty? not))
                               (filter metadata/public-attachment?)))
      (assoc :documents (map
                          strip-document
                          (remove (fn-> :schema-info :name #{"paatoksen-toimitus-rakval"})
                                  (concat
                                    (filter (fn-> :schema-info :subtype (= "hakija")) documents)
                                    (filter (fn-> :schema-info :type (not= "party")) documents)))))))

(defquery neighbor-application
  {:parameters [applicationId neighborId token]
   :input-validators [(partial action/non-blank-parameters [:applicationId :neighborId :token])]
   :user-roles #{:anonymous}}
  [{user :user created :created :as command}]
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
  [{:keys [user created lang] :as command}]
  (if-let [vetuma-user (rename-keys (vetuma/get-user stamp) {:firstname :firstName :lastname :lastName})]
    (let [application (domain/get-application-no-access-checking applicationId)
          neighbor (util/find-by-id neighborId (:neighbors application))
          ]
      (if-not (valid-token? token (:status neighbor) created)
        (fail :error.token-not-found)
        (let [new-state {:state (str "response-given-" response)
                         :message message
                         :user (user/summary user) ; Most likely nil
                         :vetuma vetuma-user
                         :created created}]
          (do
            (update-application (action/application->command application)
                                {:neighbors {$elemMatch {:id neighborId}}}
                                {$push {:neighbors.$.status new-state}})
            (vetuma/consume-user stamp)
            (child-to-attachment/create-attachment-from-children vetuma-user (domain/get-application-no-access-checking (:id application)) :neighbors neighborId lang)
            (ok)))))
    (fail :error.invalid-vetuma-user)))

;;
;; Loading attachments
;;

; http://localhost:8000/api/raw/neighbor/download-attachment?neighbor-id=51b1b6bfaa24d5fcab8a3239&token=G7s1enGjJrHcwHYOzpJ60wDw3JoIfqGhCW74ZLQhKUSiD7wZ&file-id=51b1b86daa24d5fcab8a32d7
(defraw neighbor-download-attachment
        {:parameters [neighborId token fileId]
   :input-validators [(partial action/non-blank-parameters [:neighborId :token :fileId])]
         :user-roles #{:anonymous}}
        [{created :created}]
        (let [att-info (attachment/get-attachment-file! fileId)
              application (domain/get-application-no-access-checking (:application att-info))
              neighbor (util/find-by-id neighborId (:neighbors application))
              attachment (attachment/get-attachment-info-by-file-id application fileId)
              att-type (-> attachment :type :type-group)]
          (if (and
                (valid-token? token (:status neighbor) created)
                (= att-type "paapiirustus"))
            (attachment/output-attachment fileId true attachment/get-attachment-file!)
            {:status 401
             :headers {"Content-Type" "text/plain"}
             :body "401 Unauthorized"})))
