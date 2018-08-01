(ns lupapalvelu.backing-system.asianhallinta.asianhallinta-mapping
  (:require [taoensso.timbre :refer [infof]]
            [lupapalvelu.application :refer [get-operations]]
            [lupapalvelu.document.asianhallinta-canonical :as canonical]
            [lupapalvelu.integrations.messages :as messages]
            [lupapalvelu.integrations.statement-canonical :refer [statement-as-canonical]]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.backing-system.asianhallinta.mapping-common :as ah]
            [lupapalvelu.xml.emit :as emit]
            [lupapalvelu.xml.disk-writer :as writer]
            [lupapalvelu.backing-system.krysp.mapping-common :as common]
            [lupapalvelu.xml.validator :as validator]
            [sade.core :refer [def-]]
            [sade.util :as util]
            [clojure.data.xml :as data-xml]))

(def uusi-asia
  {:tag :UusiAsia
   :ns "ah"
   :attr {:xmlns:ah "http://www.lupapiste.fi/asianhallinta"}
   :child [{:tag :Tyyppi}
           {:tag :Kuvaus}
           {:tag :Kuntanumero}
           {:tag :Hakijat :child ah/hakijat-type}
           {:tag :Maksaja :child ah/maksaja-type}
           {:tag :HakemusTunnus}
           {:tag :VireilletuloPvm}
           {:tag :Liitteet :child [{:tag :Liite :child ah/liite-type}]}
           {:tag :Asiointikieli}
           {:tag :Toimenpiteet :child [{:tag :Toimenpide :child ah/toimenpide-type}]}
           {:tag :Viiteluvat :child [{:tag :Viitelupa :child ah/viitelupa-type}]}
           {:tag :Sijainti :child [{:tag :Sijaintipiste}]}
           {:tag :Kiinteistotunnus}]})

(def uusi-asia-1_2
  (assoc
    uusi-asia
    :child
    (common/update-child-element
      (:child uusi-asia)
      [:Liitteet :Liite]
      {:tag :Liite :child ah/liite-type-1_2})))

(def uusi-asia-1_3
  (-> uusi-asia-1_2
      (update :child
              (fn [tags newtag]
                (apply vector (first tags) newtag (rest tags)))
              {:tag :TyypinTarkenne})
      (update :child
              common/merge-into-coll-after-tag
              :Toimenpiteet
              [{:tag :Lausunnot :child [{:tag :Lausunto :child ah/lausunto-type}]}
               {:tag :Lausuntopyynto :child ah/lausuntopyynto-type}])))

(def taydennys-asiaan
  {:tag :TaydennysAsiaan
   :ns "ah"
   :attr {:xmlns:ah "http://www.lupapiste.fi/asianhallinta"}
   :child [{:tag :HakemusTunnus}
           {:tag :AsianTunnus}
           {:tag :Liitteet :child [{:tag :Liite :child ah/liite-type}]}]})

(def lausunto-vastaus
  {:tag :LausuntoVastaus
   :ns "ah"
   :attr {:xmlns:ah "http://www.lupapiste.fi/asianhallinta"}
   :child [{:tag :HakemusTunnus}
           {:tag :AsianTunnus}
           {:tag :Lausunto :child ah/lausunto-type}
           {:tag :Liitteet :child [{:tag :Liite :child ah/liite-type-1_2}]}]})

(def- ua-version-mapping
  {"1.1" uusi-asia
   "1.2" uusi-asia-1_2
   "1.3" uusi-asia-1_3})

(def- ta-version-mapping
  {"1.1" taydennys-asiaan
   "1.2" taydennys-asiaan
   "1.3" taydennys-asiaan})

(defn- get-mapping [version-mapping version]
  (let [mapping (get-in version-mapping [(name version)])]
    (if mapping
      (assoc-in mapping [:attr :version] (name version))
      (throw (IllegalArgumentException. (str "Unsupported Asianhallinta version: " version))))))

(defn get-uusi-asia-mapping [version]
  (get-mapping ua-version-mapping version))

(defn get-taydennys-asiaan-mapping [version]
  (get-mapping ta-version-mapping version))

(defn- attachments-for-write
  ([attachments]
    (attachments-for-write attachments #(and (not= "statement" (-> % :target :type))
                                             (not= "verdict" (-> % :target :type)))))
  ([attachments pred]
   (for [attachment attachments
         :when (and (:latestVersion attachment)
                    (pred attachment))
         :let [fileId (-> attachment :latestVersion :fileId)]]
     {:fileId fileId
      :filename (writer/get-file-name-on-server fileId (get-in attachment [:latestVersion :filename]))})))

