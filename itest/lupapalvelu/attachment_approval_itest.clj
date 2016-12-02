(ns lupapalvelu.attachment-approval-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]))

(apply-remote-minimal)

(defn latest-approval [apikey application-id attachment-id]
  (let [{:keys [approvals latestVersion]} (get-attachment-by-id apikey application-id attachment-id)]
    (get approvals (-> latestVersion :originalFileId keyword))))

(defn last-attachment [application-id]
  (-> (query-application veikko application-id) :attachments last))

(defn- approve-attachment [application-id {:keys [id latestVersion]}]
  (fact {:midje/description (str "approve " application-id \/ id)}
    (command veikko :approve-attachment :id application-id :attachmentId id  :fileId (:fileId latestVersion)) => ok?
    (let [approved (latest-approval veikko application-id id)]
      (:state approved) => "ok"
      (:user approved) => {:id veikko-id, :firstName "Veikko", :lastName "Viranomainen"}
      (:timestamp approved) => pos?)))

(defn- reject-attachment [application-id {:keys [id latestVersion]}]
  (fact {:midje/description (str "reject " application-id \/ id)}
    (command veikko :reject-attachment :id application-id :attachmentId id :fileId (:fileId latestVersion)) => ok?
    (let [approved (latest-approval veikko application-id id)]
      (:state approved) => "requires_user_action"
      (:user approved) => {:id veikko-id, :firstName "Veikko", :lastName "Viranomainen"}
      (:timestamp approved) => pos?)))

(defn- reject-attachment-note [application-id {:keys [id latestVersion]} note]
  (let [{:keys [fileId originalFileId]} latestVersion]
   (fact {:midje/description (str "reject-note" application-id "/" id ":" note)}
         (command veikko :reject-attachment-note :id application-id :attachmentId id :fileId fileId :note note) => ok?
         (let [approved (latest-approval veikko application-id id)]
           (:state approved) => "requires_user_action"
           (:note approved) => note))))

(defn- delete-latest-version [apikey application-id {:keys [id latestVersion]} success?]
  (let [{:keys [fileId originalFileId]} latestVersion]
    (fact {:midje/description (str "Delete latest version" application-id "/" id)}
          (command apikey :delete-attachment-version :id application-id
                   :attachmentId id :fileId fileId :originalFileId originalFileId)
          => (if success? ok? unauthorized?)
          (let [{:keys [approvals] :as att} (get-attachment-by-id veikko application-id id)]
            (get approvals (keyword originalFileId)) => (if success? nil? truthy)))))


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

      (fact "Veikko sets reject note"
            (reject-attachment-note application-id (first attachments) "Bu hao!"))

      (fact "Pena submits the application"
        (command pena :submit-application :id application-id) => ok?
        (:state (query-application veikko application-id)) => "submitted")

      (fact "Veikko can still approve attachment"
            (approve-attachment application-id (first attachments)))

      (fact "Pena cannot delete approved version"
            (delete-latest-version pena application-id (first attachments) false))

      (fact "Veikko can still reject attachment"
            (reject-attachment application-id (first attachments)))
      (fact "Pena cannot delete rejected version"
            (delete-latest-version pena application-id (first attachments) false)))

    (fact "Pena can delete new version"
          (upload-attachment pena application-id (first attachments) true)
          (let [{:keys [attachments]} (query-application veikko application-id)]
            (delete-latest-version pena application-id (first attachments) true)))

    (upload-attachment pena application-id (first attachments) true)

    (let [{:keys [attachments]} (query-application veikko application-id)
          att (first attachments)]

      (fact "After new version upload the attachment is no longer approved/rejected"
            (let [{:keys [approvals latestVersion]} att
                  approved (get approvals (keyword (:fileId latestVersion)))]
              (:state approved) => "requires_authority_action"))

      (fact "Veikko rejects attachment"
            (reject-attachment application-id (first attachments)))

      (fact "Veikko sets reject note"
            (reject-attachment-note application-id (first attachments) "Zhe ge ye bu hao!"))

      (fact "Veikko sets reject note again"
            (reject-attachment-note application-id (first attachments) "Mei wenti.")
            (let [{:keys [attachments]} (query-application veikko application-id)
                  {approvals :approvals} (first attachments)]
              (count approvals) => 2
              (map :note (vals approvals)) => ["Bu hao!" "Mei wenti."]))

      (fact "Veikko re-approves"
            (approve-attachment application-id (first attachments)))

      (fact "Delete version"
            (delete-latest-version veikko application-id att true))

      (let [{:keys [attachments]} (query-application veikko application-id)
            att (first attachments)
            {:keys [approvals latestVersion]} att
            approved (get approvals (keyword (:fileId latestVersion)))]

        (fact "After the latest version was deleted the previous version is popped"
              approved =contains=> {:state "requires_user_action"
                                    :note "Bu hao!"}
              (let [{approvals :approvals} att]
                (count approvals) => 1
                (-> approvals vals first :note) => "Bu hao!"))))

    (fact "Rotating does not affect approval status"
          (let [{attachment-id :id :as attachment} (last-attachment application-id)
                _ (upload-attachment veikko application-id attachment true
                                     :filename "dev-resources/test-pdf.pdf")
                {attachment-id :id :as attachment} (last-attachment application-id)
                _  (latest-approval veikko application-id attachment) => {:state "requires_authority_action"}
                _ (reject-attachment application-id attachment)
                _ (reject-attachment-note application-id attachment "Chabuduo")
                approval (latest-approval veikko application-id attachment-id)]
            (command veikko :rotate-pdf :id application-id :attachmentId attachment-id :rotation 180) => ok?
            (let [{:keys [approvals latestVersion]} (get-attachment-by-id veikko
                                                                          application-id
                                                                          attachment-id)]
              (get approvals (-> latestVersion :originalFileId keyword))
              => approval
              (not= (:originalFileId latestVersion) (:fileId latestVersion)) => true)))))
