(ns lupapalvelu.document.rakennuslupa-canonical
  (:require [clojure.java.io :as io]
            [clojure.xml :as xml]
            [clojure.string :as s]
            [sade.core :refer [now]]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.document.canonical-common :refer :all]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.schemas :as schemas]))

(defn- get-huoneisto-data [huoneistot]
  (let [huoneistot (vals (into (sorted-map) huoneistot))
        huoneistot (filter :muutostapa huoneistot)]
    (for [huoneisto huoneistot
         :let [huoneistonumero (-> huoneisto :huoneistonumero)
               huoneistoPorras (-> huoneisto :porras)
               jakokirjain (-> huoneisto :jakokirjain)]
         :when (seq huoneisto)]
     (merge {:muutostapa (-> huoneisto :muutostapa)
             :huoneluku (-> huoneisto :huoneluku)
             :keittionTyyppi (-> huoneisto :keittionTyyppi)
             :huoneistoala (ss/replace (-> huoneisto :huoneistoala) "," ".")
             :varusteet {:WCKytkin (true? (-> huoneisto :WCKytkin))
                         :ammeTaiSuihkuKytkin (true? (-> huoneisto :ammeTaiSuihkuKytkin))
                         :saunaKytkin (true? (-> huoneisto :saunaKytkin))
                         :parvekeTaiTerassiKytkin (true? (-> huoneisto :parvekeTaiTerassiKytkin))
                         :lamminvesiKytkin (true? (-> huoneisto :lamminvesiKytkin))}
             :huoneistonTyyppi (-> huoneisto :huoneistoTyyppi)}
            (when (ss/numeric? huoneistonumero)
              {:huoneistotunnus
               (merge {:huoneistonumero (format "%03d" (util/->int (ss/remove-leading-zeros huoneistonumero)))}
                      (when (not-empty huoneistoPorras) {:porras (ss/upper-case huoneistoPorras)})
                      (when (not-empty jakokirjain) {:jakokirjain (ss/lower-case jakokirjain)}))})))))

(defn- get-rakennuksen-omistaja [omistaja]
  (when-let [osapuoli (get-osapuoli-data omistaja :rakennuksenomistaja)]
    {:Omistaja osapuoli}))

(defn- operation-description [{primary :primaryOperation secondaries :secondaryOperations} op-id]
  (:description (util/find-by-id op-id (cons primary secondaries)) ))

(defn get-rakennustunnus [unwrapped-doc-data application {{op-id :id} :op}]
  (let [{:keys [tunnus rakennusnro valtakunnallinenNumero manuaalinen_rakennusnro]} unwrapped-doc-data
        description-parts (remove ss/blank? [tunnus (operation-description application op-id)])
        defaults (util/assoc-when-pred {:jarjestysnumero nil
                                        :kiinttun (:propertyId application)
                                        :muuTunnustieto [{:MuuTunnus {:tunnus op-id :sovellus "toimenpideId"}}
                                                         {:MuuTunnus {:tunnus op-id :sovellus "Lupapiste"}}]}
                   util/not-empty-or-nil?
                   :rakennusnro rakennusnro
                   :rakennuksenSelite (ss/join ": " description-parts)
                   :valtakunnallinenNumero valtakunnallinenNumero)]
    (if manuaalinen_rakennusnro
      (assoc defaults :rakennusnro manuaalinen_rakennusnro)
      defaults)))

