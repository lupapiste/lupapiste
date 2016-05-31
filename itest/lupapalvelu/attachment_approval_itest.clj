(ns lupapalvelu.attachment-approval-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]))

(defn- approve-attachment [application-id {:keys [id latestVersion]}]
  (fact {:midje/description (str "approve " application-id \/ id)}
    (command veikko :approve-attachment :id application-id :attachmentId id  :fileId (:fileId latestVersion)) => ok?
    (let [att (get-attachment-by-id veikko application-id id)]
      att => (in-state? "ok"))))

(defn- reject-attachment [application-id {:keys [id latestVersion]}]
  (fact {:midje/description (str "reject " application-id \/ id)}
    (command veikko :reject-attachment :id application-id :attachmentId id :fileId (:fileId latestVersion)) => ok?
    (let [att (get-attachment-by-id veikko application-id id)]
      att => (in-state? "requires_user_action"))))

(facts "attachment approval"
  (let [{application-id :id attachments :attachments} (create-and-open-application  pena :propertyId tampere-property-id :operation "kerrostalo-rivitalo")]

    (fact "Veikko can not approve attachment before any files are uploaded"
      (command veikko :reject-attachment
        :id application-id
        :attachmentId (-> attachments first :id)
        :fileId (-> attachments first :latestVersion :fileId)) => fail?)

    (upload-attachment pena application-id (first attachments) true)

    (let [{:keys [attachments]} (query-application veikko application-id)]
      (fact "Veikko can approve attachment"
        (approve-attachment application-id (first attachments)))

      (fact "Veikko can reject attachment"
        (reject-attachment application-id (first attachments)))

      (fact "Pena submits the application"
        (command pena :submit-application :id application-id) => ok?
        (:state (query-application veikko application-id)) => "submitted")

      (fact "Veikko can still approve attachment"
        (approve-attachment application-id (first attachments)))

      (fact "Veikko can still reject attachment"
        (reject-attachment application-id (first attachments))))

    ))
