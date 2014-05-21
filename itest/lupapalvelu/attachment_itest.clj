(ns lupapalvelu.attachment-itest
  (:require [lupapalvelu.attachment :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]))

(defn- get-attachment-by-id [apikey application-id attachment-id]
  (get-attachment-info (query-application apikey application-id) attachment-id))

(defn- approve-attachment [application-id attachment-id]
  (command veikko :approve-attachment :id application-id :attachmentId attachment-id) => ok?
  (get-attachment-by-id veikko application-id attachment-id) => (in-state? "ok"))

(defn- reject-attachment [application-id attachment-id]
  (command veikko :reject-attachment :id application-id :attachmentId attachment-id) => ok?
  (get-attachment-by-id veikko application-id attachment-id) => (in-state? "requires_user_action"))

(facts "attachments"
  (let [{application-id :id :as response} (create-app pena :municipality veikko-muni :operation "asuinrakennus")]

    response => ok?

    (comment-application application-id pena true)

    (let [resp (command veikko
                 :create-attachments
                 :id application-id
                 :attachmentTypes [{:type-group "paapiirustus" :type-id "asemapiirros"}
                                   {:type-group "paapiirustus" :type-id "pohjapiirros"}])
          attachment-ids (:attachmentIds resp)]

      (fact "Veikko can create an attachment"
        (success resp) => true)

      (fact "Two attachments were created in one call"
        (fact (count attachment-ids) => 2))

      (fact "attachment has been saved to application"
        (get-attachment-by-id veikko application-id (first attachment-ids)) => (contains
                                                                                 {:type {:type-group "paapiirustus" :type-id "asemapiirros"}
                                                                                  :state "requires_user_action"
                                                                                  :versions []})
        (get-attachment-by-id veikko application-id (second attachment-ids)) => (contains
                                                                                  {:type {:type-group "paapiirustus" :type-id "pohjapiirros"}
                                                                                   :state "requires_user_action"
                                                                                   :versions []}))

      (fact "uploading files"
        (let [application (query-application pena application-id)
              _           (upload-attachment-to-all-placeholders pena application)
              application (query-application pena application-id)]

          (fact "download all"
            (let [resp (raw pena "download-all-attachments" :id application-id)]
              resp => http200?
              (get-in resp [:headers "content-disposition"]) => "attachment;filename=\"liitteet.zip\"")
              (fact "p\u00e5 svenska"
                (get-in (raw pena "download-all-attachments" :id application-id :lang "sv") [:headers "content-disposition"])
                => "attachment;filename=\"bilagor.zip\""))

          (fact "pdf export"
            (raw pena "pdf-export" :id application-id) => http200?)

          (doseq [attachment-id (get-attachment-ids application)
                  :let [file-id (attachment-latest-file-id application attachment-id)]]

            (fact "view-attachment anonymously should not be possible"
              (raw nil "view-attachment" :attachment-id file-id) => http401?)

            (fact "view-attachment as pena should be possible"
              (raw pena "view-attachment" :attachment-id file-id) => http200?)

            (fact "download-attachment anonymously should not be possible"
              (raw nil "download-attachment" :attachment-id file-id) => http401?)

            (fact "download-attachment as pena should be possible"
              (raw pena  "download-attachment" :attachment-id file-id) => http200?))))

      (fact "Veikko can approve attachment"
        (approve-attachment application-id (first attachment-ids)))

      (fact "Veikko can reject attachment"
        (reject-attachment application-id (first attachment-ids)))

      (fact "Pena submits the application"
        (command pena :submit-application :id application-id) => ok?
        (:state (query-application veikko application-id)) => "submitted")

      (fact "Veikko can still approve attachment"
        (approve-attachment application-id (first attachment-ids)))

      (fact "Veikko can still reject attachment"
        (reject-attachment application-id (first attachment-ids)))

      (fact "Pena signs attachments"
        (fact "meta" attachment-ids => seq)

        (fact "Signing fails if password is incorrect"
          (command pena :sign-attachments :id application-id :attachmentIds attachment-ids :password "not-pena") => (partial expected-failure? "error.password"))

        (fact "Signing succeeds if password is correct"
          (command pena :sign-attachments :id application-id :attachmentIds attachment-ids :password "pena") => ok?)

        (fact "Signature is set"
          (let [application (query-application pena application-id)
                attachments (get-attachments-infos application attachment-ids)]
            (doseq [{signatures :signatures latest :latestVersion} attachments]
              (count signatures) => 1
              (let [{:keys [user created version]} (first signatures)]
                (:username user) => "pena"
                (:id user) => pena-id
                (:firstName user) => "Pena"
                (:lastName user) => "Panaani"
                created => pos?
                version => (:version latest))))))

      (let [versioned-attachment (first (:attachments (query-application veikko application-id)))]
        (last-email) ; Inbox zero
        (fact "Meta"
          (get-in versioned-attachment [:latestVersion :version :major]) => 1
          (get-in versioned-attachment [:latestVersion :version :minor]) => 0)

        (fact "Veikko upload a new version"
         (upload-attachment veikko application-id versioned-attachment true)
         (let [updated-attachment (get-attachment-by-id veikko application-id (:id versioned-attachment))]
           (get-in updated-attachment [:latestVersion :version :major]) => 1
           (get-in updated-attachment [:latestVersion :version :minor]) => 1

           (fact "Pena receives email pointing to comment page"
             (let [emails (sent-emails)
                   email  (first emails)
                   pena-email  (email-for "pena")]
               (count emails) => 1
               email => (partial contains-application-link-with-tab? application-id "conversation")
               (:to email) => pena-email))

           (fact "Delete version"
             (command veikko :delete-attachment-version :id application-id
               :attachmentId (:id versioned-attachment) :fileId (get-in updated-attachment [:latestVersion :fileId])) => ok?
             (let [ver-del-attachment (get-attachment-by-id veikko application-id (:id versioned-attachment))]
               (get-in ver-del-attachment [:latestVersion :version :major]) => 1
               (get-in ver-del-attachment [:latestVersion :version :minor]) => 0))

           (fact "Delete attachment"
             (command veikko :delete-attachment :id application-id
               :attachmentId (:id versioned-attachment)) => ok?
             (get-attachment-by-id veikko application-id (:id versioned-attachment)) => nil?)))))))

