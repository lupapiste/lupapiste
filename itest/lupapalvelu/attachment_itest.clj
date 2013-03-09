(ns lupapalvelu.attachment-itest
  (:use [lupapalvelu.attachment]
        [lupapalvelu.itest-util]
        [midje.sweet]))

(defn- get-attachment [application-id attachment-id]
  (let [application     (:application (query pena :application :id application-id))]
    (some #(when (= (:id %) attachment-id) %) (:attachments application))))

(defn- approve-attachment [application-id attachment-id]
  (success (command veikko :approve-attachment :id application-id :attachmentId attachment-id)) => true
  (:state (get-attachment application-id attachment-id)) => "ok")

(defn- reject-attachment [application-id attachment-id]
  (success (command veikko :reject-attachment :id application-id :attachmentId attachment-id)) => true
  (:state (get-attachment application-id attachment-id)) => "requires_user_action")

(facts "attachments"
  (let [resp (create-app pena :municipality veikko-muni)
        application-id  (:id resp)]

    (success resp) => true

    (comment-application application-id pena)

    (let [resp            (command veikko :create-attachment :id application-id :attachmentType {:type-group "tg" :type-id "tid"})
          attachment-id   (:attachmentId resp)]

      (fact "Veikko can create an attachment"
            (success resp) => true)

      (fact "attachment has been saved to application"
            (get-attachment application-id attachment-id) => (contains
                                                               {:type {:type-group "tg" :type-id "tid"}
                                                                :state "requires_user_action"
                                                                :versions []}))

      (fact "Veikko can approve attachment"
            (approve-attachment application-id attachment-id))

      (fact "Veikko can reject attachment"
            (reject-attachment application-id attachment-id))

      (fact "Veikko submits the application"
            (success (command veikko :submit-application :id application-id)) => true
            (-> (query veikko :application :id application-id) :application :state) => "submitted")

      (fact "Veikko can still approve attachment"
            (approve-attachment application-id attachment-id))

      (fact "Veikko can still reject attachment"
            (reject-attachment application-id attachment-id)))))

