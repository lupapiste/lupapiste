(ns lupapalvelu.ui.auth-admin.edit-authority.util
  (:require [lupapalvelu.ui.auth-admin.edit-authority.state :as state]
            [lupapalvelu.ui.common :refer [loc query command]]))

(defn- set-toggle [a-set elem]
  (if (a-set elem)
    (disj a-set elem)
    (conj a-set elem)))

(defn- save-org-info-to-state [org]
  (let [allowed-roles (:allowedRoles org)
        org-id        (keyword (:id org))]
    (reset! state/allowed-roles allowed-roles)
    (reset! state/org-id org-id)))

(defn- get-organization []
  (query "organization-by-user"
         (fn [result]
           (-> result
               :organization
               (save-org-info-to-state)))))

(defn- get-authority []
  (query "user-for-edit-authority"
         (fn [result]
           (->> result
                :data
                (reset! state/authority)))
         :authority-id @state/authority-id))
