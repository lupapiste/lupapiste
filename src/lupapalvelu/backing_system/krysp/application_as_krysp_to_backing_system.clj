(ns lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system
  "Convert application to KuntaGML and send it to configured backing system"
  (:require [clojure.data.xml :as data-xml]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.backing-system.dmcity.config :as dm-cfg]
            [lupapalvelu.backing-system.dmcity.core :as dmcity]
            [lupapalvelu.backing-system.dmcity.schemas :as dm-sc] ;; Make sure all the mappers are registered
            [lupapalvelu.backing-system.krysp.kiinteistotoimitus-mapping]
            [lupapalvelu.backing-system.krysp.kuntagml-yht-version :as yht]
            [lupapalvelu.backing-system.krysp.maa-aines-mapping]
            [lupapalvelu.backing-system.krysp.maankayton-muutos-mapping]
            [lupapalvelu.backing-system.krysp.poikkeamis-mapping]
            [lupapalvelu.backing-system.krysp.rakennuslupa-mapping :as rl-mapping]
            [lupapalvelu.backing-system.krysp.verdict-mapping]
            [lupapalvelu.backing-system.krysp.vesihuolto-mapping]
            [lupapalvelu.backing-system.krysp.yleiset-alueet-mapping]
            [lupapalvelu.backing-system.krysp.ymparisto-ilmoitukset-mapping]
            [lupapalvelu.backing-system.krysp.ymparistolupa-mapping]
            [lupapalvelu.comment :as comment]
            [lupapalvelu.document.approval :as approval]
            [lupapalvelu.document.document :as doc]
            [lupapalvelu.document.parties-canonical]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.domain-attachment :as domain-att]
            [lupapalvelu.foreman-application-util :as foreman-util]
            [lupapalvelu.link-permit :as link-permit]
            [lupapalvelu.logging :refer [error info]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.pate.verdict-interface :as vif]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.sftp.context :as sftp-ctx]
            [lupapalvelu.sftp.core :as sftp]
            [lupapalvelu.states :as states]
            [lupapalvelu.system.util-components :as components]
            [lupapalvelu.tiedonohjaus :refer [tos-function-with-name]]
            [lupapalvelu.user :as usr]
            [lupapalvelu.xml.validator :as validator]
            [net.cgrand.enlive-html :as enlive]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.util :as util]
            [sade.xml :as xml])
  (:import [java.io InputStream]))

(defn- get-begin-of-link [system permit-type org]
  {:pre  [(map? system) permit-type]
   :post [%]}
  (let [host (components/system->host system)]
    (cond
      ;; For now, only dmCity supports HTTP integration, so the REST API link can be deduced implicitly
      (dm-cfg/target-enabled? org permit-type)
      (str host "/rest/")

      (:use-attachment-links-integration org)
      (str host "/api/raw/")

      :else
      (str (env/value (cond->> [:fileserver-address]
                        (sftp-ctx/gcs-sftp? org) (cons :gcs)))
           (permit/get-sftp-directory permit-type) "/"))))

(defn- resolve-krysp-version [organization application]
  {:pre [(map? organization) (map? application)]}
  (let [permit-type (yht/effective-permit-type-for-krysp application)]
    (if-let [krysp-version (yht/application-krysp-version organization application)]
      (do
        (when-not (yht/valid-krysp-version-string? krysp-version)
          (error (str \' krysp-version "' does not look like a KRYSP version"))
          (fail! :error.integration.krysp-version-wrong-form))
        krysp-version)
      (do
        (error (str "KRYSP version not found for organization " (:id organization) ", permit-type " permit-type))
        (fail! :error.integration.krysp-version-missing)))))

(defn- enrich-tos-function-name [{tos-function :tosFunction org-id :organization :as application}]
  (assoc application :tosFunctionName (:name (tos-function-with-name tos-function org-id))))

(defn post-process-app-for-krysp
  "Prepares `application` for KuntaGML. `organization` can be map or `delay`."
  [application organization]
  (let [organization (force organization)]
    (-> application
        (domain/enrich-application-handlers organization)
        (domain/enrich-application-tags organization)
        enrich-tos-function-name
        meta-fields/enrich-with-link-permit-data
        link-permit/update-backend-ids-in-link-permit-data
        domain-att/remove-attachment-map)))

(defn- remove-unsupported-attachments [application]
  (update application :attachments #(filter attachment/transmittable-to-krysp? %)))

(defn- filter-attachments-by-state [current-state {attachments :attachments :as application}]
  (if (states/pre-verdict-states (keyword current-state))
    (->> (remove (comp states/post-verdict-states keyword :applicationState) attachments)
         (assoc application :attachments))
    application))

(defn- remove-non-approved-designers [application]
  (update application :documents #(remove (every-pred doc/designer-document?
                                                      (complement approval/approved?)) %)))

(defn- approved-before-verdict? [application document]
  (not (doc/approved-after-verdict? document application)))

(defn- remove-pre-verdict-designers [application]
  (update application :documents #(remove (every-pred doc/designer-document?
                                                      (partial approved-before-verdict? application)) %)))

