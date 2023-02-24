(ns lupapalvelu.attachment.ram-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.attachment.util :refer [attachment-state]]))

(apply-remote-minimal)

(facts "RAM attachments"
  (let [application (create-and-submit-application pena :propertyId sipoo-property-id)
        application-id (:id application)
        {attachments :attachments} (query-application pena application-id)
        base-attachment (first attachments)]

    (defn latest-attachment []
      (->> (query-application pena application-id) :attachments (sort-by :modified) last))

    (defn first-attachment []
      (-> (query-application pena application-id) :attachments first))

    (sent-emails) ;; Clear inbox

    (fact "Upload file to attachment"
      (upload-file-and-bind pena application-id {:type (:type base-attachment)} :attachment-id (:id base-attachment)) => truthy)

    (fact "No email sent"
      (sent-emails) => [])

    (fact "Ronja is set as general handler"
      (command sonja :upsert-application-handler :id application-id :userId ronja-id :roleId sipoo-general-handler-id) => ok?)

    (fact "Cannot create ram attachment before verdict is given"
      (command pena :create-ram-attachment :id application-id :attachmentId (:id base-attachment))
      => (partial expected-failure? :error.command-illegal-state))

    (fact "Give verdict"
      (command sonja :check-for-verdict :id application-id)
      => ok?)

    (fact "Attachment id should match attachment in the application"
      (command pena :create-ram-attachment :id application-id :attachmentId "invalid_id")
      => (partial expected-failure? :error.attachment.id))

    (fact "RAM link cannot be created on unapproved attachment"
      (command pena :create-ram-attachment :id application-id :attachmentId (:id base-attachment))
      => (partial expected-failure? :error.attachment-not-approved))

    (sent-emails);; Clear inbox

    (fact "RAM link is created after approval"
      (command sonja :approve-attachment :id application-id
               :fileId (-> (first-attachment) :latestVersion :fileId)) => ok?
      (command pena :create-ram-attachment :id application-id :attachmentId (:id base-attachment))
      => ok?)

    (fact "No email sent"
      (sent-emails) => [])

    (fact "RAM link cannot be created twice on same attachment"
      (command pena :create-ram-attachment :id application-id :attachmentId (:id base-attachment))
      => (partial expected-failure? :error.ram-linked))

    (facts "Pena uploads new post-verdict attachment and corresponding RAM attachment"
      (upload-file-and-bind pena application-id {:type {:type-group "paapiirustus" :type-id "julkisivupiirustus"}}) => truthy
      (let [base (latest-attachment)]
        (fact "testaus" (:type base) => {:type-group "paapiirustus" :type-id "julkisivupiirustus"})
        (fact "RAM creation fails do to unapproved base attachment"
          (command pena :create-ram-attachment :id application-id :attachmentId (:id base))
          => (partial expected-failure? :error.attachment-not-approved) )

        (fact "Approve and create RAM"
          (command sonja :approve-attachment :id application-id
                   :fileId (-> (latest-attachment) :latestVersion :fileId)) => ok?
          (command pena :create-ram-attachment :id application-id :attachmentId (:id base)) => ok?)
        ;; Clear inbox
        (sent-emails)
        (upload-file-and-bind pena application-id
                              {:type {:type-group "paapiirustus" :type-id "julkisivupiirustus"}}
                              :attachment-id (:id (latest-attachment))) => truthy
        (Thread/sleep 100) ; wait for email delivery
        (fact "Email notification about new RAM is sent"
          (let [email (last-email)]
            (:to email) => (contains (email-for-key ronja))
            (:subject email) => "Lupapiste: foo 42, bar, Sipoo - Ilmoitus uudesta RAM:sta"))
        (fact "Applicant cannot delete base attachment"
          (command pena :delete-attachment :id application-id :attachmentId (:id base))
          => fail?)
        (fact "Applicant cannot delete base attachment version"
          (command pena :delete-attachment-version :id application-id :attachmentId (:id base)
                   :fileId (-> base :latestVersion :fileId) :originalFileId (-> base :latestVersion :originalFileId))
          => (partial expected-failure? :error.ram-linked))))
    (let [ram-id (:id (latest-attachment))]
      (facts "Latest attachment has RAM link"
        (let [{[a b] :ram-links} (query pena :ram-linked-attachments :id application-id :attachmentId ram-id)]
          (fact "Base attachment has no link" (:ram-link a) => nil)
          (fact "RAM attachment links to base attachment" (:ramLink b) => (:id a))))
      (fact "Authority can query RAM links"
        (query sonja :ram-linked-attachments :id application-id :attachmentId ram-id) => ok?)
      (fact "Reader authority can query RAM links"
        (query luukas :ram-linked-attachments :id application-id :attachmentId ram-id) => ok?)
      (fact "Sonja approves RAM attachment"
        (command sonja :approve-attachment :id application-id :fileId (-> (latest-attachment) :latestVersion :fileId)) => ok?)
      (let [{{:keys [fileId originalFileId]} :latestVersion :as ram} (latest-attachment)]
        (fact "RAM is approved" (attachment-state ram) => :ok)
        (fact "Sonja cannot delete approved RAM"
          (command sonja :delete-attachment :id application-id :attachmentId ram-id) => (partial expected-failure? :error.ram-approved))
        (fact "Sonja cannot delete approved RAM version"
          (command sonja :delete-attachment-version :id application-id :attachmentId ram-id
                   :fileId fileId :originalFileId originalFileId) => (partial expected-failure? :error.ram-approved))
        (fact "Pena cannot delete approved RAM"
          (command pena :delete-attachment :id application-id :attachmentId ram-id) => fail?)
        (fact "Pena cannot delete approved RAM version"
          (command pena :delete-attachment-version :id application-id :attachmentId ram-id
                   :fileId fileId :originalFileId originalFileId) => (partial expected-failure? :error.ram-approved))
        (fact "Sonja rejects RAM attachment"
          (command sonja :reject-attachment :id application-id :fileId (-> (latest-attachment) :latestVersion :fileId)) => ok?)
        (fact "Pena cannot delete rejected RAM version"
          (command pena :delete-attachment-version :id application-id :attachmentId ram-id
                   :fileId fileId :originalFileId originalFileId) => (partial expected-failure? :error.unauthorized))
        (fact "Pena cannot delete rejected RAM"
          (command pena :delete-attachment :id application-id :attachmentId ram-id) => fail?)
        (fact "Sonja can delete RAM"
          (command sonja :delete-attachment :id application-id :attachmentId ram-id) => ok?)
        (let [base (latest-attachment)]
          (fact "Pena again creates RAM attachment"
            (command pena :create-ram-attachment :id application-id :attachmentId (:id base)) => ok?)
          (fact "Sonja cannot delete base attachment"
            (command sonja :delete-attachment :id application-id :attachmentId (:id base)) => (partial expected-failure? :error.ram-linked))
          (fact "Fill and approve RAM and create one more link"
            (sent-emails)  ;; Clear inbox
            (let [ram (latest-attachment)]
              (upload-version-and-bind pena application-id (:id ram))) => truthy
            (fact "Email notification about new RAM is sent"
              (let [email (last-email)]
                (:to email) => (contains (email-for-key ronja))
                (:subject email) => "Lupapiste: foo 42, bar, Sipoo - Ilmoitus uudesta RAM:sta"))
            (let [middle (latest-attachment)]
              (command sonja :approve-attachment :id application-id
                       :fileId (-> middle :latestVersion :fileId)) => ok?
              (command pena :create-ram-attachment :id application-id :attachmentId (:id middle)) => ok?
              (fact "Sonja can reject the middle RAM"
                (command sonja :reject-attachment :id application-id
                         :fileId (-> middle :latestVersion :fileId)) => ok?)
              (fact "... but cannot delete it"
                (command sonja :delete-attachment :id application-id :attachmentId (:id middle))
                => (partial expected-failure? :error.ram-linked))
              (fact "... or its version"
                (command sonja :delete-attachment-version :id application-id
                         :attachmentId (:id middle)
                         :fileId (-> middle :latestVersion :fileId)
                         :originalFileId (-> middle :latestVersion :originalFileId))
                => (partial expected-failure? :error.ram-linked)))))))
    (facts "Pena uploads new post-verdict attachment that does not support RAMs"
      (upload-file-and-bind pena application-id {:type {:type-group "osapuolet" :type-id "cv"}}) => truthy
      (let [base (latest-attachment)]
        (fact "Approve"
          (command sonja :approve-attachment :id application-id
                   :fileId (-> (latest-attachment) :latestVersion :fileId)) => ok?)
        (fact "RAM creation fails"
          (command pena :create-ram-attachment :id application-id :attachmentId (:id base))
          => (partial expected-failure? :error.ram-not-allowed))))
    (fact "Admin disables RAM for the organization"
      (command sipoo :toggle-organization-ram :organizationId "753-R" :disabled true) => ok?
      (command sipoo :set-organization-ram-message
               :organizationId "753-R"
               :message {:fi "Morjens"
                         :sv "   "
                         :en "Hello"}) => ok?)
    (fact "No disabled message for attachment that does not support RAM"
      (query sonja :ram-disabled-message :id application-id
             :attachmentId (:id (latest-attachment)))
      => (partial expected-failure? :error.ram-not-allowed))
    (facts "Pena uploads attachment that supports RAM"
      (upload-file-and-bind pena application-id {:type {:type-group "paapiirustus"
                                                        :type-id "julkisivupiirustus"}})
      => truthy
      (let [base (latest-attachment)]
        (fact "No disabled message, because the attachment is not approved"
          (query pena :ram-disabled-message :id application-id
                 :attachmentId (:id base))
          => (partial expected-failure? :error.attachment-not-approved))
        (fact "Approve"
          (command sonja :approve-attachment :id application-id
                   :fileId (-> base :latestVersion :fileId)) => ok?)
        (fact "RAM creation fails because organization has disabled RAMs"
          (command pena :create-ram-attachment :id application-id :attachmentId (:id base))
          => (partial expected-failure? :error.ram-disabled-in-organization))
        (fact "RAM disabled message is shown: using the user default language."
          (query pena :ram-disabled-message :id application-id
                 :attachmentId (:id base))
          => {:ok true :message "Morjens"})
        (fact "Blank disabled message is nil. Using Swedish."
          (query pena :ram-disabled-message :id application-id
                 :attachmentId (:id base)
                 :lang "sv")
          => {:ok true :message nil})
        (fact "English message"
          (query pena :ram-disabled-message :id application-id
                 :attachmentId (:id base)
                 :lang "en")
          => {:ok true :message "Hello"})
        (fact "Enable RAMs in the organization"
          (command sipoo :toggle-organization-ram
                   :organizationId "753-R"
                   :disabled false) => ok?)
        (fact "No more disabled RAM message"
          (query pena :ram-disabled-message :id application-id
                 :attachmentId (:id base)
                 :lang "en")
          => (partial expected-failure? :error.ram-enabled-in-organization))
        (fact "RAM creation succeeds"
          (command pena :create-ram-attachment :id application-id
                   :attachmentId (:id base))
          => ok?)))))
