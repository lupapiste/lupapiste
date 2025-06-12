(ns lupapalvelu.document.attachments-canonical
  (:require [clojure.walk :as walk]
            [lupapalvelu.document.canonical-common :as canonical-common]
            [lupapalvelu.document.rakennuslupa-canonical :as rakval-canon]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain-attachment :as domain-att]
            [lupapalvelu.foreman-application-util :as foreman-util]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.sftp.util :as sftp-util]
            [lupapalvelu.states :as states]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]))

(set! *warn-on-reflection* true)

(defn- create-metatieto [k v]
  (when v
    {:metatieto {:metatietoNimi k :metatietoArvo v}
     :Metatieto {:metatietoNimi k :metatietoArvo v}}))

(defn- all-operation-ids [application]
  (let [primary (-> application :primaryOperation :id)
        secondaries (map :id (:secondaryOperations application))]
    (remove nil? (conj secondaries primary))))

(defn- attachment-operation-ids
  "Returns set of operation ids for the given attachment.
  If the attachment is not explicitly linked to an operation,
  every application operation id is included in the result."
  [attachment application]
  (set (or (->> attachment :op (map :id) not-empty)
           (some-> attachment :op :id list)
           (all-operation-ids application))))

(defn- operation-attachment-meta
  "Operation id and VTJ-PRT from either the attachment's 'own'
  operation or every operation if the attachment is not bound to any
  specific op."
  [attachment application]
  (let [ops (attachment-operation-ids attachment application)
        metas (for [op-id ops
                    :let [docs  (filter #(= op-id (-> % :schema-info :op :id))
                                        (:documents application))]]
                [(when-not (foreman-util/foreman-app? application)
                   (create-metatieto "toimenpideId" op-id))
                 (map #(create-metatieto "VTJ-PRT" (-> % :data :valtakunnallinenNumero :value))
                      docs)])]
    (->> metas flatten (remove nil?) )))

