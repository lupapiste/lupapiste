(ns lupapalvelu.document.asianhallinta-canonical
  (:require [lupapalvelu.document.attachments-canonical :as acanon]
            [lupapalvelu.document.canonical-common :as common]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.sftp.util :as sftp-util]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.property :as p]
            [sade.util :as util]))


;; UusiAsia

(def ^:private root-element {:UusiAsia nil})

(defn- get-asian-tyyppi-string [_]
  ; KasiteltavaHakemus, TODO tulossa Tiedoksianto (ilmoitukset)
  "KasiteltavaHakemus")

(defn- get-yhteystiedot [data]
  (util/strip-nils
    {:Jakeluosoite (get-in data [:osoite :katu])
     :Postinumero (get-in data [:osoite :postinumero])
     :Postitoimipaikka (get-in data [:osoite :postitoimipaikannimi])
     :Maa nil
     :Email (get-in data [:yhteystiedot :email])
     :Puhelinnumero (get-in data [:yhteystiedot :puhelin])}))

(defn- get-yhteyshenkilo [data]
  (util/strip-nils
    {:Etunimi (get-in data [:yhteyshenkilo :henkilotiedot :etunimi])
     :Sukunimi (get-in data [:yhteyshenkilo :henkilotiedot :sukunimi])
     :Yhteystiedot (get-yhteystiedot (:yhteyshenkilo data))
     :VainSahkoinenAsiointi (get-in data [:yhteyshenkilo :kytkimet :vainsahkoinenAsiointiKytkin])}))

(defn- get-henkilo [data]
  (util/strip-nils
    {:Etunimi (get-in data [:henkilo :henkilotiedot :etunimi])
     :Sukunimi (get-in data [:henkilo :henkilotiedot :sukunimi])
     :Yhteystiedot (get-yhteystiedot (:henkilo data))
     :Henkilotunnus (get-in data [:henkilo :henkilotiedot :hetu])
     :VainSahkoinenAsiointi (get-in data [:henkilo :kytkimet :vainsahkoinenAsiointiKytkin])
     :Turvakielto (get-in data [:henkilo :henkilotiedot :turvakieltoKytkin])}))

(defn- get-yritys [data]
  {:Nimi (get-in data [:yritys :yritysnimi])
   :Ytunnus (get-in data [:yritys :liikeJaYhteisoTunnus])
   :Yhteystiedot (get-yhteystiedot (:yritys data))
   :Yhteyshenkilo (get-yhteyshenkilo (:yritys data))})

(defn- get-hakijat [documents]
  (when (seq documents)
    {:Hakija (map
               (fn [doc]
                 (let [hakija-data (:data doc)
                       sel (:_selected hakija-data)]
                   (if (= sel "yritys")
                     {:Yritys (get-yritys hakija-data)}
                     {:Henkilo (get-henkilo hakija-data)})))
               documents)}))

(defn- get-maksaja [{data :data}]
  (let [sel (:_selected data)
        maksaja-map (util/strip-empty-maps
                      (util/strip-nils
                        {:Laskuviite (:laskuviite data)
                         :Verkkolaskutustieto (when (= "yritys" sel)
                                                {:OVT-tunnus (get-in data [:yritys :verkkolaskutustieto :ovtTunnus])
                                                 :Verkkolaskutunnus (get-in data [:yritys :verkkolaskutustieto :verkkolaskuTunnus])
                                                 :Operaattoritunnus (get-in data [:yritys :verkkolaskutustieto :valittajaTunnus])})}))]

    (if (= sel "yritys")
      (util/strip-empty-maps (assoc-in maksaja-map [:Yritys] (get-yritys data)))
      (util/strip-empty-maps (assoc-in maksaja-map [:Henkilo] (get-henkilo data))))))

(defn- get-metatiedot [attachment]
  (let [op-names   (->> (map :name (:op attachment)) (remove nil?))
        type-group (get-in attachment [:type :type-group])
        type-id    (get-in attachment [:type :type-id])]
    (cond->> (map (partial hash-map :Avain "operation" :Arvo) op-names)
      type-id    (cons {:Avain "type-id"    :Arvo type-id})
      type-group (cons {:Avain "type-group" :Arvo type-group}))))

(defn- get-toimenpide [operation lang]
  (util/strip-nils
    {:ToimenpideTunnus (:name operation)
     :ToimenpideTeksti (i18n/localize lang "operations" (:name operation))}))

