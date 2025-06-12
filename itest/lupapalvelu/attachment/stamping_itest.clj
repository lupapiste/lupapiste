(ns lupapalvelu.attachment.stamping-itest
  (:require [lupapalvelu.attachment :refer [get-attachment-info]]
            [lupapalvelu.attachment.util :refer [attachment-state]]
            [lupapalvelu.fixture.pate-verdict :as pate-fixture]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.logging :as log]
            [midje.sweet :refer :all]
            [sade.core :as core]
            [sade.date :as date]
            [sade.util :as util]))

(def ^:private sipoo-R-org-id "753-R")

(def today (date/finnish-date (date/now) :zero-pad))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (facts "Stamping"
    (let [application         (create-and-submit-application pena :propertyId sipoo-property-id)
          application-id      (:id application)
          attachment          (first (:attachments application))
          _                   (upload-attachment pena application-id attachment true :filename "dev-resources/test-pdf.pdf")
          application         (query-application pena application-id)
          comments            (:comments application)
          stamp               {:id         "123456789012345678901234"
                               :name       "Oletusleima"
                               :position   {:x 10 :y 200}
                               :background 0
                               :page       :first
                               :qrCode     true
                               :rows       [[{:type :custom-text :value "Hyv\u00e4ksytty"}
                                             {:type :current-date :value today}]
                                            [{:type :backend-id :value "17-0753-R"}]
                                            [{:type :organization :value "Sipoon rakennusvalvonta"}]]}
          {job :job :as resp} (command
                                sonja
                                :stamp-attachments
                                :id application-id
                                :timestamp ""
                                :files [(:id attachment)]
                                :lang "fi"
                                :stamp stamp)
          file-id             (get-in (:value job) [(-> job :value keys first) :fileId])]

      (fact "stamp is validated against schema"
        (command
          sonja
          :stamp-attachments
          :id application-id
          :timestamp ""
          :files [(:id attachment)]
          :lang "fi"
          :stamp (assoc stamp :page "foo")) => (partial expected-failure? :error.illegal-value:schema-validation))

      (fact "not stamped by default"
        (get-in (get-attachment-info application (:id attachment)) [:latestVersion :stamped]) => falsey)

      (fact "Attachment state is not ok"
        (attachment-state (get-attachment-info application (:id attachment))) =not=> :ok)

      resp => ok?
      (fact "Job id is returned" (:id job) => truthy)
      (fact "FileId is returned" file-id => truthy)

      ;; Poll for 10 seconds
      (when-not (= "done" (:status job))
        (log/info "Polling for stamp job id" (:id job) "version" (:version job) "application" application-id "attachment" (:id attachment))
        (fact "stamp-attachments-job finishes successfully"
          (poll-job sonja :stamp-attachments-job (:id job) (:version job) 50) => ok?))

      (let [attachment     (get-attachment-by-id sonja application-id (:id attachment))
            pena-app       (query-application pena application-id)
            comments-after (:comments pena-app)]

        (fact "Attachment has stamp and no new comments"
          (get-in attachment [:latestVersion :stamped]) => true
          comments-after => comments)
        (fact "Pena can't delete attachemnt, as it's bound to application by operation"
          (allowed? pena :delete-attachment :id application-id :attachmentId (:id attachment)) => :error.unauthorized)

        (fact "Attachment has Sonja's stamper auth"
          (get-in attachment [:auth 1 :id]) => sonja-id
          (get-in attachment [:auth 1 :role]) => "stamper")

        (fact "Attachment state is ok"
          (attachment-state attachment) => :ok)

        (fact "New fileid is in response" (get-in attachment [:latestVersion :fileId]) =not=> file-id)

        (facts "re-stamp"
          (let [{job :job :as resp} (command
                                      sonja
                                      :stamp-attachments
                                      :id application-id
                                      :timestamp ""
                                      :files [(:id attachment)]
                                      :lang "fi"
                                      :stamp stamp)]
            resp => ok?
            ;; Poll for 10 seconds
            (when-not (= "done" (:status job))
              (poll-job sonja :stamp-attachments-job (:id job) (:version job) 50) => ok?)

            (fact "Latest version has changed"
              (let [attachment-after-restamp (get-attachment-by-id sonja application-id (:id attachment))]
                (:latestVersion attachment) =not=> (:latestVersion attachment-after-restamp)
                (get-in attachment [:latestVersion :stamped]) => true))))))))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (facts "Stamped attachment can't be deleted LPK-3335"
    (let [application         (create-and-submit-application pena :propertyId sipoo-property-id)
          application-id      (:id application)
          attachment          (first (:attachments application))
          ;; upload to placeholder
          _                   (upload-file-and-bind pena application-id {:filename "dev-resources/test-pdf.pdf"} :attachment-id (:id attachment))
          ;; we upload new attachment, as attachment created on app creation can't be deleted
          _                   (upload-file-and-bind pena application-id {:type  {:type-group "suunnitelmat"
                                                                                 :type-id    "valaistussuunnitelma"}
                                                                         :group {:groupType  "operation"
                                                                                 :operations [{:id (-> application :primaryOperation :id)}]}})
          application         (query-application pena application-id)
          new-attachment      (util/find-first #(= "valaistussuunnitelma" (get-in % [:type :type-id])) (:attachments application))
          stamp               {:id         "123456789012345678901234"
                               :name       "Oletusleima"
                               :position   {:x 10 :y 200}
                               :background 0
                               :page       :first
                               :qrCode     true
                               :rows       [[{:type :custom-text :value "Hyväksytty"}
                                             {:type :current-date :value today}]
                                            [{:type :backend-id :value "17-0753-R"}]
                                            [{:type :organization :value "Sipoon rakennusvalvonta"}]]}
          {job :job :as resp} (command
                                sonja
                                :stamp-attachments
                                :id application-id
                                :timestamp ""
                                :files [(:id new-attachment)
                                        (:id attachment)]
                                :lang "fi"
                                :stamp stamp)
          file-id             (get-in (:value job) [(-> job :value keys first) :fileId])]
      new-attachment => truthy

      (fact "stamp is validated against schema"
        (command
          sonja
          :stamp-attachments
          :id application-id
          :timestamp ""
          :files [(:id new-attachment)
                  (:id attachment)]
          :lang "fi"
          :stamp (assoc stamp :page "foo")) => (partial expected-failure? :error.illegal-value:schema-validation))

      (fact "not stamped by default"
        (get-in (get-attachment-info application (:id attachment)) [:latestVersion :stamped]) => falsey
        (get-in (get-attachment-info application (:id new-attachment)) [:latestVersion :stamped]) => falsey)

      (fact "Attachment state is not ok"
        (attachment-state (get-attachment-info application (:id attachment))) =not=> :ok
        (attachment-state (get-attachment-info application (:id new-attachment))) =not=> :ok)

      resp => ok?
      (fact "Job id is returned" (:id job) => truthy)
      (fact "FileId is returned" file-id => truthy)

      ;; Poll for 5 seconds
      (when-not (= "done" (:status job)) (poll-job sonja :stamp-attachments-job (:id job) (:version job) 25))

      (let [application        (query-application sonja application-id)
            regular-attachment (get-attachment-info application (:id attachment))
            new-attachment     (get-attachment-info application (:id new-attachment))]

        (fact "Attachments have stamp"
          (get-in new-attachment [:latestVersion :stamped]) => true
          (get-in regular-attachment [:latestVersion :stamped]) => true)
        (fact "Pena can't delete attachemnt, as it's stamped"
          (allowed? pena :delete-attachment :id application-id :attachmentId (:id new-attachment)) => :error.attachment.stamped)
        (fact "Sonja can delete as she is authority"
          (allowed? sonja :delete-attachment :id application-id :attachmentId (:id new-attachment)) => true)

        (fact "Attachment state is now ok"
          (attachment-state new-attachment) => :ok)
        (fact "New fileid is in response" (get-in new-attachment [:latestVersion :fileId]) =not=> file-id)))))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (facts "Stamping copies all signings"
    (let [application-id (create-app-id pena :propertyId sipoo-property-id :operation "kerrostalo-rivitalo")
          application    (query-application pena application-id)
          attachment-id  (:id (first (:attachments application)))
          _              (upload-attachment pena application-id {:id attachment-id :type {:type-group "paapiirustus" :type-id "asemapiirros"}} true)
          _              (command pena :invite-with-role :id application-id :email "mikko@example.com" :text  "" :documentName "" :documentId "" :path "" :role "writer")
          _              (command mikko :approve-invite :id application-id)
          attachment-id2 (upload-attachment pena application-id
                                            {:type {:type-group "paapiirustus" :type-id "pohjapiirustus"}}
                                            true
                                            :filename "dev-resources/test-pdf.pdf")
          stamp          {:id         "123456789012345678901234"
                          :name       "Oletusleima"
                          :position   {:x 10 :y 200}
                          :background 0
                          :page       "all"
                          :qrCode     true
                          :rows       [[{:type "custom-text" :value "Stamp"}]]}]

      (fact "Both guys sign the first attachment"
        (command pena :sign-attachments :id application-id :attachmentIds [attachment-id] :password "pena") => ok?
        (command mikko :sign-attachments :id application-id :attachmentIds [attachment-id] :password "mikko123") => ok?
        (count (:signatures (first (:attachments (query-application pena application-id))))) => 2)

      (fact "Pena signs the second attachment"
        (command pena :sign-attachments :id application-id :attachmentIds [attachment-id2] :password "pena") => ok?)

      (fact "Pena rotates the second attachment"
        (command pena :rotate-pdf :id application-id :attachmentId attachment-id2 :rotation -90) => ok?)

      (fact "Sonja stamps the attachments"
        (command pena :submit-application :id application-id) = ok?
        (let [{job :job} (command sonja :stamp-attachments :id application-id
                                  :timestamp (sade.core/now)
                                  :files [attachment-id attachment-id2] :lang :fi :stamp stamp)]
          ;; Wait that stamping job is done
          (when-not (= "done" (:status job)) (poll-job sonja :stamp-attachments-job (:id job) (:version job) 25))))

      (fact "The first stamped attachment has both signatures"
        (let [{attachments :attachments} (query-application sonja application-id)
              stamped-attachment         (first attachments)]
          (count (:signatures stamped-attachment)) => 4
          (:version (get (:signatures stamped-attachment) 2)) => {:major 1 :minor 1}
          (:version (get (:signatures stamped-attachment) 3)) => {:major 1 :minor 1}))
      (fact "The second stamped attachment has Pena's signature"
        (let [{attachments :attachments} (query-application sonja application-id)
              stamped-attachment         (last attachments)]
          (:latestVersion stamped-attachment) => (contains {:stamped true
                                                            :version {:major 1 :minor 1}})
          (:signatures stamped-attachment) => (just [(contains {:user    (contains {:firstName "Pena"})
                                                                :version {:major 1 :minor 0}})
                                                     (contains {:user    (contains {:firstName "Pena"})
                                                                :version {:major 1 :minor 1}})])))
      (fact "Rotation retains stamping status and signatures"
        (let [stamped-fn                         #(->> (query-application sonja application-id)
                                                       :attachments
                                                       (util/find-by-id attachment-id2))
              {att-id :id latest :latestVersion} (stamped-fn)
              old-file-id                        (:fileId latest)]
          (:stamped latest) => true
          (command sonja :rotate-pdf :id application-id
                   :attachmentId att-id
                   :rotation 90) => ok?
          (let [{:keys [latestVersion signatures]} (stamped-fn)
                new-file-id                        (:fileId latestVersion)]
            (:stamped latestVersion) => true
            (util/find-by-key :fileId old-file-id signatures) => nil
            (->> signatures
                 (filter #(= (:fileId %) new-file-id))
                 count) => 1)))

      (fact "Re-stamping updates existing signatures"
        (fact "Sonja re-stamps the first attachment"
          (let [stamp-attachment             (fn []
                                               (let [{job :job} (command sonja :stamp-attachments :id application-id
                                                                         :timestamp (sade.core/now)
                                                                         :files [attachment-id2] :lang :fi :stamp stamp)]
                                                 ;; Wait that stamping job is done
                                                 (when-not (= "done" (:status job)) (poll-job sonja :stamp-attachments-job
                                                                                              (:id job) (:version job) 25))))
                sigs-info                    #(let [{:keys [latestVersion
                                                            signatures]} (->> (query-application sonja application-id)
                                                                              :attachments
                                                                              (util/find-by-id attachment-id2))]
                                                {:latest-file-id (:fileId latestVersion)
                                                 :stamped        (boolean (:stamped latestVersion))
                                                 :signatures     (map (fn [{:keys [fileId version]}]
                                                                        [fileId  (util/kw-path (:major version) (:minor version))])
                                                                      signatures)})
                {old-file-id    :latest-file-id old-stamped :stamped
                 old-signatures :signatures} (sigs-info)

                _                            (stamp-attachment)

                {new-file-id    :latest-file-id new-stamped :stamped
                 new-signatures :signatures} (sigs-info)
                file-id-filter               (fn [fid sigs]
                                               (count (filter #(= (first %) fid) sigs)))]
            (fact "Stamped"
              old-stamped => true
              new-stamped => true)

            (fact "Signatures vs. file ids"
              (count old-signatures) => 2
              (count new-signatures) => 2
              (file-id-filter old-file-id old-signatures) => 1
              (file-id-filter old-file-id new-signatures) => 0
              (file-id-filter new-file-id old-signatures) => 0
              (file-id-filter new-file-id new-signatures) => 1)

            (fact "Pena adds new attachment version and signs it"
              (let [earlier-id new-file-id
                    file-id    (upload-version-and-bind pena application-id attachment-id2
                                                        "dev-resources/test-pdf.pdf")]
                (command pena :sign-attachments :id application-id :attachmentIds [attachment-id2] :password "pena") => ok?
                (sigs-info) => (just {:latest-file-id file-id
                                      :stamped        false
                                      :signatures     (just (just truthy :1.0) [earlier-id :1.1] [file-id :2.0])})
                (fact "Pena rotates the attachment"
                  (command sonja :rotate-pdf :id application-id
                           :attachmentId attachment-id2
                           :rotation 90) => ok?
                  (let [{:keys [latest-file-id signatures]} (sigs-info)]
                    latest-file-id =not=> file-id
                    signatures => (just (just truthy :1.0) [earlier-id :1.1] [latest-file-id :2.0])
                    (facts "Sonja stamps the new attachment version"
                      (stamp-attachment)
                      (let [{stamped-file-id :latest-file-id
                             :keys           [signatures stamped]} (sigs-info)]
                        stamped-file-id =not=> latest-file-id
                        stamped => true
                        signatures => (just (just truthy :1.0) [earlier-id :1.1] [latest-file-id :2.0]
                                            [stamped-file-id :2.1])
                        (fact "Restamp"
                          (stamp-attachment)
                          (let [{restamped-file-id :latest-file-id
                                 :keys             [signatures stamped]} (sigs-info)]
                            restamped-file-id =not=> stamped-file-id
                            stamped => true
                            signatures => (just (just truthy :1.0) [earlier-id :1.1] [latest-file-id :2.0]
                                                [restamped-file-id :2.1])))))))))))))))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (facts "Stamping does not change approval"
    (let [application    (create-and-submit-application pena :propertyId sipoo-property-id)
          application-id (:id application)
          att-template   (first (:attachments application))
          attachment-id  (upload-attachment pena application-id att-template true :filename "dev-resources/test-pdf.pdf")
          attachment     (-> (query-application sonja application-id) :attachments (first))
          stamp          {:id         "123456789012345678901234"
                          :name       "Oletusleima"
                          :position   {:x 10 :y 200}
                          :background 0
                          :page       :first
                          :qrCode     true
                          :rows       [[{:type :custom-text :value "Hyv\u00e4ksytty"}
                                        {:type :current-date :value today}]
                                       [{:type :backend-id :value "17-0753-R"}]
                                       [{:type :organization :value "Sipoon rakennusvalvonta"}]]}]

      (fact "Sonja approves attachment"
        (command sonja :approve-attachment :id application-id :attachmentId attachment-id :fileId (-> attachment :latestVersion :fileId)) => ok?
        (attachment-state (get-attachment-info (query-application sonja application-id) (:id attachment))) => :ok)

      (fact "Ronja stamps attachment"
        (let [{job :job :as resp} (command ronja :stamp-attachments :id application-id :timestamp (sade.core/now) :files [attachment-id] :lang :fi :stamp stamp)]
          resp => ok?
          ;; Wait that stamping job is done
          (when-not (= "done" (:status job)) (poll-job sonja :stamp-attachments-job (:id job) (:version job) 25))

          (fact "attachment still has Sonja's approval"
            (let [attachment (get-attachment-info (query-application sonja application-id) attachment-id)
                  fileId     (-> attachment :latestVersion :originalFileId (keyword))
                  approval   (-> attachment :approvals (get fileId))]
              (-> approval :user :firstName) => "Sonja")))))))

