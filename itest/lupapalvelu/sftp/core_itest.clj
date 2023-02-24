(ns lupapalvelu.sftp.core-itest
  "Typical (happy) use cases for both legacy and gcs SFTP."
  (:require [babashka.fs :as fs]
            [clj-uuid :as uuid]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.sftp-itest-util :refer [gcs-remove-test-folder
                                                 fs-remove-test-dirs]]
            [lupapalvelu.sftp.context :as sftp-ctx]
            [lupapalvelu.sftp.core :as sftp]
            [lupapalvelu.xml.gcs-writer :as gcsw]
            [midje.sweet :refer :all]
            [mount.core :as mount]
            [sade.common-reader :refer [strip-xml-namespaces]]
            [sade.date :as date]
            [sade.env :as env]
            [sade.files :as files]
            [sade.strings :as ss]
            [sade.xml :as xml]))

(def OUTDIR (env/value :outgoing-directory))

(defn configure [sftp-type username]
  (fact {:midje/description (format "Configure (%s): %s" sftp-type username)}
    (command admin :update-sftp-organization-configuration
             :organizationId "753-R"
             :sftpType sftp-type
             :users [{:username username :type "backing-system" :permitTypes ["R"]}
                     {:username username :type "case-management" :permitTypes ["P"]}
                     {:username username :type "invoicing"}]) => ok?
    (command admin :update-invoicing-config
             :org-id "753-R"
             :invoicing-config {:local-sftp? true}) => ok?))

(defn generate-and-submit [apikey address operation]
  (let [{app-id :id
         :as    a} (create-and-submit-local-application apikey
                                                        :address address
                                                        :operation operation)]
    (generate-documents! a apikey true)
    (upload-file-and-bind apikey
                          app-id
                          {:type     {:type-group "rakennuspaikka"
                                      :type-id    "karttaote"}
                           :filename "dev-resources/test-pdf.pdf"
                           :contents "Map"
                           :group    {}})
    app-id))

(defn approve [apikey app-id]
  (fact "Approve"
    (command apikey :update-app-bulletin-op-description :id app-id
             :description "El toro e tin!") => ok?
    (command apikey :approve-application :id app-id
             :lang "fi") => ok?))

(defn to-cm [apikey app-id]
  (fact "Application to case management"
    (command apikey :application-to-asianhallinta :id app-id
             :lang "fi") => ok?))


(def xml-check #(re-matches #".+_(\d+)\.xml" %))

(defn check-files [app-id filenames xml?]
  (fact {:midje/description (format "Correct files (xml: %s)" xml?)}
    filenames => (just (cond-> [(str app-id "_submitted_application.pdf")
                                (str app-id "_current_application.pdf")
                                #(ss/ends-with % "_test-pdf.pdf")]
                         xml? (conj xml-check))
                       :in-any-order)))

(defn extract-data [xml-string cm?]
  (let [xml (-> xml-string
                (xml/parse-string "utf8")
                strip-xml-namespaces)]
    {:app-id (xml/get-text xml (if cm?
                                 [:HakemusTunnus]
                                 [:muuTunnustieto :MuuTunnus :tunnus]))
     :link   (xml/get-text xml (if cm?
                                 [:LinkkiLiitteeseen]
                                 [:linkkiliitteeseen]))}))

(defn check-integration-message
  ([apikey app-id msg? legacy? cm?]
   (fact "Integration message"
     (let [{:keys [ok messages]} (query apikey :integration-messages :id app-id)]
       ok => true
       (if-not msg?
         (fact "No messages"
           messages => {:ok [] :error [] :waiting []})
         (let [filename (-> messages :waiting first :name)]
           (fact "One message"
             messages => (just {:ok      []
                                :error   []
                                :waiting (just [(just {:name         xml-check
                                                       :size         pos?
                                                       :content-type "application/xml"
                                                       :modified     pos?})])}))
           (fact "XML contents"
             (let [{:keys [status
                           body]} (local-raw apikey :integration-message :id app-id
                                             :fileType "waiting"
                                             :filename filename)]
               status => 200
               (extract-data body cm?)
               => (just {:app-id app-id
                         :link   #(ss/starts-with % (str (if legacy?
                                                           (env/value :fileserver-address)
                                                           (env/value :gcs :fileserver-address))
                                                         (if cm?
                                                           "/asianhallinta/from_lupapiste"
                                                           "/rakennus")))}))))))))
  ([apikey app-id msg? legacy?]
   (check-integration-message apikey app-id msg? legacy? false))
  ([apikey app-id msg?]
   (check-integration-message apikey app-id msg? false))
  ([apikey app-id]
   (check-integration-message apikey app-id false)))

