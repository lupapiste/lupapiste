(ns lupapalvelu.integrations.document-export-itest
  "Integration tests for the document export aspect of the
  `move-attachments-to-backing-system` command (lupapalvelu.integrations-api)."
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [lupapalvelu.itest-util :refer [local-fixture-factory
                                            create-and-submit-local-application
                                            generate-documents!
                                            upload-file-and-bind
                                            command query
                                            sonja]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.tiedonohjaus :as tos]
            [monger.operators :refer [$set]]
            [sade.env :as env]
            [sade.strings :as ss])
  (:import [jakarta.xml.bind JAXB]
           [java.io StringReader]
           [lupapalvelu.tiedonohjaus ClassificationScheme]))

(set! *warn-on-reflection* true)

(use-fixtures :once (local-fixture-factory :db-name "test_document-export-itest"))

(defn- setup-org-for-sftp-export!
  "Sets :replace-archive-with-sftp and :permanent-archive-enabled on 753-R organization."
  []
  (mongo/update-by-id :organizations "753-R"
                       {$set {:replace-archive-with-sftp  true
                              :permanent-archive-enabled  true}}))

(def ^:private mock-classification-xml
  "Minimal ClassificationScheme XML that satisfies the SAHKE2 schema."
  (str "<ClassificationScheme xmlns=\"http://www.lupapiste.fi/onkalo/schemas/sahke2-case-file/2016/6/1\">"
       "<MainFunction>"
       "<Title>Test</Title>"
       "<FunctionCode>10</FunctionCode>"
       "<FunctionClassification>"
       "<Title>Test</Title>"
       "<FunctionCode>10 03</FunctionCode>"
       "<SubFunction>"
       "<Title>Test</Title>"
       "<FunctionCode>10 03 00 01</FunctionCode>"
       "</SubFunction>"
       "</FunctionClassification>"
       "</MainFunction>"
       "</ClassificationScheme>"))

(deftest mock-classification-xml-is-valid
  (testing "mock XML can be unmarshalled as a valid SAHKE2 ClassificationScheme"
    (let [result (with-open [reader (StringReader. mock-classification-xml)]
                   (JAXB/unmarshal reader ClassificationScheme))]
      (is (instance? ClassificationScheme result)))))

(defn- mock-toj-api
  "Mock implementation of get-from-toj-api that returns test data without HTTP calls."
  [_organization-id _coerce? & path-parts]
  (let [path (apply str path-parts)]
    (cond
      (ss/includes? path "/classification") mock-classification-xml
      (ss/includes? path "/toimenpide-for-state/") {:name "Toimenpide"}
      :else nil)))

(def ^:private test-process-metadata
  {:julkisuusluokka :julkinen
   :henkilotiedot   :ei-sisalla
   :sailytysaika    {:arkistointi :ei
                     :perustelu   "Ei arkistoida"}
   :tila            "valmis"})

(defn- create-sent-application!
  "Creates an application, generates documents, uploads an attachment,
  sets TOS metadata, and approves it (state → :sent). Returns the app-id."
  []
  (let [{app-id :id :as app} (create-and-submit-local-application
                                sonja
                                :address "Document Export Test"
                                :operation "aita")]
    (generate-documents! app sonja true)
    (upload-file-and-bind sonja app-id
                          {:type     {:type-group "rakennuspaikka"
                                      :type-id    "karttaote"}
                           :filename "dev-resources/test-pdf.pdf"
                           :contents "Map"
                           :group    {}})
    ;; Set TOS function and process metadata required by xml-case-file
    (mongo/update-by-id :applications app-id
                        {$set {:tosFunction     "10 03 00 01"
                               :metadata        {:julkisuusluokka :julkinen
                                                  :henkilotiedot   :ei-sisalla
                                                  :sailytysaika    {:arkistointi :ei
                                                                    :perustelu   "Ei arkistoida"}}
                               :processMetadata test-process-metadata}})
    (command sonja :update-app-bulletin-op-description
             :id app-id :description "Test description")
    (command sonja :approve-application :id app-id :lang "fi")
    app-id))

(defn- get-application [app-id]
  (:application (query sonja :application :id app-id)))

(defn- sftp-file-names
  "Returns file names in the legacy SFTP output directory for dev_sipoo."
  []
  (let [outdir  (env/value :outgoing-directory)
        sftp-dir (str outdir "/dev_sipoo/rakennus/")]
    (when (fs/exists? sftp-dir)
      (mapv fs/file-name (fs/list-dir sftp-dir)))))

