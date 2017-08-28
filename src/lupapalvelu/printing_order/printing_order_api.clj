(ns lupapalvelu.printing-order.printing-order-api
  (:require [lupapalvelu.action :refer [defquery defcommand]]
            [sade.core :refer :all]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.attachment :as att]
            [lupapalvelu.states :as states]
            [lupapalvelu.attachment.tag-groups :as att-tag-groups]
            [sade.util :as util]))

(def omitted-attachment-type-groups
  [:hakija :osapuolet :rakennuspaikan_hallinta :paatoksenteko :muutoksenhaku
   :katselmukset_ja_tarkastukset :tietomallit])

(defquery attachments-for-printing-order
  {:feature          :printing-order
   :parameters       [id]
   :states           states/post-verdict-states
   :user-roles       #{:applicant}
   :user-authz-roles roles/all-authz-writer-roles}
  [{application :application :as command}]
  (ok :attachments (->> (att/sorted-attachments command)
                        (map att/enrich-attachment)
                        (remove #(and
                                  (util/contains-value? omitted-attachment-type-groups (keyword (-> % :type :type-group)))
                                  (not (:forPrinting %))))
                        (filter (fn [att] (util/contains-value? (:tags att) :hasFile)))
                        (filter #(= (-> % :latestVersion :contentType) "application/pdf")))
      :tagGroups (att-tag-groups/attachment-tag-groups application)))

(defquery printing-order-pricing
  {:feature          :printing-order
   :states           states/post-verdict-states
   :user-roles       #{:applicant}
   :user-authz-roles roles/all-authz-writer-roles}
  [_]
  (ok :pricing {:delivery 6.95
                :volume  [{:min    1
                           :max    6
                           :fixed 90}
                          {:min    7
                           :max   12
                           :fixed 180}
                          {:min   13
                           :additionalInformation "ABCDEFGHIJKLMNOP"}]}))

(defcommand submit-printing-order
            {:description ""
             :parameters [id]
             :states states/post-verdict-states
             :user-roles #{:applicant}} [] ())