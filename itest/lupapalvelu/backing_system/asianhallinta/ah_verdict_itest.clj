(ns lupapalvelu.backing-system.asianhallinta.ah-verdict-itest
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [lupapalvelu.application :as app]
            [lupapalvelu.backing-system.asianhallinta.reader :as ah-reader]
            [lupapalvelu.batchrun :as batch]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.integrations-api]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.pate-legacy-itest-util :refer :all]
            [lupapalvelu.user :as usr]
            [lupapalvelu.verdict-api]                       ; for notification definition
            [me.raynes.fs :as fs]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.common-reader :as cr]
            [sade.core :refer [now]]
            [sade.dummy-email-server :as dummy-email]
            [sade.email]
            [sade.env :as env]
            [sade.files :as files]
            [sade.util :as util]))


(def db-name (str "test_xml_asianhallinta_verdict-itest_" (now)))

(def system-user (usr/batchrun-user []))

(mongo/connect!)
(mongo/with-db db-name
  (fixture/apply-fixture "minimal")
  (dummy-email/reset-sent-messages))


(def example-ah-xml-path         "resources/asianhallinta/sample/ah-example-verdict.xml")
(def example-ah-attachment-path  "resources/asianhallinta/sample/ah-example-attachment.pdf")
(def example-ah-attachment2-path "resources/asianhallinta/sample/ah-example-attachment2.txt")

(def parsed-example-ah-xml {:AsianPaatos {:PaatoksenTunnus "12345678"
                                          :PaatoksenTekija "Pena Panaani"
                                          :AsianTunnus "2015-1234"
                                          :Liitteet {:Liite {:Kuvaus "Liitteen kuvaus"
                                                             :LinkkiLiitteeseen "sftp://foo/bar/baz/ah-example-attachment.pdf"
                                                             :Tyyppi "Liitteen tyyppi"}}
                                          :PaatoksenPvm "2015-01-01"
                                          :PaatosKoodi "my\u00F6nnetty"
                                          :Pykala "\u00a7987"
                                          :HakemusTunnus "LP-297-2015-00001"}})

(def parsed-example-ah-xml-no-paatoskoodi
  (util/dissoc-in parsed-example-ah-xml [:AsianPaatos :PaatosKoodi]))

(defn- create-local-ah-app []
  (create-and-submit-local-application
    pena
    :operation "poikkeamis"
    :propertyId "29703401070010"
    :y 6965051.2333374 :x 535179.5
    :address "Suusaarenkierto 44"))

(testable-privates lupapalvelu.backing-system.asianhallinta.verdict build-verdict)
(testable-privates lupapalvelu.backing-system.asianhallinta.reader unzip-file)


(def local-target-folder (str (env/value :outgoing-directory) "/dev_ah_kuopio/asianhallinta/to_lupapiste"))

