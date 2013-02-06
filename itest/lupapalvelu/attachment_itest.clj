(ns lupapalvelu.attachment-itest
  (:use [lupapalvelu.attachment]
        [lupapalvelu.itest-util]
        [midje.sweet]))

(fact
  (let [resp (create-app pena :municipality veikko-muni)
        application-id  (:id resp)]
    (success resp) => true

    (comment-application application-id pena)

    (let [resp            (command veikko :create-attachment :id application-id :attachmentType {:type-group "tg" :type-id "tid"})
          attachment-id   (:attachmentId resp)
          application     (:application (query pena :application :id application-id))
          attachment      (some #(if (= (:id %) attachment-id) %) (:attachments application))]
      (success resp) => true
      attachment => (contains {:type {:type-group "tg" :type-id "tid"} :state "requires_user_action" :versions []}))))

