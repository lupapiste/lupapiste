(ns lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system-test
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.backing-system.dmcity.config :as dm-cfg]
            [lupapalvelu.backing-system.dmcity.core :as dmcity]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :refer :all]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.poikkeamis-canonical-test :refer [poikkari-hakemus]]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.sftp.context :as sftp-ctx]
            [lupapalvelu.sftp.core :as sftp]
            [lupapalvelu.test-util :refer [err]]
            [lupapalvelu.tiedonohjaus :as toj]
            [lupapalvelu.xml.disk-writer :as writer]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.core :refer [now]])
  (:import [java.io File]))

(set! *warn-on-reflection* true)

(testable-privates lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system
                   remove-unsupported-attachments
                   remove-non-approved-designers
                   filter-attachments-by-state
                   get-begin-of-link
                   enrich-tos-function-name
                   foreman-app-to-foreman-termination-app)

(defn dmcity-config [r-enabled?]
  (dm-cfg/simple->config {:username "hello"  :password "world"
                          :vault    "sommer" :url      "http://example.net/dmcity"
                          :targets  {:R r-enabled?}}))

(facts "get-begin-of-link"
  (against-background
      (sade.env/value [:fileserver-address]) => "sftp://file-server"
      (sade.env/value [:gcs :fileserver-address]) => "sftp://gcs-server"
      (sade.env/value :backing-system :crypto-key) => "YmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmJiYmI=")
  (let [system {:config {:host "http://server"}}]
    (fact "Legacy SFTP"
      (get-begin-of-link system "R" {}) => "sftp://file-server/rakennus/"
      (get-begin-of-link system "R" {:sftpType                         "legacy"
                                     :use-attachment-links-integration false})
      => "sftp://file-server/rakennus/")
    (fact "GCS SFTP"
      (get-begin-of-link system "R" {:sftpType "gcs"}) => "sftp://gcs-server/rakennus/"
      (get-begin-of-link system "R" {:sftpType                         "gcs"
                                     :use-attachment-links-integration false})
      => "sftp://gcs-server/rakennus/")
    (fact "HTTP raw"
      (get-begin-of-link system "R" {:use-attachment-links-integration true})
      => "http://server/api/raw/"
      (get-begin-of-link system "R" {:use-attachment-links-integration true
                                     :dmcity                           (dmcity-config false)})
      => "http://server/api/raw/")
    (fact "HTTP REST"
      (get-begin-of-link system "R" {:use-attachment-links-integration true
                                     :dmcity                           (dmcity-config true)})
      => "http://server/rest/"
      (get-begin-of-link system "R" {:dmcity (dmcity-config true)})
      => "http://server/rest/")))

(fact "Remove function removes unsupported attachments"
  (let [application {:attachments [{:type {:type-group :muut
                                           :type-id :paatos}}
                                   {:type {:type-group :muut
                                           :type-id :muu}}
                                   {:type {:type-group :paapiirustus
                                           :type-id :asemapiirros}}]
                     :id "LP-123456"}]
    (remove-unsupported-attachments application) => {:attachments [{:type {:type-group :muut
                                                                           :type-id :muu}}
                                                                   {:type {:type-group :paapiirustus
                                                                           :type-id :asemapiirros}}]
                                                     :id "LP-123456"}))

(facts enrich-tos-function-name
  (fact "no tos-function"
    (enrich-tos-function-name {:id ..app-id.. :organization "753-R"})
    => {:id ..app-id.. :organization "753-R" :tosFunctionName nil}
    (provided (toj/available-tos-functions anything) => irrelevant :times 0))

  (fact "one matching tos-function"
    (-> {:id ..app-id.. :organization "753-R" :tosFunction "12 13 14 15"}
        enrich-tos-function-name
        :tosFunctionName) => ..tos-name..
    (provided (toj/available-tos-functions "753-R") => [{:code "12 13 14 15" :name ..tos-name..}]))

  (fact "no matching tos-function"
    (-> {:id ..app-id.. :organization "753-R" :tosFunction "12 13 14 15"}
        enrich-tos-function-name
        :tosFunctionName) => nil
    (provided (toj/available-tos-functions "753-R") => [{:code "00 00 00 00" :name ..tos-name..}]))

  (fact "multiple tos-functions for organization"
    (-> {:id ..app-id.. :organization "753-R" :tosFunction "12 13 14 15"}
        enrich-tos-function-name
        :tosFunctionName) => ..tos-name-2..
    (provided (toj/available-tos-functions "753-R") => [{:code "12 13 14 14" :name ..tos-name-1..}
                                                        {:code "12 13 14 15" :name ..tos-name-2..}
                                                        {:code "12 13 14 16" :name ..tos-name-3..}
                                                        {:code "12 13 14 17" :name ..tos-name-4..}
                                                        {:code "12 13 14 18" :name ..tos-name-5..}
                                                        {:code "12 13 14 19" :name ..tos-name-6..}])))

