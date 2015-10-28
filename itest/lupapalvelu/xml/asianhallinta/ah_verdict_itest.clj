(ns lupapalvelu.xml.asianhallinta.ah-verdict-itest
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.batchrun :as batch]
            [lupapalvelu.xml.asianhallinta.verdict :as ahk]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.integrations-api]
            [me.raynes.fs.compression :as fsc]
            [me.raynes.fs :as fs]
            [lupapalvelu.mongo :as mongo]
            [sade.core :refer [now] :as core]
            [sade.common-reader :as cr]
            [sade.env :as env]
            [sade.util :as util]
            [sade.dummy-email-server :as dummy-email])
  (:import (java.io File ByteArrayOutputStream)))


(def db-name (str "test_xml_asianhallinta_verdict-itest_" (now)))

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

(defn- slurp-bytes [fpath]
  (with-open [data (io/input-stream (fs/file fpath))]
    (with-open [out (ByteArrayOutputStream.)]
      (io/copy data out)
      (.toByteArray out))))

(defn- zip-files! [file fpaths]
  (let [filename-content-pairs (map (juxt fs/base-name slurp-bytes) fpaths)]
    (io/copy (fsc/make-zip-stream filename-content-pairs)
             (fs/file file))
    file))

(defn- build-zip! [fpaths]
  (let [temp-file (File/createTempFile "test" ".zip")]
    (zip-files! temp-file fpaths)
    temp-file))

(defn- create-local-ah-app []
  (create-and-submit-local-application
    pena
    :operation "poikkeamis"
    :propertyId "29703401070010"
    :y 6965051.2333374 :x 535179.5
    :address "Suusaarenkierto 44"))

(defn- add-new-verdict [application-id]
  (let [new-verdict-resp (local-command velho :new-verdict-draft :id application-id)
        verdict-id (:verdictId new-verdict-resp)]
    (local-command velho :save-verdict-draft :id application-id :verdictId verdict-id :backendId "aaa" :status 42 :name "Paatoksen antaja" :given 123 :official 124 :text "" :agreement false :section "")))

(testable-privates lupapalvelu.xml.asianhallinta.verdict unzip-file build-verdict)


(def local-target-folder (str (env/value :outgoing-directory) "/dev_ah_kuopio/asianhallinta/to_lupapiste"))

