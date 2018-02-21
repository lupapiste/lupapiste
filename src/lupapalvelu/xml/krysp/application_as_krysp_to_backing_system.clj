(ns lupapalvelu.xml.krysp.application-as-krysp-to-backing-system
  "Convert application to KuntaGML and send it to configured backing system"
  (:require [taoensso.timbre :refer [trace debug debugf info infof warn error fatal]]
            [clojure.java.io :as io]
            [swiss.arrows :refer :all]
            [sade.xml :as xml]
            [sade.common-reader :refer [strip-xml-namespaces]]
            [sade.env :as env]
            [sade.core :refer :all]
            [sade.util :as util]
            [lupapiste-commons.attachment-types :as attachment-types]
            [lupapalvelu.document.document :as doc]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.parties-canonical]
            [lupapalvelu.document.tools :as doc-tools]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.organization :as org]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]
            ;; Make sure all the mappers are registered
            [lupapalvelu.xml.krysp.rakennuslupa-mapping :as rl-mapping]
            [lupapalvelu.xml.krysp.poikkeamis-mapping]
            [lupapalvelu.xml.krysp.yleiset-alueet-mapping]
            [lupapalvelu.xml.krysp.ymparistolupa-mapping]
            [lupapalvelu.xml.krysp.maa-aines-mapping]
            [lupapalvelu.xml.krysp.maankayton-muutos-mapping]
            [lupapalvelu.xml.krysp.kiinteistotoimitus-mapping]
            [lupapalvelu.xml.krysp.ymparisto-ilmoitukset-mapping]
            [lupapalvelu.xml.krysp.vesihuolto-mapping]
            [lupapalvelu.xml.krysp.verdict-mapping]
            [lupapalvelu.xml.krysp.http :as krysp-http]
            [lupapalvelu.xml.disk-writer :as writer]
            [lupapalvelu.xml.validator :as validator]
            [clojure.data.xml :as data-xml]))

(defn- get-begin-of-link [permit-type org]
  {:pre  [permit-type]
   :post [%]}
  (if (or (get-in org [:krysp (keyword permit-type) :http])
          (:use-attachment-links-integration org))
    (str (env/value :host) "/api/raw/")
    (str (env/value :fileserver-address) (permit/get-sftp-directory permit-type) "/")))

(defn resolve-output-directory [organization permit-type]
  {:pre  [(map? organization) permit-type]
   :post [%]}
  (let [sftp-user (get-in organization [:krysp (keyword permit-type) :ftpUser])]
    (str (env/value :outgoing-directory) "/" sftp-user (permit/get-sftp-directory permit-type))))

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

(defn- http-conf
  "Returns outgoing HTTP conf for organization"
  [org permit-type]
  (get-in org [:krysp (keyword permit-type) :http]))

(defn http-not-allowed
  "Pre-check that fails, if http IS configured (thus sftp is OK)"
  [{:keys [organization application]}]
  (when (http-conf @organization (:permitType application))
    (fail :error.integration.krysp-http)))

(defn- remove-unsupported-attachments [application]
  (->> (remove
         (fn [{{:keys [type-group type-id]} :type}]
           (let [group (keyword type-group)
                 id (keyword type-id)]
             (when-let [forbidden-set (group attachment-types/types-not-transmitted-to-backing-system)]
               (forbidden-set id))))
         (:attachments application))
       (assoc application :attachments)))

(defn- filter-attachments-by-state [current-state {attachments :attachments :as application}]
  (if (states/pre-verdict-states (keyword current-state))
    (->> (remove (comp states/post-verdict-states keyword :applicationState) attachments)
         (assoc application :attachments))
    application))

(defn- designer-doc? [document]
  (= :suunnittelija (doc-tools/doc-subtype document)))

(defn- non-approved? [document]
  (or (not (doc/approved? document))
      (pos? (model/modifications-since-approvals document))))

