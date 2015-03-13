(ns lupapalvelu.xml.asianhallinta.asianhallinta-itest
  (:require [clojure.java.io :as io]
            [clojure.data.xml :as xml]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.factlet :as fl]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.xml.asianhallinta.core :as ah]
            [lupapalvelu.xml.validator :as validator]
            [sade.core :refer [now]]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.xml :as sxml]
            [sade.util :as util])
  (:import [java.net URI]))

(apply-remote-minimal)

(testable-privates lupapalvelu.xml.asianhallinta.core resolve-output-directory resolve-ah-version)
(testable-privates lupapalvelu.xml.asianhallinta.uusi_asia_mapping attachments-for-write)

(fl/facts* "Asianhallinta itest"
  (facts "UusiAsia from poikkeamis application"
    (let [app-id (create-app-id
                    pena
                    :municipality velho-muni
                    :operation "poikkeamis"
                    :propertyId "29703401070010"
                    :y 6965051.2333374 :x 535179.5
                    :address "Suusaarenkierto 44") => truthy
          application (query-application pena app-id) => truthy
          organization (organization/resolve-organization velho-muni (:permitType application)) => truthy
          scope  (organization/resolve-organization-scope velho-muni (:permitType application) organization) => truthy]
      (:caseManagement scope) => truthy

      (generate-documents application pena)
      (upload-attachment-to-all-placeholders pena application)

      (command pena :submit-application :id app-id) => ok?
      (fact "Pena can't move application to asianhallinta"
        (command pena :application-to-asianhallinta :id app-id :lang "fi") => unauthorized?)

      (command velho :application-to-asianhallinta :id app-id :lang "fi") => ok?

      (let [updated-application (query-application velho app-id)
            email (last-email)]

        (:to email) => (contains (email-for-key pena))
        (:subject email) => (contains "hakemuksen tila muuttunut")
        (get-in email [:body :plain]) => (contains "K\u00e4sittelyss\u00e4")


        (fact "Application is sent and timestamp is there"
          (:state updated-application) => "sent"
          (:sent updated-application) => util/pos?)

        (fact "Attachments have sent timestamp"
          (every? #(-> % :sent util/pos?) (:attachments updated-application)) => true)

        (facts "XML file"
          (let [output-dir (str (resolve-output-directory scope) "/")
                target-file-name (str "target/Downloaded-" (:id application) "-" (now) ".xml")
                filename-starts-with (:id application)
                xml-file (if get-files-from-sftp-server?
                           (io/file (get-file-from-server
                                      (get-in scope [:caseManagement :ftpUser])
                                      (subs (env/value :fileserver-address) 7)
                                      filename-starts-with
                                      target-file-name
                                      (str ah/ah-from-dir "/")))
                           (io/file (get-local-filename output-dir filename-starts-with)))
                xml-as-string (slurp xml-file)
                xml (xml/parse (io/reader xml-file))]

            (fact "Correctly named xml file is created" (.exists xml-file) => true)

            (fact "XML file is valid" ;; Validate again??
              (validator/validate xml-as-string (:permitType updated-application) (str "ah-" (resolve-ah-version scope))))

            (fact "Application IDs match"
              (sxml/get-text xml [:UusiAsia :HakemusTunnus]) => (:id updated-application))

            (fact "Attachments are correct"
              (let [xml-attachments (sxml/select xml [:UusiAsia :Liitteet :Liite])
                    writed-attachments (attachments-for-write updated-application)
                    last-link (sxml/get-text (last xml-attachments) [:LinkkiLiitteeseen])
                    filename (last (ss/split last-link #"/"))
                    linkki-as-uri (URI. last-link)
                    attachment-file (if get-files-from-sftp-server?
                                      (get-file-from-server
                                        (get-in scope [:caseManagement :ftpUser])
                                        (.getHost linkki-as-uri)
                                        (.getPath linkki-as-uri)
                                        (str "target/Downloaded-" filename))
                                      (str output-dir filename))
                    attachment-string (slurp attachment-file)]

                (count xml-attachments) => (+ (count (:attachments updated-application)) 2) ; 2 x Genereated PDFs

                (fact "Filename in XML and filename written to disk are the same"
                  filename => (-> writed-attachments first :filename))

                (fact "Filename content is correct" attachment-string => "This is test file for file upload in itest.")))

            (fact "Operations are correct"
              (let [operations (sxml/select xml [:UusiAsia :Toimenpiteet :Toimenpide])]
                (count operations) => (count (:operations updated-application))
                (sxml/get-text operations [:ToimenpideTunnus]) => (-> updated-application :operations first :name)))
            (fact
              "Link permits are correct"
              (let [links (sxml/select xml [:UusiAsia :Viiteluvat :Viitelupa :MuuTunnus])]
                (>pprint links)
                )))))))


  (fact "Can't create asianhallinta with non-asianhallinta operation"
    (let [app-id (create-app-id
                    pena
                    :municipality velho-muni
                    :operation "asuinrakennus"
                    :propertyId "29703401070010"
                    :y 6965051.2333374 :x 535179.5
                    :address "Suusaarenkierto 44") => truthy
          app (query-application pena app-id)]
      (command pena :submit-application :id app-id) => ok?
      (command velho :application-to-asianhallinta :id app-id :lang "fi") => (partial expected-failure? "error.operations.asianhallinta-disabled")))

  (fact "If asianhallinta is not set error occurs"
    (let [app-id (create-app-id
                    pena
                    :municipality sonja-muni
                    :operation "poikkeamis"
                    :propertyId "75312312341234"
                    :x 444444 :y 6666666
                    :address "foo 42, bar") => truthy
          app (query-application pena app-id)]
      (command pena :submit-application :id app-id) => ok?
      (command sonja :application-to-asianhallinta :id app-id :lang "fi") => (partial expected-failure? "error.integration.asianhallinta-disabled")))

  (facts "Auth admin configs"
    (fact "Pena can't use asianhallinta configure query & command"
      (query pena "asianhallinta-config") => unauthorized?
      (command pena "save-asianhallinta-config" :permitType "P" :municipality sonja-muni :enabled true :version "1.3") => unauthorized?)
    (fact "Sonja can't use asianhallinta configure query & command"
      (query sonja "asianhallinta-config") => unauthorized?
      (command sonja "save-asianhallinta-config" :permitType "P" :municipality sonja-muni :enabled true :version "1.3") => unauthorized?)

    (fact "Sipoo auth admin can query asianhallinta-config, response has scope, but no caseManagement"
      (let [resp (query sipoo "asianhallinta-config")
            scope (:scope resp)]
        resp => ok?
        scope => truthy
        (some :caseManagement scope) => nil?))

    (facts "Kuopio auth admin"
      (fact "query asianhallinta-config, response has scope with one caseManagement"
        (let [resp      (query kuopio "asianhallinta-config")
              ah-config (some :caseManagement (:scope resp))]
          resp => ok?
          ah-config => {:enabled true :ftpUser "dev_ah_kuopio" :version "1.1"}))
      (fact "admin can disable asianhallinta using command"
        (command kuopio "save-asianhallinta-config" :permitType "P" :municipality velho-muni :enabled false :version "1.1")
        (let [resp (query kuopio "asianhallinta-config")
              ah-config (some :caseManagement (:scope resp))]
          (:enabled ah-config) => false))
      (fact "admin can enable asianhallinta and change version using command"
        (command kuopio "save-asianhallinta-config" :permitType "P" :municipality velho-muni :enabled true :version "lol")
          (let [resp (query kuopio "asianhallinta-config")
                ah-config (some :caseManagement (:scope resp))]
            (:enabled ah-config) => true
            (:version ah-config) => "lol"))))

  (fact "Fail when organization has unsupported version selected"
    (let [app-id (create-app-id
                    pena
                    :municipality velho-muni
                    :operation "poikkeamis"
                    :propertyId "29703401070010"
                    :y 6965051.2333374 :x 535179.5
                    :address "Suusaarenkierto 44") => truthy
          app (query-application pena app-id)]
      (command pena :submit-application :id app-id) => ok?
      (command velho :application-to-asianhallinta :id app-id :lang "fi") => (partial
                                                                               expected-failure?
                                                                               "error.integration.asianhallinta-version-wrong-form")))

  (fact "Fail when organization has version missing"
    (let [_ (command kuopio "save-asianhallinta-config" :permitType "P" :municipality velho-muni :enabled true :version false)
          app-id (create-app-id
                    pena
                    :municipality velho-muni
                    :operation "poikkeamis"
                    :propertyId "29703401070010"
                    :y 6965051.2333374 :x 535179.5
                    :address "Suusaarenkierto 44") => truthy
          app (query-application pena app-id)]
      (command pena :submit-application :id app-id) => ok?
      (command velho :application-to-asianhallinta :id app-id :lang "fi") => (partial
                                                                               expected-failure?
                                                                               "error.integration.asianhallinta-version-missing"))))
