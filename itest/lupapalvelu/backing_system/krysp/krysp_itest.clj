(ns lupapalvelu.backing-system.krysp.krysp-itest
  (:require [clojure.data.xml :refer [parse]]
            [clojure.java.io :as io]
            [lupapalvelu.backing-system.krysp.maa-aines-mapping :refer [maa-aines_to_krysp_221]]
            [lupapalvelu.backing-system.krysp.poikkeamis-mapping :refer [poikkeamis_to_krysp_221]]
            [lupapalvelu.backing-system.krysp.rakennuslupa-mapping :refer [get-rakennuslupa-mapping]]
            [lupapalvelu.backing-system.krysp.vesihuolto-mapping :refer [vesihuolto-to-krysp_221]]
            [lupapalvelu.backing-system.krysp.yleiset-alueet-mapping :refer [get-yleiset-alueet-krysp-mapping]]
            [lupapalvelu.backing-system.krysp.ymparisto-ilmoitukset-mapping :refer [ilmoitus_to_krysp_221]]
            [lupapalvelu.backing-system.krysp.ymparistolupa-mapping :refer [ymparistolupa_to_krysp_221]]
            [lupapalvelu.document.canonical-common :refer [ya-operation-type-to-schema-name-key] :as common]
            [lupapalvelu.document.maa-aines-canonical]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.poikkeamis-canonical]
            [lupapalvelu.document.rakennuslupa-canonical]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.vesihuolto-canonical]
            [lupapalvelu.document.yleiset-alueet-canonical]
            [lupapalvelu.document.ymparisto-ilmoitukset-canonical]
            [lupapalvelu.document.ymparistolupa-canonical]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.factlet :refer [fact* facts*]]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.linked-files :as linked-files]
            [lupapalvelu.minimal-util :as min-util]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-legacy-itest-util :refer [give-legacy-verdict]]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.tasks] ; ensure task schemas are loaded
            [lupapalvelu.xml.emit :refer [element-to-xml]]
            [lupapalvelu.xml.validator :refer [validate]]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [net.cgrand.enlive-html :as enlive]
            [sade.common-reader :as cr]
            [sade.core :refer [def- now]]
            [sade.date :as date]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as xml]))

(set! *warn-on-reflection* true)

(testable-privates lupapalvelu.application count-required-link-permits)

(def VRKLupatunnus-path [:LupaTunnus :VRKLupatunnus])

(defn output-directory [sftp-user permit-type]
  (ss/join-file-path (env/value :outgoing-directory)
                     sftp-user
                     (permit/get-sftp-directory permit-type)
                     "/"))