(defn- extra-attachment-meta
  "Optional additional metadata added originally for Tampere in LPK-5113.
   Extended with salassapito metadata in LPK-8212 and LPK-8213."
  [{:keys [approvals metadata applicationState latestVersion] :as _attachment} organization]
  (when (:krysp-extra-attachment-metadata-enabled organization)
    (let [approval  (->> latestVersion :originalFileId keyword (get approvals))
          approved? (= "ok" (:state approval))
          {:keys [julkisuusluokka salassapitoaika salassapitoperuste
                  salassapidettavat-osat
                  security-period-end henkilotiedot suojaustaso
                  turvallisuusluokka kayttajaryhma kayttajaryhmakuvaus
                  parent-file-id]} metadata]
      (->> {"rakentamisenaikainen"     (when (contains? states/post-verdict-states (keyword applicationState)) "true")
            "nakyvyys"                 (case (:nakyvyys metadata)
                                         "julkinen"                "julkinen"
                                         "viranomainen"            "liitteen lis\u00e4\u00e4j\u00e4 ja viranomaiset"
                                         "asiakas-ja-viranomainen" "hankkeen osapuolet ja viranomaiset"
                                         nil)
            "dokumentinHyvaksymispvm"  (when approved?
                                         (-> approval :timestamp (date/finnish-date :zero-pad)))
            "dokumentinHyvaksyja"      (when approved?
                                         (format "%s %s"
                                                 (-> approval :user :firstName)
                                                 (-> approval :user :lastName)))
            ;; Salassapito metadata
            "julkisuusluokka"          julkisuusluokka
            "salassapitoaika"          (some-> salassapitoaika str)
            "salassapitoperuste"       salassapitoperuste
            "salassapidettavat-osat"   salassapidettavat-osat
            "salassapitoaikapaattyy"   (some-> security-period-end date/xml-datetime)
            "henkilotieto"             henkilotiedot
            "suojaustaso"              suojaustaso
            "turvallisuusluokka"       turvallisuusluokka
            "kayttajaryhma"            kayttajaryhma
            "kayttajaryhmakuvaus"      kayttajaryhmakuvaus
            ;; Parent reference for linked files
            "parent_id"                parent-file-id}
           util/strip-nils
           (map #(apply create-metatieto %))))))

(defn- get-attachment-signature-meta
  [index {created :created {:keys [firstName lastName]} :user :as _signature}]
  (let [name       (str firstName " " lastName)
        date-time  (date/xml-datetime created)
        key-signer (str "allekirjoittaja_" index)
        key-time   (str "allekirjoittajaAika_" index)]
    (->> {key-signer name
          key-time   date-time}
         (map #(apply create-metatieto %)))))

(defn- get-attachment-meta
  [{:keys [signatures latestVersion id forPrinting contents] :as attachment} application organization]
  (let [op-metas            (operation-attachment-meta attachment application)
        liitepohja          [(create-metatieto "liiteId" id)]
        latest-version      (select-keys (:version latestVersion) [:major :minor])
        signatures          (->> signatures
                                 (filter #(= latest-version (select-keys (:version %) [:major :minor])))
                                 (map get-attachment-signature-meta (range))
                                 (apply concat)
                                 vec)
        verdict-attachment  (some->> forPrinting (create-metatieto "paatoksen liite") vector)
        filename            [(create-metatieto "tiedostonimi" (ss/encode-filename (:filename latestVersion)))]
        contents            (when-not (ss/blank? contents)
                              [(create-metatieto "sisalto" contents)])
        extra-metadata      (extra-attachment-meta attachment organization)]
    (remove empty? (concat liitepohja op-metas signatures verdict-attachment contents filename extra-metadata))))


(defn- get-Liite [title link attachment type filename & [meta building-ids]]
  (let [{:keys [version fileId created modified]} (:latestVersion attachment)]
    (cond-> {:kuvaus              title
             :linkkiliitteeseen   link
             :muokkausHetki       (date/xml-datetime (or modified created))
             :versionumero        (str (:major version) "." (:minor version))
             :tyyppi              type
             :metatietotieto      meta
             :rakennustunnustieto building-ids
             :fileId              fileId
             :filename            filename}
      ;; Preserve linked file metadata for BIM files
      (:linked-file attachment) (assoc :linked-file (:linked-file attachment)))))

(defn- get-attachment-building-ids [attachment application]
  (let [op-ids (attachment-operation-ids attachment application)
        ;; Attachment operations that have buildings
        docs (->> (:documents application)
                  (map (fn [doc]
                         (let [data (:data doc)]
                           (when (and (contains? op-ids (-> doc :schema-info :op :id))
                                      (or (:rakennusnro data) (:manuaalinen_rakennusnro data)))
                             doc))))
                  (remove nil?))]
    (for [[i doc] (zipmap (range (count docs)) docs)
          ;; Remove keys with blank (or nil) values.
          :let [data (reduce (fn [acc [k v]] (if-not (or (nil? v)
                                                         (and (string? v) (ss/blank? v)))
                                               (assoc acc k v)
                                               acc))
                             {}
                             (:data doc))
                bid (rakval-canon/get-rakennustunnus data application (:schema-info doc))]]
      ;; Jarjestysnumero is mandatory, however the semantics are bit hazy. The number
      ;; should probably be unique among application, but for now we just use local value.
      {:Rakennustunnus (assoc bid :jarjestysnumero (inc i))})))

(defn attachment-url [{:keys [id]}]
  (str "latest-attachment-version?attachment-id=" id))

(def no-statements-no-verdicts
  (fn [attachment]
    (and (not= "statement" (-> attachment :target :type))
         (not= "verdict" (-> attachment :target :type)))))

(defn get-attachment-link [begin-of-link attachment]
  (let [use-http-links? (re-matches #"https?://.*" begin-of-link)]
    (str begin-of-link (if use-http-links?
                         (attachment-url attachment)
                         (when-not use-http-links?
                           (sftp-util/get-file-name-on-server (-> attachment :latestVersion :fileId)
                                                              (-> attachment :latestVersion :filename)))))))

(defn attachment-as-canonical
  [attachment application organization begin-of-link]
  (let [unwrapped-app             (tools/unwrapped application)
        type-group                (-> attachment :type :type-group)
        type-id                   (-> attachment :type :type-id)
        attachment-localized-name (i18n/localize "fi" "attachmentType" (name type-group) (name type-id))
        attachment-title          (if (:contents attachment)
                                    (str attachment-localized-name ": " (:contents attachment))
                                    attachment-localized-name)
        file-id                   (-> attachment :latestVersion :fileId)
        use-http-links?           (re-matches #"https?://.*" begin-of-link)
        filename                  (when-not use-http-links?
                                    (sftp-util/get-file-name-on-server file-id
                                                                       (-> attachment :latestVersion :filename)))
        link                      (get-attachment-link begin-of-link attachment)
        meta                      (get-attachment-meta attachment application organization)
        building-ids              (get-attachment-building-ids attachment unwrapped-app)]
    (get-Liite attachment-title link attachment type-id filename meta building-ids)))

(defn get-attachments-as-canonical
  ([application organization begin-of-link]
   (get-attachments-as-canonical application organization begin-of-link no-statements-no-verdicts))
  ([{:keys [attachments] :as application} organization begin-of-link pred]
   (not-empty (for [attachment attachments
                    :when (and (:latestVersion attachment)
                               (pred attachment))
                    :let [liite (attachment-as-canonical attachment application organization begin-of-link)]]
                {:Liite liite}))))

;;
;;  Statement attachments
;;

(defn flatten-statement-attachments
  "Argument originates from `get-statement-attachments-as-canonical` and is an id - list
  map. Flattening in this context means concatenating value lists. "
  [statement-attachments]
  (apply concat (vals statement-attachments)))

(defn add-statement-attachments [canonical statement-attachments lausunto-path]
  (cond-> canonical
    statement-attachments
    (update-in lausunto-path
               (util/fn->>
                 (map (fn [canon-statement]
                        (let [statement-id (get-in canon-statement [:Lausunto :id])
                              attachments  (get statement-attachments statement-id)]
                          (cond-> canon-statement
                            attachments
                            (assoc-in [:Lausunto :lausuntotieto :Lausunto :liitetieto]
                                      attachments)))))))))

(defn- get-liite-for-lausunto
  [attachment application organization begin-of-link]
  {:Liite (-> (attachment-as-canonical attachment application organization begin-of-link)
              (assoc :tyyppi "lausunto"
                     :kuvaus (str (:title application) ": lausunto-"  (:id attachment))))})

(defn get-statement-attachments-as-canonical
  "Returns statement-id - canonical attachment list map. No empty lists. Nil if empty."
  [application organization begin-of-link allowed-statement-ids]
  (some->> (seq allowed-statement-ids)
           (keep (fn [statement-id]
                   (some->> (:attachments application)
                            (filter (util/fn-> :target (select-keys [:type :id])
                                               (= {:type "statement"
                                                   :id   statement-id})))
                            seq
                            (map #(get-liite-for-lausunto % application organization begin-of-link))
                            (hash-map statement-id))))
           (into {})))

(defn walk-statement-attachments
  "Adds statement attachments to canonical :lausuntotieto and returns a map with them
  by walking the canonical, looking for statement elements.
  Only necessary when there are idiosyncratic KuntaGML schemas (MM and KT)."
  [canonical application organization begin-of-link]
  (let [statement-ids  (->> application :statements (filter canonical-common/status->puolto) (map :id) set)
        statement-atts (get-statement-attachments-as-canonical application organization begin-of-link statement-ids)
        new-canonical  (walk/postwalk (fn [form]
                                        (if (and (coll? form)
                                                 (-> form first (= :Lausunto))
                                                 (-> form second map?)
                                                 (-> form second :id) ; not the inner Lausunto
                                                 (-> form second :lausuntotieto)) ; not a request
                                          (update-in form
                                                     [1 :lausuntotieto :Lausunto :liitetieto]
                                                     into
                                                     (get statement-atts (-> form second :id)))
                                          form))
                                      canonical)]
    {:canonical-with-statement-atts new-canonical
     :statement-atts                statement-atts}))

(defn verdict-attachment-link
  "Attachment is not mandatory and can be missing."
  [application organization verdict begin-of-link]
  (some-> (vc/verdict-attachment-id verdict)
          ;; We might be altering `:attachments` when building verdicts
          ;; so we can't assume `:attachments-by-id` is valid anymore
          (as-> id (domain-att/get-attachment-by-id-slow application id))
          (attachment-as-canonical application
                                   organization
                                   begin-of-link)
          (assoc :versionumero "0.1")
          (dissoc :rakennustunnustieto)))