(defn- get-toimenpiteet [{:keys [primaryOperation secondaryOperations]} lang]
  (let [operations (conj secondaryOperations primaryOperation)]
    (when (seq operations)
      {:Toimenpide (map #(-> % (get-toimenpide lang)) operations)})))

(defn- get-viitelupa [linkPermit]
  (if (= (:type linkPermit) "lupapistetunnus")
    (util/strip-nils
     {:MuuTunnus {:Tunnus (:id linkPermit)
                  :Sovellus "Lupapiste"}})
    (util/strip-nils
     {:AsianTunnus (:id linkPermit)})))

(defn- get-viiteluvat [{:keys [linkPermitData]}]
  (when (seq linkPermitData)
    {:Viitelupa (map get-viitelupa linkPermitData)}))

(defn- get-sijaintipiste [{:keys [location]}]
  {:Sijaintipiste (str (first location) " " (second location))})

(defn- get-liite
  "Return attachment in canonical format, with provided link as LinkkiLiitteeseen"
  [attachment link]
  (let [attachment-type (get-in attachment [:type :type-id])
        attachment-group (get-in attachment [:type :type-group])]
    (util/strip-nils
      {:Kuvaus attachment-type
       :KuvausFi (i18n/localize "fi" "attachmentType" attachment-group attachment-type)
       :KuvausSv (i18n/localize "sv" "attachmentType" attachment-group attachment-type)
       :Tyyppi (get-in attachment [:latestVersion :contentType])
       :LinkkiLiitteeseen link
       :Luotu (date/xml-date (:modified attachment))
       :Metatiedot {:Metatieto (get-metatiedot attachment)}})))

;; Public

(defn get-attachments-as-canonical
  ([attachments begin-of-link]
    (get-attachments-as-canonical attachments begin-of-link acanon/no-statements-no-verdicts))
  ([attachments begin-of-link pred]
   (not-empty
     (for [attachment attachments
           :when (and (:latestVersion attachment)
                      (pred attachment))
           :let [file-id (get-in attachment [:latestVersion :fileId])
                 attachment-file-name (sftp-util/get-file-name-on-server file-id
                                                                         (get-in attachment [:latestVersion :filename]))
                 link (str begin-of-link attachment-file-name)]]
       (get-liite attachment link)))))

(defn get-submitted-application-pdf [{:keys [id submitted]} begin-of-link]
  {:Kuvaus "Vireille tullut hakemus"
   :KuvausFi "Vireille tullut hakemus"
   :KuvausSv "Ans\u00f6kan under behandling"
   :Tyyppi "application/pdf"
   :LinkkiLiitteeseen (str begin-of-link (sftp-util/get-submitted-filename id))
   :Luotu (date/xml-date submitted)
   :Metatiedot {:Metatieto [{:Avain "type-group" :Arvo "hakemus"}
                            {:Avain "type-id"    :Arvo "hakemus_vireilletullessa"}]}})

(defn get-current-application-pdf [{:keys [id]} begin-of-link]
  {:Kuvaus "J\u00e4rjestelm\u00e4\u00e4n siirrett\u00e4ess\u00e4"
   :KuvausFi "Hakemus j\u00e4rjestelm\u00e4\u00e4n siirrett\u00e4ess\u00e4"
   :KuvausSv "Ans\u00f6kan i system\u00f6verf\u00f6ring"
   :Tyyppi "application/pdf"
   :LinkkiLiitteeseen (str begin-of-link (sftp-util/get-current-filename id))
   :Luotu (date/xml-date (sade.core/now))
   :Metatiedot {:Metatieto [{:Avain "type-group" :Arvo "hakemus"}
                            {:Avain "type-id"    :Arvo "hakemus_jarjestelmaan_siirrettaessa"}]}})

;; TaydennysAsiaan, prefix: ta-

(def ^:private ta-root-element {:TaydennysAsiaan nil})

;; AsianPaatos, prefix: ap-


;; AsianTunnusVastaus, prefix: atr-


(defn application-to-asianhallinta-canonical
  "Return canonical, does not contain attachments"
  [application lang & [type]]
  (let [documents (tools/unwrapped (common/documents-without-blanks application))]
    (-> root-element
      (assoc-in [:UusiAsia :Tyyppi] (or type (get-asian-tyyppi-string application)))
      (assoc-in [:UusiAsia :Kuvaus] (:title application))
      (assoc-in [:UusiAsia :Kuntanumero] (:municipality application))
      (assoc-in [:UusiAsia :Hakijat] (get-hakijat (domain/get-applicant-documents documents)))
      (assoc-in [:UusiAsia :Maksaja] (get-maksaja (first (domain/get-documents-by-subtype documents "maksaja"))))
      (assoc-in [:UusiAsia :HakemusTunnus] (:id application))
      (assoc-in [:UusiAsia :VireilletuloPvm] (date/xml-date (:submitted application)))
      (assoc-in [:UusiAsia :Asiointikieli] lang)
      (assoc-in [:UusiAsia :Toimenpiteet] (get-toimenpiteet application lang))
      (assoc-in [:UusiAsia :Sijainti] (get-sijaintipiste application))
      (assoc-in [:UusiAsia :Kiinteistotunnus] (p/to-human-readable-property-id (:propertyId application)))
      (assoc-in [:UusiAsia :Viiteluvat] (get-viiteluvat application)))))

(defn application-to-asianhallinta-taydennys-asiaan-canonical
  "Return TaydennysAsiaan canonical"
  [application]
  (assoc-in ta-root-element [:TaydennysAsiaan :HakemusTunnus] (:id application)))
