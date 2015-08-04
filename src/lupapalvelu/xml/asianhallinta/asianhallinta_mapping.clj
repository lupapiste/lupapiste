(ns lupapalvelu.xml.asianhallinta.asianhallinta_mapping
  (:require [lupapalvelu.document.asianhallinta_canonical :as canonical]
            [lupapalvelu.xml.asianhallinta.mapping_common :as ah]
            [lupapalvelu.xml.emit :as emit]
            [lupapalvelu.xml.disk-writer :as writer]
            [lupapalvelu.application :refer [get-operations]]
            [sade.core :refer [def-]]))

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

(def taydennys-asiaan
  {:tag :TaydennysAsiaan
   :ns "ah"
   :attr {:xmlns:ah "http://www.lupapiste.fi/asianhallinta"}
   :child [{:tag :HakemusTunnus}
           {:tag :AsianTunnus}
           {:tag :Liitteet :child [{:tag :Liite :child ah/liite-type}]}]})

(def- ua-version-mapping
  {"1.1" uusi-asia})

(def- ta-version-mapping
  {"1.1" taydennys-asiaan})

(defn- get-mapping [version-mapping version]
  (let [mapping (get-in version-mapping [(name version)])]
       (if mapping
         (assoc-in mapping [:attr :version] (name version))
         (throw (IllegalArgumentException. (str "Unsupported Asianhallinta version: " version))))))

(defn get-ua-mapping [version]
  (get-mapping ua-version-mapping version))

(defn get-ta-mapping [version]
  (get-mapping ta-version-mapping version))

(defn- attachments-for-write [attachments & [target]]
  (for [attachment attachments
        :when (and (:latestVersion attachment)
                (not= "statement" (-> attachment :target :type))
                (not= "verdict" (-> attachment :target :type))
                (or (nil? target) (= target (:target attachment))))
        :let [fileId (-> attachment :latestVersion :fileId)]]
    {:fileId fileId
     :filename (writer/get-file-name-on-server fileId (get-in attachment [:latestVersion :filename]))}))

(defn- enrich-attachment-with-operation [attachment operations]
  (if-let [op-id (get-in attachment [:op :id])]
    (assoc-in attachment [:op :name] (some
                                       #(when (= op-id (:id %))
                                          (:name %))
                                       operations))
    attachment))

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
        mapping (get-ua-mapping ah-version)

        xml (emit/element-to-xml canonical-with-attachments mapping)
        attachments (attachments-for-write (:attachments application))]
    (writer/write-to-disk application attachments xml (str "ah-" ah-version) output-dir submitted-application lang)))

(defn taydennys-asiaan-from-application
  "Construct AsiaanTaydennys XML message. Writes XML and attachmets to disk (ouput-dir)"
  [application attachments lang ah-version begin-of-link output-dir]
  (let [canonical (canonical/application-to-asianhallinta-taydennys-asiaan-canonical application)
        attachments (enrich-attachments-with-operation-data attachments (get-operations application))
        attachments-canonical (canonical/get-attachments-as-canonical attachments begin-of-link)
        canonical-with-attachments (assoc-in canonical [:TaydennysAsiaan :Liitteet :Liite] attachments-canonical)
        mapping (get-ta-mapping ah-version)
        xml (emit/element-to-xml canonical-with-attachments mapping)
        attachments (attachments-for-write attachments)]
    (writer/write-to-disk application attachments xml (str "ah-" ah-version) output-dir nil nil "taydennys")))
