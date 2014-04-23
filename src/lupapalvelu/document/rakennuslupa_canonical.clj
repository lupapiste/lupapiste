(ns lupapalvelu.document.rakennuslupa_canonical
  (:require [clojure.java.io :as io]
            [clojure.xml :as xml]
            [clojure.string :as s]
            [lupapalvelu.core :refer [now]]
            [sade.strings :refer :all]
            [sade.util :refer :all]
            [lupapalvelu.i18n :refer [with-lang loc]]
            [lupapalvelu.document.canonical-common :refer :all]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.schemas :as schemas]))

(defn- get-huoneisto-data [huoneistot]
  (for [huoneisto (vals (into (sorted-map) huoneistot))
        :let [tyyppi (:huoneistonTyyppi huoneisto)
              varusteet (:varusteet huoneisto)
              huoneistonumero (-> huoneisto :huoneistoTunnus :huoneistonumero)
              huoneistoPorras (-> huoneisto :huoneistoTunnus :porras)
              jakokirjain (-> huoneisto :huoneistoTunnus :jakokirjain)]
        :when (seq huoneisto)]
    (merge {:muutostapa (-> huoneisto :muutostapa)
            :huoneluku (-> tyyppi :huoneluku)
            :keittionTyyppi (-> huoneisto :keittionTyyppi)
            :huoneistoala (-> tyyppi :huoneistoala)
            :varusteet {:WCKytkin (true? (-> varusteet :WCKytkin))
                        :ammeTaiSuihkuKytkin (true? (-> varusteet :ammeTaiSuihkuKytkin))
                        :saunaKytkin (true? (-> varusteet :saunaKytkin))
                        :parvekeTaiTerassiKytkin (true? (-> varusteet  :parvekeTaiTerassiKytkin))
                        :lamminvesiKytkin (true? (-> varusteet :lamminvesiKytkin))}
            :huoneistonTyyppi (-> tyyppi :huoneistoTyyppi)}
           (when (numeric? huoneistonumero)
             {:huoneistotunnus
              (merge {:huoneistonumero (format "%03d" (read-string (remove-leading-zeros huoneistonumero)))}
                     (when (not-empty huoneistoPorras) {:porras (s/upper-case huoneistoPorras)})
                     (when (not-empty jakokirjain) {:jakokirjain (lower-case jakokirjain)}))}))))

(defn- get-rakennuksen-omistaja [omistaja]
  {:Omistaja (merge (get-osapuoli-data omistaja :rakennuksenomistaja))})