(fact "filter attachments by state"
  (let [application {:attachments [{:applicationState "draft"}
                                   {:applicationState "submitted"}
                                   {:applicationState "verdictGiven"}
                                   {:applicationState "constructionStarted"}]
                     :id          "LP-123456"}]

    (fact "post verdict attachments are filtered in  pre verdict state"
      (filter-attachments-by-state "submitted" application) => {:attachments [{:applicationState "draft"}
                                                                              {:applicationState "submitted"}]
                                                                :id "LP-123456"})

    (fact "no filtering in post verdict state"
      (filter-attachments-by-state "verdictGiven" application) => {:attachments [{:applicationState "draft"}
                                                                                 {:applicationState "submitted"}
                                                                                 {:applicationState "verdictGiven"}
                                                                                 {:applicationState "constructionStarted"}]
                                                                   :id "LP-123456"})

    (fact "no filtering in unknown state"
      (filter-attachments-by-state nil application) => {:attachments [{:applicationState "draft"}
                                                                      {:applicationState "submitted"}
                                                                      {:applicationState "verdictGiven"}
                                                                      {:applicationState "constructionStarted"}]
                                                        :id "LP-123456"})))

(facts "Designer documents that have not been approved are removed"
  (let [documents (mapv #(model/new-document (schemas/get-schema 1 %) 123) ["paasuunnittelija" "suunnittelija" "hakija-r"])
        approved-docs (mapv #(assoc-in % [:meta :_approved] {:value "approved" :timestamp 124}) documents)]

    (fact "Approved have not been removed"
      (let [filtered (:documents (remove-non-approved-designers {:documents approved-docs}))]
        (count filtered) => 3
        (get-in (first filtered) [:schema-info :name]) => "paasuunnittelija"
        (get-in (second filtered) [:schema-info :name]) => "suunnittelija"
        (get-in (last filtered) [:schema-info :name]) => "hakija-r"))

    (fact "Non-approved have been removed"
      (let [filtered (:documents (remove-non-approved-designers {:documents documents}))]
        (count filtered) => 1
        (get-in (first filtered) [:schema-info :name]) => "hakija-r"))

    (fact "Modified, previously approved document is removed"
      (let [modified (assoc-in approved-docs [0 :data :henkilotiedot :etunimi :modified] 125)
            filtered (:documents (remove-non-approved-designers {:documents modified}))]
        (count filtered) => 2
        (get-in (first filtered) [:schema-info :name]) => "suunnittelija"
        (get-in (last filtered) [:schema-info :name]) => "hakija-r"))))

(defn write-xml ^File [dir filename kayttotapaus]
  (let [file (io/file (str dir "/" filename))]
    (spit file
          (str "<?xml version='1.0' encoding='UTF-8' ?>"
               "<hii:TopLevel><rakval:RakennusvalvontaAsia>"
               "<bar:kayttotapaus>" kayttotapaus "</bar:kayttotapaus>"
               "</rakval:RakennusvalvontaAsia></hii:TopLevel>"))
    (fact {:midje/description (str file)} (.exists file) => true)
    file))

(def application-id (str "Foobar-" (now)))
(def foo-org  {:krysp {:R {:ftpUser "test_foo"
                           :version "1.2.3"}}})

(defn- get-sftp-user [organization permit-type]
  (get-in organization [:krysp (keyword permit-type) :ftpUser]))

(defn resolve-output-directory [organization permit-type]
  {:pre  [(map? organization) permit-type]
   :post [%]}
  (format "%s/%s%s"
          (sade.env/value :outgoing-directory)
          (get-sftp-user organization permit-type)
          (permit/get-sftp-directory permit-type)))

(defn create-dirs-if-needed [dir]
  (when-not (fs/exists? dir)
    (sftp-ctx/create-non-existing-dirs dir))
  (fact "Directory exists"
    (and (fs/exists? dir) (fs/directory? dir)) => true))

(facts "Deprecated messages cleanup"
  (let [app      {:id application-id :organization "FOO-R" :permitType "R"}
        dir      (resolve-output-directory foo-org (:permitType app))
        safe-dir (str dir "/safe")]
         (fact "Output directory exists"
           (create-dirs-if-needed dir))
         (fact "Make safe subdir"
           (create-dirs-if-needed safe-dir))
         (let [rm1   (write-xml dir (str (:id app) "_first.xml") "Rakentamisen aikainen muutos")
               rm2   (write-xml dir (str (:id app) "_second.XML") "Uusi hakemus")
               stay1 (write-xml dir (str (:id app) "_third.xml") "Uusi katselmus")
               bad1  (write-xml dir (str (:id app) "fourth.xml") "Uusi hakemus")
               bad2  (write-xml dir (str (:id app) "_fifthxml") "Uusi hakemus")
               bad3  (write-xml dir (str (:id app) "_sixth.xmlx") "Uusi hakemus")
               stay2 (write-xml dir (str (:id app) "_seventh.xml") "Uusi hakemus")
               stay3 (write-xml dir (str (:id app) "_eighth.xml") "Uusi hakemus")
               bad4  (write-xml dir (str "bad" (:id app) "_ninth.xml") "Uusi hakemus")
               safe  (write-xml safe-dir (str (:id app) "_safe.xml") "Uusi hakemus")]
           (spit stay2 "Garbage instead of good XML. Zhen daomei!")
           (spit stay3 (str "<?xml version='1.0' encoding='UTF-8' ?>"
              "<hii:TopLevel><rakval:RakennusvalvontaAsia>"
              "<bar:baz>Uusi hakemus</bar:baz>"
              "</rakval:RakennusvalvontaAsia></hii:TopLevel>"))
           (fact "KRYSP file count before cleanup" (count (writer/krysp-xml-files application-id dir)) => 5) ;; rm + stay

           (fact "Cleanup does not throw exceptions"
             (sftp/cleanup-directory {:application  app
                                      :organization (delay foo-org)}) => nil)
           (fact "KRYSP file count after cleanup" (count (writer/krysp-xml-files application-id dir)) => 3) ;; stay

           (fact "Correct files have been deleted"
                 (.exists rm1) => false
                 (.exists rm2) => false)

           (fact "All the others still exist"
                 (.exists stay1) => true
                 (.exists bad1) => true
                 (.exists bad2) => true
                 (.exists bad3) => true
                 (.exists stay2) => true
                 (.exists stay3) => true
                 (.exists bad4) => true
                 (.exists safe) => true)
           (fact "Unknown organization fails silently"
             (sftp/cleanup-directory {:application (assoc app :organization "Unknown")}) => nil)
           (fact "Unknown permit-type throws"
             (sftp/cleanup-directory {:application  (assoc app :permitType "XYZ")
                                      :organization foo-org}) => (throws))
           (fact "No messages for the application"
             (sftp/cleanup-directory {:application  (assoc app :id "Meiyou")
                                      :organization foo-org}) => nil))))

(facts "Foreman app transformation"
  (let [app {:id               "LP-000-000000"
             :attachments      []
             :state            "submitted"
             :primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}}]
    (foreman-app-to-foreman-termination-app true 123455667 app) =>
    {:id                 "LP-000-000000-T00"
     :attachments        []
     :state              "submitted"
     :permitSubtype      "tyonjohtaja-hakemus"
     :primaryOperation   {:name "tyonjohtajan-vastuiden-paattaminen"}
     :foremanTermination {:ended          123455667
                          :termination-ts 123455667}}

    (fact "existing ending timestamp respected"
      (->> (assoc app :foremanTermination {:ended 1000000})
           (foreman-app-to-foreman-termination-app true 123455667))
      => (contains {:foremanTermination {:ended          1000000
                                         :termination-ts 123455667}}))

    (let [attachment-1 {:id     "foo"
                        :source {:type "foreman-termination-verdict"}}
          attachment-2 {:id "bar"}]
      (fact "attachments describing foreman termination are preserved"
        (->> (assoc app :attachments [attachment-1
                                      attachment-2])
             (foreman-app-to-foreman-termination-app true 123455667))
        => (contains {:attachments [attachment-1]})))))

(defn save-as-krysp [p-target submitted-app]
  (try
    (save-application-as-krysp
      {:application  poikkari-hakemus
       :organization (cond-> {:krysp  {:P {:ftpUser "dev_sipoo"
                                           :version "2.2.4"}}}
                       (boolean? p-target)
                       (assoc :dmcity (dm-cfg/simple->config
                                        {:username "hello" :password "world"
                                         :vault    "sommer"
                                         :url      "http://example.net/dmcity"
                                         :targets  {:P p-target}})))
       :system       {:config {:host "http://localhost"}}}
      "fi" submitted-app)
    (catch Exception e
      (ex-data e))))

(facts "Submitted application vs. dmcity"
  (fact "Without dmcity the submitted application is mandatory"
    (doseq [[p app] [[nil nil] [false nil] [nil {}] [nil {:id "bad-id"}]]]
      (save-as-krysp p app) => (err :error.submitted-application)))
  (fact "With dmcity it is not"
    (doseq [app [nil {} {:id "bad-id"}]]
      (save-as-krysp true app) => empty?
      (provided
        (dmcity/send-xml anything :application anything) => nil :times 1))))

(against-background
  [(lupapalvelu.mongo/select :app-links anything) => nil]
  (facts "Enriched application data for foreman-v3"
    (fact "linkPermitData gets populated correctly with main application data"
      (-> (meta-fields/enrich-with-link-permit-data {:id                   "app-foreman-v3-test"
                                                     :original-application {:id               "real-app"
                                                                            :permitType       "R"
                                                                            :primaryOperation {:name "kerrostalo"}
                                                                            :permitSubtype    "muutoslupa"}
                                                     :permitType           "TJ-V3"
                                                     :documents            [{:schema-info {:name "tyonjohtaja-v3"}
                                                                             :data        {:foreman             {:value "John Doe"}
                                                                                           :foremanRole         {:value "vastaava työnjohtaja"}
                                                                                           :isSubstituteForeman {:value false}}}]})
          :linkPermitData)
      => [{:id            "real-app"
           :type          "application"
           :operation     "kerrostalo"
           :permitSubtype "muutoslupa"}])))
