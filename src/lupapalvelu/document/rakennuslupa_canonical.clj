(ns lupapalvelu.document.rakennuslupa-canonical
  (:require [clojure.walk :as walk]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.document.canonical-common :refer :all]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.operations :as ops]
            [lupapalvelu.organization :as org]
            [lupapalvelu.ryhti.transform :as ryhti-transform]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [swiss.arrows :refer :all]))

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

(defn- get-ryhti-huoneistot-schema [schema-name]
  (->> {:name schema-name}
       schemas/get-schema
       :body
       (util/find-by-key :name "rakennuskohteen-osa")
       :body
       (util/find-by-key :name "huoneistot")))

(defn- huoneisto-varusteet [huoneisto ryhti-data-2026]
  (if ryhti-data-2026
    {:WCKytkin                (-> huoneisto :wc          util/->int pos?)
     :ammeTaiSuihkuKytkin     (-> huoneisto :suihku      util/->int pos?)
     :saunaKytkin             (-> huoneisto :sauna       util/->int pos?)
     :parvekeTaiTerassiKytkin (-> huoneisto :parveke     util/->int pos?)
     :lamminvesiKytkin        (-> huoneisto :lammin-vesi util/->int pos?)}
    {:WCKytkin                (true? (-> huoneisto :WCKytkin))
     :ammeTaiSuihkuKytkin     (true? (-> huoneisto :ammeTaiSuihkuKytkin))
     :saunaKytkin             (true? (-> huoneisto :saunaKytkin))
     :parvekeTaiTerassiKytkin (true? (-> huoneisto :parvekeTaiTerassiKytkin))
     :lamminvesiKytkin        (true? (-> huoneisto :lamminvesiKytkin))}))

(defn- get-huoneisto-data
  ([huoneistot schema-name change-permit? ryhti-data-2026]
   (let [schema     (if ryhti-data-2026
                      (get-ryhti-huoneistot-schema schema-name)
                      (get-huoneistot-schema schema-name))
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
               :varusteet                       (huoneisto-varusteet huoneisto ryhti-data-2026 )
               :huoneistonTyyppi                (-> huoneisto :huoneistoTyyppi)
               :valtakunnallinenHuoneistotunnus (-> huoneisto :pysyvaHuoneistotunnus)}
              (when (ss/numeric? huoneistonumero)
                {:huoneistotunnus
                 (merge {:huoneistonumero (format "%03d" (util/->int (ss/remove-leading-zeros huoneistonumero)))}
                        (when (not-empty porras) {:porras (ss/upper-case porras)})
                        (when (not-empty jakokirjain) {:jakokirjain (ss/lower-case jakokirjain)}))})))))
  ([huoneistot schema-name]
   (get-huoneisto-data huoneistot schema-name false false)))

(defn- ignore-zero
  "VRK doesn't accept zero in fields where it is unnecessary, see TT-18492"
  [number]
  (if (zero? number) nil number))

