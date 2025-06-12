(ns lupapalvelu.document.ymparisto-ilmoitukset-canonical
  "Utilities for forming the canonical model out of applications for use with the ilmoitukset KuntaGML schema.
  This is a bit of a special case since there are four main types of ilmoitus elements:
  - melutarina
  - koeluontoinentoiminta
  - poikkeus
  - pimapuhdistus
  Which are not technically mutually exclusive but in practice always are.
  Also, for historical reasons only melutarina is on a permit of type YI (ymparistoilmoitus);
  the others are YM (muu ymparistolupa) since they didn't originally have KuntaGML support"
  (:require [lupapalvelu.document.asianhallinta-canonical :as ah-canonical]
            [lupapalvelu.document.canonical-common :as canonical-common]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.ymparistolupa-canonical :as ymparistolupa-canonical]
            [lupapalvelu.operations :as operations]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn- convert-kesto-row [[_ row]]
  (apply merge
         {:alkuPvm  (date/xml-date (:alku row))
          :loppuPvm (date/xml-date (:loppu row))}
         (-> (dissoc row :alku :loppu)
             (util/convert-values util/to-xml-time-from-string))))

(defn- toiminnansijaintitieto [{:keys [municipality address propertyId] :as application}]
  (let [sijainti-seq (canonical-common/get-sijaintitieto application)]
    (->> (rest sijainti-seq)
         (map (fn [sijainti]
                {:ToiminnanSijainti (assoc sijainti :Osoite {:osoitenimi {:teksti canonical-common/empty-tag}})}))
         (cons {:ToiminnanSijainti
                {:Osoite                    {:osoitenimi {:teksti address}
                                             :kunta      municipality}
                 :Kunta                     municipality
                 :Sijainti                  (-> sijainti-seq first :Sijainti)
                 :Kiinteistorekisterinumero propertyId}}))))

(defn- meluilmoitus-rakentaminen [{{{:keys [kuvaus muu-rakentaminen melua-aihettava-toiminta]} :rakentaminen} :data}]
  (let [muu-rakentaminen? (not (ss/blank? muu-rakentaminen))]
    (when (or melua-aihettava-toiminta
              muu-rakentaminen?)
      {(keyword (or melua-aihettava-toiminta "muu"))
       (cond->> kuvaus
         muu-rakentaminen? (str muu-rakentaminen ": "))})))

(defn- meluilmoitus-tapahtuma [{{{:keys [nimi ulkoilmakonsertti kuvaus]} :tapahtuma} :data}]
  (when nimi
    {(if ulkoilmakonsertti :ulkoilmakonsertti :muu) (str nimi " - " kuvaus)}))

(defn- meluilmoitus-toimintatieto [documents]
  (let [meluilmoitus (-> documents :meluilmoitus first)]
    (util/assoc-when
      {:yksilointitieto (:id meluilmoitus)
       :alkuHetki       (date/xml-datetime (:created meluilmoitus))}
      :rakentaminen (meluilmoitus-rakentaminen meluilmoitus)
      :tapahtuma (meluilmoitus-tapahtuma meluilmoitus))))


(defn- ymi-ilmoituskohde-canonical
  "All of the ilmoitus-schemas derive from ymi:AbstractIlmoitusKohde so share the canonical code for them too"
  [application documents lang]
  (let [kesto-row    (-> documents :ymp-ilm-kesto first :data :kesto first convert-kesto-row)
        hakija-key   (-> application operations/get-applicant-doc-schema-name keyword)
        link-permits (:linkPermitData application)]
    {:yksilointitieto        (:id application)
     :alkuHetki              (-> application :submitted date/xml-datetime)
     :kasittelytietotieto    (canonical-common/get-kasittelytieto-ymp application :Kasittelytieto)
     :luvanTunnistetiedot    (canonical-common/lupatunnus application)
     :toimenpiteet           (ah-canonical/get-toimenpiteet application lang)
     :kohteet                (canonical-common/kohteet application)
     :viitelupatieto         (map canonical-common/get-viitelupatieto link-permits)
     :lausuntotieto          (-> application :statements canonical-common/get-statements)
     :ilmoittaja             (-> documents (get hakija-key) first canonical-common/get-yhteystiedot)
     :maksajatieto           (canonical-common/get-maksajatiedot-if-user-filled-in-data documents)
     :toiminnanSijaintitieto (toiminnansijaintitieto application)
     :toimintatieto          {:Toiminta (meluilmoitus-toimintatieto documents)}
     :toiminnanKesto         kesto-row}))

