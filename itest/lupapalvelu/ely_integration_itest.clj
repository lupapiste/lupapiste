(ns lupapalvelu.ely-integration-itest
  (:require [midje.sweet :refer :all]
            [clojure.data.xml :as xml]
            [clojure.java.io :as io]
            [schema.core :as sc]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.integrations.ely :as ely]
            [lupapalvelu.integrations.messages :as messages]
            [lupapalvelu.user :as usr]
            [lupapalvelu.backing-system.asianhallinta.core :as ah]
            [lupapalvelu.xml.validator :as validator]
            [sade.core :refer [now]]
            [sade.env :as env]
            [sade.xml :as sxml]
            [sade.util :as util]))

(apply-remote-minimal)

(facts "ELY statement process"                              ; LPK-2941
  (let [app (create-and-submit-application mikko :propertyId sipoo-property-id :operation "poikkeamis")
        statement-subtype "Lausuntopyynt\u00f6 poikkeamishakemuksesta"]
    (generate-documents app mikko)                          ; generate documents so generated XML is valid

    (fact "request XML is created"
      (command sonja :ely-statement-request :id (:id app) :subtype statement-subtype :saateText "moro") => ok?
      (let [output-dir (str (:output-dir ah/ely-config) "/")
            target-file-name (str "target/Downloaded-" (:id app) "-" (now) "_ely_statement_request.xml")
            filename-starts-with (:id app)
            xml-file (if get-files-from-sftp-server?
                       (io/file (get-file-from-server
                                  (env/value :ely :sftp-user)
                                  (subs (env/value :fileserver-address) 7)
                                  filename-starts-with
                                  target-file-name
                                  (str ah/ah-from-dir "/")))
                       (io/file (get-local-filename output-dir filename-starts-with)))
            xml-as-string (slurp xml-file)
            xml (xml/parse (io/reader xml-file))]
        (fact "file exists" (.exists xml-file) => true)
        (fact "XML file is valid"
          (validator/validate xml-as-string (:permitType app) "ah-1.3"))
        (fact "Application IDs match"
          (sxml/get-text xml [:UusiAsia :HakemusTunnus]) => (:id app))
        (fact "Tyyppi is correct"
          (sxml/get-text xml [:UusiAsia :Tyyppi]) => "Lausuntopyynt\u00f6")
        (fact "TyypinTarkenne is correct"
          (sxml/get-text xml [:UusiAsia :TyypinTarkenne]) => statement-subtype)
        (fact "messageId is written"
          (sxml/select1-attribute-value xml [:UusiAsia] :messageId) => string?)))

    (fact "ELY statement is saved to application"
      (let [statements (:statements (query-application mikko (:id app)))
            ely-statement (first statements)]
        (count statements) => 1
        (fact "ELY person" (get ely-statement :person) => (ely/ely-statement-giver statement-subtype))
        (fact "state ok" (get ely-statement :state) => "requested")
        (fact "external config ok"
          (get ely-statement :external) => (just {:partner "ely"
                                                  :messageId string?
                                                  :subtype statement-subtype}))

        (fact "valid integration-message saved to db"
          (let [resp (:body (get-by-id :integration-messages (get-in ely-statement [:external :messageId])))]
            resp => ok?
            (sc/check messages/IntegrationMessage (:data resp)) => nil))))

    (fact "Statement allowed actions"
      (let [ely-statement (-> (query-application mikko (:id app))
                              :statements
                              first)
            ely-id (:id ely-statement)]
        (fact "delete-statement not possible"
          sonja =not=> (allowed? :delete-statement :id (:id app) :statementId ely-id))))

    (fact "message is acknowledged by partner"
      (let [ely-statement (-> (query-application mikko (:id app)) (:statements) (first))
            external-data (:external ely-statement)]
        (fact "Random FTP user can't update statement"
          (-> (decoded-get (str (server-address) "/dev/ah/message-response")
                                   {:query-params {:id (:id app)
                                                   :ftp-user "foo"
                                                   :messageId (:messageId external-data)}})
              :body) => (partial expected-failure? :error.unauthorized))

        ; Mock XML unzipping and messageId parsing from AsianTunnusVastaus
        (-> (decoded-get (str (server-address) "/dev/ah/message-response")
                                 {:query-params {:id (:id app)
                                                 :ftp-user (env/value :ely :sftp-user)
                                                 :messageId (:messageId external-data)}})
            :body) => ok?

        (let [ely-statement (-> (query-application mikko (:id app)) (:statements) (first))]
          (fact "Statement is acknowledged by ELY"
            (get-in ely-statement [:external :acknowledged]) => pos?
            (get-in ely-statement [:external :externalId]) => string?))))

    (fact "ELY sends statement response"
      (let [ely-statement (-> (query-application mikko (:id app)) (:statements) (first))]
        (fact "Random FTP user can't update statement"
          (-> (decoded-get (str (server-address) "/dev/ah/statement-response")
                           {:query-params {:id (:id app)
                                           :ftp-user "foo"
                                           :statement-id (:id ely-statement)}})
              :body) => (partial expected-failure? :error.unauthorized))
        (fact "Statement response is processed correctly"
          (-> (decoded-get (str (server-address) "/dev/ah/statement-response")
                           {:query-params {:id (:id app)
                                           :ftp-user (env/value :ely :sftp-user)
                                           :statement-id (:id ely-statement)}})
              :body) => ok?)

        (let [ely-statement-new (-> (query-application mikko (:id app)) (:statements) (first))]
          (fact "State is given"
            (:state ely-statement-new) => "given")
          (fact "Values from XML are saved to statement"
            (get-in ely-statement-new [:person :name]) => (contains "Eija Esimerkki")
            (:status ely-statement-new) => "puollettu"
            (:text ely-statement-new) => "Hyv\u00e4 homma"
            (util/to-xml-date (:given ely-statement-new)) => (util/to-xml-date (.getTime #inst"2017-05-07"))) ; Depends on system TZ
          (fact "Old data is OK"
            (:saateText ely-statement-new) => "moro"
            (get-in ely-statement-new [:external :acknowledged]) => pos?)
          (fact "externalId is changed, as AsianTunnus is different in acknowledgement XML vs statement-response"
            (get-in ely-statement-new [:external :externalId]) => string?
            (get-in ely-statement [:external :externalId]) => string?
            (get-in ely-statement-new [:external :externalId]) =not=> (get-in ely-statement [:external :externalId]))))
      (fact "Atttachments are uploaded"
        (let [app (query-application mikko (:id app))
              ely-statement (first (:statements app))
              statement-attachments (filter
                                      #(= (:id ely-statement) (-> % :target :id))
                                      (:attachments app))
              ely-attachment (first statement-attachments)]
          (get-in ely-statement [:external :partner]) => "ely"
          (count statement-attachments) => 1
          (get-in ely-attachment [:target :id]) => (:id ely-statement)
          (get-in ely-attachment [:target :type]) => "statement"
          (get-in ely-attachment [:type :type-id]) => "lausunto"
          (get ely-attachment :contents) => (contains "Lausunnon liite")
          (get-in ely-attachment [:latestVersion :user :username]) => (:username usr/batchrun-user-data)
          (fact "as in give-statement, attachment should be set readonly"
            (:readOnly ely-attachment) => true)
          (fact "locked true comes from asiahallinta reader" ; hmm?
            (:locked ely-attachment) => true)
          (fact "'child' attachment PDF is generated"
            (let [child (->> (:attachments app)
                             (filter #(= (:id ely-statement) (-> % :source :id)))
                             first)]
              (get-in child [:source :type]) => "statements"
              (get-in child [:contents]) => statement-subtype
              (get-in child [:latestVersion :contentType]) => "application/pdf"))))
      (fact "Response saved to integration-messages"
        (let [msg (last (integration-messages (:id app)))
              statement (-> (query-application mikko (:id app)) (:statements) (first))]
          msg => truthy
          (:status msg) => "processed"
          (get-in msg [:target :id]) => (:id statement))))))
