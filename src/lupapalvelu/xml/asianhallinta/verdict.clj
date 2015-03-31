(ns lupapalvelu.xml.asianhallinta.verdict
  (:require [sade.core :refer [ok fail fail!] :as core]
            [sade.common-reader :as reader]
            [sade.xml :as xml]
            [pandect.core :as pandect]
            [taoensso.timbre :refer [error]]
            [me.raynes.fs :as fs]
            [me.raynes.fs.compression :as fsc]
            [monger.operators :refer :all]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.organization :as org]
            [lupapalvelu.action :as action]
            [lupapalvelu.mongo :as mongo]
            [clojure.string :as s]
            [sade.common-reader :as cr]
            [lupapalvelu.attachment :as attachment]))

(defn- error-and-fail! [error-msg fail-key]
  (error error-msg)
  (fail! fail-key))

(defn- unzip-file [path-to-zip]
  (if-not (fs/exists? path-to-zip)
    (error-and-fail! (str "Could not find file " path-to-zip) :error.integration.asianhallinta-file-not-found)
    (let [tmp-dir (fs/temp-dir "ah")]
      (fsc/unzip path-to-zip tmp-dir)
      tmp-dir)))

(defn- ensure-attachments-present! [unzipped-path attachments]
  (let [attachment-paths (->> attachments
                              (map :LinkkiLiitteeseen)
                              (map fs/base-name))]
    (doseq [filename attachment-paths]
      (when (empty? (fs/find-files unzipped-path (re-pattern filename)))
        (error-and-fail! (str "Attachment referenced in XML was not present in zip: " filename) :error.integration.asianhallinta-missing-attachment)))))

(defn- build-verdict [{:keys [AsianPaatos]}]
  {:id              (mongo/create-id)
   :kuntalupatunnus (:AsianTunnus AsianPaatos)
   :timestamp (core/now)
   :paatokset [{:paatostunnus (:PaatoksenTunnus AsianPaatos)
                :paivamaarat  {:anto (cr/to-timestamp (:PaatoksenPvm AsianPaatos))}
                :poytakirjat  [{:paatoksentekija (:PaatoksenTekija AsianPaatos)
                                :paatospvm       (cr/to-timestamp (:PaatoksenPvm AsianPaatos))
                                :pykala          (:Pykala AsianPaatos)
                                :paatoskoodi     (:PaatosKoodi AsianPaatos)}]}]})

(defn- insert-attachment! [application attachment unzipped-path verdict-id attachment-id]
  (prn attachment)
  (let [filename (fs/base-name (:LinkkiLiitteeseen attachment))
        file     (fs/file (s/join "/" [unzipped-path filename]))
        file-size (.length file)
        orgs      (lupapalvelu.organization/resolve-organizations
                    (:municipality application)
                    (:permitType application))
        eraajo-user {:id "-"
                     :enabled true
                     :lastName "Er\u00e4ajo"
                     :firstName "Lupapiste"
                     :role "authority"
                     :organizations (map :id orgs)}]
    (attachment/attach-file! {:application application
                              :filename filename
                              :size file-size
                              :content file
                              :attachment-id attachment-id
                              :attachment-type {:type-group "muut" :type-id "muu"}
                              :target {:type "verdict" :id attachment-id}
                              :required false
                              :locked true
                              :user eraajo-user
                              :created (core/now)
                              :state :ok})))

(defn process-ah-verdict [path-to-zip ftp-user]
  (try
    (let [unzipped-path (unzip-file path-to-zip)
          xmls (fs/find-files unzipped-path #".*xml$")]
      ; path must contain exactly one xml
      (when-not (= (count xmls) 1)
        (error-and-fail! (str "Expected to find one xml, found " (count xmls)) :error.integration.asianhallinta-wrong-number-of-xmls))

      ; parse XML
      (let [parsed-xml (-> (first xmls) slurp xml/parse reader/strip-xml-namespaces xml/xml->edn)
            attachments (-> (get-in parsed-xml [:AsianPaatos :Liitteet])
                            (reader/ensure-sequential :Liite)
                            :Liite)]
        ; Check that all referenced attachments were included in zip
        (ensure-attachments-present! unzipped-path attachments)

        ; Create verdict
        ; -> fetch application
        (let [application-id (get-in parsed-xml [:AsianPaatos :HakemusTunnus])
              application (domain/get-application-no-access-checking application-id)
              org-scope (org/resolve-organization-scope (:municipality application) (:permitType application))]

          ; -> check ftp-user has right to modify app
          (when-not (= ftp-user (get-in org-scope [:caseManagement :ftpUser]))
            (error-and-fail! (str "FTP user " ftp-user " is not allowed to make changes to application " application-id) :error.integration.asianhallinta.unauthorized))

          ; -> build update clause
          ; -> update-application
          (let [new-verdict   (build-verdict parsed-xml)
                command       (action/application->command application)
                new-verdict   (reduce (fn [verdict attachment]
                                        (let [attachment-id (pandect/sha1 (:LinkkiLiitteeseen attachment))]
                                          (insert-attachment!
                                            application
                                            attachment
                                            unzipped-path
                                            (:id new-verdict)
                                            attachment-id)
                                          (assoc-in verdict [:paatokset 0 :poytakirjat 0 :urlHash] attachment-id))) ; TODO: this is b0rken. Poytakirja should be copied for each attachment. Works only for one attachment
                                      new-verdict
                                      attachments)
                update-clause {$push {:verdicts new-verdict}}] ; TODO: Should update modified? can you use $push then?
            (action/update-application command update-clause)
            (ok)))))
    (catch Exception e
      (if-let [error-key (some-> e ex-data :object :text)]
        (fail error-key)
        (fail :error.unknown)))))