(defn meluilmoitus-canonical [application lang]
  (let [unwrapped-docs {:documents (-> application :documents tools/unwrapped)}
        documents      (canonical-common/stripped-documents-by-type unwrapped-docs)
        meluilmo       (-> documents :meluilmoitus first)
        melu           (-> meluilmo :data :melu)]
    {:Ilmoitukset
     {:toimituksenTiedot (canonical-common/toimituksen-tiedot application lang)
      :melutarina
      {:Melutarina
       (merge (ymi-ilmoituskohde-canonical application documents lang)
              {:melutiedot {:koneidenLkm (-> meluilmo :data :rakentaminen :koneet)
                            :melutaso    {:db       (:melu10mdBa melu)
                                          :paiva    (:paivalla melu)
                                          :yo       (:yolla melu)
                                          :mittaaja (:mittaus melu)}}})}}}))

(defn koeluontoinen-canonical [application lang]
  (let [unwrapped-docs {:documents (-> application :documents tools/unwrapped)}
        documents      (canonical-common/stripped-documents-by-type unwrapped-docs)]
    {:Ilmoitukset
     {:toimituksenTiedot (canonical-common/toimituksen-tiedot application lang)
      :koeluontoinentoiminta
      {:Koeluontoinentoiminta
       (merge (ymi-ilmoituskohde-canonical application documents lang)
              ;; These children should be attachments but in Lupapiste they are text fields;
              ;; the parent is required by the schema but the children are not
              {:kuvausKoeluonteisestaToiminnasta {:kuvaus       nil
                                                  :raaka_aineet nil
                                                  :paastot      nil
                                                  :selvitys     nil}})}}}))

(defn poikkeus-canonical [application lang]
  (let [unwrapped-docs {:documents (-> application :documents tools/unwrapped)}
        documents      (canonical-common/stripped-documents-by-type unwrapped-docs)]
    {:Ilmoitukset
     {:toimituksenTiedot (canonical-common/toimituksen-tiedot application lang)
      :poikkeus
      {:Poikkeus (ymi-ilmoituskohde-canonical application documents lang)}}}))

(defn pimapuhdistus-canonical [application lang]
  (let [unwrapped-docs {:documents (-> application :documents tools/unwrapped)}
        documents      (canonical-common/stripped-documents-by-type unwrapped-docs)]
    {:Ilmoitukset
     {:toimituksenTiedot (canonical-common/toimituksen-tiedot application lang)
      :pimapuhdistus
      {:Pimapuhdistus (ymi-ilmoituskohde-canonical application documents lang)}}}))

(defn YI-kuntagml-schema? [application]
  (contains? #{"meluilmoitus"
               "koeluontoinen-toiminta"
               "ilmoitus-poikkeuksellisesta-tilanteesta"
               "pima"}
             (-> application :primaryOperation :name)))

(defn canonical-path [application]
  (case (-> application :primaryOperation :name)
    "meluilmoitus"                            [:Ilmoitukset :melutarina :Melutarina]
    "koeluontoinen-toiminta"                  [:Ilmoitukset :koeluontoinentoiminta :Koeluontoinentoiminta]
    "ilmoitus-poikkeuksellisesta-tilanteesta" [:Ilmoitukset :poikkeus :Poikkeus]
    "pima"                                    [:Ilmoitukset :pimapuhdistus :Pimapuhdistus]))

(defmethod canonical-common/application->canonical :YI [application lang]
  ;; Note that other ilmoitus types except melutarina are YM; see namespace docstring above
  (case (-> application :primaryOperation :name)
    "meluilmoitus"                            (meluilmoitus-canonical application lang)
    "koeluontoinen-toiminta"                  (koeluontoinen-canonical application lang)
    "ilmoitus-poikkeuksellisesta-tilanteesta" (poikkeus-canonical application lang)
    "pima"                                    (pimapuhdistus-canonical application lang)
    ;; Fallback for YI apps that do not use YI schema (for tests and reporting db export)
    (ymparistolupa-canonical/ymparistolupa-canonical application lang)))

(defmethod canonical-common/description :YI [_ canonical]
  (let [toiminta (or (get-in canonical [:Ilmoitukset :melutarina :Melutarina :toimintatieto :Toiminta])
                     (get-in canonical [:Ilmoitukset :koeluontoinentoiminta :Koeluontoinentoiminta :toimintatieto :Toiminta])
                     (get-in canonical [:Ilmoitukset :poikkeus :Poikkeus :toimintatieto :Toiminta])
                     (get-in canonical [:Ilmoitukset :pimapuhdistus :Pimapuhdistus :toimintatieto :Toiminta]))]
    (or (some-> toiminta :rakentaminen vals first)
        (some-> toiminta :tapahtuma vals first))))
