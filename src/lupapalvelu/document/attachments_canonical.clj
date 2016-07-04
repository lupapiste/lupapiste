(ns lupapalvelu.document.attachments-canonical
  (:require [lupapalvelu.i18n :as i18n]
            [lupapalvelu.xml.disk-writer :as writer]
            [lupapalvelu.document.tools :as tools]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.document.rakennuslupa-canonical :as rakval-canon]))

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
  (let [ops (or (-> attachment :op :id) (all-operation-ids application))]
    (-> ops list flatten set)))

(defn- operation-attachment-meta
  "Operation id and VTJ-PRT from either the attachment's 'own'
  operation or every operation if the attachment is not bound to any
  specific op."
  [attachment application]
  (let [ops (attachment-operation-ids attachment application)
        metas (for [op-id ops
                    :let [docs  (filter #(= op-id (-> % :schema-info :op :id))
                                        (:documents application))]]
                [(create-metatieto "toimenpideId" op-id)
                 (map #(create-metatieto "VTJ-PRT" (-> % :data :valtakunnallinenNumero :value))
                      docs)])]
    (->> metas flatten (remove nil?) )))

(defn- get-attachment-meta [attachment application]
  (let [op-metas (operation-attachment-meta attachment application)
        signatures (:signatures attachment)
        latestVersion (:latestVersion attachment)
        liitepohja [(create-metatieto "liiteId" (:id attachment))]
        signatures (->> signatures
                        (filter #(and
                                  (= (get-in % [:version :major]) (get-in latestVersion [:version :major]))
                                  (= (get-in % [:version :minor]) (get-in latestVersion [:version :minor]))))
                        (map #(let [firstName (get-in %2 [:user :firstName])
                                    lastName (get-in %2 [:user :lastName])
                                    created (util/to-xml-datetime (:created %2))
                                    count %1]
                               [(create-metatieto (str "allekirjoittaja_" count) (str firstName " " lastName))
                                (create-metatieto (str "allekirjoittajaAika_" count) created)]) (range))
                        (flatten)
                        (vec))]
    (remove empty? (concat liitepohja op-metas signatures))))


(defn- get-Liite [title link attachment type file-id filename & [meta building-ids]]
  {:kuvaus title
   :linkkiliitteeseen link
   :muokkausHetki (util/to-xml-datetime (:modified attachment))
   :versionumero 1
   :tyyppi type
   :metatietotieto meta
   :rakennustunnustieto building-ids
   :fileId file-id
   :filename filename})


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

(defn get-attachments-as-canonical [{:keys [attachments] :as application} begin-of-link & [target]]
  (let [unwrapped-app (tools/unwrapped application)]
    (not-empty (for [attachment attachments
                     :when (and (:latestVersion attachment)
                                (not= "statement" (-> attachment :target :type))
                                (not= "verdict" (-> attachment :target :type))
                                (or (nil? target) (= target (:target attachment))))
                     :let [type-group (get-in attachment [:type :type-group])
                           type-id (get-in attachment [:type :type-id])
                           attachment-localized-name (i18n/localize "fi" (ss/join "." ["attachmentType" type-group type-id]))
                           attachment-title (if (:contents attachment)
                                              (str attachment-localized-name ": " (:contents attachment))
                                              attachment-localized-name)
                           file-id (get-in attachment [:latestVersion :fileId])
                           use-http-links? (re-matches #"https?://.*" begin-of-link)
                           attachment-file-name (when-not use-http-links? (writer/get-file-name-on-server file-id (get-in attachment [:latestVersion :filename])))
                           link (str begin-of-link (if use-http-links? (attachment-url attachment) attachment-file-name))
                           meta (get-attachment-meta attachment application)
                           building-ids (get-attachment-building-ids attachment unwrapped-app)]]
                 {:Liite (get-Liite attachment-title link attachment type-id file-id attachment-file-name meta building-ids)}))))

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
        (let [lausuntotieto (get-in c lausunto-path)
              lausunto-id (name (first (keys a)))
              paivitettava-lausunto (some #(if (= (get-in % [:Lausunto :id]) lausunto-id) %) lausuntotieto)
              index-of-paivitettava (.indexOf lausuntotieto paivitettava-lausunto)
              paivitetty-lausunto (assoc-in paivitettava-lausunto [:Lausunto :lausuntotieto :Lausunto :liitetieto] ((keyword lausunto-id) a))
              paivitetty (assoc lausuntotieto index-of-paivitettava paivitetty-lausunto)]
          (assoc-in c lausunto-path paivitetty)))
      canonical
      statement-attachments)))


(defn- get-liite-for-lausunto [attachment application begin-of-link]
  (let [type "lausunto"
        title (str (:title application) ": " type "-" (:id attachment))
        file-id (get-in attachment [:latestVersion :fileId])
        use-http-links? (re-matches #"https?://.*" begin-of-link)
        attachment-file-name (when-not use-http-links? (writer/get-file-name-on-server file-id (get-in attachment [:latestVersion :filename])))
        link (str begin-of-link (if use-http-links? (attachment-url attachment) attachment-file-name))
        meta (get-attachment-meta attachment application)
        building-ids (get-attachment-building-ids attachment (tools/unwrapped application))]
    {:Liite (get-Liite title link attachment type file-id attachment-file-name meta building-ids)}))

(defn get-statement-attachments-as-canonical [application begin-of-link allowed-statement-ids]
  (let [statement-attachments-by-id (group-by
                                      (util/fn-> :target :id keyword)
                                      (filter
                                        (util/fn-> :target :type (= "statement"))
                                        (:attachments application)))
        canonical-attachments (for [id allowed-statement-ids]
                                {(keyword id) (for [attachment ((keyword id) statement-attachments-by-id)]
                                                (get-liite-for-lausunto attachment application begin-of-link))})]
    (not-empty canonical-attachments)))
