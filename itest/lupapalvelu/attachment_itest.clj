(ns lupapalvelu.attachment-itest
  (:require [lupapalvelu.attachment :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]))

(defn- get-attachment-by-id [application-id attachment-id]
  (let [application     (query-application pena application-id)]
    (some #(when (= (:id %) attachment-id) %) (:attachments application))))

(defn- approve-attachment [application-id attachment-id]
  (command veikko :approve-attachment :id application-id :attachmentId attachment-id) => ok?
  (get-attachment-by-id application-id attachment-id) => (in-state? "ok"))

(defn- reject-attachment [application-id attachment-id]
  (command veikko :reject-attachment :id application-id :attachmentId attachment-id) => ok?
  (get-attachment-by-id application-id attachment-id) => (in-state? "requires_user_action"))

(facts "attachments"
  (let [{application-id :id :as response} (create-app pena :municipality veikko-muni :operation "asuinrakennus")]

    response => ok?

    (comment-application application-id pena)

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
        (get-attachment-by-id application-id (first attachment-ids)) => (contains
                                                                          {:type {:type-group "paapiirustus" :type-id "asemapiirros"}
                                                                           :state "requires_user_action"
                                                                           :versions []})
        (get-attachment-by-id application-id (second attachment-ids)) => (contains
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

      (fact "Veikko upload a new version, Pena receives email pointing to comment page"
        (last-email) ; Inbox zero

        (upload-attachment veikko application-id (first (:attachments (query-application veikko application-id))) true)

        (let [emails (sent-emails)
              email  (first emails)
              pena-email  (email-for "pena")]
          (count emails) => 1
          email => (partial contains-application-link-with-tab? application-id "conversation")
          (:to email) => pena-email)))))

(fact "pdf does not work with YA-lupa"
  (let [{application-id :id :as response} (create-app pena :municipality "753" :operation "ya-katulupa-vesi-ja-viemarityot")
        application (query-application pena application-id)]
    (:organization application) => "753-YA"
    pena =not=> (allowed? :pdf-export :id application-id)
    (raw pena "pdf-export" :id application-id) => http404?))
