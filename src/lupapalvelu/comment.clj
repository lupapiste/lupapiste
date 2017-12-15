(ns lupapalvelu.comment
  (:require [monger.operators :refer :all]
            [clojure.set :refer [rename-keys]]
            [sade.util :as util]
            [sade.strings :as ss]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [lupapalvelu.authorization :as auth]))

(defn- enrich-attachment-comment [attachments {{target-type :type target-id :id :as target} :target :as comment}]
  (if (and (= (keyword target-type) :attachment) target-id)
    (assoc comment :target (-> (util/find-by-id target-id attachments)
                               (select-keys [:id :type])
                               (rename-keys {:id :attachmentId :type :attachmentType})
                               (merge target)))
    comment))

(defn- enrich-user-information [auth {user :user :as comment}]
  (let [user-auths  (filter (comp #{(:id user)} :id) auth)
        party-roles (:party-roles (first user-auths))
        other-roles (:other-roles (first user-auths))
        auth-roles  (map (comp keyword :role) user-auths)]
    (->> (or (some (set other-roles) [:authority])
             (first party-roles)
             (some (set auth-roles) [:foreman :owner :statementGiver])
             (when (not-empty auth-roles) :other-auth)
             (when (usr/authority? user)  :authority)
             :other-auth)
         (assoc-in comment [:user :application-role]))))

(defn enrich-comments [{comments :comments attachments :attachments :as application}]
  (->> comments
       (map (partial enrich-attachment-comment attachments))
       (map (partial enrich-user-information (auth/enrich-auth-information application)))))

(defn comment-mongo-update
  ([current-app-state text target type mark-answered user to-user timestamp]
    (comment-mongo-update current-app-state text target type mark-answered user to-user timestamp [:applicant :authority]))
  ([current-app-state text target type mark-answered user to-user timestamp roles]
    {:pre [current-app-state (not (nil? mark-answered))]}

    (let [answered? (and mark-answered (usr/authority? user))]
      (util/deep-merge
        {$set  {:modified timestamp}}

        (when-not (and answered? (ss/blank? text))
          {$push {:comments (domain/->comment text target type user to-user timestamp roles)}})

        (case (keyword current-app-state)
          ;; LUPA-371, LUPA-745
          :info (when answered? {$set {:state :answered}})

          ;; LUPA-371
          :answered (when (usr/applicant? user) {$set {:state :info}})

          nil)))))

(defn- attachment-ids [application]
  (->> (:attachments application)
       (map :id)
       set))

(defn flag-removed-attachment-comments
  "Flags comments relating to removed attachments. Called before any
  attachment filtering. See filter-application-content-for function in
  domain.clj for details. The flags are used when removing
  comments (see below)."
  [application]
  (let [attachment-ids (attachment-ids application)]
    (update application :comments (fn [comments]
                                    (map (fn [{target :target :as comment}]
                                           (cond-> comment
                                             (and (util/=as-kw (:type target) :attachment)
                                                  (not (contains? attachment-ids (:id target))))
                                             (assoc :removed true)))
                                         comments)))))

(defn remove-hidden-attachment-comments
  "Removes comments that are related to attachments that the user is not
  allowed to see. Called after the attachments have been filtered."
  [{:keys [attachments] :as application}]
  (let [attachment-ids (attachment-ids application)]
    (update application :comments (fn [comments]
                                    (remove (fn [{:keys [target removed]}]
                                              (and (util/=as-kw (:type target) :attachment)
                                                   (not removed)
                                                   (not (contains? attachment-ids (:id target)))))
                                            comments)))))
