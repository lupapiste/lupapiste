(ns lupapalvelu.replies-api
  "This api deals with replies (Vastine) connected to statements, replies and bulletins"
  (:require [lupapalvelu.action :refer [defcommand defquery] :as action]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.notifications :as notifications :refer [get-application-link]]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.replies-schemas :refer [Reply ReplyAnswer]]
            [lupapalvelu.user :as user]
            [monger.operators :refer [$elemMatch]]
            [sade.core :refer [now ok]]
            [sade.schemas :as ssc]
            [sade.util :as util]))


(defn generate-display-name [data issue-type]
  (case issue-type
    :bulletin  (-> data :contact-info user/full-name)
    :neighbor  (->> data :status (util/filter-by-key :state "response-given-comments") first :vetuma user/full-name)
    :statement (-> data :person :name)))

(defn- populate-issue
  "Takes the type and id of one issue and populates that issue with all its related data"
  [issue-type id bulletins application]
  (case (keyword issue-type)
    :bulletin  (let [data (get bulletins id)]
                 (assoc data
                        :issue-type   issue-type
                        :display-name (generate-display-name data :bulletin)))
    :neighbor  (let [data (->> application :neighbors (util/find-by-id id))]
                 (assoc data
                        :issue-type   issue-type
                        :display-name (generate-display-name data :neighbor)))
    :statement (let [data  (->> application :statements (util/find-by-id id))
                     files (->> application
                                :attachments
                                (filterv #(= (-> % :target :id) id)))]
                 (assoc data
                        :issue-type   issue-type
                        :files        files
                        :display-name (generate-display-name data :statement)))))

(defn- populate-issues
  "Takes a list of issue ids and issue types and populates the issue with all related data"
  [issues application]
  (let [bulletins-ids (->> issues (util/filter-by-key :issue-type "bulletin") (map :id))
        bulletins     (->> (mongo/select :application-bulletin-comments {:_id {"$in" bulletins-ids}})
                           (reduce (fn [acc bulletin] (assoc acc (:id bulletin) bulletin)) {}))]
    (reduce (fn [acc {:keys [id issue-type]}]
            (assoc acc id (populate-issue issue-type id bulletins application)))
            {}
          issues)))

(defn update-related-issues
  "Takes a reply and a map of all populated issues.
  Updates all the related issues in the reply to to populated ones"
  [reply populated-issues]
  (assoc reply :related-issues (mapv (fn [issue] (get populated-issues (:id issue)))
                                     (:related-issues reply))))


(defn- create-email-link [{:keys [application system]} conf recipient]
  (fn [lang] (str (get-application-link system application (:tab conf) lang recipient) "/process")))

(notifications/defemail :reply-request-created
  {:subject-key   "reply-request-created"
   :model-fn      (fn [command conf recipient]
                    {:link        (create-email-link command conf recipient)
                     :application (:application command)})})

(notifications/defemail :reply-request-answered
  {:subject-key   "reply-request-answered"
   :recipients-fn (fn [{:keys [reply]}] [(:creator reply)])
   :model-fn      (fn [command conf recipient]
                    {:link         (create-email-link command conf recipient)
                     :application  (:application command)})})

(def read-replies-permission
  [{:context {:application {:permitType permit/ymp-permit-type-kw-set }}
    :required [:application/read]}])

(def authority-replies-permission
  [{:context  {:application {:state      #{:submitted :sent :complementNeeded}
                             :permitType permit/ymp-permit-type-kw-set}}
    :required [:application/edit-replies]}])

(def answer-replies-permission
  [{:context {:application {:permitType permit/ymp-permit-type-kw-set}}
    :required [:application/edit]}])

(defquery get-reply-target-issues
  {:description      "Returns all the statements, bulletins and neighbour responses connected to some application.
   Will add map a :display-name attribute to each issue for easier handling of names"
   :request-schema {:id ssc/ApplicationId}
   :permissions      read-replies-permission}
  [{:keys [application data]}]
  (let [statements (->> application
                        :statements
                        (keep #(when (= (:state %) "given")
                                 (assoc % :display-name (generate-display-name % :statement))))
                        (into []))
        bulletins  (->> (mongo/select :application-bulletin-comments {:bulletinId (:id data)})
                        (mapv #(assoc % :display-name (generate-display-name % :bulletin))))
        neighbors  (->> application
                        :neighbors
                        (keep (fn [neighbour]
                                (when (->> neighbour
                                           :status
                                           (some #(-> % :state (= "response-given-comments"))))
                                  (assoc neighbour :display-name (generate-display-name neighbour :neighbor)))))
                        (into []))]
    (ok {:replies-targets {:statements statements :bulletins bulletins :neighbors neighbors}})))

(defquery replies
  {:description    "Returns all the reply requests connected to some application id"
   :request-schema {:id ssc/ApplicationId}
   :permissions      read-replies-permission}
  [{:keys [application data]}]
  (let [replies           (mongo/select :replies {:application-id (:id data)})
        populated-issues  (-> (mapcat :related-issues replies)
                              set
                              (populate-issues application))
        populated-replies (->> replies
                               (reduce (fn [acc reply]
                                         (assoc acc (:id reply) (update-related-issues reply populated-issues)))
                                       {}))]
    (ok {:replies populated-replies})))

(defcommand create-reply
  {:description    "Create a new reply request for some application id"
   :request-schema {:id    ssc/ApplicationId
                    :reply Reply}
   :permissions    authority-replies-permission}
  [{:keys [user application] {:keys [reply]} :data :as command}]
  (let [reply             (assoc reply
                                 :reply-created (now)
                                 :creator       (user/summary user)
                                 :id            (mongo/create-id))
        populated-issues  (populate-issues (:related-issues reply) application)
        reply-with-issues (update-related-issues reply populated-issues)]
    (mongo/insert :replies reply)
    (notifications/notify! :reply-request-created (assoc command :reply-id (:id reply)))
    (ok {:reply reply-with-issues})))

(defcommand delete-reply
  {:description    "Deletes a reply request from an application"
   :request-schema {:id      ssc/ApplicationId
                    :replyId ssc/ObjectIdStr}
   :permissions    authority-replies-permission}
  [{{:keys [id replyId]} :data}]
  (mongo/remove-many :replies {:_id replyId :application-id id})
  (ok))

(defcommand answer-reply
  {:description    "Create an answer to a reply request"
   :request-schema {:id      ssc/ApplicationId
                    :replyId ssc/ObjectIdStr
                    :answer  ReplyAnswer}
   :permissions    answer-replies-permission}
  [{:keys [user] {:keys [id replyId answer]} :data :as command}]
  (let [reply         (mongo/select-one :replies {:_id replyId :application-id id})
        answer        (assoc answer :created (now) :user (user/summary user))
        updated-reply (mongo/update-one-and-return :replies
                                                   {:_id replyId}
                                                   {:$set {:answer answer}})]
    (when updated-reply
      (notifications/notify! :reply-request-answered (assoc command :reply reply :answer answer))
      (ok {:success updated-reply}))))

(defquery issue-reply-state
  {:description    "Returns the state of some issue connected to a reply request"
   :request-schema {:id      ssc/ApplicationId
                    :issueId ssc/ObjectIdStr}
   :permissions    read-replies-permission}
  [{{:keys [id issueId]} :data}]
  (let [reply      (mongo/select :replies {:application-id id
                                           :related-issues {$elemMatch {:id issueId}}})
        has-answer (some :answer reply)
        state      (cond
                     (some? has-answer) :answered
                     (seq reply)        :waiting-for-answer
                     :else              :no-reply)]
    (ok {:state state})))

(defquery replies-connected-to-issue
  {:description    "Returns all reply requests that are connected to some issue"
   :request-schema {:id      ssc/ApplicationId
                    :issueId ssc/ObjectIdStr}
   :permissions    read-replies-permission}
  [{{:keys [id issueId]} :data}]
  (let [replies (->> (mongo/select :replies {:application-id id
                                             :related-issues {$elemMatch {:id issueId}}})
                     (into []))]
    (ok {:replies replies})))
