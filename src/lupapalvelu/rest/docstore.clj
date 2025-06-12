(ns lupapalvelu.rest.docstore
  (:require [lupapalvelu.organization :as org]
            [lupapalvelu.permissions :as perm]
            [lupapalvelu.roles :as roles]
            [monger.operators :refer :all]))

(defn- permission-based-organizations [user]
  (some->> (:orgAuthz user)
           keys
           (keep (fn [org-id]
                   (let [permissions (perm/get-organization-permissions
                                      {:user user
                                       :data {:organizationId org-id}})]
                     (cond
                       (contains? permissions :organization/departmental)
                       [org-id :departmental]

                       (contains? permissions :organization/departmental-limited)
                       [org-id :departmental-limited]))))
           seq))

(defn- oauth-role-based-organizations [user]
  (some->> (:oauth-roles user)
           (keep (fn [{:keys [client-type role organization]}]
                   (let [organization (keyword organization)
                         role         (keyword role)]
                     (when (and (= client-type "docdepartmental")
                                (roles/departmental-roles role))
                       [organization role]))))
           seq))

(defn departmental-organizations
  "Returns a list of [organization role] tuples where role is either
  :departmental or :departmental-limited. Organization is included if its
  department terminal is enabled and the user has the access rights."
  [user]
  (let [org-roles       (concat (permission-based-organizations user)
                                (oauth-role-based-organizations user))
        ;; When a user has multiple roles for the same org (e.g., from both
        ;; orgAuthz and oauth-roles), prefer the stronger "departmental" role.
        strongest-roles (->> org-roles
                             (group-by first)
                             (map (fn [[org-id pairs]]
                                    (let [roles (set (map second pairs))
                                          role  (if (contains? roles :departmental)
                                                  :departmental
                                                  :departmental-limited)]
                                      [org-id role])))
                             (into {}))
        org-ids         (keys strongest-roles)]

    (when (seq org-ids)
      (->> (org/get-organizations {:_id                                {$in org-ids}
                                   :docstore-info.docDepartmentalInUse true}
                                  [:name :scope :docstore-info]
                                  {:order-by {:_id 1}})
           (mapv (fn [org] [org (strongest-roles (-> org :id keyword))]))))))
