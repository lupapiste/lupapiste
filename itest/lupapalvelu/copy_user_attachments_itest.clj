(ns lupapalvelu.copy-user-attachments-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]))

(apply-remote-minimal)

(facts "Create R application"
  (let [application-id (create-app-id pena :propertyId tampere-property-id :operation "kerrostalo-rivitalo")
        {:keys [attachments]} (query-application pena application-id)
        cv-type {:type-group "osapuolet"
                 :type-id "cv"}
        tutkintotodistus-type {:type-group "osapuolet"
                               :type-id "tutkintotodistus"}]
    (count attachments) => 4

    (fact "Pena is not architect"
      (command pena :copy-user-attachments-to-application :id application-id) => unauthorized?)
    (command pena :update-user :firstName "Pena" :lastName "Panaani" :architect true) => ok?

    (fact "Pena is has no attachments"
      (command pena :copy-user-attachments-to-application :id application-id) => (partial expected-failure? :error.no-user-attachments))

    (upload-user-attachment pena "osapuolet.cv" true)

    (fact "Pena can copy-user-attachments to application"
      (command pena :copy-user-attachments-to-application :id application-id) => ok?)

    (let [{:keys [attachments]} (query-application pena application-id)
          user-att (last attachments)]
      (fact "attachment is added"
        (count attachments) => 5)
      (fact "attachmnet type"
        (:type user-att) => cv-type)
      (fact "attachment grouping"
        (:groupType user-att) => "parties"))

    (command pena :submit-application :id application-id) => ok?

    (let [resp (command veikko
                        :create-attachments
                        :id application-id
                        :attachmentTypes [tutkintotodistus-type]
                        :group nil)
          tutkintotodistus-id (first (:attachmentIds resp))]
      (count (:attachmentIds resp)) => 1
      (fact "Authority asks for tutkintotodistus" resp => ok?)
      (fact "Pena uploads tutkintodistus to his profile" (upload-user-attachment pena "osapuolet.tutkintotodistus" true))
      (fact "Pena copies his attachments again to application"
        (command pena :copy-user-attachments-to-application :id application-id) => ok?)

      (let [{:keys [attachments]} (query-application pena application-id)
            tutkintotodistus-attachment (first (filter #(= (:type %) tutkintotodistus-type) attachments))]
        (fact "No new attachments are present" (count attachments) => 6) ; 6th is tutkintotodistus
        (fact "Tutkintotodistus placeholder has Pena's version uploaded"
          (:id tutkintotodistus-attachment) => tutkintotodistus-id
          (get-in tutkintotodistus-attachment [:latestVersion :user]) => (contains {:username "pena"})
          (fact "one version" (count (:versions tutkintotodistus-attachment)) => 1)))

      (fact "Copying again creates new tutkintotodistus, as there are no empty placeholders left"
        (command pena :copy-user-attachments-to-application :id application-id) => ok?
        (let [{:keys [attachments]} (query-application pena application-id)
              tutkintotodistus-attachments (filter #(= (:type %) tutkintotodistus-type) attachments)]
          (count attachments) => 7
          (count tutkintotodistus-attachments) => 2
          (fact "one version" (count (:versions (last tutkintotodistus-attachments))) => 1)))

      (fact "Copying once more does not result in new attachment, as previous upload resulted in known user-attachment-id"
        ; format for plain user attachment id is: (str application-id "." user-id "." attachment-id), it's existence is checked when copying user attachments to application
        (command pena :copy-user-attachments-to-application :id application-id) => ok?
        (let [{:keys [attachments]} (query-application pena application-id)
              tutkintotodistus-attachments (filter #(= (:type %) tutkintotodistus-type) attachments)]
          (count attachments) => 7
          (count tutkintotodistus-attachments) => 2
          (fact "still one version" ; previously guard query in copy-user-attachments-to-application did not work correctly
            (count (:versions (last tutkintotodistus-attachments))) => 1)))

      (fact "Not needed attachments are filled as 'needed'"
        (let [{:keys [attachments]} (query-application pena application-id)
              cv-attachment (first (filter #(= (:type %) cv-type) attachments))]
          (count attachments) => 7
          (doseq [v (:versions cv-attachment)]
            (command pena
                     :delete-attachment-version
                     :id application-id
                     :attachmentId (:id cv-attachment)
                     :fileId (:fileId v)
                     :originalFileId (:fileId v)) => ok?)
          (count (filter #(= (:type %) cv-type)
                         (:attachments (query-application pena application-id)))) => 1
          (get (first (filter #(= (:type %) cv-type)
                              (:attachments (query-application pena application-id))))
               :versions) => empty?

          (command pena :set-attachment-not-needed :id application-id :attachmentId (:id cv-attachment) :notNeeded true) => ok?
          (command pena :copy-user-attachments-to-application :id application-id) => ok?
          (let [{:keys [attachments]} (query-application pena application-id)
                cv-attachments (filter #(= (:type %) cv-type) attachments)]
            (fact "new attachment is added"
              (count attachments) => 7
              (count cv-attachments) => 1)
            (fact "CV is needed now"
              (:notNeeded (first cv-attachments)) => false)))))
    (fact "Fetch verdict"
      (command veikko :check-for-verdict :id application-id) => ok?)
    (fact "Copying own attachments is still possible"
      (command pena :copy-user-attachments-to-application :id application-id) => ok?)
    (facts "Foreman application"
      (let [for-id (create-foreman-application application-id pena mikko-id "Vastaava ty\u00f6njohtaja" "A")]
        (fact "Copy own attachments"
          (command pena :copy-user-attachments-to-application :id for-id) => ok?)
        (fact "Set subtype and submit application"
          (command pena :change-permit-sub-type :id for-id :permitSubtype "tyonjohtaja-hakemus") => ok?
          (command pena :submit-application :id for-id) => ok?)
        (fact "Fetch verdict"
          (command veikko :check-for-verdict :id for-id) => ok?)
        (fact "Copying own attachments no longer possible"
          (command pena :copy-user-attachments-to-application :id for-id)
          => (partial expected-failure? :error.illegal-state))))))

(facts "YA application"
  (let [application-id (create-app-id mikko :propertyId sipoo-property-id :operation "ya-katulupa-vesi-ja-viemarityot")
        {:keys [attachments]} (query-application mikko application-id)
        cv-type {:type-group "osapuolet"
                 :type-id "cv"}
        ;; TODO: Use or remove:
        tutkintotodistus-type {:type-group "osapuolet"
                               :type-id "tutkintotodistus"}]
    (count attachments) => 1

    (fact "No user attachments"
      (command mikko :copy-user-attachments-to-application :id application-id) => (partial expected-failure? :error.no-user-attachments))

    (fact "Upload user attachment"
      (upload-user-attachment mikko "osapuolet.cv" true))

    (fact "Copy user attachments to application"
      (command mikko :copy-user-attachments-to-application :id application-id) => ok?)

    (let [{:keys [attachments]} (query-application mikko application-id)
          user-att (last attachments)]
      (fact "one attachment is added"
        (count attachments) => 2)
      (fact "attachment type"
        (:type user-att) => cv-type)
      (fact "attachment grouping not set for YA applications"
        (:groupType user-att) => nil))))