(defn get-huoneistot-lkm [huoneistot]
  (->> huoneistot
       (filter #(= "lisäys" (:muutostapa %)))
       count
       ignore-zero))

(defn get-huoneistot-pintaala [huoneistot]
  (->> (filter #(= "lisäys" (:muutostapa %)) huoneistot)
       (map :huoneistoala)
       (map #(util/->double %))
       (apply +)
       ignore-zero))

(defn- get-rakennuksen-omistaja [omistaja]
  (when-let [osapuoli (get-osapuoli-data omistaja :rakennuksenomistaja)]
    {:Omistaja osapuoli}))

(defn- operation-description [{primary :primaryOperation secondaries :secondaryOperations} op-id]
  (:description (util/find-by-id op-id (cons primary secondaries)) ))

(defn vtj-prt-index [{:keys [buildings]} vtj-prt]
  (some-<> vtj-prt
           ss/blank-as-nil
           (util/find-by-key :nationalId <> buildings)
           :index
           ss/blank-as-nil))

(defn get-rakennustunnus [unwrapped-doc-data application {{op-id :id} :op}]
  (let [{:keys [buildingId tunnus
                rakennusnro
                manuaalinen_rakennusnro]} unwrapped-doc-data
        valtakunnallinenNumero            (if (:ryhti-data-2026 application)
                                            (:pysyva-rakennustunnus unwrapped-doc-data)
                                            (:valtakunnallinenNumero unwrapped-doc-data))
        description-parts                 (remove ss/blank? [tunnus (operation-description application op-id)])
        defaults                          (util/assoc-when-pred {:jarjestysnumero (vtj-prt-index application valtakunnallinenNumero)
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

(defn- get-mitat
  [{{:keys [mitat] :as data} :data} ryhti-data-2026]
  (let [mitat          (if ryhti-data-2026
                         (-> data :rakennuskohteen-osa :ulkokuorentiedot)
                         mitat)
        ;; Facta accepts only particular fields depending on the chosen rakennusoikeus
        oikeus-type    (or (:rakennusoikeusValinta mitat) schemas/default-rakennusoikeusValinta)
        rakennus-keys  (into [:tilavuus :kokonaisala :kellarinpinta-ala :kerrosluku :kerrosala]
                             (cond
                               (= "kerrosala" oikeus-type)    [:rakennusoikeudellinenKerrosala]
                               (= "tilavuus" oikeus-type)     [:rakennusoikeusKuutiometreina]
                               (= "harjakorkeus" oikeus-type) [:rakennusoikeusKuutiometreina
                                                               :harjanKorkeusrakennusalalla]
                               :else                          (throw (ex-info "unknown rakennusoikeusValinta" {:mitat mitat}))))
        {:keys [harjanKorkeusrakennusalalla]
         :as   result} (->> (-> mitat
                                (select-keys rakennus-keys)
                                ;; KuntaGML does not accept "123,4" for decimal fields
                                (update-vals (fn [v] (ss/replace v "," "."))))
                            ;; Filter out zeros, negative numbers or non-number strings
                            (filter #(-> % val util/->double pos?))
                            (into {}))
        ;; `:rakennusoikeusValinta` is not mapped to XML, but we need to keep track
        ;; of it to validate these fields later when mapping to XML
        result         (assoc result :rakennusoikeusValinta oikeus-type)
        ->two-decimals (fn [s] (ss/format-fixed "%.2f" (parse-double s)))]
    (cond-> result
      ;; Facta wants harjankorkeus with two decimals
      harjanKorkeusrakennusalalla (update :harjanKorkeusrakennusalalla ->two-decimals))))

(defn get-primary
  "Find the `:is-primary` entry, or `nil` if none.
  If there are multiple, return the one which is earliest."
  [m]
  (some->> m
           (filter (comp :is-primary val))
           seq
           (apply min-key (comp util/->int name key))
           val))

(defn- ^:ryhti get-ryhti-lammitys [rakennuskohteen-osa]
  (let [lammonlahde  (get-primary (get-in rakennuskohteen-osa [:energiatiedot :lammonlahde]))
        lammitystapa (get-primary (get-in rakennuskohteen-osa [:energiatiedot :lammitystapa]))
        heatsource   (:lammonlahdetyyppi lammonlahde)]
    (cond-> {}
      (:lammitystapatyyppi lammitystapa) (assoc :lammitystapa (:lammitystapatyyppi lammitystapa))
      ;; Use the same 'other value' as legacy data.
      heatsource                         (assoc :lammonlahde (if (= "muu" heatsource)
                                                               "other"
                                                               heatsource))
      (:muu-lammonlahde lammonlahde)     (assoc :muu-lammonlahde (:muu-lammonlahde lammonlahde)))))

(defn- ^:ryhti ryhti-luokitus [rakennuskohteen-osa]
  (let [energialuokka (get-in rakennuskohteen-osa [:energiatiedot :energialuokka])
        paloluokka    (get-in rakennuskohteen-osa [:materiaalitiedot :paloluokka])]
    (util/assoc-when {}
                     :energialuokka energialuokka
                     :paloluokka    paloluokka)))

(defn- ^:ryhti verkostoliittymat-for [{:keys [verkostoliittymat]} {tekniset-tiedot :tekniset-tiedot} ryhti-data-2026]
  (if ryhti-data-2026
    (let [liittymatyypit (map (fn[[_ v]] (:verkostoliittymatyyppi v))
                              (tools/unwrapped (:verkostoliittymat tekniset-tiedot)))
          result {:sahkoKytkin     (some? (some #{"sahko"} liittymatyypit))
                  :maakaasuKytkin  (some? (some #{"kaasu"} liittymatyypit))
                  :viemariKytkin   (some? (some #{"jatevesi"} liittymatyypit))
                  :vesijohtoKytkin (some? (some #{"talousvesi"} liittymatyypit))
                  :kaapeliKytkin   (some? (some #{"tietoliikennekaapeli"} liittymatyypit))}]
      (if (util/contains-value? result true)
        result
        {}))
    (if (util/contains-value? verkostoliittymat true)
      {:sahkoKytkin     (true? (-> verkostoliittymat :sahkoKytkin))
       :maakaasuKytkin  (true? (-> verkostoliittymat :maakaasuKytkin))
       :viemariKytkin   (true? (-> verkostoliittymat :viemariKytkin))
       :vesijohtoKytkin (true? (-> verkostoliittymat :vesijohtoKytkin))
       :kaapeliKytkin   (true? (-> verkostoliittymat :kaapeliKytkin))}
      {})))

(defn- vaestonsuoja-capacity [rakennuskohteen-osa]
  (some->> (get-in rakennuskohteen-osa [:vaestonsuoja])
           vals
           (keep (comp ss/blank-as-nil :kapasiteetti))
           (map util/->int)
           (reduce + 0)))

(defn- ^:ryhti varusteet-for [toimenpide rakennuskohteen-osa ryhti-data-2026 vaestonsuoja]
  (let [varusteet-map (if ryhti-data-2026
                        (let [varusteet (get-in rakennuskohteen-osa [:tekniset-tiedot :varuste])]
                          (reduce
                            (fn [acc [_ v]]
                              (let [tyyppi (:varustetyyppi v)
                                    maara  (-> v :maara util/->int)]
                                (case tyyppi
                                  "sahko"                   (assoc acc :sahkoKytkin (pos? maara))
                                  "kaasu"                   (assoc acc :kaasuKytkin (pos? maara))
                                  "jatevesiviemari"         (assoc acc :viemariKytkin (pos? maara))
                                  "vesijohto"               (assoc acc :vesijohtoKytkin (pos? maara))
                                  "lammin-vesi"             (assoc acc :lamminvesiKytkin (pos? maara))
                                  "aurinkopaneeli"          (assoc acc :aurinkopaneeliKytkin (pos? maara))
                                  "hissi"                   (assoc acc :hissiKytkin (pos? maara))
                                  "koneellinen-ilmastointi" (assoc acc :koneellinenilmastointiKytkin (pos? maara))
                                  "sauna"                   (assoc acc :saunoja (positive-integer maara))
                                  acc)))
                            {}
                            (tools/unwrapped varusteet)))
                        (:varusteet toimenpide))]
    (if (or (some true? (vals varusteet-map))
            (util/not-empty-or-nil? vaestonsuoja)
            (util/not-empty-or-nil? (:saunoja varusteet-map)))
      {:sahkoKytkin                  (true? (:sahkoKytkin varusteet-map))
       :kaasuKytkin                  (true? (:kaasuKytkin varusteet-map))
       :viemariKytkin                (true? (:viemariKytkin varusteet-map))
       :vesijohtoKytkin              (true? (:vesijohtoKytkin varusteet-map))
       :lamminvesiKytkin             (true? (:lamminvesiKytkin varusteet-map))
       :aurinkopaneeliKytkin         (true? (:aurinkopaneeliKytkin varusteet-map))
       :hissiKytkin                  (true? (:hissiKytkin varusteet-map))
       :koneellinenilmastointiKytkin (true? (:koneellinenilmastointiKytkin varusteet-map))
       :saunoja                      (:saunoja varusteet-map)
       :vaestonsuoja                 (if ryhti-data-2026
                                       (vaestonsuoja-capacity rakennuskohteen-osa)
                                       vaestonsuoja)}
      {})))

(defn- get-rakennus [application {created :created info :schema-info toimenpide :data :as doc}]
  (let [ryhti-data-2026               (:ryhti-data-2026 application)
        {:keys [kaytto
                rakenne lammitys
                luokitus huoneistot
                rakennuskohteen-osa]} toimenpide
        kantava-rakennus-aine-map     (if ryhti-data-2026
                                        (let [primary-kantava-rakennusaine (get-primary (-> rakennuskohteen-osa :materiaalitiedot :kantavaRakennusaine))]
                                          (muu-select-map :muuRakennusaine (:muuRakennusaine primary-kantava-rakennusaine)
                                                          :rakennusaine (:rakennusaine primary-kantava-rakennusaine)))
                                        (muu-select-map :muuRakennusaine (:muuRakennusaine rakenne)
                                                        :rakennusaine (:kantavaRakennusaine rakenne)))
        lammitys                      (if ryhti-data-2026
                                        (get-ryhti-lammitys rakennuskohteen-osa)
                                        lammitys)
        lammonlahde-map               (muu-select-map
                                        :muu (-> lammitys :muu-lammonlahde)
                                        :polttoaine (if (= "kiviihiili koksi tms" (-> lammitys :lammonlahde))
                                                      (str (-> lammitys :lammonlahde) ".")
                                                      (-> lammitys :lammonlahde)))
        julkisivu-map                 (if ryhti-data-2026
                                        (let [primary-julkisivu (get-primary (-> rakennuskohteen-osa :materiaalitiedot :julkisivu))]
                                          (muu-select-map :muuMateriaali (:muuMateriaali primary-julkisivu)
                                                          :julkisivumateriaali (:julkisivumateriaali primary-julkisivu)))
                                        (muu-select-map :muuMateriaali (:muuMateriaali rakenne)
                                                        :julkisivumateriaali (:julkisivu rakenne)))

        lammitystapa                  (:lammitystapa lammitys)
        huoneistot-data               (when-not (some-> info :op :name
                                                        (ops/get-operation-metadata :no-kuntagml-apartments))
                                        (if ryhti-data-2026
                                          (get-huoneisto-data (:huoneistot rakennuskohteen-osa) (:name info) (muutoslupa? application) ryhti-data-2026)
                                          (get-huoneisto-data huoneistot (:name info) (muutoslupa? application) ryhti-data-2026)))
        huoneistot                    (when huoneistot-data
                                        (if (org/pate-scope? application)
                                          {:huoneisto         huoneistot-data
                                           :asuntojenPintaala (get-huoneistot-pintaala huoneistot-data)
                                           :asuntojenLkm      (get-huoneistot-lkm huoneistot-data)}
                                          {:huoneisto huoneistot-data}))
        vaestonsuoja-value            (ss/trim (get-in toimenpide [:varusteet :vaestonsuoja]))
        vaestonsuoja                  (when (ss/numeric? vaestonsuoja-value) (util/->int vaestonsuoja-value))
        verkostoliittymat             (verkostoliittymat-for toimenpide rakennuskohteen-osa ryhti-data-2026)
        varusteet                     (varusteet-for toimenpide rakennuskohteen-osa ryhti-data-2026 vaestonsuoja)
        organization                  (org/get-application-organization application)
        rakennuksen-tiedot-basic-info (util/assoc-when
                                        {:kayttotarkoitus                     (if ryhti-data-2026
                                                                                (-> (get-in rakennuskohteen-osa [:kayttotiedot :kayttotarkoitus])
                                                                                    get-primary
                                                                                    :tyyppi)
                                                                                (:kayttotarkoitus kaytto))
                                         :rakentamistapa                      (if ryhti-data-2026
                                                                                (-> rakennuskohteen-osa
                                                                                    (get-in [:materiaalitiedot :rakentamistapa])
                                                                                    {"paikalla-tehty" "paikalla"
                                                                                     "elementti"      "elementti"
                                                                                     nil              nil})
                                                                                (:rakentamistapa rakenne))
                                         :verkostoliittymat                   verkostoliittymat
                                         :lammitystapa                        (cond
                                                                                (= lammitystapa "suorasahkö")   "suora sähkö"
                                                                                (= lammitystapa "eiLammitysta") "ei lämmitystä"
                                                                                :else                           lammitystapa)
                                         :varusteet                           varusteet
                                         :liitettyJatevesijarjestelmaanKytkin (if ryhti-data-2026
                                                                                (->> rakennuskohteen-osa :tekniset-tiedot :verkostoliittymat
                                                                                     (some (fn [[_ {:keys [verkostoliittymatyyppi liitetty]}]]
                                                                                             (when (= verkostoliittymatyyppi "jatevesi")
                                                                                               liitetty)))
                                                                                     true?)
                                                                                (true? (-> toimenpide :varusteet :liitettyJatevesijarjestelmaanKytkin)))
                                         :rakennustunnus                      (get-rakennustunnus toimenpide application info)}
                                        :rakennusluokka                       (if ryhti-data-2026
                                                                                (:rakennusluokka kaytto)
                                                                                (when (tools/has-rakennusluokka? application organization)
                                                                                  (:rakennusluokka kaytto))))
        luokitus                      (if ryhti-data-2026
                                        (ryhti-luokitus rakennuskohteen-osa)
                                        luokitus)

        rakennuksen-tiedot         (merge
                                     (get-mitat doc ryhti-data-2026)
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
                                       {:asuinhuoneistot huoneistot
                                        ;; duplicate for 2.2.6 as field was renamed
                                        :huoneistot      huoneistot})
                                     ;; TODO: tänne ne korkeus + kuutiot?
                                     (util/assoc-when-pred rakennuksen-tiedot-basic-info util/not-empty-or-nil?
                                                           :kantavaRakennusaine kantava-rakennus-aine-map
                                                           :lammonlahde lammonlahde-map
                                                           :julkisivu julkisivu-map))
        tilapainenRakennusKytkin   (:tilapainenRakennusKytkin kaytto)
        valiaikainenRakennusKytkin (:valiaikainenRakennusKytkin kaytto)]
    (util/assoc-when-pred {:yksilointitieto   (-> info :op :id)
                           :alkuHetki         (date/xml-datetime  created)
                           :sijaintitieto     {:Sijainti {:tyhja empty-tag}}
                           :rakentajatyyppi   (if ryhti-data-2026
                                                (if (:rakentaja-tyyppi kaytto)
                                                  "liiketaloudellinen"
                                                  "muu")
                                                (:rakentajaTyyppi kaytto))
                           :rakennuksenTiedot rakennuksen-tiedot}
                          util/not-empty-or-nil?
                          :omistajatieto (remove nil? (for [m (vals (:rakennuksenOmistajat toimenpide))] (get-rakennuksen-omistaja m)))
                          :kokoontumistilanHenkilomaara (-> toimenpide :varusteet :kokoontumistilanHenkilomaara)
                          :esteetonKytkin (:esteetonKytkin kaytto)
                          :tilapainenRakennusKytkin tilapainenRakennusKytkin
                          :tilapainenRakennusvoimassaPvm (when tilapainenRakennusKytkin
                                                           (date/xml-date (:tilapainenRakennusvoimassaPvm kaytto)))
                          :valiaikainenRakennusKytkin valiaikainenRakennusKytkin
                          :valiaikainenRakennusvoimassaPvm (when valiaikainenRakennusKytkin
                                                             (date/xml-date (:valiaikainenRakennusvoimassaPvm kaytto))))))

(defn- get-rakennus-data
  "Canonical building representation. The source data is a combination of `doc` and its
  `document-buildings` counterpart if one exists. The `doc` has the higher priority and its
  values (including blanks and nils) are never overwritten. "
  [{:keys [document-buildings] :as application} doc]
  (let [ryhti?            (ryhti-transform/ryhti-doc? doc)
        ;; Just in case.
        application       (assoc application :ryhti-data-2026 ryhti?)
        doc-vtj-prt       (if ryhti?
                            (some-> doc :data :pysyva-rakennustunnus ss/blank-as-nil)
                            (some-> doc :data :valtakunnallinenNumero ss/blank-as-nil))
        document-building (when-not ryhti?
                            ;; Merge is not needed for Ryhti documents, since there aren't
                            ;; any extra attributes after transform. Thus, the document
                            ;; data is authoritative. Also, the Ryhti schemas are not
                            ;; suitable for straight-forward deep merges (many repeating
                            ;; groups).
                             (some-<> doc-vtj-prt
                                      (util/find-by-key :vtj-prt <> document-buildings)
                                      :building
                                      util/strip-blanks))
        building          (get-rakennus application
                               (cond->> doc
                                 document-building (util/deep-merge
                                                     {:data document-building})
                                 ;; Nil values must be present above, otherwise the explicitly
                                 ;; emptied fields could be erroneously filled with
                                 ;; `document-building` data. We need to retain the nils,
                                 ;; otherwise unwrapping breaks in the next steps.
                                 true              util/strip-empty-maps))]
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
    "rakennuksen pääasiallinen käyttötarkoitusmuutos"
    v))

(defn- get-rakennuksen-muuttaminen-toimenpide [{data :data :as rakennuksen-muuttaminen-doc}
                                               application]
  {:Toimenpide {:muuMuutosTyo  (-> (get-toimenpiteen-kuvaus rakennuksen-muuttaminen-doc)
                                   (assoc :perusparannusKytkin            (true? (:perusparannuskytkin data))
                                          :rakennustietojaEimuutetaKytkin (true? (:rakennustietojaEimuutetaKytkin data))
                                          :muutostyonLaji                 (fix-typo-in-kayttotarkotuksen-muutos (:muutostyolaji data)))
                                   (util/assoc-when :raukeamisPvm (get-raukeamis-pvm application rakennuksen-muuttaminen-doc)))
                :rakennustieto (get-rakennus-data application rakennuksen-muuttaminen-doc)}
   :created    (:created rakennuksen-muuttaminen-doc)})

(defn- get-rakennustietojen-korjaus-toimenpide [rakennustietojen-korjaus-doc application]
  {:Toimenpide {:muuMuutosTyo  (-> (get-toimenpiteen-kuvaus rakennustietojen-korjaus-doc)
                                   (assoc :perusparannusKytkin            false
                                          :rakennustietojaEimuutetaKytkin false
                                          :muutostyonLaji                 schemas/muumuutostyo)
                                   (util/assoc-when :raukeamisPvm (get-raukeamis-pvm application rakennustietojen-korjaus-doc)))
                :rakennustieto (get-rakennus-data application rakennustietojen-korjaus-doc)}
   :created    (:created rakennustietojen-korjaus-doc)})

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

(defn- get-rakennelma-data [doc application]
  (let [toimenpide (-> doc :data)
        tilapainenRakennelmaKytkin (:tilapainenRakennelmaKytkin toimenpide)]
    {:Rakennelma (util/assoc-when-pred
                  {:yksilointitieto (-> doc :schema-info :op :id)
                   :alkuHetki (date/xml-datetime (:created doc))
                   :sijaintitieto {:Sijainti {:tyhja empty-tag}}
                   :kokonaisala (when-not (:ryhti-data-2026 application)
                                  (-> toimenpide :kokonaisala))
                   :kayttotarkoitus (if (:ryhti-data-2026 application)
                                      (-> toimenpide :kaytto :rakennelman-kayttotarkoitus)
                                      (-> toimenpide :kayttotarkoitus))
                   :kuvaus {:kuvaus (-> toimenpide :kuvaus)}
                   :tunnus {:jarjestysnumero (vtj-prt-index application
                                                            (:valtakunnallinenNumero toimenpide))}
                   :kiinttun (:propertyId application)}
                  util/not-empty-or-nil?
                  :kokoontumistilanHenkilomaara (-> toimenpide :kokoontumistilanHenkilomaara)
                  :tilapainenRakennelmaKytkin (:tilapainenRakennelmaKytkin toimenpide)
                  :tilapainenRakennelmavoimassaPvm (when tilapainenRakennelmaKytkin
                                                     (date/xml-date (:tilapainenRakennelmavoimassaPvm toimenpide))))}))

(defn get-kaupunkikuvatoimenpide [kaupunkikuvatoimenpide-doc application]
  {:Toimenpide {:kaupunkikuvaToimenpide (get-toimenpiteen-kuvaus kaupunkikuvatoimenpide-doc)
                :rakennelmatieto        (get-rakennelma-data kaupunkikuvatoimenpide-doc application)}
   :created (:created kaupunkikuvatoimenpide-doc)})

(def maalampokaivo-kayttotarkoitus "Maalämpöpumppujärjestelmä")

(defn get-maalampokaivo [kaupunkikuvatoimenpide-doc application]
  (-> (get-kaupunkikuvatoimenpide kaupunkikuvatoimenpide-doc application)
      (util/dissoc-in [:Toimenpide :rakennelmatieto :Rakennelma :kokonaisala])
      (assoc-in [:kayttotarkoitus] maalampokaivo-kayttotarkoitus)))

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
                           ;; Needed in order to pass KuntaGML validation
                           (assoc-in basic-switch
                                     [:laajennuksentiedot :huoneistoala]
                                     [{:kayttotarkoitusKoodi "ei tiedossa"}])]

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

(def area-mapping
  "Translate document schema enums to KuntaGML enums"
  {"aurinkopaneelikentta" "Aurinkopaneelikenttä"
   "kaatopaikka"          "Kaatopaikka"
   "puisto"               "Puisto"
   "pysakointialue"       "Pysäköintialue"
   "tapahtuma-alue"       "Tapahtuma-alue"
   "tori"                 "Tori"
   "urheilualue"          "Urheilualue"
   "varastointialue"      "Varastointialue"
   "muu-alue"             "Muu maisemaa muuttava toimenpide"})

(defn- first-non-blank
  "Returns first non-blank value for a kw-path in `data`. Nil, if none found."
  [data & kw-paths]
  (some #(some->> (util/split-kw-path %)
                  (get-in data)
                  ss/blank-as-nil)
        kw-paths))

(defn- get-erityista-toimintaa-alue-data [{:keys [created schema-info data]}]
  {:ErityistaToimintaavartenRakennettavaAlue
   {:alkuHetki       (date/xml-datetime created)
    :alueenPintaAla  (first-non-blank data
                                      :mitat.pintaAla ;; Ryhti
                                      :pintaAla)
    :alueenTyyppi    (get area-mapping
                          (first-non-blank data
                                           :kaytto.erityista-toimintaa-tyyppi ;; Ryhti
                                           :tyyppi))
    :kuvaus          {:kuvaus (:kuvaus data)}
    :sijaintitieto   {:Sijainti {:tyhja empty-tag}}
    :yksilointitieto (-> schema-info :op :id)}})

(defn get-uusi-erityista-toimintaa [doc application]
  ;; TODO: LPK-7217 : Add additional fields once we get a new version of the 2.2.6 schema
  {:Toimenpide {:uusi                                          (-> (get-toimenpiteen-kuvaus doc)
                                                                   (util/assoc-when :raukeamisPvm (get-raukeamis-pvm application doc)))
                :erityistaToimintaavartenRakennettavaAlueTieto (get-erityista-toimintaa-alue-data doc)}
   :created    (:created doc)})

(defn- get-toimenpide-with-count [n toimenpide]
  (walk/postwalk #(if (and (map? %) (contains? % :jarjestysnumero) (not (:jarjestysnumero %)))
                            (assoc % :jarjestysnumero n)
                            %) toimenpide))

(defn- get-sijoittamislupa-toimenpide
  [{:keys [created] :as doc} application]
  {:Toimenpide {:sijoittamislupa (get-toimenpiteen-kuvaus doc)
                :rakennustieto   (get-rakennus-data application doc)}
   :created    created})

(defn- get-sijoittamislupa-toimenpide-common
  "Common builder for sijoittamislupa variants. The `planning-key` determines the top-level
  toimenpide tag (e.g. `:sijoittamislupa` or `:puhtaanSiirtymanSijoittamislupa`)."
  [{:keys [created] :as doc} application planning-key]
  (let [planning-type {planning-key (get-toimenpiteen-kuvaus doc)}
        ;; Assoc building data depending on permit type
        toimenpide    (when-let [permit-type (some-> doc :data :hankkeen-tyyppi keyword)]
                        (let [permit-type-data               (some-> doc :data permit-type)
                              doc-with-only-permit-type-data (assoc doc :data permit-type-data)]
                          (case permit-type
                            :uusiRakennus
                            (assoc planning-type
                                   :rakennustieto
                                   (get-rakennus-data application doc-with-only-permit-type-data))

                            :erityista-toimintaa-varten-rakennettava-alue
                            (assoc planning-type
                                   :erityistaToimintaavartenRakennettavaAlueTieto
                                   (get-erityista-toimintaa-alue-data doc-with-only-permit-type-data))

                            :rakennelma
                            (assoc planning-type
                                   :rakennelmatieto
                                   (get-rakennelma-data doc-with-only-permit-type-data application)))))]
    {:Toimenpide (or toimenpide
                     planning-type)
     :created    created}))

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

(defmethod operation-canonical :purkamisilmoitus [application document]
  ;; TODO: Purkamisilmoitus in KuntaGML?

  ;; Demolition notice is delivered to the backend system using the
  ;; same message as demolition permit. The main difference is that we
  ;; need to add extra key value pair to the message with key "asia"
  ;; and value "purkamisilmoitus".
  (get-purku-toimenpide document application))

(defmethod operation-canonical :rakennuskohteenPurkaminen-purkamisilmoitus [application document]
  ;; TODO: Purkamisilmoitus in KuntaGML?  This is the new demolition
  ;; notice that uses Ryhti demolition permit-compatible demolition
  ;; document schema.

  ;; Demolition notice is delivered to the backend system using the
  ;; same message as demolition permit. The main difference is that we
  ;; need to add extra key value pair to the message with key "asia"
  ;; and value "purkamisilmoitus".
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

(defmethod operation-canonical :erityista-toimintaa-varten-rakennettava-alue [application document]
  (get-uusi-erityista-toimintaa document application))

(defmethod operation-canonical :sijoittamislupa [application document]
  (get-sijoittamislupa-toimenpide document application))

(defmethod operation-canonical :sijoittamislupa-v2 [application document]
  (let [permit-type       (some-> document :data :hankkeen-tyyppi keyword)
        clean-transition? (when permit-type
                            (-> document :data permit-type :puhtaan-siirtyman-lupa :puhtaan-siirtyman-lupa-enabled))
        planning-key      (if clean-transition?
                            :puhtaanSiirtymanSijoittamislupa
                            :sijoittamislupa)]
    (get-sijoittamislupa-toimenpide-common document application planning-key)))

(defmethod operation-canonical :sijoittamislupa-v3 [application document]
  (get-sijoittamislupa-toimenpide-common document application :sijoittamislupa))

(defmethod operation-canonical :puhtaan-siirtyman-sijoittamislupa [application document]
  (get-sijoittamislupa-toimenpide-common document application :puhtaanSiirtymanSijoittamislupa))

;; Ryhti specific operations

(defmethod operation-canonical :uusiRakennuskohde [application document]
  (case (-> document :data :kaytto :kohteen-laji)
    "etvra"      (get-uusi-erityista-toimintaa document application)
    "rakennelma" (get-kaupunkikuvatoimenpide document application)
    "rakennus"   (get-uusi-toimenpide document application)
    (get-uusi-toimenpide document application)))

(defmethod operation-canonical :rakennuskohteenPurkaminen [application document]
  (get-purku-toimenpide document application))

(defmethod operation-canonical :rakennuskohteenLaajentaminen [application document]
  (get-rakennuksen-laajentaminen-toimenpide document application))

(defmethod operation-canonical :rakennuskohteenMuuttaminen [application document]
  (get-rakennuksen-muuttaminen-toimenpide document application))

(defmethod operation-canonical :rakennuskohteenTietojenKorjaus [application document]
  (get-rakennustietojen-korjaus-toimenpide document application))

(defn- get-operations [application]
  (->> (app-utils/get-sorted-operation-documents application)
       ;; Nils cannot be removed yet, otherwise `tools/unwrapped` would not work in the
       ;; later steps.
       (ss/stringwalk ss/blank-as-nil)
       (keep (partial operation-canonical application))
       (map-indexed #(get-toimenpide-with-count (inc %1) %2))
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
                                 (:aloitusoikeus documents-by-type)
                                 (:rasite-tai-yhteisjarjestely documents-by-type)
                                 (:uusiRakennuskohde documents-by-type))
        asian-tiedot         (:data (first hankkeen-kuvaus-doc))
        kuvaus               (join-fn [(:kuvaus asian-tiedot)
                                       (compose-kuvaukset (:maisematyo documents-by-type))
                                       (get-paper-texts :kuvaus documents-by-type)])
        poikkeamat           (join-fn [(:poikkeamat asian-tiedot)
                                       (get-paper-texts :poikkeamat documents-by-type)])]
    (cond-> nil
      kuvaus     (assoc-in [:Asiantiedot :rakennusvalvontaasianKuvaus] kuvaus)
      poikkeamat (assoc-in [:Asiantiedot :vahainenPoikkeaminen] poikkeamat))))

(defn- get-kayttotapaus [documents-by-type toimenpiteet]
  (if (and (contains? documents-by-type :maisematyo) (empty? toimenpiteet))
      "Uusi maisematyöhakemus"
      "Uusi hakemus"))

(defn application-to-canonical-operations
  [application]
  (-> application tools/unwrapped get-operations))

(defn- unwrap-with-apartments
  "Apartment data resolution requires unwrapped apartments."
  [{:keys [documents ryhti-data-2026] :as application}]
  (let [application (-> application (dissoc :documents) tools/unwrapped)
        documents   (map (fn [doc]
                           (let [path (if ryhti-data-2026
                                        [:data :rakennuskohteen-osa :huoneistot]
                                        [:data :huoneistot])]
                             (if-let [apartments (get-in doc path)]
                               (assoc-in (tools/unwrapped doc)
                                         path apartments)
                               (tools/unwrapped doc))))
                         documents)]
    (assoc application :documents documents)))

(defn rakval-application-to-canonical
  "Transforms application mongodb-document to canonical model."
  [application lang]
  (let [tj-v3?            (util/=as-kw :TJ-V3 (:permitType application))
        application       (cond-> (unwrap-with-apartments application)
                            ;; This is needed as is for generating tilatieto for foreman-v3
                            tj-v3? (assoc :changeLog (:changeLog application)))
        link-permits      (:linkPermitData application)
        documents-by-type (stripped-documents-by-type application)
        toimenpiteet      (get-operations application)
        operation-name    (-> application :primaryOperation :name)]
    {:Rakennusvalvonta
     {:toimituksenTiedot (toimituksen-tiedot application lang)
      :rakennusvalvontaAsiatieto
      {:RakennusvalvontaAsia
       (cond-> (util/assoc-when-pred
                 {:kasittelynTilatieto (if tj-v3?
                                         (get-foreman-v3-state application)
                                         (get-state application))
                  :luvanTunnisteTiedot (if tj-v3?
                                         ;; leave kuntalupatunnus out for TJ-V3
                                         (lupatunnus (:id application) (:submitted application) nil)
                                         (lupatunnus-with-vrktunnus application))
                  :osapuolettieto      (osapuolet application documents-by-type lang)
                  :kayttotapaus        (if (muutoslupa? application)
                                         "Rakentamisen aikainen muutos"
                                         (condp = operation-name
                                           "tyonjohtajan-nimeaminen"               "Uuden työnjohtajan nimeäminen"
                                           "tyonjohtajan-nimeaminen-v2"            "Uuden työnjohtajan nimeäminen"
                                           "tyonjohtajan-nimeaminen-v3"            "Uuden työnjohtajan nimeäminen"
                                           "tyonjohtajan-irtisanominen"            "Työnjohtajan irtisanominen"
                                           "tyonjohtajan-irtisanominen-v3"         "Työnjohtajan irtisanominen"
                                           "tyonjohtajan-vastuiden-paattaminen"    "Työnjohtajan vastuun päättäminen"
                                           "tyonjohtajan-vastuiden-paattaminen-v3" "Työnjohtajan vastuun päättäminen"
                                           "suunnittelijan-nimeaminen"             "Uuden suunnittelijan nimeäminen"
                                           "jatkoaika"                             "Jatkoaikahakemus"
                                           "raktyo-aloit-loppuunsaat"              "Jatkoaikahakemus"
                                           "aloitusoikeus"                         "Uusi aloitusoikeus"
                                           "rasite-tai-yhteisjarjestely"           "Uusi rakennusrasitehakemus"
                                           (get-kayttotapaus documents-by-type toimenpiteet)))
                  :asianTiedot         (get-asian-tiedot documents-by-type)
                  :lisatiedot          (get-lisatiedot-with-asiakirjat-toimitettu application lang)}
                 util/not-empty-or-nil?
                 :viitelupatieto (map get-viitelupatieto link-permits)
                 :avainsanaTieto (get-avainsanaTieto application)
                 :menettelyTOS (:tosFunctionName application)
                 :rakennuspaikkatieto (get-rakennuspaikkatieto documents-by-type application)
                 :toimenpidetieto toimenpiteet
                 :lausuntotieto (get-statements (:statements application))
                 :avainArvoTieto (seq (get-avain-arvo-parit application)))

         (#{"tyonjohtajan-nimeaminen"
            "suunnittelijan-nimeaminen"
            "tyonjohtajan-irtisanominen"
            "tyonjohtajan-vastuiden-paattaminen"
            "tyonjohtajan-nimeaminen-v3"
            "tyonjohtajan-irtisanominen-v3"} operation-name)
         (dissoc :rakennuspaikkatieto :toimenpidetieto :lausuntotieto))}}}))

(defmethod application->canonical :R [application language]
  (-> application
      (rakval-application-to-canonical language)
      ss/trimwalk))

(defmethod application->canonical :TJ-V3 [application language]
  (-> application
      (rakval-application-to-canonical language)
      ss/trimwalk))

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
      "lämpö-, vesi- ja ilmanvaihtolaitteiden katselmus" "lämpö-, vesi- ja ilmanvaihtolaitteiden katselmus"
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
      (util/all-blank? (-> building :rakennus vals))))

(defn- non-blank-eq
  "True if both non-blank and equal
  False if both non-blank and not equal
  nil if either blank"
  [a b]
  (let [a (some-> a ss/trim ss/blank-as-nil)
        b (some-> b ss/trim ss/blank-as-nil)]
    (when (and a b)
      (= a b))))

(defn- best-building-match
  "Returns the best matching application building for given task building. If there are two
  equally good top matches the first one is selected. Blank values are ignored but
  non-blank differences are penalized. For example, the buildings cannot match if they
  both have non-blank but not equal national ids (VTJ-PRT)."
  [buildings
   {national-id :valtakunnallinenNumero
    rakennusnro :rakennusnro
    local-id    :kunnanSisainenPysyvaRakennusnumero}
   property-id]
  (letfn [(score-change [n a b]
            (case (non-blank-eq a b)
              true  n
              false (- n)
              0))]
    (some->> buildings
             (util/filter-by-key :propertyId property-id)
             (reduce (fn [{:keys [highscore]:as acc}
                          {:keys [nationalId buildingId
                                  localId localShortId]
                           :as   building}]
                       (let [score (->> [[8 national-id nationalId]
                                         [4 national-id buildingId]
                                         [2 local-id localId]
                                         [1 rakennusnro localShortId]]
                                        (map (partial apply score-change))
                                        (apply +))]
                         (if (> score highscore)
                           {:highscore score :building building}
                           acc)))
                     {:highscore 0})
             :building)))

(defn- enrich-review-building [{:keys [propertyId buildings]}
                               {{national-id :valtakunnallinenNumero
                                 kiinttun    :kiinttun
                                 :as         rakennus} :rakennus
                                :as                    building}]
  (when rakennus
    (let [property-id  (or (not-empty kiinttun) propertyId)
          app-building (best-building-match buildings rakennus property-id)]
      (update building :rakennus util/assoc-when-pred
              ss/not-blank?
              :kiinttun property-id
              :valtakunnallinenNumero (when (ss/blank? national-id) (:nationalId app-building))
              :operationId (:operationId app-building)
              :description (:description app-building)
              :jarjestysnumero (:index app-building)))))

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
  (let [{{{pito-pvm      :pitoPvm
           pitaja        :pitaja
           pitajaDetails :pitajaDetails
           lasnaolijat   :lasnaolijat
           poikkeamat    :poikkeamat
           tila          :tila
           tiedoksianto  :tiedoksianto
           huomautukset  :huomautukset} :katselmus
          katselmuksen-laji             :katselmuksenLaji
          vaadittu-lupaehtona           :vaadittuLupaehtona
          rakennus                      :rakennus} :data
         task-name :taskname
         :as  task} (tools/unwrapped task)

        buildings       (->> (vals rakennus)
                             (remove (fn [{:keys [rakennus tila]}]
                                       (or (util/empty-or-nil? (:tila tila))
                                           (every? util/emptyish? (vals rakennus)))) )
                             (map (partial enrich-review-building application))
                             (remove bad-review-building?))
        application     (tools/unwrapped application)
        ryhti-data-2026 (:ryhti-data-2026 application)
        ;; we don't check the organization if they use the code-review-list because that breaks existing tests
        pitaja-value    (cond
                          (and ryhti-data-2026 (:code pitaja))
                          (:code pitaja)

                          ryhti-data-2026
                          (ss/trim-join " " (:etunimi pitajaDetails) (:sukunimi pitajaDetails))

                          (map? pitaja)
                          (:code pitaja)

                          :else
                          pitaja)
        katselmustyyppi (katselmusnimi-to-type katselmuksen-laji :katselmus)]

    {:Rakennusvalvonta
     {:toimituksenTiedot (toimituksen-tiedot application lang)
      :rakennusvalvontaAsiatieto
      {:RakennusvalvontaAsia
       {:kasittelynTilatieto (get-state application)
        :luvanTunnisteTiedot (lupatunnus application)
        :osapuolettieto      {:Osapuolet
                              {:osapuolitieto
                               {:Osapuoli
                                {:kuntaRooliKoodi "Ilmoituksen tekijä"
                                 :henkilo         {:nimi             {:etunimi  (:firstName user)
                                                                      :sukunimi (:lastName user)}
                                                   :osoite           {:osoitenimi           {:teksti (or (:street user) "")}
                                                                      :postitoimipaikannimi (:city user)
                                                                      :postinumero          (:zip user)}
                                                   :sahkopostiosoite (:email user)
                                                   :puhelin          (:phone user)}}}}}
        :katselmustieto      {:Katselmus (-> {:pitoPvm                         (date/xml-date pito-pvm)
                                              :katselmuksenLaji                katselmustyyppi
                                              :vaadittuLupaehtonaKytkin        (true? vaadittu-lupaehtona)
                                              :osittainen                      tila
                                              :lasnaolijat                     lasnaolijat
                                              :pitaja                          pitaja-value
                                              :poikkeamat                      poikkeamat
                                              :verottajanTvLlKytkin            tiedoksianto
                                              :tarkastuksenTaiKatselmuksenNimi (ss/trim task-name)}
                                             (util/assoc-when-pred
                                               util/not-empty-or-nil?
                                               :muuTunnustieto (get-review-muutunnustieto task)
                                               ;; This is here for supporting legacy
                                               ;; versions of KuntaGML. Note that the
                                               ;; order of `buildings` is not well defined
                                               :rakennustunnus (get-review-rakennustunnus buildings)
                                               :katselmuksenRakennustieto (map get-review-katselmuksenrakennus buildings)
                                               :huomautukset (get-review-huomautus huomautukset)))}
        :lisatiedot          (get-lisatiedot lang)
        :kayttotapaus        (katselmus-kayttotapaus katselmuksen-laji :katselmus)}}}}))


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
                                 {:kuntaRooliKoodi "Ilmoituksen tekijä"
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
        :kayttotapaus "Liitetiedoston lisäys"}}}}))


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
