(ns lupapalvelu.bim.bimmer.bimmer-to-lp-test
  (:require [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.test :as t]
            [lupapalvelu.bim.bimmer.bimmer-to-lp :as btl]
            [lupapalvelu.bim.bimmer.ifc-field-mappings :as ifc-mappings]
            [lupapalvelu.document.model :as doc-model]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.document.schemas :as schemas]
            [matcher-combinators.test]
            [schema.core :as sc]))

(def example-bimmer-response
  (-> (slurp "dev-resources/bim/example-bimmer-parsed-ifc-response.edn")
      edn/read-string))

(def test-schema
  (schemas/get-schema (schemas/get-latest-schema-version) "uusiRakennuskohde"))

(t/deftest row-mappings-structure-test
  (t/testing "verkostoliittymat entries target the correct table"
    (let [rows (filter #(= :ifc-tbl-verkostoliittymat (:ifc-mapping %))
                       ifc-mappings/row-mappings)]
      (t/is (= 9 (count rows)))
      (t/is (every? #(string? (:verkostoliittymatyyppi (:row %))) rows))))
  (t/testing "lammonlahde entries target the correct table"
    (let [rows (filter #(= :ifc-tbl-lammonlahde (:ifc-mapping %))
                       ifc-mappings/row-mappings)]
      (t/is (= 15 (count rows)))
      (t/is (every? #(string? (:lammonlahdetyyppi (:row %))) rows))))
  (t/testing "lammitystapa entries target the correct table"
    (let [rows (filter #(= :ifc-tbl-lammitystapa (:ifc-mapping %))
                       ifc-mappings/row-mappings)]
      (t/is (= 8 (count rows)))
      (t/is (every? #(string? (:lammitystapatyyppi (:row %))) rows))))
  (t/testing "julkisivu entries target the correct table"
    (let [rows (filter #(= :ifc-tbl-julkisivu (:ifc-mapping %))
                       ifc-mappings/row-mappings)]
      (t/is (= 7 (count rows)))
      (t/is (every? #(string? (:julkisivumateriaali (:row %))) rows))))
  (t/testing "kantavaRakennusaine entries target the correct table"
    (let [rows (filter #(= :ifc-tbl-kantavarakennusaine (:ifc-mapping %))
                       ifc-mappings/row-mappings)]
      (t/is (= 5 (count rows)))
      (t/is (every? #(string? (:rakennusaine (:row %))) rows))))
  (t/testing "varuste entries target the correct table"
    (let [rows (filter #(= :ifc-tbl-varuste (:ifc-mapping %))
                       ifc-mappings/row-mappings)]
      (t/is (= 10 (count rows)))
      (t/is (every? #(string? (:varustetyyppi (:row %))) rows))))
  (t/testing "total row-mappings count is 9 + 15 + 8 + 7 + 5 + 10 = 54"
    (t/is (= 54 (count ifc-mappings/row-mappings)))))

(t/deftest psets-query-test
  (let [result  (btl/psets-query)
        as-sets (update-vals result set)]
    (t/testing "result is valid against PsetsQuery schema"
      (t/is (= result (sc/validate btl/PsetsQuery result))))
    (t/is (= {:IfcSite             #{{:name "FI_Kiinteistö"}}
              :IfcBuilding         #{{:name "FI_Kohde"}
                                     {:name "FI_Talotekniikka"}}
              :IfcSpatialZone      #{{:name "FI_Huoneisto"}
                                     {:name    "FI_Laajuustieto"
                                      :replace {"Tyyppi" {"Huoneistoala" :area
                                                          "Kokonaisala"  :area}}}}
              :IfcSpace            #{{:name "FI_VSS"}
                                     {:name    "FI_Laajuustieto"
                                      :replace {"Tyyppi" {"Kokonaisala"                     :area
                                                          "Kerrosala"                       :area
                                                          "Rakennusoikeudellinen kerrosala" :area
                                                          "Kellariala"                      :area
                                                          "Ullakkoala"                      :area
                                                          "Lämmitetty nettoala"             :area}}}}
              :IfcTransportElement #{{:attributes ["Name"]}
                                     {:name "Pset_TransportElementElevator"}
                                     {:name "ePset_TransportElementElevator"}
                                     {:name "Pset_TransportElementCommon"}}
              :IfcDoor             #{{:attributes ["Name"]}
                                     {:name "Pset_DoorCommon"}
                                     {:name "cPset_RegulatoryCondition"}
                                     {:name "cPset_DoorRegulatory"}}
              :IfcMapConversion    #{{:attributes ["Eastings"
                                                   "Northings"
                                                   "OrthogonalHeight"
                                                   "Scale"
                                                   "XAxisAbscissa"
                                                   "XAxisOrdinate"]}}}
             as-sets))))

(t/deftest ifc->lp-key-test
  (t/testing "lowercases ASCII strings"
    (t/is (= "keittio" (ifc-mappings/ifc->lp-key "Keittio"))))
  (t/testing "converts Finnish/Swedish diacritics to ASCII equivalents before lowercasing"
    (t/is (= "kayttotarkoitus" (ifc-mappings/ifc->lp-key "Käyttötarkoitus")))
    (t/is (= "lisays" (ifc-mappings/ifc->lp-key "Lisäys")))
    (t/is (= "lamminvesi" (ifc-mappings/ifc->lp-key "LämminVesi"))))
  (t/testing "replaces whitespace runs with hyphens to match LP option key convention"
    (t/is (= "paasisaankaynti-porrashuoneeseen" (ifc-mappings/ifc->lp-key "Pääsisäänkäynti porrashuoneeseen")))
    (t/is (= "muu-sisaankaynti" (ifc-mappings/ifc->lp-key "Muu sisäänkäynti"))))
  (t/testing "returns nil for nil input (safe for use in nil-punning chains)"
    (t/is (nil? (ifc-mappings/ifc->lp-key nil)))))

(t/deftest extract-ifc-location-test
  (t/testing "Scale is used to convert model units to target CRS units"
    ;; 500.0 mm / 0.001 = 500 000 m (valid x), 6700.0 mm / 0.001 = 6 700 000 m (valid y)
    (t/is (= {:x 500000.0 :y 6700000.0}
             (btl/extract-ifc-location
               {"IfcMapConversion" [{"Eastings"  500.0
                                     "Northings" 6700.0
                                     "Scale"     0.001}]}))))
  (t/testing "Scale=1 returns raw coordinate values unchanged"
    (t/is (= {:x 500000.0 :y 6700000.0}
             (btl/extract-ifc-location
               {"IfcMapConversion" [{"Eastings"  500000.0
                                     "Northings" 6700000.0
                                     "Scale"     1.0}]}))))
  (t/testing "falls back to raw values when Scale is absent"
    (t/is (= {:x 500000.0 :y 6700000.0}
             (btl/extract-ifc-location
               {"IfcMapConversion" [{"Eastings"  500000.0
                                     "Northings" 6700000.0}]}))))
  (t/testing "valid-x? boundaries: x must be strictly in (10000, 800000)"
    (t/is (nil? (btl/extract-ifc-location {"IfcMapConversion" [{"Eastings" 10000 "Northings" 7000000}]})))
    (t/is (some? (btl/extract-ifc-location {"IfcMapConversion" [{"Eastings" 10001 "Northings" 7000000}]})))
    (t/is (some? (btl/extract-ifc-location {"IfcMapConversion" [{"Eastings" 799999 "Northings" 7000000}]})))
    (t/is (nil? (btl/extract-ifc-location {"IfcMapConversion" [{"Eastings" 800000 "Northings" 7000000}]}))))
  (t/testing "valid-y? boundaries: y must be in [6610000, 7779999]"
    (t/is (nil? (btl/extract-ifc-location {"IfcMapConversion" [{"Eastings" 500000 "Northings" 6609999}]})))
    (t/is (some? (btl/extract-ifc-location {"IfcMapConversion" [{"Eastings" 500000 "Northings" 6610000}]})))
    (t/is (some? (btl/extract-ifc-location {"IfcMapConversion" [{"Eastings" 500000 "Northings" 7779999}]})))
    (t/is (nil? (btl/extract-ifc-location {"IfcMapConversion" [{"Eastings" 500000 "Northings" 7780000}]}))))
  (t/testing "returns nil when IfcMapConversion is absent"
    (t/is (nil? (btl/extract-ifc-location {}))))
  (t/testing "returns nil when coordinates are absent"
    (t/is (nil? (btl/extract-ifc-location {"IfcMapConversion" [{}]}))))
  (t/testing "returns nil when coordinates are non-numeric"
    (t/is (nil? (btl/extract-ifc-location
                  {"IfcMapConversion" [{"Eastings"  "not-a-number"
                                        "Northings" 99.0}]}))))
  (t/testing "example-bimmer-response fixture: exact ETRS-TM35FIN coordinates"
    (t/is (= {:x 395233.1000000003 :y 6706018.6}
             (btl/extract-ifc-location (:properties example-bimmer-response))))))

(t/deftest merge-psets-queries-test
  (t/testing "merges queries for different entities independently"
    (t/is (= {:IfcBuilding [{:name "FI_Kohde"}]
              :IfcSite     [{:name "FI_Kiinteistö"}]}
             (btl/merge-psets-queries [{:IfcBuilding [{:name "FI_Kohde"}]}
                                       {:IfcSite [{:name "FI_Kiinteistö"}]}]))))
  (t/testing "concatenates pset entries for the same entity"
    (t/is (= {:IfcBuilding [{:name "FI_Kohde"} {:name "FI_Talotekniikka"}]}
             (btl/merge-psets-queries [{:IfcBuilding [{:name "FI_Kohde"}]}
                                       {:IfcBuilding [{:name "FI_Talotekniikka"}]}]))))
  (t/testing "deduplicates identical pset entries"
    (t/is (= {:IfcBuilding [{:name "FI_Kohde"}]}
             (btl/merge-psets-queries [{:IfcBuilding [{:name "FI_Kohde"}]}
                                       {:IfcBuilding [{:name "FI_Kohde"}]}
                                       {:IfcBuilding [{:name "FI_Kohde"}]}]))))
  (t/testing "returns empty map for empty input"
    (t/is (= {} (btl/merge-psets-queries [])))))

(t/deftest build-fields-data-test
  (let [properties (:properties example-bimmer-response)
        result     (btl/build-fields-data test-schema properties)]
    (t/testing "produces scalar fields at the correct nested LP paths"
      (t/is (= {:kerrosluku         "4"
                :korkeus            "66.55"
                :tilavuus           "4022"
                :suhde-maan-pintaan "olennaisesti-kokonaan-pinnan-ylla"}
               (get-in result [:rakennuskohteen-osa :ulkokuorentiedot])))
      (t/is (= {:kayttoika "100"}
               (get-in result [:rakennuskohteen-osa :kayttotiedot])))
      (t/is (= {:energialuokka "A" :energialuokan-alaindeksi "2018"}
               (select-keys (get-in result [:rakennuskohteen-osa :energiatiedot])
                            [:energialuokka :energialuokan-alaindeksi]))))
    (t/testing "Group C: talousvesityyppi and jatevesienkasittely are mapped as scalar selects"
      (t/is (= {:talousvesityyppi    "verkostoliittymat"
                :jatevesienkasittely "verkostoliittyma"}
               (select-keys (get-in result [:rakennuskohteen-osa :tekniset-tiedot])
                            [:talousvesityyppi :jatevesienkasittely]))))
    (t/testing "Group C: jaahdytystapa creates row :0 in repeating jaahdytystapa table"
      (t/is (= {:0 {:jaahdytystapatyyppi "ei-kiinteaa-jaahdytyslaitetta"}}
               (get-in result [:rakennuskohteen-osa :energiatiedot :jaahdytystapa]))))
    (t/testing "Group C: ilmanvaihtotapa creates row :0 in repeating ilmanvaihtotavan-laji table"
      (t/is (= {:0 {:ilmanvaihtotyyppi "koneellinen-tulo-ja-poistoilmanvaihto"}}
               (get-in result [:rakennuskohteen-osa :tekniset-tiedot :ilmanvaihtotavan-laji]))))
    (t/testing "populates repeating huoneistot table with indexed keys :0, :1, …"
      (let [huoneistot (get-in result [:rakennuskohteen-osa :huoneistot])]
        (t/is (= 7 (count huoneistot)))
        (t/is (every? keyword? (keys huoneistot)))
        (t/is (= {:0 {:huoneistoTyyppi "asuinhuoneisto"}
                  :6 {:huoneistoTyyppi "toimitila"}}
                 (-> huoneistot
                     (select-keys [:0 :6])
                     (update :0 select-keys [:huoneistoTyyppi])
                     (update :6 select-keys [:huoneistoTyyppi]))))))
    (t/testing "huoneisto jakokirjain is stored when Jakokirjain is present in FI_Huoneisto"
      (let [props-jk  (assoc-in properties ["IfcSpatialZone" 0 "FI_Huoneisto" "Jakokirjain"] "a")
            result-jk (btl/build-fields-data test-schema props-jk)]
        (t/is (= "a" (get-in result-jk [:rakennuskohteen-osa :huoneistot :0 :jakokirjain])))))
    (t/testing "sijaintikerros is stored as string when Sijaintikerros is present in FI_Huoneisto"
      (let [props  (assoc-in properties ["IfcSpatialZone" 0 "FI_Huoneisto" "Sijaintikerros"] 3)
            result (btl/build-fields-data test-schema props)]
        (t/is (match? {:rakennuskohteen-osa {:huoneistot {:0 {:sijaintikerros "3"}}}}
                      result))))
    (t/testing "sijaintikerroksen-tarkenne: known values mapped, unknown value yields nil"
      (let [set-tarkenne (fn [v] (assoc-in properties ["IfcSpatialZone" 0 "FI_Huoneisto" "SijaintikerroksenTarkenne"] v))
            tarkenne     (fn [v] (get-in (btl/build-fields-data test-schema (set-tarkenne v))
                                         [:rakennuskohteen-osa :huoneistot :0 :sijaintikerroksen-tarkenne]))]
        (t/is (= {"Kellari" "kellari"
                  "Ullakko" "ullakko"
                  "Kerros"  nil}
                 {"Kellari" (tarkenne "Kellari")
                  "Ullakko" (tarkenne "Ullakko")
                  "Kerros"  (tarkenne "Kerros")}))))
    (t/testing "does not include any row-mapping tables — those belong to create-ifc-rows-data"
      (t/is (nil? (get-in result [:rakennuskohteen-osa :tekniset-tiedot :verkostoliittymat]))))
    (t/testing "nimi, osoite, postinumero, postitoimipaikka are mapped from FI_Kohde"
      (t/is (= "Housing Timber" (get-in result [:kaytto :nimi])))
      (t/is (= {:katu                 "Myllytie 7"
                :postinumero          "04410"
                :postitoimipaikannimi "Järvenpää"}
               (get-in result [:sijaintitiedot :osoite]))))
    (t/testing "Väliaikainen=false is stored as boolean false (if-some preserves falsy booleans)"
      (t/is (= false (get-in result [:kaytto :valiaikainenRakennusKytkin]))))
    (t/testing "Väliaikainen=true is stored as boolean true"
      (let [props-true  (assoc-in properties ["IfcBuilding" 0 "FI_Kohde" "Väliaikainen"] true)
            result-true (btl/build-fields-data test-schema props-true)]
        (t/is (match? {:valiaikainenRakennusKytkin true}
                      (get-in result-true [:kaytto])))))
    (t/testing "latauspiste scalar fields: latauspisteidenMaara and polkupyorapaikkojenMaara"
      (t/is (= {:latauspisteidenMaara     "0"
                :polkupyorapaikkojenMaara "34"
                :esikaapelointi           false
                :muutJohtoreitit          false
                :putkitus                 false}
               (get-in result [:latauspiste]))))
    (t/testing "latauspiste boolean fields are false when false, present as true when true"
      (let [props-true  (-> properties
                            (assoc-in ["IfcSite" 0 "FI_Kiinteistö" "LatauspisteidenEsikaapelointi"] true)
                            (assoc-in ["IfcSite" 0 "FI_Kiinteistö" "LatauspisteidenMuutJohtoreitit"] true)
                            (assoc-in ["IfcSite" 0 "FI_Kiinteistö" "LatauspisteidenPutkitus"] true))
            result-true (btl/build-fields-data test-schema props-true)]
        (t/is (= {:latauspisteidenMaara     "0"
                  :polkupyorapaikkojenMaara "34"
                  :esikaapelointi           true
                  :muutJohtoreitit          true
                  :putkitus                 true}
                 (get-in result-true [:latauspiste])))))
    (t/testing "kayttajamaara is stored when SuunniteltuKäyttäjämäärä is present"
      (let [props-kayttaja  (assoc-in properties ["IfcBuilding" 0 "FI_Kohde" "SuunniteltuKäyttäjämäärä"] 50)
            result-kayttaja (btl/build-fields-data test-schema props-kayttaja)]
        (t/is (= "50" (get-in result-kayttaja [:rakennuskohteen-osa :kayttotiedot :kayttajamaara])))))
    (t/testing "sisaverkko: OptinenKaapelointi=true is stored; false booleans are stored as false"
      (t/is (= {:yleiskaapeloinninOptinenKaapelointi true
                :antenniverkko                       false
                :olemassaOlevaValokuituverkko        false
                :yleiskaapeloinninParikaapelointi    false}
               (get-in result [:rakennuskohteen-osa :sisaverkko])))
      (let [props-true  (-> properties
                            (assoc-in ["IfcBuilding" 0 "FI_Talotekniikka" "Antenniverkko"] true)
                            (assoc-in ["IfcBuilding" 0 "FI_Talotekniikka" "OlemassaOlevaValokuituverkko"] true)
                            (assoc-in ["IfcBuilding" 0 "FI_Talotekniikka" "Parikaapelointi"] true))
            result-true (btl/build-fields-data test-schema props-true)]
        (t/is (= {:yleiskaapeloinninOptinenKaapelointi true
                  :antenniverkko                       true
                  :olemassaOlevaValokuituverkko        true
                  :yleiskaapeloinninParikaapelointi    true}
                 (get-in result-true [:rakennuskohteen-osa :sisaverkko])))))))

(t/deftest create-ifc-rows-data-test
  (let [properties (:properties example-bimmer-response)
        result     (btl/create-ifc-rows-data test-schema properties)]
    (t/testing "produces only the rows-driven table, not any scalar fields"
      (t/is (some? (get-in result [:rakennuskohteen-osa :tekniset-tiedot :verkostoliittymat])))
      (t/is (= #{:verkostoliittymat :varuste}
               (set (keys (get-in result [:rakennuskohteen-osa :tekniset-tiedot]))))))
    (t/testing "each included row gets an indexed keyword key :0, :1, …"
      (let [rows (get-in result [:rakennuskohteen-osa :tekniset-tiedot :verkostoliittymat])]
        (t/is (pos? (count rows)))
        (t/is (every? keyword? (keys rows)))
        (t/is (every? #(true? (:liitetty %)) (vals rows)))
        (t/is (every? #(string? (:verkostoliittymatyyppi %)) (vals rows)))))
    (t/testing "does not contain any scalar field paths — those belong to build-fields-data"
      (t/is (nil? (get-in result [:rakennuskohteen-osa :ulkokuorentiedot :kerrosluku]))))
    (t/testing "Group D: lammonlahde rows are populated from FI_Talotekniikka booleans"
      (let [rows (get-in result [:rakennuskohteen-osa :energiatiedot :lammonlahde])]
        (t/is (= {:0 {:lammonlahdetyyppi "maalämpö"}}
                 rows))))
    (t/testing "Group E: lammitystapa rows are populated from FI_Talotekniikka booleans"
      (let [rows (get-in result [:rakennuskohteen-osa :energiatiedot :lammitystapa])]
        (t/is (= {:0 {:lammitystapatyyppi "vesikeskus"}}
                 rows))))
    (t/testing "Group F: julkisivu rows are populated from FI_Kohde booleans"
      (let [rows (get-in result [:rakennuskohteen-osa :materiaalitiedot :julkisivu])]
        (t/is (= {:0 {:julkisivumateriaali "puu"}}
                 rows))))
    (t/testing "Group G: kantavaRakennusaine rows are populated from FI_Kohde booleans"
      (let [rows (get-in result [:rakennuskohteen-osa :materiaalitiedot :kantavaRakennusaine])]
        (t/is (= {:0 {:rakennusaine "puu"}}
                 rows))))
    (t/testing "Group H: varuste rows are populated from FI_Kohde booleans"
      (let [rows    (get-in result [:rakennuskohteen-osa :tekniset-tiedot :varuste])
            tyyppit (set (map :varustetyyppi (vals rows)))]
        (t/is (pos? (count rows)))
        (t/is (every? keyword? (keys rows)))
        (t/is (set/subset? #{"sahko" "vesijohto" "sauna"} tyyppit))))))

(t/deftest build-aggregate-data-test
  (let [properties (:properties example-bimmer-response)]
    (t/testing "sums IfcSpace FI_Laajuustieto values into ulkokuorentiedot and sisatilat"
      (t/is (match? {:rakennuskohteen-osa {:ulkokuorentiedot {:kokonaisala                    "2221"
                                                              :kerrosala                      "977"
                                                              :rakennusoikeudellinenKerrosala "941"}
                                           :energiatiedot    {:lammitetty-nettoala "1053"}
                                           :sisatilat        {:kellariala   "259"
                                                              :ullakkoala   "240"
                                                              :huoneistoala "562"}}}
                    (btl/build-aggregate-data test-schema properties))))
    (t/testing "returns empty map when no IfcSpace entries are present"
      (t/is (= {} (btl/build-aggregate-data test-schema (dissoc properties "IfcSpace" "IfcSpatialZone")))))
    (t/testing "field is omitted when the specific property key is absent for that entity"
      (let [spaces-without-kellari (mapv (fn [s] (update s "FI_Laajuustieto" dissoc "Kellariala"))
                                         (get properties "IfcSpace"))
            props                  (assoc properties "IfcSpace" spaces-without-kellari)]
        (t/is (nil? (get-in (btl/build-aggregate-data test-schema props)
                            [:rakennuskohteen-osa :sisatilat :kellariala])))))))

(defn- expected-verkostoliittymat-rows
  "Derive expected verkostoliittymat rows from the fixture and `ifc-mappings/row-mappings`."
  [properties]
  (->> ifc-mappings/row-mappings
       (filter #(= :ifc-tbl-verkostoliittymat (:ifc-mapping %)))
       (keep (fn [{:keys [entity pset property row]}]
               (let [v (-> properties (get (name entity)) first (get-in [(name pset) (name property)]))]
                 (when v row))))
       (map-indexed (fn [idx row] [(keyword (str idx)) row]))
       (into {})))

(t/deftest operation->kohteen-laji-test
  (t/testing "returns \"rakennus\" for building operations"
    (t/is (= "rakennus" (btl/operation->kohteen-laji "kerrostalo-rivitalo")))
    (t/is (= "rakennus" (btl/operation->kohteen-laji "pientalo"))))
  (t/testing "returns \"rakennelma\" for structure operations"
    (t/is (= "rakennelma" (btl/operation->kohteen-laji "mainoslaite"))))
  (t/testing "returns nil for nil input"
    (t/is (nil? (btl/operation->kohteen-laji nil))))
  (t/testing "returns nil for unknown operation name"
    (t/is (nil? (btl/operation->kohteen-laji "tuntematon-toimenpide")))))

(t/deftest properties-to-uusirakennuskohde-data-map-test
  (let [properties       (:properties example-bimmer-response)
        result           (btl/create-data-map test-schema properties)
        vl-rows          (get-in result [:data :rakennuskohteen-osa :tekniset-tiedot :verkostoliittymat])
        expected-vl-rows (expected-verkostoliittymat-rows properties)]
    (t/testing "kaytto map: no kohteen-laji when no operation-name supplied"
      (t/is (= {:nimi "Housing Timber" :rakentaja-tyyppi true :valiaikainenRakennusKytkin false}
               (get-in result [:data :kaytto]))))
    (t/testing "kaytto map: kohteen-laji set from operation-name"
      (t/is (= {:nimi "Housing Timber" :rakentaja-tyyppi true :valiaikainenRakennusKytkin false :kohteen-laji "rakennus"}
               (get-in (btl/create-data-map test-schema properties "kerrostalo-rivitalo")
                       [:data :kaytto])))
      (t/is (= {:nimi "Housing Timber" :rakentaja-tyyppi true :valiaikainenRakennusKytkin false :kohteen-laji "rakennelma"}
               (get-in (btl/create-data-map test-schema properties "mainoslaite")
                       [:data :kaytto]))))
    (t/testing "verkostoliittymat rows match what the fixture FI_Talotekniikka pset implies"
      (t/is (= expected-vl-rows vl-rows)))
    (t/testing "verkostoliittymat row order matches declaration order in ifc-mappings/row-mappings"
      (let [declared-order (->> ifc-mappings/row-mappings
                                (filter #(= :ifc-tbl-verkostoliittymat (:ifc-mapping %)))
                                (keep (fn [{:keys [entity pset property row]}]
                                        (let [v (-> properties (get (name entity)) first
                                                    (get-in [(name pset) (name property)]))]
                                          (when v (:verkostoliittymatyyppi row)))))
                                vec)
            actual-order   (->> vl-rows (sort-by (comp parse-long name key)) (mapv (comp :verkostoliittymatyyppi val)))]
        (t/is (= declared-order actual-order))))
    (t/testing "full data map matches expected shape (sisaankaynti tested separately)"
      (let [result-no-si (update-in result [:data :rakennuskohteen-osa] dissoc :sisaankaynti)]
        (t/is (= {:data {:kaytto               {:nimi "Housing Timber" :rakentaja-tyyppi true :valiaikainenRakennusKytkin false}
                         :rakennuksenOmistajat {:0 {:omistajalaji "asunto-oy tai asunto-osuuskunta"}}
                         :sijaintitiedot       {:osoite {:katu                 "Myllytie 7"
                                                         :postinumero          "04410"
                                                         :postitoimipaikannimi "Järvenpää"}}
                         :latauspiste          {:latauspisteidenMaara     "0"
                                                :polkupyorapaikkojenMaara "34"
                                                :esikaapelointi           false
                                                :muutJohtoreitit          false
                                                :putkitus                 false}
                         :rakennuskohteen-osa  {:energiatiedot    {:energialuokan-alaindeksi "2018"
                                                                   :energialuokka            "A"
                                                                   :lammitetty-nettoala      "1053"
                                                                   :jaahdytystapa            {:0 {:jaahdytystapatyyppi "ei-kiinteaa-jaahdytyslaitetta"}}
                                                                   :lammonlahde              {:0 {:lammonlahdetyyppi "maalämpö"}}
                                                                   :lammitystapa             {:0 {:lammitystapatyyppi "vesikeskus"}}}
                                                :kayttotiedot     {:kayttoika "100"}
                                                :materiaalitiedot {:paloluokka          "P2"
                                                                   :rakentamistapa      "elementti"
                                                                   :julkisivu           {:0 {:julkisivumateriaali "puu"}}
                                                                   :kantavaRakennusaine {:0 {:rakennusaine "puu"}}}
                                                :ulkokuorentiedot {:kerrosluku                     "4"
                                                                   :korkeus                        "66.55"
                                                                   :suhde-maan-pintaan             "olennaisesti-kokonaan-pinnan-ylla"
                                                                   :tilavuus                       "4022"
                                                                   :kokonaisala                    "2221"
                                                                   :kerrosala                      "977"
                                                                   :rakennusoikeudellinenKerrosala "941"}
                                                :huoneistot       {:0 {:huoneistoTyyppi "asuinhuoneisto"
                                                                       :huoneistoala    "85.43645438474877"
                                                                       :porras          "A"
                                                                       :huoneistonumero "1"
                                                                       :huoneluku       "3"
                                                                       :keittionTyyppi  "keittio"
                                                                       :wc              "1"
                                                                       :suihku          "1"
                                                                       :parveke         "1"
                                                                       :lammin-vesi     "1"
                                                                       :muutostapa      "lisäys"}
                                                                   :1 {:huoneistoTyyppi "asuinhuoneisto"
                                                                       :huoneistoala    "102.01800108265114"
                                                                       :porras          "A"
                                                                       :huoneistonumero "2"
                                                                       :huoneluku       "4"
                                                                       :keittionTyyppi  "keittio"
                                                                       :wc              "1"
                                                                       :suihku          "1"
                                                                       :sauna           "1"
                                                                       :parveke         "1"
                                                                       :lammin-vesi     "1"
                                                                       :muutostapa      "lisäys"}
                                                                   :2 {:huoneistoTyyppi "asuinhuoneisto"
                                                                       :huoneistoala    "85.43644612114299"
                                                                       :porras          "A"
                                                                       :huoneistonumero "3"
                                                                       :huoneluku       "3"
                                                                       :keittionTyyppi  "keittio"
                                                                       :wc              "1"
                                                                       :suihku          "1"
                                                                       :parveke         "1"
                                                                       :lammin-vesi     "1"
                                                                       :muutostapa      "lisäys"}
                                                                   :3 {:huoneistoTyyppi "asuinhuoneisto"
                                                                       :huoneistoala    "102.01815259981507"
                                                                       :porras          "A"
                                                                       :huoneistonumero "4"
                                                                       :huoneluku       "4"
                                                                       :keittionTyyppi  "keittio"
                                                                       :wc              "1"
                                                                       :suihku          "1"
                                                                       :sauna           "1"
                                                                       :parveke         "1"
                                                                       :lammin-vesi     "1"
                                                                       :muutostapa      "lisäys"}
                                                                   :4 {:huoneistoTyyppi "asuinhuoneisto"
                                                                       :huoneistoala    "85.43644612114299"
                                                                       :porras          "A"
                                                                       :huoneistonumero "5"
                                                                       :huoneluku       "3"
                                                                       :keittionTyyppi  "keittio"
                                                                       :wc              "1"
                                                                       :suihku          "1"
                                                                       :parveke         "1"
                                                                       :lammin-vesi     "1"
                                                                       :muutostapa      "lisäys"}
                                                                   :5 {:huoneistoTyyppi "asuinhuoneisto"
                                                                       :huoneistoala    "102.01815259981507"
                                                                       :porras          "A"
                                                                       :huoneistonumero "6"
                                                                       :huoneluku       "4"
                                                                       :keittionTyyppi  "keittio"
                                                                       :wc              "1"
                                                                       :suihku          "1"
                                                                       :sauna           "1"
                                                                       :parveke         "1"
                                                                       :lammin-vesi     "1"
                                                                       :muutostapa      "lisäys"}
                                                                   :6 {:huoneistoTyyppi "toimitila"
                                                                       :porras          "B"
                                                                       :huoneistonumero "7"
                                                                       :huoneluku       "1"
                                                                       :muutostapa      "lisäys"}}
                                                :tekniset-tiedot  {:talousvesityyppi      "verkostoliittymat"
                                                                   :jatevesienkasittely   "verkostoliittyma"
                                                                   :ilmanvaihtotavan-laji {:0 {:ilmanvaihtotyyppi "koneellinen-tulo-ja-poistoilmanvaihto"}}
                                                                   :verkostoliittymat     {:0 {:verkostoliittymatyyppi "tietoliikennekaapeli" :liitetty true}
                                                                                           :1 {:verkostoliittymatyyppi "talousvesi" :liitetty true}
                                                                                           :2 {:verkostoliittymatyyppi "jatevesi" :liitetty true}
                                                                                           :3 {:verkostoliittymatyyppi "sahko" :liitetty true}
                                                                                           :4 {:verkostoliittymatyyppi "hulevesi" :liitetty true}
                                                                                           :5 {:verkostoliittymatyyppi "sammutusvesi" :liitetty true}}
                                                                   :varuste               {:0 {:varustetyyppi "sahko"}
                                                                                           :1 {:varustetyyppi "jatevesiviemari"}
                                                                                           :2 {:varustetyyppi "vesijohto"}
                                                                                           :3 {:varustetyyppi "lammin-vesi"}
                                                                                           :4 {:varustetyyppi "koneellinen-ilmastointi"}
                                                                                           :5 {:varustetyyppi "aurinkopaneeli"}
                                                                                           :6 {:varustetyyppi "hissi"}
                                                                                           :7 {:varustetyyppi "sauna"}}}
                                                :hissit           {:0 {:hissin-tiedot {:palomieshissi "no"
                                                                                       :hissin-laji   "henkilohissi"
                                                                                       :nimi          "Henkilöhissi:HENH-1:2590834"}
                                                                       :kapasiteetti  {:henkilomaara "8"
                                                                                       :kantavuus    "600.0"}
                                                                       :mitat         {:oviaukon-leveys "900.0"
                                                                                       :sisaleveys      "1100.0"
                                                                                       :sisapituus      "1400.0"}}}
                                                :sisaverkko       {:yleiskaapeloinninOptinenKaapelointi true
                                                                   :antenniverkko                       false
                                                                   :olemassaOlevaValokuituverkko        false
                                                                   :yleiskaapeloinninParikaapelointi    false}
                                                :sisatilat        {:kellariala   "259"
                                                                   :ullakkoala   "240"
                                                                   :huoneistoala "562"}}}}
                 result-no-si))))
    (t/testing "sisaankaynti: 82 doors total (one per IfcDoor in fixture)"
      (t/is (= 82 (count (get-in result [:data :rakennuskohteen-osa :sisaankaynti])))))
    (t/testing "sisaankaynti: all doors get :nimi from Name attribute"
      (let [sisaankaynti (get-in result [:data :rakennuskohteen-osa :sisaankaynti])]
        (t/is (= "Teräslasiulko-ovi passiiviovella:TLUO-2.10+4x21:1402305"
                 (get-in sisaankaynti [:0 :nimi])))
        (t/is (= "Puurakenteinen ovi:O-4.Yhteistila:1402389"
                 (get-in sisaankaynti [:1 :nimi])))
        (t/is (every? :nimi (vals sisaankaynti)))))
    (t/testing "sisaankaynti: only doors with cPset_DoorRegulatory/EntranceType get :tila"
      (let [sisaankaynti (get-in result [:data :rakennuskohteen-osa :sisaankaynti])]
        (t/is (= "paasisaankaynti-porrashuoneeseen" (get-in sisaankaynti [:0 :tila :laji])))
        (t/is (= "paasisaankaynti-porrashuoneeseen" (get-in sisaankaynti [:2 :tila :laji])))
        (t/is (= "muu-sisaankaynti" (get-in sisaankaynti [:3 :tila :laji])))
        (t/is (= "sisaankaynti-huoneistoon" (get-in sisaankaynti [:74 :tila :laji])))
        (t/is (nil? (get-in sisaankaynti [:1 :tila])) "door without EntranceType has no :tila")))))

(def rakennuspaikka-schema
  (schemas/get-schema (schemas/get-latest-schema-version) "rakennuspaikka"))

(t/deftest rakennuspaikka-field-mappings-test
  (let [properties (:properties example-bimmer-response)]
    (t/testing "maps kaavatilanne and hallintaperuste from IfcSite FI_Kiinteistö"
      (t/is (= {:kaavatilanne    "asemakaava"
                :hallintaperuste "oma"}
               (btl/create-rakennuspaikka-data-map rakennuspaikka-schema properties))))
    (t/testing "unknown IFC value is omitted, known sibling is still present"
      (let [props-bad (assoc-in properties ["IfcSite" 0 "FI_Kiinteistö" "Kaavatilanne"] "TuntematomaKaava")]
        (t/is (= {:hallintaperuste "oma"}
                 (btl/create-rakennuspaikka-data-map rakennuspaikka-schema props-bad)))))))

(t/deftest validated-select-lookup-fn-test
  (t/testing "space-free options match case-insensitively"
    (let [lookup (#'ifc-mappings/validated-select-lookup-fn ["oma" "vuokra" "muu oikeus" "ei tiedossa"])]
      (t/is (= "oma"         (lookup "Oma"))         "space-free option should match")
      (t/is (= "muu oikeus"  (lookup "Muu oikeus"))  "multi-word option should match")
      (t/is (= "ei tiedossa" (lookup "Ei tiedossa")) "multi-word option should match")))
  (t/testing "kaavatilanne multi-word options match"
    (let [lookup (#'ifc-mappings/validated-select-lookup-fn ["maakuntakaava"
                                                             "oikeusvaikutteinen yleiskaava"
                                                             "asemakaava"])]
      (t/is (= "asemakaava" (lookup "Asemakaava")) "single-word option should match")
      (t/is (= "oikeusvaikutteinen yleiskaava" (lookup "Oikeusvaikutteinen yleiskaava"))
            "multi-word option should match"))))

(t/deftest safe-transform-resilience-test
  (let [properties (:properties example-bimmer-response)]

    (t/testing "scalar field: vector value for a mapping->ifc-lookup transform does not throw and skips the field"
      ;; This is the exact bug: Bimmer returns a vector for a property that expects a string.
      ;; mapping->ifc-lookup calls ifc->lp-key -> scandics->ascii -> clojure.string/escape,
      ;; which requires a CharSequence and threw ClassCastException when given a PersistentVector.
      (let [props-bad (assoc-in properties ["IfcBuilding" 0 "FI_Talotekniikka" "JätevesienKäsittelytapa"]
                                ["verkostoliittyma" "muu"])
            result    (btl/build-fields-data test-schema props-bad)]
        (t/is (nil? (get-in result [:rakennuskohteen-osa :tekniset-tiedot :jatevesienkasittely]))
              "bad vector value should be skipped (field absent), not throw")
        (t/is (= "verkostoliittymat"
                 (get-in result [:rakennuskohteen-osa :tekniset-tiedot :talousvesityyppi]))
              "sibling scalar field should still be mapped normally")))

    (t/testing "scalar field: vector value for validated-select-lookup-fn does not throw and skips the field"
      (let [props-bad (assoc-in properties ["IfcSite" 0 "FI_Kiinteistö" "Kaavatilanne"]
                                ["asemakaava" "ranta-asemakaava"])
            result    (btl/create-rakennuspaikka-data-map rakennuspaikka-schema props-bad)]
        (t/is (nil? (:kaavatilanne result))
              "vector kaavatilanne should be skipped")
        (t/is (= "oma" (:hallintaperuste result))
              "sibling field hallintaperuste should still be mapped")))

    (t/testing "repeating table: one instance with a vector value is skipped, others are mapped normally"
      ;; IfcSpatialZone[0] gets a vector for Käyttötarkoitus — that huoneisto loses huoneistoTyyppi
      ;; but all other huoneistot and fields on the same huoneisto are unaffected.
      (let [props-bad (assoc-in properties ["IfcSpatialZone" 0 "FI_Huoneisto" "Käyttötarkoitus"]
                                ["asuinhuoneisto" "toimitila"])
            result    (btl/build-fields-data test-schema props-bad)]
        (t/is (nil? (get-in result [:rakennuskohteen-osa :huoneistot :0 :huoneistoTyyppi]))
              "the bad instance's huoneistoTyyppi should be absent")
        (t/is (= "A" (get-in result [:rakennuskohteen-osa :huoneistot :0 :porras]))
              "other fields on the same bad instance should still be mapped")
        (t/is (= "asuinhuoneisto"
                 (get-in result [:rakennuskohteen-osa :huoneistot :1 :huoneistoTyyppi]))
              "the next instance with a valid string value should still be mapped")))

    (t/testing "all entity lists empty: build-fields-data returns an empty map instead of throwing"
      (let [props-empty (-> properties
                            (assoc "IfcBuilding" [])
                            (assoc "IfcSite" [])
                            (assoc "IfcSpatialZone" [])
                            (assoc "IfcSpace" [])
                            (assoc "IfcTransportElement" [])
                            (assoc "IfcDoor" []))
            result      (btl/build-fields-data test-schema props-empty)]
        (t/is (= {} result))))

    (t/testing "repeating table: vector value for an omistajalaji lookup does not throw"
      (let [props-bad (assoc-in properties ["IfcBuilding" 0 "FI_Kohde" "Omistajalaji"]
                                ["yksityinen-henkilo" "muu"])
            result    (btl/build-fields-data test-schema props-bad)]
        (t/is (nil? (get-in result [:rakennuksenOmistajat :0 :omistajalaji]))
              "vector omistajalaji should be skipped")
        (t/is (= "Housing Timber" (get-in result [:kaytto :nimi]))
              "unrelated scalar field should be unaffected")))))

(t/deftest assoc-ifc-data-query-test
  (t/testing "adds the correct :query into context"
    (let [result (btl/assoc-ifc-data-query {:some-existing-key "value"})]
      (t/is (= (btl/psets-query) (:query result)))))
  (t/testing "preserves existing keys in the context"
    (let [result (btl/assoc-ifc-data-query {:some-existing-key "value"})]
      (t/is (= "value" (:some-existing-key result))))))

(t/deftest remove-readonly-model-updates-test
  (t/testing "readonly fields are filtered out of model-updates for uusiRakennuskohde"
    ;; postitoimipaikannimi is :readonly true in the ryhti-rakennuskohteen-osoite group
    (let [schema-version (schemas/get-latest-schema-version)
          document       {:schema-info {:name "uusiRakennuskohde" :version schema-version}
                          :data        {}}
          ;; Include both a normal writable field and the readonly one
          all-updates    [[:sijaintitiedot :osoite :katu]
                          [:sijaintitiedot :osoite :postinumero]
                          [:sijaintitiedot :osoite :postitoimipaikannimi]]
          model-updates  (map #(vector % "value") all-updates)
          filtered       (doc-persistence/remove-readonly-model-updates document model-updates)
          paths          (map first filtered)]
      (t/is (not (some #{[:sijaintitiedot :osoite :postitoimipaikannimi]} paths))
            "readonly postitoimipaikannimi should be removed")
      (t/is (some #{[:sijaintitiedot :osoite :katu]} paths)
            "writable katu field should be kept")
      (t/is (some #{[:sijaintitiedot :osoite :postinumero]} paths)
            "writable postinumero field should be kept")))

  (t/testing "ifc-data map for uusiRakennuskohde has postitoimipaikannimi present (field is mapped)"
    ;; The field is intentionally kept in ifc-data — it's only filtered at write time.
    (let [ifc-data (:data (btl/create-data-map test-schema (:properties example-bimmer-response)))]
      (t/is (some? (get-in ifc-data [:sijaintitiedot :osoite :postitoimipaikannimi]))
            "postitoimipaikannimi should be present in ifc-data even though it is readonly in the schema")))

  (t/testing "all readonly field paths in uusiRakennuskohde are filtered"
    (let [schema-version (schemas/get-latest-schema-version)
          document       {:schema-info {:name "uusiRakennuskohde" :version schema-version}
                          :data        {}}
          ifc-data       (:data (btl/create-data-map test-schema (:properties example-bimmer-response)))
          model-updates  (doc-model/map2updates [] ifc-data)
          filtered       (doc-persistence/remove-readonly-model-updates document model-updates)
          filtered-paths (set (map first filtered))
          known-readonly #{[:sijaintitiedot :osoite :postitoimipaikannimi]
                           [:sijaintitiedot :osoite :jakokirjain]
                           [:sijaintitiedot :osoite :jakokirjain2]
                           [:sijaintitiedot :osoite :porras]
                           [:sijaintitiedot :osoite :huoneisto]}]
      (doseq [rpath known-readonly]
        (t/is (not (contains? filtered-paths rpath))
              (str rpath " should have been filtered out"))))))
