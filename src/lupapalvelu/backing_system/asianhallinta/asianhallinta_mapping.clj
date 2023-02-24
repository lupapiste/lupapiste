(ns lupapalvelu.backing-system.asianhallinta.asianhallinta-mapping
  (:require [clojure.data.xml :as data-xml]
            [lupapalvelu.application-utils :refer [get-operations]]
            [lupapalvelu.backing-system.asianhallinta.mapping-common :as ah]
            [lupapalvelu.backing-system.krysp.mapping-common :as common]
            [lupapalvelu.document.asianhallinta-canonical :as canonical]
            [lupapalvelu.integrations.messages :as messages]
            [lupapalvelu.integrations.statement-canonical :refer [statement-as-canonical]]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.sftp.context :as sftp-ctx]
            [lupapalvelu.sftp.core :as sftp]
            [lupapalvelu.sftp.util :as sftp-util]
            [lupapalvelu.xml.emit :as emit]
            [lupapalvelu.xml.validator :as validator]
            [sade.core :refer [def-]]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [taoensso.timbre :refer [infof]]))

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
      :filename (sftp-util/get-file-name-on-server fileId (get-in attachment [:latestVersion :filename]))})))

(defn- enrich-attachment-with-operation [attachment operations]
  (update attachment :op (partial map #(util/assoc-when % :name (:name (util/find-by-id (:id %) operations))))))

(defn- enrich-attachments-with-operation-data [attachments operations]
  (mapv #(enrich-attachment-with-operation % operations) attachments))

(defn enrich-application [application]
  (let [operations (conj (seq (:secondaryOperations application)) (:primaryOperation application))]
    (update-in application [:attachments] enrich-attachments-with-operation-data operations)))

(defn- resolve-begin-of-link [organization]
  (str (env/value (cond->> [:fileserver-address]
                    (sftp-ctx/gcs-sftp? organization) (cons :gcs)))
       "/"
       (ss/join-file-path
         sftp-ctx/CASE-MANAGEMENT
         sftp-ctx/CASE-MANAGEMENT-OUT
         "/")))

(defn uusi-asia-from-application
  "Construct UusiAsia XML message. Writes XML and attachments to SFTP."
  [application organization lang ah-version submitted-application]
  (let [begin-of-link              (resolve-begin-of-link organization)
        application                (enrich-application application)
        canonical                  (canonical/application-to-asianhallinta-canonical application lang)
        attachments-canonical      (canonical/get-attachments-as-canonical (:attachments application) begin-of-link)
        attachments-with-pdfs      (conj attachments-canonical
                                         (canonical/get-submitted-application-pdf application begin-of-link)
                                         (canonical/get-current-application-pdf application begin-of-link))
        canonical-with-attachments (assoc-in canonical [:UusiAsia :Liitteet :Liite] attachments-with-pdfs)
        mapping                    (get-uusi-asia-mapping ah-version)
        xml-s                      (-> (emit/element-to-xml canonical-with-attachments mapping)
                                       (data-xml/emit-str)
                                       (validator/validate-integration-message! (permit/permit-type application)
                                                                                (str "ah-" ah-version)))
        attachments                (attachments-for-write (:attachments application))]
    (sftp/write-application organization application
                            {:xml                   xml-s
                             :attachments           attachments
                             :submitted-application submitted-application
                             :lang                  lang
                             :sftp-links?           true})))

(defn taydennys-asiaan-from-application
  "Construct AsiaanTaydennys XML message. Writes XML and attachments to SFTP."
  [application organization attachments _ ah-version]
  (let [begin-of-link              (resolve-begin-of-link organization)
        canonical                  (canonical/application-to-asianhallinta-taydennys-asiaan-canonical application)
        attachments                (enrich-attachments-with-operation-data attachments (get-operations application))
        attachments-canonical      (canonical/get-attachments-as-canonical attachments begin-of-link)
        canonical-with-attachments (assoc-in canonical [:TaydennysAsiaan :Liitteet :Liite] attachments-canonical)
        mapping                    (get-taydennys-asiaan-mapping ah-version)
        xml-s                      (-> (emit/element-to-xml canonical-with-attachments mapping)
                                       (data-xml/emit-str)
                                       (validator/validate-integration-message! (permit/permit-type application)
                                                                                (str "ah-" ah-version)))
        attachments                (attachments-for-write attachments)]
    (sftp/write-application organization application
                            {:xml         xml-s
                             :attachments attachments
                             :file-suffix "taydennys"
                             :sftp-links? true})))

(defn- create-statement-request-canonical
  [user application statement lang]
  (-> (canonical/application-to-asianhallinta-canonical application lang "Lausuntopyynt\u00f6")
      (assoc-in [:UusiAsia :TyypinTarkenne] (get-in statement [:external :subtype]))
      (assoc-in [:UusiAsia :Lausuntopyynto] (statement-as-canonical user statement lang))))

(defn statement-request
  "Construct UusiAsia XML with type Lausuntopyyntö. Writes XML and attachments to SFTP."
  [{:keys [user created organization application] :as command} submitted-application statement lang]
  (let [begin-of-link              (resolve-begin-of-link sftp-ctx/ELY-CONFIG)
        {:keys [version]}          sftp-ctx/ELY-CONFIG
        message-id                 (get-in statement [:external :messageId])
        application                (enrich-application application)
        ctx                        (sftp-ctx/ely-context organization application)
        integration-message-data   (util/assoc-when
                                     {:id           message-id             :direction "out"
                                      :output-dir   (:directory ctx)       :format    "xml"
                                      :partner      (get-in statement [:external :partner])
                                      :messageType  "ah-statement-request" :created   created
                                      :transferType "sftp"
                                      :application  (select-keys application [:id :organization :state])
                                      :target       {:id (:id statement) :type "statement"}
                                      :initiator    (select-keys user [:id :username])
                                      :status       "done"}
                                     :action      (:action command))
        canonical                  (create-statement-request-canonical user application statement lang)
        attachments-canonical      (canonical/get-attachments-as-canonical (:attachments application)
                                                                           begin-of-link
                                                                           #(and (not= "verdict" (-> % :target :type))
                                                                                 (< (-> % :latestVersion :size) 50000000)))
        attachments-with-pdfs      (conj attachments-canonical
                                         (canonical/get-submitted-application-pdf application begin-of-link)
                                         (canonical/get-current-application-pdf application begin-of-link))
        canonical-with-attachments (assoc-in canonical [:UusiAsia :Liitteet :Liite] attachments-with-pdfs)
        mapping                    (-> (get-uusi-asia-mapping version)
                                       (assoc-in [:attr :messageId] message-id))
        xml-s                      (-> (emit/element-to-xml canonical-with-attachments mapping)
                                       (data-xml/emit-str)
                                       (validator/validate-integration-message! (permit/permit-type application)
                                                                                (str "ah-" version)))
        attachments                (attachments-for-write (:attachments application)
                                                          #(not= "verdict" (-> % :target :type)))
        saved-attachment-ids       (sftp/write-application ctx
                                                           {:xml                   xml-s
                                                            :attachments           attachments
                                                            :submitted-application submitted-application
                                                            :lang                  lang
                                                            :file-suffix           "statement_request"
                                                            :sftp-links?           true})]
    (messages/save (assoc integration-message-data :attachmentsCount (count saved-attachment-ids)))
    (infof "ELY statement-request written, messageId: %s, attachments (excl generated PDFs): %d"
           message-id (count saved-attachment-ids))
    saved-attachment-ids))