(defn- get-rakennus [toimenpide application {id :id created :created}]
  (let [{kuvaus   :toimenpiteenKuvaus
         kaytto   :kaytto
         mitat    :mitat
         rakenne  :rakenne
         lammitys :lammitys
         luokitus :luokitus
         huoneistot :huoneistot} toimenpide
        kantava-rakennus-aine-map (muu-select-map :muuRakennusaine (-> rakenne :muuRakennusaine)
                                                  :rakennusaine (-> rakenne :kantavaRakennusaine))
        lammonlahde-map (muu-select-map
                          :muu (-> lammitys :muu-lammonlahde)
                          :polttoaine (if (= "kiviihiili koksi tms" (-> lammitys :lammonlahde))
                                          (str (-> lammitys :lammonlahde) ".")
                                          (-> lammitys :lammonlahde)))
        julkisivu-map (muu-select-map :muuMateriaali (-> rakenne :muuMateriaali)
                                      :julkisivumateriaali (-> rakenne :julkisivu))
        lammitystapa (-> lammitys :lammitystapa)
        huoneistot {:huoneisto (get-huoneisto-data huoneistot)}]
    {:yksilointitieto id
     :alkuHetki (to-xml-datetime  created)
     :sijaintitieto {:Sijainti {:tyhja empty-tag}}
     :rakentajatyyppi (-> kaytto :rakentajaTyyppi)
     :omistajatieto (for [m (vals (:rakennuksenOmistajat toimenpide))] (get-rakennuksen-omistaja m))
     :rakennuksenTiedot (merge {:kayttotarkoitus (-> kaytto :kayttotarkoitus)
                                :tilavuus (-> mitat :tilavuus)
                                :kokonaisala (-> mitat :kokonaisala)
                                :kellarinpinta-ala (-> mitat :kellarinpinta-ala)
                                ;:BIM empty-tag
                                :kerrosluku (-> mitat :kerrosluku)
                                :kerrosala (-> mitat :kerrosala)
                                :rakentamistapa (-> rakenne :rakentamistapa)
                                :verkostoliittymat {:sahkoKytkin (true? (-> toimenpide :verkostoliittymat :sahkoKytkin))
                                                    :maakaasuKytkin (true? (-> toimenpide :verkostoliittymat :maakaasuKytkin))
                                                    :viemariKytkin (true? (-> toimenpide :verkostoliittymat :viemariKytkin))
                                                    :vesijohtoKytkin (true? (-> toimenpide :verkostoliittymat :vesijohtoKytkin))
                                                    :kaapeliKytkin (true? (-> toimenpide :verkostoliittymat :kaapeliKytkin))}
                                :energialuokka (-> luokitus :energialuokka)
                                :energiatehokkuusluku (-> luokitus :energiatehokkuusluku)
                                :energiatehokkuusluvunYksikko (-> luokitus :energiatehokkuusluvunYksikko)
                                :paloluokka (-> luokitus :paloluokka)
                                :lammitystapa (cond (= lammitystapa "suorasahk\u00f6") "suora s\u00e4hk\u00f6"
                                                    (= lammitystapa "eiLammitysta") "ei l\u00e4mmityst\u00e4"
                                                :default lammitystapa)
                                :varusteet {:sahkoKytkin (true? (-> toimenpide :varusteet :sahkoKytkin))
                                            :kaasuKytkin (true? (-> toimenpide :varusteet :kaasuKytkin))
                                            :viemariKytkin (true? (-> toimenpide :varusteet :sahkoKytkin))
                                            :vesijohtoKytkin (true? (-> toimenpide :varusteet :vesijohtoKytkin))
                                            :lamminvesiKytkin (true? (-> toimenpide :varusteet :lamminvesiKytkin))
                                            :aurinkopaneeliKytkin (true? (-> toimenpide :varusteet :aurinkopaneeliKytkin))
                                            :hissiKytkin (true? (-> toimenpide :varusteet :hissiKytkin))
                                            :koneellinenilmastointiKytkin (true? (-> toimenpide :varusteet :koneellinenilmastointiKytkin))
                                            :saunoja (-> toimenpide :varusteet :saunoja)
                                            :vaestonsuoja (-> toimenpide :varusteet :vaestonsuoja)}
                                :liitettyJatevesijarjestelmaanKytkin (true? (-> toimenpide :varusteet :liitettyJatevesijarjestelmaanKytkin))}
                               (cond
                                 (-> toimenpide :manuaalinen_rakennusnro)
                                 {:rakennustunnus {:rakennusnro (-> toimenpide :manuaalinen_rakennusnro)
                                                   :jarjestysnumero nil
                                                   :kiinttun (:propertyId application)}}
                                 (-> toimenpide :rakennusnro)
                                 {:rakennustunnus {:rakennusnro (-> toimenpide :rakennusnro)
                                                   :jarjestysnumero nil
                                                   :kiinttun (:propertyId application)}}
                                 :default
                                 {:rakennustunnus {:jarjestysnumero nil
                                                   :kiinttun (:propertyId application)}})
                               (when kantava-rakennus-aine-map {:kantavaRakennusaine kantava-rakennus-aine-map})
                               (when lammonlahde-map {:lammonlahde lammonlahde-map})
                               (when julkisivu-map {:julkisivu julkisivu-map})
                               (when (and huoneistot (not-empty (:huoneisto huoneistot)))
                                 {:asuinhuoneistot huoneistot}))}))