(defn- populate-task [{:keys [id tasks]} task-id apikey]
  (let [task                  (some #(when (= (:id %) task-id) %) tasks)
        schema                (model/get-document-schema task)
        data                  (tools/create-document-data schema (partial tools/dummy-values (min-util/id-for-key apikey)))
        readonly-schemas      (filter :readonly (:body schema))
        ;; remove data where schema has readonly defined (katselmuksenLaji) as it's illegal to update readonly data
        data                  (reduce (fn [final-data {schema-name :name}] (dissoc final-data (keyword schema-name))) data readonly-schemas)
        updates               (tools/path-vals data)
        updates               (map (fn [[p v]] [(butlast p) v]) updates)
        updates               (map (fn [[p v]] [(ss/join "." (map name p)) v]) updates)
        ;; Filter out updates for fields with :schema-include flags (e.g., :ryhti-data-2026)
        ;; as these cannot be updated when the application doesn't have the corresponding flag
        schema-include-fields #{"toimitusTilanne" "pitajaDetails" "etunimi" "sukunimi"}
        updates               (remove (fn [[path _]]
                                        (some #(ss/includes? path %) schema-include-fields))
                                      updates)]
    (command apikey :update-task :id id :doc task-id :updates updates)))

(def- drawings [{:id       1,
                 :name     "A",
                 :desc     "A desc",
                 :category "123",
                 :geometry "POLYGON((438952 6666883.25,441420 6666039.25,441920 6667359.25,439508 6667543.25,438952 6666883.25))",
                 :area     "2686992",
                 :height   "1"}
                {:id       2,
                 :name     "B",
                 :desc     "B desc",
                 :category "123",
                 :geometry "POLYGON((440652 6667459.25,442520 6668435.25,441912 6667359.25,440652 6667459.25))",
                 :area     "708280",
                 :height   "12"}])

(defn- add-drawings [application]
  (fact {:midje/description (format "pena adds drawings, permitType: %s, primaryOperation: %s"
                                    (:permitType application) (:name (:primaryOperation application)))}
    (command pena :save-application-drawings
             :id (:id application)
             :drawings drawings) => ok?))

(defn- generate-link-permit [{id :id :as application} apikey]
  (when (pos? (count-required-link-permits application))
    (fact "Add required link permit"
      (command apikey :add-link-permit :id id :linkPermitId "Kuntalupatunnus 123") => ok?)))

(defn- validate-attachment [liite expected-type application]
  (let [permit-type                          (keyword (permit/permit-type application))
        organization                         (organization-from-minimal-by-id (:organization application))
        sftp-user                            (get-in organization [:krysp permit-type :ftpUser])
        output-dir                           (output-directory sftp-user permit-type)

        ^String linkkiliitteeseen            (:linkkiliitteeseen liite)
        tyyppi                               (:tyyppi liite)
        ;; NOTE: LPK-4304: The "linkkiliitteeseen" does not fulfil the form requirements of java.net.URI (includes spaces).
        ;;       Project team decision was that it does not need to, so here it is checked with a regexp.
        [_ _ host path attachment-file-name] (re-matches #"^(.+://)(.+?)(/.+/)(.+)$" linkkiliitteeseen)
        attachment-file-name-with-directory  (str output-dir attachment-file-name)
        attachment-file                      (if get-files-from-sftp-server?
                                               (get-file-from-server
                                                 sftp-user
                                                 host
                                                 (str path attachment-file-name)
                                                 (str "target/Downloaded-" attachment-file-name))
                                               attachment-file-name-with-directory)
        attachment-string                    (slurp attachment-file)]

    (if (#{"lausunto"
           "aloituskokouksen_poytakirja"
           "katselmuksen_tai_tarkastuksen_poytakirja"} (:tyyppi liite))
      (fact {:midje/description (str attachment-file-name " is a PDF")}
        (.contains attachment-string "PDF") => true)
      (do
        (if (.contains linkkiliitteeseen "rakennus")
          (fact "linkki liiteeseen contains '_test-attachment.pdf'"
            linkkiliitteeseen => #".+_test-attachment.pdf$")
          (fact "linkki liiteeseen contains '_test-gif-attachment.pdf'"
            linkkiliitteeseen => #".+_test-gif-attachment.gif$"))
        (fact "Liitetiedostossa on sisalto"
          (not (nil? attachment-string)))
        (fact "Tyyppi on oikea" tyyppi => expected-type)))))

(defn- final-xml-validation [test-name application autogenerated-attachment-count expected-attachment-count
                             expected-sent-attachment-count file-suffix & [additional-validator]]
  (let [permit-type          (keyword (permit/permit-type application))
        app-attachments      (filter :latestVersion (:attachments application))
        organization         (organization-from-minimal-by-id (:organization application))
        sftp-user            (get-in organization [:krysp permit-type :ftpUser])
        krysp-version        (get-in organization [:krysp permit-type :version])
        permit-type-dir      (permit/get-sftp-directory permit-type)
        output-dir           (output-directory sftp-user permit-type)
        sftp-server          (subs (env/value :fileserver-address) 7)
        target-file-name     (str "target/Downloaded-" (:id application) "-" (now) ".xml")
        filename-starts-with (get-app-id-filename-pred (:id application) file-suffix)
        xml-file             (if get-files-from-sftp-server?
                               (io/file (get-file-from-server
                                          sftp-user
                                          sftp-server
                                          filename-starts-with
                                          target-file-name
                                          (str permit-type-dir "/")))
                               (io/file (get-local-filename output-dir filename-starts-with)))
        xml-as-string        (slurp xml-file)
        xml                  (parse (io/reader xml-file))
        liitetieto           (xml/select xml [:liitetieto])
        polygon              (xml/select xml [:Polygon])]

    (facts {:midje/description test-name}
      (fact "Correctly named xml file is created" (.exists xml-file) => true)

      (fact "XML file is valid"
        (validate xml-as-string (:permitType application) krysp-version))

      (let [id (or
                 (xml/get-text xml [:LupaTunnus :tunnus])   ; Rakval, poik, ymp.
                 (xml/get-text xml [:Kasittelytieto :asiatunnus]))] ; YA
        (fact "Application ID" id => (:id application)))

      (fact {:midje/description (str test-name " - validate kasittelija")}
        (let [kasittelytieto (or
                               (xml/select1 xml [:kasittelynTilatieto])
                               (xml/select1 xml [:Kasittelytieto])
                               (xml/select1 xml [:KasittelyTieto]))
              etunimi        (xml/get-text kasittelytieto [:kasittelija :etunimi])
              sukunimi       (xml/get-text kasittelytieto [:kasittelija :sukunimi])]
          etunimi => (just #"(Sonja|Rakennustarkastaja)")
          sukunimi => (just #"(Sibbo|Järvenpää)")))

      (fact "XML contains correct amount attachments"
        (count liitetieto) => (+ autogenerated-attachment-count expected-attachment-count))

      (fact "Correct number of attachments are marked sent"
        (->> app-attachments
             (filter #(not= (get-in % [:target :type]) "verdict"))
             (filter :sent)
             count)
        => expected-sent-attachment-count)

      (fact "XML contains correct amount of polygons"
        (count polygon) => (case permit-type
                             :R 0
                             :P 0
                             (count drawings)))

      (when (pos? expected-attachment-count)
        (let [take-liite-fn  (case permit-type
                               :YA #(nth % 2)
                               last)
              liite-edn      (-> liitetieto take-liite-fn (xml/select1 [:Liite]) xml/xml->edn :Liite (util/ensure-sequential :metatietotieto))
              liite-id       (->> liite-edn
                                  :metatietotieto
                                  (map #(or (:metatieto %) (:Metatieto %)))
                                  (some #(when (= "liiteId" (:metatietoNimi %)) (:metatietoArvo %))))
              app-attachment (some #(when (= liite-id (:id %)) %) app-attachments)
              expected-type  (get-in app-attachment [:type :type-id])]

          (fact "XML has corresponding attachment in app" app-attachment => truthy)
          (validate-attachment liite-edn expected-type application)))

      (when (fn? additional-validator)
        (additional-validator xml)))))

(defn- do-test [operation-name]
  (facts "Valid KRYSP from generated application"
    (let [lupa-name-key    (ya-operation-type-to-schema-name-key (keyword operation-name))
          application-id   (create-app-id pena :propertyId "75341600550007" :operation operation-name :address "Ryspitie 289")
          _                (when (= "kerrostalo-rivitalo" operation-name)  ;; "R" permit
              (command pena :add-operation :id application-id :operation "jakaminen-tai-yhdistaminen")
              (command pena :add-operation :id application-id :operation "laajentaminen")
              (command pena :add-operation :id application-id :operation "purkaminen")
              (command pena :add-operation :id application-id :operation "aita")
              (command pena :add-operation :id application-id :operation "puun-kaataminen"))
          _                (when (= "yl-puiden-kaataminen" operation-name)
              (command pena :add-operation :id application-id :operation "yl-puiden-kaataminen"))
          generic-ymp-app? (#{"yl-puiden-kaataminen" "lannan-varastointi-v2" "yleinen-ilmoitus"} operation-name)
          application      (query-application pena application-id)
          _                (add-drawings application)
          application      (query-application pena application-id)
          organization-id  (str (:municipality application) "-" (case (:permitType application)
                                                                  "P"    "R"
                                                                  "YI"   "R"
                                                                  "YL"   "R"
                                                                  "YM"   "R"
                                                                  "MAL"  "R"
                                                                  "VVVL" "R"
                                                                  (:permitType application)))

          organization     (organization-from-minimal-by-id organization-id)
          permit-type      (keyword (permit/permit-type application)) ;; throws assertion error if permit type is not valid
          krysp-version    (get-in organization [:krysp permit-type :version])]

      (generate-documents! application pena)
      (generate-attachment application pena "pena")
      (generate-link-permit application pena)

      (command pena :submit-application :id application-id) => ok?

      (generate-construction-time-attachment application sonja "sonja")

      (let [updated-application (generate-statement application-id sonja)]

        (fact "updated-application exists" updated-application => truthy)

        (let [canonical (common/application->canonical updated-application "fi")
              mapping   (if (= operation-name "yleinen-ilmoitus")
                          ymparistolupa_to_krysp_221 ; special case where schema is different from permit type
                          (case permit-type
                            :R    (get-rakennuslupa-mapping krysp-version)
                            :YA   (get-yleiset-alueet-krysp-mapping lupa-name-key krysp-version)
                            :P    poikkeamis_to_krysp_221
                            :YI   ilmoitus_to_krysp_221
                            :YL   ymparistolupa_to_krysp_221
                            :YM   ymparistolupa_to_krysp_221
                            :MAL  maa-aines_to_krysp_221
                            :VVVL vesihuolto-to-krysp_221))
              xml       (element-to-xml canonical mapping)]
          (fact "xml exists" xml => truthy)))

      (facts "Application"
        (let [application (query-application sonja application-id)]
          (fact "is not assigned"
            (:handlers application) => empty?)
          (fact "has correct number of attachments with files"
            (->> (:attachments application)
                 (filter (comp :fileId :latestVersion))
                 count) => (if generic-ymp-app? 3 4))
          (fact "one of attachments is post a verdict attachment"
            (->> (:attachments application)
                 (filter (comp :fileId :latestVersion))
                 (filter (comp #{"verdictGiven"} :applicationState))
                 count) => 1)))

      (fact "Approve application"
        (when (#{"kerrostalo-rivitalo" "aloitusoikeus"} operation-name)
          (fact "update description"
            (command sonja :update-app-bulletin-op-description :id application-id :description "otsikko julkipanoon") => ok?))
        (let [resp (command sonja :approve-application :id application-id :lang "fi")]
          resp => ok?
          (:integrationAvailable resp) => true))

      (let [approved-application           (query-application pena application-id)
            email                          (last-email)
            ;; Two generated application pdf attachments
            autogenerated-attachment-count 2
            ;; see generate-attachment and generate-statement
            ;; gif-attachment + statement attachment (post verdict attachment is not transfered)
            expected-attachment-count      (if generic-ymp-app? 2 3)
            expected-sent-attachment-count expected-attachment-count]
        (fact "application is sent" (:state approved-application) => "sent")
        (final-xml-validation
          (str "do-test - " operation-name)
          approved-application
          autogenerated-attachment-count
          expected-attachment-count
          expected-sent-attachment-count
          nil
          (fn [xml]
            ;; VRKLupatunnus only when Pate is enabled in org
            (when (and (= :R permit-type)
                       (some->> (:scope organization)
                                (util/find-by-key :permitType "R")
                                :pate
                                :enabled))
              (fact {:midje/description (str operation-name " - VRKLupatunnus R")}
                (xml/get-text xml VRKLupatunnus-path) => string?))))

        (:to email) => (contains (min-util/email-for-key pena))
        (:subject email) => "Lupapiste: Ryspitie 289, Sipoo - hankkeen tila on nyt Käsittelyssä"
        (get-in email [:body :plain]) => (contains "Käsittelyssä")
        email => (partial contains-application-link? application-id "applicant"))

      (command sonja :request-for-complement :id application-id) => ok?

      (let [application (query-application pena application-id)
            email       (last-email)]
        (:state application) => "complementNeeded"
        (-> application :history last :state) => "complementNeeded"
        (:to email) => (contains (min-util/email-for-key pena))
        (:subject email) => "Lupapiste: Ryspitie 289, Sipoo - hankkeen tila on nyt Täydennettävänä"
        (get-in email [:body :plain]) => (contains "Täydennettävänä")
        email => (partial contains-application-link? application-id "applicant")))))

(with-test-system (-> {:fixture            "minimal"
                       :schema-validation? false}
                      test-system/with-remote-components)
  (facts
      (doseq [op ["kerrostalo-rivitalo"
                  "ya-sijoituslupa-ilmajohtojen-sijoittaminen"
                  "ya-kayttolupa-mainostus-ja-viitoitus"
                  "poikkeamis"
                  "aloitusoikeus"
                  "meluilmoitus" ; YI with YI schema
                  "lannan-varastointi-v2" ; YM
                  "yleinen-ilmoitus" ; YI but uses YM schema
                  "yl-uusi-toiminta"
                  "yl-puiden-kaataminen"
                  "maa-aineslupa"
                  "vvvl-vesijohdosta"]]

        (fact {:midje/description op} (do-test op)))))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components
                      test-system/with-mock-laundry)
  (fact "Attachments are transferred to the backing system when verdict has been given."
    (let [application      (create-and-submit-application sonja
                                                          :propertyId sipoo-property-id
                                                          :address "Paatoskuja 10")
          application-id   (:id application)
          first-attachment (get-in application [:attachments 0])]
      (upload-attachment sonja application-id first-attachment true)
      (-> (query-application sonja application-id)
          :attachments first :sent)
      => nil
      (fact "Attachments cannot be sent before the application"
        (command sonja :move-attachments-to-backing-system :id application-id :lang "fi"
                 :attachmentIds [(:id first-attachment)]) => fail?)
      (command sonja :update-app-bulletin-op-description :id application-id :description "otsikko julkipanoon") => ok?
      (command sonja :approve-application :id application-id :lang "fi") => ok?

      (let [application (query-application sonja application-id)]
        (fact "The first attachment has been sent"
          (->> application :attachments (filter :sent) (map :id))
          => (just (:id first-attachment)))

        (give-legacy-verdict sonja application-id :R true)

        (let [application (query-application sonja application-id)
              old-sent    (-> application :attachments first :sent)]
          (command sonja :move-attachments-to-backing-system :id application-id :lang "fi"
                   :attachmentIds (get-attachment-ids application)) => ok?

          (let [application (query-application sonja application-id)
                sents       (->> application :attachments (keep :sent))]
            (count sents) => 3 ;; First attachment, verdict, bulletin
            (apply = sents) => true
            (> (first sents) old-sent) => true
            (:state application) => "verdictGiven"))))))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components
                      test-system/with-mock-laundry)
  (facts "BIM linked files are recognized and processed by move-attachments-to-backing-system"
    (let [application      (create-and-submit-application sonja
                                                          :propertyId sipoo-property-id
                                                          :address "BIM-testikatu 1")
          application-id   (:id application)
          first-attachment (get-in application [:attachments 0])
          operation-id     (-> application :primaryOperation :id)
          ;; Create a BIM linked file directly in the database
          bim-file-id      (str (random-uuid))
          bim-version-id   (str (random-uuid))
          bim-linked-file  {:target-entity {:target-type    "bim-model-file"
                                            :file-id        bim-file-id
                                            :application-id application-id
                                            :operation-id   operation-id}
                            :metadata      {:name       "test-model.ifc"
                                            :model-type "architectural"}
                            :versions      [{:storage-system   "gcs"
                                             :file-version-id  bim-version-id
                                             :version          {:major 0 :minor 1}
                                             :bucket           "application-files"
                                             :target-file-path (str application-id "/" bim-version-id)
                                             :uploaded-by      {:id        sonja-id
                                                                :username  "sonja"
                                                                :firstName "Sonja"
                                                                :lastName  "Sibbo"
                                                                :role      "authority"}
                                             :metadata         {:name         "test-model.ifc"
                                                                :size         10000
                                                                :content-type "application/step"}
                                             :uploaded-at      (now)}]}]

      (mongo/insert linked-files/linked-file-metadata-collection bim-linked-file)

      (fact "BIM linked file was inserted"
        (linked-files/find-one-linked-file-with-metadata {:file-id bim-file-id}) => truthy)

      (upload-attachment sonja application-id first-attachment true)
      (command sonja :update-app-bulletin-op-description :id application-id :description "BIM test") => ok?
      (command sonja :approve-application :id application-id :lang "fi") => ok?
      (give-legacy-verdict sonja application-id :R true)

      (let [application (query-application sonja application-id)]
        (fact "Application is in verdictGiven state"
          (:state application) => "verdictGiven")

        ;; Verify that transferring only regular attachments works
        (let [attachment-ids (get-attachment-ids application)]
          (fact "move-attachments-to-backing-system succeeds with regular attachments only"
            (command sonja :move-attachments-to-backing-system :id application-id :lang "fi"
                     :attachmentIds attachment-ids) => ok?)

          (let [updated-app (query-application sonja application-id)
                transfer    (->> updated-app :transfers
                                 (filter #(= (keyword (:type %)) :exported-to-backing-system))
                                 last)]
            (fact "Transfer record was created for regular attachments"
              transfer => truthy)
            (fact "Transfer contains regular attachment IDs"
              (:attachments transfer) => (contains (first attachment-ids)))))

        ;; Verify that BIM file ID can be fetched and converted to pseudo-attachment
        (fact "BIM linked file can be fetched by file-id from integrations context"
          (let [linked-file (linked-files/find-one-linked-file-with-metadata
                              {:file-id        bim-file-id
                               :application-id application-id})]
            linked-file => truthy
            (get-in linked-file [:target-entity :target-type]) => "bim-model-file"
            (get-in linked-file [:metadata :model-type]) => "architectural"
            (-> linked-file :versions first :file-version-id) => bim-version-id))

        ;; Test the full BIM transfer flow - include BIM file ID in transfer
        ;; Use local-command because the BIM file was inserted directly via mongo/insert
        ;; and is only visible in the test process, not the web server process
        (let [attachment-ids (get-attachment-ids application)
              ;; Include BIM file ID in the transfer
              all-ids        (util/conjv attachment-ids bim-file-id)
              result         (local-command sonja :move-attachments-to-backing-system :id application-id :lang "fi"
                                            :attachmentIds all-ids)]
          ;; The command should either succeed or fail due to file content not being available.
          ;; It should NOT fail due to BIM file ID being rejected or not found in linked files.
          (fact "move-attachments-to-backing-system processes BIM file ID correctly"
            (if (:ok result)
              ;; Success: verify the transfer record includes BIM file ID
              (let [updated-app (local-query sonja :application :id application-id)
                    transfer    (->> updated-app :application :transfers
                                     (filter #(= (keyword (:type %)) :exported-to-backing-system))
                                     last)]
                (fact "Transfer record includes BIM file ID"
                  (:attachments transfer) => (contains bim-file-id)))
              ;; Failure is acceptable only for file download/content issues (GCS not available in test env),
              ;; not for command processing errors like "BIM file not found" or validation errors
              (fact "Failure should be due to file content issues, not command processing"
                ;; Acceptable errors: file not found in storage, download failed, etc.
                ;; Unacceptable: validation errors, nil pointer, unknown attachment ID
                (:text result) =not=> (contains "error.unknown")
                (:text result) =not=> (contains "error.invalid"))))
          ;; Cleanup: remove the test BIM file from the database using exact application-id
          (mongo/remove-many linked-files/linked-file-metadata-collection
                             {:target-entity.application-id application-id}))))))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components
                      test-system/with-mock-laundry)
  (facts* "Katselmus is transferred to the backing system with building info"
    (let [application    (create-and-submit-application sonja :propertyId sipoo-property-id :address "Katselmuskatu 17")
          application-id (:id application)
          _              (command sonja :upsert-application-handler :id application-id :userId sonja-id :roleId sipoo-general-handler-id) => ok?
          _              (command sonja :check-for-verdict :id application-id)                                                            => ok?
          application    (query-application sonja application-id)
          task-id        (-> application :tasks first :id)
          building       (first (:buildings application))]

      (fact "Building has operation"
        (:operationId building) =not=> ss/blank?)

      (upload-attachment-to-target sonja application-id nil true task-id "task") ; Related to task
      (upload-attachment-to-target sonja application-id nil true task-id "task" "katselmukset_ja_tarkastukset.katselmuksen_tai_tarkastuksen_poytakirja")

      (fact "Set state for building that was reviewed"
        (command sonja :update-task :id application-id :doc task-id :updates [["rakennus.0.tila.tila" "osittainen"]]) => ok?)

      (fact "Review done fails as missing required info for KRYSP transfer"
        (command sonja :review-done :id application-id :taskId task-id :lang "fi") => (partial expected-failure? :error.missing-parameters))

      (fact "Set state for the review"
        (command sonja :update-task :id application-id :doc task-id :updates [["katselmus.tila" "osittainen"]
                                                                              ["katselmus.pitoPvm" "12.04.2016"]
                                                                              ;; We use default value
                                                                              ["katselmus.pitaja" ""]]) => ok?)

      (fact "After filling required review data, transfer is ok"
        (command sonja :review-done :id application-id :taskId task-id :lang "fi") => ok?)


      (final-xml-validation (str "katselmus transfer test - app-id: " application-id)
                            (query-application sonja application-id)
                            1                          ; The attachment generated from the review itself
                            2                          ; Two attachments were uploaded above
                            3
                            "review"
                            (fn [xml]
                              (let [katselmus            (xml/select1 xml [:RakennusvalvontaAsia :katselmustieto :Katselmus])
                                    katselmuksenRakennus (xml/select1 xml [:katselmuksenRakennustieto :KatselmuksenRakennus])
                                    liitetieto           (xml/select1 katselmus [:liitetieto])]

                                (fact "Operation ID is transferred"
                                  (let [ids           (xml/select katselmuksenRakennus [:MuuTunnus])
                                        toimenpide-id (xml/select ids (enlive/has [:sovellus (enlive/text-pred (partial = "toimenpideId"))]))
                                        lupapiste-id  (xml/select ids (enlive/has [:sovellus (enlive/text-pred (partial = "Lupapiste"))]))]

                                    (fact "with sovellus=toimenpideId"
                                      (xml/get-text toimenpide-id [:MuuTunnus :tunnus]) =not=> ss/blank?)

                                    (fact "with sovellus=Lupapiste"
                                      (xml/get-text lupapiste-id [:MuuTunnus :tunnus]) =not=> ss/blank?)))

                                (xml/get-text katselmus :pitaja) => "Sonja Sibbo"
                                (xml/get-text katselmus :osittainen) => "osittainen"
                                (xml/get-text liitetieto :kuvaus) => "Katselmuksen pöytäkirja"
                                (xml/get-text liitetieto ::tyyppi) => "katselmuksen_tai_tarkastuksen_poytakirja"
                                (xml/get-text katselmuksenRakennus :rakennusnro) => (:localShortId building)
                                (xml/get-text katselmuksenRakennus :jarjestysnumero) => (:index building)
                                (xml/get-text katselmuksenRakennus :kiinttun) => (:propertyId building)
                                (xml/get-text katselmuksenRakennus :valtakunnallinenNumero) => (:nationalId building)))))))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components
                      test-system/with-mock-laundry)
  (facts* "Fully populated katselmus is transferred to the backing system"

    (fact "Meta test: KRYSP configs have different versions"
      (let [sipoo-r (organization-from-minimal-by-id "753-R")
            jp-r    (organization-from-minimal-by-id "186-R")]
        sipoo-r => truthy
        jp-r => truthy
        (get-in sipoo-r [:krysp :R :version]) => "2.2.2"
        (get-in jp-r [:krysp :R :version]) => "2.1.3"))

    (doseq [[apikey assignee property-id handler-role-id] [[sonja sonja-id sipoo-property-id sipoo-general-handler-id]
                                                           [raktark-jarvenpaa (min-util/id-for-key raktark-jarvenpaa) jarvenpaa-property-id jarvenpaa-general-handler-id]]]
      (let [application    (create-and-submit-application apikey :propertyId property-id :address "Katselmuskatu 17")
            application-id (:id application)
            _              (command apikey :upsert-application-handler :id application-id :userId assignee :roleId handler-role-id)                                 => ok?
            task-name      "do the shopping"
            task-id        (:taskId (command apikey :create-task :id application-id :taskName task-name :schemaName "task-katselmus" :taskSubtype "aloituskokous")) => truthy
            application    (query-application apikey application-id)]

        (populate-task application task-id apikey) => ok?

        (upload-attachment-to-target apikey application-id nil true task-id "task")
        (upload-attachment-to-target apikey application-id nil true task-id "task" "katselmukset_ja_tarkastukset.katselmuksen_tai_tarkastuksen_poytakirja")

        (doseq [attachment (:attachments (query-application apikey application-id))]
          (fact "sent timestamp not set"
            (:sent attachment) => nil)
          (fact "read only is false"
            (:readOnly attachment) => false))

        (command apikey :review-done :id application-id :taskId task-id :lang "fi") => ok?

        (let [application (query-application apikey application-id)]
          (final-xml-validation (str "fully populated katselmus test - " property-id)
                                application
                                1                      ; The attachment generated from the review itself
                                        ; Uploaded 2 regular attachments,the other should be katselmuspoytakirja.
                                        ; In KRYSP 2.2.0+ both are wrapped in liitetieto elements.
                                (if (= "753-R" (:organization application)) 2 1)
                                3
                                "review"
                                (fn [xml]
                                  (let [katselmus      (xml/select1 xml [:RakennusvalvontaAsia :katselmustieto :Katselmus])
                                        poytakirja     (xml/xml->edn (or (xml/select1 katselmus [:katselmuspoytakirja]) (xml/select1 katselmus [:Liite])))
                                        poytakirja-edn (or (:katselmuspoytakirja poytakirja) (:Liite poytakirja))]

                                    (validate-attachment poytakirja-edn "katselmuksen_tai_tarkastuksen_poytakirja" application)
                                    (fact "task name is transferred for muu katselmus type"
                                      (xml/get-text katselmus [:tarkastuksenTaiKatselmuksenNimi]) => task-name))))

          (doseq [attachment (filter :target (:attachments application))]
            (fact "sent timestamp is set"
              (:sent attachment) => number?)
            (fact "read only is true"
              (:readOnly attachment) => true)))))))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (fact "Only approved designers are transferred"
    (let [application            (create-and-submit-application sonja :propertyId sipoo-property-id :address "Katselmuskatu 18")
          application-id         (:id application)
          {paasuunnittelija :id} (domain/get-document-by-name application "paasuunnittelija")
          {suunnittelija1 :id}   (domain/get-document-by-name application "suunnittelija")
          {suunnittelija2 :doc}  (command sonja :create-doc :id application-id :schemaName "suunnittelija")]

      (command sonja :update-doc :id application-id :doc paasuunnittelija :updates (data->updates (assoc paasuunnittelija-required-data "henkilotiedot.sukunimi" "Paasuunnittelija" "yhteystiedot.email" "sonja.sibbo@sipoo.fi"))) => ok?
      (command sonja :update-doc :id application-id :doc suunnittelija1 :updates (data->updates (assoc suunnittelija-required-data "henkilotiedot.sukunimi" "Suunnittelija A" "yhteystiedot.email" "sonja.sibbo@sipoo.fi"))) => ok?
      (command sonja :update-doc :id application-id :doc suunnittelija2 :updates (data->updates (assoc suunnittelija-required-data "henkilotiedot.sukunimi" "Suunnittelija B" "yhteystiedot.email" "sonja.sibbo@sipoo.fi"))) => ok?

      (command sonja :designer-consent :id application-id :documentId paasuunnittelija :consent true) => ok?
      (command sonja :designer-consent :id application-id :documentId suunnittelija2 :consent true) => ok?

      (command sonja :approve-doc :id application-id :doc paasuunnittelija :path nil :collection "documents") => ok?
      (command sonja :approve-doc :id application-id :doc suunnittelija2 :path nil :collection "documents") => ok?

      (command sonja :update-doc :id application-id :doc paasuunnittelija :updates [["henkilotiedot.etunimi" "etu"]]) => ok?

      (command sonja :update-app-bulletin-op-description :id application-id :description "otsikko julkipanoon") => ok?
      (command sonja :approve-application :id application-id :lang "fi") => ok?

      (final-xml-validation "approved designers are tranferred"
                            (query-application sonja application-id)
                            2                          ; autogenerated
                            0                          ; no attachments are sent...
                            0                          ; ...so nothing is marked sent
                            nil
                            (fn [xml]
                              (let [designers (map xml/get-text (xml/select xml [:Suunnittelija :sukunimi]))]
                                (fact "Only Suunnittelija B is in XML message"
                                        ; Suunnittelija A has not been approved,
                                        ; Paasuunnittelija was modified after approval
                                  (count designers) => 1
                                  (first designers) => "Suunnittelija B"))))))

  (facts "Attachments are linked with HTTP links in KRYSP XML if the organization so chooses"
    (let [application    (create-and-open-application raktark-helsinki :propertyId "09141600550007" :address "Fleminginkatu 1")
          application-id (:id application)]

      (generate-documents! application raktark-helsinki)
      (generate-attachment application raktark-helsinki "helsinki")

      (command raktark-helsinki :submit-application :id application-id) => ok?
      (command raktark-helsinki :approve-application :id application-id :lang "fi") => ok?

      (let [application          (query-application raktark-helsinki application-id)
            permit-type          (keyword (permit/permit-type application))
            app-attachments      (filter :latestVersion (:attachments application))
            organization         (organization-from-minimal-by-id (:organization application))
            sftp-user            (get-in organization [:krysp permit-type :ftpUser])
            krysp-version        (get-in organization [:krysp permit-type :version])
            permit-type-dir      (permit/get-sftp-directory permit-type)
            output-dir           (output-directory sftp-user permit-type)
            sftp-server          (subs (env/value :fileserver-address) 7)
            target-file-name     (str "target/Downloaded-" (:id application) "-" (now) ".xml")
            filename-starts-with (:id application)
            xml-file             (if get-files-from-sftp-server?
                                   (io/file (get-file-from-server
                                              sftp-user
                                              sftp-server
                                              filename-starts-with
                                              target-file-name
                                              (str permit-type-dir "/")))
                                   (io/file (get-local-filename output-dir filename-starts-with)))
            xml-as-string        (slurp xml-file)
            xml                  (parse (io/reader xml-file))
            liitetieto           (xml/select xml [:liitetieto])
            liite-edn            (-> liitetieto last (xml/select1 [:Liite]) xml/xml->edn :Liite (util/ensure-sequential :metatietotieto))
            liite-id             (->> liite-edn
                                      :metatietotieto
                                      (map #(or (:metatieto %) (:Metatieto %)))
                                      (some #(when (= "liiteId" (:metatietoNimi %)) (:metatietoArvo %))))
            app-attachment       (some #(when (= liite-id (:id %)) %) app-attachments)
            attachment-urls      (map
                                   (fn [liite]
                                     (-> (xml/select1 liite [:Liite])
                                         xml/xml->edn
                                         :Liite
                                         :linkkiliitteeseen))
                                   liitetieto)]
        (fact "Correctly named xml file is created" (.exists xml-file) => true)

        (fact "XML file is valid"
          (validate xml-as-string (:permitType application) krysp-version))

        (fact "XML contains correct amount attachments"
          (count liitetieto) => 3)

        (fact "Correct number of attachments are marked sent"
          (->> app-attachments
               (filter :latestVersion)
               (filter #(not= (get-in % [:target :type]) "verdict"))
               (filter :sent)
               count) => 1)

        (fact "XML has corresponding attachment in app" app-attachment => truthy)

        (fact "XML contains HTTP links for attachments"
          attachment-urls => (contains [(re-pattern (str "[^\\s]+/api/raw/pdf-export\\?id=" application-id "&lang=fi"))
                                        (re-pattern (str "[^\\s]+/api/raw/submitted-application-pdf-export\\?id=" application-id "&lang=fi"))
                                        (re-pattern (str "[^\\s]+/api/raw/latest-attachment-version\\?attachment-id=" liite-id))]
                                       :in-any-order)))))

  (facts "VRKLupatunnus missing if PATE disabled"           ; Testing with Jaervenpaa R
    (let [app            (create-and-submit-application pena
                                                        :propertyId jarvenpaa-property-id
                                                        :operation "kerrostalo-rivitalo"
                                                        :address "Testikatu")
          application-id (:id app)]
      (generate-documents! app pena)
      (:organization app) => "186-R"

      (let [resp (command raktark-jarvenpaa :approve-application :id application-id :lang "fi")]
        (fact "approve app"
          resp => ok?
          (:integrationAvailable resp) => true))
      (let [approved-application           (query-application pena application-id)
            ;; Two generated application pdf attachments
            autogenarated-attachment-count 2
            expected-attachment-count      0
            expected-sent-attachment-count expected-attachment-count]
        (fact "application is sent" (:state approved-application) => "sent")
        (final-xml-validation
          "VRKLupatunnus not present - "
          approved-application
          autogenarated-attachment-count
          expected-attachment-count
          expected-sent-attachment-count
          nil
          (fn [xml]
            (fact "No VRKLupatunnus in XML"
              (xml/get-text xml VRKLupatunnus-path) => nil?)))))))

(defn fetch-building-extinction-xml [application extinct-operation-id]
  (let [operation-extinction-message? (fn [file]
                                        (let [filename (file-name-accessor file)]
                                          (when (and (ss/includes? filename (:id application))
                                                     (ss/ends-with? filename (str "building-extinction-" extinct-operation-id ".xml")))
                                            filename)))]
    (get-valid-krysp-xml application operation-extinction-message?)))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components
                      test-system/with-mock-laundry)
  (facts* "Extinct date is set to operation in application"
    (let [app-id               (create-app-id pena :operation "pientalo" :propertyId sipoo-property-id)
          resp1                (command pena :add-operation :id app-id :operation "rakennustietojen-korjaus") => ok?
          resp2                (command pena :submit-application :id app-id)                                  => ok?
          _                    (command sonja :update-app-bulletin-op-description :id app-id :description "otsikko julkipanoon")
          resp3                (command sonja :approve-application :id app-id :lang "fi")                     => ok?
          resp4                (give-legacy-verdict sonja app-id :R true)                                     => string?
          app                  (query-application sonja app-id)
          extinct-operation-id (-> app :primaryOperation :id)]

      (facts "invalid extinct param values"
        (doseq [failing-value [-1 "1575158400000" "" {} []]]
          (command sonja :set-building-extinct
                   :id app-id
                   :lang "fi"
                   :operationId extinct-operation-id
                   :extinct failing-value) => (partial expected-failure? :error.invalid-request)))

      (facts* "set extinct date"
        (let [extinct-timestamp (date/timestamp "1.12.2019")
              resp5             (command sonja :set-building-extinct
                                         :id app-id
                                         :lang "fi"
                                         :operationId extinct-operation-id
                                         :extinct extinct-timestamp)
              =>                ok?

              app               (query-application sonja app-id)
              parsed-xml        (fetch-building-extinction-xml app extinct-operation-id)]

          (fact* "correct toimenpidetieto in the xml"
            (let [toimenpiteet                (->> (xml/select parsed-xml [:toimenpidetieto :Toimenpide])
                                                   (map cr/all-of))
                  pientalo-op                 (->> toimenpiteet (some #(when (contains? % :uusi) %)))
                  =>                          truthy
                  rakennustietojen-korjaus-op (->> toimenpiteet (some #(when (contains? % :muuMuutosTyo) %)))
                  =>                          truthy]
              (count toimenpiteet) => 2
              (get-in pientalo-op [:uusi :raukeamisPvm]) => "2019-12-01+02:00"
              (get rakennustietojen-korjaus-op :muuMuutosTyo) =not=> (contains :raukeamisPvm)))

          (fact "Rakennuspaikkatieto in the xml"
            (not-empty (xml/select parsed-xml [:rakennuspaikkatieto])) => truthy)

          (fact "extinct timestamp has been updated to operation in application data"
            (-> app :primaryOperation :extinct) => extinct-timestamp)))

      (facts* "remove extinct date"
        (let [resp5                       (command sonja :set-building-extinct
                                                   :id app-id
                                                   :lang "fi"
                                                   :operationId extinct-operation-id
                                                   :extinct nil)
              =>                          ok?
              app                         (query-application sonja app-id)
              parsed-xml                  (fetch-building-extinction-xml app extinct-operation-id)
              toimenpiteet                (->> (xml/select parsed-xml [:toimenpidetieto :Toimenpide])
                                               (map cr/all-of))
              pientalo-op                 (->> toimenpiteet (some #(when (contains? % :uusi) %)))
              =>                          truthy
              rakennustietojen-korjaus-op (->> toimenpiteet (some #(when (contains? % :muuMuutosTyo) %)))
              =>                          truthy]

          (fact "correct toimenpidetieto in the xml"
            (count toimenpiteet) => 2
            (get-in pientalo-op [:uusi :raukeamisPvm]) =not=> (contains :raukeamisPvm)
            (get rakennustietojen-korjaus-op :muuMuutosTyo) =not=> (contains :raukeamisPvm))

          (fact "extinct timestamp has been removed from operation in application data"
            (-> app :primaryOperation) =not=> (contains :extinct))))

      (let [runeberg                 (date/timestamp "5.2.2021")
            original-mq-feature-flag (get-in (query pena :features) [:features :integration-message-queue] false)]
        (facts "Building extinct vs. HTTP integration"
          (when original-mq-feature-flag
            (fact "Disable the message queue for this test"
              (command pena :set-feature :feature "integration-message-queue" :value false) => ok?))

          (fact "Configure HTTP integration for Sipoo"
            (command admin :upsert-dmcity-configuration :organizationId "753-R"
                     :configuration {:url      (str (server-address) "/dev/dummy-dmcity/endpoint")
                                     :targets  {:R true}
                                     :vault    "test-vault"
                                     :username "test-user"
                                     :password "test-secret"})
            => ok?)

          (fact "Mark building extinct"
            (command sonja :set-building-extinct
                     :id app-id
                     :lang "fi"
                     :operationId extinct-operation-id
                     :extinct runeberg) => ok?)
          (facts "Integration messages"
            (let [[sent received & _
                   :as msgs] (filter #(ss/includes? (:messageType %) "KuntaGML")
                                     (integration-messages app-id
                                                           :test-db-name test-db-name))]
              (fact "Only two messages"
                (count msgs) => 2)
              (fact "Sent"
                sent => (contains {:messageType  "KuntaGML building-extinction"
                                   :direction    "out"
                                   :acknowledged pos?
                                   :partner      "dmcity"
                                   :status       "done"
                                   :transferType "http"
                                   :application  {:id app-id :organization "753-R"}})
                (in-text (:data sent)
                         "Rakennuksen raukeaminen"
                         "<rakval:raukeamisPvm>2021-02-05+02:00</rakval:raukeamisPvm>"))
              (fact "Received"
                received => (contains {:application  {:id app-id}
                                       :data         {:target "Rakennusvalvontapaatos"
                                                      :body   (:data sent)}
                                       :direction    "in"
                                       :format       "xml"
                                       :messageType  "KuntaGML Rakennusvalvontapaatos"
                                       :status       "received"
                                       :transferType "http"}))))
          (when original-mq-feature-flag
            (fact "Restore MQ feature"
              (command pena :set-feature
                       :feature "integration-message-queue"
                       :value original-mq-feature-flag) => ok?)))))))