(defn- remove-non-approved-designers [application]
  (update application :documents #(remove (every-pred designer-doc? non-approved?) %)))

(defn- created-before-verdict? [application document]
  (not (doc/created-after-verdict? document application)))

(defn- remove-pre-verdict-designers [application]
  (update application :documents #(remove (every-pred designer-doc? (partial created-before-verdict? application)) %)))

(defn- remove-disabled-documents [application]
  (update application :documents (fn [docs] (remove :disabled docs))))

(defn save-application-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent."
  [{:keys [application organization user]} lang submitted-application & {:keys [current-state]}]
  {:pre [(map? application) lang (map? submitted-application) (or (map? organization) (delay? organization))]}
  (assert (= (:id application) (:id submitted-application)) "Not same application ids.")
  (let [organization  (if (delay? organization) @organization organization)
        permit-type   (permit/permit-type application)
        krysp-version (resolve-krysp-version organization permit-type)
        begin-of-link (get-begin-of-link permit-type organization)
        filtered-app  (->> application
                           remove-unsupported-attachments
                           (filter-attachments-by-state current-state)
                           remove-non-approved-designers)
        submitted-app (->> submitted-application
                           remove-unsupported-attachments
                           (filter-attachments-by-state current-state))
        output-dir    (resolve-output-directory organization permit-type)

        output-fn     (if-some [http-conf (http-conf organization permit-type)]
                        (fn [xml attachments]
                          (krysp-http/send-xml application user :application xml http-conf)
                          (->> attachments (map :fileId) (remove nil?)))
                        #(writer/write-to-disk %1 application %2 output-dir submitted-app lang))

        mapping-result (permit/application-krysp-mapper filtered-app lang krysp-version begin-of-link)]
    (if-some [{:keys [xml attachments]} mapping-result]
      (-> (data-xml/emit-str xml)
          (validator/validate-integration-message! permit-type krysp-version)
          (output-fn attachments))
      (fail! :error.unknown))))

(defn save-parties-as-krysp
  "Send application's parties (currently only designers) to municipality backend. Returns sent party document ids."
  [{:keys [application organization user]} lang]
  (let [permit-type   (permit/permit-type application)
        krysp-version (resolve-krysp-version @organization permit-type)
        output-dir    (resolve-output-directory @organization permit-type)
        filtered-app  (-> application
                          (dissoc :attachments)            ; attachments not needed
                          remove-non-approved-designers
                          remove-pre-verdict-designers
                          remove-disabled-documents)

        output-fn      (if-some [http-conf (http-conf @organization permit-type)]
                         (fn [xml _] (krysp-http/send-xml application user :parties xml http-conf))
                         #(writer/write-to-disk %1 application nil output-dir nil nil %2))
        mapped-data (permit/parties-krysp-mapper filtered-app :suunnittelija lang krysp-version)]
    (when-not (map? mapped-data)
      (fail! :error.unknown))
    (run!
      (fn [[id xml]]
        (-> (data-xml/emit-str xml)
            (validator/validate-integration-message! permit-type krysp-version)
            (output-fn id)))
      mapped-data)
    (keys mapped-data)))

(defn save-review-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent."
  [{:keys [application organization user]} task lang]
  (let [permit-type (permit/permit-type application)]
    (when (org/krysp-integration? @organization permit-type)
      (let [krysp-version (resolve-krysp-version @organization permit-type)
            begin-of-link (get-begin-of-link permit-type @organization)
            filtered-app  (remove-unsupported-attachments application)
            output-dir    (resolve-output-directory @organization permit-type)

            output-fn     (if-some [http-conf (http-conf @organization permit-type)]
                            (fn [xml attachments]
                              (krysp-http/send-xml application user :review xml http-conf)
                              (->> attachments (map :fileId) (remove nil?)))
                            #(writer/write-to-disk %1 application %2 output-dir nil nil "review"))
            mapping-result (permit/review-krysp-mapper filtered-app task user lang krysp-version begin-of-link)]
        (if-some [{:keys [xml attachments]} mapping-result]
          (-> (data-xml/emit-str xml)
              (validator/validate-integration-message! permit-type krysp-version)
              (output-fn attachments))
          (fail! :error.unknown))))))

(defn save-unsent-attachments-as-krysp
  "Sends application to municipality backend. Returns a sequence of attachment file IDs that ware sent."
  [{:keys [application organization user]} lang]
  (let [permit-type   (permit/permit-type application)
        krysp-version (resolve-krysp-version @organization permit-type)
        begin-of-link (get-begin-of-link permit-type @organization)
        filtered-app  (remove-unsupported-attachments application)

        output-fn     (if-some [http-conf (http-conf @organization permit-type)]
                        (fn [xml attachments]
                          (krysp-http/send-xml application user :attachments xml http-conf)
                          (->> attachments (map :fileId) (remove nil?)))
                        #(writer/write-to-disk %1 application %2 (resolve-output-directory @organization permit-type)))
        mapping-result (rl-mapping/save-unsent-attachments-as-krysp filtered-app lang krysp-version begin-of-link)]
    (if-some [{:keys [xml attachments]} mapping-result]
      (-> (data-xml/emit-str xml)
          (validator/validate-integration-message! permit-type krysp-version)
          (output-fn attachments))
      (fail! :error.unknown))))

(defn verdict-as-kuntagml
  [{:keys [application organization user]} verdict]
  (let [organization  (if (delay? organization) @organization organization)
        permit-type   (permit/permit-type application)
        krysp-version (resolve-krysp-version organization permit-type)
        begin-of-link (get-begin-of-link permit-type organization)
        filtered-app  (-> application
                           #_(filter-attachments-by-state current-state)
                           (dissoc :attachments)            ; TODO: should application attachment be included in verdict XML?
                           remove-non-approved-designers)
        output-dir    (resolve-output-directory organization permit-type)
        output-fn     (if-some [http-conf (http-conf organization permit-type)]
                        (fn [xml attachments]
                          (krysp-http/send-xml application user :verdict xml http-conf)
                          (->> attachments (map :fileId) (remove nil?)))
                        #(writer/write-to-disk %1 application %2 output-dir nil nil "verdict"))

        mapping-result (permit/verdict-krysp-mapper filtered-app verdict
                                                    (get-in verdict [:data :language])
                                                    krysp-version
                                                    begin-of-link)]
    (if-some [{:keys [xml attachments]} mapping-result]
      (-> (data-xml/emit-str xml)
          (validator/validate-integration-message! permit-type krysp-version)
          (output-fn attachments))
      (fail! :error.unknown))))

(defn krysp-xml-files
  "Returns list of file paths that have XML extension and match the
  given application."
  [{:keys [id organization] :as application}]
  (let [pattern     (re-pattern (str "(?i)" id "_.*\\.xml$"))
        permit-type (permit/permit-type application)]
    (when (permit/valid-permit-type? permit-type)
      (some-<>> (org/get-organization organization)
                (resolve-output-directory <> permit-type)
                (util/get-files-by-regex <> pattern)
                (map str)))))

(defn cleanup-output-dir
  "Removes the old KRYSP message files from the output folder. Only
  the messages with the removable kayttotapaus value will be removed."
  [application]
  (let [removable #{"Rakentamisen aikainen muutos"
                    "Uuden ty\u00f6njohtajan nime\u00e4minen"
                    "Uuden suunnittelijan nime\u00e4minen"
                    "Jatkoaikahakemus"
                    "Uusi aloitusoikeus"
                    "Uusi maisematy\u00f6hakemus"
                    "Uusi hakemus"
                    "Uusi poikkeamisasia"
                    "Uusi suunnittelutarveasia"}
        content-fn  (fn [path]
                      (try
                        (-> path
                            slurp
                            (xml/parse-string "utf-8")
                            strip-xml-namespaces
                            (xml/select1 [:RakennusvalvontaAsia :kayttotapaus])
                            :content
                            first)
                        (catch Exception _)))]
    (doseq [file (krysp-xml-files application)
            :when (contains? removable (content-fn file)) ]
      (debugf "Remove deprecated %s" file)
      (io/delete-file file true))))