(defn- get-rakennus-data [toimenpide application doc]
  {:Rakennus (get-rakennus toimenpide application doc)})

(defn- get-toimenpiteen-kuvaus [doc]
  ;Uses fi as default since krysp uses finnish in enumeration values
  {:kuvaus (with-lang "fi" (loc (str "operations." (-> doc :schema-info :op :name))))})

(defn get-uusi-toimenpide [doc application]
  (let [toimenpide (:data doc)]
    {:Toimenpide {:uusi (get-toimenpiteen-kuvaus doc)
                  :rakennustieto (get-rakennus-data toimenpide application doc)}
     :created (:created doc)}))

(defn fix-typo-in-kayttotarkotuksen-muutos [v]
  (if (= v lupapalvelu.document.schemas/kayttotarkotuksen-muutos)
    "rakennuksen p\u00e4\u00e4asiallinen k\u00e4ytt\u00f6tarkoitusmuutos"
    v))

(defn- get-rakennuksen-muuttaminen-toimenpide [rakennuksen-muuttaminen-doc application]
  (let [toimenpide (:data rakennuksen-muuttaminen-doc)]
    {:Toimenpide {:muuMuutosTyo (conj (get-toimenpiteen-kuvaus rakennuksen-muuttaminen-doc)
                                      {:perusparannusKytkin (true? (-> rakennuksen-muuttaminen-doc :data :perusparannuskytkin))}
                                      {:muutostyonLaji (fix-typo-in-kayttotarkotuksen-muutos (-> rakennuksen-muuttaminen-doc :data :muutostyolaji))})
                  :rakennustieto (get-rakennus-data toimenpide application rakennuksen-muuttaminen-doc)}
     :created (:created rakennuksen-muuttaminen-doc)}))

(defn- get-rakennuksen-laajentaminen-toimenpide [laajentaminen-doc application]
  (let [toimenpide (:data laajentaminen-doc)
        mitat (-> toimenpide :laajennuksen-tiedot :mitat )]
    {:Toimenpide {:laajennus (conj (get-toimenpiteen-kuvaus laajentaminen-doc)
                                   {:perusparannusKytkin (true? (-> laajentaminen-doc :data :laajennuksen-tiedot :perusparannuskytkin))}
                                   {:laajennuksentiedot {:tilavuus (-> mitat :tilavuus)
                                                         :kerrosala (-> mitat :kerrosala)
                                                         :kokonaisala (-> mitat :kokonaisala)
                                                         :huoneistoala (for [huoneistoala (vals (:huoneistoala mitat))]
                                                                         {:pintaAla (-> huoneistoala :pintaAla)
                                                                          :kayttotarkoitusKoodi (-> huoneistoala :kayttotarkoitusKoodi)})}})
                  :rakennustieto (get-rakennus-data toimenpide application laajentaminen-doc)}
     :created (:created laajentaminen-doc)}))

(defn- get-purku-toimenpide [purku-doc application]
  (let [toimenpide (:data purku-doc)]
    {:Toimenpide {:purkaminen (conj (get-toimenpiteen-kuvaus purku-doc)
                                   {:purkamisenSyy (-> toimenpide :poistumanSyy)}
                                   {:poistumaPvm (to-xml-date-from-string (-> toimenpide :poistumanAjankohta))})
                  :rakennustieto (update-in
                                   (get-rakennus-data toimenpide application purku-doc)
                                   [:Rakennus :rakennuksenTiedot]
                                   (fn [m]
                                     (-> m
                                       ; Cleanup top level keys that will bi nil anyway.
                                       ; Recursive strip-nils would be too much.
                                       (dissoc :verkostoliittymat)
                                       (dissoc :energialuokka )
                                       (dissoc :energiatehokkuusluku)
                                       (dissoc :energiatehokkuusluvunYksikko)
                                       (dissoc :paloluokka)
                                       (dissoc :lammitystapa)
                                       (dissoc :varusteet)
                                       (dissoc :liitettyJatevesijarjestelmaanKytkin))))}
     :created (:created purku-doc)}))

