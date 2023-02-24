(ns lupapalvelu.document.rakennuslupa-canonical
  (:require [clojure.walk :as walk]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.document.canonical-common :refer :all]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.organization :as org]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [swiss.arrows :refer [-<>]]))

(def LISAYS "lisäys")
(def MUUTOS "muutos")
(def POISTO "poisto")

(defn- muutostapa
  "If muutostapa is hidden and has a default value via schema the
  latter is always used in the canonical model. For example, for the
  new buildings the only allowed muutostapa is lisäys."
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

(defn muutoslupa? [{:keys [permitSubtype]}]
  (util/=as-kw permitSubtype :muutoslupa))

(defn- existing-huoneisto? [huoneisto]
  (boolean (some (comp ss/not-blank? :source) (vals huoneisto))))

(defn- str-eq? [a b]
  (->> [a b]
       (map (comp ss/trim ss/->plain-string))
       (apply =)))

(defn- unchanged-huoneisto? [huoneisto]
  (and (existing-huoneisto? huoneisto)
       (->> (dissoc huoneisto :muutostapa)
            vals
            (every? (fn [{:keys [value sourceValue]}]
                      (str-eq? value sourceValue))))))

(defn- regular-huoneisto
  "Returns unwrapped huoneisto after cleanup:
  - Bad legacy data: lisäys muutostapa for an existing building is changed to muutos if prudent
  - Missing muutostapa for an existing building is changed to muutos, if changes.
  - Muutos without changes is omitted
  - New apartment can only have lisäys muutostapa"
  [schema huoneisto]
  (let [unwrapped  (tools/unwrapped huoneisto)
        m-value    (ss/blank-as-nil (muutostapa unwrapped schema))
        missing?   (nil? m-value)
        source?    (existing-huoneisto? huoneisto)
        unchanged? (unchanged-huoneisto? huoneisto)
        lisays?    (= m-value LISAYS)
        muutos?    (= m-value MUUTOS)
        pack       #(assoc unwrapped :muutostapa %)]
    (cond
      (and unchanged? muutos?)
      nil

      (and source? (or lisays? missing?))
      (when-not unchanged?
        (pack MUUTOS))

      (and (not source?) (not lisays?))
      (when muutos?
        (pack LISAYS))

      :else
      (pack m-value))))

(defn- fix-legacy-apt [schema huoneisto]
  (let [old                  (some-> huoneisto :muutostapa :value)
        {:keys [muutostapa]} (regular-huoneisto schema
                                                (cond-> huoneisto
                                                  ;; Just in case :muutostapa contains only validationResult
                                                  (nil? old) (assoc-in [:muutostapa :value] old)))
        change?              (not= old muutostapa)]
    (cond
      (and change? (nil? muutostapa)) (dissoc huoneisto :muutostapa)
      change?                         (assoc huoneisto :muutostapa {:value muutostapa})
      :else                           huoneisto)))

(defn fix-legacy-apartments
  "Fixes bad muutostapa value on the fly. Part of the `process-documents-and-tasks` pipeline."
  ([application document]
   (if-let [schema (and (util/=as-kw (:permitType application) :R)
                        (not (muutoslupa? application))
                        (some-> document :schema-info :name get-huoneistot-schema))]
     (update-in document [:data :huoneistot]
                #(util/map-values (fn [v]
                                    (util/pcond->> v
                                      ;; The data can contain :validationResult vector
                                      map? (fix-legacy-apt schema))) %))
     document))
  ([application]
   (update application :documents #(map (partial fix-legacy-apartments application) %))))

(defn- change-permit-huoneisto
  "Change permit apartments in KuntaGML must consider the source values originating from the
  base permit as well. This is especially true for muutostapa. The table shows how the
  KuntaGML muutostapa is resolved for different base permit muutostapa (first column) and
  change permit apartment operations (top row). For example, when huoneisto with (base)
  lisäys muutostapa is edited in the change permit, the muutostapa is not changed (cell A2
  in the table).

     BASE MUUTOSTAPA                  OPERATIONS IN THE CHANGE PERMIT
   +---------------------------+----------+----------+----------+----------+---------+
   |                           | Add      | Edit     | Remove   | Nothing  | EXCLUDE |
   +---------------------------+----------+----------+----------+----------+---------+
 A | Lisäys                    | -        | Lisäys   | EXCLUDE  | Lisäys   | EXCLUDE |
   +---------------------------+----------+----------+----------+----------+---------+
 B | Muutos                    | -        | Muutos   | Poisto   | Muutos   | EXCLUDE |
   +---------------------------+----------+----------+----------+----------+---------+
 C | Poisto                    | -        | Muutos   | -        | Poisto   | EXCLUDE |
   +---------------------------+----------+----------+----------+----------+---------+
 D | No muutostapa             | -        | Muutos   | Poisto   | EXCLUDE  | EXCLUDE |
   +---------------------------+----------+----------+----------+----------+---------+
 E | The apartment is not in   | Lisäys   | Lisäys   | REMOVE   | -        | -       |
   | the base permit           |          |          |          |          |         |
   +---------------------------+----------+----------+----------+----------+---------+
                                    1          2          3          4          5

  EXCLUDE = The apartment is not in included in the KuntaGML message. The exclusion can
            also be done explicitly in the UI.
  REMOVE  = The apartment is removed from the application."
  [schema huoneisto]
  (let [{m-source-value :sourceValue
         m-value        :value} (:muutostapa huoneisto)
        m-value                 (ss/blank-as-nil m-value)
        m-source-value          (ss/blank-as-nil (muutostapa {:muutostapa m-source-value}
                                                             schema))
        source?                 (existing-huoneisto? huoneisto)
        lisays?                 #(= LISAYS %)
        muutos?                 #(= MUUTOS %)
        poisto?                 #(= POISTO %)
        pack                    (fn [muutostapa k]
                                  (->> (dissoc huoneisto :muutostapa)
                                       (util/map-values k)
                                       (merge {:muutostapa muutostapa})))]
    (cond
      ;; EXCLUDE
      ;; D4, last column
      (nil? m-value)    nil
      ;; A3
      (and (lisays? m-source-value)
           (poisto? m-value)) nil
      ;; E3 (should never happen)
      (and (poisto? m-value)
           (not source?))     nil
      ;; Lisäys A2, A4, E1, E2
      (or (lisays? m-source-value)
          (not source?))      (pack LISAYS :value)
      ;; Muutos
      ;; B2, B4, C2, D2
      (muutos? m-value)       (pack MUUTOS :value)
      ;; Poisto
      ;; B3, D3, C4
      (poisto? m-value)       (pack POISTO :sourceValue))))

(defn- get-huoneisto-data
  ([huoneistot schema-name change-permit?]
   (let [schema     (get-huoneistot-schema schema-name)
         huoneistot (vals (into (sorted-map) huoneistot))]
     (for [{:keys [muutostapa huoneistonumero porras jakokirjain]
            :as   huoneisto} (map (partial (if change-permit?
                                             change-permit-huoneisto
                                             regular-huoneisto)
                                           schema)
                                  huoneistot)
           :let              [poisto? (= muutostapa POISTO)
                              ;; Huoneistoala is mandatory in KuntaGML but cannot be edited for removals. Thus
                              ;; we fallback to zero.
                              huoneisto (cond-> huoneisto
                                          (and poisto? (ss/blank? (:huoneistoala huoneisto)))
                                          (assoc :huoneistoala "0"))
                              required-filled? (or poisto? (required-fields-have-value? schema huoneisto))]
           :when             (and muutostapa (seq huoneisto) required-filled?)]
       (merge {:muutostapa     muutostapa
               :huoneluku      (-> huoneisto :huoneluku)
               :keittionTyyppi (-> huoneisto :keittionTyyppi)

               :huoneistoala                    (ss/replace (:huoneistoala huoneisto) "," ".")
               :varusteet                       {:WCKytkin                (true? (-> huoneisto :WCKytkin))
                                                 :ammeTaiSuihkuKytkin     (true? (-> huoneisto :ammeTaiSuihkuKytkin))
                                                 :saunaKytkin             (true? (-> huoneisto :saunaKytkin))
                                                 :parvekeTaiTerassiKytkin (true? (-> huoneisto :parvekeTaiTerassiKytkin))
                                                 :lamminvesiKytkin        (true? (-> huoneisto :lamminvesiKytkin))}
               :huoneistonTyyppi                (-> huoneisto :huoneistoTyyppi)
               :valtakunnallinenHuoneistotunnus (-> huoneisto :pysyvaHuoneistotunnus)}
              (when (ss/numeric? huoneistonumero)
                {:huoneistotunnus
                 (merge {:huoneistonumero (format "%03d" (util/->int (ss/remove-leading-zeros huoneistonumero)))}
                        (when (not-empty porras) {:porras (ss/upper-case porras)})
                        (when (not-empty jakokirjain) {:jakokirjain (ss/lower-case jakokirjain)}))})))))
  ([huoneistot schema-name]
   (get-huoneisto-data huoneistot schema-name false)))

(defn- ignore-zero
  "VRK doesn't accept zero in fields where it is unnecessary, see TT-18492"
  [number]
  (if (zero? number) nil number))

(defn get-huoneistot-lkm [huoneistot]
  (->> huoneistot
       (filter #(= "lis\u00e4ys" (:muutostapa %)))
       count
       ignore-zero))

(defn get-huoneistot-pintaala [huoneistot]
  (->> (filter #(= "lis\u00e4ys" (:muutostapa %)) huoneistot)
       (map :huoneistoala)
       (map #(util/->double %))
       (apply +)
       ignore-zero))

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
        huoneistot-data (get-huoneisto-data huoneistot (:name info) (muutoslupa? application))
        huoneistot (if (org/pate-scope? application)
                     {:huoneisto huoneistot-data
                      :asuntojenPintaala (get-huoneistot-pintaala huoneistot-data)
                      :asuntojenLkm (get-huoneistot-lkm huoneistot-data)}
                     {:huoneisto huoneistot-data})
        vaestonsuoja-value (ss/trim (get-in toimenpide [:varusteet :vaestonsuoja]))
        vaestonsuoja (when (ss/numeric? vaestonsuoja-value) (util/->int vaestonsuoja-value))
        verkostoliittymat (if (some true? (vals (:verkostoliittymat toimenpide)))
                            {:sahkoKytkin (true? (-> toimenpide :verkostoliittymat :sahkoKytkin))
                             :maakaasuKytkin (true? (-> toimenpide :verkostoliittymat :maakaasuKytkin))
                             :viemariKytkin (true? (-> toimenpide :verkostoliittymat :viemariKytkin))
                             :vesijohtoKytkin (true? (-> toimenpide :verkostoliittymat :vesijohtoKytkin))
                             :kaapeliKytkin (true? (-> toimenpide :verkostoliittymat :kaapeliKytkin))}
                            {})
        varusteet (if (or (some true? (vals (:varusteet toimenpide)))
                          (util/not-empty-or-nil? vaestonsuoja)
                          (util/not-empty-or-nil? (-> toimenpide :varusteet :saunoja positive-integer)))
                    {:sahkoKytkin (true? (-> toimenpide :varusteet :sahkoKytkin))
                     :kaasuKytkin (true? (-> toimenpide :varusteet :kaasuKytkin))
                     :viemariKytkin (true? (-> toimenpide :varusteet :viemariKytkin))
                     :vesijohtoKytkin (true? (-> toimenpide :varusteet :vesijohtoKytkin))
                     :lamminvesiKytkin (true? (-> toimenpide :varusteet :lamminvesiKytkin))
                     :aurinkopaneeliKytkin (true? (-> toimenpide :varusteet :aurinkopaneeliKytkin))
                     :hissiKytkin (true? (-> toimenpide :varusteet :hissiKytkin))
                     :koneellinenilmastointiKytkin (true? (-> toimenpide :varusteet :koneellinenilmastointiKytkin))
                     :saunoja (-> toimenpide :varusteet :saunoja positive-integer)
                     :vaestonsuoja vaestonsuoja}
                    {})
        rakennuksen-tiedot-basic-info (util/assoc-when
                                        {:kayttotarkoitus (:kayttotarkoitus kaytto)
                                         :rakentamistapa (:rakentamistapa rakenne)
                                         :verkostoliittymat verkostoliittymat
                                         :lammitystapa (cond
                                                         (= lammitystapa "suorasahk\u00f6") "suora s\u00e4hk\u00f6"
                                                         (= lammitystapa "eiLammitysta") "ei l\u00e4mmityst\u00e4"
                                                         :else lammitystapa)
                                         :varusteet varusteet
                                         :liitettyJatevesijarjestelmaanKytkin (true? (-> toimenpide :varusteet :liitettyJatevesijarjestelmaanKytkin))
                                         :rakennustunnus (get-rakennustunnus toimenpide application info)}
                                        :rakennusluokka (when (contains? (tools/resolve-schema-flags {:organization (org/get-application-organization application)
                                                                                                      :application application})
                                                                         :rakennusluokka)
                                                          (:rakennusluokka kaytto)))
        rakennuksen-tiedot (merge
                            (-<> mitat
                                 (select-keys [:tilavuus :kokonaisala :kellarinpinta-ala
                                               :kerrosluku :kerrosala :rakennusoikeudellinenKerrosala])
                                 (filter #(-> % last (ss/replace "," ".") util/->double pos?) <>)
                                 (into {} <>))
                            (-<> luokitus
                                 (select-keys [:energialuokka :energiatehokkuusluku :paloluokka])
                                 (filter #(-> % last ss/blank? not) <>)
                                 (map (fn [[k v]] ;; Doing this normalization for paloluokka breaks data, so we want to skip it.
                                                  ;; See TT-656.
                                        [k (cond-> v
                                             (not= :paloluokka k) (ss/replace " " ""))]) <>)
                                 (into {} <>))
                             (when-not (ss/blank? (:energiatehokkuusluku luokitus))
                               (select-keys luokitus [:energiatehokkuusluvunYksikko]))
                             (when (util/not-empty-or-nil? (:huoneisto huoneistot))
                               {:asuinhuoneistot huoneistot})
                             (util/assoc-when-pred rakennuksen-tiedot-basic-info util/not-empty-or-nil?
                               :kantavaRakennusaine kantava-rakennus-aine-map
                               :lammonlahde lammonlahde-map
                               :julkisivu julkisivu-map))
        tilapainenRakennusKytkin (:tilapainenRakennusKytkin kaytto)]
    (util/assoc-when-pred {:yksilointitieto (-> info :op :id)
                           :alkuHetki (date/xml-datetime  created)
                           :sijaintitieto {:Sijainti {:tyhja empty-tag}}
                           :rakentajatyyppi (:rakentajaTyyppi kaytto)
                           :rakennuksenTiedot rakennuksen-tiedot}
      util/not-empty-or-nil?
      :omistajatieto (remove nil? (for [m (vals (:rakennuksenOmistajat toimenpide))] (get-rakennuksen-omistaja m)))
      :kokoontumistilanHenkilomaara (-> toimenpide :varusteet :kokoontumistilanHenkilomaara)
      :tilapainenRakennusKytkin tilapainenRakennusKytkin
      :tilapainenRakennusvoimassaPvm (when tilapainenRakennusKytkin
                                       (date/xml-date (:tilapainenRakennusvoimassaPvm kaytto))))))

(defn- get-rakennus-data [application doc]
  (let [doc-vtj-prt (get-in doc [:data :valtakunnallinenNumero])
        document-building (->> (filter #(and (= (:vtj-prt %) doc-vtj-prt)
                                             (not (nil? (:vtj-prt %)))) (:document-buildings application))
                               first
                               :building)
        building (get-rakennus application
                               (util/deep-merge-with
                                (fn [first-val second-val] (if-not (nil? second-val) second-val first-val))
                                {:data document-building}
                                doc))]
    {:Rakennus building}))

(defn- get-raukeamis-pvm [application doc]
  (let [doc-id (-> doc :schema-info :op :id)
        operation (util/find-by-id doc-id (app-utils/get-operations application))]
    (date/xml-date (:extinct operation))))

(defn- get-toimenpiteen-kuvaus [doc]
  ;Uses fi as default since krysp uses finnish in enumeration values
  {:kuvaus (i18n/localize "fi" (str "operations." (-> doc :schema-info :op :name)))})

(defn get-uusi-toimenpide [doc application]
  {:Toimenpide {:uusi (-> (get-toimenpiteen-kuvaus doc)
                          (util/assoc-when :raukeamisPvm (get-raukeamis-pvm application doc)))
                :rakennustieto (get-rakennus-data application doc)}
   :created (:created doc)})

(defn fix-typo-in-kayttotarkotuksen-muutos [v]
  (if (= v lupapalvelu.document.schemas/kayttotarkotuksen-muutos)
    "rakennuksen p\u00e4\u00e4asiallinen k\u00e4ytt\u00f6tarkoitusmuutos"
    v))

(defn- get-rakennuksen-muuttaminen-toimenpide [rakennuksen-muuttaminen-doc application]
  {:Toimenpide {:muuMuutosTyo (-> (get-toimenpiteen-kuvaus rakennuksen-muuttaminen-doc)
                                  (assoc :perusparannusKytkin (true? (-> rakennuksen-muuttaminen-doc :data :perusparannuskytkin))
                                         :rakennustietojaEimuutetaKytkin (true? (-> rakennuksen-muuttaminen-doc :data :rakennustietojaEimuutetaKytkin))
                                         :muutostyonLaji (fix-typo-in-kayttotarkotuksen-muutos (-> rakennuksen-muuttaminen-doc :data :muutostyolaji)))
                                  (util/assoc-when :raukeamisPvm (get-raukeamis-pvm application rakennuksen-muuttaminen-doc)))
                :rakennustieto (get-rakennus-data application rakennuksen-muuttaminen-doc)}
   :created (:created rakennuksen-muuttaminen-doc)})

(defn- get-rakennustietojen-korjaus-toimenpide [rakennustietojen-korjaus-doc application]
  {:Toimenpide {:muuMuutosTyo (-> (get-toimenpiteen-kuvaus rakennustietojen-korjaus-doc)
                                  (assoc :perusparannusKytkin false
                                         :rakennustietojaEimuutetaKytkin false
                                         :muutostyonLaji schemas/muumuutostyo)
                                  (util/assoc-when :raukeamisPvm (get-raukeamis-pvm application rakennustietojen-korjaus-doc)))
                :rakennustieto (get-rakennus-data application rakennustietojen-korjaus-doc)}
   :created (:created rakennustietojen-korjaus-doc)})

(defn- get-rakennuksen-laajentaminen-toimenpide [laajentaminen-doc application]
  {:Toimenpide {:laajennus (-> (get-toimenpiteen-kuvaus laajentaminen-doc)
                               (assoc :perusparannusKytkin (true? (get-in laajentaminen-doc [:data :laajennuksen-tiedot :perusparannuskytkin]))
                                      :laajennuksentiedot (-> (get-in laajentaminen-doc [:data :laajennuksen-tiedot :mitat])
                                                              (select-keys [:tilavuus :kerrosala :kokonaisala :rakennusoikeudellinenKerrosala :huoneistoala])
                                                              (update :huoneistoala #(map select-keys (vals %) (repeat [:pintaAla :kayttotarkoitusKoodi])))))
                               (util/assoc-when :raukeamisPvm (get-raukeamis-pvm application laajentaminen-doc)))
                :rakennustieto (get-rakennus-data application laajentaminen-doc)}
   :created (:created laajentaminen-doc)})

(defn- get-purku-toimenpide [{data :data :as purku-doc} application]
  {:Toimenpide {:purkaminen (conj (get-toimenpiteen-kuvaus purku-doc)
                                 {:purkamisenSyy (:poistumanSyy data)
                                  :poistumaPvm (date/xml-date (:poistumanAjankohta data))})
                :rakennustieto (update-in
                                 (get-rakennus-data application purku-doc)
                                 [:Rakennus :rakennuksenTiedot]
                                 (fn [m]
                                   ; Cleanup top level keys that will bi nil anyway.
                                   ; Recursive strip-nils would be too much.
                                   (dissoc m
                                           :verkostoliittymat
                                           :energialuokka
                                           :energiatehokkuusluku
                                           :energiatehokkuusluvunYksikko
                                           :paloluokka
                                           :lammitystapa
                                           :varusteet
                                           :liitettyJatevesijarjestelmaanKytkin)))}
   :created (:created purku-doc)})

(defn get-kaupunkikuvatoimenpide [kaupunkikuvatoimenpide-doc application]
  (let [toimenpide (:data kaupunkikuvatoimenpide-doc)
        tilapainenRakennelmaKytkin (:tilapainenRakennelmaKytkin toimenpide)]
    {:Toimenpide {:kaupunkikuvaToimenpide (get-toimenpiteen-kuvaus kaupunkikuvatoimenpide-doc)
                  :rakennelmatieto {:Rakennelma (util/assoc-when-pred
                                                  {:yksilointitieto (-> kaupunkikuvatoimenpide-doc :schema-info :op :id)
                                                   :alkuHetki (date/xml-datetime (:created kaupunkikuvatoimenpide-doc))
                                                   :sijaintitieto {:Sijainti {:tyhja empty-tag}}
                                                   :kokonaisala (-> toimenpide :kokonaisala)
                                                   :kayttotarkoitus (-> toimenpide :kayttotarkoitus)
                                                   :kuvaus {:kuvaus (-> toimenpide :kuvaus)}
                                                   :tunnus {:jarjestysnumero nil}
                                                   :kiinttun (:propertyId application)}
                                                  util/not-empty-or-nil?
                                                  :kokoontumistilanHenkilomaara (-> toimenpide :kokoontumistilanHenkilomaara)
                                                  :tilapainenRakennelmaKytkin (:tilapainenRakennelmaKytkin toimenpide)
                                                  :tilapainenRakennelmavoimassaPvm (when tilapainenRakennelmaKytkin
                                                                                     (date/xml-date (:tilapainenRakennelmavoimassaPvm toimenpide))))}}
     :created (:created kaupunkikuvatoimenpide-doc)}))


(defn get-maalampokaivo [kaupunkikuvatoimenpide-doc application]
  (-> (get-kaupunkikuvatoimenpide kaupunkikuvatoimenpide-doc application)
      (util/dissoc-in [:Toimenpide :rakennelmatieto :Rakennelma :kokonaisala])
      (assoc-in [:kayttotarkoitus] "Maal\u00e4mp\u00f6pumppuj\u00e4rjestelm\u00e4")))

(defn get-aiemman-luvan-toimenpide [{:keys [data created] :as doc} application]
  (let [{:keys [toimenpide rakennustietojaEimuutetaKytkin muutostyolaji]
         :as   op-data} (:kuntagml-toimenpide data)
        basic-switch    {:perusparannusKytkin (-> op-data :perusparannuskytkin boolean)}
        description     {:kuvaus (-> data :kuvaus ss/trim (or ""))}
        [tag m]         (case toimenpide
                          "uusi"                   [:uusi]
                          "purkaminen"             [:purkaminen]
                          "kaupunkikuvaToimenpide" [:kaupunkikuvaToimenpide]

                          "laajennus"
                          [:laajennus
                           (assoc-in basic-switch
                                     [:laajennuksentiedot :huoneistoala :kayttotarkoitusKoodi]
                                     ;; Needed in order to pass KuntaGML validation
                                     "ei tiedossa")]

                          "uudelleenrakentaminen"
                          [:uudelleenrakentaminen
                           (assoc basic-switch :muutostyonLaji muutostyolaji)]

                          "muuMuutosTyo"
                          [:muuMuutosTyo
                           (merge basic-switch
                                  {:rakennustietojaEimuutetaKytkin (boolean rakennustietojaEimuutetaKytkin)
                                   :muutostyonLaji                 muutostyolaji})]

                          nil)]
    (when tag
      {:Toimenpide {tag            (merge m description)
                    :rakennustieto (get-rakennus-data application doc)}
       :created    created})))

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

(defmethod operation-canonical :aiemman-luvan-toimenpide
  [application  document]
  (get-aiemman-luvan-toimenpide document application))

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

(defmethod operation-canonical :kaupunkikuvatoimenpide-kokoontumistilalla [application document]
  (get-kaupunkikuvatoimenpide document application))

(defmethod operation-canonical :maalampokaivo [application document]
  (get-maalampokaivo document application))

(defmethod operation-canonical :rasite-tai-yhteisjarjestely [application document]
  (get-uusi-toimenpide document application))

(defn- get-operations [application]
  (->> (app-utils/get-sorted-operation-documents application)
       (map (partial walk/postwalk empty-strings-to-nil))
       (map (partial operation-canonical application))
       (remove nil?)
       (map get-toimenpide-with-count (range 1 1000))
       not-empty))

(defn- get-asiakirjat-toimitettu-pvm [{:keys [attachments]}]
  (when-let [versions (seq (map :latestVersion attachments))]
    (date/xml-date
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

(defn- get-paper-texts
  "Trimmed distinct texts joined by newlines."
  [data-key documents-by-type]
  (some->> (:aiemman-luvan-toimenpide documents-by-type)
           (map (comp ss/trim data-key :data))
           (remove ss/blank?)
           distinct
           (ss/join "\n\n")))

(defn- compose-kuvaukset [docs]
  (for [doc docs]
    (str (:kuvaus (get-toimenpiteen-kuvaus doc))
         ":" (-> doc :data :kuvaus ss/trim))))

(defn- get-asian-tiedot [documents-by-type]
  (let [join-fn              (util/fn->> flatten
                                         (map ss/trim)
                                         (remove ss/blank?)
                                         (ss/join "\n\n")
                                         ss/blank-as-nil)
        hankkeen-kuvaus-doc  (or (:hankkeen-kuvaus documents-by-type)
                                 (:hankkeen-kuvaus-minimum documents-by-type)
                                 (:jatkoaika-hankkeen-kuvaus documents-by-type)
                                 (:aloitusoikeus documents-by-type))
        asian-tiedot         (:data (first hankkeen-kuvaus-doc))
        kuvaus               (join-fn [(:kuvaus asian-tiedot)
                                       (compose-kuvaukset (:maisematyo documents-by-type))
                                       (compose-kuvaukset (:rasite-tai-yhteisjarjestely documents-by-type))
                                       (get-paper-texts :kuvaus documents-by-type)])
        poikkeamat           (join-fn [(:poikkeamat asian-tiedot)
                                       (get-paper-texts :poikkeamat documents-by-type)])]
    (cond-> nil
      kuvaus     (assoc-in [:Asiantiedot :rakennusvalvontaasianKuvaus] kuvaus)
      poikkeamat (assoc-in [:Asiantiedot :vahainenPoikkeaminen] poikkeamat))))

(defn- get-kayttotapaus [documents-by-type toimenpiteet]
  (if (and (contains? documents-by-type :maisematyo) (empty? toimenpiteet))
      "Uusi maisematy\u00f6hakemus"
      "Uusi hakemus"))

(defn application-to-canonical-operations
  [application]
  (-> application tools/unwrapped get-operations))

(defn- unwrap-with-apartments
  "Apartment data resolution requires unwrapped apartments."
  [{:keys [documents] :as application}]
  (let [application (-> application (dissoc :documents) tools/unwrapped)
        documents   (map (fn [doc]
                           (if-let [apartments (get-in doc [:data :huoneistot])]
                             (assoc-in (tools/unwrapped doc)
                                       [:data :huoneistot] apartments)
                             (tools/unwrapped doc)))
                         documents)]
    (assoc application :documents documents)))

(defn rakval-application-to-canonical
  "Transforms application mongodb-document to canonical model."
  [application lang]
  (let [application       (unwrap-with-apartments application)
        link-permits      (:linkPermitData application)
        documents-by-type (stripped-documents-by-type application)
        toimenpiteet      (get-operations application)
        operation-name    (-> application :primaryOperation :name)]
    {:Rakennusvalvonta
     {:toimituksenTiedot (toimituksen-tiedot application lang)
      :rakennusvalvontaAsiatieto
      {:RakennusvalvontaAsia
       (cond-> (util/assoc-when-pred
                 {:kasittelynTilatieto (get-state application)
                  :luvanTunnisteTiedot (lupatunnus-with-vrktunnus application)
                  :osapuolettieto (osapuolet application documents-by-type lang)
                  :kayttotapaus (if  (muutoslupa? application)
                                  "Rakentamisen aikainen muutos"
                                  (condp = operation-name
                                    "tyonjohtajan-nimeaminen"             "Uuden työnjohtajan nimeäminen"
                                    "tyonjohtajan-nimeaminen-v2"          "Uuden työnjohtajan nimeäminen"
                                    "tyonjohtajan-irtisanominen"          "Työnjohtajan irtisanominen"
                                    "tyonjohtajan-vastuiden-paattaminen"  "Työnjohtajan vastuun päättäminen"
                                    "suunnittelijan-nimeaminen"           "Uuden suunnittelijan nimeäminen"
                                    "jatkoaika"                           "Jatkoaikahakemus"
                                    "raktyo-aloit-loppuunsaat"            "Jatkoaikahakemus"
                                    "aloitusoikeus"                       "Uusi aloitusoikeus"
                                    "rasite-tai-yhteisjarjestely"         "Uusi rakennusrasitehakemus"
                                    (get-kayttotapaus documents-by-type toimenpiteet)))
                  :asianTiedot (get-asian-tiedot documents-by-type)
                  :lisatiedot (get-lisatiedot-with-asiakirjat-toimitettu application lang)}
                 util/not-empty-or-nil?
                 :viitelupatieto (map get-viitelupatieto link-permits)
                 :avainsanaTieto (get-avainsanaTieto application)
                 :menettelyTOS (:tosFunctionName application)
                 :rakennuspaikkatieto (get-rakennuspaikkatieto documents-by-type application)
                 :toimenpidetieto toimenpiteet
                 :lausuntotieto (get-statements (:statements application))
                 :avainArvoTieto (seq (get-avain-arvo-parit documents-by-type)))

         (#{"tyonjohtajan-nimeaminen"
            "suunnittelijan-nimeaminen"
            "tyonjohtajan-irtisanominen"
            "tyonjohtajan-vastuiden-paattaminen"} operation-name)
         (dissoc :rakennuspaikkatieto :toimenpidetieto :lausuntotieto))}}}))

(defmethod application->canonical :R [application language]
  (rakval-application-to-canonical application language))

(defmethod description :R [_ canonical]
  (get-in canonical [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia
                     :asianTiedot :Asiantiedot :rakennusvalvontaasianKuvaus]))

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

(defn- non-blank-eq
  "True if both non-blank and equal
  False if both non-blank and not equal
  nil if either blank"
  [a b]
  (when (and (ss/not-blank? a) (ss/not-blank? b))
    (= a b)))

(defn- enrich-review-building [{app-buildings :buildings :as app} {{national-id :valtakunnallinenNumero
                                                                    kiinttun :kiinttun
                                                                    rakennusnro :rakennusnro
                                                                    :as rakennus} :rakennus :as building}]
  (when rakennus
    (let [property-id (or (not-empty kiinttun) (:propertyId app))
          app-building (util/find-first (every-pred (comp #{property-id} :propertyId)
                                                    (fn [{:keys [nationalId buildingId localShortId]}]
                                                      (->> [(non-blank-eq national-id nationalId)
                                                            (non-blank-eq national-id buildingId)
                                                            (non-blank-eq rakennusnro localShortId)]
                                                           (filter boolean?)
                                                           first)))
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
                  :maaraAika (date/xml-date maara-aika)
                  :toteamisHetki (date/xml-date toteamis-hetki))}))

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
                       (remove #(->> % :rakennus vals (every? util/emptyish?)))
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
        :katselmustieto {:Katselmus (-> {:pitoPvm (date/xml-date pito-pvm)
                                         :katselmuksenLaji katselmustyyppi
                                         :vaadittuLupaehtonaKytkin (true? vaadittu-lupaehtona)
                                         :osittainen tila
                                         :lasnaolijat lasnaolijat
                                         :pitaja (if (map? pitaja) (:code pitaja) pitaja)
                                         :poikkeamat poikkeamat
                                         :verottajanTvLlKytkin tiedoksianto
                                         :tarkastuksenTaiKatselmuksenNimi (ss/trim task-name)}
                                        (util/assoc-when-pred util/not-empty-or-nil?
                                          :muuTunnustieto (get-review-muutunnustieto task)
                                          ;; This is here for
                                          ;; supporting legacy
                                          ;; versions of
                                          ;; KuntaGML. Note that the
                                          ;; order of `buildings` is
                                          ;; not well defined
                                          :rakennustunnus (get-review-rakennustunnus buildings)
                                          :katselmuksenRakennustieto (map get-review-katselmuksenrakennus buildings)
                                          :huomautukset (get-review-huomautus huomautukset)))}
        :lisatiedot (get-lisatiedot lang)
        :kayttotapaus (katselmus-kayttotapaus katselmuksen-laji :katselmus)}}}}))


(defmethod review->canonical :R [application katselmus {:keys [lang user]}]
  (katselmus-canonical application lang katselmus user))

(defmethod review-path :R [_]
  [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :katselmustieto :Katselmus])

(defn aloitusilmoitus-canonical [{:keys [application lang user]} notice-form]
  (let [{form-ts   :timestamp
         form-user :user} (-> notice-form :history first)]
    {:Rakennusvalvonta
     {:toimituksenTiedot (toimituksen-tiedot application lang)
      :rakennusvalvontaAsiatieto
      {:RakennusvalvontaAsia
       {:kasittelynTilatieto (get-state application)
        :luvanTunnisteTiedot (lupatunnus application)
        :osapuolettieto      {:Osapuolet
                              {:osapuolitieto
                               [{:Osapuoli
                                 {:kuntaRooliKoodi "Ilmoituksen tekij\u00e4"
                                  :henkilo         {:nimi             {:etunimi  (:firstName user)
                                                                       :sukunimi (:lastName user)}
                                                    :osoite           {:osoitenimi           {:teksti (or (:street user) "")}
                                                                       :postitoimipaikannimi (:city user)
                                                                       :postinumero          (:zip user)}
                                                    :sahkopostiosoite (:email user)
                                                    :puhelin          (:phone user)}}}]}}
        :katselmustieto      {:Katselmus
                              {:pitoPvm                         (date/xml-date form-ts)
                               :katselmuksenLaji                "muu katselmus"
                               :vaadittuLupaehtonaKytkin        false
                               :osittainen                      "lopullinen"
                               :pitaja                          (str (:firstName form-user) " " (:lastName form-user))
                               :huomautukset                    {:huomautus {:kuvaus (:text notice-form)}}
                               :tarkastuksenTaiKatselmuksenNimi "Aloitusilmoitus"
                               :muuTunnustieto                  (get-review-muutunnustieto notice-form)
                               :katselmuksenRakennustieto       (->> (:buildings application)
                                                                     (filter #(util/includes-as-kw? (:buildingIds notice-form)
                                                                                                    (:buildingId %)))
                                                                     (map (fn [{:keys [nationalId index description propertyId]}]
                                                                            (->> {:valtakunnallinenNumero nationalId
                                                                                  :jarjestysnumero        index
                                                                                  :rakennuksenSelite      description
                                                                                  :kiinttun               propertyId}
                                                                                 (remove (comp ss/blank? last))
                                                                                 (into {})))))}}
        :kayttotapaus        "Aloitusilmoitus"}}}}))

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


(defn building-extinction-canonical [application lang]
  (let [application       (tools/unwrapped application)
        documents-by-type (stripped-documents-by-type application)
        toimenpiteet      (get-operations application)]
    {:Rakennusvalvonta
     {:toimituksenTiedot (toimituksen-tiedot application lang)
      :rakennusvalvontaAsiatieto
      {:RakennusvalvontaAsia
       (-> {:kasittelynTilatieto (get-state application)
            :luvanTunnisteTiedot (lupatunnus-with-vrktunnus application) ;; LPK-4705: more info about lupatunnus-with-vrktunnus
            :kayttotapaus        "Rakennuksen raukeaminen"}
           (util/assoc-when-pred
             util/not-empty-or-nil?
             :toimenpidetieto toimenpiteet
             :rakennuspaikkatieto (get-rakennuspaikkatieto documents-by-type application)))}}}))
