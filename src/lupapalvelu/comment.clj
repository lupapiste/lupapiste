(ns lupapalvelu.comment
  (:require [clojure.string :refer [blank?]]
            [monger.operators :refer :all]
            [sade.util :as util]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as user]))

(defn comment-mongo-update
  ([current-app-state text target type mark-answered user to-user timestamp]
    (comment-mongo-update current-app-state text target type mark-answered user to-user timestamp [:applicant :authority]))
  ([current-app-state text target type mark-answered user to-user timestamp roles]
    {:pre [current-app-state (not (nil? mark-answered))]}

    (let [answered? (and mark-answered (user/authority? user))]
      (util/deep-merge
        {$set  {:modified timestamp}}

        (when-not (and answered? (blank? text))
          {$push {:comments (domain/->comment text target type user to-user timestamp roles)}})

        (case (keyword current-app-state)
          ;; LUPA-371, LUPA-745
          :info (when answered? {$set {:state :answered}})

          ;; LUPA-371
          :answered (when (user/applicant? user) {$set {:state :info}})

          nil)))))
