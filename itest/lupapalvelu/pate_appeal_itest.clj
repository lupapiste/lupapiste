(ns lupapalvelu.pate-appeal-itest
  "Appeal itest transformed into Pate world."
  (:require [lupapalvelu.factlet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [lupapalvelu.pdf.libreoffice-conversion-client :as libre]
            [lupapalvelu.pdf.pdfa-conversion :as pdfa-conversion]
            [midje.sweet :refer :all]
            [sade.core :refer [now]]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]))

(apply-remote-minimal)

(facts "Upserting appeal"
  (let [{app-id :id} (create-and-submit-application pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)
        created (now)]
    app-id => string?
    (fact "Can't add appeal before verdict"
      (command sonja :upsert-pate-appeal
               :id app-id
               :verdict-id (mongo/create-id)
               :type "appeal"
               :author "Pena"
               :datestamp 123456
               :text "foo"
               :filedatas []) => (partial expected-failure? :error.command-illegal-state))

    (let [vid (give-legacy-verdict sonja app-id)]
      vid => string?
      (fact "wrong verdict ID"
        (command sonja :upsert-pate-appeal
                 :id app-id
                 :verdict-id (mongo/create-id)
                 :type "appeal"
                 :author "Pena"
                 :datestamp 123456
                 :text "foo"
                 :filedatas []) => (partial expected-failure? :error.verdict-not-found))
      (fact "successful appeal"
        (command sonja :upsert-pate-appeal
                 :id app-id
                 :verdict-id vid
                 :type "appeal"
                 :author "Pena"
                 :datestamp created
                 :text "foo"
                 :filedatas []) => ok?)
      (fact "text is optional"
        (command sonja :upsert-pate-appeal
                 :id app-id
                 :verdict-id vid
                 :type "rectification"
                 :author "Pena"
                 :datestamp created
                 :filedatas []) => ok?)
      (fact "appeal is saved to application to be viewed"
        (map :type (:appeals (query-application pena app-id))) => (just ["appeal" "rectification"]))
      (fact "appeal query is OK"
        (let [response-data (:data (query pena :appeals :id app-id))
              verdictid-key (keyword vid)]
          (keys response-data) => (just [verdictid-key])
          (count (get response-data verdictid-key)) => 2))

      (fact* "updating appeal when appeal-id is given"
        (let [appeals-before  (appeals-for-verdict pena app-id vid)
              target-appeal   (first appeals-before)
              _ (command sonja :upsert-pate-appeal :id app-id
                         :verdict-id vid
                         :type "appeal"
                         :author "Teppo"
                         :datestamp created
                         :appeal-id (:id target-appeal)
                         :filedatas []) => ok?
              appeals-after   (appeals-for-verdict pena app-id vid)
              updated-target-appeal   (first appeals-after)]

          (fact "Count of appeals is the same"
            (count appeals-before) => (count appeals-after))

          (fact "Appellant has been changed"
            (:appellant target-appeal) => "Pena"
            (:appellant updated-target-appeal) => "Teppo")

          (fact "can't change type"
            (command sonja :upsert-pate-appeal :id app-id
                     :verdict-id vid
                     :type "rectification"
                     :author "Teppo"
                     :datestamp created
                     :appeal-id (:id target-appeal)
                     :filedatas []) => (partial expected-failure? :error.appeal-type-change-denied))))

      (fact "Upsert is validated"
        (fact "appeal-id must be found from application"
          (command sonja :upsert-pate-appeal :id app-id
                   :verdict-id vid
                   :type "rectification"
                   :author "Teppo"
                   :datestamp created
                   :appeal-id "foobar"
                   :filedatas []) => (partial expected-failure? :error.unknown-appeal))

        (let [appeals (appeals-for-verdict pena app-id vid)
              test-appeal (first appeals)]
          (fact "Appeal must be valid when upserting"
            (command sonja :upsert-pate-appeal :id app-id
                     :verdict-id vid
                     :type "trolol"
                     :author "Teppo"
                     :datestamp created
                     ;;:appeal-id (:id test-appeal)
                     :filedatas []) => (partial expected-failure? :error.invalid-appeal))))

      (fact "Delete"
        (let [appeals       (appeals-for-verdict pena app-id vid)
              first-appeal  (first appeals)
              second-appeal (second appeals)]
          (count appeals) => 2

          (command sonja :delete-pate-appeal :id app-id
                   :verdict-id vid
                   :appeal-id (:id first-appeal)) => ok?

          (fact "sane parameters"
            (command sonja :delete-pate-appeal :id app-id
                     :verdict-id vid
                     :appeal-id "foobar") => (partial expected-failure? :error.unknown-appeal)
            (command sonja :delete-pate-appeal :id app-id
                     :verdict-id "foofaa"
                     :appeal-id (:id second-appeal)) => (partial expected-failure? :error.verdict-not-found))

          (fact "one appeal left"
            (count (appeals-for-verdict pena app-id vid)) => 1))))))

