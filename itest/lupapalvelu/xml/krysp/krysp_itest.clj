(ns lupapalvelu.xml.krysp.krysp-itest
    (:require [taoensso.timbre :as timbre :refer (trace debug info warn error fatal)]
      [clojure.string :as s]
      [clojure.java.io :as io]
      [clojure.data.xml :refer :all]
      [midje.sweet :refer :all]
      [midje.util :refer [testable-privates]]
      [lupapalvelu.itest-util :refer :all]
      [lupapalvelu.factlet :refer :all]
      [lupapalvelu.document.model :refer [default-max-len]]
      [lupapalvelu.xml.emit :refer :all]
      [lupapalvelu.xml.validator :refer [validate]]
      [lupapalvelu.xml.krysp.rakennuslupa-mapping :refer [get-rakennuslupa-mapping]]
      [lupapalvelu.xml.krysp.poikkeamis-mapping :refer [poikkeamis_to_krysp_221]]
      [lupapalvelu.xml.krysp.ymparisto-ilmoitukset-mapping :refer [ilmoitus_to_krysp_221]]
      [lupapalvelu.xml.krysp.ymparistolupa-mapping :refer [ymparistolupa_to_krysp_221]]
      [lupapalvelu.xml.krysp.maa-aines-mapping :refer [maa-aines_to_krysp_221]]
      [lupapalvelu.xml.krysp.vesihuolto-mapping :refer [vesihuolto-to-krysp_221]]
      [lupapalvelu.xml.krysp.yleiset-alueet-mapping :refer [get-yleiset-alueet-krysp-mapping]]
      [lupapalvelu.document.canonical-common :refer [by-type ya-operation-type-to-schema-name-key]]
      [lupapalvelu.document.rakennuslupa-canonical :as rakennuslupa_canonical]
      [lupapalvelu.document.yleiset-alueet-canonical :as yleiset-alueet-canonical]
      [lupapalvelu.document.poikkeamis-canonical :as poikkeamis-canonical]
      [lupapalvelu.document.ymparisto-ilmoitukset-canonical :as ympilm-canonical]
      [lupapalvelu.document.ymparistolupa-canonical :as ymparistolupa-canonical]
      [lupapalvelu.document.maa-aines-canonical :as maa-aines-canonical]
      [lupapalvelu.document.vesihuolto-canonical :as vesihuolto-canonical]
      [lupapalvelu.document.schemas :as schemas]
      [lupapalvelu.document.tools :as tools]
      [lupapalvelu.document.model :as model]
      [lupapalvelu.permit :as permit]
      [lupapalvelu.operations :as operations]
      [lupapalvelu.i18n :refer [with-lang loc localize]]
      [lupapalvelu.tasks]                                   ; ensure task schemas are loaded
      [sade.env :as env]
      [sade.xml :as xml]
      [sade.util :as util]
      [sade.core :refer [def- now]]
      [lupapalvelu.application :as ns-app])
    (:import [java.net URI]))

(apply-remote-minimal)

(testable-privates lupapalvelu.application is-link-permit-required)

