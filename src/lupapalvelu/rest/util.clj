(ns lupapalvelu.rest.util
  (:require [lupapalvelu.company :as company]
            [lupapalvelu.organization :as org]
            [lupapalvelu.rest.docstore :as docstore]))


(defn get-organization-roles
  "Returns a list of the roles visible for a user in a REST endpoint"
  [user]
  (let [dept-org-ids (->> (docstore/departmental-organizations user)
                          (map :id)
                          set)]
    (->> (:orgAuthz user)
         (map (fn [[org-id org-authz-roles]]
                (let [org-str           (name org-id)
                      organization      (org/get-organization org-str [:reporting-enabled])
                      remove-reporters? (not (:reporting-enabled organization))
                      ;; Departmental role can be either implicit or explicit. Thus, it
                      ;; needs to be removed if the organization does not have
                      ;; Departmental (Virkapääte).
                      org-authz-roles ((if (dept-org-ids org-str) conj disj)
                                       (set org-authz-roles)
                                       "departmental")]
                  {:id    org-str
                   :roles (seq (cond-> org-authz-roles
                                 remove-reporters? (disj "reporter")))})))
         (filter :roles)
         (not-empty))))

(defn rest-user
  "Basic information for REST endpoints (e.g, '/rest/user').
  Organizations can have 'pseudo-role' departmental"
  [{:keys [oauth-role] :as user}]
  (let [company-id    (get-in user [:company :id])
        company       (when company-id (company/find-company-by-id company-id))
        organizations (get-organization-roles user)]
    (cond-> (select-keys user [:id :role :email :firstName :lastName])
      oauth-role                (assoc :role oauth-role)
      company                   (assoc :company {:id company-id :name (:name company)})
      (not-empty organizations) (assoc :organizations organizations))))
