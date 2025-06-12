(ns lupapalvelu.backing-system.asianhallinta.ah-verdict-itest
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [lupapalvelu.application :as app]
            [lupapalvelu.backing-system.asianhallinta.reader :as ah-reader]
            [lupapalvelu.factlet :refer [fact*]]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.integrations-api]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.logging :as log]
            [lupapalvelu.minimal-util :as min-util]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.sftp.context :as sftp-ctx]
            [lupapalvelu.sftp.core :as sftp]
            [lupapalvelu.user :as usr]
            [lupapalvelu.verdict-api]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.date :as date]
            [sade.dummy-email-server :as dummy-email]
            [sade.email]
            [sade.env :as env]
            [sade.files :as files]
            [sade.strings :as ss]
            [sade.util :as util]))

(set! *warn-on-reflection* true)

(def system-user (usr/batchrun-user []))

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
                                          :Pykala "987"
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


(def local-target-folder (ss/join-file-path (env/value :outgoing-directory)
                                            "dev_ah_kuopio/asianhallinta/to_lupapiste"))

(defn make-dirs []
  (let [basedir (ss/join-file-path (env/value :outgoing-directory)
                                   "dev_ah_kuopio/asianhallinta")]
    (doseq [dir    ["from_lupapiste" "to_lupapiste"]
            subdir ["archive" "error"]]
      (sftp-ctx/create-non-existing-dirs (ss/join-file-path basedir dir subdir)))))

