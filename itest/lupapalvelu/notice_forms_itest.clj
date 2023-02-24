(ns lupapalvelu.notice-forms-itest
  (:require [lupapalvelu.i18n :as i18n]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer [err]]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [lupapalvelu.test-util :refer [in-text]]
            [midje.sweet :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]))

(apply-remote-minimal)

(defn check-sipoo-notice-forms [check]
  (fact "Sipoo (753-R) notice-forms"
    (some-> (query sipoo :organization-by-user
                  :organizationId "753-R")
           :organization
           :notice-forms) => check))

(defn application-building-ids [app-id]
  (->> (query-application pena app-id)
       :buildings
       (map :buildingId)))

(defn delete-open-notices [app-id]
  (fact "Delete every open notice"
    (doseq [{form-id :id} (->> (query-application pena app-id)
                               :notice-forms
                               (filter (util/fn-> :history last :state (= "open"))))]
      (command pena :delete-notice-form :id app-id :formId form-id ) => ok?)))

(defn notice-forms-cleared [app-id]
  (let [{:keys [state notice-forms attachments]} (query-application sonja app-id)]
        (fact "Application state has been rewound"
          state => "submitted")
        (fact "There are no more notice forms"
          notice-forms => empty?)
        (fact "... nor form attachments"
          (filter (util/fn-> :target :type (= "notice-form"))
                  attachments)
          => empty?)
        (fact "... nor assignments"
          (:assignments (query sonja :assignments-for-application :id app-id))
          => empty?)))

(defn form-assignments [app-id form-id]
  (->> (query sonja :assignments-for-application
              :id app-id)
       :assignments
       (filter (fn [{:keys [targets]}]
                 (util/find-by-id form-id
                                  targets)))))

