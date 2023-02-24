(ns lupapalvelu.document.attachments-canonical
  (:require [lupapalvelu.document.rakennuslupa-canonical :as rakval-canon]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.foreman-application-util :as foreman-util]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.pate.verdict-common :as vc]
            [lupapalvelu.sftp.util :as sftp-util]
            [lupapalvelu.states :as states]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util])
  (:import [clojure.lang APersistentVector]))

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
  "Optional additional metadata added originally for Tampere in LPK-5113."
  [{:keys [approvals metadata applicationState latestVersion] :as _attachment} organization]
  (when (:krysp-extra-attachment-metadata-enabled organization)
    (let [approval  (->> latestVersion :originalFileId keyword (get approvals))
          approved? (= "ok" (:state approval))]
      (->> {"rakentamisenaikainen"    (when (contains? states/post-verdict-states (keyword applicationState)) "true")
            "nakyvyys"                (case (:nakyvyys metadata)
                                        "julkinen"                "julkinen"
                                        "viranomainen"            "liitteen lis\u00e4\u00e4j\u00e4 ja viranomaiset"
                                        "asiakas-ja-viranomainen" "hankkeen osapuolet ja viranomaiset"
                                        nil)
            "dokumentinHyvaksymispvm" (when approved?
                                        (-> approval :timestamp (date/finnish-date :zero-pad)))
            "dokumentinHyvaksyja"     (when approved?
                                        (format "%s %s"
                                                (-> approval :user :firstName)
                                                (-> approval :user :lastName)))}
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
    {:kuvaus              title
     :linkkiliitteeseen   link
     :muokkausHetki       (date/xml-datetime (or modified created))
     :versionumero        (str (:major version) "." (:minor version))
     :tyyppi              type
     :metatietotieto      meta
     :rakennustunnustieto building-ids
     :fileId              fileId
     :filename            filename}))

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

(defn flatten-statement-attachments [statement-attachments]
  (let [attachments (for [statement statement-attachments] (vals statement))]
    (reduce concat (reduce concat attachments))))

(defn add-statement-attachments [canonical statement-attachments lausunto-path]
  (if (empty? statement-attachments)
    canonical
    (reduce
      (fn [c a]
        (let [^APersistentVector lausuntotieto (get-in c lausunto-path)
              lausunto-id (name (first (keys a)))
              paivitettava-lausunto (some #(if (= (get-in % [:Lausunto :id]) lausunto-id) %) lausuntotieto)
              index-of-paivitettava (.indexOf lausuntotieto paivitettava-lausunto)
              paivitetty-lausunto (assoc-in paivitettava-lausunto [:Lausunto :lausuntotieto :Lausunto :liitetieto] ((keyword lausunto-id) a))
              paivitetty (assoc lausuntotieto index-of-paivitettava paivitetty-lausunto)]
          (assoc-in c lausunto-path paivitetty)))
      canonical
      statement-attachments)))

(defn- get-liite-for-lausunto
  [attachment application organization begin-of-link]
  {:Liite (-> (attachment-as-canonical attachment application organization begin-of-link)
              (assoc :tyyppi "lausunto"
                     :kuvaus (str (:title application) ": lausunto-"  (:id attachment))))})

(defn get-statement-attachments-as-canonical [application organization begin-of-link allowed-statement-ids]
  (let [statement-attachments-by-id (group-by
                                      (util/fn-> :target :id keyword)
                                      (filter
                                        (util/fn-> :target :type (= "statement"))
                                        (:attachments application)))
        canonical-attachments (for [id allowed-statement-ids]
                                {(keyword id) (for [attachment ((keyword id) statement-attachments-by-id)]
                                                (get-liite-for-lausunto attachment application organization begin-of-link))})]
    (not-empty canonical-attachments)))

(defn verdict-attachment-link
  [application organization verdict begin-of-link]
  (if-let [verdict-attachment (util/find-by-id (vc/verdict-attachment-id verdict) (:attachments application))]
    (-> (attachment-as-canonical verdict-attachment
                                 application
                                 organization
                                 begin-of-link)
        (assoc :versionumero "0.1")
        (dissoc :rakennustunnustieto))
    (error-and-fail! (str "attachment for verdict " (:id verdict) " missing") :error.unknown)))
