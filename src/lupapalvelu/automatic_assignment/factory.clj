(ns lupapalvelu.automatic-assignment.factory
  "Trigger-specific (attachment, notice form, foreman, review) automatic assignment
  stuff. Mainly creating, updating and deleting assignments."
  (:require [lupapalvelu.assignment :as assi]
            [lupapalvelu.automatic-assignment.core :as automatic]
            [lupapalvelu.automatic-assignment.email :as email]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))


(sc/defn ^:private ^:always-validate process-filter-recipients
  [{:keys [application user created] :as command}
   target     :- {:group sc/Str
                  :id    sc/Str}
   trigger    :- (sc/enum assi/notice-form-trigger assi/foreman-trigger)
   recipients :- (sc/maybe [automatic/FilterRecipient])
   email-options]
  (let [user (usr/summary user)]
    (doseq [{:keys [filter-id filter-name recipient] :as fltr} recipients]
      (if-let [assignments (seq (mongo/select :assignments
                                              (cond-> {:application.id (:id application)
                                                       :filter-id      filter-id}
                                                (= trigger assi/notice-form-trigger)
                                                (assoc :targets.group (:group target))
                                                (= trigger assi/foreman-trigger)
                                                (assoc :trigger assi/foreman-trigger))))]
        (assi/update-assignments {:_id {$in (map :id assignments)}}
                                 {$set  {:recipient   recipient
                                         :description filter-name
                                         :status      "active"
                                         :modified    created
                                         :filter-id   filter-id}
                                  $push {:targets (assoc target :timestamp created)
                                         :states  {:type      "targets-added"
                                                   :user      user
                                                   :timestamp created}}})
          (assi/insert-assignment (assi/new-assignment command
                                                       {:recipient   recipient
                                                        :trigger     trigger
                                                        :description filter-name
                                                        :targets     [target]
                                                        :filter-id   filter-id})))
      (email/send-email-notification fltr
                                     (assoc email-options
                                            :application application)))))


(defn process-foreman-automatic-assignments
  "The only way to create foreman assignments is via automatic assignment filters. This is
  similar to notice form assignments. However, the assignments are managed
  differently. Each foreman role corresponding to the same filter are targets within the
  same assignment. When a termination request is confirmed, its target is removed from the
  assignments. Accordingly, an assignment without targets is removed. In other words, the
  user does not need to manually mark the assignments ready.

  The assignment recipient resolution is determined by the automatic assignment filter
  resolution."
  [command foreman-app-id {:keys [role-code] :as options}]
  (when (assi/assignments-enabled? command)
    (let [foreman-role (automatic/resolve-foreman-role role-code)]
      (process-filter-recipients
        command
        {:group "foremen" :id foreman-app-id}
        assi/foreman-trigger
        (automatic/resolve-filters command :foreman-role foreman-role)
        (assoc options :foreman-role foreman-role)))))

(defn process-notice-form-automatic-assignments
  "Each individual form is represented as a target in the notice form assignments for the
  application. When a form is approved or rejected, its target is removed from the
  assignments. Accordingly, an assignment without targets is removed. In other words, the
  user does not need to manually mark the assignments ready.

  The assignment recipient resolution is determined by the automatic assignment filter
  resolution."
  [{:keys [application] :as command} {form-id      :id
                                      form-type    :type
                                      form-history :history
                                      :as form}]
  (when (assi/assignments-enabled? command)
    (let [group (str "notice-forms-" form-type )]
      (if (some-> form-history last :state (= "open"))
        (process-filter-recipients command
                                   {:group group :id form-id}
                                   assi/notice-form-trigger
                                   (automatic/resolve-filters command
                                                              :notice-form-type
                                                              form-type)
                                   {:notice-form form})
        (assi/remove-target-from-assignments (:id application) form-id)))))

(defn- application-open-reviews [application]
  (filter (fn [{:keys [state schema-info]}]
            (and (util/includes-as-kw? #{:review :review-backend}
                                       (:subtype schema-info))
                 (not (util/includes-as-kw? #{:sent :faulty_review_task}
                                            state))))
          (:tasks application)))

