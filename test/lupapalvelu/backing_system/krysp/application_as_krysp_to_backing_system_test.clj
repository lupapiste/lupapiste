(ns lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system-test
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system :refer :all]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.sftp.core :as sftp]
            [lupapalvelu.xml.disk-writer :as writer]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.core :refer [now]]))

(testable-privates lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system
                   remove-unsupported-attachments
                   remove-non-approved-designers
                   filter-attachments-by-state
                   get-begin-of-link
                   foreman-app-to-foreman-termination-app)

(facts "get-begin-of-link"
  (against-background
    (sade.env/value [:fileserver-address]) => "sftp://file-server"
    (sade.env/value [:gcs :fileserver-address]) => "sftp://gcs-server"
    (sade.env/value :host) => "http://server")
  (fact "Legacy SFTP"
    (get-begin-of-link "R" {}) => "sftp://file-server/rakennus/"
    (get-begin-of-link "R" {:sftpType                         "legacy"
                            :use-attachment-links-integration false})
    => "sftp://file-server/rakennus/")
  (fact "GCS SFTP"
    (get-begin-of-link "R" {:sftpType "gcs"}) => "sftp://gcs-server/rakennus/"
    (get-begin-of-link "R" {:sftpType                         "gcs"
                            :use-attachment-links-integration false})
    => "sftp://gcs-server/rakennus/")
  (fact "HTTP raw"
    (get-begin-of-link "R" {:use-attachment-links-integration true})
    => "http://server/api/raw/"
    (get-begin-of-link "R" {:use-attachment-links-integration true
                            :krysp                            {:R {:http {:enabled false}}}})
    => "http://server/api/raw/")
  (fact "HTTP REST"
    (get-begin-of-link "R" {:use-attachment-links-integration true
                            :krysp                            {:R {:http {:enabled true}}}})
    => "http://server/rest/"
    (get-begin-of-link "R" {:krysp {:R {:http {:enabled true}}}})
    => "http://server/rest/"))

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

(fact "filter attachments by state"
  (let [application {:attachments [{:applicationState "draft"}
                                   {:applicationState "submitted"}
                                   {:applicationState "verdictGiven"}
                                   {:applicationState "constructionStarted"}]
                     :id "LP-123456"}]

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

(defn write-xml [dir filename kayttotapaus]
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
    (fs/create-dirs dir))
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
  (let [app {:id "LP-000-000000"
             :attachments []
             :state "submitted"
             :primaryOperation {:name "tyonjohtajan-nimeaminen-v2"}}]
    (foreman-app-to-foreman-termination-app true 123455667 app) =>
    {:id "LP-000-000000-T00"
     :state "submitted"
     :permitSubtype "tyonjohtaja-hakemus"
     :primaryOperation {:name "tyonjohtajan-vastuiden-paattaminen"}
     :foremanTermination {:ended 123455667}}))