(facts "Upserting appeal verdicts"
  (let [{app-id :id} (create-and-submit-application pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)
        created      (now)]
    app-id => string?
    (fact "Can't add appeal verdict before verdict"
      (command sonja :upsert-pate-appeal
               :id app-id
               :verdict-id (mongo/create-id)
               :type "appealVerdict"
               :author "Teppo"
               :datestamp 123456
               :text "foo"
               :filedatas []) => (partial expected-failure? :error.command-illegal-state))

    (let [vid (give-legacy-verdict sonja app-id)]
      vid => string?
      (fact "wrong verdict ID"
        (command sonja :upsert-pate-appeal
                 :id app-id
                 :verdict-id (mongo/create-id)
                 :type "appealVerdict"
                 :author "Teppo"
                 :datestamp 123456
                 :text "foo"
                 :filedatas []) => (partial expected-failure? :error.verdict-not-found))
      (fact "an appeal must exists before creating verdict appeal"
        (command sonja :upsert-pate-appeal
                 :id app-id
                 :verdict-id vid
                 :type "appealVerdict"
                 :author "Teppo"
                 :datestamp created
                 :text "foo"
                 :filedatas []) => (partial expected-failure? :error.appeals-not-found))
      (fact "first create appeal"
        (command sonja :upsert-pate-appeal
                 :id app-id
                 :verdict-id vid
                 :type "rectification"
                 :author "Pena"
                 :datestamp created
                 :text "rectification 1"
                 :filedatas []) => ok?)
      (fact "... then try to create invalid appeal verdict"
        (command sonja :upsert-pate-appeal
                 :id app-id
                 :verdict-id vid
                 :type "appealVerdict"
                 :author "Teppo"
                 :datestamp "18.3.2016"
                 :text "verdict for rectification 1"
                 :filedatas []) => {:ok         false
                                    :parameters ["datestamp"]
                                    :text       "error.illegal-number"})
      (fact "... then actually create a valid appeal verdict"
        (command sonja :upsert-pate-appeal
                 :id app-id
                 :verdict-id vid
                 :type "appealVerdict"
                 :author "Teppo"
                 :datestamp (+ created 1)
                 :text "verdict for rectification 1"
                 :filedatas []) => ok?)

      (fact "appeal query is OK after giving appeal and appeal verdict"
        (let [response-data (:data (query pena :appeals :id app-id))
              verdictid-key (keyword vid)]
          (keys response-data) => (just [verdictid-key])
          (count (get response-data verdictid-key)) => 2
          (:type (first (get response-data verdictid-key))) => "rectification"
          (:type (second (get response-data verdictid-key))) => "appealVerdict"))

      (fact "appeal can't be edited after appeal verdict is created"
        (let [appeals     (appeals-for-verdict pena app-id vid)
              test-appeal (first appeals)]
          (command sonja :upsert-pate-appeal
                   :id app-id
                   :verdict-id vid
                   :type "rectification"
                   :author "Pena"
                   :datestamp created
                   :text "rectification edition"
                   :appeal-id (:id test-appeal)
                   :filedatas []) => (partial expected-failure? :error.appeal-verdict-already-exists)

          (fact "Query has editable flag set correctly"
            (:editable test-appeal) => false)))

      (fact* "Upsert updates successfully"
             (let [appeals-before                (appeals-for-verdict pena app-id vid)
                   target-appeal-verdict         (second appeals-before)
                   _                             (:editable target-appeal-verdict) => true
                   _                             (command sonja :upsert-pate-appeal :id app-id
                                                          :verdict-id vid
                                                          :type "appealVerdict"
                                                          :author "Seppo"
                                                          :datestamp (+ created 1)
                                                          :appeal-id (:id target-appeal-verdict)
                                                          :filedatas [])  => ok?
                   appeals-after                 (appeals-for-verdict pena app-id vid)
                   updated-target-appeal-verdict (second appeals-after)]

          (fact "Count of appeals is the same"
            (count appeals-before) => (count appeals-after))

          (fact "Appellant has been changed"
            (:giver target-appeal-verdict) => "Teppo"
            (:giver updated-target-appeal-verdict) => "Seppo")))

      (fact "If new appeals have been made, old appeal-verdict can't be edited"
        (command sonja :upsert-pate-appeal
                 :id app-id
                 :verdict-id vid
                 :type "appeal"
                 :author "Pena again"
                 :datestamp (now)
                 :text "new appeal"
                 :filedatas []) => ok?
        (let [appeals               (appeals-for-verdict pena app-id vid)
              target-appeal-verdict (second appeals)]
          (fact "upsert"
            (command sonja :upsert-pate-appeal :id app-id
                     :verdict-id vid
                     :author "Seppo"
                     :type "appealVerdict"
                     :datestamp created
                     :appeal-id (:id target-appeal-verdict)
                     :filedatas []) => (partial expected-failure? :error.appeal-already-exists))

          (fact "delete"
            (command sonja :delete-pate-appeal :id app-id
                     :verdict-id vid
                     :appeal-id (:id target-appeal-verdict)) => (partial expected-failure? :error.appeal-already-exists))

          (fact "query has editable flag set correctly"
            (:editable target-appeal-verdict) => false)))

      (facts "Deleting"
        (let [appeals (appeals-for-verdict sonja app-id vid)]
          ; types of first second last:
          (map :type appeals) => (just ["rectification" "appealVerdict" "appeal"])
          (fact "Pena can't remove"
            (command pena :delete-pate-appeal :id app-id
                     :verdict-id vid
                     :appeal-id (-> appeals last :id)) => unauthorized?)
          (fact "First remove appeal"
            (command sonja :delete-pate-appeal :id app-id
                     :verdict-id vid
                     :appeal-id (-> appeals last :id)) => ok?)
          (fact "Can't still remove first rectification as verdict exists"
            (command sonja :delete-pate-appeal :id app-id
                     :verdict-id vid
                     :appeal-id (-> appeals first :id)) => (partial expected-failure? :error.appeal-verdict-already-exists))
          (fact "Parameters must be sane"
            (command sonja :delete-pate-appeal :id app-id
                     :verdict-id vid
                     :appeal-id "foobar") => (partial expected-failure? :error.unknown-appeal))
          (fact "Appeal verdict can now be removed with correct parameters and application state"
            (command sonja :delete-pate-appeal :id app-id
                     :verdict-id vid
                     :appeal-id (-> appeals second :id)) => ok?)

          (let [appeals-after (appeals-for-verdict sonja app-id vid)]
            (count appeals-after) => 1
            (:editable (last appeals-after)) => true))))))

