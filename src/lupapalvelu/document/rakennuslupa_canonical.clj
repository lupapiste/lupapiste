(ns lupapalvelu.document.rakennuslupa-canonical
  (:require [clojure.walk :as walk]
            [swiss.arrows :refer [-<>]]
            [sade.core :refer [now]]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.application :as app]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.document.canonical-common :refer :all]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.organization :as org]))

(defn- muutostapa
  "If muutostapa is hidden and has a default value via schema the
  latter is always used in the canonical model. For example, for the
  new buildings the only allowed muutostapa is Lis\u00e4ys."
  [huoneisto schema]
  (let [{:keys [hidden default]} (->> schema
                                      :body
                                      (util/find-by-key :name "muutostapa"))]
    (if (and hidden (ss/not-blank? default))
      default
      (:muutostapa huoneisto))))

(defn- schema-body-required-fields [schema]
  (->> (:body schema)
       (filter :required)
       (map (comp keyword :name))))

(defn- required-fields-have-value? [schema huoneisto]
  (let [required-fields (schema-body-required-fields schema)]
    (every? #(some? (get huoneisto %)) required-fields)))

(defn- get-huoneistot-schema [schema-name]
  (->> {:name schema-name}
       schemas/get-schema
       :body
       (util/find-by-key :name "huoneistot")))

(defn- get-huoneisto-data [huoneistot schema-name]
  (let [schema     (get-huoneistot-schema schema-name)
        huoneistot (vals (into (sorted-map) huoneistot))]
    (for [huoneisto huoneistot
          :let      [huoneistonumero (-> huoneisto :huoneistonumero)
                     huoneistoPorras (-> huoneisto :porras)
                     jakokirjain (-> huoneisto :jakokirjain)
                     muutostapa (muutostapa huoneisto schema)
                     required-filled? (required-fields-have-value? schema huoneisto)]
          :when     (and muutostapa (seq huoneisto) required-filled?)]
      (merge {:muutostapa       muutostapa
              :huoneluku        (-> huoneisto :huoneluku)
              :keittionTyyppi   (-> huoneisto :keittionTyyppi)
              :huoneistoala     (ss/replace (-> huoneisto :huoneistoala) "," ".")
              :varusteet        {:WCKytkin                (true? (-> huoneisto :WCKytkin))
                                 :ammeTaiSuihkuKytkin     (true? (-> huoneisto :ammeTaiSuihkuKytkin))
                                 :saunaKytkin             (true? (-> huoneisto :saunaKytkin))
                                 :parvekeTaiTerassiKytkin (true? (-> huoneisto :parvekeTaiTerassiKytkin))
                                 :lamminvesiKytkin        (true? (-> huoneisto :lamminvesiKytkin))}
              :huoneistonTyyppi (-> huoneisto :huoneistoTyyppi)}
            (when (ss/numeric? huoneistonumero)
              {:huoneistotunnus
               (merge {:huoneistonumero (format "%03d" (util/->int (ss/remove-leading-zeros huoneistonumero)))}
                      (when (not-empty huoneistoPorras) {:porras (ss/upper-case huoneistoPorras)})
                      (when (not-empty jakokirjain) {:jakokirjain (ss/lower-case jakokirjain)}))})))))

(defn get-huoneistot-lkm [huoneistot]
  (count (filter #(= "lis\u00e4ys" (:muutostapa %)) huoneistot)))

(defn get-huoneistot-pintaala [huoneistot]
  (->> (filter #(= "lis\u00e4ys" (:muutostapa %)) huoneistot)
       (map :huoneistoala)
       (map #(util/->double %))
       (apply +)))

(defn- get-rakennuksen-omistaja [omistaja]
  (when-let [osapuoli (get-osapuoli-data omistaja :rakennuksenomistaja)]
    {:Omistaja osapuoli}))

(defn- operation-description [{primary :primaryOperation secondaries :secondaryOperations} op-id]
  (:description (util/find-by-id op-id (cons primary secondaries)) ))

(defn get-rakennustunnus [unwrapped-doc-data application {{op-id :id} :op}]
  (let [{:keys [buildingId tunnus rakennusnro valtakunnallinenNumero manuaalinen_rakennusnro]} unwrapped-doc-data
        description-parts (remove ss/blank? [tunnus (operation-description application op-id)])
        defaults (util/assoc-when-pred {:jarjestysnumero nil
                                        :kiinttun (:propertyId application)
                                        :muuTunnustieto [{:MuuTunnus {:tunnus op-id :sovellus "toimenpideId"}}
                                                         {:MuuTunnus {:tunnus op-id :sovellus "Lupapiste"}}]}
                   util/not-empty-or-nil?
                   :rakennusnro rakennusnro
                   :rakennuksenSelite (ss/join ": " description-parts)
                   :valtakunnallinenNumero valtakunnallinenNumero)]
    (if (and (ss/not-blank? manuaalinen_rakennusnro) (= buildingId "other"))
      (assoc defaults :rakennusnro manuaalinen_rakennusnro)
      defaults)))

(defn- get-rakennus [application {created :created info :schema-info toimenpide :data}]
  (let [{:keys [kaytto mitat rakenne lammitys luokitus huoneistot]} toimenpide
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
        huoneistot-data (get-huoneisto-data huoneistot (:name info))
        huoneistot (if (org/pate-scope? application)
                     {:huoneisto huoneistot-data
                      :asuntojenPintaala (get-huoneistot-pintaala huoneistot-data)
                      :asuntojenLkm (get-huoneistot-lkm huoneistot-data)}
                     {:huoneisto huoneistot-data})
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
                                                   :saunoja (-> toimenpide :varusteet :saunoja positive-integer)
                                                   :vaestonsuoja vaestonsuoja}
                                       :liitettyJatevesijarjestelmaanKytkin (true? (-> toimenpide :varusteet :liitettyJatevesijarjestelmaanKytkin))
                                       :rakennustunnus (get-rakennustunnus toimenpide application info)}
        rakennuksen-tiedot (merge
                            (-<> mitat
                                 (select-keys [:tilavuus :kokonaisala :kellarinpinta-ala
                                               :kerrosluku :kerrosala :rakennusoikeudellinenKerrosala])
                                 (filter #(-> % last (ss/replace "," ".") util/->double pos?) <>)
                                 (into {} <>))
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
                                    {:rakennustietojaEimuutetaKytkin (true? (-> rakennuksen-muuttaminen-doc :data :rakennustietojaEimuutetaKytkin))}
                                    {:muutostyonLaji (fix-typo-in-kayttotarkotuksen-muutos (-> rakennuksen-muuttaminen-doc :data :muutostyolaji))})
                :rakennustieto (get-rakennus-data application rakennuksen-muuttaminen-doc)}
   :created (:created rakennuksen-muuttaminen-doc)})

(defn- get-rakennustietojen-korjaus-toimenpide [rakennustietojen-korjaus-doc application]
  {:Toimenpide {:muuMuutosTyo (conj (get-toimenpiteen-kuvaus rakennustietojen-korjaus-doc)
                                    {:perusparannusKytkin false
                                     :rakennustietojaEimuutetaKytkin false}
                                    {:muutostyonLaji      schemas/muumuutostyo})
                :rakennustieto (get-rakennus-data application rakennustietojen-korjaus-doc)}
   :created (:created rakennustietojen-korjaus-doc)})

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

(defn- get-toimenpide-with-count [n toimenpide]
  (walk/postwalk #(if (and (map? %) (contains? % :jarjestysnumero))
                            (assoc % :jarjestysnumero n)
                            %) toimenpide))

(defmulti operation-canonical {:arglists '([application document])}
  (fn [_ document] (-> document :schema-info :name keyword)))

(defmethod operation-canonical :hankkeen-kuvaus-minimum [& _] nil)

(defmethod operation-canonical :hankkeen-kuvaus [& _] ;; For prev permit applications
  nil)

(defmethod operation-canonical :jatkoaika-hankkeen-kuvaus [& _] nil)

(defmethod operation-canonical :maisematyo [& _] nil)

(defmethod operation-canonical :aloitusoikeus [& _] nil)

(defmethod operation-canonical :aiemman-luvan-toimenpide [& _] nil)

(defmethod operation-canonical :tyonjohtaja-v2 [& _] nil)

(defmethod operation-canonical :uusiRakennus [application document]
  (get-uusi-toimenpide document application))

(defmethod operation-canonical :uusi-rakennus-ei-huoneistoa [application document]
  (get-uusi-toimenpide document application))

(defmethod operation-canonical :rakennuksen-muuttaminen [application document]
  (get-rakennuksen-muuttaminen-toimenpide document application))

(defmethod operation-canonical :rakennuksen-muuttaminen-ei-huoneistoja [application document]
  (get-rakennuksen-muuttaminen-toimenpide document application))

(defmethod operation-canonical :rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia [application document]
  (get-rakennuksen-muuttaminen-toimenpide document application))

(defmethod operation-canonical :rakennustietojen-korjaus [application document]
  (get-rakennustietojen-korjaus-toimenpide document application))

(defmethod operation-canonical :rakennuksen-laajentaminen [application document]
  (get-rakennuksen-laajentaminen-toimenpide document application))

(defmethod operation-canonical :rakennuksen-laajentaminen-ei-huoneistoja [application document]
  (get-rakennuksen-laajentaminen-toimenpide document application))

(defmethod operation-canonical :purkaminen [application document]
  (get-purku-toimenpide document application))

(defmethod operation-canonical :kaupunkikuvatoimenpide [application document]
  (get-kaupunkikuvatoimenpide document application))

(defmethod operation-canonical :kaupunkikuvatoimenpide-ei-tunnusta [application document]
  (get-kaupunkikuvatoimenpide document application))

(defmethod operation-canonical :maalampokaivo [application document]
  (get-maalampokaivo document application))

(defn- get-operations [application]
  (->> (app/get-sorted-operation-documents application)
       (map (partial walk/postwalk empty-strings-to-nil))
       (map (partial operation-canonical application))
       (remove nil?)
       (map get-toimenpide-with-count (range 1 1000))
       not-empty))

(defn- get-asiakirjat-toimitettu-pvm [{:keys [attachments]}]
  (when-let [versions (seq (map :latestVersion attachments))]
    (util/to-xml-date
      (->> versions
           (sort-by :created)
           last
           :created))))

(defn get-lisatiedot [lang]
  {:Lisatiedot {:asioimiskieli (case lang
                                 "sv" "ruotsi"
                                 "suomi")}})

(defn get-lisatiedot-with-asiakirjat-toimitettu [application lang]
  (-> (get-lisatiedot lang)
      (update :Lisatiedot util/assoc-when :asiakirjatToimitettuPvm (get-asiakirjat-toimitettu-pvm application))))

(defn- get-asian-tiedot [documents-by-type]
  (let [maisematyo_documents (:maisematyo documents-by-type)
        hankkeen-kuvaus-doc (or (:hankkeen-kuvaus documents-by-type)
                                (:hankkeen-kuvaus-minimum documents-by-type)
                                (:jatkoaika-hankkeen-kuvaus documents-by-type)
                                (:aloitusoikeus documents-by-type))
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

(defn application-to-canonical-operations
  [application]
  (-> application tools/unwrapped get-operations))

(defn application-to-canonical
  "Transforms application mongodb-document to canonical model."
  [application lang]
  (let [application (tools/unwrapped application)
        link-permits (:linkPermitData application)
        documents-by-type (documents-by-type-without-blanks application)
        toimenpiteet (get-operations application)
        operation-name (-> application :primaryOperation :name)]
    {:Rakennusvalvonta
     {:toimituksenTiedot (toimituksen-tiedot application lang)
      :rakennusvalvontaAsiatieto
      {:RakennusvalvontaAsia
       (cond-> (util/assoc-when-pred
                     {:kasittelynTilatieto (get-state application)
                      :luvanTunnisteTiedot (lupatunnus-with-vrktunnus application)
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
                      :lisatiedot (get-lisatiedot-with-asiakirjat-toimitettu application lang)}
                 util/not-empty-or-nil?
                 :viitelupatieto (map get-viitelupatieto link-permits)
                 :avainsanaTieto (get-avainsanaTieto application)
                 :menettelyTOS (:tosFunctionName application)
                 :rakennuspaikkatieto (get-bulding-places (concat (:rakennuspaikka documents-by-type)
                                                                  (:rakennuspaikka-ilman-ilmoitusta documents-by-type))
                                                          application)
                 :toimenpidetieto toimenpiteet
                 :lausuntotieto (get-statements (:statements application)))

         (#{"tyonjohtajan-nimeaminen" "suunnittelijan-nimeaminen"} operation-name)
         (dissoc :rakennuspaikkatieto :toimenpidetieto :lausuntotieto))}}}))

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


(defn- bad-review-building?
  "Building can be bad either by choice (no state selected) or by
  accident (ghost buildings)."
  [building]
  (or (nil? building)
      (= "ei tiedossa" (get-in building [:rakennus :jarjestysnumero]))
      (util/empty-or-nil? (get-in building [:tila :tila]))
      (every? ss/blank? (-> building :rakennus vals))))

(defn- enrich-review-building [{app-buildings :buildings :as app} {{national-id :valtakunnallinenNumero kiinttun :kiinttun rakennusnro :rakennusnro :as rakennus} :rakennus :as building}]
  (when rakennus
    (let [property-id (or (not-empty kiinttun) (:propertyId app))
          app-building (util/find-first (every-pred (comp #{property-id} :propertyId)
                                                    (some-fn (comp #{national-id} :nationalId)
                                                             (comp #{national-id} :buildingId)
                                                             (comp #{rakennusnro} :localShortId)))
                                        app-buildings)]
      (update building :rakennus util/assoc-when
              :kiinttun property-id
              :valtakunnallinenNumero (when (ss/blank? national-id) (:nationalId app-building))
              :operationId (:operationId app-building)
              :description (:description app-building)))))

(defn- get-review-muutunnustieto [task]
  (->> [{:tunnus (:id task) :sovellus "Lupapiste"}
        {:tunnus (get-in task [:data :muuTunnus]) :sovellus (get-in task [:data :muuTunnusSovellus])}]
       (remove (comp ss/blank? :tunnus))
       (map (partial hash-map :MuuTunnus))))

(defn- get-review-rakennustunnus [[{:keys [rakennus]} & _]]
  (util/assoc-when-pred
      (select-keys rakennus [:jarjestysnumero :kiinttun]) util/not-empty-or-nil?
      :rakennusnro (:rakennusnro rakennus)
      :valtakunnallinenNumero (:valtakunnallinenNumero rakennus)))

(defn- get-review-katselmuksenrakennus [{rakennus :rakennus state :tila}]
  {:KatselmuksenRakennus
   (-> {:jarjestysnumero                     (:jarjestysnumero rakennus)
        :katselmusOsittainen                 (:tila state)
        :kayttoonottoKytkin                  (:kayttoonottava state)}
       (util/assoc-when-pred util/not-empty-or-nil?
         :kiinttun                           (:kiinttun rakennus)
         :rakennusnro                        (:rakennusnro rakennus)
         :valtakunnallinenNumero             (:valtakunnallinenNumero rakennus)
         :kunnanSisainenPysyvaRakennusnumero (:kunnanSisainenPysyvaRakennusnumero rakennus)
         :muuTunnustieto (when-let [op-id (:operationId rakennus)]
                           [{:MuuTunnus {:tunnus op-id :sovellus "toimenpideId"}}
                            {:MuuTunnus {:tunnus op-id :sovellus "Lupapiste"}}])
         :rakennuksenSelite (:description rakennus)))})

(defn- get-review-huomautus [{kuvaus :kuvaus toteaja :toteaja maara-aika :maaraAika toteamis-hetki :toteamisHetki}]
  (when kuvaus
    {:huomautus (util/assoc-when-pred
                  {:kuvaus "-"} util/not-empty-or-nil?
                  :kuvaus kuvaus
                  :toteaja toteaja
                  :maaraAika (util/to-xml-date-from-string maara-aika)
                  :toteamisHetki (util/to-xml-date-from-string toteamis-hetki))}))

(defn katselmus-canonical [application lang task user]
  (let [{{{pito-pvm     :pitoPvm
           pitaja       :pitaja
           lasnaolijat  :lasnaolijat
           poikkeamat   :poikkeamat
           tila         :tila
           tiedoksianto :tiedoksianto
           huomautukset :huomautukset} :katselmus
          katselmuksen-laji            :katselmuksenLaji
          vaadittu-lupaehtona          :vaadittuLupaehtona
          rakennus                     :rakennus} :data
         task-name    :taskname
         :as          task}     (tools/unwrapped task)
        buildings (->> (vals rakennus)
                       (map (partial enrich-review-building application))
                       (remove bad-review-building?))
        application (tools/unwrapped application)
        katselmustyyppi (katselmusnimi-to-type katselmuksen-laji :katselmus)]
    {:Rakennusvalvonta
     {:toimituksenTiedot (toimituksen-tiedot application lang)
      :rakennusvalvontaAsiatieto
      {:RakennusvalvontaAsia
       {:kasittelynTilatieto (get-state application)
        :luvanTunnisteTiedot (lupatunnus application)
        :osapuolettieto {:Osapuolet {:osapuolitieto {:Osapuoli {:kuntaRooliKoodi "Ilmoituksen tekij\u00e4"
                                                                :henkilo {:nimi {:etunimi (:firstName user)
                                                                                 :sukunimi (:lastName user)}
                                                                          :osoite {:osoitenimi {:teksti (or (:street user) "")}
                                                                                   :postitoimipaikannimi (:city user)
                                                                                   :postinumero (:zip user)}
                                                                          :sahkopostiosoite (:email user)
                                                                          :puhelin (:phone user)}}}}}
        :katselmustieto {:Katselmus (-> {:pitoPvm (if (number? pito-pvm) (util/to-xml-date pito-pvm) (util/to-xml-date-from-string pito-pvm))
                                         :katselmuksenLaji katselmustyyppi
                                         :vaadittuLupaehtonaKytkin (true? vaadittu-lupaehtona)
                                         :osittainen tila
                                         :lasnaolijat lasnaolijat
                                         :pitaja pitaja
                                         :poikkeamat poikkeamat
                                         :verottajanTvLlKytkin tiedoksianto
                                         :tarkastuksenTaiKatselmuksenNimi (ss/trim task-name)}
                                        (util/assoc-when-pred util/not-empty-or-nil?
                                          :muuTunnustieto (get-review-muutunnustieto task)
                                          :rakennustunnus (get-review-rakennustunnus buildings)
                                          :katselmuksenRakennustieto (map get-review-katselmuksenrakennus buildings)
                                          :huomautukset (get-review-huomautus huomautukset)))}
        :lisatiedot (get-lisatiedot lang)
        :kayttotapaus (katselmus-kayttotapaus katselmuksen-laji :katselmus)}}}}))

(defn unsent-attachments-to-canonical [application lang]
  (let [application (tools/unwrapped application)]
    {:Rakennusvalvonta
     {:toimituksenTiedot (toimituksen-tiedot application lang)
      :rakennusvalvontaAsiatieto
      {:RakennusvalvontaAsia
       {:kasittelynTilatieto (get-state application)
        :luvanTunnisteTiedot (lupatunnus application)
        :lisatiedot (get-lisatiedot-with-asiakirjat-toimitettu application lang)
        :kayttotapaus "Liitetiedoston lis\u00e4ys"}}}}))