(defn- remove-disabled-documents [application]
  (update application :documents (fn [docs] (remove :disabled docs))))

(defn- filter-attachments-for-verdict
  "Only verdict attachment is kept for all verdicts.
  On the other hand foreman verdicts may also contain other attachments after filtering."
  [{:keys [state] :as application} verdict-attachment]
  (if (foreman-util/foreman-app? application)
    (->> application
         remove-unsupported-attachments
         (filter-attachments-by-state state))
    (assoc application :attachments [verdict-attachment])))

(defn save-application-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that were sent."
  [{:keys [application organization system] :as command} lang submitted-application & {:keys [current-state]}]
  {:pre [(map? application)
         lang
         (or (map? organization)
             (delay? organization))
         (map? system)]}
  (let [organization  (force organization)
        permit-type   (permit/permit-type application)
        dmcity?       (dm-cfg/target-enabled? organization permit-type)
        _             (when-not (or dmcity?
                                    (and (map? submitted-application)
                                         (= (:id application) (:id submitted-application))))
                        (fail! :error.submitted-application))
        krysp-version (resolve-krysp-version organization application)
        begin-of-link (get-begin-of-link system permit-type organization)
        filtered-app  (->> application
                           remove-unsupported-attachments
                           (filter-attachments-by-state current-state)
                           remove-non-approved-designers)
        submitted-app (some->> submitted-application
                               remove-unsupported-attachments
                               (filter-attachments-by-state current-state))
        output-fn     (if dmcity?
                        (fn [xml attachments]
                          (dmcity/send-xml command :application xml)
                          (->> attachments (map :fileId) (remove nil?)))
                        #(sftp/write-application command
                                                 {:xml                   %1
                                                  :attachments           %2
                                                  :submitted-application submitted-app
                                                  :lang                  lang}))

        mapping-result (permit/application-krysp-mapper filtered-app organization lang krysp-version begin-of-link)]
    (if-some [{:keys [xml attachments]} mapping-result]
      (-> (data-xml/emit-str xml)
          (validator/validate-integration-message! permit-type krysp-version)
          (output-fn attachments))
      (fail! :error.unknown))))

(defn save-parties-as-krysp
  "Send application's parties (currently only designers) to municipality backend. Returns sent party document ids."
  [{:keys [application organization] :as command} lang]
  (let [organization  (force organization)
        permit-type   (permit/permit-type application)
        krysp-version (resolve-krysp-version organization application)
        filtered-app  (-> application
                          (dissoc :attachments)            ; attachments not needed
                          remove-non-approved-designers
                          remove-pre-verdict-designers
                          remove-disabled-documents)
        output-fn     (if (dm-cfg/target-enabled? organization permit-type)
                        (fn [xml _] (dmcity/send-xml command :parties xml))
                        #(sftp/write-application command
                                                 {:xml         %1
                                                  :file-suffix %2}))
        mapped-data   (permit/parties-krysp-mapper filtered-app :suunnittelija lang krysp-version)]
    (when-not (map? mapped-data)
      (fail! :error.unknown))
    (run!
      (fn [[id xml]]
        (-> (data-xml/emit-str xml)
            (validator/validate-integration-message! permit-type krysp-version)
            (output-fn id)))
      mapped-data)
    (keys mapped-data)))

(defn save-building-extinction-as-krysp
  "Send extinction date for an operation to municipality backend."
  [{:keys [application organization] :as command} lang operation-id]
  (let [organization (force organization)
        permit-type  (permit/permit-type application)]
    (when (org/krysp-write-integration? organization permit-type)
      (let [krysp-version  (resolve-krysp-version organization application)
            output-fn      (if (dm-cfg/target-enabled? organization permit-type)
                             (fn [xml] (dmcity/send-xml command :application xml
                                                        {:type-suffix :building-extinction}))
                             (fn [xml] (sftp/write-application command
                                                               {:xml         xml
                                                                :file-suffix (str "building-extinction-" operation-id)})))
            mapping-result (rl-mapping/building-extinction-as-krysp application lang krysp-version)]

        (when-not (and (map? mapping-result) (contains? mapping-result :xml))
          (fail! :error.unknown))
        (-> (data-xml/emit-str (:xml mapping-result))
            (validator/validate-integration-message! permit-type krysp-version)
            output-fn)
        ;; The tradition has been to return array of sent attachment ids. Those are not
        ;; sent here, so returning an empty array.
        []))))


