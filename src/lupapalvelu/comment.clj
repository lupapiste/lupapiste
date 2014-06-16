(ns lupapalvelu.comment
  (:require [clojure.string :refer [blank?]]
            [monger.operators :refer :all]
            [sade.util :as util]
            [lupapalvelu.core :refer [fail!]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]))

(defn comment-mongo-update
  ([current-app-state text target type mark-answered user to-user timestamp]
    (comment-mongo-update current-app-state text target type mark-answered user to-user timestamp [:applicant :authority]))
  ([current-app-state text target type mark-answered user to-user timestamp roles]
    {:pre [current-app-state (or (nil? text) (string? text)) (map? target)
           (not (nil? mark-answered)) type (map? user) (number? timestamp) (sequential? roles)]}

    (when (and to-user (not ((set (map name roles)) (name (:role to-user)))))
      (fail! :error.comment-must-be-visible-to-receiver))

    (let [answered? (and mark-answered (user/authority? user))]
      (util/deep-merge
        {$set  {:modified timestamp}}

        (when-not (and answered? (blank? text))
          {$push {:comments {:text    text
                             :type    type
                             :target  target
                             :created timestamp
                             :roles   roles
                             :to      (user/summary to-user)
                             :user    (user/summary user)}}})

        (case (keyword current-app-state)
          ;; LUPA-371, LUPA-745
          :info (when answered? {$set {:state :answered}})

          ;; LUPA-371 (was: mark-inforequest-answered)
          :answered (when (user/applicant? user) {$set {:state :info}})

          nil)))))