(defn prune-application-review-assignments
  "Deletes every review request assignment that no longer refers to an open review."
  ([application-id]
   (let [application (mongo/by-id :applications application-id [:tasks])
         open-task-ids (map :id (application-open-reviews application))]
     (mongo/remove-many :assignments
                        {:targets.id     {$nin open-task-ids}
                         :application.id (:id application)
                         :trigger        assi/review-trigger}))))

(defn delete-review-assignments
  "Deletes every review assignment that refers to any given `task-ids`. `task-ids` can be
  either an individual task-id (string) or list of ids."
  ([application-id task-ids]
   (mongo/remove-many :assignments
                      {:targets.id     (util/pcond->> task-ids
                                         sequential? (hash-map $in))
                       :application.id application-id
                       :trigger        assi/review-trigger}))
  ([command application-id task-ids]
   (when (assi/assignments-enabled? command)
     (delete-review-assignments application-id task-ids))))

(defn find-open-review
  "Task if `task-id` matches an open review, otherwise nil."
  [application task-id]
  (util/find-by-id task-id (application-open-reviews application)))

(defn process-review-automatic-assignments
  "Each review request assignment matches only one review. When a review is deleted or done,
  the assignment is deleted. So, `task-id` can refer either an existing or deleted task."
  [{:keys [application user created] :as command} task-id review-request]
  (when (assi/assignments-enabled? command)
    (if-let [review-name (some-> (find-open-review application task-id)
                                 :taskname
                                 ss/trim
                                 ss/capitalize)]
      (doseq [{:keys [filter-id recipient]
               :as   fltr} (automatic/resolve-filters command
                                                      :review-name review-name)
              :let         [m {:targets     [{:group     "review-request"
                                              :id        task-id
                                              :timestamp created}]
                               :recipient   recipient
                               :status      "active"
                               :description review-name
                               :states      [{:type      "created"
                                              :user      (usr/summary user)
                                              :timestamp created}]
                               :modified    created}]]
        ;; The assignment should never already exist, but anyhow...
        (if-let [assignment (mongo/select-one :assignments
                                              {:application.id (:id application)
                                               :trigger        assi/review-trigger
                                               :filter-id      filter-id
                                               :targets.id     task-id})]
          (assi/update-assignments {:_id assignment} {$set m})
          (assi/insert-assignment (assi/new-assignment command
                                                       (assoc m
                                                              :targets [{:group "review-request"
                                                                         :id    task-id}]
                                                              :filter-id filter-id
                                                              :trigger assi/review-trigger))))
        (email/send-email-notification fltr {:application    application
                                             :review-name    review-name
                                             :review-request review-request}))
      ;; Not an open review, but let's clean up just in case.
      (delete-review-assignments (:id application) task-id))))

(defn- group-by-filters [organization application targets]
  (->> targets
       (mapcat (fn [{:keys [trigger-type] :as target}]
                 (map (fn [fltr]
                        {:filter fltr :target target})
                      (automatic/resolve-filters {:organization    organization
                                                  :application     application
                                                  :attachment-type trigger-type}))))
       (group-by :filter)
       (util/map-values (partial map :target))))

(defn attachment-assignment-processor
  "Returns function that upserts assignments based on attachment trigger targets. Used as
  `:postprocess-fn` when attachments are updated."
  [response-fn]
  (fn [& response]
    (let [{:keys [user organization application targets assignment-group
                  timestamp]} (apply response-fn response)]
      (when-not (= (:permitType application) permit/ARK)
        (doseq [[fltr targets] (group-by-filters organization application targets)]
          (assi/upsert-assignment-targets (usr/summary user)
                                          application
                                          fltr
                                          timestamp
                                          assignment-group
                                          targets)
          (email/send-email-notification fltr
                                         {:application     application
                                          :attachment-type (-> targets first :trigger-type)}))))))
