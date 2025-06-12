(ns lupapalvelu.integrations.document-export
  "Helper functions for exporting generated documents (application PDF, case-file PDF,
  case-file XML) to the backing system via SFTP as part of the KuntaGML message."
  (:require [lupapalvelu.bim.schema :as bim-schema]
            [lupapalvelu.laundry.client :as laundry-client]
            [lupapalvelu.linked-files :as linked-files]
            [lupapalvelu.pdf.libreoffice-template-history :as history]
            [lupapalvelu.pdf.pdf-export :as pdf-export]
            [lupapalvelu.tiedonohjaus :as tos]
            [monger.operators :refer [$inc $set]]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.files :as files]))

(defn has-sendable-attachments
  "Pre-check for send attachments use case. The only requirement is the existence of a latest
  version. No type or target related restrictions. Also checks for linked files (BIM models)
  that have at least one version. When documentIds are provided, those also count as sendable."
  [{{attachments :attachments app-id :id} :application {document-ids :documentIds} :data}]
  (let [has-documents?          (seq document-ids)
        has-regular-attachment? (some :latestVersion attachments)
        has-linked-file?        (when-not (or has-regular-attachment? has-documents?)
                                  (some (comp seq :versions)
                                        (linked-files/find-many-linked-files-with-metadata
                                          {:application-id app-id}
                                          :only-fields [:versions])))]
    (when-not (or has-documents? has-regular-attachment? has-linked-file?)
      (fail :error.no-sendable-attachments))))

(defn model-phase->application-state
  "Derives an applicationState value from the BIM model phase."
  [model-phase]
  (when model-phase
    (if (bim-schema/post-verdict-model-phases (keyword model-phase))
      "verdictGiven"
      "submitted")))

(defn linked-file->pseudo-attachment
  "Converts a linked file (BIM model) to a pseudo-attachment format compatible with
  the attachment transfer system. The :linked-file key marks this as a linked file
  for special handling during file download."
  [linked-file]
  (let [latest-version (linked-files/latest-version linked-file)
        file-id        (get-in linked-file [:target-entity :file-id])
        target-type    (get-in linked-file [:target-entity :target-type])
        parent-file-id (get-in linked-file [:target-entity :parent-file-id])
        linked-meta    (:metadata linked-file)
        model-type     (:model-type linked-meta)
        model-phase    (:model-phase linked-meta)]
    (cond-> {:id            file-id
             :latestVersion {:fileId   (:file-version-id latest-version)
                             :filename (get-in latest-version [:metadata :name])
                             :version  (:version latest-version)
                             :modified (:uploaded-at latest-version)}
             :type          (update-vals (tos/linked-file-target-type->tos-type target-type model-type) name)
             ;; Include metadata for extra-attachment-meta to find confidentiality fields and parent reference
             :metadata      (cond-> (select-keys linked-meta [:julkisuusluokka :salassapitoaika :salassapitoperuste
                                                              :security-period-end :henkilotiedot :suojaustaso
                                                              :turvallisuusluokka :kayttajaryhma :kayttajaryhmakuvaus
                                                              :nakyvyys])
                              parent-file-id (assoc :parent-file-id parent-file-id))
             :linked-file   linked-file}
      ;; Derive applicationState from model-phase so that extra-attachment-meta
      ;; can determine the "rakentamisenaikainen" metadata correctly
      model-phase (assoc :applicationState (model-phase->application-state model-phase)))))

(defn- create-metatieto
  "Builds a single KuntaGML metatietotieto entry. Both `:metatieto` and `:Metatieto`
  keys are required by the canonical format."
  [k v]
  (when v
    {:metatieto {:metatietoNimi k :metatietoArvo v}
     :Metatieto {:metatietoNimi k :metatietoArvo v}}))

(defn- create-app-meta
  "Extracts the given metadata keys from `metadata` map and returns a vector of
  KuntaGML metatietotieto entries. Nil values are filtered out."
  [metadata ks]
  (into [] (keep (fn [k] (create-metatieto (name k) (some-> (get metadata k) name)))) ks))

(defn build-generated-documents
  "Builds a list of generated document descriptors for inclusion in KuntaGML.
  Each descriptor has :id, :canonical (the Liite entry), :filename, and :content-fn.
  Only includes documents whose IDs are in `document-id-set`."
  [{:keys [application]} document-id-set lang]
  (let [app-id           (:id application)
        lang-kw          (keyword lang)
        ts               (now)
        app-metadata     (:metadata application)
        process-metadata (:processMetadata application)
        ;; KuntaGML versionumero uses "major.minor" format.
        ;; Major versions are set to always start from 1 here, only minor version is incremented.
        ;; There is no process that increments major versions for documents.
        app-version      (str "1." (or (:export-version app-metadata) 0))
        process-version  (str "1." (or (:export-version process-metadata) 0))
        app-meta         (create-app-meta app-metadata [:julkisuusluokka :henkilotiedot])
        process-meta     (create-app-meta process-metadata [:julkisuusluokka])]
    (cond-> []
      (document-id-set (str app-id "-application"))
      (conj {:id         (str app-id "-application")
             :filename   (str app-id "-application.pdf")
             :canonical  {:Liite {:kuvaus            "Hakemus"
                                  :linkkiliitteeseen (str app-id "-application.pdf")
                                  :muokkausHetki     (date/xml-datetime ts)
                                  :versionumero      app-version
                                  :tyyppi            "hakemus"
                                  :metatietotieto    app-meta}}
             :content-fn (fn [] (pdf-export/generate-application-pdfa application lang-kw))})

      (document-id-set (str app-id "-case-file"))
      (conj {:id         (str app-id "-case-file")
             :filename   (str app-id "-case-file.pdf")
             :canonical  {:Liite {:kuvaus            "Käsittelypöytäkirja"
                                  :linkkiliitteeseen (str app-id "-case-file.pdf")
                                  :muokkausHetki     (date/xml-datetime ts)
                                  :versionumero      process-version
                                  :tyyppi            "käsittelypöytäkirja"
                                  :metatietotieto    process-meta}}
             :content-fn (fn []
                           (files/with-temp-file libre-file
                             (history/write-history-libre-doc application lang-kw libre-file)
                             (laundry-client/convert-libre-template-to-pdfa-stream libre-file)))})

      (document-id-set (str app-id "-case-file-xml"))
      (conj {:id         (str app-id "-case-file-xml")
             :filename   (str app-id "-case-file.xml")
             :canonical  {:Liite {:kuvaus            "Käsittelypöytäkirja (XML)"
                                  :linkkiliitteeseen (str app-id "-case-file.xml")
                                  :muokkausHetki     (date/xml-datetime ts)
                                  :versionumero      process-version
                                  :tyyppi            "käsittelypöytäkirja"
                                  :metatietotieto    process-meta}}
             :content-fn (fn [] (tos/xml-case-file application lang-kw))}))))

(defn build-document-export-updates
  "Builds MongoDB update maps for document export state tracking.
  Returns a map with $set and $inc operations for sent timestamps and version counters."
  [app-id sent-doc-ids created]
  (let [sent-set      (set sent-doc-ids)
        app-sent?     (sent-set (str app-id "-application"))
        process-sent? (or (sent-set (str app-id "-case-file"))
                          (sent-set (str app-id "-case-file-xml")))]
    (cond-> {}
      app-sent?     (assoc-in [$set "metadata.sent"] created)
      app-sent?     (assoc-in [$inc "metadata.export-version"] 1)
      process-sent? (assoc-in [$set "processMetadata.sent"] created)
      process-sent? (assoc-in [$inc "processMetadata.export-version"] 1))))