(defn- get-rakennus [application {id :id created :created info :schema-info toimenpide :data :as doc}]
  (let [{:keys [kaytto mitat rakenne lammitys luokitus huoneistot]} toimenpide
        kuvaus (:toimenpiteenKuvaus toimenpide)
        kantava-rakennus-aine-map (muu-select-map :muuRakennusaine (:muuRakennusaine rakenne)
                                                  :rakennusaine (:kantavaRakennusaine rakenne))
        lammonlahde-map (muu-select-map
                          :muu (-> lammitys :muu-lammonlahde)
                          :polttoaine (if (= "kiviihiili koksi tms" (-> lammitys :lammonlahde))
                                        (str (-> lammitys :lammonlahde) ".")
                                        (-> lammitys :lammonlahde)))
        julkisivu-map (muu-select-map :muuMateriaali (:muuMateriaali rakenne)
                                      :julkisivumateriaali (:julkisivu rakenne))
        lammitystapa (:lammitystapa lammitys)
        huoneistot {:huoneisto (get-huoneisto-data huoneistot)}
        vaestonsuoja-value (ss/trim (get-in toimenpide [:varusteet :vaestonsuoja]))
        vaestonsuoja (when (ss/numeric? vaestonsuoja-value) (util/->int vaestonsuoja-value))
        rakennuksen-tiedot-basic-info {:kayttotarkoitus (:kayttotarkoitus kaytto)
                                       :rakentamistapa (:rakentamistapa rakenne)
                                       :verkostoliittymat {:sahkoKytkin (true? (-> toimenpide :verkostoliittymat :sahkoKytkin))
                                                           :maakaasuKytkin (true? (-> toimenpide :verkostoliittymat :maakaasuKytkin))
                                                           :viemariKytkin (true? (-> toimenpide :verkostoliittymat :viemariKytkin))
                                                           :vesijohtoKytkin (true? (-> toimenpide :verkostoliittymat :vesijohtoKytkin))
                                                           :kaapeliKytkin (true? (-> toimenpide :verkostoliittymat :kaapeliKytkin))}
                                       :lammitystapa (cond
                                                       (= lammitystapa "suorasahk\u00f6") "suora s\u00e4hk\u00f6"
                                                       (= lammitystapa "eiLammitysta") "ei l\u00e4mmityst\u00e4"
                                                       :default lammitystapa)
                                       :varusteet {:sahkoKytkin (true? (-> toimenpide :varusteet :sahkoKytkin))
                                                   :kaasuKytkin (true? (-> toimenpide :varusteet :kaasuKytkin))
                                                   :viemariKytkin (true? (-> toimenpide :varusteet :viemariKytkin))
                                                   :vesijohtoKytkin (true? (-> toimenpide :varusteet :vesijohtoKytkin))
                                                   :lamminvesiKytkin (true? (-> toimenpide :varusteet :lamminvesiKytkin))
                                                   :aurinkopaneeliKytkin (true? (-> toimenpide :varusteet :aurinkopaneeliKytkin))
                                                   :hissiKytkin (true? (-> toimenpide :varusteet :hissiKytkin))
                                                   :koneellinenilmastointiKytkin (true? (-> toimenpide :varusteet :koneellinenilmastointiKytkin))
                                                   :saunoja (-> toimenpide :varusteet :saunoja)
                                                   :vaestonsuoja vaestonsuoja}
                                       :liitettyJatevesijarjestelmaanKytkin (true? (-> toimenpide :varusteet :liitettyJatevesijarjestelmaanKytkin))
                                       :rakennustunnus (get-rakennustunnus toimenpide application info)}
        rakennuksen-tiedot (merge
                             (select-keys mitat [:tilavuus :kokonaisala :kellarinpinta-ala :kerrosluku :kerrosala :rakennusoikeudellinenKerrosala])
                             (select-keys luokitus [:energialuokka :energiatehokkuusluku :paloluokka])
                             (when-not (ss/blank? (:energiatehokkuusluku luokitus))
                               (select-keys luokitus [:energiatehokkuusluvunYksikko]))
                             (when (util/not-empty-or-nil? (:huoneisto huoneistot))
                               {:asuinhuoneistot huoneistot})
                             (util/assoc-when-pred rakennuksen-tiedot-basic-info util/not-empty-or-nil?
                               :kantavaRakennusaine kantava-rakennus-aine-map
                               :lammonlahde lammonlahde-map
                               :julkisivu julkisivu-map))]

    (util/assoc-when-pred {:yksilointitieto (-> info :op :id)
                           :alkuHetki (util/to-xml-datetime  created)
                           :sijaintitieto {:Sijainti {:tyhja empty-tag}}
                           :rakentajatyyppi (:rakentajaTyyppi kaytto)
                           :rakennuksenTiedot rakennuksen-tiedot}
      util/not-empty-or-nil?
      :omistajatieto (remove nil? (for [m (vals (:rakennuksenOmistajat toimenpide))] (get-rakennuksen-omistaja m))))))