(defn- setup-target-folder! []
  (fs/mkdirs local-target-folder)
  (let [old-files (concat
                    (util/get-files-by-regex local-target-folder #".*$")
                    (util/get-files-by-regex (str local-target-folder "/archive") #".*$")
                    (util/get-files-by-regex (str local-target-folder "/error") #".*$"))]
    (doseq [f old-files]
     (.delete f))))

(def test-file (.replace "/dev/null" "/" env/file-separator))

(facts "Batchrun"
  (setup-target-folder!)

  (against-background
    [(app/make-application-id anything) => "LP-297-2015-00001"]
    (fact "Successful batchrun"
      (mongo/with-db db-name
        (files/with-zip-file [example-ah-xml-path example-ah-attachment-path example-ah-attachment2-path]
          (let [_ (fs/copy (io/file zip-file) (fs/file (str local-target-folder "/verdict1.zip")))
                app (create-local-ah-app)
                app-id (:id app)]
          (generate-documents app pena true)
          (local-command velho :application-to-asianhallinta :id app-id :lang "fi") => ok?

          (count (:verdicts (query-application local-query pena app-id))) => 0

          (batch/fetch-asianhallinta-messages)

          (fact "Zip has been moved from to_lupapiste to archive folder"
            (count (util/get-files-by-regex local-target-folder #"verdict1\.zip$")) => 0
            (count (util/get-files-by-regex (str local-target-folder "/archive") #"verdict1\.zip$")) => 1)

          (fact "application has verdict"
            (count (:verdicts (query-application local-query pena app-id))) => 1))))))

  (against-background
    [(app/make-application-id anything) => "LP-297-2015-00002"]
    (fact "Batchrun with unsuccessful verdict save (no xml inside zip)"
      (mongo/with-db db-name
        (files/with-zip-file [example-ah-attachment-path example-ah-attachment2-path]
          (let [_ (fs/copy (io/file zip-file) (fs/file (str local-target-folder "/verdict2.zip")))
                app (create-local-ah-app)
                app-id (:id app)]

          (generate-documents app pena true)
          (local-command velho :application-to-asianhallinta :id app-id :lang "fi")

          (count (:verdicts (query-application local-query pena app-id))) => 0

          (batch/fetch-asianhallinta-messages)

          (fact "Zip has been moved from to_lupapiste to error folder"
            (count (util/get-files-by-regex local-target-folder #"verdict2\.zip$")) => 0
            (count (util/get-files-by-regex (str local-target-folder "/error") #"verdict2\.zip$")) => 1)

          (fact "application doesn't have verdict"
            (count (:verdicts (query-application local-query pena app-id))) => 0))))))
  (fact "fetch-asianhallinta-messages logs proess-ah-verdict error result"
    (mongo/with-db db-name
      (lupapalvelu.batchrun/fetch-asianhallinta-messages) => nil
      (provided
        (sade.util/get-files-by-regex anything #".+\.zip$") => [(io/file test-file)]
        (ah-reader/process-message anything anything anything) => (sade.core/fail "nope"))))
  (fact "fetch-asianhallinta-messages logs proess-ah-verdict ok result"
    (mongo/with-db db-name
      (lupapalvelu.batchrun/fetch-asianhallinta-messages) => nil
      (provided
        (sade.util/get-files-by-regex anything #".+\.zip$") => [(io/file test-file)]
        (ah-reader/process-message anything anything anything) => (sade.core/ok)))))

(facts "Processing asianhallinta verdicts"
  (mongo/with-db db-name
    (fixture/apply-fixture "minimal")
    (files/with-zip-file [example-ah-xml-path example-ah-attachment-path example-ah-attachment2-path]
      (let [application (create-local-ah-app)
            AsianPaatos (:AsianPaatos parsed-example-ah-xml)]

        (fact "If application in wrong state, return error"
          (ah-reader/process-message zip-file "dev_ah_kuopio" system-user) => (partial expected-failure? "error.integration.asianhallinta.wrong-state"))

        ; generate docs, set application to 'sent' state by moving it to asianhallinta
        (generate-documents application pena true)
        (local-command velho :application-to-asianhallinta :id (:id application) :lang "fi")

        (Thread/sleep 500)
        (facts "Well-formed, return ok"
          (dummy-email/reset-sent-messages)

          (fact* "Creates verdict based on xml"
            (:verdicts application) => empty?
            (ah-reader/process-message zip-file "dev_ah_kuopio" system-user) => ok?
            (let [application (query-application local-query pena (:id application))
                  verdicts (:verdicts application) =not=> empty?
                  new-verdict (last verdicts)
                  paatos (first (:paatokset new-verdict))
                  poytakirja (first (:poytakirjat paatos))]

              (fact "application state is verdictGiven"

                (keyword (:state application)) => :verdictGiven

                (let [email (last (dummy-email/messages :reset true))]
                  (:to email) => (contains (email-for-key pena))
                  (:subject email) => "Lupapiste: Suusaarenkierto 44, Kuopio - hankkeen tila on nyt P\u00e4\u00e4t\u00f6s annettu"
                  email => (partial contains-application-link-with-tab? (:id application) "verdict" "applicant"))

                (:kuntalupatunnus new-verdict) => (:AsianTunnus AsianPaatos)
                (:paatostunnus paatos) => (:PaatoksenTunnus AsianPaatos)
                (:anto (:paivamaarat paatos)) => (cr/to-timestamp (:PaatoksenPvm AsianPaatos))

                (:paatoksentekija poytakirja) => (:PaatoksenTekija AsianPaatos)
                (:pykala poytakirja) => (:Pykala AsianPaatos)
                (:paatospvm poytakirja) => (cr/to-timestamp (:PaatoksenPvm AsianPaatos))
                (:paatoskoodi poytakirja) => (:PaatosKoodi AsianPaatos))))

          (fact* "Pushes verdict if there exists previous verdicts"
            (fixture/apply-fixture "minimal")                 ; must empty applications because the app-is is hard coded in the xml :(


            (let [app (create-local-ah-app)
                  app-id (:id app)]
              (local-command velho :new-legacy-verdict-draft :id app-id) => ok?

              (let [application (query-application local-query velho app-id)
                    orig-verdicts (:pate-verdicts application) =not=> empty?
                    orig-verdict-count (count orig-verdicts)]
                (generate-documents application pena true)
                (local-command velho :application-to-asianhallinta :id app-id :lang "fi")

                (ah-reader/process-message zip-file "dev_ah_kuopio" system-user)

                (let [application (query-application local-query velho app-id)
                      new-verdict (last (:verdicts application))]
                  (count (:verdicts application)) => 1 ;; from asianhallinta
                  (count (:pate-verdicts application)) => 1  ;; manually created draft
                  (:kuntalupatunnus new-verdict) => (:AsianTunnus AsianPaatos)

                  (fact "application state is verdictGiven" (keyword (:state application)) => :verdictGiven)

                  (let [email (last (dummy-email/messages :reset true))]
                    (:to email) => (contains (email-for-key pena))
                    (:subject email) => "Lupapiste: Suusaarenkierto 44, Kuopio - hankkeen tila on nyt P\u00e4\u00e4t\u00f6s annettu"
                    email => (partial contains-application-link-with-tab? (:id application) "verdict" "applicant"))))))))))
  (against-background
    (app/make-application-id anything) => "LP-297-2015-00001"))

(facts "unit tests"
  (files/with-zip-file [example-ah-xml-path example-ah-attachment-path]
    (fact* "Can unzip passed zipfile"
           (let [tmp-dir    (fs/temp-dir "ah-unzip-test")
                 unzip-path (unzip-file zip-file tmp-dir) =not=> (throws Exception)
                 pattern    (->> [example-ah-xml-path example-ah-attachment-path]
                                 (map fs/base-name)
                                 (s/join "|")
                                 re-pattern)]

             (count (fs/find-files unzip-path pattern)) => 2
             (fs/delete-dir tmp-dir) => truthy)))

  (fact "Creates correct verdict model from xml"
    (build-verdict parsed-example-ah-xml 123456) =>
    {:id "deadbeef" ; mongo/create-id
     :source "ah"
     :kuntalupatunnus "2015-1234"
     :timestamp       123456
     :paatokset       [{:paatostunnus "12345678"
                        :paivamaarat  {:anto 1420070400000} ;2015-01-01
                        :poytakirjat  [{:paatoksentekija "Pena Panaani"
                                        :paatospvm       1420070400000
                                        :pykala          "\u00a7987"
                                        :paatoskoodi     "my\u00F6nnetty"
                                        :id              "deadbeef"}]}]}
    (provided (mongo/create-id) => "deadbeef"))

  (fact "Paatostunnus is used if paatoskoodi not available"
    (build-verdict parsed-example-ah-xml-no-paatoskoodi 123456) =>
    {:id "deadbeef" ; mongo/create-id
     :source "ah"
     :kuntalupatunnus "2015-1234"
     :timestamp       123456
     :paatokset       [{:paatostunnus "12345678"
                        :paivamaarat  {:anto 1420070400000} ;2015-01-01
                        :poytakirjat  [{:paatoksentekija "Pena Panaani"
                                        :paatospvm       1420070400000
                                        :pykala          "\u00a7987"
                                        :paatoskoodi     "12345678"
                                        :id              "deadbeef"}]}]}
    (provided (mongo/create-id) => "deadbeef")))

(facts "Errorenous, return error"
  (fact "If passed zip file is missing"
    (mongo/with-db db-name
      (ah-reader/process-message "/foo/bar" "dev_ah_kuopio" system-user) => (partial expected-failure? "error.integration.asianhallinta-file-not-found")))

  (fact "If xml message missing from zip"
    (mongo/with-db db-name
      (files/with-zip-file [example-ah-attachment-path] ; xml is missing
        (ah-reader/process-message zip-file "dev_ah_kuopio" system-user) => (partial expected-failure? "error.integration.asianhallinta-wrong-number-of-xmls"))))

  (fact "If attachment files missing from zip"
    (mongo/with-db db-name
      (files/with-zip-file [example-ah-xml-path] ;attachment is missing
        (ah-reader/process-message zip-file "dev_ah_kuopio" system-user) => (partial expected-failure? "error.integration.asianhallinta-missing-attachment"))))

  (fact "If xml message references an application that the ftp user cannot access"
    (mongo/with-db db-name
      (files/with-zip-file [example-ah-xml-path example-ah-attachment-path example-ah-attachment2-path]
        (ah-reader/process-message zip-file "sipoo" system-user) => (partial expected-failure? "error.integration.asianhallinta.unauthorized")))))
