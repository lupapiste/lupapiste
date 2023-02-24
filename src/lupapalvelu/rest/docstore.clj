(ns lupapalvelu.rest.docstore
  (:require [lupapalvelu.organization :as org]
            [lupapalvelu.permissions :as perm]
            [monger.operators :refer :all]))



(defn departmental-organizations
  "Organizations that `user` has an `:organization/departmental` permission and
  Departmental (Virkapääte) is enabled."
  [user]
  (when-let [org-ids (some->> (:orgAuthz user)
                              keys
                              (filter (fn [org-id]
                                        (contains? (perm/get-organization-permissions
                                                     {:user user
                                                      :data {:organizationId org-id}})
                                                   :organization/departmental)))
                              seq)]
    (org/get-organizations {:_id                                {$in org-ids}
                            :docstore-info.docDepartmentalInUse true}
                           [:name :scope :docstore-info])))
