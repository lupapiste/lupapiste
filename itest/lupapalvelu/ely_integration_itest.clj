(ns lupapalvelu.ely-integration-itest
  (:require [midje.sweet :refer :all]
            [clojure.data.xml :as xml]
            [clojure.java.io :as io]
            [schema.core :as sc]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.integrations.ely :as ely]
            [lupapalvelu.integrations.messages :as messages]
            [lupapalvelu.xml.asianhallinta.core :as ah]
            [lupapalvelu.xml.validator :as validator]
            [sade.core :refer [now]]
            [sade.env :as env]
            [sade.xml :as sxml]))

(apply-remote-minimal)

(facts "ELY statement-request"
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
          sonja =not=> (allowed? :delete-statement :id (:id app) :statementId ely-id))))))
