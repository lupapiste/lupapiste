(ns lupapalvelu.xml.krysp.krysp-itest
    (:require [taoensso.timbre :as timbre :refer (trace debug info warn error fatal)]
      [clojure.string :as s]
      [clojure.java.io :as io]
      [clojure.data.xml :refer :all]
      [midje.sweet :refer :all]
      [midje.util :refer [testable-privates]]
      [net.cgrand.enlive-html :as enlive]
      [lupapalvelu.itest-util :refer :all]
      [lupapalvelu.factlet :refer :all]
      [lupapalvelu.domain :as domain]
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
      [sade.strings :as ss]
      [sade.core :refer [def- now]]
      [lupapalvelu.application :as ns-app])
    (:import [java.net URI]))

(apply-remote-minimal)

(testable-privates lupapalvelu.application required-link-permits)

(defn- populate-task [{:keys [id tasks]} task-id apikey]
       (let [task (some #(when (= (:id %) task-id) %) tasks)
             schema (model/get-document-schema task)
             data (tools/create-document-data schema (partial tools/dummy-values (id-for-key apikey)))
             readonly-schemas (filter :readonly (:body schema))
             ;; remove data where schema has readonly defined (katselmuksenLaji) as it's illegal to update readonly data
             data    (reduce (fn [final-data {schema-name :name}] (dissoc final-data (keyword schema-name))) data readonly-schemas)
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

(defn- generate-link-permit [{id :id :as application} apikey]
  (when (pos? (required-link-permits application))
    (fact "Add required link permit"
      (command apikey :add-link-permit :id id :linkPermitId "Kuntalupatunnus 123") => ok?)))

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
              (fact {:midje/description (str (ss/suffix linkkiliitteeseen "/") " is a PDF")}
                (.contains attachment-string "PDF") => true)
              (do
                (fact "linkki liiteeseen contains '_test-attachment.pdf'"
                  linkkiliitteeseen => #".+_test-attachment.pdf$")
                (fact "Liitetiedostossa on sisalto"
                  (not (nil? attachment-string)))
                (fact "Tyyppi on oikea" tyyppi => expected-type)))))

(defn- final-xml-validation [application autogenerated-attachment-count expected-attachment-count expected-sent-attachment-count & [additional-validator]]
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

            (fact "XML contains correct amount attachments"
              (count liitetieto) => (+ autogenerated-attachment-count expected-attachment-count))

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
                                            :YA #(nth % 2)
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

      (let [updated-application (generate-statement application-id sonja)]

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
              xml (element-to-xml canonical mapping)]
          (fact "xml exists" xml => truthy)))

      (fact "Application is not assigned"
        (get-in (query-application sonja application-id) [:authority :id]) => nil)

      (fact "Approve application"
        (let [resp (command sonja :approve-application :id application-id :lang "fi")]
          resp => ok?
          (:integrationAvailable resp) => true))

      (let [approved-application (query-application pena application-id)
            email (last-email)
            autogenarated-attachment-count 2 ; Two generated application pdf attachments
            expected-attachment-count 3 ; see generate-attachment, generate-statement+1
            expected-sent-attachment-count expected-attachment-count]
        (fact "application is sent" (:state approved-application) => "sent")
        (final-xml-validation approved-application autogenarated-attachment-count expected-attachment-count expected-sent-attachment-count)

        (:to email) => (contains (email-for-key pena))
        (:subject email) => "Lupapiste: Ryspitie 289 - hakemuksen tila muuttunut"
        (get-in email [:body :plain]) => (contains "K\u00e4sittelyss\u00e4")
        email => (partial contains-application-link? application-id "applicant"))

      (command sonja :request-for-complement :id application-id) => ok?

      (let [application (query-application pena application-id)
            email (last-email)]
        (:state application) => "complementNeeded"
        (-> application :history last :state) => "complementNeeded"
        (:to email) => (contains (email-for-key pena))
        (:subject email) => "Lupapiste: Ryspitie 289 - hakemuksen tila muuttunut"
        (get-in email [:body :plain]) => (contains "T\u00e4ydennett\u00e4v\u00e4n\u00e4")
        email => (partial contains-application-link? application-id "applicant")))))

(facts
  (doseq [op ["kerrostalo-rivitalo"
              "ya-sijoituslupa-ilmajohtojen-sijoittaminen"
              "ya-kayttolupa-mainostus-ja-viitoitus"
              "poikkeamis"
              "aloitusoikeus"
              "meluilmoitus"
              "yl-uusi-toiminta"
              "maa-aineslupa"
              "vvvl-vesijohdosta"]]

    (fact {:midje/description op} (do-test op))))

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