(defn upsert-and-poll [& kvs]
  (fact {:midje/description (apply str "Upsert-appeal: " (ss/join " " kvs))}
    (let [{{:keys [id version]} :job} (apply (partial command
                                                      raktark-jarvenpaa
                                                      :upsert-pate-appeal)
                                             kvs)]
      (fact "Job created"
        id => truthy
        version => int?)
      (poll-job raktark-jarvenpaa :bind-attachments-job id version 10))))

(facts "appeals with attachments"
  (let [{app-id :id}          (create-and-submit-application pena :operation "pientalo" :propertyId jarvenpaa-property-id)
        {:keys [attachments]} (query-application pena app-id)
        created               (now)
        vid                   (give-legacy-verdict raktark-jarvenpaa app-id)
        expected-att-cnt      1
        resp1                 (upload-file raktark-jarvenpaa "dev-resources/test-attachment.txt")
        file-id-1             (get-in resp1 [:files 0 :fileId])
        resp2                 (upload-file raktark-jarvenpaa "dev-resources/invalid-pdfa.pdf")
        file-id-2             (get-in resp2 [:files 0 :fileId])
        pdfa-conversion?      (and (string? (env/value :pdf2pdf :license-key)) (pdfa-conversion/pdf2pdf-executable))]
    (fact "uplaod txt attachment"
      resp1 => ok?)
    (fact "upload invalid pdfa attachment"
      resp2 => ok?)
    (fact "Initially there are no attachments"
      (count attachments) => 0)

    (fact "file not linked to application"
      (raw raktark-jarvenpaa
           "download-attachment"
           :file-id file-id-1
           :id app-id) => http404?)

    (fact "successful appeal"
      (upsert-and-poll :id app-id
                       :verdict-id vid
                       :type "appeal"
                       :author "Pena"
                       :datestamp created
                       :text "foo"
                       :filedatas [{:fileId   file-id-1
                                    :type     {:type-group "muutoksenhaku"
                                               :type-id    "valitus"}
                                    :contents "Complaint"}]))

    (let [{:keys [attachments appeals]} (query-application raktark-jarvenpaa app-id)
          {aid :id}                     (first appeals)
          appeal-attachment             (util/find-first (fn [{target :target}]
                                                           (and (= "appeal" (:type target))
                                                                (= aid (:id target))))
                                                         attachments)
          converted-file-id-1           (-> appeal-attachment :latestVersion :fileId)]
      (fact "new attachment has been created"
        (count attachments) => (+ expected-att-cnt 1)
        (count (:versions appeal-attachment)) => 1          ; only one version, even if PDF/A converted

        (if (libre/enabled?)
          (fact "txt is converted to pdf/a"
            (-> appeal-attachment :latestVersion :fileId) =not=> file-id-1
            (-> appeal-attachment :latestVersion :originalFileId) => file-id-1
            (-> appeal-attachment :latestVersion :contentType) => "application/pdf"
            (-> appeal-attachment :latestVersion :archivable) => true)
          (println "Skipped appeail-itest libreoffice tests!")))

      (fact "attachment type is correct"
        (:type appeal-attachment) => {:type-group "muutoksenhaku"
                                      :type-id    "valitus"})

      (fact "attachment contents are correct"
        (:contents appeal-attachment) => "Complaint")

      (fact "file is linked to application"
        (raw raktark-jarvenpaa
             "download-attachment"
             :file-id file-id-1
             :id app-id) => http200?)

      (fact "updating appeal with new attachment"
        (upsert-and-poll :id app-id
                         :verdict-id vid
                         :type "appeal"
                         :author "Pena"
                         :datestamp created
                         :text "foo"
                         :appeal-id aid
                         :filedatas [{:fileId   file-id-2
                                      :type     {:type-group "muutoksenhaku"
                                                 :type-id    "oikaisuvaatimus"}
                                      :contents "Hello again!"}])

        (let [{:keys [attachments appeals]}               (query-application raktark-jarvenpaa app-id)
              appeal-attachments                          (filter #(= "appeal" (-> % :target :type)) attachments)
              {pdf-versions :versions :as pdf-attachment} (last appeal-attachments)]
          (count appeals) => 1
          (count attachments) => (+ expected-att-cnt 2)
          (count appeal-attachments) => 2
          (count pdf-versions) => 1
          (if pdfa-conversion?
            (facts "PDF/A converted"
              (-> pdf-attachment :latestVersion) => (contains {:version    {:major 0
                                                                            :minor 1}
                                                               :archivable true})
              (-> pdf-attachment :latestVersion :fileId) =not=> file-id-2
              (-> pdf-attachment :latestVersion :originalFileId) => file-id-2
              (last pdf-versions) => (-> pdf-attachment :latestVersion)
              (fact "Conversion has content"
                (-> pdf-attachment :latestVersion :size) => pos?))
            (facts "No PDF/A conversion"
              (count pdf-versions) => 1
              (-> pdf-versions first :fileId) => file-id-2
              (-> pdf-versions first :archivabilityError) => "not-validated"))
          (fact "New attachment type, contents and target"
            pdf-attachment => (contains {:contents "Hello again!"
                                         :target   {:id   aid
                                                    :type "appeal"}
                                         :type     {:type-group "muutoksenhaku"
                                                    :type-id    "oikaisuvaatimus"}}))))

      (fact "assignment can be created for appeal attachment"
        (let [target-attachment (util/find-first
                                 #(and (= "appeal" (-> % :target :type))
                                       (or (= (get-in % [:latestVersion :fileId]) file-id-2)
                                           (= (get-in % [:latestVersion :originalFileId]) file-id-2)))
                                 (:attachments (query-application raktark-jarvenpaa app-id)))]
          (create-assignment raktark-jarvenpaa
                             raktark-jarvenpaa-id
                             app-id
                             [{:group "attachments" :id (:id target-attachment)}]
                             "Onko aiheellinen?") => ok?))

      (fact "removing second attachment from appeal"
        (let [assignment               (first (get-user-assignments raktark-jarvenpaa))
              assignment-attachment-id (get-in assignment [:targets 0 :id])
              attachment               (-> (query-application raktark-jarvenpaa app-id) :attachments last)]
          (get-in assignment [:targets 0 :group]) => "attachments"
          (fact "assignment has correct attachment target"
            (:target (get-attachment-by-id raktark-jarvenpaa app-id assignment-attachment-id)) => {:type "appeal"
                                                                                                   :id   aid})
          (command raktark-jarvenpaa :upsert-pate-appeal
                   :id app-id
                   :verdict-id vid
                   :type "appeal"
                   :author "Pena"
                   :datestamp created
                   :text "foo"
                   :appeal-id aid
                   :filedatas []
                   :deleted-file-ids [(-> attachment :latestVersion :fileId)]) => ok?
          (let [{:keys [attachments appeals]} (query-application raktark-jarvenpaa app-id)
                appeal-attachments            (filter #(= "appeal" (-> % :target :type)) attachments)]
            (count appeals) => 1
            (count attachments) => (+ expected-att-cnt 1)
            (count appeal-attachments) => 1
            (-> appeal-attachments first :latestVersion :originalFileId) => file-id-1
            (-> appeal-attachments first :latestVersion :fileId) => converted-file-id-1
            (count (-> appeal-attachments first :versions)) => 1)

          (fact "assignment is deleted with appeal attachment"
            (map :id (get-user-assignments raktark-jarvenpaa)) =not=> (contains (:id assignment)))

          (fact "file doesn't exist"
            (raw raktark-jarvenpaa
                 "download-attachment"
                 :file-id file-id-2
                 :id app-id) => http404?))))))

(facts "Files vs. attachments"
  (let [{app-id :id}          (create-and-submit-application pena :operation "pientalo" :propertyId jarvenpaa-property-id)
        {:keys [attachments]} (query-application pena app-id)
        created               (now)
        vid                   (give-legacy-verdict raktark-jarvenpaa app-id)
        expected-att-cnt      1
        resp1                 (upload-file raktark-jarvenpaa "dev-resources/test-attachment.txt")
        file-id-1             (get-in resp1 [:files 0 :fileId])
        resp2                 (upload-file raktark-jarvenpaa "dev-resources/invalid-pdfa.pdf")
        file-id-2             (get-in resp2 [:files 0 :fileId])
        resp3                 (upload-file raktark-jarvenpaa "dev-resources/cake.png")
        file-id-3             (get-in resp3 [:files 0 :fileId])]
    (fact "New appeal with file"
      (upsert-and-poll :id app-id :verdict-id vid :type "appeal" :author "Author" :datestamp 12345
                       :text "Hello world!" :filedatas [{:fileId   file-id-1
                                                         :type     {:type-group "muut"
                                                                    :type-id    "muu"}
                                                         :contents "Howdy"}]))
    (fact "Appeal and its attachment has been created"
      (let [{:keys [attachments appeals]} (query-application pena app-id)
            {appeal-id :id :as appeal}    (last appeals)
            att-id                        (-> attachments last :id)
            att-file-id                   (-> attachments last :latestVersion :fileId)]
        (count appeals) => 1
        (count attachments) => 2 ;; Verdict and appeal attachment
        (fact "Attachment"
          (last attachments) => (contains {:type          {:type-group "muut"
                                                           :type-id    "muu"}
                                           :contents      "Howdy"
                                           :target        {:id   (:id appeal)
                                                           :type "appeal"}
                                           :latestVersion (contains {:originalFileId file-id-1})}))
        (fact "Appeal"
          appeal => {:id             appeal-id
                     :type           "appeal"
                     :appellant      "Author"
                     :datestamp      12345
                     :target-verdict vid
                     :text           "Hello world!"})

        (fact "Non-existing files cannot be deleted"
          (command raktark-jarvenpaa :upsert-pate-appeal
                   :id app-id :verdict-id vid :type "appeal" :author "  Author  " :datestamp 12345
                   :text "  Hello world!  " :filedatas [] :deleted-file-ids ["bad"])
          => (partial expected-failure? :error.file-cannot-be-deleted))

        (fact "Files can be added and removed at the same time"
          (upsert-and-poll :id app-id :verdict-id vid :type "appeal" :author "Somebody Else"
                           :datestamp 56789 :text "Nimen hao!" :appeal-id appeal-id
                           :deleted-file-ids [att-file-id]
                           :filedatas [{:fileId   file-id-2
                                        :type     {:type-group "osapuolet"
                                                   :type-id    "valtakirja"}
                                        :contents "Letter"}])
          (let [{:keys [attachments appeals]} (query-application pena app-id)]
            (util/find-by-id att-id attachments) => nil
            (count attachments) => 2
            (fact "New attachment"
              (last attachments) => (contains {:type          {:type-group "osapuolet"
                                                               :type-id    "valtakirja"}
                                               :contents      "Letter"
                                               :target        {:id   (:id appeal)
                                                               :type "appeal"}
                                               :latestVersion (contains {:originalFileId file-id-2})}))
            (fact "Updated appeal"
              (count appeals) => 1
              (first appeals) => {:id             appeal-id
                                  :type           "appeal"
                                  :appellant      "Somebody Else"
                                  :datestamp      56789
                                  :target-verdict vid
                                  :text           "Nimen hao!"})
            (fact "New appeal verdict with file"
              (upsert-and-poll :id app-id :verdict-id vid :type "appealVerdict" :author "Verdict Author" :datestamp 12345
                               :text "Have a cake!" :filedatas [{:fileId   file-id-3
                                                                 :type     {:type-group "muut"
                                                                            :type-id    "valokuva"}
                                                                 :contents "VA"}]))))))
    (facts "Deleting legacy verdict wipes out appeals as well"
      (fact "Status before deletion"
        (let [{:keys [pate-verdicts appeals appealVerdicts
                      attachments state]} (query-application raktark-jarvenpaa app-id)]
          (count pate-verdicts) => 1
          (count appeals) => 1
          (count appealVerdicts) => 1
          (count attachments) => 3
          state => "verdictGiven"))
      (fact "Delete verdict"
        (command raktark-jarvenpaa :delete-legacy-verdict
                 :id app-id
                 :verdict-id vid) => ok?)
      (fact "Status after deletion"
        (let [{:keys [pate-verdicts appeals appealVerdicts
                      attachments state]} (query-application raktark-jarvenpaa app-id)]
          (count pate-verdicts) => 0
          (count appeals) => 0
          (count appealVerdicts) => 0
          (count attachments) => 0
          state => "submitted")))))