(defn make-verdict-zip [ftp-user app-id legacy?]
  (fs/with-temp-dir [dir]
    (let [a-pdf "resources/asianhallinta/sample/ah-example-attachment.pdf"
          v-xml (ss/join-file-path (str dir) "verdict.xml")
          v-zip (ss/join-file-path (when legacy? OUTDIR)
                                   ftp-user "asianhallinta/to_lupapiste/cm-verdict.zip")]
      (spit v-xml
            (format "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<ah:AsianPaatos version=\"1.1\"
  xmlns:ah=\"http://www.lupapiste.fi/asianhallinta\"
  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
  xsi:schemaLocation=\"http://www.lupapiste.fi/asianhallinta asianhallinta.xsd\">
  <ah:HakemusTunnus>%s</ah:HakemusTunnus>
  <ah:AsianTunnus>2015-1234</ah:AsianTunnus>
  <ah:PaatoksenTunnus>12345678</ah:PaatoksenTunnus>
  <ah:PaatoksenPvm>2015-01-01</ah:PaatoksenPvm>
  <ah:PaatosKoodi>myönnetty</ah:PaatosKoodi>
  <ah:PaatoksenTekija>Casey Manageran</ah:PaatoksenTekija>
  <ah:Pykala>321</ah:Pykala>
  <ah:Liitteet>
    <ah:Liite>
        <ah:Kuvaus>Looking good!</ah:Kuvaus>
        <ah:Tyyppi>Verdict note</ah:Tyyppi>
        <ah:LinkkiLiitteeseen>sftp://foo/bar/baz/ah-example-attachment.pdf</ah:LinkkiLiitteeseen>
    </ah:Liite>
  </ah:Liitteet>
</ah:AsianPaatos>" app-id))
      (files/with-zip-file [v-xml a-pdf]
        (if legacy?
          (fs/copy zip-file v-zip)
          (gcsw/write-file v-zip (fs/file zip-file)))))))

(defn zip-moved [ftp-user legacy?]
  (let [outdir   (when legacy? OUTDIR)
        old-path (ss/join-file-path outdir ftp-user
                                    "asianhallinta/to_lupapiste/cm-verdict.zip")
        new-path (ss/join-file-path outdir ftp-user
                                    "asianhallinta/to_lupapiste/archive/cm-verdict.zip")]
    (facts "CM verdict zip archived"
     (if legacy?
       (fact "Legacy"
         (fs/exists? old-path) => false
         (fs/exists? new-path) => true)
       (fact "GCS"
         (gcsw/file-exists? old-path) => false
         (gcsw/file-exists? new-path) => true)))))

(defn cm-verdict [ftp-user app-id legacy?]
  (fact "CM verdict"
    (fact "No verdict yet"
      (query-application sonja app-id)
      => (contains {:state "sent" :verdicts empty?}))
    (make-verdict-zip ftp-user app-id legacy?)
    (fact "Process verdict"
      (sftp/process-case-management-responses)
      (let [verdict-ts      (date/timestamp "1.1.2015")
            {:keys [attachments verdicts
                    state]} (query-application sonja app-id)
            vid             (-> verdicts first :id)]
        state => "verdictGiven"
        (last attachments)
        => (contains {:contents      "Looking good!"
                      :latestVersion (contains {:filename "ah-example-attachment.pdf"})
                      :target        (contains {:type "verdict" :id vid})
                      :type          {:type-group "muut" :type-id "paatos"}})
        verdicts
        => (just [(contains {:id              vid
                             :kuntalupatunnus "2015-1234"
                             :source          "ah"
                             :paatokset       (just
                                                [(contains
                                                   {:paatostunnus "12345678"
                                                    :paivamaarat  {:anto verdict-ts}
                                                    :poytakirjat
                                                    (just [(just {:id              truthy
                                                                  :paatoksentekija "Casey Manageran"
                                                                  :paatoskoodi     "myönnetty"
                                                                  :paatospvm       verdict-ts
                                                                  :pykala          "321"})])})])})])))
    (zip-moved ftp-user legacy?))
  )

(defn make-legacy-dirs [ftp-user]
  (let [[bld cm $] (map (partial ss/join-file-path OUTDIR ftp-user)
                        ["rakennus" "asianhallinta/from_lupapiste" "laskutus"])]
    (sftp-ctx/fs-make-dirs bld :subdirs? true)
    (sftp-ctx/fs-make-dirs cm :subdirs? true :cm? true)
    (sftp-ctx/fs-make-dirs $)))

(defn legacy-file-list
  ([ftp-user cm?]
   (mapv fs/file-name
         (fs/list-dir (ss/join-file-path OUTDIR ftp-user
                                         (if cm?
                                           "asianhallinta/from_lupapiste"
                                           "rakennus"))
                      fs/regular-file?)))
  ([ftp-user]
   (legacy-file-list ftp-user false)))

(defn bounce [apikey app-id]
  (fact "Bounce and cleanup"
    (command apikey :request-for-complement :id app-id) => ok?
    (command apikey :cleanup-krysp :id app-id) => ok?))

(defn gcs-file-list
  ([ftp-user cm?]
   (mapv :fileId
         (gcsw/list-files (ss/join-file-path ftp-user
                                             (if cm?
                                               "asianhallinta/from_lupapiste"
                                               "rakennus")))))
  ([ftp-user]
   (gcs-file-list ftp-user false)))

(mount/start #'mongo/connection)
(mongo/with-db "sftp_core_itest"
  (fixture/apply-fixture "minimal")
  (with-local-actions
    (facts "File system (legacy)"
      (let [ftp-user (str "legacy_" (uuid/v1) "_test")]
        (against-background
          [(before :contents (make-legacy-dirs ftp-user))
           (after :contents (fs-remove-test-dirs ftp-user))]
          (configure "legacy" ftp-user)
          (facts "Building permit (backing system)"
              (let [app-id (generate-and-submit pena "Legacy Lane 1" "aita")]
              app-id => truthy
              (approve sonja app-id)
              (check-files app-id (legacy-file-list ftp-user) true)
              (check-integration-message sonja app-id true true)
              (bounce sonja app-id)
              (check-files app-id (legacy-file-list ftp-user) false)
              (check-integration-message sonja app-id)))
          (fact "Invoicing file"
            (sftp/write-invoicing-file "753-R" "legacy-invoice.xml"
                                       "<xml>100 markkaa</xml>")
            (fs/exists? (ss/join-file-path OUTDIR ftp-user "laskutus"
                                           "legacy-invoice.xml"))
            => true)
          (facts "Case management"
            (let [app-id (generate-and-submit pena "Exception End 11" "poikkeamis")]
              app-id => truthy
              (to-cm sonja app-id)
              (check-files app-id (legacy-file-list ftp-user true) true)
              (check-integration-message sonja app-id true true true)
              (cm-verdict ftp-user app-id true))
            ))))
    (facts "Google Cloud Storage (gcs)"
      (let [ftp-user (str "cloud_" (uuid/v1) "_test")]
        (against-background
          [(after :contents (gcs-remove-test-folder ftp-user 21))]
          (configure "gcs" ftp-user)
          (facts "Building permit (backing system)"
            (let [app-id (generate-and-submit teppo "Cloud Corner 2" "aita")]
              app-id => truthy
              (approve sonja app-id)
              (check-files app-id (gcs-file-list ftp-user) true)
              (check-integration-message sonja app-id true false)
              (bounce sonja app-id)
              (check-files app-id (gcs-file-list ftp-user) false)
              (check-integration-message sonja app-id)))
          (fact "Invoicing file"
            (sftp/write-invoicing-file "753-R" "cloud-invoice.xml"
                                       "<xml>200 euros</xml>")
            (gcsw/file-exists? (ss/join-file-path ftp-user "laskutus"
                                                  "cloud-invoice.xml"))
            => true)
          (facts "Case management"
            (let [app-id (generate-and-submit pena "Anomaly Alley 22" "poikkeamis")]
              app-id => truthy
              (to-cm sonja app-id)
              (check-files app-id (gcs-file-list ftp-user true) true)
              (check-integration-message sonja app-id true false true)
              (cm-verdict ftp-user app-id false))))))))
