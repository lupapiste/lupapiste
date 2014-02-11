(ns lupapalvelu.comment
  (:require [clojure.string :refer [blank?]]
            [monger.operators :refer :all]
            [lupapalvelu.mongo :as mongo]
            [sade.util :as util]
            [lupapalvelu.user :as user]))

(defn comment-mongo-update [current-app-state text target type mark-answered user to-user timestamp]
  (let [answered? (and mark-answered (user/authority? user))]
    (util/deep-merge
      {$set  {:modified timestamp}}

      (when-not (and answered? (blank? text))
        {$push {:comments {:text    text
                           :type    type
                           :target  target
                           :created timestamp
                           :to      (user/summary to-user)
                           :user    (user/summary user)}}})

      (case (keyword current-app-state)
        ;; LUPA-XYZ (was: open-application)
        ;;:draft  (when-not (blank? text) {$set {:state :open, :opened timestamp}})

        ;; LUPA-371, LUPA-745
        :info (when answered? {$set {:state :answered}})

        ;; LUPA-371 (was: mark-inforequest-answered)
        :answered (when (user/applicant? user) {$set {:state :info}})

        nil))))