(ns lupapalvelu.backing-system.asianhallinta.asianhallinta-itest
  (:require [clojure.data.xml :as xml]
            [clojure.java.io :as io]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.organization :as organization]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [lupapalvelu.sftp.context :as sftp-ctx]
            [lupapalvelu.xml.validator :as validator]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.core :refer [now]]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as sxml]
            [taoensso.timbre :as timbre])
  (:import [java.net URI]))

(def sipoo-R-org-id "753-R")
(def kuopio-R-org-id "297-R")
(def ah-from-dir (str (ss/join-file-path sftp-ctx/CASE-MANAGEMENT sftp-ctx/CASE-MANAGEMENT-OUT) "/"))

(apply-remote-minimal)

(testable-privates lupapalvelu.backing-system.asianhallinta.core resolve-ah-version)
(testable-privates lupapalvelu.backing-system.asianhallinta.asianhallinta-mapping attachments-for-write)

(defn from-dir
  "This works since we now that the "
  [organization application]
  (-> (sftp-ctx/files-context organization application)
      :directory
      (str "/")))

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
          scope        (organization/resolve-organization-scope velho-muni
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

        (:to email) => (contains (email-for-key pena))
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

                (count xml-attachments) => (+ (count (:attachments updated-application)) 2) ; 2 x Genereated PDFs

                ;; Following fact seems to fail sometimes, so add some logging
                (timbre/debug "WRITTEN ATTS" (pr-str written-atts))
                (timbre/debug "XML ATTACHMENTS" (pr-str xml-attachments))

                (fact "Filename in XML and filename written to disk are the same"
                  filename => (-> written-atts first :filename))

                (fact "File have content" (not (nil? attachment-string)))))

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
          _            (give-legacy-verdict velho link-app-id :kuntalupatunnus "KLTunnus1")
          manual-link  "Another kuntalupatunnus"

          application  (create-and-submit-application ; Actual app for asianhallinta
                         pena
                         :propertyId kuopio-property-id
                         :operation "poikkeamis"
                         :propertyId "29703401070010"
                         :y 6965051.2333374 :x 535179.5
                         :address "Suusaarenkierto 44")
          app-id       (:id application)
          organization (organization/resolve-organization velho-muni (:permitType application))
          scope        (organization/resolve-organization-scope velho-muni (:permitType application) organization)]

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
          organization (organization/resolve-organization velho-muni (:permitType application))
          scope        (organization/resolve-organization-scope velho-muni (:permitType application) organization)]
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
            (let [xml-attachments    (sxml/select xml [:TaydennysAsiaan :Liitteet :Liite])
                  writed-attachments (attachments-for-write (:attachments updated-application))
                  last-link          (sxml/get-text (last xml-attachments) [:LinkkiLiitteeseen])
                  filename           (last (ss/split last-link #"/"))
                  linkki-as-uri      (URI. last-link)
                  attachment-file    (if get-files-from-sftp-server?
                                       (get-file-from-server
                                         (get-in scope [:caseManagement :ftpUser])
                                         (.getHost linkki-as-uri)
                                         (.getPath linkki-as-uri)
                                         (str "target/Downloaded-" filename))
                                       (str output-dir filename))
                  attachment-string  (slurp attachment-file)]
              (count xml-attachments) => (count (:attachments updated-application))
              (fact "Filename in XML and filename written to disk are the same"
                filename => (-> writed-attachments first :filename))
              (fact "File have content" (not (nil? attachment-string)))))))))

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
      (query pena "asianhallinta-config" :organizationId sipoo-R-org-id) => unauthorized?
      (command pena "save-asianhallinta-config" :organizationId sipoo-R-org-id
               :permitType "P" :municipality "753" :propertyId sipoo-property-id :enabled true :version "1.3")
      => unauthorized?)
    (fact "Sonja can't use asianhallinta configure query & command"
      (query sonja "asianhallinta-config" :organizationId sipoo-R-org-id) => unauthorized?
      (command sonja "save-asianhallinta-config" :organizationId sipoo-R-org-id
               :permitType "P" :municipality "753" :propertyId sipoo-property-id :enabled true :version "1.3")
      => unauthorized?)

    (fact "Sipoo auth admin can query asianhallinta-config, response has scope, caseManagement with skeleton values"
      (let [resp  (query sipoo "asianhallinta-config" :organizationId sipoo-R-org-id)
            scope (:scope resp)]
        resp => ok?
        scope => truthy
        (get scope :caseManagement) => nil? ))

    (facts "Kuopio auth admin"
      (fact "query asianhallinta-config, response has scope with one caseManagement having FTP user"
        (let [resp      (query kuopio "asianhallinta-config" :organizationId kuopio-R-org-id)
              ah-config (some #(when (-> % :caseManagement :ftpUser) (:caseManagement %)) (:scope resp))]
          resp => ok?
          ah-config => {:enabled true :ftpUser "dev_ah_kuopio" :version "1.1"}))
      (fact "admin can disable asianhallinta using command"
        (command kuopio "save-asianhallinta-config" :organizationId kuopio-R-org-id
                 :permitType "P" :municipality velho-muni :enabled false :version "1.1")
        (let [resp      (query kuopio "asianhallinta-config" :organizationId kuopio-R-org-id)
              ah-config (some #(when (and
                                       (= (:municipality %) velho-muni)
                                       (= (:permitType %) "P"))
                                 (:caseManagement %))
                              (:scope resp))]
          (:enabled ah-config) => false))
      (fact "admin can enable asianhallinta and change version using command"
        (command kuopio "save-asianhallinta-config" :organizationId kuopio-R-org-id
                 :permitType "P" :municipality velho-muni :enabled true :version "lol")
        (let [resp      (query kuopio "asianhallinta-config" :organizationId kuopio-R-org-id)
              ah-config (some #(when (and
                                       (= (:municipality %) velho-muni)
                                       (= (:permitType %) "P"))
                                 (:caseManagement %))
                              (:scope resp))]
            (:enabled ah-config) => true
            (:version ah-config) => "lol")))
    (fact "R can't be set"
      (command kuopio "save-asianhallinta-config" :organizationId kuopio-R-org-id
               :permitType "R" :municipality velho-muni :enabled true :version "lol")
      => (partial expected-failure? :error.invalid-permit-type)))

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

  (fact "Fail when organization has version missing"
    (let [_      (command kuopio "save-asianhallinta-config" :organizationId kuopio-R-org-id
                          :permitType "P" :municipality velho-muni :enabled true :version "")
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
           "error.integration.asianhallinta-version-missing"))))
