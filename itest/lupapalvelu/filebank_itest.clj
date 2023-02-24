(ns lupapalvelu.filebank-itest
  (:require [lupapalvelu.itest-util :refer :all]
            [midje.sweet :refer :all]
            [sade.shared-util :refer [find-first]]))

(defn- bind-till-done
  [filebankId filedatas]
  (let [{{:keys [id]} :job :as job} (command sonja :bind-filebank-files
                                             :id filebankId
                                             :filedatas filedatas)]
    (->> (repeatedly #(query sonja :bind-filebank-job :jobId id))
         (drop-while #(not= "done" (get-in % [:job :status])))
         first
         :job
         :status)))

(defn- query-get-files
  "Help function for testing get-file query"
  [application-id]
  (into #{} (->> (query sonja :get-filebank-files :id application-id)
                 :files
                 (mapv :filename))))

(apply-remote-minimal)

(facts "Filebank tests"

  ;; Enable filebank
  (fact "filebank enabled"
    (command admin :set-organization-boolean-attribute
             :enabled true
             :organizationId "753-R"
             :attribute :filebank-enabled) => ok?)

  ;; Create application
  (let [application-id (create-app-id pena :propertyId sipoo-property-id)]
    (fact "application was created"
      application-id => string?)

  ;; Upload files to application
    (let [filenames ["test-attachment.txt" "cake.jpg" "cake.png"]
          responses (mapv #(upload-file sonja (str "dev-resources/" %)) filenames)
          file-ids (mapv #(get-in % [:files 0 :fileId]) responses)]
      (fact "upload succeeded"
        (get file-ids 0) => string?
        (get file-ids 1) => string?
        (get file-ids 2) => string?)

      ;; Bind files to filebank
      (bind-till-done application-id [{:fileId (get file-ids 0) :keywords []}]) => "done"
      (bind-till-done application-id [{:fileId (get file-ids 1) :keywords []}]) => "done"
      (bind-till-done application-id [{:fileId (get file-ids 2):keywords []}]) => "done"

      ;;Get files
      (fact "got files for filebank-id"
        (query-get-files application-id)
        => (into #{} filenames))

      ;; Remove files
      (fact "file was removed"
        (command sonja :delete-filebank-file
                 :id application-id
                 :fileId (get file-ids 1))
        => ok?

        (query-get-files application-id)
        => (disj (into #{}  filenames) (get filenames 1)))

      ;; Set keywords
      (fact "keywords were set"
        (command sonja :update-filebank-keywords
                 :id application-id
                 :fileId (get file-ids 0)
                 :keywords ["apina" "orkesteri"])
        => ok?

        (->> (query sonja :get-filebank-files :id application-id)
             :files
             (find-first #(= (% :file-id) (get file-ids 0)))
             :keywords)
        => ["apina" "orkesteri"])

      ;; Non-authority user
      (fact "Pena can't view the files"
        (query pena :get-filebank-files :id application-id)
        => {:ok false :text "error.unauthorized"})

      (fact "Pena can't change the keywords"
        (command pena :update-filebank-keywords
                 :id application-id
                 :fileId (get file-ids 2)
                 :keywords ["tonttu"])
        => {:ok false :text "error.unauthorized"})

      ;; Other authority user in Sipoo
      (fact "Ronja can view the files because she is authority as well in Sipoo"
        (query ronja :get-filebank-files :id application-id)
        => ok?)

      (fact "Velho can't view the files because he/she/they is not authority in Sipoo"
        (query velho :get-filebank-files :id application-id)
        => {:ok false :text "error.application-not-accessible"}))))
