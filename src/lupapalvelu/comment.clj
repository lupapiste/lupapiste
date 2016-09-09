(ns lupapalvelu.comment
  (:require [monger.operators :refer :all]
            [clojure.set :refer [rename-keys]]
            [sade.util :as util]
            [sade.strings :as ss]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]))

(defn- enrich-attachment-comment [attachments {{target-type :type target-id :id :as target} :target :as comment}]
  (if (and (= (keyword target-type) :attachment) target-id)
    (assoc comment :target (-> (util/find-by-id target-id attachments)
                               (select-keys [:id :type])
                               (rename-keys {:id :attachmentId :type :attachmentType})
                               (merge target)))
    comment))

(defn enrich-comments [{comments :comments attachments :attachments}]
  (map (partial enrich-attachment-comment attachments) comments))

(defn comment-mongo-update
  ([current-app-state text target type mark-answered user to-user timestamp]
    (comment-mongo-update current-app-state text target type mark-answered user to-user timestamp [:applicant :authority]))
  ([current-app-state text target type mark-answered user to-user timestamp roles]
    {:pre [current-app-state (not (nil? mark-answered))]}

    (let [answered? (and mark-answered (user/authority? user))]
      (util/deep-merge
        {$set  {:modified timestamp}}

        (when-not (and answered? (ss/blank? text))
          {$push {:comments (domain/->comment text target type user to-user timestamp roles)}})

        (case (keyword current-app-state)
          ;; LUPA-371, LUPA-745
          :info (when answered? {$set {:state :answered}})

          ;; LUPA-371
          :answered (when (user/applicant? user) {$set {:state :info}})

          nil)))))
