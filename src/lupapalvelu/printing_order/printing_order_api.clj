(ns lupapalvelu.printing-order.printing-order-api
  (:require [lupapalvelu.action :refer [defquery]]
            [sade.core :refer :all]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.states :as states]
            [lupapalvelu.attachment.tag-groups :as att-tag-groups]
            [sade.util :as util]))

(defquery attachments-for-printing-order
  {:parameters       [id]
   :states           states/post-verdict-states
   :user-roles       #{:authority :applicant}
   :user-authz-roles roles/all-authz-roles
   :org-authz-roles  roles/reader-org-authz-roles}
  [{application :application :as command}]
  (ok :attachments (->> (att/sorted-attachments command)
                        (map att/enrich-attachment)
                        (filter (fn [att] (util/contains-value? (:tags att) :hasFile))))
      :tagGroups (att-tag-groups/attachment-tag-groups application)))