(defn- setup-target-folder! []
  (fs/mkdirs local-target-folder)
  (let [old-files (concat
                    (util/get-files-by-regex local-target-folder #".*$")
                    (util/get-files-by-regex (str local-target-folder "/archive") #".*$")
                    (util/get-files-by-regex (str local-target-folder "/error") #".*$"))]
    (doseq [f old-files]
     (.delete f))))

(facts "Batchrun"
  (mongo/with-db db-name
    (fixture/apply-fixture "minimal"))
  (setup-target-folder!)

  (fact "Successful batchrun"
    (mongo/with-db db-name
      (let [zip (fs/copy
                 (build-zip! [example-ah-xml-path example-ah-attachment-path example-ah-attachment2-path])
                 (fs/file (str local-target-folder "/verdict1.zip")))
            app (create-local-ah-app)
            app-id (:id app)]
        (generate-documents app pena true)
        (local-command velho :application-to-asianhallinta :id app-id :lang "fi") => ok?

        (count (:verdicts (query-application local-query pena app-id))) => 0

        (batch/fetch-asianhallinta-verdicts)

        (fact "Zip has been moved from to_lupapiste to archive folder"
          (count (util/get-files-by-regex local-target-folder #"verdict1\.zip$")) => 0
          (count (util/get-files-by-regex (str local-target-folder "/archive") #"verdict1\.zip$")) => 1)

        (fact "application has verdict"
          (count (:verdicts (query-application local-query pena app-id))) => 1))))

  (fact "Batchrun with unsuccessful verdict save (no xml inside zip)"
    (mongo/with-db db-name
      (let [zip (fs/copy
                 (build-zip! [example-ah-attachment-path example-ah-attachment2-path])
                 (fs/file (str local-target-folder "/verdict2.zip")))
            app (create-local-ah-app)
            app-id (:id app)]

        (generate-documents app pena true)
        (local-command velho :application-to-asianhallinta :id app-id :lang "fi")

        (count (:verdicts (query-application local-query pena app-id))) => 0

        (batch/fetch-asianhallinta-verdicts)

        (fact "Zip has been moved from to_lupapiste to error folder"
          (count (util/get-files-by-regex local-target-folder #"verdict2\.zip$")) => 0
          (count (util/get-files-by-regex (str local-target-folder "/error") #"verdict2\.zip$")) => 1)

        (fact "application doesn't have verdict"
          (count (:verdicts (query-application local-query pena app-id))) => 0)))))

(facts "Processing asianhallinta verdicts"
  (mongo/with-db db-name
    (fixture/apply-fixture "minimal")
    (let [zip-file    (.getPath (build-zip! [example-ah-xml-path example-ah-attachment-path example-ah-attachment2-path]))
          application (create-local-ah-app)
          AsianPaatos (:AsianPaatos parsed-example-ah-xml)]

      (fact "If application in wrong state, return error"
        (ahk/process-ah-verdict zip-file "dev_ah_kuopio") => (partial expected-failure? "error.integration.asianhallinta.wrong-state"))

                                        ; generate docs, set application to 'sent' state by moving it to asianhallinta
      (generate-documents application pena true)
      (local-command velho :application-to-asianhallinta :id (:id application) :lang "fi")


      (facts "Well-formed, return ok"
        (dummy-email/reset-sent-messages)

        (fact* "Creates verdict based on xml"
          (:verdicts application) => empty?
          (ahk/process-ah-verdict zip-file "dev_ah_kuopio") => ok?
          (let [application (query-application local-query pena (:id application))
                verdicts (:verdicts application) =not=> empty?
                new-verdict (last verdicts)
                paatos (first (:paatokset new-verdict))
                poytakirja (first (:poytakirjat paatos))]

            (fact "application state is verdictGiven" (keyword (:state application)) => :verdictGiven)
            (let [email (last (dummy-email/messages :reset true))]
              (:to email) => (contains (email-for-key pena))
              (:subject email) => "Lupapiste.fi: Suusaarenkierto 44 - p\u00e4\u00e4t\u00f6s"
              email => (partial contains-application-link-with-tab? (:id application) "verdict" "applicant"))

            (:kuntalupatunnus new-verdict) => (:AsianTunnus AsianPaatos)
            (:paatostunnus paatos) => (:PaatoksenTunnus AsianPaatos)
            (:anto (:paivamaarat paatos)) => (cr/to-timestamp (:PaatoksenPvm AsianPaatos))

            (:paatoksentekija poytakirja) => (:PaatoksenTekija AsianPaatos)
            (:pykala poytakirja) => (:Pykala AsianPaatos)
            (:paatospvm poytakirja) => (cr/to-timestamp (:PaatoksenPvm AsianPaatos))
            (:paatoskoodi poytakirja) => (:PaatosKoodi AsianPaatos)))

        (fact* "Pushes verdict if there exists previous verdicts"
          (fixture/apply-fixture "minimal")                 ; must empty applications because the app-is is hard coded in the xml :(


          (let [app (create-local-ah-app)
                app-id (:id app)]
            (add-new-verdict app-id)

            (let [application (query-application local-query velho app-id)
                  orig-verdicts (:verdicts application) =not=> empty?
                  orig-verdict-count (count orig-verdicts)]
              (generate-documents application pena true)
              (local-command velho :application-to-asianhallinta :id app-id :lang "fi")

              (ahk/process-ah-verdict zip-file "dev_ah_kuopio")

              (let [application (query-application local-query velho app-id)
                    new-verdict (last (:verdicts application))]
                (count (:verdicts application)) => (+ orig-verdict-count 1)
                (:kuntalupatunnus new-verdict) => (:AsianTunnus AsianPaatos)

                (fact "application state is verdictGiven" (keyword (:state application)) => :verdictGiven)

                (let [email (last (dummy-email/messages :reset true))]
                  (:to email) => (contains (email-for-key pena))
                  (:subject email) => "Lupapiste.fi: Suusaarenkierto 44 - p\u00e4\u00e4t\u00f6s"
                  email => (partial contains-application-link-with-tab? (:id application) "verdict" "applicant"))))))))))

(facts "unit tests"
  (let [zip-file (.getPath (build-zip! [example-ah-xml-path example-ah-attachment-path]))]
    (fact* "Can unzip passed zipfile"
           (let [unzip-path (unzip-file zip-file) =not=> (throws Exception)
                 pattern    (->> [example-ah-xml-path example-ah-attachment-path]
                                 (map fs/base-name)
                                 (s/join "|")
                                 re-pattern)]

             (count (fs/find-files unzip-path pattern)) => 2)))

  (fact "Creates correct verdict model from xml"
    (build-verdict parsed-example-ah-xml) => {:id "deadbeef" ; mongo/create-id
                                              :source "ah"
                                              :kuntalupatunnus "2015-1234"
                                              :timestamp       "123456" ; core/now
                                              :paatokset       [{:paatostunnus "12345678"
                                                                 :paivamaarat  {:anto 1420070400000} ;2015-01-01
                                                                 :poytakirjat  [{:paatoksentekija "Pena Panaani"
                                                                                 :paatospvm       1420070400000
                                                                                 :pykala          "\u00a7987"
                                                                                 :paatoskoodi     "my\u00F6nnetty"
                                                                                 :id              "deadbeef"}]}]}
                                              (provided (mongo/create-id) => "deadbeef")
                                              (provided (core/now) => "123456"))

  (fact "Paatostunnus is used if paatoskoodi not available"
    (build-verdict parsed-example-ah-xml-no-paatoskoodi) => {:id "deadbeef" ; mongo/create-id
                                                             :source "ah"
                                                             :kuntalupatunnus "2015-1234"
                                                             :timestamp       "123456" ; core/now
                                                             :paatokset       [{:paatostunnus "12345678"
                                                                                :paivamaarat  {:anto 1420070400000} ;2015-01-01
                                                                                :poytakirjat  [{:paatoksentekija "Pena Panaani"
                                                                                                :paatospvm       1420070400000
                                                                                                :pykala          "\u00a7987"
                                                                                                :paatoskoodi     "12345678"
                                                                                                :id              "deadbeef"}]}]}
                                                             (provided (mongo/create-id) => "deadbeef")
                                                             (provided (core/now) => "123456")))

(facts "Errorenous, return error"
  (fact "If passed zip file is missing"
    (mongo/with-db db-name
      (ahk/process-ah-verdict "/foo/bar" "dev_ah_kuopio") => (partial expected-failure? "error.integration.asianhallinta-file-not-found")))

  (fact "If xml message missing from zip"
    (mongo/with-db db-name
      (let [zip-file (.getPath (build-zip! [example-ah-attachment-path]))] ; xml is missing
        (ahk/process-ah-verdict zip-file "dev_ah_kuopio") => (partial expected-failure? "error.integration.asianhallinta-wrong-number-of-xmls"))))

  (fact "If attachment files missing from zip"
    (mongo/with-db db-name
      (let [zip-file (.getPath (build-zip! [example-ah-xml-path]))] ;attachment is missing
        (ahk/process-ah-verdict zip-file "dev_ah_kuopio") => (partial expected-failure? "error.integration.asianhallinta-missing-attachment"))))

  (fact "If xml message references an application that the ftp user cannot access"
    (mongo/with-db db-name
      (let [zip-file (.getPath (build-zip! [example-ah-xml-path example-ah-attachment-path example-ah-attachment2-path]))]
        (ahk/process-ah-verdict zip-file "sipoo") => (partial expected-failure? "error.integration.asianhallinta.unauthorized")))))