(fact* "Katselmus is transferred to the backing system with building info"
  (let [application (create-and-submit-application sonja :propertyId sipoo-property-id :address "Katselmuskatu 17")
        application-id (:id application)
        _ (command sonja :assign-application :id application-id :assigneeId sonja-id) => ok?
        _ (command sonja :check-for-verdict :id application-id) => ok?
        application (query-application sonja application-id)
        task-id (-> application :tasks first :id)
        building (first (:buildings application))
        ]

    (fact "Building has operation"
      (:operationId building) =not=> s/blank?)

    (upload-attachment-to-target sonja application-id nil true task-id "task") ; Related to task
    (upload-attachment-to-target sonja application-id nil true task-id "task" "katselmukset_ja_tarkastukset.katselmuksen_tai_tarkastuksen_poytakirja")

    (fact "Set state for building that was reviewed"
      (command sonja :update-task :id application-id :doc task-id :updates [["rakennus.0.tila.tila" "osittainen"]]) => ok?)

    (fact "Review done fails as missing required info for KRYSP transfer"
      (command sonja :review-done :id application-id :taskId task-id :lang "fi") => (partial expected-failure? :error.missing-parameters))

    (fact "Set state for the review"
      (command sonja :update-task :id application-id :doc task-id :updates [["katselmus.tila" "osittainen"]
                                                                           ["katselmus.pitoPvm" "12.04.2016"]
                                                                           ["katselmus.pitaja" "Sonja Sibbo"]]) => ok?)

    (fact "After filling required review data, transfer is ok"
      (command sonja :review-done :id application-id :taskId task-id :lang "fi") => ok?)

    (final-xml-validation
      (query-application sonja application-id)
      0
      2 ; Two attachments were uploaded above
      2
      (fn [xml]
        (let [katselmus (xml/select1 xml [:RakennusvalvontaAsia :katselmustieto :Katselmus])
              katselmuksenRakennus (xml/select1 xml [:katselmuksenRakennustieto :KatselmuksenRakennus])
              liitetieto (xml/select1 katselmus [:liitetieto])]

          (fact "Operation ID is transferred"
            (let [ids (xml/select katselmuksenRakennus [:MuuTunnus])
                  toimenpide-id (xml/select ids (enlive/has [:sovellus (enlive/text-pred (partial = "toimenpideId"))]))
                  lupapiste-id  (xml/select ids (enlive/has [:sovellus (enlive/text-pred (partial = "Lupapiste"))]))]

              (fact "with sovellus=toimenpideId"
                (xml/get-text toimenpide-id [:MuuTunnus :tunnus]) =not=> s/blank?)

              (fact "with sovellus=Lupapiste"
                (xml/get-text lupapiste-id [:MuuTunnus :tunnus]) =not=> s/blank?)))

          (xml/get-text katselmus :osittainen) => "osittainen"
          (xml/get-text liitetieto :kuvaus) => "Katselmuksen p\u00f6yt\u00e4kirja"
          (xml/get-text liitetieto ::tyyppi) => "katselmuksen_tai_tarkastuksen_poytakirja"
          (xml/get-text katselmuksenRakennus :rakennusnro) => (:localShortId building)
          (xml/get-text katselmuksenRakennus :jarjestysnumero) => (:index building)
          (xml/get-text katselmuksenRakennus :kiinttun) => (:propertyId building)
          (xml/get-text katselmuksenRakennus :valtakunnallinenNumero) => (:nationalId building))))))

(fact* "Fully populated katselmus is transferred to the backing system"

  (fact "Meta test: KRYSP configs have different versions"
    (let [sipoo-r (organization-from-minimal-by-id "753-R")
          jp-r (organization-from-minimal-by-id "186-R")]
      sipoo-r => truthy
      jp-r => truthy
      (get-in sipoo-r [:krysp :R :version]) => "2.2.0"
      (get-in jp-r [:krysp :R :version]) => "2.1.3"))

  (doseq [[apikey assignee property-id] [[sonja sonja-id sipoo-property-id] [raktark-jarvenpaa (id-for-key raktark-jarvenpaa) jarvenpaa-property-id]]]
    (let [application (create-and-submit-application apikey :propertyId property-id :address "Katselmuskatu 17")
          application-id (:id application)
          _ (command apikey :assign-application :id application-id :assigneeId assignee) => ok?
          task-name "do the shopping"
          task-id (:taskId (command apikey :create-task :id application-id :taskName task-name :schemaName "task-katselmus" :taskSubtype "aloituskokous")) => truthy
          application (query-application apikey application-id)]

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
        (final-xml-validation
          application
          0
          ; Uploaded 2 regular attachments,the other should be katselmuspoytakirja.
          ; In KRYSP 2.2.0+ both are wrapped in liitetieto elements.
          (if (= "753-R" (:organization application)) 2 1)
          2
          (fn [xml]
            (let [katselmus (xml/select1 xml [:RakennusvalvontaAsia :katselmustieto :Katselmus])
                  poytakirja (xml/xml->edn (or (xml/select1 katselmus [:katselmuspoytakirja]) (xml/select1 katselmus [:Liite])))
                  poytakirja-edn (or (:katselmuspoytakirja poytakirja) (:Liite poytakirja))]

              (validate-attachment poytakirja-edn "katselmuksen_tai_tarkastuksen_poytakirja" application)
              (fact "task name is transferred for muu katselmus type"
                (xml/get-text katselmus [:tarkastuksenTaiKatselmuksenNimi]) => task-name))))

        (doseq [attachment (filter :target (:attachments application))]
          (fact "sent timestamp is set"
            (:sent attachment) => number?)
          (fact "read only is true"
            (:readOnly attachment) => true))))))

