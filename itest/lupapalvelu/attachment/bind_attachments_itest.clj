(ns lupapalvelu.attachment.bind-attachments-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pdf.libreoffice-conversion-client :as libre]
            [sade.util :as util]))

(apply-remote-minimal)

(facts "view-file"
  (let [store        (atom {})
        cookie-store (doto (->cookie-store store)
                       (.addCookie test-db-cookie))
        upload-resp  (upload-file pena "dev-resources/test-attachment.txt" :cookie-store cookie-store)
        file-id      (get-in upload-resp [:files 0 :fileId])]
    upload-resp => ok?
    file-id => string?
    (raw pena :view-file :fileId file-id :cookie-store cookie-store) => http200?))

(facts "placeholder bind"
  (let [application    (create-and-submit-application pena :propertyId sipoo-property-id)
        operation      (:primaryOperation application)
        application-id (:id application)
        attachments    (:attachments application)
        resp1 (upload-file pena "dev-resources/test-attachment.txt")
        file-id-1 (get-in resp1 [:files 0 :fileId])
        resp2 (upload-file pena "dev-resources/invalid-pdfa.pdf")
        file-id-2 (get-in resp2 [:files 0 :fileId])
        resp3 (upload-file pena "dev-resources/invalid-pdfa.pdf")
        file-id-3 (get-in resp3 [:files 0 :fileId])]
    (fact "attachment types - for clarity"
      (map :type attachments) => (just [{:type-group "paapiirustus", :type-id "asemapiirros"}
                                        {:type-group "paapiirustus", :type-id "pohjapiirustus"}
                                        {:type-group "hakija", :type-id "valtakirja"}
                                        {:type-group "pelastusviranomaiselle_esitettavat_suunnitelmat", :type-id "vaestonsuojasuunnitelma"}]))

    (facts "validation errors"
      (fact "unknown attachmentId"
        (command pena :bind-attachments
                 :id application-id
                 :filedatas [{:fileId file-id-1 :type (:type (first attachments))
                              :group {:groupType "operation"
                                      :operations [operation]}
                              :contents "eka"}
                             {:fileId file-id-2 :type (:type (second attachments))
                              :group {:groupType nil}
                              :contents "toka"
                              :attachmentId "foo"}]) => (partial expected-failure? :error.attachment.id))
      (fact "invalid operation"
        (command pena :bind-attachments
                 :id application-id
                 :filedatas [{:fileId file-id-1 :type (:type (first attachments))
                              :group {:groupType "operation"
                                      :operations [operation]} ; this is valid
                              :contents "eka"}
                             {:fileId file-id-3 :type (:type (first attachments))
                              :group {:groupType "operation"
                                      :operations [{:id "fooo"}]} ; this is invalid
                              :contents "eka"}
                             {:fileId file-id-2 :type (:type (second attachments))
                              :group {:groupType nil}
                              :contents "toka"
                              :attachmentId (-> attachments first :id)}]) => (partial expected-failure? :error.illegal-attachment-operation)))

    (let [{job :job :as resp} (command
                                pena
                                :bind-attachments
                                :id application-id
                                :filedatas [{:fileId file-id-1 :type (:type (first attachments))
                                             :group {:groupType "operation"
                                                     :operations [operation]
                                                     :title "Osapuolet"} ; :title is illegal, but this tests that bind-attachments can ignore it
                                             :contents "eka"}
                                            {:fileId file-id-2 :type (:type (second attachments))
                                             :group {:groupType nil}
                                             :contents "toka"}])]
      resp => ok?
      (fact "Job id is returned" (:id job) => truthy)
      (when-not (= "done" (:status job))
        (poll-job pena :bind-attachments-job (:id job) (:version job) 25) => ok?))

    (facts "attachments status"
      (let [app (query-application pena application-id)
            attachments (:attachments app)
            att1 (first attachments)
            att2 (second attachments)]
        (fact "now new attachments created, as placeholders were empty"
          (count attachments) => 4)
        (fact "versions exists - att1"
          (count (:versions att1)) => 1)
        (fact "versions exists - att2"
          (count (:versions att2)) => 1)
        (fact "contents are set"
          (:contents att1) => "eka"
          (:contents att2) => "toka")
        (fact "contents = comment for attachment"
          (first (:comments app)) => (contains {:target {:type "attachment" :id (:id att1)}
                                                :text (:contents att1)
                                                :user (contains {:username "pena"})})
          (second (:comments app)) => (contains {:target {:type "attachment" :id (:id att2)}
                                                 :text (:contents att2)
                                                 :user (contains {:username "pena"})}))

        (when libre/enabled?
          (fact "txt converted"
            (:autoConversion (:latestVersion att1)) => true
            (:fileId (:latestVersion att1)) =not=> file-id-1
            (:originalFileId (:latestVersion att1)) => file-id-1))
        (fact "groups are set"
          (:op att1) => [{:id (:id operation) :name (:name operation)}]
          (:groupType att1) => "operation")

        (facts "Upload new version"
          (let [{job :job :as resp} (command
                                     pena
                                     :bind-attachments
                                     :id application-id
                                     :filedatas [{:fileId file-id-3 :attachmentId (:id att1)}])]
            resp => ok?
            (fact "Job id is returned" (:id job) => truthy)
            (when-not (= "done" (:status job))
              (poll-job pena :bind-attachments-job (:id job) (:version job) 25) => ok?)
            (fact "new version exists"
              (count (:versions att1)) => 1                 ; old count
              (-> (query-application pena application-id) :attachments first :versions count) => 2)))))))

