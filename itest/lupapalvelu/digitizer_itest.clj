(ns lupapalvelu.digitizer-itest
  (:require [lupapalvelu.archive.archiving :as arch]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate.verdict-interface :as vif]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [mount.core :as mount]
            [sade.core :refer [now]]
            [sade.util :as util]))

(def ^:private jarvenpaa-R-org-id "186-R")

(def VTJ-PRT-1 "122334455R")
(def VTJ-PRT-2 "199887766E")

(defonce db-name (str "test_digitizer_" (now)))

(defn archiving-project-id [propertyId kuntalupatunnus refreshBuildings & [user]]
  (let [result (command (or user digitoija) :create-archiving-project
                 :lang "fi"
                 :x "404262.00"
                 :y "6694511.00"
                 :address "Street 1"
                 :propertyId propertyId
                 :organizationId "186-R"
                 :kuntalupatunnus kuntalupatunnus
                 :createWithoutPreviousPermit true
                 :createWithoutBuildings true
                 :createWithDefaultLocation false
                 :refreshBuildings refreshBuildings)]
    (fact "Archiving project created"
      result => ok?)
    (:id result)))

(mount/start #'mongo/connection)
(mongo/with-db db-name
  (fixture/apply-fixture "minimal")
  (with-local-actions
    (fact "Update organization"
      (command jarvenpaa :set-krysp-endpoint
               :organizationId jarvenpaa-R-org-id
               :permitType "R"
               :password ""
               :username ""
               :url (str (server-address) "/dev/krysp")
               :version "2.2.2") => ok?)

    (facts "Create digitizer project with default location"

      (fact "Digitizer can NOT create project without location if no default location has been set"
        (command digitoija :create-archiving-project
                 :lang "fi"
                 :x ""
                 :y ""
                 :address ""
                 :propertyId ""
                 :organizationId "186-R"
                 :kuntalupatunnus "186-00X"
                 :createWithoutPreviousPermit true
                 :createWithoutBuildings true
                 :createWithDefaultLocation true)
        => (partial expected-failure? :error.no-default-digitalization-location))

      (fact "Admin can set default location for organization"
        (command jarvenpaa :set-default-digitalization-location :organizationId jarvenpaa-R-org-id
                 :x "404262.00" :y "6694511.00") => ok?

        (let [organization (first (:organizations (query admin :organizations)))]
          (get-in organization [:default-digitalization-location :x]) => "404262.00"
          (get-in organization [:default-digitalization-location :y]) => "6694511.00"))

      (fact "The default location must be approximately in Finland"
        (command jarvenpaa :set-default-digitalization-location :organizationId jarvenpaa-R-org-id
                 :x "0.00" :y "0.00")
        => (partial expected-failure? :error.illegal-coordinates))

      (let [response    (command digitoija :create-archiving-project
                                 :lang "fi"
                                 :x ""
                                 :y ""
                                 :address ""
                                 :propertyId ""
                                 :organizationId "186-R"
                                 :kuntalupatunnus "186-00X"
                                 :createWithoutPreviousPermit true
                                 :createWithoutBuildings false
                                 :createWithDefaultLocation true)
            app-id      (:id response)
            application (query-application digitoija app-id)]

        (fact "If default location has been set, digitizer can create project without location"
          response => ok?
          (:address application) => "Sijainti puuttuu"
          (:x (:location application)) => 404262.00
          (:y (:location application)) => 6694511.00
          (-> application :archived :initial) => (:created application)
          (:non-billable-application application) => nil
          (->> (:documents application)
               (filter #(= "archiving-project" (:name (:schema-info %))))
               first :data :kaytto
               ((fn [map]
                  [(-> map :kayttotarkoitus :value) (-> map :rakennusluokka :value)])))
          => ["039 muut asuinkerrostalot" "0110 omakotitalot"])

        (fact "Dummy verdict is published"
          (let [verdict (-> application :verdicts first)]
            verdict => truthy
            (:draft verdict) => nil
            (vif/latest-published-verdict  {:application application}) => verdict))

        (fact "buildings from building XML"
          (:buildings application) => (just [(contains {:buildingId "122334455R"
                                                        :location   [395320.093 6697384.603]})]))

        (fact "Digitizer can change location"
          (command digitoija :change-location
                   :id app-id
                   :x 400062.00
                   :y 6664511.00
                   :address "New address"
                   :propertyId "18677128000000"
                   :refreshBuildings false) => ok?
          (let [application (query-application digitoija app-id)]
            (:address application) => "New address"
            (:x (:location application)) => 400062.00
            (:y (:location application)) => 6664511.00))

        (fact "Operations, buildings and documents are changed correctly")
        (let [application               (query-application digitoija app-id)
              primary-operation-id      (get-in application [:primaryOperation :id])
              secondary-operation-id    (:id (first (:secondaryOperations application)))
              first-building-id         (:operationId (first (:buildings application)))
              second-building-id        (:operationId (second (:buildings application)))
              documents                 (domain/get-documents-by-name application "archiving-project")
              first-building-doc-op-id  (get-in (first documents) [:schema-info :op :id])
              second-building-doc-op-id (get-in (second documents) [:schema-info :op :id])]
          (= primary-operation-id first-building-id first-building-doc-op-id) => true?
          (= secondary-operation-id second-building-id second-building-doc-op-id) => true?
          (:buildings application) => (just [(contains {:buildingId "122334455R"
                                                        :location   [395320.093 6697384.603]})]))))

    (fact "When digitization-project-user creates app it has :non-billable-application set to true"
      (->> (archiving-project-id "18600101140009" "186-0009" true digitization-project-user)
           (query-application digitization-project-user)
           :non-billable-application)
      => true)

    (letfn [(building-ids [& [refresh?]]
              (->> (command digitoija :create-archiving-project
                            :lang "fi"
                            :x ""
                            :y ""
                            :address ""
                            :propertyId ""
                            :organizationId "186-R"
                            :kuntalupatunnus (str (gensym "186-00-"))
                     :createWithoutPreviousPermit true
                     :createWithoutBuildings false
                     :createWithDefaultLocation true
                     :refreshBuildings (boolean refresh?))
            :id
            (query-application digitoija)
            :buildings
            (map :nationalId)))]
      (facts "Bad buildings are ignored"
        (building-ids) => ["122334455R"]
        (building-ids true) => ["122334455R" "199887766E"]
        (building-ids) => ["199887766E"]
        (provided
          (sade.coordinate/valid-x? 395320.093) => false
          (sade.coordinate/valid-x? anything) => true)
        (building-ids true) => ["199887766E"]
        (provided
          (sade.coordinate/valid-x? 395320.093) => false
          (sade.coordinate/valid-x? anything) => true)
        (building-ids) => []
        (provided
          (sade.coordinate/valid-x? 395320.093) => false
          (sade.coordinate/valid-x? 395403.406) => false
          (sade.coordinate/valid-x? anything) => true)
        (building-ids true) => []
        (provided
          (sade.coordinate/valid-x? 395320.093) => false
          (sade.coordinate/valid-x? 395403.406) => false
          (sade.coordinate/valid-x? anything) => true)))

    (facts "Add multiple backendId and verdict dates"

      (fact "There can be multiple backendId"
        (let [app-id (archiving-project-id "18600101140005" "186-0005" true)]
          (command digitoija :store-archival-project-backend-ids :id app-id :verdicts [{:kuntalupatunnus "186-0003"} {:kuntalupatunnus "186-0004"}]) => ok?
          (let [verdict-ids         (map :id (:verdicts (query-application digitoija app-id)))
                verdict-updates     [{:id (first verdict-ids) :kuntalupatunnus "186-0003" :verdictDate 1512597600000}
                                     {:id (second verdict-ids) :kuntalupatunnus "186-0004" :verdictDate 1512594000000}]
                _                   (command digitoija :store-archival-project-backend-ids :id app-id :verdicts verdict-updates)
                {:keys [verdicts permitType]
                 :as   application} (query-application digitoija app-id)
                paatospvm           #(some-> % :paatokset first :poytakirjat first :paatospvm)]
            permitType => "ARK"
            (count verdicts) => 2
            (:kuntalupatunnus (first verdicts)) => "186-0003"
            (paatospvm (first verdicts)) => 1512597600000
            (arch/get-ark-paatospvm application {:backendId "186-0003"}) => 1512597600000
            (:kuntalupatunnus (second verdicts)) => "186-0004"
            (paatospvm (second verdicts)) => 1512594000000
            (arch/get-ark-paatospvm application {:backendId "186-0004"}) => 1512594000000
            (fact "Latest published verdict date"
              (vif/latest-published-verdict-date verdicts) => 1512597600000)))))

    (letfn [(check-ids [vtj-prt {bid :nationalId op-id :operationId} doc op]
              (fact "Check links between building, document and operation"
                vtj-prt => truthy
                bid => vtj-prt
                op-id => truthy
                (= (-> doc :schema-info :op :id) op-id (:id op)) => true
                (-> doc :data :valtakunnallinenNumero :value) => bid))
            (check-docs [a b]
              (let [op (util/fn-> :schema-info :op (select-keys [:id :created :name :description]))]
                (fact "Documents match"
                  (op a) => (op b)
                  (:data a) => (:data b)
                  (:created a) => (:created b))))
            (check-ops [a b]
              (let [op #(select-keys % [:id :created :name :description])]
                (fact "Operations match"
                  (op a) => (op b))))]

      (facts "Fetching buildings from backend"

        (fact "Creating archiving project without extra buildings"
          (let [app-id               (archiving-project-id "18600101140006" "186-0006" false)
                application          (query-application digitoija app-id)
                [b1 :as buildings]   (:buildings application)
                {b1-op :operationId} b1
                [doc1 :as arch-docs] (domain/get-documents-by-name application "archiving-project")]
            (count buildings) => 1
            (count arch-docs) => 1
            (check-ids VTJ-PRT-1 b1 doc1 (:primaryOperation application))
            (count (:secondaryOperations application)) => 0
            (fact "Update operation and document"
              (command digitoija :update-doc :id app-id
                       :collection "documents"
                       :doc (:id doc1)
                       :updates [["kuvaus" "Lorem ipsum"]]) => ok?
              (command digitoija :update-doc-identifier :id app-id
                       :doc (:id doc1)
                       :identifier "tunnus"
                       :value "HQ") => ok?
              (command digitoija :update-op-description :id app-id
                       :op-id b1-op
                       :desc "Headquarters") => ok?)

            (fact "Change location and not refresh buildings"
              (let [application (query-application digitoija app-id)
                    _           (command digitoija :change-location
                                         :id app-id
                                         :x 400062.00
                                         :y 6664511.00
                                         :address "New address"
                                         :propertyId "18677128000000"
                                         :refreshBuildings false)
                    moved       (query-application digitoija app-id)
                    ks          [:buildings :primaryOperation :secondaryOperations
                                 :documents]]
                (select-keys moved ks) => (select-keys application ks)
                (:location moved) =not=> (:location application)
                (:address moved) =not=> (:address application)))

            (facts "Change location and refresh buildings (within the same property)"
              (let [application (query-application digitoija app-id)
                    _           (command digitoija :change-location
                                         :id app-id
                                         :x 400063.00
                                         :y 6664512.00
                                         :address "Buildings"
                                         :propertyId "18677128000000"
                                         :refreshBuildings true)
                    moved       (query-application digitoija app-id)
                    a-docs      (domain/get-documents-by-name application "archiving-project")
                    m-docs      (domain/get-documents-by-name moved "archiving-project")]
                (:location moved) =not=> (:location application)
                (count a-docs) => 1
                (count m-docs) => 2
                (fact "The original building, operation, document"
                  (-> moved :buildings first) => (-> application :buildings first)
                  (check-docs (first m-docs) (first a-docs))
                  (:primaryOperation moved) => (:primaryOperation application))
                (fact "New building, operation, document"
                  (check-ids VTJ-PRT-2
                             (-> moved :buildings last)
                             (last m-docs)
                             (-> moved :secondaryOperations first)))))

            (facts "Change location and refresh buildings, one removed, one added"
              (mongo/update-by-id :applications app-id
                                  {$set {:buildings.0.nationalId                        "to-be-removed"
                                         :documents.1.data.valtakunnallinenNumero.value "to-be-removed"}})
              (let [application (query-application digitoija app-id)
                    _           (command digitoija :change-location
                                         :id app-id
                                         :x 400064.00
                                         :y 6664513.00
                                         :address "Gone"
                                         :propertyId "18677128000000"
                                         :refreshBuildings true)
                    moved       (query-application digitoija app-id)
                    a-docs      (domain/get-documents-by-name application "archiving-project")
                    m-docs      (domain/get-documents-by-name moved "archiving-project")]
                (:location moved) =not=> (:location application)
                (count a-docs) => 2
                (count m-docs) => 2
                (fact "The original building, operation, document is gone"
                  (check-docs (first m-docs) (last a-docs))
                  (check-ops (:primaryOperation moved) (-> application :secondaryOperations last))
                  (some #(= (:nationalId %) "to-be-removed") (:buildings application)) => true
                  (some #(= (-> % :data :valtakunnallinenNumero :value) "to-be-removed")
                        a-docs) => true
                  (not-any? #(= (:nationalId %) "to-be-removed") (:buildings moved)) => true
                  (not-any? #(= (-> % :data :valtakunnallinenNumero :value) "to-be-removed")
                            m-docs) => true)
                (fact "The unchanged operation is 'promoted' to the primary operation"
                  (check-ids VTJ-PRT-2
                             (->> moved :buildings (util/find-by-key :nationalId VTJ-PRT-2))
                             (first m-docs)
                             (:primaryOperation moved)))
                (fact "New building, operation, document"
                  (check-ids VTJ-PRT-1
                             (->> moved :buildings (util/find-by-key :nationalId VTJ-PRT-1))
                             (last m-docs)
                             (-> moved :secondaryOperations first)))
                (fact "Buildings with bad coordinates are ignored"
                  (command digitoija :change-location
                           :id app-id
                           :x 400065.00
                           :y 6664514.00
                           :address "One bad"
                           :propertyId "18677128000000"
                           :refreshBuildings true) => ok?
                  (provided
                    (sade.coordinate/valid-x? 395320.093) => false
                    (sade.coordinate/valid-x? anything) => true)
                  (let [{:keys [buildings documents primaryOperation]
                         :as   application} (query-application digitoija app-id)]
                    (fact "Only one building left"
                      (count buildings) => 1
                      (count documents) => 2 ;; hakija-r, archiving-project
                      (:secondaryOperations application) => []
                      (check-ids VTJ-PRT-2 (first buildings) (last documents)
                                 primaryOperation))
                    (fact "No buildings"
                      (command digitoija :change-location
                               :id app-id
                               :x 400066.00
                               :y 6664515.00
                               :address "No buildings"
                               :propertyId "18677128000000"
                               :refreshBuildings true)
                      => (partial expected-failure? "error.no-buildings-found")
                      (provided
                        (lupapalvelu.organization/get-building-wfs anything "R") => nil))
                    (fact "Only bad coordinates"
                      (command digitoija :change-location
                               :id app-id
                               :x 400067.00
                               :y 6664516.00
                               :address "No buildings"
                               :propertyId "18677128000000"
                               :refreshBuildings true)
                      => (partial expected-failure? "error.no-buildings-found")
                      (provided
                        (sade.coordinate/valid-x? 395320.093) => false
                        (sade.coordinate/valid-x? 395403.406) => false
                        (sade.coordinate/valid-x? anything) => true))
                    (fact "Application not updated"
                      (query-application digitoija app-id) => application)))))))

        (fact "Creating archiving project with all buildings from backend"
          (let [app-id              (archiving-project-id "18600101140007" "186-0007" true)
                {:keys [buildings primaryOperation secondaryOperations]
                 :as   application} (query-application digitoija app-id)
                docs                (domain/get-documents-by-name application "archiving-project")]
            (count buildings) => 2
            (count docs) => 2
            (count secondaryOperations) => 1
            (check-ids VTJ-PRT-1 (first buildings) (first docs) primaryOperation)
            (check-ids VTJ-PRT-2 (last buildings) (last docs) (first secondaryOperations))

            (fact "Change location and refresh all buildings (same buildings)"
              (let [_     (command digitoija :change-location
                                   :id app-id
                                   :x 400062.00
                                   :y 6664511.00
                                   :address "New address"
                                   :propertyId "18677128000000"
                                   :refreshBuildings true)
                    moved (query-application digitoija app-id)
                    ks    [:buildings :primaryOperation :secondaryOperations
                           :documents]]
                (select-keys moved ks) => (select-keys application ks)
                (:location moved) =not=> (:location application)
                (:address moved) =not=> (:address application)))))))

    (fact "Operation cannot be replaced unless there is at least one operation for which add-operation-allowed? is true"
      (let [app-id (archiving-project-id "18600101140007" "186-0007" true)
            op-id  (get-in (query-application digitoija app-id) [:primaryOperation :id])]
        (command digitoija :replace-operation :id app-id :opId op-id :operation "archiving-project") => {:ok false :text "error.unsupported-permit-type"}))

    (facts "Removing buildings from project"

      (fact "Remove all buildings but primary operation building"
        (let [app-id            (archiving-project-id "18600101140010" "186-0010" true)
              app-before-change (query-application digitoija app-id)
              _                 (command digitoija :remove-buildings :id app-id) => ok?
              application       (query-application digitoija app-id)]
          (count (:buildings app-before-change)) => 2
          (count (domain/get-documents-by-name app-before-change "archiving-project")) => 2
          (count (:secondaryOperations app-before-change)) => 1
          (count (:buildings application)) => 1
          (count (domain/get-documents-by-name application "archiving-project")) => 1
          (count (:secondaryOperations application)) => 0)))

    (facts "digitizing-enabled"
      (fact "Digitizer, archivist and authority-admin roles see the tab"
        (query digitoija :digitizing-enabled) => ok?
        (query raktark-jarvenpaa :digitizing-enabled) => ok?
        (query jarvenpaa :digitizing-enabled) => ok?)
      (fact "Simple authority does not"
        (query lupasihteeri-jarvenpaa :digitizing-enabled) => fail?)
      (fact "Disable digitizing in Järvenpää"
        (command admin :set-organization-boolean-attribute
                 :attribute "digitizer-tools-enabled"
                 :enabled false
                 :organizationId "186-R") => ok?
        (query digitoija :digitizing-enabled) => fail?)
      (fact "Enable digitizing and disable archive"
        (command admin :set-organization-boolean-attribute
                 :attribute "digitizer-tools-enabled"
                 :enabled true
                 :organizationId "186-R") => ok?
        (command admin :set-organization-boolean-attribute
                 :attribute "permanent-archive-enabled"
                 :enabled false
                 :organizationId "186-R") => ok?
        (query digitoija :digitizing-enabled) => fail?)
      (fact "Enable both digitizing and archive"
        (command admin :set-organization-boolean-attribute
                 :attribute "permanent-archive-enabled"
                 :enabled true
                 :organizationId "186-R") => ok?
        (query digitoija :digitizing-enabled) => ok?))

    (fact "Project cannot be created outside of the organization"
      (command digitoija :create-archiving-project
               :lang "fi"
               :x "404262.00"
               :y "6694511.00"
               :address "Sibbo Sideways"
               :propertyId sipoo-property-id
               :organizationId "186-R"
               :kuntalupatunnus "outside"
               :createWithoutPreviousPermit true
               :createWithoutBuildings true
               :createWithDefaultLocation false
               :refreshBuildings false)
      => (partial expected-failure? "error.bad-municipality"))

    (fact "Dingo is pure digitizer but Rakennustarkastaja is not"
      (query digitoija :user-is-pure-digitizer) => ok?
      (query raktark-jarvenpaa :user-is-pure-digitizer) => fail?)

    (facts "Existing backend-id check"
      (let [{old-app-id :id } (mongo/select-one :applications
                                                {:organization "186-R"
                                                 :permitType   :ARK})]
        old-app-id => truthy
        (fact "Create archiving project without kuntalupatunnus"
          (mongo/update-by-id :applications
                              old-app-id
                              {$set {:verdicts.0.kuntalupatunnus ""}})
          (let [{app-id :id} (command digitoija :create-archiving-project
                                      :lang "fi"
                                      :address "Big Church"
                                      :x "395252"
                                      :y "6705722"
                                      :propertyId jarvenpaa-property-id
                                      :organizationId "186-R"
                                      :kuntalupatunnus ""
                                      :createWithoutPreviousPermit true
                                      :createWithoutBuildings true
                                      :createWithDefaultLocation false
                                      :refreshBuildings false)]
            app-id => truthy
            app-id =not=> old-app-id))
        (fact "Clashes with non-digitized applications are allowed"
          (mongo/insert :applications
                        {:id           "LP-REGULAR"
                         :organization "186-R"
                         :permitType   :R
                         :verdicts     [{:kuntalupatunnus "hello"}]})
          (let [{app-id :id} (command digitoija :create-archiving-project
                                      :lang "fi"
                                      :address "Big Church"
                                      :x "395252"
                                      :y "6705722"
                                      :propertyId jarvenpaa-property-id
                                      :organizationId "186-R"
                                      :kuntalupatunnus "hello"
                                      :createWithoutPreviousPermit true
                                      :createWithoutBuildings true
                                      :createWithDefaultLocation false
                                      :refreshBuildings false)]
            app-id => truthy
            app-id =not=> "LP-REGULAR"))
        (fact "Clash with an earlier digitized application -> return it"
          (mongo/update-by-id :applications
                              old-app-id
                              {$set {:verdicts.0.kuntalupatunnus "world"}})
          (let [{app-id :id} (command digitoija :create-archiving-project
                                      :lang "fi"
                                      :address "Big Church"
                                      :x "395252"
                                      :y "6705722"
                                      :propertyId jarvenpaa-property-id
                                      :organizationId "186-R"
                                      :kuntalupatunnus "  world "
                                      :createWithoutPreviousPermit true
                                      :createWithoutBuildings true
                                      :createWithDefaultLocation false
                                      :refreshBuildings false)]
            app-id => old-app-id))))))