(fact* "Only approved designers are transferred"
  (let [application (create-and-submit-application sonja :propertyId sipoo-property-id :address "Katselmuskatu 18")
        application-id (:id application)
        {paasuunnittelija :id}(domain/get-document-by-name application "paasuunnittelija")
        {suunnittelija1 :id} (domain/get-document-by-name application "suunnittelija")
        {suunnittelija2 :doc} (command sonja :create-doc :id application-id :schemaName "suunnittelija")]

       (command sonja :update-doc :id application-id :doc paasuunnittelija :updates [["henkilotiedot.sukunimi" "paasuunnittelija"]]) => ok?
       (command sonja :update-doc :id application-id :doc suunnittelija1 :updates [["henkilotiedot.sukunimi" "suunnittelija1"]]) => ok?
       (command sonja :update-doc :id application-id :doc suunnittelija2 :updates [["henkilotiedot.sukunimi" "suunnittelija2"]]) => ok?

       (command sonja :approve-doc :id application-id :doc paasuunnittelija :path nil :collection "documents") => ok?
       (command sonja :approve-doc :id application-id :doc suunnittelija2 :path nil :collection "documents") => ok?

       (command sonja :update-doc :id application-id :doc paasuunnittelija :updates [["henkilotiedot.etunimi" "etu"]]) => ok?

       (command sonja :approve-application :id application-id :lang "fi") => ok?

       (final-xml-validation
         (query-application sonja application-id)
         2 ; autogenerated
         0 ; no attachments are sent...
         0 ; ...so nothing is marked sent
         (fn [xml]
           (let [designers (map xml/get-text (xml/select xml [:Suunnittelija :sukunimi]))]
             (fact "Only suunnittelija2 is in XML message"
               ; suunnittelija1 has not been approved,
               ; paasuunnittelija was modified after approval
               (count designers) => 1
               (first designers) => "suunnittelija2"))))))

(facts "Attachments are linked with HTTP links in KRYSP XML if the organization so chooses"
       (let [application (create-and-open-application raktark-helsinki :propertyId "09141600550007" :address "Fleminginkatu 1")
             application-id (:id application)]

         (generate-documents application raktark-helsinki)
         (generate-attachment application raktark-helsinki "helsinki")

         (command raktark-helsinki :submit-application :id application-id :lang "fi") => ok?
         (command raktark-helsinki :approve-application :id application-id :lang "fi") => ok?

         (let [application (query-application raktark-helsinki application-id)
               permit-type (keyword (permit/permit-type application))
               app-attachments (filter :latestVersion (:attachments application))
               organization (organization-from-minimal-by-id (:organization application))
               sftp-user (get-in organization [:krysp permit-type :ftpUser])
               krysp-version (get-in organization [:krysp permit-type :version])
               permit-type-dir (permit/get-sftp-directory permit-type)
               output-dir (str "target/" sftp-user permit-type-dir "/")
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
               liite-edn (-> liitetieto last (xml/select1 [:Liite]) xml/xml->edn :Liite (util/ensure-sequential :metatietotieto))
               liite-id (->> liite-edn
                             :metatietotieto
                             (map #(or (:metatieto %) (:Metatieto %)))
                             (some #(when (= "liiteId" (:metatietoNimi %)) (:metatietoArvo %))))
               app-attachment (some #(when (= liite-id (:id %)) %) app-attachments)
               attachment-urls (map
                                 (fn [liite]
                                   (-> (xml/select1 liite [:Liite])
                                       xml/xml->edn
                                       :Liite
                                       :linkkiliitteeseen))
                                 liitetieto)
               host (env/value :host)]

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
                  count)
             => 1)

           (fact "XML has corresponding attachment in app" app-attachment => truthy)

           (fact "XML contains HTTP links for attachments"
             attachment-urls => (contains [(str host "/api/raw/pdf-export?id=LP-091-2016-90001&lang=fi")
                                           (str host "/api/raw/submitted-application-pdf-export?id=LP-091-2016-90001&lang=fi")
                                           (str host "/api/raw/latest-attachment-version?attachment-id=" liite-id)]
                                          :in-any-order)))))

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

              (final-xml-validation (query-application sonja application-id) 0 0 0)))
