(ns lupapalvelu.pate-appeal-itest
  "Appeal itest transformed into Pate world."
  (:require [lupapalvelu.attachment.conversion :as conversion]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.export-itest-util :as export-itest-util]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-itest-util :refer :all]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [lupapalvelu.vetuma-itest-util :as vetuma-util]
            [midje.sweet :refer :all]
            [sade.core :refer [now]]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]))

(set! *warn-on-reflection* true)

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

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components
                      test-system/with-mock-laundry)
  (facts "Upserting appeal"
    (let [{app-id :id} (create-and-submit-application pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)
          created      (now)]
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

      (let [vid (give-legacy-verdict sonja app-id :R true)]
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
          (let [appeals-before        (appeals-for-verdict pena app-id vid)
                target-appeal         (first appeals-before)
                _                     (command sonja :upsert-pate-appeal :id app-id
                                               :verdict-id vid
                                               :type "appeal"
                                               :author "Teppo"
                                               :datestamp created
                                               :appeal-id (:id target-appeal)
                                               :filedatas [])
                =>                    ok?
                appeals-after         (appeals-for-verdict pena app-id vid)
                updated-target-appeal (first appeals-after)]

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

          (let [appeals     (appeals-for-verdict pena app-id vid)
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

      (let [vid (give-legacy-verdict sonja app-id :R true)]
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
                                                       :filedatas [])
                =>                            ok?
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

  (facts "appeals with attachments"
    (let [{app-id :id}          (create-and-submit-application pena :operation "pientalo" :propertyId jarvenpaa-property-id)
          {:keys [attachments]} (query-application pena app-id)
          created               (now)
          vid                   (give-legacy-verdict raktark-jarvenpaa app-id :R true)
          expected-att-cnt      2
          resp1                 (upload-file raktark-jarvenpaa "dev-resources/test-attachment.txt")
          file-id-1             (get-in resp1 [:files 0 :fileId])
          resp2                 (upload-file raktark-jarvenpaa "dev-resources/invalid-pdfa.pdf")
          file-id-2             (get-in resp2 [:files 0 :fileId])]
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
          (count (:versions appeal-attachment)) => 1 ; only one version, even if PDF/A converted

          (if (conversion/enabled?)
            (fact "txt is converted to pdf/a"
              (-> appeal-attachment :latestVersion :fileId) =not=> file-id-1
              (-> appeal-attachment :latestVersion :originalFileId) => file-id-1
              (-> appeal-attachment :latestVersion :contentType) => "application/pdf"
              (-> appeal-attachment :latestVersion :archivable) => true)
            (println "Skipped pate-appeal-itest laundry tests!")))

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
            (if (conversion/enabled?)
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
          vid                   (give-legacy-verdict raktark-jarvenpaa app-id :R true)
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
          (count attachments) => 3 ;; Verdict, appeal attachment and app-at-time-of-verdict
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
              (count attachments) => 3
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
            (count attachments) => 4
            state => "verdictGiven"))
        (fact "Delete verdict"
          (command raktark-jarvenpaa :delete-pate-verdict
                   :id app-id
                   :verdict-id vid) => ok?)
        (fact "Status after deletion"
          (let [{:keys [pate-verdicts appeals appealVerdicts
                        attachments state]} (query-application raktark-jarvenpaa app-id)]
            (count pate-verdicts) => 0
            (count appeals) => 0
            (count appealVerdicts) => 0
            (count attachments) => 1    ;; App at time of verdict does not get deleted
            state => "submitted")))))

  (facts "Add appeal for published bulletin"
    (let [store        (atom {})
          cookie-store (doto (->cookie-store store)
                         (.addCookie test-db-cookie))
          app          (create-and-submit-application mikko :operation "kerrostalo-rivitalo"
                                                      :propertyId sipoo-property-id
                                                      :x 406898.625 :y 6684125.375
                                                      :address "Hitantine 108")
          _            (command sonja :check-for-verdict :id (:id app))
          _            (vetuma-util/authenticate-to-vetuma! cookie-store)
          bulletin-id  (-> (datatables mikko :application-bulletins :municipality sonja-muni
                                       :searchText "" :state "" :page 1)
                           :data first :id)]
      (fact "Can appeal"
        (-> (query pena :public-bulletin :bulletinId bulletin-id)
            :bulletin :can-appeal?) => true
        (-> (query pena :vetuma-bulletin :bulletinId bulletin-id
                   :cookie-store cookie-store)
            :bulletin :can-appeal?) => true)
      (fact "add appeal"
        (command sonja :add-bulletin-appeal :bulletin-id bulletin-id :text "i be appealing"
                 :cookie-store cookie-store) => ok?)

      (let [{:keys [appeals state]} (query-application sonja (:id app))
            appeal                  (first appeals)]
        (fact "Application is appealed"
          state => "appealed")
        (fact "appeal text"
          (:text appeal) => "i be appealing"))

      (fact "Disable bulletin appeal"
        (command sipoo :update-organization-bulletin-scope :organizationId "753-R"
                 :permitType "R"
                 :municipality "753"
                 :appeal false) => ok?)

      (fact "Cannot appeal"
        (-> (query pena :public-bulletin :bulletinId bulletin-id)
            :bulletin :can-appeal?) => nil
        (-> (query pena :vetuma-bulletin :bulletinId bulletin-id
                   :cookie-store cookie-store)
            :bulletin :can-appeal?) => nil

        (command sonja :add-bulletin-appeal
                 :bulletin-id bulletin-id
                 :text "I scream ice cream."
                 :cookie-store cookie-store)
        => (partial expected-failure? :error.appeal-not-enabled))

      (fact "Enable bulletin appeal"
        (command sipoo :update-organization-bulletin-scope :organizationId "753-R"
                 :permitType "R"
                 :municipality "753"
                 :appeal true) => ok?)

      (fact "Can appeal again"
        (-> (query pena :public-bulletin :bulletinId bulletin-id)
            :bulletin :can-appeal?) => true
        (-> (query pena :vetuma-bulletin :bulletinId bulletin-id
                   :cookie-store cookie-store)
            :bulletin :can-appeal?) => true

        (command sonja :add-bulletin-appeal
                 :bulletin-id bulletin-id
                 :text "I scream ice cream."
                 :cookie-store cookie-store)
        => ok?
        (-> (query-application sonja (:id app))
            :appeals last :text)
        => "I scream ice cream.")))

  (facts "Rectification verdicts"
    (let [{app-id :id} (create-and-submit-application pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)
          created      (now)]
      app-id => string?
      (fact "enable Pate for Sipoo"
        (toggle-pate "753-R" true) => true)
      (fact "enable OIK verdicts for Sipoo"
        (command admin :set-organization-boolean-attribute
                 :attribute "rectification-verdicts-enabled"
                 :enabled true
                 :organizationId "753-R") => ok?)
      (set-default-r-verdict-template-settings sipoo "753-R")
      (set-default-oik-verdict-template-settings sipoo "753-R")
      (let [oik-template-id (init-and-publish-verdict-template
                              sipoo "753-R" :oik
                              :language "fi"
                              :paatosteksti "default paatos"
                              :proposal-text "default proposal"
                              :proposal-reasoning-text "default proposal reasoning"
                              :verdict-dates ["julkipano" "muutoksenhaku" "lainvoimainen" "aloitettava" "voimassa" "anto"]
                              :kuulutus-dates ["k-kuulutus" "k-muutoksenhaku" "k-lainvoimainen" "k-aloitettava" "k-voimassa"])
            r-template-id   (init-and-publish-verdict-template
                              sipoo "753-R" :r
                              :language "fi"
                              :giver :viranhaltija
                              :verdict-code :ehdollinen
                              :paatosteksti "Verdict text.")
            vid             (create-and-publish-verdict
                              sonja
                              app-id
                              r-template-id
                              :verdict-code "hyvaksytty"
                              :verdict-text "Verdict given"
                              :verdict-date (date/timestamp "22.5.2018")
                              :giver-first-name "Sonja"
                              :giver-last-name "Sibbo")]
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
        (fact "making a rectification verdict is not allowed yet"
          (allowed? sonja :give-rectification-verdict :id app-id :verdict-id vid) => :error.rectification-verdict-cannot-be-given)
        (fact "successful rectification; text is optional"
          (command sonja :upsert-pate-appeal
                   :id app-id
                   :verdict-id vid
                   :type "rectification"
                   :author "Pena"
                   :datestamp created
                   :filedatas []) => ok?)
        (fact "making a rectification verdict is allowed now"
          (allowed? sonja :give-rectification-verdict :id app-id :verdict-id vid) => true)
        (fact "make another rectification"
          (command sonja :upsert-pate-appeal
                   :id app-id
                   :verdict-id vid
                   :type "rectification"
                   :author "Uno"
                   :datestamp created
                   :text "Uno text"
                   :filedatas []) => ok?)
        (fact "appeals are saved to application to be viewed"
          (map :type (:appeals (query-application pena app-id))) => (just ["appeal" "rectification" "rectification"]))
        (fact "rectification verdict created"
          (command sonja :new-pate-verdict-draft :id app-id :template-id oik-template-id :rectified-verdict-id vid) => ok?)
        (let [app             (query-application sonja app-id)
              rect-verdict    (-> app :pate-verdicts last)
              rect-verdict-id (:id rect-verdict)
              rectifications  (-> rect-verdict :references :parent-verdict-rectifications)
              created-date    (date/finnish-date created :zero-pad)]
          (fact "rectification references are ok and do not include non-rects"
            (map #(dissoc % :id) rectifications)
            => [{:appellant           "Pena"
                 :datestamp           created
                 :rectification-id    (str app-id "-OIK-1")
                 :rectification-label (str app-id "-OIK-1, Pena " created-date)
                 :target-verdict      vid
                 :type                "rectification"}
                {:appellant           "Uno"
                 :datestamp           created
                 :rectification-id    (str app-id "-OIK-2")
                 :rectification-label (str app-id "-OIK-2, Uno " created-date)
                 :target-verdict      vid
                 :text                "Uno text"
                 :type                "rectification"}])
          (fact "rectification defaults are as expected"
            (-> rect-verdict :data (tools/unwrapped :_value))
            => {:appellants                    "Pena, Uno"
                :parent-verdict-rectifications (mapv :id rectifications)
                :proposal-reasoning-text       "default proposal reasoning"
                :proposal-text                 "default proposal"
                :verdict-text                  "default paatos"})
          (fact "fill rectification verdict fields"
            (fill-verdict sonja app-id rect-verdict-id
                          :parent-verdict-rectifications (map :id rectifications)
                          :appellants "Viljami Viekas"
                          :phases "Harkittu uudelleen pitkän ajan kuluttua"
                          :verdict-section "sektio"
                          :verdict-code :hyvaksytty
                          :verdict-text "Päätös tehty istunnon päätteeksi"
                          :handler-title "kersantti"
                          :handler "Kaakeli"
                          :verdict-date (now)
                          :k-kuulutus (now)
                          :k-muutoksenhaku (+ (now) 86400000)
                          :k-lainvoimainen (+ (now) 86400000)
                          :k-aloitettava (+ (now) 86400000)
                          :k-voimassa (+ (now) 86400000))
            => nil?)
          (fact "publish the rectification verdict"
            (command sonja :publish-pate-verdict :id app-id :verdict-id rect-verdict-id) => ok?)
          (fact "making a rectification verdict is no longer allowed (all rectifications addressed)"
            (allowed? sonja :give-rectification-verdict :id app-id :verdict-id vid) => :error.rectification-verdict-cannot-be-given)
          (fact "add one more rectification to original verdict"
            (command sonja :upsert-pate-appeal
                     :id app-id
                     :verdict-id vid
                     :type "rectification"
                     :author "Uno"
                     :datestamp created
                     :filedatas []) => ok?)
          (fact "making a rectification verdict is allowed again (unaddressed rectification available)"
            (allowed? sonja :give-rectification-verdict :id app-id :verdict-id vid) => true)
          (fact "rectification verdicts do not show up as latest verdict for application"
            (mapv #(select-keys % [:paatoskategoria :uusinPaatos])
                  (-> app-id export-itest-util/export-application :application :verdicts))
            => [{:paatoskategoria "r"
                 :uusinPaatos     true}
                {:paatoskategoria "oik"
                 :uusinPaatos     false}])
          (fact "should create bulletin for appeal verdict"
            (-> (query sonja :public-bulletin :bulletinId (str app-id "_" rect-verdict-id))
                :bulletin
                not-empty) => truthy))
        (fact "disable Pate for other tests"
          (toggle-pate "753-R" false) => true)))))