(fact "pdf does not work with YA-lupa"
  (let [{application-id :id :as response} (create-app pena :municipality "753" :operation "ya-katulupa-vesi-ja-viemarityot")
        application (query-application pena application-id)]
    (:organization application) => "753-YA"
    pena =not=> (allowed? :pdf-export :id application-id)
    (raw pena "pdf-export" :id application-id) => http404?))

(defn- poll-job [id version limit]
  (when (pos? limit)
    (let [resp (query sonja :stamp-attachments-job :job-id id :version version)]
      (when-not (= (get-in resp [:job :status]) "done")
        (Thread/sleep 200)
        (poll-job id (get-in resp [:job :version]) (dec limit))))))

(facts "Stamping"
  (let [application (create-and-submit-application sonja :municipality sonja-muni)
        application-id (:id application)
        attachment (first (:attachments application))
        _ (upload-attachment sonja application-id attachment true :filename "dev-resources/VRK_Virhetarkistukset.pdf")
        {job :job :as resp} (command sonja :stamp-attachments :id application-id :files [(:id attachment)] :xMargin 0 :yMargin 0)]

    resp => ok?
    (fact "Job id is returned" (:id job) => truthy)

    ; Poll for 5 seconds
    (when-not (= "done" (:status job)) (poll-job (:id job) (:version job) 25))

    (fact "Attachment has stamp"
      (let [attachment (get-attachment-by-id sonja application-id (:id attachment))]
        (get-in attachment [:latestVersion :stamped]) => true))))