(defn save-review-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that
  were sent. Can be nil if nothing is sent."
  [{:keys [application organization user system] :as command} task lang]
  (let [organization (force organization)
        permit-type  (permit/permit-type application)]
    (when (org/krysp-write-integration? organization permit-type)
      (let [krysp-version (resolve-krysp-version organization application)
            begin-of-link (get-begin-of-link system permit-type organization)
            filtered-app  (remove-unsupported-attachments application)
            dmcity?       (dm-cfg/target-enabled? organization permit-type)]
        (when-let [output-fn (cond
                               (and dmcity? (dm-sc/review-support? permit-type))
                               (fn [xml attachments]
                                 (dmcity/send-xml command :review xml)
                                 (->> attachments (map :fileId) (remove nil?) seq))

                               ;; Dmcity does not support reviews for all permit
                               ;; types (e.g., YA).
                               dmcity? nil

                               :else
                               #(sftp/write-application command
                                                        {:xml         %1
                                                         :attachments %2
                                                         :file-suffix "review"}))]
          (let [mapping-result (permit/review-krysp-mapper filtered-app organization task user
                                                           lang krysp-version begin-of-link)]
            (if-some [{:keys [xml attachments]} mapping-result]
              (-> (data-xml/emit-str xml)
                  (validator/validate-integration-message! permit-type krysp-version)
                  (output-fn attachments))
              (fail! :error.unknown))))))))

(defn save-aloitusilmoitus-as-krysp
  "Sends aloitusilmoitus to municipality backend. Returns a sequence of
  attachment file IDs that were sent. Note: only SFTP supported for
  now."
  [{:keys [application organization system] :as command} notice-form]
  (let [organization (force organization)
        permit-type  (permit/permit-type application)]
    (when (and (org/krysp-write-integration? organization permit-type)
               (not (dm-cfg/target-enabled? organization permit-type)))
      (let [krysp-version  (resolve-krysp-version organization application)
            begin-of-link  (get-begin-of-link system permit-type organization)
            output-fn      #(sftp/write-application command
                                                    {:xml         %1
                                                     :attachments %2
                                                     :file-suffix "aloitusilmoitus"})
            mapping-result (rl-mapping/save-aloitusilmoitus-as-krysp (update-in command [:application]
                                                                                remove-unsupported-attachments)
                                                                     notice-form
                                                                     krysp-version begin-of-link)]
        (if-some [{:keys [xml attachments]} mapping-result]
          (-> (data-xml/emit-str xml)
              (validator/validate-integration-message! permit-type krysp-version)
              (output-fn attachments))
          (fail! :error.unknown))))))

(defn save-rh-tietojen-muutos-as-krysp
  "Sends RH-tietojen muutos KuntaGML message to municipality backend."
  [{:keys [application organization] :as command}]
  (let [organization (force organization)
        permit-type (permit/permit-type application)]
    (when (org/krysp-write-integration? organization permit-type)
      (let [krysp-version  (resolve-krysp-version organization application)
            output-fn      (if (dm-cfg/target-enabled? organization permit-type)
                             (fn [xml]
                               (dmcity/send-xml command :application xml
                                                {:type-suffix "RH-tietojen muutos"}))
                             #(sftp/write-application command
                                                      {:xml         %
                                                       :file-suffix "RH"}))
            mapping-result (rl-mapping/rh-tietojen-muutos-kuntagml command krysp-version)]
        (if-some [{:keys [xml]} mapping-result]
          (-> (data-xml/emit-str xml)
              (validator/validate-integration-message! permit-type krysp-version)
              output-fn)
          (fail! :error.unknown))))))

(defn save-attachments-as-kuntagml
  "Sends attachments to municipality backend. Returns a sequence of attachment file IDs that were sent."
  ([system user organization application attachments lang]
   (save-attachments-as-kuntagml system user organization application attachments lang {}))
  ([system user organization application attachments lang {:keys [extra-canonical-attachments]}]
   {:pre [(map? system)]}
   (let [application    (assoc application :attachments attachments) ; HACK: Callees expect to get `attachments` like this.
        permit-type    (permit/permit-type application)
        krysp-version  (resolve-krysp-version organization application)
        begin-of-link  (get-begin-of-link system permit-type organization)

        output-fn      (if (dm-cfg/target-enabled? organization permit-type)
                         (fn [xml attachments]
                           (dmcity/send-xml system organization application user :attachments xml)
                           (->> attachments (map :fileId) (remove nil?)))
                         #(sftp/write-application  organization
                                                   application
                                                   {:xml         %1
                                                    :attachments %2}))
        mapping-result (rl-mapping/save-attachments-as-kuntagml application organization
                                                                lang krysp-version begin-of-link
                                                                (constantly true)
                                                                {:extra-canonical-attachments
                                                                 extra-canonical-attachments})]
    (if-some [{:keys [xml attachments]} mapping-result]
      (-> (data-xml/emit-str xml)
          (validator/validate-integration-message! permit-type krysp-version)
          (output-fn attachments))
      (fail! :error.unknown)))))