(defn no-errors?
  [{:keys [errors ok]}]
  (and (nil? errors) (true? ok)))

(with-test-system (-> {:fixture "pate-verdict"}
                      test-system/with-remote-components
                      test-system/with-mock-laundry)
  (facts "Authority can remove stamped version from an attachment included in published pate verdict"
    (let [application    (create-and-submit-application pena :propertyId sipoo-property-id)
          application-id (:id application)
          stamp          {:id         "123456789012345678901234"
                          :name       "Oletusleima"
                          :position   {:x 10 :y 200}
                          :background 0
                          :page       :first
                          :qrCode     true
                          :rows       [[{:type :custom-text :value "Hyv\u00e4ksytty"}
                                        {:type :current-date :value today}]
                                       [{:type :backend-id :value "17-0753-R"}]
                                       [{:type :organization :value "Sipoon rakennusvalvonta"}]]}
          _              (upload-attachment pena application-id
                                            {:type {:type-group "osapuolet" :type-id "cv"}}
                                            true
                                            :filename "dev-resources/test-pdf.pdf")]

      (fact "submit, approve and create verdict"
        (command sonja :set-tos-function-for-application :id application-id :functionCode "10 03 00 01")
        (command sonja :update-app-bulletin-op-description :id application-id :description "Bulletin description") => ok?
        (command sonja :approve-application :id application-id :lang "fi") => ok?)

      (fact "Stamping attachment"
        (let [attachments   (-> (query-application sonja application-id) :attachments)
              attachment    (util/find-first #(= "osapuolet" (get-in % [:type :type-group]))
                                             attachments)
              attachment-id (:id attachment)
              {job :job}    (command sonja :stamp-attachments :id application-id :timestamp (sade.core/now) :files [attachment-id] :lang :fi :stamp stamp)]
          ;; Wait that stamping job is done
          (when-not (= "done" (:status job)) (poll-job sonja :stamp-attachments-job (:id job) (:version job) 25))
          (let [{verdict-id :verdict-id} (command sonja :new-pate-verdict-draft
                                                  :id application-id
                                                  :template-id (-> pate-fixture/verdict-templates-r
                                                                   first
                                                                   :id))
                attachment               (->> (query-application pena application-id) :attachments (filter #(= (:id %) attachment-id)) first)
                _                        (command sonja :edit-pate-verdict :id application-id :verdict-id verdict-id :path [:attachments] :value [attachment-id])]
            (fact "Pate verdict is published"
              (command sonja :edit-pate-verdict :id application-id :verdict-id verdict-id :path [:automatic-verdict-dates] :value true) => no-errors?
              (command sonja :edit-pate-verdict :id application-id :verdict-id verdict-id :path [:verdict-date] :value (core/now)) => no-errors?
              (command sonja :edit-pate-verdict :id application-id :verdict-id verdict-id :path [:verdict-code] :value "hyvaksytty") => no-errors?
              (command sonja :edit-pate-verdict :id application-id :verdict-id verdict-id :path [:giver-first-name] :value "Pena") => no-errors?
              (command sonja :edit-pate-verdict :id application-id :verdict-id verdict-id :path [:giver-last-name] :value "Päättäjä") => no-errors?
              (command sonja :publish-pate-verdict :id application-id :verdict-id verdict-id) => ok?)

            (fact "Non-stamped attachment version included in published verdict cannot be deleted"
              (let [version (->> attachment
                                 :versions
                                 (filter #(not (:stamped %)))
                                 first)]
                (command sonja :delete-attachment-version
                         :id application-id
                         :attachmentId attachment-id
                         :fileId (:fileId version)
                         :originalFileId (:originalFileId version)) => (partial expected-failure? :error.attachment-in-published-verdict)))

            (fact "Stamped attachment version included in published verdict can be deleted by authority"
              (let [version (:latestVersion attachment)]
                (command sonja :delete-attachment-version
                         :id application-id
                         :attachmentId attachment-id
                         :fileId (:fileId version)
                         :originalFileId (:originalFileId version)) => ok?)))))))

  (facts "Authority can remove stamped version from a readonly attachment"
    (let [application    (create-and-submit-application pena :propertyId sipoo-property-id)
          application-id (:id application)
          stamp          {:id         "123456789012345678901234"
                          :name       "Oletusleima"
                          :position   {:x 10 :y 200}
                          :background 0
                          :page       :first
                          :qrCode     true
                          :rows       [[{:type :custom-text :value "Hyv\u00e4ksytty"}
                                        {:type :current-date :value today}]
                                       [{:type :backend-id :value "17-0753-R"}]
                                       [{:type :organization :value "Sipoon rakennusvalvonta"}]]}]

      (fact "Sonja fetches verdict"
        (command sonja :check-for-verdict :id application-id) => ok?)

      (let [application                (query-application sonja application-id)
            {attachment-id :id
             :as           attachment} (-> application :attachments last)]
        (fact "Verdict attachment is locked and readonly"
          (:readOnly attachment) => true
          (:locked attachment) => true)

        (fact "Ronja stamps attachment"
          (let [{job :job :as resp} (command ronja :stamp-attachments :id application-id :timestamp (sade.core/now) :files [attachment-id] :lang :fi :stamp stamp)]
            resp => ok?
            ;; Wait that stamping job is done
            (when-not (= "done" (:status job)) (poll-job sonja :stamp-attachments-job (:id job) (:version job) 25))

            (let [[ver1 ver2] (-> (query-application sonja application-id) :attachments last :versions)
                  delete-cmd  (fn [apikey ver success? & [error-key]]
                                (fact {:midje/description (str "delete attachment version should "
                                                               (if success? "succeed" "fail"))}
                                  (command apikey :delete-attachment-version
                                           :id application-id
                                           :attachmentId attachment-id
                                           :fileId (:fileId ver)
                                           :originalFileId (:originalFileId ver))
                                  => #(if success?
                                        (ok? %)
                                        (expected-failure? (or error-key :error.unauthorized) %))))]
              (fact "Pena cannot delete either version"
                (delete-cmd pena ver1 false :error.pre-verdict-attachment)
                (delete-cmd pena ver2 false))
              (fact "Sonja cannot delete the non-stamped version"
                (:stamped ver1) => falsey
                (delete-cmd sonja ver1 false))
              (fact "Sonja can delete the stamped version"
                (:stamped ver2) => true
                (delete-cmd sonja ver2 true))))))))

  (facts "stamp-templates"
    (let [result (query sipoo :stamp-templates :organizationId sipoo-R-org-id)
          stamps (:stamps result)]

      (fact "stamps in query result"
        (keys result) => (contains :stamps))

      (fact "two stamps by default"
        (count stamps) => 2))))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (facts "editing stamp templates"
    (let [add-result (command sipoo :upsert-stamp-template :organizationId sipoo-R-org-id
                              :name "zero stamp" :page "first" :background 0
                              :qrCode false :position {:x 0 :y 0}
                              :rows [[{:type "custom-text" :text "Hello World"}]])
          stamp-id   (:stamp-id add-result)]
      (fact "add new stamp"
        add-result => ok?
        stamp-id => string?)

      (fact "stamp is added"
        (let [stamps    (:stamps (query sipoo :stamp-templates :organizationId sipoo-R-org-id))
              new-stamp (last stamps)]
          (count stamps) => 3

          (fact "stamp data is ok"
            (keys new-stamp) => (just [:id :name :page :background
                                       :qrCode :position :rows]
                                      :in-any-order :gaps-ok)
            (:id new-stamp) => stamp-id
            (:name new-stamp) => "zero stamp"
            (:page new-stamp) => "first"
            (:background new-stamp) => 0
            (:qrCode new-stamp) => false
            (:position new-stamp) => {:x 0 :y 0}
            (:rows new-stamp) => [[{:type "custom-text" :text "Hello World"}]])))

      (facts "edit existing stamp"
        (let [edit-result (command sipoo :upsert-stamp-template :organizationId sipoo-R-org-id
                                   :stamp-id stamp-id :name "zero stamp"
                                   :page "last" :background 20 :qrCode false
                                   :position {:x 1 :y 2}
                                   :rows [[{:type "custom-text"
                                            :text "Hello World"}]])]
          (fact "edit ok"
            edit-result => ok?
            (:stamp-id edit-result) => stamp-id)

          (fact "stamp is added"
            (let [stamps       (:stamps (query sipoo :stamp-templates :organizationId sipoo-R-org-id))
                  edited-stamp (last stamps)]
              (count stamps) => 3

              (fact "stamp data is ok"
                (keys edited-stamp) => (just [:id :name :page :background
                                              :qrCode :position :rows]
                                             :in-any-order :gaps-ok)
                (:id edited-stamp) => stamp-id
                (:name edited-stamp) => "zero stamp"
                (:page edited-stamp) => "last"
                (:background edited-stamp) => 20
                (:qrCode edited-stamp) => false
                (:position edited-stamp) => {:x 1 :y 2}
                (:rows edited-stamp) => [[{:type "custom-text" :text "Hello World"}]]))))))))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (facts "remove stamp"
    (let [stamps   (:stamps (query sipoo :stamp-templates :organizationId sipoo-R-org-id))
          stamp-id (:id (last stamps))]

      stamp-id => string?

      (command sipoo :delete-stamp-template :organizationId sipoo-R-org-id :stamp-id stamp-id) => ok?

      (fact "stamp is removed"
        (let [updated-stamps (:stamps (query sipoo :stamp-templates :organizationId sipoo-R-org-id))]
          (count updated-stamps) => (dec (count stamps))
          (util/find-by-id stamp-id updated-stamps) => nil)))))