(defn- get-rakennus-data [application doc]
  {:Rakennus (get-rakennus application doc)})

(defn- get-toimenpiteen-kuvaus [doc]
  ;Uses fi as default since krysp uses finnish in enumeration values
  {:kuvaus (i18n/localize "fi" (str "operations." (-> doc :schema-info :op :name)))})

(defn get-uusi-toimenpide [doc application]
  {:Toimenpide {:uusi (get-toimenpiteen-kuvaus doc)
                :rakennustieto (get-rakennus-data application doc)}
   :created (:created doc)})

(defn fix-typo-in-kayttotarkotuksen-muutos [v]
  (if (= v lupapalvelu.document.schemas/kayttotarkotuksen-muutos)
    "rakennuksen p\u00e4\u00e4asiallinen k\u00e4ytt\u00f6tarkoitusmuutos"
    v))

(defn- get-rakennuksen-muuttaminen-toimenpide [rakennuksen-muuttaminen-doc application]
  {:Toimenpide {:muuMuutosTyo (conj (get-toimenpiteen-kuvaus rakennuksen-muuttaminen-doc)
                                    {:perusparannusKytkin (true? (-> rakennuksen-muuttaminen-doc :data :perusparannuskytkin))}
                                    {:muutostyonLaji (fix-typo-in-kayttotarkotuksen-muutos (-> rakennuksen-muuttaminen-doc :data :muutostyolaji))})
                :rakennustieto (get-rakennus-data application rakennuksen-muuttaminen-doc)}
   :created (:created rakennuksen-muuttaminen-doc)})

