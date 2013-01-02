(ns lupapalvelu.attachment-itest
  (:use [lupapalvelu.attachment]
        [lupapalvelu.itest-util]
        [midje.sweet]))

(fact
  (let [resp            (command pena :create-application :permitType "buildingPermit" :x 408048 :y 6693225 :address "foo 42, bar" :municipality "753")
        application-id  (:id resp)
        application     (:application (query pena :application :id application-id))
        resp            (command veikko :create-attachment :id application-id :attachmentType {:type-group "tg" :type-id "tid"})
        attachment-id   (:attachmentId resp)
        application     (:application (query pena :application :id application-id))
        attachment      (some #(if (= (:id %) attachment-id) %) (:attachments application))]
    attachment => (contains
                    {:type {:type-group "tg"
                            :type-id "tid"}
                     :state "requires_user_action"
                     :versions []})))
