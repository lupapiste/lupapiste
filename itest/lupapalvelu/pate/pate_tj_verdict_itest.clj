(ns lupapalvelu.pate.pate-tj-verdict-itest
  (:require [clojure.data.xml :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [lupapalvelu.fixture.pate-verdict :as pate-fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.xml.validator :refer [validate]]
            [midje.sweet :refer :all]
            [sade.core :as core]
            [sade.env :as env]
            [sade.date :as date]
            [sade.util :as util]
            [sade.xml :as xml]))

(apply-remote-fixture "pate-verdict")

(def app-id (create-app-id pena
                           :propertyId sipoo-property-id
                           :operation  :kerrostalo-rivitalo))

(def tj-verdict-date (core/now))

(facts "PATE TJ verdicts"

  (fact "Enable pate sftp"
    (command admin :update-organization
             :permitType "R"
             :municipality "753"
             :pateEnabled true
             :pateSftp true) => ok?)

  (facts "Submit and approve application"
    (command pena :submit-application :id app-id) => ok?
    (command sonja :update-app-bulletin-op-description :id app-id :description "Bulletin description") => ok?
    (command sonja :approve-application :id app-id :lang "fi") => ok?)

  (facts "Create, submit and approve foreman application"
    (let [{foreman-app-id :id} (command sonja :create-foreman-application :id app-id
                                        :taskId ""
                                        :foremanRole "KVV-ty\u00f6njohtaja"
                                        :foremanEmail "johtaja@mail.com")]
    (command sonja :change-permit-sub-type :id foreman-app-id :permitSubtype "tyonjohtaja-hakemus") => ok?
    (upload-attachment sonja foreman-app-id {:type {:type-group "paatoksenteko", :type-id "paatosote"}} true)
    (upload-attachment sonja foreman-app-id {:type {:type-group "osapuolet", :type-id "cv"}} true)
    (command sonja :submit-application :id foreman-app-id) => ok?
    (command sonja :approve-application :id foreman-app-id :lang "fi") => ok?

    (facts "Create, edit and publish TJ verdict"
      (let [template-id              (->> (:templates pate-fixture/verdict-templates-setting-r)
                                          (filter #(= "tj" (:category %)))
                                          (first)
                                          :id)
            {verdict-id :verdict-id} (command sonja :new-pate-verdict-draft :id foreman-app-id :template-id template-id)]

        (fact "Set automatic calculation of other dates"
          (command sonja :edit-pate-verdict :id foreman-app-id :verdict-id verdict-id
                   :path [:automatic-verdict-dates] :value true) => no-errors?)
        (fact "Verdict date"
          (command sonja :edit-pate-verdict :id foreman-app-id :verdict-id verdict-id
                   :path [:verdict-date] :value tj-verdict-date) => no-errors?)
        (fact "Verdict code"
          (command sonja :edit-pate-verdict :id foreman-app-id :verdict-id verdict-id
                   :path [:verdict-code] :value "hyvaksytty") => no-errors?)
        (fact "Start day for responsibilities"
          (command sonja :edit-pate-verdict :id foreman-app-id :verdict-id verdict-id
                   :path [:responsibilities-start-date] :value tj-verdict-date) => no-errors?)
        (fact "Publish verdict"
          (command sonja :publish-pate-verdict :id foreman-app-id :verdict-id verdict-id) => no-errors?)
        (verdict-pdf-queue-test sonja {:app-id     foreman-app-id
                                       :verdict-id verdict-id
                                       :state      "foremanVerdictGiven"})
        (check-verdict-date sonja foreman-app-id tj-verdict-date)
        (fact "Applicant cant create verdict"
          (command pena :new-pate-verdict-draft :id foreman-app-id :template-id template-id) => unauthorized?)

        (facts "Xml is created and is valid"
          (let [foreman-application (query-application sonja foreman-app-id)
                permit-type (keyword (permit/permit-type foreman-application))
                app-attachments (filter :latestVersion (:attachments foreman-application))
                organization (organization-from-minimal-by-id (:organization foreman-application))
                sftp-user (get-in organization [:krysp permit-type :ftpUser])
                krysp-version (get-in organization [:krysp permit-type :version])
                permit-type-dir (permit/get-sftp-directory permit-type)
                output-dir (str "target/" sftp-user permit-type-dir "/")
                sftp-server (subs (env/value :fileserver-address) 7)
                target-file-name (str "target/Downloaded-" (:id foreman-application) "-" (core/now) "_verdict" ".xml")
                filename-starts-with (:id foreman-application)
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
                liite-edn  (-> liitetieto last (xml/select1 [:Liite]) xml/xml->edn :Liite (util/ensure-sequential :metatietotieto))
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
                url-contains-filename #(string/includes? % "test-gif-attachment.gif")]

            (fact "Query returns right application"
              (:id foreman-application) => foreman-app-id)

            (fact "Correctly named xml file is created" (.exists xml-file) => true)

            (fact "XML file is valid"
              (validate xml-as-string (:permitType foreman-application) krysp-version))

            (fact "XML has right application id"
              (xml/get-text xml [:LupaTunnus :tunnus]) => (:id foreman-application))

            (fact "Kayttotapaus is 'Uuden tyonjohtajan nimeaminen'"
              (xml/get-text xml [:kayttotapaus]) => "Uuden ty\u00f6njohtajan nime\u00e4minen")

            (facts "Paatostieto is correct"
              (let [paatostieto (xml/select xml [:paatostieto])
                    paatos      (xml/select1 paatostieto [:Paatos])
                    poytakirja  (xml/select paatos [:poytakirja])
                    liite       (xml/select poytakirja [:liite])]
                (fact "Paatostieto contains only one Paatos"
                  (count paatostieto) => 1)

                (facts "poytakirja is correct"
                  (fact "paatoskoodi" (xml/get-text poytakirja [:paatoskoodi]) => "hyväksytty")
                  (fact "paatoskoodi" (xml/get-text poytakirja [:paatoksentekija]) => "Sonja Sibbo")
                  (fact "paatospvm"   (xml/get-text poytakirja [:paatospvm]) => (date/xml-date tj-verdict-date)))

                ;;TODO VALIDATE METATIETO OF ATTACHMENT
                (facts "poytakirja contains correct liitetieto"
                  (fact "kuvaus" (xml/get-text liite [:kuvaus]) => "Päätös: Päätös")
                  (fact "linkki"
                    (xml/get-text liite [:linkkiliitteeseen])
                    => (contains "-90002 Paatos"))
                  (fact "versionumero" (xml/get-text liite [:versionumero]) => "0.1")
                  (fact "tyyppi" (xml/get-text liite [:tyyppi]) => "paatos"))))

            (when (env/feature? :attachments-for-foreman-verdicts)
              (fact "XML contains correct amount of attachments"
                (count liitetieto) => 2)

              (fact "XML has corresponding attachment in app" app-attachment => truthy)

              (fact "XML contains links for attachments"
                attachment-urls => (just [url-contains-filename url-contains-filename])))))

        (facts "TJ verdict is published with corrected data"
          (let [application        (query-application sonja foreman-app-id)
                tj-verdict         (first (filter #(= verdict-id (:id %)) (:pate-verdicts application)))
                verdict-attachment (first (filter #(= verdict-id (-> % :source :id)) (:attachments application)))]
            (fact "Category" (:category tj-verdict) => "tj")
            (fact "verdict has the correct attachment id"
              (get-in tj-verdict [:published :attachment-id]) => (:id verdict-attachment))
            (fact "verdict attachment has sent timestamp"
              (:sent verdict-attachment) => pos?)
            (fact "Verdict code" (get-in tj-verdict [:data :verdict-code]) => "hyvaksytty"
              (fact "Verdict text" (get-in tj-verdict [:data :verdict-text]) => "Paatos annettu\n"
                (fact "Verdict date" (get-in tj-verdict [:data :verdict-date]) => tj-verdict-date
                  (fact "Appeal" (get-in tj-verdict [:data :appeal]) => "Ohje muutoksen hakuun.\n"
                    (fact "Handler" (get-in tj-verdict [:data :handler]) => "Sonja Sibbo"))))))))))))
