(ns lupapalvelu.backing-system.krysp.application-as-krysp-to-backing-system
  "Convert application to KuntaGML and send it to configured backing system"
  (:require [clojure.data.xml :as data-xml]
            [lupapalvelu.application-meta-fields :as meta-fields]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.backing-system.krysp.http :as krysp-http]
            ;; Make sure all the mappers are registered
            [lupapalvelu.backing-system.krysp.kiinteistotoimitus-mapping]
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
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.parties-canonical]
            [lupapalvelu.document.tools :as doc-tools]
            [lupapalvelu.foreman :as foreman]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.organization :as org]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.sftp.context :as sftp-ctx]
            [lupapalvelu.sftp.core :as sftp]
            [lupapalvelu.states :as states]
            [lupapalvelu.tiedonohjaus :refer [tos-function-with-name]]
            [lupapalvelu.xml.validator :as validator]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.util :as util]
            [taoensso.timbre :refer [error warn]])
  (:import [java.io InputStream]))

(defn- get-begin-of-link [permit-type org]
  {:pre  [permit-type]
   :post [%]}
  (cond
    ;; For now, only Matti supports HTTP integration, so the REST API link can be deduced implicitly
    (get-in org [:krysp (keyword permit-type) :http :enabled])
    (str (env/value :host) "/rest/")

    (:use-attachment-links-integration org)
    (str (env/value :host) "/api/raw/")

    :else
    (str (env/value (cond->> [:fileserver-address]
                      (sftp-ctx/gcs-sftp? org) (cons :gcs)))
         (permit/get-sftp-directory permit-type) "/")))