(defn get-kaupunkikuvatoimenpide [kaupunkikuvatoimenpide-doc application]
  (let [toimenpide (:data kaupunkikuvatoimenpide-doc)]
    {:Toimenpide {:kaupunkikuvaToimenpide (get-toimenpiteen-kuvaus kaupunkikuvatoimenpide-doc)
                  :rakennelmatieto {:Rakennelma {:yksilointitieto (:id kaupunkikuvatoimenpide-doc)
                                                 :alkuHetki (to-xml-datetime (:created kaupunkikuvatoimenpide-doc))
                                                 :sijaintitieto {:Sijainti {:tyhja empty-tag}}
                                                 :kokonaisala (-> toimenpide :kokonaisala)
                                                 :kuvaus {:kuvaus (-> toimenpide :kuvaus)}
                                                 :tunnus {:jarjestysnumero nil}
                                                 :kiinttun (:propertyId application)}}}
     :created (:created kaupunkikuvatoimenpide-doc)}))


(defn- get-toimenpide-with-count [toimenpide n]
  (clojure.walk/postwalk #(if (and (map? %) (contains? % :jarjestysnumero))
                            (assoc % :jarjestysnumero n)
                            %) toimenpide))


(defn- get-operations [documents application]
  (let [toimenpiteet (filter not-empty (concat (map #(get-uusi-toimenpide % application) (:uusiRakennus documents))
                                               (map #(get-uusi-toimenpide % application) (:uusi-rakennus-ei-huoneistoa documents))
                                               (map #(get-rakennuksen-muuttaminen-toimenpide % application) (:rakennuksen-muuttaminen documents))
                                               (map #(get-rakennuksen-muuttaminen-toimenpide % application) (:rakennuksen-muuttaminen-ei-huoneistoja documents))
                                               (map #(get-rakennuksen-muuttaminen-toimenpide % application) (:rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia documents))
                                               (map #(get-rakennuksen-laajentaminen-toimenpide % application) (:rakennuksen-laajentaminen documents))
                                               (map #(get-purku-toimenpide % application) (:purkaminen documents))
                                               (map #(get-kaupunkikuvatoimenpide % application) (:kaupunkikuvatoimenpide documents))))
        toimenpiteet (map get-toimenpide-with-count toimenpiteet (range 1 9999))]
    (not-empty (sort-by :created toimenpiteet))))


(defn- get-lisatiedot [documents lang]
  (let [lisatiedot (:data (first (:lisatiedot documents)))
        aloitusoikeus (:data (first (:aloitusoikeus documents)))]
    {:Lisatiedot {:asioimiskieli (if (= lang "sv")
                                   "ruotsi"
                                   "suomi")}}))

(defn- get-asian-tiedot [documents]
  (let [maisematyo_documents (:maisematyo documents)
        hankkeen-kuvaus-doc (or (:hankkeen-kuvaus documents) (:hankkeen-kuvaus-minimum documents) (:aloitusoikeus documents))
        asian-tiedot (:data (first hankkeen-kuvaus-doc))
        maisematyo_kuvaukset (for [maisematyo_doc maisematyo_documents]
                               (str "\n\n" (:kuvaus (get-toimenpiteen-kuvaus maisematyo_doc))
                                 ":" (-> maisematyo_doc :data :kuvaus)))
        r {:Asiantiedot {:rakennusvalvontaasianKuvaus (str (-> asian-tiedot :kuvaus)
                                                        (apply str maisematyo_kuvaukset))}}]
    (if (:poikkeamat asian-tiedot)
      (assoc-in r [:Asiantiedot :vahainenPoikkeaminen] (or (-> asian-tiedot :poikkeamat) empty-tag))
      r)))

(defn- get-kayttotapaus [documents toimenpiteet]
  (if (and (contains? documents :maisematyo) (empty? toimenpiteet))
      "Uusi maisematy\u00f6hakemus"
      "Uusi hakemus"))

(defn application-to-canonical
  "Transforms application mongodb-document to canonical model."
  [application lang]
  (let [application (tools/unwrapped application)
        link-permit-data (first (:linkPermitData application))
        documents (documents-by-type-without-blanks application)
        toimenpiteet (get-operations documents application)
        operation-name (-> application :operations first :name)
        canonical {:Rakennusvalvonta
                   {:toimituksenTiedot (toimituksen-tiedot application lang)
                    :rakennusvalvontaAsiatieto
                    {:RakennusvalvontaAsia
                     {:kasittelynTilatieto (get-state application)
                      :luvanTunnisteTiedot (lupatunnus application)
                      :osapuolettieto (osapuolet documents (:neighbors application) lang)
                      :kayttotapaus (if (= "muutoslupa" (:permitSubtype application))
                                      "Rakentamisen aikainen muutos"
                                      (condp = operation-name
                                        "tyonjohtajan-nimeaminen" "Uuden ty\u00f6njohtajan nime\u00e4minen"
                                        "suunnittelijan-nimeaminen" "Uuden suunnittelijan nime\u00e4minen"
                                        "jatkoaika" "Jatkoaikahakemus"
                                        "aloitusoikeus" "Uusi aloitusoikeus"
                                        (get-kayttotapaus documents toimenpiteet)))
                      :asianTiedot (get-asian-tiedot documents)
                      :lisatiedot (get-lisatiedot documents lang)}}}}
        canonical (if link-permit-data
                    (assoc-in canonical [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :viitelupatieto]
                      (get-viitelupatieto link-permit-data))
                    canonical)
        canonical (if-not (or (= operation-name "tyonjohtajan-nimeaminen")
                            (= operation-name "suunnittelijan-nimeaminen")
                            (= operation-name "jatkoaika"))
                    (update-in canonical [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia]
                      assoc-when
                      :rakennuspaikkatieto (get-bulding-places (:rakennuspaikka documents) application)
                      :toimenpidetieto toimenpiteet
                      :lausuntotieto (get-statements (:statements application)))
                    canonical)]
    canonical))

(defn katselmusnimi-to-type [nimi tyyppi]
  (if (= :tarkastus tyyppi)
    "muu tarkastus"
    (case nimi
      "Aloitusilmoitus" "ei tiedossa"
      "muu katselmus" "muu katselmus"
      "muu tarkastus" "muu tarkastus"
      "aloituskokous" "aloituskokous"
      "rakennuksen paikan merkitseminen" "rakennuksen paikan merkitseminen"
      "rakennuksen paikan tarkastaminen" "rakennuksen paikan tarkastaminen"
      "pohjakatselmus" "pohjakatselmus"
      "rakennekatselmus" "rakennekatselmus"
      "l\u00e4mp\u00f6-, vesi- ja ilmanvaihtolaitteiden katselmus" "l\u00e4mp\u00f6-, vesi- ja ilmanvaihtolaitteiden katselmus"
      "osittainen loppukatselmus" "osittainen loppukatselmus"
      "loppukatselmus" "loppukatselmus"
      "ei tiedossa")))

(defn katselmus-kayttotapaus [nimi tyyppi]
  (if (= nimi "Aloitusilmoitus")
    "Aloitusilmoitus"
    (if (= tyyppi :katselmus)
      "Uusi katselmus"
      "Uusi tarkastus")))

(defn katselmus-canonical [application lang task-id task-name pitoPvm buildings user katselmuksen-nimi tyyppi osittainen pitaja lupaehtona huomautukset lasnaolijat poikkeamat]
  (let [application (tools/unwrapped application)
        documents (documents-by-type-without-blanks application)
        katselmus (strip-nils
                    (merge
                      {:pitoPvm (if (number? pitoPvm) (to-xml-date pitoPvm) (to-xml-date-from-string pitoPvm))
                       :katselmuksenLaji (katselmusnimi-to-type katselmuksen-nimi tyyppi)
                       :vaadittuLupaehtonaKytkin (true? lupaehtona)
                       :tarkastuksenTaiKatselmuksenNimi task-name
                       :osittainen osittainen
                       :lasnaolijat lasnaolijat
                       :pitaja pitaja
                       :poikkeamat poikkeamat}
                      (when task-id {:muuTunnustieto {:MuuTunnus {:tunnus task-id :sovellus "Lupapiste"}}}) ; v 2.1.3
                      (when (seq buildings)
                        {:rakennustunnus (let [building (-> buildings first :rakennus)]
                                           (merge
                                             (select-keys building [:jarjestysnumero :kiinttun])
                                             (when-not (s/blank? (:rakennusnro building)) {:rakennusnro (:rakennusnro building)}))) ; v2.1.2
                         :katselmuksenRakennustieto (map #(let [building (:rakennus %)
                                                                building-canonical (merge
                                                                                     (select-keys building [:jarjestysnumero :kiinttun])
                                                                                     (when-not (s/blank? (:rakennusnro building)) {:rakennusnro (:rakennusnro building)})
                                                                                     {:katselmusOsittainen (get-in % [:tila :tila])
                                                                                      :kayttoonottoKytkin  (get-in % [:tila :kayttoonottava])})]
                                                            {:KatselmuksenRakennus building-canonical}) buildings)}) ; v2.1.3
                      (when (:kuvaus huomautukset) {:huomautukset {:huomautus (reduce-kv
                                                                                (fn [m k v] (assoc m k (to-xml-date-from-string v)))
                                                                                (select-keys huomautukset [:kuvaus :toteaja])
                                                                                (select-keys huomautukset [:maaraAika :toteamisHetki]))}})))
        canonical {:Rakennusvalvonta
                   {:toimituksenTiedot (toimituksen-tiedot application lang)
                    :rakennusvalvontaAsiatieto
                    {:RakennusvalvontaAsia
                     {:kasittelynTilatieto (get-state application)
                      :luvanTunnisteTiedot (lupatunnus application)
                      ; Osapuoli is not required in KRYSP 2.1.3
                      :osapuolettieto {:Osapuolet {:osapuolitieto {:Osapuoli {:kuntaRooliKoodi "Ilmoituksen tekij\u00e4"
                                                                              :henkilo {:nimi {:etunimi (:firstName user)
                                                                                               :sukunimi (:lastName user)}
                                                                                        :osoite {:osoitenimi {:teksti (or (:street user) "")}
                                                                                                 :postitoimipaikannimi (:city user)
                                                                                                 :postinumero (:zip user)}
                                                                                         :sahkopostiosoite (:email user)
                                                                                         :puhelin (:phone user)}}}}}
                      :katselmustieto {:Katselmus katselmus}
                      :lisatiedot (get-lisatiedot (:lisatiedot documents) lang)
                      :kayttotapaus (katselmus-kayttotapaus katselmuksen-nimi tyyppi)
                      }}}}]
    canonical))

(defn unsent-attachments-to-canonical [application lang]
  (let [application (tools/unwrapped application)
        documents (documents-by-type-without-blanks application)
        hakija-info (-> (filter #(= (-> % :Osapuoli :VRKrooliKoodi) "hakija") (get-parties documents))
                      first
                      (assoc-in [:Osapuoli :kuntaRooliKoodi] "ei tiedossa"))
        canonical {:Rakennusvalvonta
                   {:toimituksenTiedot (toimituksen-tiedot application lang)
                    :rakennusvalvontaAsiatieto
                    {:RakennusvalvontaAsia
                     {:kasittelynTilatieto (get-state application)
                      :luvanTunnisteTiedot (lupatunnus application)
                      :osapuolettieto {:Osapuolet {:osapuolitieto hakija-info}}
                      :lisatiedot (get-lisatiedot (:lisatiedot documents) lang)
                      :kayttotapaus "Liitetiedoston lis\u00e4ys"
                      }}}}]
    canonical))