(defn save-attachments-and-documents-as-kuntagml
  "Sends attachments and generated documents to municipality backend in a single KuntaGML message.
  `generated-documents` is a sequence of maps with:
    :canonical - The {:Liite {...}} canonical entry for the KuntaGML liitetieto
    :filename  - Filename to write to SFTP
    :content-fn - Zero-arg function that returns the file content (InputStream, String, or File)
  Returns a map with:
    :sent-file-ids      - sequence of attachment file IDs that were sent
    :sent-document-ids  - sequence of document IDs that were successfully written to SFTP
    :failed-document-ids - sequence of document IDs that failed to write"
  [system user organization application attachments lang generated-documents]
  {:pre [(map? system)]}
  (let [application    (assoc application :attachments attachments)
        permit-type    (permit/permit-type application)
        krysp-version  (resolve-krysp-version organization application)
        begin-of-link  (get-begin-of-link system permit-type organization)
        extra-canonical (mapv (fn [{:keys [canonical]}]
                               (update-in canonical [:Liite :linkkiliitteeseen]
                                          #(str begin-of-link %)))
                             generated-documents)
        mapping-result (rl-mapping/save-attachments-as-kuntagml application organization
                                                                lang krysp-version begin-of-link
                                                                (constantly true)
                                                                {:extra-canonical-attachments
                                                                 extra-canonical})]
    (if-some [{:keys [xml attachments]} mapping-result]
      (let [ctx (sftp-ctx/->ApplicationSftp organization application)
            sent-file-ids (sftp/write-application ctx
                                                  {:xml         (-> (data-xml/emit-str xml)
                                                                    (validator/validate-integration-message!
                                                                      permit-type krysp-version))
                                                   :attachments attachments})
            doc-results (reduce (fn [acc {:keys [id filename content-fn]}]
                                  (try
                                    (sftp-ctx/write-file ctx filename (content-fn))
                                    (update acc :sent conj id)
                                    (catch Exception e
                                      (error e "Failed to write document" id "to SFTP")
                                      (update acc :failed conj id))))
                                {:sent [] :failed []}
                                generated-documents)]
        {:sent-file-ids      sent-file-ids
         :sent-document-ids  (:sent doc-results)
         :failed-document-ids (:failed doc-results)})
      (fail! :error.unknown))))

(defn- main-app-to-foreman-v3-app
  "Changes the main application so that it will generate a foreman KuntaGML message.
   Application id is the document's id."
  [app foreman-doc]
  (-> app
      (assoc :original-application app
             :id (:id foreman-doc)
             :permitType "TJ-V3"
             :permitSubtype (if (= (:type foreman-doc) "application")
                              "tyonjohtaja-hakemus"
                              "tyonjohtaja-ilmoitus")
             :changeLog (:changeLog foreman-doc))
      (assoc-in [:primaryOperation :name] "tyonjohtajan-nimeaminen-v3")
      ;; Remove designers and other foremen.
      (update :documents (partial remove (fn [{tj? :tj-document doc-id :id
                                               :as document}]
                                           (or (and tj? (not= doc-id (:id foreman-doc)))
                                               (doc/designer-document? document)))))
      ;; Keep attachments that reference the document (pätevyystodistukset)
      (update :attachments (fn [attachments]
                             (filter
                               (fn [attachment]
                                 (and (= "suunnittelija-patevyystodistus" (:type (:target attachment)))
                                      (= (:id foreman-doc) (:id (:target attachment)))))
                               attachments)))))

(defn verdict-as-kuntagml
  [{:keys [application organization system] :as command} verdict verdict-attachment]
  {:pre [(map? system)]}
  (let [organization   (force organization)
        permit-type    (permit/permit-type application)
        krysp-version  (resolve-krysp-version organization application)
        begin-of-link  (get-begin-of-link system permit-type organization)
        foreman-doc    (some->> verdict :document-id (domain/get-document-by-id application))
        filtered-app   (cond-> application
                         ;; likely modifying attachments -> remove stale cache just in case
                         true              domain-att/remove-attachment-map
                         foreman-doc       (main-app-to-foreman-v3-app foreman-doc)
                         foreman-doc       (update :attachments concat [verdict-attachment])
                         ;; previous line already handled attachments for foreman-v3
                         (not foreman-doc) (filter-attachments-for-verdict verdict-attachment)
                         true              remove-non-approved-designers
                         true              (assoc :tosFunctionName (:name (tos-function-with-name (:tosFunction application)
                                                                                                  (:id organization)))))
        output-fn      (if (dm-cfg/target-enabled? organization permit-type)
                         (fn [xml attachments]
                           (if foreman-doc
                             (info "Not sending foreman XML to dmCity")
                             (do (dmcity/send-xml command :application xml {:type-suffix :verdict})
                                 (->> attachments (map :fileId) (remove nil?)))))
                         (fn [xml attachments]
                           (sftp/write-application command
                                                   (util/assoc-when {:xml         xml
                                                                     :attachments (filter #(not (nil? (:fileId %))) attachments)
                                                                     :file-suffix "verdict"}
                                                                    :foreman-doc-id (some-> foreman-doc :id)))))
        mapping-result (permit/verdict-krysp-mapper filtered-app organization verdict
                                                    (or (get-in verdict [:data :language]) "fi")
                                                    krysp-version
                                                    begin-of-link)]
    (if-some [{:keys [xml attachments]} mapping-result]
      (-> (data-xml/emit-str xml)
          (validator/validate-integration-message! permit-type krysp-version)
          (output-fn attachments))
      (fail! :error.unknown))))

(defn- export-comments-pdf-to-sftp [system sftp-context application]
  {:pre [(map? system)]}
  (let [lang                  (attachment/resolve-lang-for-comments-attachment application)
        filename              (comment/get-comments-filename lang application)
        ^InputStream contents (:pdf-file-stream (comment/get-comments-as-pdf system lang application))]
    (sftp-ctx/write-file sftp-context filename contents)))

(defn full-application-export-as-kuntagml
  "WIP Outputs full application as KuntaGML.
  Currently only basic application details, attachments and comments (SFTP only)
  In the future if needed:
  - verdicts (note the `vc/kuntagml-verdict?` fn for this)
  - reviews

  See also `full-batchrun-kuntagml` for a similar, but different approach."
  [{:keys [application organization lang system] :or {lang "fi"} :as command}]
  {:pre [(map? system)]}
  (let [organization  (force organization)
        permit-type   (permit/permit-type application)
        krysp-version (resolve-krysp-version organization application)
        begin-of-link (get-begin-of-link system permit-type organization)
        filtered-app  (-> application
                          remove-non-approved-designers
                          (assoc :tosFunctionName (:name (tos-function-with-name (:tosFunction application)
                                                                                 (:id organization)))))
        output-fn     (if (dm-cfg/target-enabled? organization permit-type)
                        (fn [xml attachments]
                          (dmcity/send-xml command :application xml)
                          (->> attachments (map :fileId) (remove nil?)))
                        #(let [ctx (sftp-ctx/default-context organization application)]
                           (export-comments-pdf-to-sftp system ctx application)
                           (sftp/write-application organization
                                                   application
                                                   {:xml         %1
                                                    :attachments %2
                                                    :file-suffix "export"})))
        mapped-app    (permit/application-krysp-mapper filtered-app organization lang krysp-version begin-of-link)]
    (if-some [{:keys [xml attachments]} mapped-app]
      (-> (data-xml/emit-str xml)
          (validator/validate-integration-message! permit-type krysp-version)
          (output-fn attachments))
      (fail! :error.unknown))))

(defn- foreman-app-to-foreman-termination-app
  "Changes a foreman application so it will generate a termination KuntaGML message.
   Fakes an application id by attaching a running total "
  [confirmation? termination-ts foreman-app]
  (-> foreman-app
      ;; Only include termination verdicts
      (update :attachments (fn [attachments]
                             (->> attachments
                                  (filter (comp #{"foreman-termination-verdict"} :type :source)))))
      (assoc :id (format "%s-T00" (:id foreman-app)))
      ;; Attaches a "running" total to separate from actual apps;
      ;; TODO: Make it increase if it becomes relevant
      ;; (i.e. able to terminate foreman application more than once)
      (assoc :permitSubtype (if confirmation? "tyonjohtaja-hakemus" "tyonjohtaja-ilmoitus"))
      (assoc-in [:primaryOperation :name] (if confirmation? "tyonjohtajan-vastuiden-paattaminen"
                                                            "tyonjohtajan-irtisanominen"))
      (update-in [:foremanTermination :ended] (fnil identity termination-ts))
      ;; Record _when_ the termination was confirmed
      (assoc-in [:foremanTermination :termination-ts] termination-ts)))

(defn- foreman-app-to-foreman-v3-termination-app
  "Changes the application so that it will generate a foreman termination KuntaGML message."
  [confirmation? termination-ts foreman-doc app]
  (-> app
      ;; Only include termination verdicts
      (update :attachments (fn [attachments]
                             (->> attachments
                                  (filter (comp #{"foreman-termination-verdict"} :type :source))
                                  (filter (comp #(= % (:id foreman-doc)) :app-or-doc-id :source))
                                  (into []))))
      (assoc :original-application app
             :id (format "%s-TJ" (:id foreman-doc))
             :permitType "TJ-V3"
             ;; this needs to be given in order to get the state of the correct document later
             ;; (since id in this case doesn't match the document id)
             :tj-doc-id (:id foreman-doc)
             :permitSubtype (if (= (:type foreman-doc) "application")
                              "tyonjohtaja-hakemus"
                              "tyonjohtaja-ilmoitus")
             ;; this is used as saapumisPvm in LupaTunnus
             :submitted (:created foreman-doc)
             :changeLog (:changeLog foreman-doc)
             :foremanTermination (:foremanTermination foreman-doc))
      (assoc-in [:primaryOperation :name] (if confirmation? "tyonjohtajan-vastuiden-paattaminen-v3"
                                                            "tyonjohtajan-irtisanominen-v3"))
      (assoc-in [:foremanTermination :ended] (get-in foreman-doc [:foremanTermination :ended] termination-ts))
      ;; Record _when_ the termination was confirmed
      (assoc-in [:foremanTermination :termination-ts] termination-ts)))

(defn foreman-as-kuntagml
  "Sends an altered copy of the main application informing the backend of the foreman"
  [{:keys [organization state system]} app lang foreman-doc]
  {:pre [lang (map? app) (map? system) (or (map? organization) (delay? organization))]}
  (let [organization   (force organization)
        permit-type    (permit/permit-type app)
        krysp-version  (resolve-krysp-version organization app)
        begin-of-link  (get-begin-of-link system permit-type organization)
        filtered-app   (-> (main-app-to-foreman-v3-app app foreman-doc)
                           meta-fields/enrich-with-link-permit-data)
        submitted-app  (some->> (mongo/by-id :submitted-applications (:id app))
                                remove-unsupported-attachments
                                (filter-attachments-by-state state))
        output-fn      (if (dm-cfg/target-enabled? organization permit-type)
                         (fn [_ _]
                           (info "Not sending foreman XML to dmCity"))
                         (fn [xml _]
                           (sftp/write-application organization
                                                   app
                                                   {:xml                   xml
                                                    ;; TODO: pätevyystodistus?
                                                    :attachments           [] ;; leave out attachments for foreman-v3
                                                    :submitted-application submitted-app
                                                    :lang                  lang
                                                    :foreman-doc-id        (:id foreman-doc)})))
        mapping-result (permit/application-krysp-mapper filtered-app organization lang krysp-version begin-of-link)]
    (if-some [{:keys [xml attachments]} mapping-result]
      (-> (data-xml/emit-str xml)
          (validator/validate-integration-message! permit-type krysp-version)
          (output-fn attachments))
      (fail! :error.unknown))))

(defn- foreman-v3-termination-as-kuntagml
  "Sends an altered copy of the application informing the backend of the foreman's termination"
  [{:keys [organization created system]} app confirmation? lang foreman-doc]
  (let [organization   (force organization)
        permit-type    (permit/permit-type app)
        krysp-version  (resolve-krysp-version organization app)
        begin-of-link  (get-begin-of-link system permit-type organization)
        filtered-app   (->> (foreman-app-to-foreman-v3-termination-app confirmation? created foreman-doc app)
                            meta-fields/enrich-with-link-permit-data)
        output-fn      (if (dm-cfg/target-enabled? organization permit-type)
                         (fn [_ _]
                           (info "Not sending foreman XML to dmCity"))
                         (fn [xml _]
                           (sftp/write-application organization
                                                   app
                                                   {:xml            xml
                                                    :attachments    []
                                                    :lang           lang
                                                    :foreman-doc-id (:id foreman-doc)})))
        mapping-result (permit/application-krysp-mapper filtered-app organization lang krysp-version begin-of-link)]
    (if-some [{:keys [xml attachments]} mapping-result]
      (-> (data-xml/emit-str xml)
          (validator/validate-integration-message! permit-type krysp-version)
          (output-fn attachments))
      (fail! :error.unknown))))

(defn foreman-termination-as-kuntagml
  "Sends an altered copy of the foreman application informing the backend of the foreman's termination"
  [{:keys [organization user state created system] :as command} foreman-app confirmation? lang foreman-doc]
  {:pre [lang (map? foreman-app) (map? system) (or (map? organization) (delay? organization))]}
  (if foreman-doc
    (foreman-v3-termination-as-kuntagml command foreman-app confirmation? lang foreman-doc)
    (let [organization   (force organization)
          permit-type    (permit/permit-type foreman-app)
          krysp-version  (resolve-krysp-version organization foreman-app)
          begin-of-link  (get-begin-of-link system permit-type organization)
          filtered-app   (->> foreman-app
                              meta-fields/enrich-with-link-permit-data
                              (foreman-app-to-foreman-termination-app confirmation? created))
          submitted-app  (->> (mongo/by-id :submitted-applications (:id foreman-app))
                              remove-unsupported-attachments
                              (filter-attachments-by-state state))
          output-fn      (if (dm-cfg/target-enabled? organization permit-type)
                           (fn [xml attachments]
                             (dmcity/send-xml organization foreman-app user :application xml
                                              {:type-suffix "foreman termination"})
                             (->> attachments (map :fileId) (remove nil?)))
                           #(sftp/write-application organization
                                                    foreman-app
                                                    {:xml                   %1
                                                     :attachments           %2
                                                     :submitted-application submitted-app
                                                     :lang                  lang}))
          mapping-result (permit/application-krysp-mapper filtered-app organization lang krysp-version begin-of-link)]
      (if-some [{:keys [xml attachments]} mapping-result]
        (-> (data-xml/emit-str xml)
            (validator/validate-integration-message! permit-type krysp-version)
            (output-fn attachments))
        (fail! :error.unknown))))) ;;TODO: send verdict when `confirmation?` is true? Spec implies they are optional

(defn batchrun-kuntagml
  "KuntaGML for the given application.

  Options (the last parameter)

  `verdict`: Pate or backing system verdict. If given, verdict KuntaGML is generated.

  `review`: Finished (done) review. If given, review KuntaGML is generated.

  `validate?`: If false, the generated XML is not validated, this is useful when the data
  is known to be non-valid KuntaGML (in the test environment, for example). Default is
  true

  `user`: User for review mapping. Defaults to batchrun user.

  `lang`: Language for generated data. Defaults to fi.
  "
  ([application organization system {:keys [verdict review validate? lang user]}]
   {:pre [(not (and verdict review)) (map? system)]}
   (let [lang               (or lang "fi")
         user               (or user (usr/batchrun-user [(:id organization)]))
         validate?          (if (nil? validate?) true validate?)
         permit-type        (permit/permit-type application)
         krysp-version      (resolve-krysp-version organization application)
         begin-of-link      (get-begin-of-link system permit-type organization)
         verdict-attachment (domain-att/get-attachment-by-id-slow application (vc/verdict-attachment-id verdict))
         filtered-app       (-> application
                                (remove-non-approved-designers)
                                (post-process-app-for-krysp organization)
                                (assoc :tosFunctionName (:name (tos-function-with-name (:tosFunction application)
                                                                                       (:id organization))))
                                (cond->
                                    verdict (filter-attachments-for-verdict verdict-attachment)
                                    review remove-unsupported-attachments))
         mapping-result     (cond
                              verdict
                              (permit/verdict-krysp-mapper filtered-app organization verdict
                                                           (or (get-in verdict [:data :language]) lang)
                                                           krysp-version
                                                           begin-of-link)
                              review
                              (permit/review-krysp-mapper filtered-app organization review user lang krysp-version begin-of-link)
                              :else
                              (permit/application-krysp-mapper filtered-app organization "fi" krysp-version begin-of-link))]
     (if-some [{:keys [xml]} mapping-result]
       (cond-> (data-xml/emit-str xml)
         validate? (validator/validate-integration-message! permit-type krysp-version))
       (fail! :error.no-xml))))
  ([application organization system]
   (batchrun-kuntagml application organization system {})))

(defn sent-review?
  "True if task is a review and marked sent."
  [{:keys [schema-info state]}]
  (and (util/=as-kw (:name schema-info) :task-katselmus)
       (util/=as-kw state :sent)))

(defn- review-attachment
  [review-id attachments]
  (or (util/find-first #(some-> % :source :id (= review-id)) attachments)
      (util/find-first (fn [{{:keys [type-group type-id]} :type
                             {target-id :id}              :target}]
                         (and (= target-id review-id)
                              (= type-group "katselmukset_ja_tarkastukset")
                              (#{"katselmuksen_tai_tarkastuksen_poytakirja"
                                 "aloituskokouksen_poytakirja"} type-id)))
                       attachments)))

(defn- data-pairs->xml [application pairs mapper-fn tag]
  (when tag
    (some->> pairs
             (map (fn [[data attachment]]
                    (-> (cond-> application
                          attachment (assoc :attachments [attachment]))
                        (mapper-fn data)
                        :xml
                        data-xml/emit-str
                        (xml/parse-string "utf8")
                        (xml/select1 [tag]))))
             seq)))

(defn- resolve-tag [permit-type usecase]
  (case [(keyword permit-type) usecase]
    [:R :usecase]    :rakval:kayttotapaus
    [:R :verdict]    :rakval:paatostieto
    [:R :review]     :rakval:katselmustieto
    [:R :statement]  :rakval:lausuntotieto
    [:R :operation]  :rakval:toimenpidetieto

    [:P :usecase]    :ppst:kayttotapaus
    [:P :verdict]    :ppst:paatostieto
    [:P :review]     nil
    [:P :statement]  :ppst:lausuntotieto
    [:P :operation]  :ppst:toimenpidetieto

    [:KT :usecase]   :kiito:kayttotapaus
    [:KT :verdict]   :kiito:paatostieto
    [:KT :review]    nil
    [:KT :statement] nil
    [:KT :operation] nil

    [:MM :usecase]   nil
    [:MM :verdict]   :mkmu:paatostieto
    [:MM :review]    nil
    [:MM :statement] nil
    [:MM :operation] nil))

(defn full-batchrun-kuntagml
  "Returns KuntaGML that contains application, verdicts, attachments and reviews. No
  side-effects, does not store or send anything. Throws if the resulting KuntaGML does not
  pass validation.

  The resulting KuntaGML references _every non-empty attachment_ (no omissions by
  type). The actual link type depends on the organization configuration.

  KuntaGML includes _every party_ (including non-approved designers).

  However, if `verdict-id` option is given only that verdict is included, no reviews and
  regularly filtered application data (similary to regular application KuntaGML).

  The resulting XML is validated, but this can be skipped by setting `validate?` option to
  false."
  ([organization
    system
    {:keys [attachments tasks] permit-type :permitType :as application}
    user
    {:keys [verdict-id validate?]}]
   {:pre [(map? system)]}
   (let [attachments     (filter :latestVersion attachments )
         application     (dissoc application :attachments)
         krysp-version   (resolve-krysp-version organization application)
         begin-of-link   (get-begin-of-link system permit-type organization)
         ;; List of verdict, attachment pairs
         verdicts        (->> (cond->> (vif/published-verdicts {:application application})
                                verdict-id (util/filter-by-key :id verdict-id))
                              (filter vc/kuntagml-verdict?)
                              (map #(list % (util/find-by-id (vc/verdict-attachment-id %)
                                                             attachments)))
                              seq)
         ;; List of review, attachment pairs
         reviews         (seq (keep #(when (sent-review? %)
                                       [% (review-attachment (:id %) attachments)])
                                    tasks))
         vr-att-ids      (->> (concat verdicts reviews)
                              (keep (comp :id second))
                              set)
         verdict-work    (fn [application]
                           (cond-> application
                             verdict-id (-> remove-non-approved-designers
                                            (post-process-app-for-krysp organization))))
         application-xml (-> application
                             (update :attachments #(remove (comp vr-att-ids :id) %))
                             verdict-work
                             (util/assoc-when  :tosFunctionName
                                               (:name (tos-function-with-name (:tosFunction application)
                                                                              (:id organization))))
                             (permit/application-krysp-mapper organization "fi"
                                                              krysp-version begin-of-link)
                             :xml
                             data-xml/emit-str
                             (xml/parse-string "utf8"))
         application-xml (if-let [tag (resolve-tag permit-type :usecase)]
                           (enlive/at application-xml [tag] nil)
                           application-xml)
         verdict-xml     (data-pairs->xml application
                                          verdicts
                                          (fn [application verdict]
                                            (permit/verdict-krysp-mapper application
                                                                         organization
                                                                         verdict
                                                                         (or (get-in verdict [:data :language]) "fi")
                                                                         krysp-version
                                                                         begin-of-link))
                                          (resolve-tag permit-type :verdict))
         review-xml      (when-not verdict-id
                           (data-pairs->xml application
                                            reviews
                                            (fn [application review]
                                              (permit/review-krysp-mapper application
                                                                          organization
                                                                          review
                                                                          user
                                                                          "fi"
                                                                          krysp-version
                                                                          begin-of-link))
                                            (resolve-tag permit-type :review)))
         ;; Note: `xml/select` and its variants do not support pseudo classes.
         statement-sel   (when-let [tag (resolve-tag permit-type :statement)]
                           [[tag enlive/last-of-type]])
         operation-sel   (when-let [tag (resolve-tag permit-type :operation)]
                           [[tag enlive/last-of-type]])
         verdict-after   (if (and statement-sel
                                  (seq (enlive/select application-xml statement-sel)))
                           statement-sel
                           operation-sel)]
     (cond-> application-xml
       verdict-xml              (enlive/at verdict-after (enlive/after verdict-xml))
       review-xml               (enlive/at operation-sel (enlive/after review-xml))
       true                     xml/element-to-string
       (not (false? validate?)) (validator/validate-integration-message! permit-type krysp-version))))
  ([organization system application options]
   (full-batchrun-kuntagml organization system application
                           (usr/batchrun-user [(:id organization)])
                           options))
  ([{:keys [organization application user options system]}]
   (full-batchrun-kuntagml (force organization) system application user options)))