(defn check-email [app-id address & extras]
  (let [randy (last-email)]
    randy => (contains {:subject (str "Lupapiste: " address ", Sipoo. Ilmoitus tehty.")
                        :to      "randy.random@example.com"})
    (apply in-text (-> randy :body :html
                       (ss/replace "\n" " ")
                       (ss/replace #" \s+" " "))
           (concat ["Every form" "Looksie!" app-id address
                    "Sipoo" (str "/app/fi/applicant#!/application/" app-id
                                 "/tasks")]
                   extras))))

(fact "Make Laura a proper authority"
          (command sipoo :update-user-roles
                   :email (email-for-key laura)
                   :organizationId "753-R"
                   :roles ["biller" "authority"]) => ok?)

(fact "753-R authorities"
  (:authorities (query sipoo :automatic-assignment-authorities
                       :organizationId "753-R"))
  => (just [{:id "777777777777777777000023" :firstName "Sonja" :lastName "Sibbo"}
            {:id "777777777777777777000024" :firstName "Ronja" :lastName  "Sibbo"}
            {:id "laura-laskuttaja" :firstName "Laura" :lastName  "Laskuttaja"}]
           :in-any-order))

(facts "Notice forms in organization 753-R"
  (fact "Organization must have R scope"
    (query sipoo-ya :notice-forms-supported
           :organizationId "753-YA")
    => (err :error.no-R-scope)
    (command sipoo-ya :toggle-organization-notice-form
             :organizationId "753-YA"
             :type "construction"
             :enabled true)
    => (err :error.no-R-scope))
  (fact "Notice forms supported in 753-R"
    (query sipoo :notice-forms-supported
           :organizationId "753-R")
    => ok?)
  (fact "Bad form type"
    (command sipoo :toggle-organization-notice-form
             :organizationId "753-R"
             :type "bad"
             :enabled true)
    => fail?)
  (fact "Notice form text cannot be set if form not enabled"
    (command sipoo :set-organization-notice-form-text
             :organizationId "753-R"
             :lang "fi"
             :type "construction"
             :text "Hei")
    => (err :error.notice-form-not-enabled))
  (fact "... likewise for integration"
    (command sipoo :toggle-organization-notice-form-integration
             :organizationId "753-R"
             :type "construction"
             :enabled true)
    => (err :error.notice-form-not-enabled))
  (fact "Enable construction notice form"
    (command sipoo :toggle-organization-notice-form
             :organizationId "753-R"
             :type "construction"
             :enabled true) => ok?
    (command sipoo :set-organization-notice-form-text
             :organizationId "753-R"
             :lang "fi"
             :type "construction"
             :text "Hei") => ok?
    (command sipoo :toggle-organization-notice-form-integration
             :organizationId "753-R"
             :type "construction"
             :enabled true) => ok?
    (check-sipoo-notice-forms {:construction {:enabled     true
                                              :text        {:fi "Hei"}
                                              :integration true}}))
  (fact "Integration possible only for construction form"
    (command sipoo :toggle-organization-notice-form-integration
             :organizationId "753-R"
             :type "terrain"
             :enabled true)
    => (err :error.illegal-value:not-in-set)
    (command sipoo :toggle-organization-notice-form-integration
             :organizationId "753-R"
             :type "location"
             :enabled true)
    => (err :error.illegal-value:not-in-set))
  (fact "Terrain form still not enabled"
    (command sipoo :set-organization-notice-form-text
             :organizationId "753-R"
             :lang "fi"
             :type "terrain"
             :text "Hei")
    => (err :error.notice-form-not-enabled))

  (fact "Bad language for form text"
    (command sipoo :set-organization-notice-form-text
             :organizationId "753-R"
             :lang "bad"
             :type "construction"
             :text "Hei") => fail?))

(defn add-operation [apikey app-id operation description]
  (fact {:midje/description (str "Add operation: " operation)}
    (command apikey :add-operation :id app-id
             :operation operation) => ok?)

  (let [{ops :secondaryOperations} (query-application apikey app-id)]
    (fact {:midje/description (str "Description for " operation ": " description)}
      (command apikey :update-op-description :id app-id
               :op-id (-> ops last :id)
               :desc description) => ok?)))

(def upsert-filter (partial upsert-automatic-assignment-filter sipoo "753-R"))
(def filter-map #(automatic-assignment-filter-ids sipoo "753-R"))

(let [{app-id :id} (create-and-submit-application pena
                                                  :propertyId sipoo-property-id
                                                  :operation "pientalo"
                                                  :address "Rue de Invoice Form")
      pena-summary {:firstName "Pena"
                    :lastName  "Panaani"
                    :id        pena-id
                    :role      "applicant"
                    :username  "pena"}]

  (add-operation pena app-id "vapaa-ajan-asuinrakennus" "Sauna")
  (add-operation pena app-id "varasto-tms" "Bikeshed")
  (fact "Check for verdict"
    (command sonja :check-for-verdict :id app-id) => ok?)
  (fact "Fake buildings"
    (fake-buildings app-id) => true)

  (fact "Application has three fake buildings"
    (:buildings (query-application sonja app-id))
    => (just [(contains {:buildingId  "building1"
                         :index       "1"
                         :nationalId  "VTJ-PRT-1"
                         :description nil})
              (contains {:buildingId  "building2"
                         :index       "2"
                         :nationalId  "VTJ-PRT-2"
                         :description "Sauna"})
              (contains {:buildingId  "building3"
                         :index       "3"
                         :nationalId  "VTJ-PRT-3"
                         :description "Bikeshed"})]))
  (facts "Notice data"
    (fact "Form not enabled"
      (query pena :new-notice-data :id app-id :type "terrain")
      => (err :error.notice-form-not-enabled))
    (fact "No foreman"
      (query pena :new-notice-data :id app-id :type "construction")
      => (err :error.no-suitable-foreman))
    (fact "Enable terrain form"
      (command sipoo :toggle-organization-notice-form
               :organizationId "753-R"
               :type "location"
               :enabled true) => ok?)
    (fact "Location form no longer dependent on the terrain forms"
      (query pena :new-notice-data :id app-id :type "location")
      => ok?))
  (let [{role-id :id} (command sipoo :upsert-handler-role
                               :organizationId "753-R"
                               :name {:fi "AVI-k\u00e4sittelij\u00e4"
                                      :sv "AVI-handl\u00e4ggare"
                                      :en "Construction Handler"})
        filter-id     (upsert-filter {:name     "Automata"
                                      :criteria {:notice-forms ["construction"]}
                                      :target   {:handler-role-id role-id}})
        tj-app-id     (create-foreman-application app-id pena mikko-id
                                                  "vastaava työnjohtaja" "A")]

    (fact "Foreman application created"
      tj-app-id => truthy)
    (fact "Construction form notice data fails"
      (query pena :new-notice-data
             :id app-id
             :type "construction")
      = (err :error.no-suitable-foreman))
    (fact "Submit foreman application"
      (command pena :change-permit-sub-type :id tj-app-id
               :permitSubtype "tyonjohtaja-hakemus") => ok?
      (command pena :submit-application :id tj-app-id) => ok?)
    (fact "Construction form notice data now OK"
      (query pena :new-notice-data
             :id app-id
             :type "construction")
      => (just {:ok        true
                :info-text "Hei"
                :buildings seq
                :foremen   [{:status     "new"
                             :stateLoc   "submitted"
                             :foremanLoc (name (util/kw-path :osapuoli.tyonjohtaja.kuntaRoolikoodi
                                                             "vastaava työnjohtaja"))
                             :fullname   ""}]}))
    (fact "Notice data for English"
      (:info-text (query pena :new-notice-data
                         :id app-id
                         :lang "en"
                         :type "construction")) => nil
      (command sipoo :set-organization-notice-form-text
               :organizationId "753-R"
               :type "construction"
               :lang "en"
               :text "Hello") => ok?
      (:info-text (query pena :new-notice-data
                         :id app-id
                         :lang "en"
                         :type "construction")) => "Hello" )
    (fact "Upload file"
      (let [filedatas (:files (upload-file pena "dev-resources/test-attachment.txt"))
            build-ids (->> (query pena :new-notice-data
                                  :id app-id
                                  :type "construction")
                           :buildings
                           (map :buildingId))]
        (fact "New construction notice form"
          (command pena :new-construction-notice-form :id app-id
                   :text "  Hello  "
                   :buildingIds [(first build-ids)]
                   :filedatas filedatas)
          => ok?)
        (fact "Form created"
          (let [{:keys [notice-forms
                        attachments
                        buildings]} (query-application pena app-id)
                form                (last notice-forms)
                form-ts             (-> form :history first :timestamp)]
            notice-forms => (just [(contains {:text        "Hello"
                                              :buildingIds [(first build-ids)]
                                              :type        "construction"
                                              :history     (just [(contains {:state     "open"
                                                                             :timestamp pos?
                                                                             :user      pena-summary})])})])
            (fact "Attachment created"
              (last attachments) => (contains {:contents      "Ilmoitus aloitusvalmiudesta"
                                               :target        {:id   (:id form)
                                                               :type "notice-form"}
                                               :latestVersion (contains {:fileId string?})
                                               :type          {:type-group "muut" :type-id "muu"}}))
            (fact "New assignment created"
              (:assignments (query sonja :assignments-for-application :id app-id))
              => (just [(contains {:application {:id           app-id
                                                 :organization "753-R"
                                                 :address      "Rue de Invoice Form"
                                                 :municipality "753"}
                                   :trigger     "notice-form"
                                   :targets     [{:group     "notice-forms-construction"
                                                  :id        (:id form)
                                                  :timestamp form-ts}]
                                   :recipient   {:roleId role-id}
                                   :status      "active"
                                   :states      [{:type      "created"
                                                  :user      pena-summary
                                                  :timestamp form-ts}]
                                   :description "Automata"
                                   :modified    form-ts
                                   :filter-id   filter-id})]))
            (fact "Language restrictions for notice forms query"
              (query pena :notice-forms :id app-id :lang "cn")
              => (err :error.unsupported-language))
            (fact "Notice forms query"
              (:noticeForms (query pena :notice-forms :id app-id :lang "fi"))
              => (just [(contains {:id          (:id form)
                                   :text        "Hello"
                                   :buildings   [(str (i18n/localize :fi "operations.pientalo")
                                                      " - "
                                                      (-> buildings first :nationalId))]
                                   :type        "construction"
                                   :attachments (just [(contains {:filename    "test-attachment.pdf"
                                                                  :contentType "application/pdf"
                                                                  :version     {:major 1 :minor 0}})])})]))))))

    (fact "Enable terrain forms in Sipoo"
      (command sipoo :toggle-organization-notice-form
               :organizationId "753-R"
               :type "terrain"
               :enabled true) => ok?
      (command sipoo :set-organization-notice-form-text
               :organizationId "753-R"
               :lang "fi"
               :type "terrain"
               :text "Terve"))

    (fact "Notice forms not supported for foreman applications"
      (command sonja :check-for-verdict
               :id tj-app-id) => ok?
      (query pena :new-notice-data
             :id tj-app-id
             :type "terrain")
      => (err :error.unsupported-primary-operation))
    (facts "Create another construction form"
      (let [build-ids (application-building-ids app-id)]
        (fact "Notice data has only two buildings"
          (let [{:keys [buildings]} (query pena :new-notice-data :id app-id
                                           :type "construction")]
            (count buildings) => 2
            (util/difference-as-kw build-ids (map :buildingId buildings))
            => [(keyword (first build-ids))]
            (fact "There cannot be two open forms for the same type and building"
              (command pena :new-construction-notice-form :id app-id
                       :text "hii"
                       :buildingIds build-ids)
              => (err :error.buildings-not-available)
              (command pena :new-construction-notice-form :id app-id
                       :text "hii"
                       :buildingIds [(first build-ids)])
              => (err :error.buildings-not-available))))
        (fact "New construction form for the remaining buildings"
          (let [{form-id :form-id} (command pena :new-construction-notice-form :id app-id
                                            :text "Second form"
                                            :buildingIds (rest build-ids))]
            (fact "New form created"
              (let [[one two
                     :as notice-forms] (:notice-forms (query-application pena app-id))
                    form-ts            (util/fn-> :history last :timestamp)]
                notice-forms => (just [map? (contains {:id          form-id
                                                       :text        "Second form"
                                                       :buildingIds (rest build-ids)
                                                       :type        "construction"})])
                (fact "The assignment has been updated"
                  (:assignments (query sonja :assignments-for-application :id app-id))
                  => (just [(contains {:application {:id           app-id
                                                     :organization "753-R"
                                                     :address      "Rue de Invoice Form"
                                                     :municipality "753"}
                                       :trigger     "notice-form"
                                       :targets     [{:group     "notice-forms-construction"
                                                      :id        (:id one)
                                                      :timestamp (form-ts one)}
                                                     {:group     "notice-forms-construction"
                                                      :id        (:id two)
                                                      :timestamp (form-ts two)}]
                                       :recipient   {:roleId role-id}
                                       :status      "active"
                                       :states      [{:type      "created"
                                                      :user      pena-summary
                                                      :timestamp (form-ts one)}
                                                     {:type      "targets-added"
                                                      :user      pena-summary
                                                      :timestamp (form-ts two)}]
                                       :description "Automata"
                                       :modified    (form-ts two)
                                       :filter-id   filter-id})])))))))))
  (fact "No more buildings left for another construction form"
    (query pena :new-notice-data :id app-id
           :type "construction")
    => (contains {:buildings empty?}))
  (fact "Enable archive in Sipoo"
    (command admin :set-organization-boolean-attribute
             :organizationId "753-R"
             :attribute "permanent-archive-enabled"
             :enabled true) => ok?
    (query pena :new-notice-data :id app-id :type "terrain")
    => ok?)

  (fact "Terrain form notice data contains prefilled customer information"
      (query pena :new-notice-data
             :id app-id
             :type "terrain")
      => (just {:ok        true
                :info-text "Terve"
                :buildings seq
                :customer  {:name  "Pena Panaani"
                            :email "pena@example.com"
                            :phone "0102030405"
                            :payer {:permitPayer true
                                    :name        "Pena Panaani"
                                    :street      "Paapankuja 12"
                                    :zip         "10203"
                                    :city        "Piippola"}}}))

  (fact "Create new terrain form"
    (let [filedatas (:files (upload-file pena "dev-resources/test-attachment.txt"))
          build-id  (first (application-building-ids app-id))
          form-id   (:form-id (command pena :new-terrain-notice-form :id app-id
                                       :text " Terrain "
                                       :buildingIds [build-id]
                                       :filedatas filedatas
                                       :customer {:name  "  Pena Alias  "
                                                  :email (str " " (email-for "pena") " ")
                                                  :phone "  12345678  "
                                                  :payer {:name       "  Terraform Inc.  "
                                                          :street     "  Rue de Notice  "
                                                          :zip        "98765"
                                                          :city       "  Humppila  "
                                                          :identifier "4874657-3"}}))]
      form-id => truthy
      (fact "Form data is trimmed"
        (->> (query pena :notice-forms :id app-id :lang "sv")
             :noticeForms
             (util/find-by-id form-id))
        => (just {:id          form-id
                  :type        "terrain"
                  :text        "Terrain"
                  :buildings   seq
                  :attachments seq
                  :customer    {:name  "Pena Alias" :email "pena@example.com" :phone "12345678"
                                :payer {:name       "Terraform Inc." :street "Rue de Notice"
                                        :zip        "98765"          :city   "Humppila"
                                        :identifier "4874657-3"}}
                  :status      (just {:fullname "Pena Panaani" :state "open" :timestamp pos?})}))
      (fact "Assignment not created"
        (->> (query sonja :assignments-for-application :id app-id)
             :assignments
             (mapcat :targets)
             (some (util/fn-> :group (= "notice-forms-terrain")))) => falsey)
      (fact "Attachment created and converted to PDF"
        (let [attachment (-> (query-application pena app-id)
                             :attachments last)]
          attachment => (contains {:contents      "Maastoonmerkinn\u00e4n tilaaminen"
                                   :target        {:id   form-id
                                                   :type "notice-form"}
                                   :latestVersion (contains {:fileId string?})
                                   :type          {:type-group "muut" :type-id "muu"}})))))

  (fact "Disable archive in Sipoo"
    (command admin :set-organization-boolean-attribute
             :organizationId "753-R"
             :attribute "permanent-archive-enabled"
             :enabled false) => ok?
    (query pena :new-notice-data :id app-id :type "terrain")
    => ok?)

  (fact "Delete the first construction form"
    (let [form-id (some-> (query-application pena app-id)
                          :notice-forms
                          first
                          :id)]
      (command pena :delete-notice-form :id app-id :formId form-id)
      => ok?
      (let [{:keys [notice-forms attachments]} (query-application pena app-id)]
        (fact "The form is gone"
          (util/find-by-id form-id notice-forms) => nil)
        (fact "... as is its attachment"
          (not-any? (util/fn-> :target :id (= form-id)) attachments)
          => true)
        (fact "... and assignment targets"
          (->> (query sonja :assignments-for-application :id app-id)
               :assignments
               (mapcat :targets)
               (util/find-by-id form-id)) => nil)
        (fact "Cannot be deleted twice"
          (command pena :delete-notice-form :id app-id :formId form-id)
          => (err :error.notice-form-not-found)))))

  (fact "Modify the filter to match every form. Add email notification."
    (upsert-filter {:id       (get (filter-map) "Automata")
                    :name     "Every form"
                    :criteria {:notice-forms ["construction" "terrain" "location"]}
                    :email    {:emails  ["randy.random@example.com"]
                               :message "Looksie!"}}))

  (fact "Create new terrain form with attachment (and assignment)"
    (let [filedatas (:files (upload-file pena "dev-resources/test-attachment.txt"))
          build-id  (second (application-building-ids app-id))
          form-id   (:form-id (command pena :new-terrain-notice-form :id app-id
                                       :text "Terrain Two"
                                       :buildingIds [build-id]
                                       :filedatas filedatas
                                       :customer {:name  "Pena Panaani"
                                                  :email (email-for "pena")
                                                  :phone "12345678"
                                                  :payer {:permitPayer true}}))]
      form-id => truthy
      (fact "Assignment created"
        (form-assignments app-id form-id)
        => (just [(contains {:description "Every form"})]))
      (fact "Attachment created"
        (let [attachment (-> (query-application pena app-id)
                             :attachments last)]
          attachment => (contains {:contents      "Maastoonmerkinn\u00e4n tilaaminen"
                                   :target        {:id   form-id
                                                   :type "notice-form"}
                                   :latestVersion (contains {:fileId string?})
                                   :type          {:type-group "muut" :type-id "muu"}})))
      (check-email app-id "Rue de Invoice Form" "Maastoonmerkinnän tilaaminen" "Terrain Two"
                   (str (i18n/localize :fi :operations.vapaa-ajan-asuinrakennus) " - Sauna - VTJ-PRT-2")
                   "Pena Panaani, pena@example.com, 12345678")))

  (fact "Approve the latest terrain form"
    (let [form-id (some-> (query-application pena app-id)
                          :notice-forms
                          last
                          :id)]
      (command sonja :approve-notice-form :id app-id
               :formId form-id)
      => ok?
      (let [{:keys [notice-forms attachments]} (query-application pena app-id)]
        (fact "The form is approved"
          (let [{:keys [modified] :as form} (util/find-by-id form-id notice-forms)]
            form  => (contains {:modified modified
                                :history  (just [(contains {:state "open"})
                                                 (contains {:state     "ok"
                                                            :timestamp modified})])})
            (fact "... as is its attachment"
              (let [{:keys [approvals
                            latestVersion]} (util/find-first (util/fn-> :target :id (= form-id))
                                                             attachments)]
                (get approvals (-> latestVersion :originalFileId keyword))
                => (contains {:state     "ok"
                              :timestamp modified})))
            (fact "... and assignment targets"
              (->> (query sonja :assignments-for-application :id app-id)
                   :assignments
                   (mapcat :targets)
                   (util/find-by-id form-id)) => nil)
            (facts "Location form"
              (fact "Notice data only includes the building approved in the terrain form"
                (->> (query pena :new-notice-data
                            :id app-id
                            :type "location")
                     :buildings
                     (map :buildingId))
                => (:buildingIds form)))))
        (fact "Approved form cannot be deleted"
          (command pena :delete-notice-form :id app-id
                   :formId form-id)
          => (err :error.notice-form-in-wrong-state))
        (fact "... and cannot be reapproved"
          (command sonja :approve-notice-form :id app-id
                   :formId form-id)
          => (err :error.notice-form-in-wrong-state))
        (fact "... but can be rejected"
          (command sonja :reject-notice-form :id app-id
                   :formId form-id
                   :info "Baad from")
          => ok?)
        (fact "... and rerejected"
          (command sonja :reject-notice-form :id app-id
                   :formId form-id
                   :info "Bad form")
          => ok?)
        (let [{:keys [notice-forms attachments]} (query-application pena app-id)]
          (fact "The form is rejected"
            (let [{:keys [modified] :as form} (util/find-by-id form-id notice-forms)]
              form  => (contains {:modified modified
                                  :history  (just [(contains {:state "open"})
                                                   (contains {:state "ok"})
                                                   (contains {:state "rejected"
                                                              :info  "Baad from"})
                                                   (contains {:state     "rejected"
                                                              :info      "Bad form"
                                                              :timestamp modified})])})
              (fact "... as is its attachment"
                (let [{:keys [approvals
                              latestVersion]} (util/find-first (util/fn-> :target :id (= form-id))
                                                               attachments)]
                  (get approvals (-> latestVersion :originalFileId keyword))
                  => (contains {:state     "requires_user_action"
                                :timestamp modified})))))
          (fact "Rejected form cannot be deleted"
            (command pena :delete-notice-form :id app-id
                     :formId form-id)
            => (err :error.notice-form-in-wrong-state)))
        (fact "Notice-forms result has the latest status"
          (->> (query sonja :notice-forms :id app-id :lang "en")
               :noticeForms
               (util/find-by-id form-id))
          => (contains {:id     form-id
                        :status (contains {:state "rejected"
                                           :info  "Bad form"})})))))

  (fact "Approval/rejection makes form buildings available"
    (let [filedatas (:files (upload-file pena "dev-resources/test-attachment.txt"))
          build-id  (second (application-building-ids app-id))
          form-id   (:form-id (command pena :new-terrain-notice-form :id app-id
                                       :text "Terrain Two"
                                       :buildingIds [build-id]
                                       :filedatas filedatas
                                       :customer {:name  "Pena Panaani"
                                                  :email (email-for "pena")
                                                  :phone "12345678"
                                                  :payer {:permitPayer true}}))]
      form-id => truthy
      (fact "Assignment created"
        (form-assignments app-id form-id)
        => (just (contains {:trigger   "notice-form"
                            :recipient nil
                            :filter-id (get (filter-map) "Every form")})))
      (fact "Attachment created"
        (let [attachment (-> (query-application pena app-id)
                             :attachments last)]
          attachment => (contains {:contents      "Maastoonmerkinn\u00e4n tilaaminen"
                                   :target        {:id   form-id
                                                   :type "notice-form"}
                                   :latestVersion (contains {:fileId string?})
                                   :type          {:type-group "muut" :type-id "muu"}})))))

  (delete-open-notices app-id)

  (facts "Handlers"
    (fact "Add a terrain handler role to Sipoo"
      (command sipoo :upsert-handler-role
               :organizationId "753-R"
               :name {:fi "Maastok\u00e4sittelij\u00e4"
                      :sv "Skoghandl\u00e4ggare"
                      :en "Terrain Handler"}) => ok?)
    (let [app-handler-roles          (:handlerRoles (query sonja :application-organization-handler-roles
                                                           :id app-id))
          {construction-role-id :id} (util/find-first (util/fn->> :name :en (re-find #"Construction"))
                                                      app-handler-roles)
          {terrain-role-id :id}      (util/find-first (util/fn->> :name :en (re-find #"Terrain"))
                                                      app-handler-roles)]
      terrain-role-id => truthy
      (fact "Automatic assignment filters for construction and terrain forms in Sipoo"
        (upsert-filter {:name     "Construction"
                        :criteria {:notice-forms ["construction"]}
                        :target   {:handler-role-id construction-role-id}})
        (upsert-filter {:name     "Terrain"
                        :criteria {:notice-forms ["terrain"]}
                        :target   {:handler-role-id terrain-role-id}}))

      (fact "Make Ronja the construction handler in the application"
        (command sonja :upsert-application-handler :id app-id
                 :roleId construction-role-id
                 :userId ronja-id) => ok?)
      (fact "New construction and terrain forms"
        (let [build-ids                  (application-building-ids app-id)
              {construction-id :form-id} (command pena :new-construction-notice-form
                                                  :id app-id
                                                  :buildingIds [(first build-ids)]
                                                  :text "Construction")
              _                          (check-email app-id "Rue de Invoice Form"
                                                      "Ilmoitus aloitusvalmiudesta"
                                                      "Construction"
                                                      (str (i18n/localize :fi :operations.pientalo)
                                                           " - VTJ-PRT-1")
                                                      ;; No customer in construction form
                                                      ["ilaajan tiedot" "Pena Panaani"
                                                       (email-for-key pena) "12345678"])
              {terrain-id :form-id}      (command pena :new-terrain-notice-form
                                                  :id app-id
                                                  :buildingIds [(first build-ids)]
                                                  :text "Terrain"
                                                  :customer {:name  "Pena Panaani"
                                                             :email (email-for "pena")
                                                             :phone "12345678"
                                                             :payer {:permitPayer true}})
              sipoo-filters              (filter-map)]
          construction-id => truthy
          terrain-id => truthy
          (form-assignments app-id construction-id)
          => (just [(contains {:filter-id   (get sipoo-filters "Every form")
                               :description "Every form"
                               :recipient   nil})
                    (contains {:filter-id (get sipoo-filters "Construction")
                               :recipient (contains {:firstName "Ronja"
                                                     :lastName  "Sibbo"})})]
                   :in-any-order)
          (fact "New terrain form assignment has only role id as recipient"
            (form-assignments app-id terrain-id)
            => (just [(contains {:filter-id   (get sipoo-filters "Every form")
                                 :description "Every form"
                                 :recipient   nil})
                      (contains {:filter-id   (get sipoo-filters "Terrain")
                                 :description "Terrain"
                                 :recipient   {:roleId terrain-role-id}})]
                     :in-any-order))
          (fact "Make Laura the terrain handler in the application"
            (command sonja :upsert-application-handler :id app-id
                     :roleId terrain-role-id
                     :userId laura-id) => ok?)
          (fact "Upon creating new terrain form, the assignment's recipient is Laura"
            (let [{:keys [form-id]} (command pena :new-terrain-notice-form
                                             :id app-id
                                             :buildingIds [(second build-ids)]
                                             :text "Terrain Two"
                                             :customer {:name  "Pena Panaani"
                                                        :email (email-for "pena")
                                                        :phone "12345678"
                                                        :payer {:permitPayer true}})]
              (form-assignments app-id form-id)
              => (just [(contains {:filter-id   (get sipoo-filters "Every form")
                                   :description "Every form"
                                   :recipient   nil})
                        (contains {:filter-id   (get sipoo-filters "Terrain")
                                   :description "Terrain"
                                   :recipient   (contains {:firstName "Laura"
                                                           :lastName  "Laskuttaja"
                                                           :roleId    terrain-role-id})
                                   :states      (just [(contains {:type "created"})
                                                       (contains {:type "targets-added"})])})]
                       :in-any-order)))
          (fact "Remove construction form application handler (Ronja)"
            (let [{handler-id :id} (->> (query sonja :application-handlers :id app-id)
                                        :handlers
                                        (util/find-by-key :roleId construction-role-id))]
              handler-id => truthy
              (command sonja :remove-application-handler :id app-id
                       :handlerId handler-id)) => ok?)
          (fact "Assignment's recipient is also cleared"
            (form-assignments app-id construction-id)
            => (just [(contains {:filter-id   (get sipoo-filters "Every form")
                                 :description "Every form"
                                 :recipient   nil
                                 :states      (just [(contains {:type "created"})])})
                      (contains {:filter-id   (get sipoo-filters "Construction")
                                 :description "Construction"
                                 :recipient   (contains {:roleId construction-role-id})
                                 :states      (just [(contains {:type "created"})])})]
                     :in-any-order))
          (fact "Set Sonja as the construction form automatic assignee for pientalo operations with a higher rank."
            (upsert-filter {:name     "Small mansion"
                            :criteria {:operations   ["pientalo"]
                                       :notice-forms ["construction"]}
                            :rank     2
                            :target   {:user-id sonja-id}}))
          (fact "Upon creating new construction form, the assignment's recipient is Sonja without handler role id."
            (let [{:keys [form-id]} (command pena :new-construction-notice-form
                                             :id app-id
                                             :buildingIds [(second build-ids)]
                                             :text "Construction Two")]
              (form-assignments app-id form-id)
              => (just [(contains {:filter-id   (get (filter-map) "Small mansion")
                                   :description "Small mansion"
                                   :recipient   {:firstName "Sonja"
                                                 :id        sonja-id
                                                 :lastName  "Sibbo"
                                                 :role      "authority"
                                                 :username  "sonja"}
                                   :states      (just [(contains {:type "created"})])})])))))))

  (facts "Cleanup notice forms when the last verdict is deleted"
    (let [[verdict-id1 verdict-id2] (->> (query-application sonja app-id)
                                         :verdicts
                                         (map :id))]
      (fact "There are two verdicts"
        verdict-id1 => truthy
        verdict-id2 => truthy)
      (fact "Delete the first verdict"
        (command sonja :delete-verdict :id app-id
                 :verdict-id verdict-id1) => ok?)
      (let [{:keys [notice-forms attachments]} (query-application sonja app-id)]
        (fact "There are still notice forms"
          (seq notice-forms) => truthy)
        (fact "and form attachments"
          (seq (filter (util/fn-> :target :type (= "notice-form"))
                       attachments))
          => truthy))
      (fact "Delete the second verdict"
        (command sonja :delete-verdict :id app-id
                 :verdict-id verdict-id2) => ok?)
      (notice-forms-cleared app-id))
    (fact "Give new legacy verdict"
      (let [verdict-id (give-legacy-verdict sonja app-id)]
        verdict-id => truthy
        (fake-buildings app-id)
        (fact "Create new terrain form with attachment (and assignment)"
          (let [filedatas (:files (upload-file pena "dev-resources/test-attachment.txt"))
                build-id  (second (application-building-ids app-id))
                form-id   (:form-id (command pena :new-terrain-notice-form :id app-id
                                             :text "Terrain Two"
                                             :buildingIds [build-id]
                                             :filedatas filedatas
                                             :customer {:name  "Pena Panaani"
                                                        :email (email-for "pena")
                                                        :phone "12345678"
                                                        :payer {:permitPayer true}}))]
            form-id => truthy
            (fact "Assignment created"
              (form-assignments app-id form-id)
              => (just [(contains {:description "Every form"})
                        (contains {:description "Terrain"})]
                       :in-any-order) => truthy)
            (fact "Attachment created"
              (let [attachment (-> (query-application pena app-id)
                                   :attachments last)]
                attachment => (contains {:contents "Maastoonmerkinn\u00e4n tilaaminen"
                                         :target   {:id   form-id
                                                    :type "notice-form"}})))))
        (fact "Delete legacy verdict"
          (command sonja :delete-pate-verdict :id app-id
                   :verdict-id verdict-id) => ok?)
        (notice-forms-cleared app-id)))))

(facts "Primary operation limits"
  (let [{app-id :id} (create-and-submit-application pena
                                                    :propertyId sipoo-property-id
                                                    :operation "sisatila-muutos"
                                                    :address "Rue de Construction")]
    (fact "Fetch verdict"
      (command sonja :check-for-verdict :id app-id) => ok?)
    (fake-buildings app-id)
    (fact "Create foreman application"
      (let [tj-id (create-foreman-application app-id pena mikko-id
                                              "vastaava ty\u00f6njohtaja"
                                              "A")]
        (finalize-foreman-app pena sonja tj-id true)))

    (fact "Only construction form is allowed"
      (:actions (query pena :allowed-actions :id app-id))
      => (contains {:new-construction-notice-form {:ok true}
                    :new-terrain-notice-form      {:ok   false
                                                   :text "error.unsupported-primary-operation"}
                    :new-location-notice-form     {:ok   false
                                                   :text "error.unsupported-primary-operation"}}))
    (fact "Disable assignments in Sipoo"
      (command sipoo :set-organization-assignments
               :organizationId "753-R"
               :enabled false) => ok?)

    (fact "New notice form does not create assignment"
      (let [{form-id :form-id} (command pena :new-construction-notice-form :id app-id
                                        :buildingIds []
                                        :text "Hello")]
        form-id => truthy
        (form-assignments app-id form-id) => empty?))

    (fact "Enable assignments in Sipoo"
      (command sipoo :set-organization-assignments
               :organizationId "753-R"
               :enabled true) => ok?)

    (fact "Now new notice form creates assignments"
      (let [{form-id :form-id} (command pena :new-construction-notice-form :id app-id
                                        :buildingIds ["building1"]
                                        :text "World")]
        form-id => truthy
        (form-assignments app-id form-id)
        => (just [(contains {:description "Every form"})
                  (contains {:description "Construction"})]
                 :in-any-order)))))
