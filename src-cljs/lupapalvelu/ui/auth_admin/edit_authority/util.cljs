(ns lupapalvelu.ui.auth-admin.edit-authority.util
  (:require [lupapalvelu.ui.common :as common]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn set-toggle [a-set elem]
  (if (a-set elem)
    (disj a-set elem)
    (conj a-set elem)))

(defn roles-with-titles [roles]
  (->> roles
       (map (juxt identity #(common/loc (util/kw-path :authorityrole %))))
       (sort-by second)))

(defn user-oauth-roles-in-organization [org-id user]
  (->> user
       :oauth-roles
       (util/filter-by-key :organization org-id)
       (map :role)
       set))

(defn full-name [user]
  (->> user
       ((juxt :firstName :lastName))
       (ss/join-non-blanks " ")))
