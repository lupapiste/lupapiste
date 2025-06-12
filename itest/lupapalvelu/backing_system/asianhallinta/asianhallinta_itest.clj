(ns lupapalvelu.backing-system.asianhallinta.asianhallinta-itest
  (:require [clojure.data.xml :as xml]
            [clojure.java.io :as io]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.minimal-util :as min-util]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.sftp.context :as sftp-ctx]
            [lupapalvelu.xml.validator :as validator]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.core :refer [now]]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as sxml])
  (:import [java.net URI]))

(set! *warn-on-reflection* true)

(def sipoo-R-org-id "753-R")
(def kuopio-R-org-id "297-R")
(def ah-from-dir (str (ss/join-file-path sftp-ctx/CASE-MANAGEMENT sftp-ctx/CASE-MANAGEMENT-OUT) "/"))

(testable-privates lupapalvelu.backing-system.asianhallinta.core resolve-ah-version)
(testable-privates lupapalvelu.backing-system.asianhallinta.asianhallinta-mapping attachments-for-write)

(defn from-dir
  "This works since we now that the "
  [organization application]
  (-> (sftp-ctx/files-context organization application)
      :directory
      (str "/")))

(defn- resolve-organization-scope
  [municipality permit-type organization]
  (fact "Municipality provided?" municipality => truthy)
  (fact "Valid permit-type?" (permit/valid-permit-type? permit-type) => truthy)
  (fact "Organization provided?" organization => truthy)
  (let [scope (organization/resolve-organization-scope municipality
                                                       permit-type
                                                       organization)]
    (fact {:midje/description (format "Scope found for %s / %s / %s"
                                      municipality permit-type organization)}
      scope => truthy)
    scope))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components
                      test-system/with-mock-laundry)
  (facts "Asianhallinta itest"
    (facts "UusiAsia from poikkeamis application"
      (let [application  (create-and-submit-application
                           pena
                           :propertyId kuopio-property-id
                           :operation "poikkeamis"
                           :propertyId "29703401070010"
                           :y 6965051.2333374 :x 535179.5
                           :address "Suusaarenkierto 44")
            app-id       (:id application)
            organization (:data (query admin :organization-by-id
                                       :organizationId (:organization application)))
            scope        (resolve-organization-scope velho-muni
                                                     (:permitType application)
                                                     organization)]
        (keys (:caseManagement scope)) => (just [:ftpUser :version :enabled] :in-any-order)

        (generate-documents! application pena)
        (upload-attachment-to-all-placeholders pena application)

        (fact "Pena can't move application to asianhallinta"
          (command pena :application-to-asianhallinta :id app-id :lang "fi") => unauthorized?)

        (command velho :application-to-asianhallinta :id app-id :lang "fi") => ok?

        (let [updated-application (query-application velho app-id)
              email               (last-email)]

          (:to email) => (contains (min-util/email-for-key pena))
          (:subject email) => (contains "Kuopio - hankkeen tila on nyt K\u00e4sittelyss\u00e4")
          (get-in email [:body :plain]) => (contains "K\u00e4sittelyss\u00e4")


          (fact "Application is sent and timestamp is there"
            (-> updated-application :history last :state) => "sent"
            (:state updated-application) => "sent"
            (:sent updated-application) => util/pos?)

          (fact "Attachments have sent timestamp"
            (every? #(-> % :sent util/pos?) (:attachments updated-application)) => true)

          (Thread/sleep 2000) ;; Wait a bit for the SFTP to be updated

          (facts "XML file"
            (let [output-dir           (from-dir organization application)
                  target-file-name     (str "target/Downloaded-" app-id "-" (now) ".xml")
                  filename-starts-with app-id
                  xml-file             (if get-files-from-sftp-server?
                                         (io/file (get-file-from-server
                                                    (get-in scope [:caseManagement :ftpUser])
                                                    (subs (env/value :fileserver-address) 7)
                                                    filename-starts-with
                                                    target-file-name
                                                    ah-from-dir))
                                         (io/file (get-local-filename output-dir filename-starts-with)))
                  xml-as-string        (slurp xml-file)
                  xml                  (xml/parse (io/reader xml-file))]

              (fact "Correctly named xml file is created" (.exists xml-file) => true)

              (fact "XML file is valid"
                (validator/validate xml-as-string (:permitType updated-application) (str "ah-" (resolve-ah-version scope))))

              (fact "Application IDs match"
                (sxml/get-text xml [:UusiAsia :HakemusTunnus]) => (:id updated-application))

              (fact "Attachments are correct"
                (let [xml-attachments   (sxml/select xml [:UusiAsia :Liitteet :Liite])
                      written-atts      (attachments-for-write (:attachments updated-application))
                      last-link         (sxml/get-text (last xml-attachments) [:LinkkiLiitteeseen])
                      filename          (last (ss/split last-link #"/"))
                      linkki-as-uri     (URI. last-link)
                      attachment-file   (if get-files-from-sftp-server?
                                          (get-file-from-server
                                            (get-in scope [:caseManagement :ftpUser])
                                            (.getHost linkki-as-uri)
                                            (.getPath linkki-as-uri)
                                            (str "target/Downloaded-" filename))
                                          (str output-dir filename))
                      attachment-string (slurp attachment-file)]

                  (count xml-attachments) => (+ (count (:attachments updated-application)) 2) ; 2 x Generated PDFs

                  (fact "Filename in XML and filename written to disk are the same"
                    (util/find-by-key :filename filename written-atts) => truthy)

                  (fact "File has content"
                    attachment-string => ss/not-blank?)))

              (fact "Operations are correct"
                (let [operations (sxml/select xml [:UusiAsia :Toimenpiteet :Toimenpide])]
                  (count operations) => (count (conj (:secondaryOperations updated-application) (:primaryOperation updated-application)))
                  (sxml/get-text operations [:ToimenpideTunnus]) => (-> updated-application :primaryOperation :name)))

              (fact "Link permits do not exist" (sxml/select xml [:UusiAsia :Viiteluvat]) => empty?))))))

    (facts "UusiAsia with link permits"
      (let [link-app     (create-and-submit-application
                           pena
                           :propertyId kuopio-property-id
                           :operation "asuinrakennus"
                           :propertyId "29703401070010"
                           :y 6965051.2333374 :x 535179.5
                           :address "Suusaarenkierto 44")
            link-app-id  (:id link-app)
            _            (give-legacy-verdict velho link-app-id :R true :kuntalupatunnus "KLTunnus1")
            manual-link  "Another kuntalupatunnus"

            application  (create-and-submit-application ; Actual app for asianhallinta
                           pena
                           :propertyId kuopio-property-id
                           :operation "poikkeamis"
                           :propertyId "29703401070010"
                           :y 6965051.2333374 :x 535179.5
                           :address "Suusaarenkierto 44")
            app-id       (:id application)
            organization (:data (query admin :organization-by-id
                                       :organizationId (:organization application)))
            scope        (resolve-organization-scope velho-muni (:permitType application) organization)]

        (generate-documents! application pena)
        (command pena :add-link-permit :id app-id :linkPermitId link-app-id) ; link-app-id has verdict with kuntalupatunnus
        (command pena :add-link-permit :id app-id :linkPermitId manual-link) ; manual kuntalupatunnus

        (command velho :application-to-asianhallinta :id app-id :lang "fi") => ok?

        (facts "XML file"
          (let [output-dir           (from-dir organization application)
                target-file-name     (str "target/Downloaded-" app-id "-" (now) ".xml")
                filename-starts-with app-id
                xml-file             (if get-files-from-sftp-server?
                                       (io/file (get-file-from-server
                                                  (get-in scope [:caseManagement :ftpUser])
                                                  (subs (env/value :fileserver-address) 7)
                                                  filename-starts-with
                                                  target-file-name
                                                  ah-from-dir))
                                       (io/file (get-local-filename output-dir filename-starts-with)))
                xml-as-string        (slurp xml-file)
                xml                  (xml/parse (io/reader xml-file))]
            (fact "XML file is valid"
              (validator/validate xml-as-string (:permitType application) (str "ah-" (resolve-ah-version scope))))

            (fact "Viiteluvat elements"
              (let [contents (sxml/select xml [:UusiAsia :Viiteluvat :Viitelupa])]
                (fact "Three link permits are present" (count contents) => 3)

                (fact "One is link to LP application"
                  (some
                    #(and
                       (= link-app-id (sxml/get-text % [:Viitelupa :MuuTunnus :Tunnus]))
                       (= "Lupapiste" (sxml/get-text % [:Viitelupa :MuuTunnus :Sovellus])))
                    contents) => truthy)

                (fact "One is link to link-permit's verdict"
                  (some
                    #(= "KLTunnus1" (sxml/get-text % [:Viitelupa :AsianTunnus]))
                    contents) => truthy)

                (fact "One is link to manual link"
                  (some
                    #(= manual-link (sxml/get-text % [:Viitelupa :AsianTunnus]))
                    contents) => truthy)))))))

    (facts "AsianTaydennys"
      (let [application  (create-and-submit-application
                           pena
                           :propertyId kuopio-property-id
                           :operation "poikkeamis"
                           :propertyId "29703401070010"
                           :y 6965051.2333374 :x 535179.5
                           :address "Suusaarenkierto 44")
            app-id       (:id application)
            organization (:data (query admin :organization-by-id
                                       :organizationId (:organization application)))
            scope        (resolve-organization-scope velho-muni (:permitType application) organization)]
        (keys (:caseManagement scope)) => (just [:ftpUser :version :enabled] :in-any-order)
        (generate-documents! application pena)
        (upload-attachment-to-all-placeholders pena application)
        (fact "Unable to send attachments if application is not yet in asianhallinta"
          (command velho :attachments-to-asianhallinta :id app-id :attachmentIds [] :lang "fi") =not=> ok?)
        (command velho :application-to-asianhallinta :id app-id :lang "fi") => ok?

        (Thread/sleep 1000) ;wait for a while so that TaydennysAsiaan xml gets later timestamp in fs and gets loaded later in the test (problem atleast with mac)

        (fact "Attachment can be reset"
          (command velho :attachments-to-asianhallinta :id app-id
                   :attachmentIds [(:id (first (:attachments application)))]
                   :lang "fi")
          => ok?)

        (fact "Able to send attachment with new file version"
          (let [versioned-attachment (first (:attachments (query-application velho (:id application))))]
            (upload-attachment velho (:id application) versioned-attachment true)
            (command velho :attachments-to-asianhallinta :id app-id :attachmentIds [(:id versioned-attachment)] :lang "fi") => ok?))

        (Thread/sleep 2000) ;; Wait a bit for the SFTP to be updated

        (facts "XML file"
          (let [updated-application  (query-application pena app-id)
                output-dir           (from-dir organization application)
                target-file-name     (str "target/Downloaded-" app-id "-" (now) ".xml")
                filename-starts-with app-id
                xml-file             (if get-files-from-sftp-server?
                                       (io/file (get-file-from-server
                                                  (get-in scope [:caseManagement :ftpUser])
                                                  (subs (env/value :fileserver-address) 7)
                                                  filename-starts-with
                                                  target-file-name
                                                  ah-from-dir))
                                       (io/file (get-local-filename output-dir filename-starts-with)))
                xml-as-string        (slurp xml-file)
                xml                  (xml/parse (io/reader xml-file))]

            (fact "Correctly named xml file is created"
              (.exists xml-file) => true)

            (fact "XML file is valid"
              (validator/validate xml-as-string (:permitType updated-application) (str "ah-" (resolve-ah-version scope))))

            (fact "Application IDs match"
              (sxml/get-text xml [:TaydennysAsiaan :HakemusTunnus]) => (:id updated-application))

            (fact "Attachments are correct"
              (let [xml-attachments     (sxml/select xml [:TaydennysAsiaan :Liitteet :Liite])
                    written-attachments (attachments-for-write (:attachments updated-application))
                    last-link           (sxml/get-text (last xml-attachments) [:LinkkiLiitteeseen])
                    filename            (last (ss/split last-link #"/"))
                    linkki-as-uri       (URI. last-link)
                    attachment-file     (if get-files-from-sftp-server?
                                          (get-file-from-server
                                            (get-in scope [:caseManagement :ftpUser])
                                            (.getHost linkki-as-uri)
                                            (.getPath linkki-as-uri)
                                            (str "target/Downloaded-" filename))
                                          (str output-dir filename))
                    attachment-string   (slurp attachment-file)]

                (count xml-attachments) => (count (:attachments updated-application))
                (fact "Filename in XML and filename written to disk are the same"
                  (util/find-by-key :filename filename written-attachments) => truthy)
                (fact "File has content"
                  attachment-string => ss/not-blank?)))))))

    (fact "Can't create asianhallinta with non-asianhallinta operation"
      (let [app-id (:id (create-and-submit-application
                          pena
                          :propertyId kuopio-property-id
                          :operation "asuinrakennus"
                          :propertyId "29703401070010"
                          :y 6965051.2333374 :x 535179.5
                          :address "Suusaarenkierto 44"))]
        (command velho :application-to-asianhallinta :id app-id :lang "fi")
        => (partial expected-failure? "error.integration.asianhallinta-disabled")))

    (fact "If asianhallinta is not set error occurs"
      (let [app-id (:id (create-and-submit-application
                          pena
                          :propertyId sipoo-property-id
                          :operation "poikkeamis"
                          :propertyId "75312312341234"
                          :x 444444 :y 6666666
                          :address "foo 42, bar"))]
        (command sonja :application-to-asianhallinta :id app-id :lang "fi")
        => (partial expected-failure? "error.integration.asianhallinta-disabled")))

    (facts "Auth admin configs"
      (fact "Pena can't use asianhallinta configure query & command"
        (query pena "integration-config" :organizationId sipoo-R-org-id) => unauthorized?
        (command pena "save-integration-config" :organizationId sipoo-R-org-id
                 :config {:targets          ["P"]
                          :integration-type "sftp"
                          :enabled?         true
                          :options          {:version "1.3"}})
        => unauthorized?)
      (fact "Sonja can't use asianhallinta configure query & command"
        (query sonja "integration-config" :organizationId sipoo-R-org-id) => unauthorized?
        (command sonja "save-integration-config" :organizationId sipoo-R-org-id
                 :config {:targets          ["P"]
                          :integration-type "sftp"
                          :enabled?         true
                          :options          {:version "1.3"}})
        => unauthorized?)

      (fact "Sipoo auth admin can query integration-config, response has no suitable configs"
        (let [resp    (query sipoo "integration-config" :organizationId sipoo-R-org-id)
              configs (:configs resp)]
          resp => ok?
          configs => empty?))

      (facts "Kuopio auth admin"
        (fact "query integration-config, response has configs with one config having FTP selected"
          (let [resp      (query kuopio "integration-config" :organizationId kuopio-R-org-id)
                configs   (-> resp :data :configs)
                ah-config (some #(when (-> % :integration-type (= "sftp")) %) configs)]
            resp => ok?
            ah-config => {:id               "297-P"
                          :enabled?         true
                          :targets          ["P"]
                          :integration-type "sftp"
                          :options          {:version "1.1"}}))
        (fact "admin can disable asianhallinta using command"
          (command kuopio "save-integration-config" :organizationId kuopio-R-org-id
                   :config {:targets          ["P"]
                            :enabled?         false
                            :integration-type "sftp"
                            :options          {:version "1.1"}})
          (let [resp      (query kuopio "integration-config" :organizationId kuopio-R-org-id)
                configs   (-> resp :data :configs)
                ah-config (some #(when (= (:targets %) ["P"]) %) configs)]
            (:enabled? ah-config) => false))
        (fact "admin can enable asianhallinta and change version using command"
          (command kuopio "save-integration-config" :organizationId kuopio-R-org-id
                   :config {:targets          ["P"]
                            :enabled?         true
                            :integration-type "sftp"
                            :options          {:version "lol"}})
          (let [resp      (query kuopio "integration-config" :organizationId kuopio-R-org-id)
                configs   (-> resp :data :configs)
                ah-config (some #(when (= (:targets %) ["P"]) %) configs)]
            (:enabled? ah-config) => true
            (-> ah-config :options :version) => "lol")))
      (fact "R can't be set"
        (command kuopio "save-integration-config" :organizationId kuopio-R-org-id
                 :config {:targets          ["R"]
                          :enabled?         true
                          :integration-type "sftp"
                          :options          {:version "lol"}})
        => (partial expected-failure? :error.invalid-configuration)))

    (fact "Fail when organization has unsupported version selected"
      (let [app-id (:id (create-and-submit-application
                          pena
                          :propertyId kuopio-property-id
                          :operation "poikkeamis"
                          :propertyId "29703401070010"
                          :y 6965051.2333374 :x 535179.5
                          :address "Suusaarenkierto 44"))]
        (command velho :application-to-asianhallinta :id app-id :lang "fi")
        => (partial
             expected-failure?
             "error.integration.asianhallinta-version-wrong-form")))
    (fact "Fail when organization has version wrong"
      (fact "Missing version is invalid"
        (command kuopio "save-integration-config" :organizationId kuopio-R-org-id
                 :config {:targets          ["P"]
                          :enabled?         true
                          :integration-type "sftp"
                          :options          {:version ""}})
        => (partial expected-failure? :error.invalid-configuration))
      (let [_      (command kuopio "save-integration-config" :organizationId kuopio-R-org-id
                            :config {:targets          ["P"]
                                     :enabled?         true
                                     :integration-type "sftp"
                                     :options          {:version "1337"}})
            app-id (:id (create-and-submit-application
                          pena
                          :propertyId kuopio-property-id
                          :operation "poikkeamis"
                          :propertyId "29703401070010"
                          :y 6965051.2333374 :x 535179.5
                          :address "Suusaarenkierto 44"))]
        (command velho :application-to-asianhallinta :id app-id :lang "fi")
        => (partial
             expected-failure?
             "error.integration.asianhallinta-version-wrong-form")))))
