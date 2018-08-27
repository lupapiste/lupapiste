(ns lupapalvelu.backing-system.asianhallinta.reader
  "Read asianhallinta messages from SFTP"
  (:require [taoensso.timbre :refer [error]]
            [me.raynes.fs :as fs]
            [sade.core :refer :all]
            [sade.util :as util]
            [sade.strings :as ss]
            [sade.xml :as xml]
            [sade.common-reader :as cr]
            [lupapalvelu.backing-system.asianhallinta.response :as ah-response]
            [lupapalvelu.backing-system.asianhallinta.statement :as ah-statement]
            [lupapalvelu.backing-system.asianhallinta.verdict :as ah-verdict]))

(defn- unzip-file [path-to-zip target-dir]
  (if-not (and (fs/exists? path-to-zip) (fs/exists? target-dir))
    (error-and-fail! (str "Could not find file " path-to-zip) :error.integration.asianhallinta-file-not-found)
    (util/unzip path-to-zip target-dir)))

(defn- ensure-attachments-present! [unzipped-path attachments]
  (when (seq attachments)
    (let [attachment-paths (->> attachments
                                (map xml/xml->edn)
                                (map (comp :LinkkiLiitteeseen :Liite))
                                (map fs/base-name))]
      (doseq [filename attachment-paths]
        (when (empty? (fs/find-files unzipped-path (ss/escaped-re-pattern filename)))
          (error-and-fail!
            (str "Attachment referenced in XML was not present in zip: " filename)
            :error.integration.asianhallinta-missing-attachment))))))

(defmulti handle-asianhallinta-message (fn [parsed-xml _ _ _ _] (:tag parsed-xml)))
(defmethod handle-asianhallinta-message :AsianPaatos
  [parsed-xml unzipped-path ftp-user system-user created]
  (ah-verdict/process-ah-verdict parsed-xml unzipped-path ftp-user system-user created))
(defmethod handle-asianhallinta-message :AsianTunnusVastaus
  [parsed-xml _ ftp-user system-user created]
  (ah-response/asian-tunnus-vastaus-handler parsed-xml ftp-user system-user created))
(defmethod handle-asianhallinta-message :LausuntoVastaus
  [parsed-xml unzipped-path ftp-user system-user created]
  (ah-statement/statement-response-handler parsed-xml unzipped-path ftp-user system-user created))

(defn process-message [path-to-zip ftp-user system-user]
  (let [tmp-dir (fs/temp-dir (str "ah"))]
    (try
      (let [unzipped-path (unzip-file path-to-zip tmp-dir)
            xmls (fs/find-files unzipped-path #".*xml$")
            created (now)]
        ;; path must contain exactly one xml
        (when-not (= (count xmls) 1)
          (error-and-fail!
            (str "Expected to find one xml, found " (count xmls) " for user " ftp-user)
            :error.integration.asianhallinta-wrong-number-of-xmls))

        ;; parse XML
        (let [parsed-xml (-> (first xmls) slurp xml/parse cr/strip-xml-namespaces)
              attachments (xml/select parsed-xml [:Liitteet :Liite])]
          ;; Check that all referenced attachments were included in zip
          (ensure-attachments-present! unzipped-path attachments)

          (handle-asianhallinta-message parsed-xml unzipped-path ftp-user system-user created)))

      (catch Throwable e
        (if-let [error-key (some-> e ex-data :text)]
          (fail error-key)                                  ; If it was 'controlled' exception, return :ok false
          (throw e)))                                       ; else throw it forward
      (finally
        (fs/delete-dir tmp-dir)))))