(defn- setup-target-folder! []
  (let [archive   (ss/join-file-path local-target-folder "archive")
        error     (ss/join-file-path local-target-folder "error")
        old-files (concat
                    (util/get-files-by-regex local-target-folder #".*$")
                    (util/get-files-by-regex archive #".*$")
                    (util/get-files-by-regex error #".*$"))]
    (doseq [f old-files]
      (fs/delete-tree f))
    (make-dirs)))

(defn- log-contents
  [filepath]
  (->> filepath
       fs/list-dir
       (mapv (fn [f]
               {:name   (str f)
                :owner  (str (fs/owner f))
                :read?  (fs/readable? f)
                :write? (fs/writable? f)
                :exec?  (fs/executable? f)}))
       (run! (fn [{:keys [name owner read? write? exec?]}]
               (log/infof "%s%s%s - %s - %s"
                          (if read? "R" "_")
                          (if write? "W" "_")
                          (if exec? "X" "_")
                          owner
                          name)))))

(defn- verbose-copy
  [from to]
  (try
    (log/infof "Copying %s to %s" from to)
    (fs/copy from to)
    (catch Exception e
      (log-contents (fs/parent to))
      (throw e))))

(with-test-system {:fixture            "minimal"
                   :schema-validation? false}
  (dummy-email/reset-sent-messages)
  (facts "Batchrun"
    (setup-target-folder!)

    (against-background
        [(app/make-application-id anything) => "LP-297-2015-00001"]
      (fact "Successful batchrun"
        (files/with-zip-file [example-ah-xml-path example-ah-attachment-path example-ah-attachment2-path]
          (let [_      (verbose-copy (io/file zip-file) (fs/file (str local-target-folder "/verdict1.zip")))
                app    (create-local-ah-app)
                app-id (:id app)]
            (generate-documents! app pena true)
            (local-command velho :application-to-asianhallinta :id app-id :lang "fi") => ok?

            (count (:verdicts (query-application local-query pena app-id))) => 0

            (sftp/process-case-management-responses (test-system/get-common-components))

            (fact "Zip has been moved from to_lupapiste to archive folder"
              (count (util/get-files-by-regex local-target-folder #"verdict1\.zip$")) => 0
              (count (util/get-files-by-regex (str local-target-folder "/archive") #"verdict1\.zip$")) => 1)

            (fact "application has verdict"
              (count (:verdicts (query-application local-query pena app-id))) => 1)))))

    (against-background
        [(app/make-application-id anything) => "LP-297-2015-00002"]
      (fact "Batchrun with unsuccessful verdict save (no xml inside zip)"
        (files/with-zip-file [example-ah-attachment-path example-ah-attachment2-path]
          (let [_      (verbose-copy (io/file zip-file) (fs/file (str local-target-folder "/verdict2.zip")))
                app    (create-local-ah-app)
                app-id (:id app)]

            (generate-documents! app pena true)
            (local-command velho :application-to-asianhallinta :id app-id :lang "fi")

            (count (:verdicts (query-application local-query pena app-id))) => 0

            (sftp/process-case-management-responses (test-system/get-common-components))

            (fact "Zip has been moved from to_lupapiste to error folder"
              (count (util/get-files-by-regex local-target-folder #"verdict2\.zip$")) => 0
              (count (util/get-files-by-regex (str local-target-folder "/error") #"verdict2\.zip$")) => 1)

            (fact "application doesn't have verdict"
              (count (:verdicts (query-application local-query pena app-id))) => 0)))))))

(with-test-system {:fixture "minimal"}
  (facts "Processing asianhallinta verdicts"
    (files/with-zip-file [example-ah-xml-path example-ah-attachment-path example-ah-attachment2-path]
      (let [application (create-local-ah-app)
            AsianPaatos (:AsianPaatos parsed-example-ah-xml)
            system      (test-system/get-common-components)]

        (fact "If application in wrong state, return error"
          (ah-reader/process-message system zip-file "dev_ah_kuopio" system-user) => (partial expected-failure? "error.integration.asianhallinta.wrong-state"))

                                        ; generate docs, set application to 'sent' state by moving it to asianhallinta
        (generate-documents! application pena true)
        (local-command velho :application-to-asianhallinta :id (:id application) :lang "fi")

        (Thread/sleep 500)
        (facts "Well-formed, return ok"
          (dummy-email/reset-sent-messages)

          (fact* "Creates verdict based on xml"
            (:verdicts application) => empty?
            (ah-reader/process-message system zip-file "dev_ah_kuopio" system-user) => ok?
            (let [application (query-application local-query pena (:id application))
                  verdicts    (:verdicts application) =not=> empty?
                  new-verdict (last verdicts)
                  paatos      (first (:paatokset new-verdict))
                  poytakirja  (first (:poytakirjat paatos))]

              (fact "application state is verdictGiven"

                (keyword (:state application)) => :verdictGiven

                (let [email (last (dummy-email/messages :reset true))]
                  (:to email) => (contains (min-util/email-for-key pena))
                  (:subject email) => "Lupapiste: Suusaarenkierto 44, Kuopio - hankkeen tila on nyt Päätös annettu"
                  email => (partial contains-application-link-with-tab? (:id application) "verdict" "applicant"))

                (:kuntalupatunnus new-verdict) => (:AsianTunnus AsianPaatos)
                (:paatostunnus paatos) => (:PaatoksenTunnus AsianPaatos)
                (:anto (:paivamaarat paatos)) => (date/timestamp (:PaatoksenPvm AsianPaatos))

                (:paatoksentekija poytakirja) => (:PaatoksenTekija AsianPaatos)
                (:pykala poytakirja) => (:Pykala AsianPaatos)
                (:paatospvm poytakirja) => (date/timestamp (:PaatoksenPvm AsianPaatos))
                (:paatoskoodi poytakirja) => (:PaatosKoodi AsianPaatos))

              (fact "verdictDate is correct"
                (:verdictDate application) => (date/timestamp (:PaatoksenPvm AsianPaatos)))))

          (fact* "Pushes verdict if there exists previous verdicts"
            ;; must empty applications because the app-is is hard coded in the xml :(
            (fixture/apply-fixture "minimal" (test-system/get-common-components))


            (let [app    (create-local-ah-app)
                  app-id (:id app)]
              (local-command velho :new-legacy-verdict-draft :id app-id) => ok?

              (let [application        (query-application local-query velho app-id)
                    orig-verdicts      (:pate-verdicts application) =not=> empty?
                    orig-verdict-count (count orig-verdicts)]
                (generate-documents! application pena true)
                (local-command velho :application-to-asianhallinta :id app-id :lang "fi")

                (ah-reader/process-message system zip-file "dev_ah_kuopio" system-user)

                (let [application (query-application local-query velho app-id)
                      new-verdict (last (:verdicts application))]
                  (count (:verdicts application)) => 1 ;; from asianhallinta
                  (count (:pate-verdicts application)) => 1  ;; manually created draft
                  (:kuntalupatunnus new-verdict) => (:AsianTunnus AsianPaatos)

                  (fact "application state is verdictGiven" (keyword (:state application)) => :verdictGiven)

                  (let [email (last (dummy-email/messages :reset true))]
                    (:to email) => (contains (min-util/email-for-key pena))
                    (:subject email) => "Lupapiste: Suusaarenkierto 44, Kuopio - hankkeen tila on nyt Päätös annettu"
                    email => (partial contains-application-link-with-tab? (:id application) "verdict" "applicant")))))))))
    (against-background
        (app/make-application-id anything) => "LP-297-2015-00001"))

  ;; The following depend on the db state from above
  (let [system (test-system/get-common-components)]
    (facts "Errorenous, return error"
      (fact "If passed zip file is missing"
        (ah-reader/process-message system "/foo/bar" "dev_ah_kuopio" system-user) => (partial expected-failure? "error.integration.asianhallinta-file-not-found"))

      (fact "If xml message missing from zip"
        (files/with-zip-file [example-ah-attachment-path] ; xml is missing
          (ah-reader/process-message system zip-file "dev_ah_kuopio" system-user) => (partial expected-failure? "error.integration.asianhallinta-wrong-number-of-xmls")))

      (fact "If attachment files missing from zip"
        (files/with-zip-file [example-ah-xml-path] ;attachment is missing
          (ah-reader/process-message system zip-file "dev_ah_kuopio" system-user) => (partial expected-failure? "error.integration.asianhallinta-missing-attachment")))

      (fact "If xml message references an application that the ftp user cannot access"
        (files/with-zip-file [example-ah-xml-path example-ah-attachment-path example-ah-attachment2-path]
          (ah-reader/process-message system zip-file "sipoo" system-user) => (partial expected-failure? "error.integration.asianhallinta.unauthorized")))))

  (facts "unit tests"
    (files/with-zip-file [example-ah-xml-path example-ah-attachment-path]
      (fact* "Can unzip passed zipfile"
        (fs/with-temp-dir [tmp-dir {:prefix "ah-unzip-test"}]
          (unzip-file zip-file tmp-dir) =not=> (throws Exception)
          (let [glob (->> [example-ah-xml-path example-ah-attachment-path]
                          (map fs/file-name)
                          (apply format "{%s,%s}"))]

            (count (fs/glob tmp-dir glob)) => 2))))

    (fact "Creates correct verdict model from xml"
      (build-verdict parsed-example-ah-xml 123456) =>
      {:id              "deadbeef" ; mongo/create-id
       :source          "ah"
       :kuntalupatunnus "2015-1234"
       :timestamp       123456
       :paatokset       [{:paatostunnus "12345678"
                          :paivamaarat  {:anto (date/timestamp "1.1.2015")}
                          :poytakirjat  [{:paatoksentekija "Pena Panaani"
                                          :paatospvm       (date/timestamp "1.1.2015")
                                          :pykala          "987"
                                          :paatoskoodi     "my\u00F6nnetty"
                                          :id              "deadbeef"}]}]}
      (provided (mongo/create-id) => "deadbeef"))

    (fact "Paatostunnus is used if paatoskoodi not available"
      (build-verdict parsed-example-ah-xml-no-paatoskoodi 123456) =>
      {:id              "deadbeef" ; mongo/create-id
       :source          "ah"
       :kuntalupatunnus "2015-1234"
       :timestamp       123456
       :paatokset       [{:paatostunnus "12345678"
                          :paivamaarat  {:anto (date/timestamp "1.1.2015")}
                          :poytakirjat  [{:paatoksentekija "Pena Panaani"
                                          :paatospvm       (date/timestamp "1.1.2015")
                                          :pykala          "987"
                                          :paatoskoodi     "12345678"
                                          :id              "deadbeef"}]}]}
      (provided (mongo/create-id) => "deadbeef"))))