(deftest document-only-export
  (setup-org-for-sftp-export!)
  (let [app-id  (create-sent-application!)
        doc-ids [(str app-id "-application")
                 (str app-id "-case-file")]
        result  (command sonja :move-attachments-to-backing-system
                         :id app-id :lang "fi"
                         :documentIds doc-ids)]
    (testing "command succeeds"
      (is (true? (:ok result))))

    (testing "documents-sent contains all document IDs"
      (is (= (set doc-ids) (set (:documents-sent result)))))

    (testing "application metadata updated"
      (let [app (get-application app-id)]
        (is (pos? (get-in app [:metadata :sent])))
        (is (= 1 (get-in app [:metadata :export-version])))))

    (testing "processMetadata updated"
      (let [app (get-application app-id)]
        (is (pos? (get-in app [:processMetadata :sent])))
        (is (= 1 (get-in app [:processMetadata :export-version])))))

    (testing "transfer record created with documents"
      (let [app       (get-application app-id)
            transfers (:transfers app)
            transfer  (last transfers)]
        (is (= :exported-to-backing-system (keyword (:type transfer))))
        (is (seq (:documents transfer)))
        (is (= (set (map :id (:documents transfer)))
               (set doc-ids)))
        (is (every? #(= 1 (:version %)) (:documents transfer)))))

    (testing "files exist in SFTP output directory"
      (let [files (sftp-file-names)]
        (is (some #(ss/ends-with? % "-application.pdf") files))
        (is (some #(ss/ends-with? % "-case-file.pdf") files))
        (is (some #(ss/ends-with? % ".xml") files)
            "KuntaGML message XML should be present")))))

(deftest case-file-xml-export
  (setup-org-for-sftp-export!)
  (with-redefs [tos/get-from-toj-api mock-toj-api]
    (let [app-id  (create-sent-application!)
          doc-ids [(str app-id "-application")
                   (str app-id "-case-file-xml")]
          result  (command sonja :move-attachments-to-backing-system
                           :id app-id :lang "fi"
                           :documentIds doc-ids)]
      (testing "command succeeds"
        (is (true? (:ok result))))

      (testing "all documents sent"
        (is (= (set doc-ids) (set (:documents-sent result)))))

      (testing "XML file exists in SFTP output directory"
        (let [files (sftp-file-names)]
          (is (some #(ss/ends-with? % "-case-file.xml") files)))))))

(deftest re-export-increments-version
  (setup-org-for-sftp-export!)
  (let [app-id  (create-sent-application!)
        doc-ids [(str app-id "-application")]
        _       (command sonja :move-attachments-to-backing-system
                         :id app-id :lang "fi"
                         :documentIds doc-ids)
        result2 (command sonja :move-attachments-to-backing-system
                         :id app-id :lang "fi"
                         :documentIds doc-ids)]
    (testing "second export succeeds"
      (is (true? (:ok result2))))

    (testing "export-version incremented to 2"
      (let [app (get-application app-id)]
        (is (= 2 (get-in app [:metadata :export-version])))))

    (testing "transfer record has version 2"
      (let [app      (get-application app-id)
            transfer (last (:transfers app))]
        (is (= 2 (:version (first (:documents transfer)))))))))

(deftest mixed-export-attachments-and-documents
  (setup-org-for-sftp-export!)
  (let [app-id  (create-sent-application!)
        app     (get-application app-id)
        att-id  (:id (first (:attachments app)))
        doc-ids [(str app-id "-application")]
        result  (command sonja :move-attachments-to-backing-system
                         :id app-id :lang "fi"
                         :attachmentIds [att-id]
                         :documentIds doc-ids)]
    (testing "command succeeds"
      (is (true? (:ok result))))

    (testing "documents-sent present"
      (is (= doc-ids (:documents-sent result))))

    (testing "attachment marked as sent"
      (let [updated-app (get-application app-id)
            attachment  (first (filter #(= att-id (:id %)) (:attachments updated-app)))]
        (is (pos? (:sent attachment)))))

    (testing "transfer record has both attachments and documents"
      (let [updated-app (get-application app-id)
            transfer    (last (:transfers updated-app))]
        (is (seq (:documents transfer)))
        (is (seq (:attachments transfer)))))))

(deftest backward-compatibility-attachments-only
  (setup-org-for-sftp-export!)
  (let [app-id (create-sent-application!)
        app    (get-application app-id)
        att-id (:id (first (:attachments app)))
        result (command sonja :move-attachments-to-backing-system
                        :id app-id :lang "fi"
                        :attachmentIds [att-id])]
    (testing "command succeeds"
      (is (true? (:ok result))))

    (testing "no document-related keys in response"
      (is (nil? (:documents-sent result))))

    (testing "metadata not updated (no documents exported)"
      (let [updated-app (get-application app-id)]
        (is (nil? (get-in updated-app [:metadata :export-version])))))))

(deftest invalid-document-id-skipped
  (setup-org-for-sftp-export!)
  (let [app-id    (create-sent-application!)
        valid-id  (str app-id "-application")
        invalid-id "bogus-document-id"
        result    (command sonja :move-attachments-to-backing-system
                           :id app-id :lang "fi"
                           :documentIds [valid-id invalid-id])]
    (testing "command succeeds"
      (is (true? (:ok result))))

    (testing "valid document is sent"
      (is (some #{valid-id} (:documents-sent result))))

    (testing "invalid document is silently skipped — not in sent"
      (is (not (some #{invalid-id} (:documents-sent result)))))))

(deftest documents-only-no-attachments
  (setup-org-for-sftp-export!)
  (let [app-id  (create-sent-application!)
        _       (mongo/update-by-id :applications app-id {$set {:attachments []}})
        doc-ids [(str app-id "-application")]
        result  (command sonja :move-attachments-to-backing-system
                         :id app-id :lang "fi"
                         :documentIds doc-ids)]
    (testing "command succeeds with only documents, no attachments"
      (is (true? (:ok result))))
    (testing "document sent"
      (is (= doc-ids (:documents-sent result))))))