(defn- resolve-krysp-version [organization permit-type]
  {:pre [(map? organization) permit-type]}
  (if-let [krysp-version (get-in organization [:krysp (keyword permit-type) :version])]
    (do
      (when-not (re-matches #"\d+\.\d+\.\d+" krysp-version)
        (error (str \' krysp-version "' does not look like a KRYSP version"))
        (fail! :error.integration.krysp-version-wrong-form))
      krysp-version)
    (do
      (error (str "KRYSP version not found for organization " (:id organization) ", permit-type " permit-type))
      (fail! :error.integration.krysp-version-missing))))

(defn http-conf
  "Returns outgoing HTTP conf for organization. Fails if http not
  enabled, unless force? is true (used by admin batchrun)."
  ([org permit-type force?]
   (when-let [http (get-in org [:krysp (keyword permit-type) :http])]
     (when (or (:enabled http) force?)
       http)))
  ([org permit-type]
   (http-conf org permit-type false)))

(defn http-not-allowed
  "Pre-check that fails, if http IS configured (thus sftp is OK)"
  [{:keys [organization application]}]
  (when (http-conf @organization (:permitType application))
    (fail :error.integration.krysp-http)))

(defn- remove-unsupported-attachments [application]
  (update application :attachments #(filter attachment/transmittable-to-krysp? %)))

(defn- filter-attachments-by-state [current-state {attachments :attachments :as application}]
  (if (states/pre-verdict-states (keyword current-state))
    (->> (remove (comp states/post-verdict-states keyword :applicationState) attachments)
         (assoc application :attachments))
    application))

(defn- designer-doc? [document]
  (= :suunnittelija (doc-tools/doc-subtype document)))

(defn- non-approved? [document]
  (or (not (approval/approved? document))
      (pos? (model/modifications-since-approvals document))))

(defn- remove-non-approved-designers [application]
  (update application :documents #(remove (every-pred designer-doc? non-approved?) %)))

(defn- created-before-verdict? [application document]
  (not (doc/created-after-verdict? document application)))

(defn- approved-before-verdict? [application document]
  (not (doc/approved-after-verdict? document application)))

(defn- remove-pre-verdict-designers [application]
  (update application :documents #(remove (every-pred designer-doc? (partial approved-before-verdict? application)) %)))

(defn- remove-disabled-documents [application]
  (update application :documents (fn [docs] (remove :disabled docs))))

(defn- filter-attachments-for-verdict
  "Only verdict attachment is kept for all verdicts.
  On the other hand foreman verdicts may also contain other attachments after filtering."
  [{:keys [state] :as application} verdict-attachment]
  (if (foreman/foreman-app? application)
    (->> application
         remove-unsupported-attachments
         (filter-attachments-by-state state))
    (assoc application :attachments [verdict-attachment])))

(defn save-application-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that were sent."
  [{:keys [application organization user] :as command} lang submitted-application & {:keys [current-state]}]
  {:pre [(map? application) lang (map? submitted-application) (or (map? organization) (delay? organization))]}
  (assert (= (:id application) (:id submitted-application)) "Not same application ids.")
  (let [organization   (if (delay? organization) @organization organization)
        permit-type    (permit/permit-type application)
        krysp-version  (resolve-krysp-version organization permit-type)
        begin-of-link  (get-begin-of-link permit-type organization)
        filtered-app   (->> application
                            remove-unsupported-attachments
                            (filter-attachments-by-state current-state)
                            remove-non-approved-designers)
        submitted-app  (->> submitted-application
                            remove-unsupported-attachments
                            (filter-attachments-by-state current-state))
        output-fn      (if-some [http-conf (http-conf organization permit-type)]
                         (fn [xml attachments]
                           (krysp-http/send-xml application user :application xml http-conf)
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
  [{:keys [application organization user] :as command} lang]
  (let [permit-type   (permit/permit-type application)
        krysp-version (resolve-krysp-version @organization permit-type)
        filtered-app  (-> application
                          (dissoc :attachments)            ; attachments not needed
                          remove-non-approved-designers
                          remove-pre-verdict-designers
                          remove-disabled-documents)
        output-fn     (if-some [http-conf (http-conf @organization permit-type)]
                        (fn [xml _] (krysp-http/send-xml application user :parties xml http-conf))
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
  [{:keys [application organization user] :as command} lang operation-id]
  (let [permit-type (permit/permit-type application)]
    (when (org/krysp-write-integration? @organization permit-type)
      (let [krysp-version  (resolve-krysp-version @organization permit-type)
            output-fn      (if-some [http-conf (http-conf @organization permit-type)]
                             (fn [xml] (krysp-http/send-xml application user :building-extinction xml http-conf))
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
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that were sent."
  [{:keys [application organization user] :as command} task lang]
  (let [permit-type (permit/permit-type application)]
    (when (org/krysp-write-integration? @organization permit-type)
      (let [krysp-version  (resolve-krysp-version @organization permit-type)
            begin-of-link  (get-begin-of-link permit-type @organization)
            filtered-app   (remove-unsupported-attachments application)
            output-fn      (if-some [http-conf (http-conf @organization permit-type)]
                             (fn [xml attachments]
                               (if (get-in http-conf [:path :review]) ; skip sending if review path doesn't exists
                                 (do
                                   (krysp-http/send-xml application user :review xml http-conf)
                                   (->> attachments (map :fileId) (remove nil?)))
                                 (do
                                   (warn "No HTTP path for reviews")
                                   [])))
                             #(sftp/write-application command
                                                      {:xml         %1
                                                       :attachments %2
                                                       :file-suffix "review"}))
            mapping-result (permit/review-krysp-mapper filtered-app organization task user lang krysp-version begin-of-link)]
        (if-some [{:keys [xml attachments]} mapping-result]
          (-> (data-xml/emit-str xml)
              (validator/validate-integration-message! permit-type krysp-version)
              (output-fn attachments))
          (fail! :error.unknown))))))

(defn save-aloitusilmoitus-as-krysp
  "Sends aloitusilmoitus to municipality backend. Returns a sequence of
  attachment file IDs that were sent. Note: only SFTP supported for
  now."
  [{:keys [application organization] :as command} notice-form]
  (let [permit-type (permit/permit-type application)]
    (when (org/krysp-write-integration? @organization permit-type)
      (let [krysp-version  (resolve-krysp-version @organization permit-type)
            begin-of-link  (get-begin-of-link permit-type @organization)
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
  [{:keys [application organization user] :as command}]
  (let [permit-type (permit/permit-type application)]
    (when (org/krysp-write-integration? @organization permit-type)
      (let [krysp-version  (resolve-krysp-version @organization permit-type)
            output-fn      (if-some [http-conf (http-conf @organization permit-type)]
                             (fn [xml]
                               (krysp-http/send-xml application user :verdict xml http-conf))
                             #(sftp/write-application command
                                                      {:xml         %
                                                       :file-suffix "RH"}))
            mapping-result (rl-mapping/rh-tietojen-muutos-kuntagml command krysp-version)]
        (if-some [{:keys [xml]} mapping-result]
          (-> (data-xml/emit-str xml)
              (validator/validate-integration-message! permit-type krysp-version)
              output-fn)
          (fail! :error.unknown))))))

(defn save-unsent-attachments-as-krysp
  "Sends attachments to municipality backend. Returns a sequence of attachment file IDs that were sent."
  [user organization application attachments lang]
  (let [application    (assoc application :attachments attachments) ; HACK: Callees expect to get `attachments` like this.
        permit-type    (permit/permit-type application)
        krysp-version  (resolve-krysp-version organization permit-type)
        begin-of-link  (get-begin-of-link permit-type organization)
        filtered-app   (remove-unsupported-attachments application)

        output-fn      (if-some [http-conf (http-conf organization permit-type)]
                         (fn [xml attachments]
                           (krysp-http/send-xml application user :attachments xml http-conf)
                           (->> attachments (map :fileId) (remove nil?)))
                         #(sftp/write-application  organization
                                                   application
                                                   {:xml         %1
                                                    :attachments %2}))
        mapping-result (rl-mapping/save-unsent-attachments-as-krysp filtered-app organization lang krysp-version begin-of-link)]
    (if-some [{:keys [xml attachments]} mapping-result]
      (-> (data-xml/emit-str xml)
          (validator/validate-integration-message! permit-type krysp-version)
          (output-fn attachments))
      (fail! :error.unknown))))

(defn verdict-as-kuntagml
  [{:keys [application organization user] :as command} verdict verdict-attachment]
  (let [organization   (force organization)
        permit-type    (permit/permit-type application)
        krysp-version  (resolve-krysp-version organization permit-type)
        begin-of-link  (get-begin-of-link permit-type organization)
        filtered-app   (-> application
                           (filter-attachments-for-verdict verdict-attachment)
                           remove-non-approved-designers
                           (assoc :tosFunctionName (:name (tos-function-with-name (:tosFunction application)
                                                                                  (:id organization)))))
        output-fn      (if-some [http-conf (http-conf organization permit-type)]
                         (fn [xml attachments]
                           (krysp-http/send-xml application user :verdict xml http-conf)
                           (->> attachments (map :fileId) (remove nil?)))
                         #(sftp/write-application command
                                                  {:xml         %1
                                                   :attachments %2
                                                   :file-suffix "verdict"}))

        mapping-result (permit/verdict-krysp-mapper filtered-app organization verdict
                                                    (or (get-in verdict [:data :language]) "fi")
                                                    krysp-version
                                                    begin-of-link)]
    (if-some [{:keys [xml attachments]} mapping-result]
      (-> (data-xml/emit-str xml)
          (validator/validate-integration-message! permit-type krysp-version)
          (output-fn attachments))
      (fail! :error.unknown))))

(defn- export-comments-pdf-to-sftp [sftp-context application]
  (let [lang                  (attachment/resolve-lang-for-comments-attachment application)
        filename              (comment/get-comments-filename lang application)
        ^InputStream contents (:pdf-file-stream (comment/get-comments-as-pdf lang application))]
    (sftp-ctx/write-file sftp-context filename contents)))

(defn full-application-export-as-kuntagml
  "WIP Outputs full application as KuntaGML.
  Currently only basic application details, attachments and comments (SFTP only)
  In the future if needed:
  - verdicts
  - reviews"
  [{:keys [application organization user lang] :or {lang "fi"}}]
  (let [organization   (force organization)
        permit-type    (permit/permit-type application)
        krysp-version  (resolve-krysp-version organization permit-type)
        begin-of-link  (get-begin-of-link permit-type organization)
        filtered-app   (-> application
                           remove-non-approved-designers
                           (assoc :tosFunctionName (:name (tos-function-with-name (:tosFunction application)
                                                                                  (:id organization)))))
        output-fn      (if-some [http-conf (http-conf organization permit-type)]
                         (fn [xml attachments]
                           (krysp-http/send-xml application user :verdict xml http-conf)
                           (->> attachments (map :fileId) (remove nil?)))
                         #(let [ctx (sftp-ctx/default-context organization application)]
                            (export-comments-pdf-to-sftp ctx application)
                            (sftp/write-application organization
                                                    application
                                                    {:xml          %1
                                                     :attachments  %2
                                                     :file-suffix  "export"})))
        ;verdict        (vif/latest-published-verdict command)
        mapped-app     (permit/application-krysp-mapper filtered-app organization lang krysp-version begin-of-link)]
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
      (dissoc :attachments)
      (assoc :id (format "%s-T00" (:id foreman-app)))
                                    ; Attaches a "running" total to separate from actual apps;
                                    ; TODO: Make it increase if it becomes relevant
                                    ; (i.e. able to terminate foreman application more than once)
      (assoc :permitSubtype (if confirmation? "tyonjohtaja-hakemus" "tyonjohtaja-ilmoitus"))
      (assoc-in [:primaryOperation :name] (if confirmation? "tyonjohtajan-vastuiden-paattaminen"
                                                            "tyonjohtajan-irtisanominen"))
      (assoc-in [:foremanTermination :ended] termination-ts)))

(defn foreman-termination-as-kuntagml
  "Sends an altered copy of the foreman application informing the backend of the foreman's termination"
  [{:keys [organization user state created]} foreman-app confirmation? lang]
  {:pre [lang (map? foreman-app) (or (map? organization) (delay? organization))]}
  (let [organization   (force organization)
        permit-type    (permit/permit-type foreman-app)
        krysp-version  (resolve-krysp-version organization permit-type)
        begin-of-link  (get-begin-of-link permit-type organization)
        filtered-app   (->> foreman-app
                            meta-fields/enrich-with-link-permit-data
                            (foreman-app-to-foreman-termination-app confirmation? created))
        submitted-app  (->> (mongo/by-id :submitted-applications (:id foreman-app))
                            remove-unsupported-attachments
                            (filter-attachments-by-state state))
        output-fn      (if-some [http-conf (http-conf organization permit-type)]
                         (fn [xml attachments]
                           (krysp-http/send-xml foreman-app user :application xml http-conf)
                           (->> attachments (map :fileId) (remove nil?)))
                         #(sftp/write-application  organization
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
      (fail! :error.unknown)))) ;;TODO: send verdict when `confirmation?` is true? Spec implies they are optional

(defn batchrun-kuntagml
  "KuntaGML for the given application.

  Options (the last parameter)

  verdict: Pate or backing system verdict. If given, verdict KuntaGML is generated.

  validate?: If false, the generated XML is not validated, this is
  useful when the data is known to be non-valid KuntaGML (in the test
  environment, for example). Default is true"
  ([application organization {:keys [verdict validate?]}]
   (let [validate?          (if (nil? validate?) true validate?)
         permit-type        (permit/permit-type application)
         krysp-version      (resolve-krysp-version organization permit-type)
         begin-of-link      (get-begin-of-link permit-type organization)
         verdict-attachment (util/find-by-id (vc/verdict-attachment-id verdict) (:attachments application))
         filtered-app       (-> application
                                (remove-non-approved-designers)
                                (assoc :tosFunctionName (:name (tos-function-with-name (:tosFunction application)
                                                                                       (:id organization))))
                                (cond->
                                  verdict (filter-attachments-for-verdict verdict-attachment)))
         mapping-result (if verdict
                          (permit/verdict-krysp-mapper filtered-app organization verdict
                                                       (or (get-in verdict [:data :language]) "fi")
                                                       krysp-version
                                                       begin-of-link)
                          (permit/application-krysp-mapper filtered-app organization "fi" krysp-version begin-of-link))]
     (if-some [{:keys [xml]} mapping-result]
       (cond-> (data-xml/emit-str xml)
         validate? (validator/validate-integration-message! permit-type krysp-version))
       (fail! :error.no-xml))))
  ([application organization]
   (batchrun-kuntagml application organization {})))