(defn- get-rakennuksen-laajentaminen-toimenpide [laajentaminen-doc application]
  {:Toimenpide {:laajennus (conj (get-toimenpiteen-kuvaus laajentaminen-doc)
                                 {:perusparannusKytkin (true? (get-in laajentaminen-doc [:data :laajennuksen-tiedot :perusparannuskytkin]))}
                                 {:laajennuksentiedot (-> (get-in laajentaminen-doc [:data :laajennuksen-tiedot :mitat])
                                                          (select-keys [:tilavuus :kerrosala :kokonaisala :rakennusoikeudellinenKerrosala :huoneistoala])
                                                          (update :huoneistoala #(map select-keys (vals %) (repeat [:pintaAla :kayttotarkoitusKoodi]))))})
                :rakennustieto (get-rakennus-data application laajentaminen-doc)}
   :created (:created laajentaminen-doc)})

(defn- get-purku-toimenpide [{data :data :as purku-doc} application]
  {:Toimenpide {:purkaminen (conj (get-toimenpiteen-kuvaus purku-doc)
                                 {:purkamisenSyy (:poistumanSyy data)}
                                 {:poistumaPvm (util/to-xml-date-from-string (:poistumanAjankohta data))})
                :rakennustieto (update-in
                                 (get-rakennus-data application purku-doc)
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
   :created (:created purku-doc)})

(defn get-kaupunkikuvatoimenpide [kaupunkikuvatoimenpide-doc application]
  (let [toimenpide (:data kaupunkikuvatoimenpide-doc)]
    {:Toimenpide {:kaupunkikuvaToimenpide (get-toimenpiteen-kuvaus kaupunkikuvatoimenpide-doc)
                  :rakennelmatieto {:Rakennelma {:yksilointitieto (-> kaupunkikuvatoimenpide-doc :schema-info :op :id)
                                                 :alkuHetki (util/to-xml-datetime (:created kaupunkikuvatoimenpide-doc))
                                                 :sijaintitieto {:Sijainti {:tyhja empty-tag}}
                                                 :kokonaisala (-> toimenpide :kokonaisala)
                                                 :kayttotarkoitus (-> toimenpide :kayttotarkoitus)
                                                 :kuvaus {:kuvaus (-> toimenpide :kuvaus)}
                                                 :tunnus {:jarjestysnumero nil}
                                                 :kiinttun (:propertyId application)}}}
     :created (:created kaupunkikuvatoimenpide-doc)}))


(defn get-maalampokaivo [kaupunkikuvatoimenpide-doc application]
  (-> (get-kaupunkikuvatoimenpide kaupunkikuvatoimenpide-doc application)
      (util/dissoc-in
       [:Toimenpide :rakennelmatieto :Rakennelma :kokonaisala])
      (assoc-in
       [:kayttotarkoitus] "Maal\u00e4mp\u00f6pumppuj\u00e4rjestelm\u00e4")))

(defn- get-toimenpide-with-count [toimenpide n]
  (clojure.walk/postwalk #(if (and (map? %) (contains? % :jarjestysnumero))
                            (assoc % :jarjestysnumero n)
                            %) toimenpide))


(defn- get-operations [documents-by-type application]
  (let [toimenpiteet (filter not-empty (concat (map #(get-uusi-toimenpide % application) (:uusiRakennus documents-by-type))
                                               (map #(get-uusi-toimenpide % application) (:uusi-rakennus-ei-huoneistoa documents-by-type))
                                               (map #(get-rakennuksen-muuttaminen-toimenpide % application) (:rakennuksen-muuttaminen documents-by-type))
                                               (map #(get-rakennuksen-muuttaminen-toimenpide % application) (:rakennuksen-muuttaminen-ei-huoneistoja documents-by-type))
                                               (map #(get-rakennuksen-muuttaminen-toimenpide % application) (:rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia documents-by-type))
                                               (map #(get-rakennuksen-laajentaminen-toimenpide % application) (:rakennuksen-laajentaminen documents-by-type))
                                               (map #(get-rakennuksen-laajentaminen-toimenpide % application) (:rakennuksen-laajentaminen-ei-huoneistoja documents-by-type))
                                               (map #(get-purku-toimenpide % application) (:purkaminen documents-by-type))
                                               (map #(get-kaupunkikuvatoimenpide % application) (:kaupunkikuvatoimenpide documents-by-type))
                                               (map #(get-kaupunkikuvatoimenpide % application) (:kaupunkikuvatoimenpide-ei-tunnusta documents-by-type))
                                               (map #(get-maalampokaivo % application) (:maalampokaivo documents-by-type))))
        toimenpiteet (map get-toimenpide-with-count toimenpiteet (range 1 9999))]
    (not-empty (sort-by :created toimenpiteet))))


(defn- get-lisatiedot [documents-by-type lang]
  {:Lisatiedot {:asioimiskieli (case lang
                                 "sv" "ruotsi"
                                 "suomi")}})

(defn- get-asian-tiedot [documents-by-type]
  (let [maisematyo_documents (:maisematyo documents-by-type)
        hankkeen-kuvaus-doc (or (:hankkeen-kuvaus documents-by-type) (:hankkeen-kuvaus-rakennuslupa documents-by-type) (:hankkeen-kuvaus-minimum documents-by-type) (:aloitusoikeus documents-by-type))
        asian-tiedot (:data (first hankkeen-kuvaus-doc))
        maisematyo_kuvaukset (for [maisematyo_doc maisematyo_documents]
                               (str "\n\n" (:kuvaus (get-toimenpiteen-kuvaus maisematyo_doc))
                                 ":" (-> maisematyo_doc :data :kuvaus)))
        r {:Asiantiedot {:rakennusvalvontaasianKuvaus (str (-> asian-tiedot :kuvaus)
                                                        (apply str maisematyo_kuvaukset))}}]
    (if (:poikkeamat asian-tiedot)
      (assoc-in r [:Asiantiedot :vahainenPoikkeaminen] (or (-> asian-tiedot :poikkeamat) empty-tag))
      r)))

(defn- get-kayttotapaus [documents-by-type toimenpiteet]
  (if (and (contains? documents-by-type :maisematyo) (empty? toimenpiteet))
      "Uusi maisematy\u00f6hakemus"
      "Uusi hakemus"))

(defn- get-hankkeen-vaativuus [documents-by-type]
  (get-in documents-by-type [:hankkeen-kuvaus-rakennuslupa 0 :data :hankkeenVaativuus]))

(defn application-to-canonical
  "Transforms application mongodb-document to canonical model."
  [application lang]
  (let [application (tools/unwrapped application)
        link-permits (:linkPermitData application)
        documents-by-type (documents-by-type-without-blanks application)
        toimenpiteet (get-operations documents-by-type application)
        operation-name (-> application :primaryOperation :name)
        canonical {:Rakennusvalvonta
                   {:toimituksenTiedot (toimituksen-tiedot application lang)
                    :rakennusvalvontaAsiatieto
                    {:RakennusvalvontaAsia
                     {:kasittelynTilatieto (get-state application)
                      :luvanTunnisteTiedot (lupatunnus application)
                      :osapuolettieto (osapuolet application documents-by-type lang)
                      :kayttotapaus (if (= "muutoslupa" (:permitSubtype application))
                                      "Rakentamisen aikainen muutos"
                                      (condp = operation-name
                                        "tyonjohtajan-nimeaminen" "Uuden ty\u00f6njohtajan nime\u00e4minen"
                                        "tyonjohtajan-nimeaminen-v2" "Uuden ty\u00f6njohtajan nime\u00e4minen"
                                        "suunnittelijan-nimeaminen" "Uuden suunnittelijan nime\u00e4minen"
                                        "jatkoaika" "Jatkoaikahakemus"
                                        "raktyo-aloit-loppuunsaat" "Jatkoaikahakemus"
                                        "aloitusoikeus" "Uusi aloitusoikeus"
                                        (get-kayttotapaus documents-by-type toimenpiteet)))
                      :asianTiedot (get-asian-tiedot documents-by-type)
                      :lisatiedot (get-lisatiedot documents-by-type lang)
                      :hankkeenVaativuus (get-hankkeen-vaativuus documents-by-type)}}}}
        canonical (if (not-empty link-permits)
                    (assoc-in canonical [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :viitelupatieto]
                              (map get-viitelupatieto link-permits))
                    canonical)
        canonical (if-not (or (= operation-name "tyonjohtajan-nimeaminen")
                            (= operation-name "suunnittelijan-nimeaminen")
                            (= operation-name "jatkoaika")
                            (= operation-name "raktyo-aloit-loppuunsaat"))
                    (update-in canonical [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia]
                      util/assoc-when-pred util/not-empty-or-nil?
                      :rakennuspaikkatieto (get-bulding-places (concat (:rakennuspaikka documents-by-type)
                                                                       (:rakennuspaikka-ilman-ilmoitusta documents-by-type))
                                                               application)
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

(defn katselmus-canonical [application lang task-id task-name pitoPvm buildings user katselmuksen-nimi tyyppi osittainen pitaja lupaehtona huomautukset lasnaolijat poikkeamat tiedoksianto]
  (let [application (tools/unwrapped application)
        documents-by-type (documents-by-type-without-blanks application)
        katselmusTyyppi (katselmusnimi-to-type katselmuksen-nimi tyyppi)
        katselmus (util/strip-nils
                    (merge
                      {:pitoPvm (if (number? pitoPvm) (util/to-xml-date pitoPvm) (util/to-xml-date-from-string pitoPvm))
                       :katselmuksenLaji katselmusTyyppi
                       :vaadittuLupaehtonaKytkin (true? lupaehtona)
                       :osittainen osittainen
                       :lasnaolijat lasnaolijat
                       :pitaja pitaja
                       :poikkeamat poikkeamat
                       :verottajanTvLlKytkin tiedoksianto
                       :tarkastuksenTaiKatselmuksenNimi (ss/trim task-name)}
                      (when task-id {:muuTunnustieto {:MuuTunnus {:tunnus task-id :sovellus "Lupapiste"}}}) ; v 2.1.3
                      (when (seq buildings)
                        {:rakennustunnus (let [building (-> buildings first :rakennus)]
                                           (util/assoc-when-pred
                                             (select-keys building [:jarjestysnumero :kiinttun]) util/not-empty-or-nil?
                                             :rakennusnro (:rakennusnro building)
                                             :valtakunnallinenNumero (:valtakunnallinenNumero building)  ; v2.1.2
                                             ))
                         :katselmuksenRakennustieto (map #(let [building (:rakennus %)
                                                                building-canonical (merge
                                                                                     (select-keys building [:jarjestysnumero :kiinttun])
                                                                                     (when-not (s/blank? (:rakennusnro building)) {:rakennusnro (:rakennusnro building)})
                                                                                     (when-not (s/blank? (:valtakunnallinenNumero building)) {:valtakunnallinenNumero (:valtakunnallinenNumero building)})
                                                                                     {:katselmusOsittainen (get-in % [:tila :tila])
                                                                                      :kayttoonottoKytkin  (get-in % [:tila :kayttoonottava])})
                                                                building-canonical (if (s/blank? (:kiinttun building-canonical))
                                                                                     (assoc building-canonical :kiinttun (:propertyId application))
                                                                                     building-canonical)
                                                                building-canonical (util/assoc-when-pred
                                                                                    building-canonical util/not-empty-or-nil?
                                                                                    :muuTunnustieto (when-let [op-id (:operationId building)]
                                                                                                      [{:MuuTunnus {:tunnus op-id :sovellus "toimenpideId"}}
                                                                                                       {:MuuTunnus {:tunnus op-id :sovellus "Lupapiste"}}])
                                                                                    :rakennuksenSelite (:description building)) ; v2.2.0
                                                                ]
                                                            {:KatselmuksenRakennus building-canonical}) buildings)}) ; v2.1.3
                      (when (:kuvaus huomautukset) {:huomautukset {:huomautus (reduce-kv
                                                                                (fn [m k v] (if-not (ss/blank? v)
                                                                                              (assoc m k (util/to-xml-date-from-string v))
                                                                                              m))
                                                                                {:kuvaus (if (ss/blank? (:kuvaus huomautukset)) "-" (:kuvaus huomautukset))
                                                                                 :toteaja (:toteaja huomautukset)}
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
                      :lisatiedot (get-lisatiedot (:lisatiedot documents-by-type) lang)
                      :kayttotapaus (katselmus-kayttotapaus katselmuksen-nimi tyyppi)
                      }}}}]
    canonical))

(defn unsent-attachments-to-canonical [application lang]
  (let [application (tools/unwrapped application)
        documents-by-type (documents-by-type-without-blanks application)]
    {:Rakennusvalvonta
     {:toimituksenTiedot (toimituksen-tiedot application lang)
      :rakennusvalvontaAsiatieto
      {:RakennusvalvontaAsia
       {:kasittelynTilatieto (get-state application)
        :luvanTunnisteTiedot (lupatunnus application)
        :lisatiedot (get-lisatiedot (:lisatiedot documents-by-type) lang)
        :kayttotapaus "Liitetiedoston lis\u00e4ys"}}}}))