(facts "new attachment bind"
  (let [application    (create-and-submit-application pena :propertyId sipoo-property-id)
        application-id (:id application)
        attachments    (:attachments application)
        file-id-1 (get-in (upload-file pena "dev-resources/test-attachment.txt") [:files 0 :fileId])
        file-id-2 (get-in (upload-file pena "dev-resources/invalid-pdfa.pdf") [:files 0 :fileId])
        file-id-3 (get-in (upload-file pena "dev-resources/test-pdf.pdf") [:files 0 :fileId])]
    (fact "attachment types - for clarity"
      (map :type attachments) => (just [{:type-group "paapiirustus", :type-id "asemapiirros"}
                                        {:type-group "paapiirustus", :type-id "pohjapiirustus"}
                                        {:type-group "hakija", :type-id "valtakirja"}
                                        {:type-group "pelastusviranomaiselle_esitettavat_suunnitelmat", :type-id "vaestonsuojasuunnitelma"}]))

    (let [{job :job :as resp} (command
                                pena
                                :bind-attachments
                                :id application-id
                                :filedatas [{:fileId file-id-1 :type (:type (get attachments 2)) ; hakija
                                             :group {:groupType "parties"}
                                             :contents "hakija"}
                                            {:fileId file-id-2 :type {:type-group "osapuolet" :type-id "tutkintotodistus"}
                                             :group {:groupType nil}
                                             :contents "todistus"}
                                            {:fileId file-id-3 :type {:type-group "erityissuunnitelmat" :type-id "iv_suunnitelma"}
                                             :group {:groupType nil}
                                             :contents "Muu IV-suunnitelma"}])]
      resp => ok?
      (fact "Job id is returned" (:id job) => truthy)
      (when-not (= "done" (:status job))
        (poll-job pena :bind-attachments-job (:id job) (:version job) 25) => ok?)

      (facts "attachments status"
        (let [attachments (:attachments (query-application pena application-id))
              hakija      (util/find-first #(= "hakija" (-> % :type :type-group)) attachments)
              todistus    (util/find-first #(= "osapuolet" (-> % :type :type-group)) attachments)
              iv-suunnnitelma (util/find-first #(= "iv_suunnitelma" (-> % :type :type-id)) attachments)]
          (count (:versions hakija)) => 1
          (count (:versions todistus)) => 1
          (fact "one new attachment was created"
            (count attachments) => 6)
          (fact "contents are set"
            (:contents hakija) => "hakija"
            (:contents todistus) => "todistus")
          (fact "groups are set"
            (:groupType hakija) => "parties"
            (:groupType todistus) => nil)
          (fact "construction time"
            (:originalApplicationState iv-suunnnitelma) => "submitted"
            (:applicationState iv-suunnnitelma) => "verdictGiven")
          (fact "requires authority action"
            (get-in hakija [:approvals (keyword file-id-1) :state]) => "requires_authority_action"
            (get-in todistus [:approvals (keyword file-id-2) :state]) => "requires_authority_action"))))
    ; Authority
    (let [file-id (get-in (upload-file sonja "dev-resources/test-attachment.txt") [:files 0 :fileId])
          {job :job} (command sonja :bind-attachments
                              :id application-id
                              :filedatas [{:fileId file-id :type (:type (get attachments 2)) ; hakija
                                           :group {:groupType "parties"}
                                           :contents "hakija authority"}])
          ]
      (when-not (= "done" (:status job))
        (poll-job sonja :bind-attachments-job (:id job) (:version job) 25) => ok?)
      (let [attachments        (:attachments (query-application pena application-id))
            hakija-attachments (filter #(= "hakija" (-> % :type :type-group)) attachments)
            target-attachment  (second hakija-attachments)]
        (fact "new hakija attachment was created" (count hakija-attachments) => 2)
        (fact "groups are set"
          (:groupType target-attachment) => "parties")
        (fact "attachment approval state is 'ok' for authority"
          (get-in target-attachment [:approvals (keyword file-id)]) => (contains {:state "ok"
                                                                                  :timestamp number?
                                                                                  :user (contains {:firstName "Sonja"})}))))))

(facts "attachment bind with target"
  (let [application    (create-and-submit-application pena :propertyId sipoo-property-id)
        application-id (:id application)
        statement-resp (command ronja :request-for-statement :functionCode nil :id application-id
                                :selectedPersons [{:email "sonja.sibbo@sipoo.fi"
                                                   :name "Sonja Lausunto"
                                                   :text "Totuus"}]
                                :saateText "saate"
                                :dueDate (util/get-timestamp-from-now :day 3))
        statements (:statements (query-application pena application-id))
        statement (first statements)
        file-id-1 (get-in (upload-file sonja "dev-resources/test-attachment.txt") [:files 0 :fileId])
        file-id-2 (get-in (upload-file sonja "dev-resources/invalid-pdfa.pdf") [:files 0 :fileId])]
    (count (:attachments application)) => 4
    statement-resp => ok?

    (fact "Pena can't bind statement"
      (command pena :bind-attachments :id application-id
        :filedatas [{:fileId file-id-1
                     :type {:type-group "ennakkoluvat_ja_lausunnot" :type-id "lausunto"}
                     :group nil
                     :target {:id (:id statement) :type "statement"}}
                    {:fileId file-id-2
                     :type {:type-group "ennakkoluvat_ja_lausunnot" :type-id "vesi_ja_viemariliitoslausunto_tai_kartta"}
                     :group nil
                     :target {:id (:id statement) :type "statement"}}]) => (partial expected-failure? :error.not-statement-owner))
    (let [{job :job :as resp} (command
                                sonja
                                :bind-attachments
                                :id application-id
                                :filedatas [{:fileId file-id-1 :type {:type-group "ennakkoluvat_ja_lausunnot" :type-id "lausunto"}
                                             :group nil
                                             :target {:id (:id statement) :type "statement"}}
                                            {:fileId file-id-2 :type {:type-group "ennakkoluvat_ja_lausunnot" :type-id "vesi_ja_viemariliitoslausunto_tai_kartta"}
                                             :group nil
                                             :target {:id (:id statement) :type "statement"}}])]
      (fact "Sonja can add bind statement attachments" resp => ok?)
      (fact "Job id is returned" (:id job) => truthy)
      (when (and (:ok resp) (not= "done" (:status job)))
        (poll-job pena :bind-attachments-job (:id job) (:version job) 25) => ok?)

      (fact "Pena can't see draft statement attachments"
        (count (:attachments (query-application pena application-id))) => 4)
      (facts "statement attachments status"
        (let [attachments (:attachments (query-application ronja application-id))
              statement-attachments (filter #(= (-> % :target :type keyword) :statement) attachments)]
          (count attachments) => 6
          (count statement-attachments) => 2
          (doseq [att statement-attachments]
            (fact {:midje/description (str (get-in att [:type :type-id]) " statement as target id")}
              (get-in att [:target :id]) => (:id statement))
            (fact {:midje/description (str (get-in att [:type :type-id]) " group type not set")}
              (:groupType att) => nil)
            (fact {:midje/description (str (get-in att [:type :type-id]) " attachment type-group is correct")}
              (get-in att [:type :type-group]) => "ennakkoluvat_ja_lausunnot")
            (fact {:midje/description (str (get-in att [:type :type-id]) " versions count ok")}
              (count (:versions att)) => 1)))))))

(defn upload-file-id [apikey filename]
  (let [resp (upload-file apikey (str "dev-resources/" filename))]
    (get-in resp [:files 0 :fileId])))

(facts "construction-time attachments bind"
       (let [application    (create-and-submit-application pena :propertyId sipoo-property-id)
             application-id (:id application)
             attachments    (:attachments application)
             file-id-1      (upload-file-id pena "test-attachment.txt")
             file-id-2      (upload-file-id pena "invalid-pdfa.pdf")
             file-id-3      (upload-file-id pena "test-attachment.txt")
             file-id-4      (upload-file-id pena "cake.jpg")]
    (fact "attachment types - for clarity"
          (map :type attachments) => (just [{:type-group "paapiirustus", :type-id "asemapiirros"}
                                            {:type-group "paapiirustus", :type-id "pohjapiirustus"}
                                            {:type-group "hakija", :type-id "valtakirja"}
                                            {:type-group "pelastusviranomaiselle_esitettavat_suunnitelmat", :type-id "vaestonsuojasuunnitelma"}]))

    (let [{job :job :as resp} (command
                               pena
                               :bind-attachments
                               :id application-id
                               :filedatas [{:fileId           file-id-1 :type (:type (get attachments 2)) ; hakija
                                            :group            {:groupType "parties"}
                                            :contents         "hakija"
                                            :constructionTime true}
                                           ;; Erityissuunnitelmat are implicitly construction time attachments.
                                           {:fileId   file-id-2 :type {:type-group "erityissuunnitelmat" :type-id "kalliorakentamistekninen_suunnitelma"}
                                            :group    {:groupType nil}
                                            :contents "esuunnitelma"}
                                           {:fileId   file-id-3 :type {:type-group "muut" :type-id "muu"}
                                            :group    {:groupType nil}
                                            :contents "harmless"}])]
      resp => ok?
      (fact "Job id is returned" (:id job) => truthy)
      (when-not (= "done" (:status job))
        (poll-job pena :bind-attachments-job (:id job) (:version job) 25) => ok?)

      (facts "attachments status"
             (let [attachments (:attachments (query-application pena application-id))
                   hakija      (util/find-first #(= "hakija" (-> % :type :type-group)) attachments)
                   suunnitelma (util/find-first #(= "erityissuunnitelmat" (-> % :type :type-group)) attachments)
                   other (util/find-first #(= "muut" (-> % :type :type-group)) attachments)]
               suunnitelma => truthy
               (fact "contents are set"
                     (:contents hakija) => "hakija"
                     (:contents suunnitelma) => "esuunnitelma"
                     (:contents other) => "harmless")
               (fact "construction time"
                     (:originalApplicationState hakija) => "draft"
                     (:applicationState hakija) => "verdictGiven"
                     (:originalApplicationState suunnitelma) => "submitted"
                     (:applicationState suunnitelma) => "verdictGiven"
                     (:originalApplicationState other) => nil
                     (:applicationState other) => "submitted")
               (facts "New version and filedata"
                      (let [{job :job :as resp} (command pena :bind-attachment
                                                         :id application-id
                                                         :attachmentId (:id other)
                                                         :fileId file-id-4)]
                        resp => ok?
                        (when-not (= "done" (:status job))
                          (poll-job pena :bind-attachments-job (:id job) (:version job) 25) => ok?)
                        (facts "New version details"
                               (let [new-other (util/find-first #(= "muut" (-> % :type :type-group))
                                                                (:attachments (query-application pena application-id)))]
                                 (fact "filename"
                                       (-> new-other :latestVersion :filename) => "cake.jpg")
                                 (fact "construction time (regression test)"
                                       (:originalApplicationState new-other) => nil
                                       (:applicationState new-other) => "submitted"))))))))))

(facts "signing w/ attachments bind"
  (let [application    (create-and-submit-application pena :propertyId sipoo-property-id)
        application-id (:id application)
        attachments    (:attachments application)
        file-id-1      (get-in (upload-file pena "dev-resources/test-attachment.txt") [:files 0 :fileId])
        file-id-2      (get-in (upload-file pena "dev-resources/invalid-pdfa.pdf")    [:files 0 :fileId])]
    (fact "attachment types - for clarity"
      (map :type attachments) => (just [{:type-group "paapiirustus", :type-id "asemapiirros"}
                                        {:type-group "paapiirustus", :type-id "pohjapiirustus"}
                                        {:type-group "hakija", :type-id "valtakirja"}
                                        {:type-group "pelastusviranomaiselle_esitettavat_suunnitelmat", :type-id "vaestonsuojasuunnitelma"}]))

    (fact "signing with invalid password fails"
      (command pena :bind-attachments :id application-id
               :filedatas [{:fileId file-id-2 :type {:type-group "erityissuunnitelmat" :type-id "kalliorakentamistekninen_suunnitelma"}
                            :group {:groupType nil}
                            :contents "esuunnitelma"
                            :constructionTime true
                            :sign true}]) => (partial expected-failure? :error.password)
      (command pena :bind-attachments :id application-id :password "wrongPass"
               :filedatas [{:fileId file-id-2 :type {:type-group "erityissuunnitelmat" :type-id "kalliorakentamistekninen_suunnitelma"}
                            :group {:groupType nil}
                            :contents "esuunnitelma"
                            :constructionTime true
                            :sign true}]) => (partial expected-failure? :error.password))

    (let [{job :job :as resp} (command
                                pena
                                :bind-attachments
                                :id application-id
                                :password "pena"
                                :filedatas [{:fileId file-id-1 :type (:type (get attachments 2)) ; hakija
                                             :group {:groupType "parties"}
                                             :contents "hakija"}
                                            {:fileId file-id-2 :type {:type-group "erityissuunnitelmat" :type-id "kalliorakentamistekninen_suunnitelma"}
                                             :group {:groupType nil}
                                             :contents "esuunnitelma"
                                             :constructionTime true
                                             :sign true}])]
      (fact "with correct password resp is ok" resp => ok?)
      (fact "Job id is returned" (:id job) => truthy)
      (when-not (= "done" (:status job))
        (poll-job pena :bind-attachments-job (:id job) (:version job) 25) => ok?)

      (facts "attachments status"
        (let [attachments (:attachments (query-application pena application-id))
              hakija      (util/find-first #(= "hakija" (-> % :type :type-group)) attachments)
              suunnitelma (util/find-first #(= "erityissuunnitelmat" (-> % :type :type-group)) attachments)]
          suunnitelma => truthy
          (fact "contents are set"
            (:contents hakija) => "hakija"
            (:contents suunnitelma) => "esuunnitelma")
          (fact "signature is set"
            (:signatures hakija) => empty?
            (count (:signatures suunnitelma)) => 1
            (first (:signatures suunnitelma)) => (just {:created number?
                                                        :fileId (get-in suunnitelma [:latestVersion :fileId])
                                                        :user map?
                                                        :version (get-in suunnitelma [:latestVersion :version])})))))))

#_(facts "inforequest bind")