(defn- populate-task [{:keys [id tasks]} task-id apikey]
       (let [task (some #(when (= (:id %) task-id) %) tasks)
             data (tools/create-document-data (model/get-document-schema task) (partial tools/dummy-values (id-for-key apikey)))
             updates (tools/path-vals data)
             updates (map (fn [[p v]] [(butlast p) v]) updates)
             updates (map (fn [[p v]] [(s/join "." (map name p)) v]) updates)]
            (command apikey :update-task :id id :doc task-id :updates updates)))

(def- drawings [{:id 1,
                 :name "A",
                 :desc "A desc",
                 :category "123",
                 :geometry "POLYGON((438952 6666883.25,441420 6666039.25,441920 6667359.25,439508 6667543.25,438952 6666883.25))",
                 :area "2686992",
                 :height "1"}
                {:id 2,
                 :name "B",
                 :desc "B desc",
                 :category "123",
                 :geometry "POLYGON((440652 6667459.25,442520 6668435.25,441912 6667359.25,440652 6667459.25))",
                 :area "708280",
                 :height "12"}])

(defn- add-drawings [application]
       (command pena :save-application-drawings
                :id (:id application)
                :drawings drawings))

;; NOTE: For this to work properly,
;;       the :operations-attachments must be set correctly for organization (in minimal.clj)
;;       and also the :attachments for operation (in operations.clj).
(defn- generate-attachment [{id :id :as application} apikey password]
       (when-let [first-attachment (or
                                     (get-in application [:attachments 0])
                                     (case (-> application :primaryOperation :name)
                                           "aloitusoikeus" {:type {:type-group "paapiirustus"
                                                                   :type-id "asemapiirros"}
                                                            :id id}
                                           nil))]
                 (upload-attachment apikey id first-attachment true)
                 (sign-attachment apikey id (:id first-attachment) password)))

;; This has a side effect which generates a attachemnt to appliction
(defn- generate-statement [application-id]
       (let [sipoo-statement-givers (:statementGivers (organization-from-minimal-by-id "753-R"))
             sonja-statement-giver-id (:id (some #(when (= (:email %) "sonja.sibbo@sipoo.fi") %) sipoo-statement-givers))
             create-statement-result (command sonja :request-for-statement
                                              :functionCode nil
                                              :id application-id
                                              :personIds [sonja-statement-giver-id])
             updated-application (query-application pena application-id)
             statement-id (:id (first (:statements updated-application)))
             upload-statement-attachment-result (upload-attachment-for-statement sonja application-id "" true statement-id)
             give-statement-result (command sonja :give-statement
                                            :id application-id
                                            :statementId statement-id
                                            :status "puoltaa"
                                            :lang "fi"
                                            :text "Annanpa luvan urakalle.")]
            (query-application pena application-id)))

(defn- generate-link-permit [{id :id :as application} apikey]
       (when (is-link-permit-required application)
             (fact "Lisataan hakemukselle viitelupa" (command apikey :add-link-permit :id id :linkPermitId "Kuntalupatunnus 123") => ok?)))

(defn- validate-attachment [liite expected-type application]
       (let [permit-type (keyword (permit/permit-type application))
             organization (organization-from-minimal-by-id (:organization application))
             sftp-user (get-in organization [:krysp permit-type :ftpUser])
             permit-type-dir (permit/get-sftp-directory permit-type)
             output-dir (str "target/" sftp-user permit-type-dir "/")

             linkkiliitteeseen (:linkkiliitteeseen liite)
             tyyppi (:tyyppi liite)
             linkki-as-uri (when linkkiliitteeseen (URI. linkkiliitteeseen))
             attachment-file-name (last (s/split linkkiliitteeseen #"/"))
             attachment-file-name-with-directory (str output-dir attachment-file-name)
             attachment-file (if get-files-from-sftp-server?
                               (get-file-from-server
                                 sftp-user
                                 (.getHost linkki-as-uri)
                                 (.getPath linkki-as-uri)
                                 (str "target/Downloaded-" attachment-file-name))
                               attachment-file-name-with-directory)
             attachment-string (slurp attachment-file)
             ]

            (if (.endsWith linkkiliitteeseen "_Lausunto.pdf")
              (do
                (fact "linkki liiteeseen contains '_test-attachment.txt'"  (.endsWith linkkiliitteeseen "_Lausunto.pdf") => true)
                (fact "Liitetiedoston sisalto on oikea"  (.contains attachment-string "PDF") => true))
              (do
                (fact "linkki liiteeseen contains '_test-attachment.txt'" (.endsWith linkkiliitteeseen "_test-attachment.txt") => true)
                (fact "Liitetiedoston sisalto on oikea" attachment-string => "This is test file for file upload in itest.")
                (fact "Tyyppi on oikea" tyyppi => expected-type)))))

(defn- final-xml-validation [application expected-attachment-count expected-sent-attachment-count & [additional-validator]]
       (let [permit-type (keyword (permit/permit-type application))
             app-attachments (filter :latestVersion (:attachments application))
             organization (organization-from-minimal-by-id (:organization application))
             sftp-user (get-in organization [:krysp permit-type :ftpUser])
             krysp-version (get-in organization [:krysp permit-type :version])
             permit-type-dir (permit/get-sftp-directory permit-type)
             output-dir (str "target/" sftp-user permit-type-dir "/")
             local-file (str output-dir (:id application) ".xml")
             sftp-server (subs (env/value :fileserver-address) 7)
             target-file-name (str "target/Downloaded-" (:id application) "-" (now) ".xml")
             filename-starts-with (:id application)
             xml-file (if get-files-from-sftp-server?
                        (io/file (get-file-from-server
                                   sftp-user
                                   sftp-server
                                   filename-starts-with
                                   target-file-name
                                   (str permit-type-dir "/")))
                        (io/file (get-local-filename output-dir filename-starts-with)))
             xml-as-string (slurp xml-file)
             xml (parse (io/reader xml-file))
             liitetieto (xml/select xml [:liitetieto])
             polygon (xml/select xml [:Polygon])]


            (fact "Correctly named xml file is created" (.exists xml-file) => true)

            (fact "XML file is valid"
                  (validate xml-as-string (:permitType application) krysp-version))

            (let [id (or
                       (xml/get-text xml [:LupaTunnus :tunnus]) ; Rakval, poik, ymp.
                       (xml/get-text xml [:Kasittelytieto :asiatunnus]))] ; YA
                 (fact "Application ID" id => (:id application)))

            (fact "kasittelija"
                  (let [kasittelytieto (or
                                         (xml/select1 xml [:kasittelynTilatieto])
                                         (xml/select1 xml [:Kasittelytieto])
                                         (xml/select1 xml [:KasittelyTieto]))
                        etunimi (xml/get-text kasittelytieto [:kasittelija :etunimi])
                        sukunimi (xml/get-text kasittelytieto [:kasittelija :sukunimi])]
                       etunimi => (just #"(Sonja|Rakennustarkastaja)")
                       sukunimi => (just #"(Sibbo|J\u00e4rvenp\u00e4\u00e4)")))

            (fact "XML contains correct amount attachments" (count liitetieto) => expected-attachment-count)

            (fact "Correct number of attachments are marked sent"
                  (->> app-attachments
                       (filter :latestVersion)
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
                  (let [take-liite-fn (case permit-type
                                            :YA first
                                            last)
                        liite-edn (-> liitetieto take-liite-fn (xml/select1 [:Liite]) xml/xml->edn :Liite (util/ensure-sequential :metatietotieto))
                        kuvaus (:kuvaus liite-edn)
                        liite-id (->> liite-edn
                                      :metatietotieto
                                      (map #(or (:metatieto %) (:Metatieto %)))
                                      (some #(when (= "liiteId" (:metatietoNimi %)) (:metatietoArvo %))))
                        app-attachment (some #(when (= liite-id (:id %)) %) app-attachments)
                        expected-type (get-in app-attachment [:type :type-id])]

                       (fact "XML has corresponding attachment in app" app-attachment => truthy)
                       (validate-attachment liite-edn expected-type application)))

            (when (fn? additional-validator)
                  (additional-validator xml))))

(defn- do-test [operation-name]
       (facts "Valid KRYSP from generated application"
              (let [lupa-name-key (ya-operation-type-to-schema-name-key (keyword operation-name))
                    application-id (create-app-id pena :propertyId "75341600550007" :operation operation-name :address "Ryspitie 289")
                    _ (when (= "kerrostalo-rivitalo" operation-name) ;; "R" permit
                            (command pena :add-operation :id application-id :operation "jakaminen-tai-yhdistaminen")
                            (command pena :add-operation :id application-id :operation "laajentaminen")
                            (command pena :add-operation :id application-id :operation "purkaminen")
                            (command pena :add-operation :id application-id :operation "aita")
                            (command pena :add-operation :id application-id :operation "puun-kaataminen"))
                    application (query-application pena application-id)
                    _ (add-drawings application)
                    application (query-application pena application-id)
                    organization-id (str (:municipality application) "-" (case (:permitType application)
                                                                               "P" "R"
                                                                               "YI" "R"
                                                                               "YL" "R"
                                                                               "MAL" "R"
                                                                               "VVVL" "R"
                                                                               (:permitType application)))

                    organization (organization-from-minimal-by-id organization-id)
                    permit-type (keyword (permit/permit-type application)) ;; throws assertion error if permit type is not valid
                    krysp-version (get-in organization [:krysp permit-type :version])]

                   (generate-documents application pena)
                   (generate-attachment application pena "pena")
                   (generate-link-permit application pena)

                   (command pena :submit-application :id application-id) => ok?

                   (let [updated-application (generate-statement application-id)]

                        (fact "updated-application exists" updated-application => truthy)

                        (let [canonical-fn (case permit-type
                                                 :R rakennuslupa_canonical/application-to-canonical
                                                 :YA yleiset-alueet-canonical/application-to-canonical
                                                 :P poikkeamis-canonical/poikkeus-application-to-canonical
                                                 :YI ympilm-canonical/meluilmoitus-canonical
                                                 :YL ymparistolupa-canonical/ymparistolupa-canonical
                                                 :MAL maa-aines-canonical/maa-aines-canonical
                                                 :VVVL vesihuolto-canonical/vapautus-canonical)
                              canonical (canonical-fn updated-application "fi")
                              mapping (case permit-type
                                            :R (get-rakennuslupa-mapping krysp-version)
                                            :YA (get-yleiset-alueet-krysp-mapping lupa-name-key krysp-version)
                                            :P poikkeamis_to_krysp_221
                                            :YI ilmoitus_to_krysp_221
                                            :YL ymparistolupa_to_krysp_221
                                            :MAL maa-aines_to_krysp_221
                                            :VVVL vesihuolto-to-krysp_221)
                              xml (element-to-xml canonical mapping)
                              xml-s (indent-str xml)]
                             (fact "xml exists" xml => truthy)))

                   (fact "Application is not assigned"
                         (get-in (query-application sonja application-id) [:authority :id]) => nil)

                   (fact "Approve application"
                         (let [resp (command sonja :approve-application :id application-id :lang "fi")]
                              resp => ok?
                              (:integrationAvailable resp) => true))

                   (let [generated-attachment-count 3       ; see generate-attachment, generate-statement+1
                         approved-application (query-application pena application-id)
                         email (last-email)
                         expected-attachment-count (case permit-type
                                                         :R (+ generated-attachment-count 2) ; 2 auto-generated PDFs
                                                         generated-attachment-count)
                         expected-sent-attachment-count (case permit-type
                                                              :R (- expected-attachment-count 2) ; 2 auto-generated PDFs
                                                              expected-attachment-count)]
                        (fact "application is sent" (:state approved-application) => "sent")
                        (final-xml-validation approved-application expected-attachment-count expected-sent-attachment-count)

                        (:to email) => (contains (email-for-key pena))
                        (:subject email) => "Lupapiste.fi: Ryspitie 289 - hakemuksen tila muuttunut"
                        (get-in email [:body :plain]) => (contains "K\u00e4sittelyss\u00e4")
                        email => (partial contains-application-link? application-id "applicant"))

                   (command sonja :request-for-complement :id application-id) => ok?

                   (let [application (query-application pena application-id)
                         email (last-email)]
                        (:state application) => "complementNeeded"
                        (-> application :history last :state) => "complementNeeded"
                        (:to email) => (contains (email-for-key pena))
                        (:subject email) => "Lupapiste.fi: Ryspitie 289 - hakemuksen tila muuttunut"
                        (get-in email [:body :plain]) => (contains "T\u00e4ydennett\u00e4v\u00e4n\u00e4")
                        email => (partial contains-application-link? application-id "applicant")))))

(facts "All krysp itests"
       (dorun (map do-test ["kerrostalo-rivitalo"
                            "ya-sijoituslupa-ilmajohtojen-sijoittaminen"
                            "ya-kayttolupa-mainostus-ja-viitoitus"
                            "poikkeamis"
                            "aloitusoikeus"
                            "meluilmoitus"
                            "yl-uusi-toiminta"
                            "maa-aineslupa"
                            "vvvl-vesijohdosta"
                            ])))

(fact* "Attachments are transferred to the backing system when verdict has been given."
       (let [application (create-and-submit-application sonja :propertyId sipoo-property-id :address "Paatoskuja 10")
             application-id (:id application)
             first-attachment (get-in application [:attachments 0])]
            (:sent first-attachment) => nil
            (command sonja :approve-application :id application-id :lang "fi") => ok?

            (let [application (query-application sonja application-id) => truthy]
                 (fact "Application's attachments have no versions so no sent timestamps either"
                       (map :versions (:attachments application)) => (partial every? empty?)
                       (get-in application [:attachments 0 :sent]) => nil)

                 (give-verdict sonja application-id) => ok?
                 (upload-attachment sonja application-id first-attachment true)

                 (let [application (query-application sonja application-id) => truthy]
                      (command sonja :move-attachments-to-backing-system :id application-id :lang "fi" :attachmentIds (get-attachment-ids application)) => ok?)

                 (let [application (query-application sonja application-id) => truthy]
                      (get-in application [:attachments 0 :sent]) => pos?
                      (:state application) => "verdictGiven"))))

(fact* "Katselmus is transferred to the backing system"
       (let [application (create-and-submit-application sonja :propertyId sipoo-property-id :address "Katselmuskatu 17")
             application-id (:id application)
             _ (command sonja :assign-application :id application-id :assigneeId sonja-id) => ok?
             task-id (:taskId (command sonja :create-task :id application-id :taskName "do the shopping" :schemaName "task-katselmus")) => truthy]

            (upload-attachment-to-target sonja application-id nil true task-id "task")

            (command sonja :update-task :id application-id :doc task-id :updates [["katselmuksenLaji" "rakennekatselmus"]]) => ok?

            (command sonja :update-task :id application-id :doc task-id :updates [["rakennus.0.rakennus.jarjestysnumero" "1"]
                                                                                  ["rakennus.0.rakennus.rakennusnro" "001"]
                                                                                  ["rakennus.0.rakennus.valtakunnallinenNumero" "1234567892"]
                                                                                  ["rakennus.0.rakennus.kiinttun" (:propertyId application)]]) => ok?

            (command sonja :send-task :id application-id :taskId task-id :lang "fi") => fail?
            (command sonja :approve-task :id application-id :taskId task-id) => ok?
            (command sonja :send-task :id application-id :taskId task-id :lang "fi") => ok?

            (final-xml-validation
              (query-application sonja application-id)
              1                                             ; One attachment
              1
              (fn [xml]
                  (let [katselmus (xml/select1 xml [:RakennusvalvontaAsia :katselmustieto :Katselmus])
                        katselmuksenRakennus (xml/select1 xml [:katselmuksenRakennustieto :KatselmuksenRakennus])]
                       (xml/get-text katselmuksenRakennus :rakennusnro) => "001"
                       (xml/get-text katselmuksenRakennus :jarjestysnumero) => "1"
                       (xml/get-text katselmuksenRakennus :kiinttun) => (:propertyId application)
                       (xml/get-text katselmuksenRakennus :valtakunnallinenNumero) => "1234567892")))))

(fact* "Fully populated katselmus is transferred to the backing system"

       (fact "Meta test: KRYSP configs have different versions"
             (let [sipoo-r (organization-from-minimal-by-id "753-R")
                   jp-r (organization-from-minimal-by-id "186-R")]
                  sipoo-r => truthy
                  jp-r => truthy
                  (get-in sipoo-r [:krysp :R :version]) => "2.1.6"
                  (get-in jp-r [:krysp :R :version]) => "2.1.3"))

       (doseq [[apikey assignee property-id] [[sonja sonja-id sipoo-property-id] [raktark-jarvenpaa (id-for-key raktark-jarvenpaa) jarvenpaa-property-id]]]
              (let [application (create-and-submit-application apikey :propertyId property-id :address "Katselmuskatu 17")
                    application-id (:id application)
                    _ (command apikey :assign-application :id application-id :assigneeId assignee) => ok?
                    task-name "do the shopping"
                    task-id (:taskId (command apikey :create-task :id application-id :taskName task-name :schemaName "task-katselmus")) => truthy
                    application (query-application apikey application-id)]

                   (populate-task application task-id apikey) => ok?

                   (upload-attachment-to-target apikey application-id nil true task-id "task")
                   (upload-attachment-to-target apikey application-id nil true task-id "task" "muut.katselmuksen_tai_tarkastuksen_poytakirja")

                   (doseq [attachment (:attachments (query-application apikey application-id))]
                          (fact "sent timestamp not set"
                                (:sent attachment) => nil))

                   (command apikey :approve-task :id application-id :taskId task-id) => ok?
                   (command apikey :send-task :id application-id :taskId task-id :lang "fi") => ok?

                   (let [application (query-application apikey application-id)]
                        (final-xml-validation
                          application
                          1                                 ; Uploaded 2 regular attachments and
                          2                                 ; the other should be katselmuspoytakirja
                          (fn [xml]
                              (let [katselmus (xml/select1 xml [:RakennusvalvontaAsia :katselmustieto :Katselmus])
                                    poytakirja-edn (-> katselmus (xml/select1 [:katselmuspoytakirja]) xml/xml->edn :katselmuspoytakirja)]

                                   (validate-attachment poytakirja-edn "katselmuksen_tai_tarkastuksen_poytakirja" application)
                                   (fact "task name is transferred for muu katselmus type"
                                         (xml/get-text katselmus [:tarkastuksenTaiKatselmuksenNimi]) => task-name))))

                        (doseq [attachment (filter :latestVersion (:attachments application))]
                               (fact "sent timestamp is set"
                                     (:sent attachment) => number?))))))

;;
;; TODO: Fix this
;;
#_(fact* "Aloitusilmoitus is transferred to the backing system"
         (let [application (create-and-submit-application sonja :propertyId sipoo-property-id :address "Aloituspolku 1")
               application-id (:id application)
               _ (command sonja :assign-application :id application-id :assigneeId sonja-id) => ok?
               _ (command sonja :check-for-verdict :id application-id) => ok?
               application (query-application sonja application-id)
               building-1 (-> application :buildings first) => truthy

               _ (:state application) => "verdictGiven"

               _ (command sonja :inform-building-construction-started
                          :id application-id
                          :buildingIndex (:index building-1)
                          :startedDate "1.1.2015"
                          :lang "fi") => ok?]

              (fact "State has changed to construction started"
                    (:state (query-application sonja application-id)) => "constructionStarted")

              (final-xml-validation (query-application sonja application-id) 0 0)))
