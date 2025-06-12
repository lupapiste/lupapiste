(ns lupapalvelu.backing-system.asianhallinta.reader
  "Read asianhallinta messages from SFTP"
  (:require [babashka.fs :as fs]
            [sade.core :refer :all]
            [sade.util :as util]
            [sade.xml :as xml]
            [sade.common-reader :as cr]
            [lupapalvelu.backing-system.asianhallinta.response :as ah-response]
            [lupapalvelu.backing-system.asianhallinta.statement :as ah-statement]
            [lupapalvelu.backing-system.asianhallinta.verdict :as ah-verdict]
            [clojure.java.io :as io]))

(defn- unzip-file [path-to-zip target-dir]
  (if-not (and (fs/exists? path-to-zip) (fs/exists? target-dir))
    (error-and-fail! (str "Could not find file " path-to-zip) :error.integration.asianhallinta-file-not-found)
    (fs/unzip path-to-zip target-dir)))

(defn- ensure-attachments-present! [unzipped-path attachments]
  (when (seq attachments)
    (let [attachment-paths (->> attachments
                                (map xml/xml->edn)
                                (map (comp :LinkkiLiitteeseen :Liite))
                                (map fs/file-name))]
      (doseq [filename attachment-paths]
        (when-not (fs/exists? (fs/file unzipped-path filename))
          (error-and-fail!
            (str "Attachment referenced in XML was not present in zip: " filename)
            :error.integration.asianhallinta-missing-attachment))))))

(defmulti handle-asianhallinta-message (fn [_system parsed-xml _ _ _ _] (:tag parsed-xml)))
(defmethod handle-asianhallinta-message :AsianPaatos
  [system parsed-xml unzipped-path ftp-user system-user created]
  (ah-verdict/process-ah-verdict system parsed-xml unzipped-path ftp-user system-user created))
(defmethod handle-asianhallinta-message :AsianTunnusVastaus
  [_system parsed-xml _ ftp-user system-user created]
  (ah-response/asian-tunnus-vastaus-handler parsed-xml ftp-user system-user created))
(defmethod handle-asianhallinta-message :LausuntoVastaus
  [system parsed-xml unzipped-path ftp-user system-user created]
  (ah-statement/statement-response-handler system parsed-xml unzipped-path ftp-user system-user created))

(defn process-message [system path-to-zip ftp-user system-user]
  {:pre [(map? system)]}
  (fs/with-temp-dir [tmp-dir {:prefix "ah"}]
    (try
      (unzip-file path-to-zip tmp-dir)
      (let [xmls    (fs/glob tmp-dir "*xml")
            created (now)]
        ;; path must contain exactly one xml
        (when-not (= (count xmls) 1)
          (error-and-fail!
            (str "Expected to find one xml, found " (count xmls) " for user " ftp-user)
            :error.integration.asianhallinta-wrong-number-of-xmls))

        ;; parse XML
        (let [parsed-xml  (-> (first xmls) fs/file io/input-stream
                              xml/parse cr/strip-xml-namespaces)
              attachments (xml/select parsed-xml [:Liitteet :Liite])]
          ;; Check that all referenced attachments were included in zip
          (ensure-attachments-present! tmp-dir attachments)

          (handle-asianhallinta-message system parsed-xml tmp-dir ftp-user system-user created)))

      (catch Throwable e
        (if-let [error-key (some-> e ex-data :text)]
          ;; If it was 'controlled' exception, return :ok false, else throw it forward
          (fail error-key)
          (throw e))))))