(defn- enrich-attachment-with-operation [attachment operations]
  (update attachment :op (partial map #(util/assoc-when % :name (:name (util/find-by-id (:id %) operations))))))

(defn- enrich-attachments-with-operation-data [attachments operations]
  (mapv #(enrich-attachment-with-operation % operations) attachments))

(defn enrich-application [application]
  (update-in application [:attachments] enrich-attachments-with-operation-data (conj (seq (:secondaryOperations application)) (:primaryOperation application))))

(defn uusi-asia-from-application
  "Construct UusiAsia XML message. Writes XML and attachments to disk (output-dir)"
  [application lang ah-version submitted-application begin-of-link output-dir]
  (let [application (enrich-application application)
        canonical (canonical/application-to-asianhallinta-canonical application lang)
        attachments-canonical (canonical/get-attachments-as-canonical (:attachments application) begin-of-link)
        attachments-with-pdfs (conj attachments-canonical
                                (canonical/get-submitted-application-pdf application begin-of-link)
                                (canonical/get-current-application-pdf application begin-of-link))
        canonical-with-attachments (assoc-in canonical [:UusiAsia :Liitteet :Liite] attachments-with-pdfs)
        mapping (get-uusi-asia-mapping ah-version)
        xml-s (-> (emit/element-to-xml canonical-with-attachments mapping)
                  (data-xml/emit-str))
        attachments (attachments-for-write (:attachments application))]
    (-> (validator/validate-integration-message! xml-s (permit/permit-type application) (str "ah-" ah-version))
        (writer/write-to-disk application attachments output-dir submitted-application lang))))

(defn taydennys-asiaan-from-application
  "Construct AsiaanTaydennys XML message. Writes XML and attachmets to disk (ouput-dir)"
  [application attachments _ ah-version begin-of-link output-dir]
  (let [canonical (canonical/application-to-asianhallinta-taydennys-asiaan-canonical application)
        attachments (enrich-attachments-with-operation-data attachments (get-operations application))
        attachments-canonical (canonical/get-attachments-as-canonical attachments begin-of-link)
        canonical-with-attachments (assoc-in canonical [:TaydennysAsiaan :Liitteet :Liite] attachments-canonical)
        mapping (get-taydennys-asiaan-mapping ah-version)
        xml-s (-> (emit/element-to-xml canonical-with-attachments mapping)
                  (data-xml/emit-str))
        attachments (attachments-for-write attachments)]
    (-> (validator/validate-integration-message! xml-s (permit/permit-type application) (str "ah-" ah-version))
        (writer/write-to-disk application attachments output-dir nil nil "taydennys"))))

(defn- create-statement-request-canonical
  [user application statement lang]
  (-> (canonical/application-to-asianhallinta-canonical application lang "Lausuntopyynt\u00f6")
      (assoc-in [:UusiAsia :TyypinTarkenne] (get-in statement [:external :subtype]))
      (assoc-in [:UusiAsia :Lausuntopyynto] (statement-as-canonical user statement lang))))

(defn statement-request
  "Construct UusiAsia XML with type Lausuntopyynt\u00f6. Writes XML and attachments to disk"
  [{:keys [user created] :as command} application submitted-application statement lang message-config]
  (let [{:keys [version begin-of-link output-dir]} message-config
        message-id (get-in statement [:external :messageId])
        application  (enrich-application application)
        integration-message-data (util/assoc-when
                                   {:id          message-id :direction "out"
                                    :output-dir  (:output-dir message-config) :format "xml"
                                    :partner     (get-in statement [:external :partner])
                                    :messageType "ah-statement-request" :created created
                                    :transferType "sftp"
                                    :application (select-keys application [:id :organization :state])
                                    :target      {:id (:id statement) :type "statement"}
                                    :initator    (select-keys user [:id :username])
                                    :status      "done"}
                                   :action      (:action command))
        canonical    (create-statement-request-canonical user application statement lang)
        attachments-canonical (canonical/get-attachments-as-canonical (:attachments application) begin-of-link #(not= "verdict" (-> % :target :type)))
        attachments-with-pdfs  (conj attachments-canonical
                                    (canonical/get-submitted-application-pdf application begin-of-link)
                                    (canonical/get-current-application-pdf application begin-of-link))
        canonical-with-attachments (assoc-in canonical [:UusiAsia :Liitteet :Liite] attachments-with-pdfs)
        mapping (-> (get-uusi-asia-mapping version)
                    (assoc-in [:attr :messageId] message-id))
        xml-s (-> (emit/element-to-xml canonical-with-attachments mapping)
                  (data-xml/emit-str))
        attachments (attachments-for-write (:attachments application) #(not= "verdict" (-> % :target :type)))
        saved-attachment-ids (-> (validator/validate-integration-message! xml-s (permit/permit-type application) (str "ah-" version))
                                 (writer/write-to-disk application attachments output-dir submitted-application lang "statement_request"))]
    (messages/save (assoc integration-message-data :attachmentsCount (count saved-attachment-ids)))
    (infof "ELY statement-request written, messageId: %s, attachments (excl generated PDFs): %d" message-id (count saved-attachment-ids))
    saved-attachment-ids))
