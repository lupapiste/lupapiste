(ns lupapalvelu.bim.bimmer.ifc-field-mappings
  "Single source of truth for the IFC ↔ LP document field mappings used by the Bimmer pipeline.

  Each entry in `field-mappings` describes one scalar field:
    :entity    — IFC entity class keyword, e.g. :IfcBuilding
    :pset      — IFC property set keyword, e.g. :FI_Kohde
    :property  — IFC property name keyword, e.g. :RakennuksenKerrosluku
    :transform — a 1-arg fn that converts the raw IFC value to the LP value
    :pset-opts — (optional) extra options forwarded in the Bimmer psets query

  The document field path for each entry is not stored here — it is derived at runtime by walking
  the target document schema and matching the :ifc-mapping annotation on each field element.
  This means a single `field-mappings` map covers all schemas (uusiRakennuskohde,
  rakennuspaikka, …) — the schema itself determines which keys are relevant.

  `row-mappings` describes repeating table rows driven by IFC boolean flags.
  Each entry:
    :ifc-mapping — key identifying the target repeating table in the LP document schema
                   (matched via :ifc-mapping annotation on the table element, resolved at runtime)
    :entity      — IFC entity class keyword
    :pset        — IFC property set keyword
    :property    — IFC property name keyword; the row is included when this value is truthy
    :row         — the LP row map to insert when the property is truthy

  `aggregate-mappings` describes area fields that are summed across all entity instances
  (IfcSpace / IfcSpatialZone) rather than read from the first instance only. See the var
  docstring for the full entry schema.

  ── Fields intentionally excluded from mappings ──────────────────────────────

  IfcBuilding / FI_Kohde scalars intentionally excluded:
    • Perusparannus — IFC exposes a boolean flag, but the LP schema only has
                      `perusparannusPaiva` (a date field) — no direct boolean counterpart.
    • Muutosala     — not present in the `uusiRakennuskohde` schema.

  Muutos-only fields (only relevant in extension/amendment permits, different schema):
    KerrosalanMuutos, RakennusoikeudellisenKerrosalanMuutos, HuoneistoalanMuutos,
    KellarialanMuutos, KerrosluvunMuutos, TilavuudenMuutos

  Coordinate fields:
    • IfcMapConversion (Eastings, Northings, OrthogonalHeight, Scale, XAxisAbscissa,
      XAxisOrdinate) — queried unconditionally by `bimmer-to-lp/psets-query` and
      extracted by `bimmer-to-lp/extract-ifc-location`; lives outside the document
      field mappings because the result is stored as `:location` on the ifc-doc, not
      as a document field.

  Other excluded:
    • Huoneistotunnus / PysyväHuoneistotunnus — only in amendment/extension permits
    • IFC Name attribute (IfcSpace, IfcTransportElement, IfcDoor) — Bimmer does not return
      the built-in Name attribute as a queryable pset property
    • Lämmitystapa Aurinkolämmitys/Ilmalämpöpumppu/Muu — no exact LP match; mapped to \"ei tiedossa\""
  (:require [clojure.string :as s]
            [lupapalvelu.ryhti.api.mappings :as ryhti-mappings]
            [sade.strings :as ss]
            [sade.util :as su]))

(def ^:private ifc-kayttotarkoitus->lp-option
  "Lookup from a Finnish building-category label (lowercase) to the corresponding
  LP rakennusluokka option string (e.g. \"omakotitalot\" → \"0110 omakotitalot\").
  Only level-3 (4-digit) entries are included; broader category strings like
  \"Asuinrakennukset\" (level-1) are intentionally absent and will return nil."
  (->> (su/read-edn-resource ryhti-mappings/rakennusluokitus-data-resource-path)
       :codes
       (filter #(and (= "VALID" (:status %)) (= 3 (:hierarchyLevel %))))
       (reduce (fn [acc {:keys [codeValue prefLabel]}]
                 (let [fi-label (-> (:fi prefLabel) ss/lower-case)]
                   (assoc acc fi-label (str codeValue " " fi-label))))
               {})))

(defn ifc->lp-key
  "Normalises an IFC enum string so it can be compared case-insensitively
  with LP enum option names: strips Scandinavian diacritics, lowercases,
  and replaces whitespace runs with hyphens (matching the LP option key convention)."
  [s]
  (some-> s ss/scandics->ascii ss/lower-case (s/replace #"\s+" "-")))

(defn- mapping->ifc-lookup
  "Builds a lookup fn that maps an IFC enum string to an LP enum option name.
  Comparison is done via `ifc->lp-key` so casing and diacritics are ignored."
  [mapping]
  (let [index (into {}
                    (map (fn [[k _]] [(ifc->lp-key (name k)) (name k)]))
                    mapping)]
    (fn [ifc-val] (get index (ifc->lp-key ifc-val)))))

(def ^:private varuste #(when % "1"))

(defn- validated-select-lookup-fn
  "Returns a transform fn that normalises an IFC string via `ifc->lp-key` and then
  validates it against the set of allowed LP option names, returning the original
  LP option string on match or nil on no match.
  LP options are also normalised via `ifc->lp-key` so that multi-word options with
  spaces (e.g. \"muu oikeus\") match hyphenated IFC keys (e.g. \"muu-oikeus\")."
  [lp-options]
  (let [normalised->original (into {} (map (fn [opt] [(ifc->lp-key opt) opt])) lp-options)]
    (fn [ifc-val]
      (get normalised->original (ifc->lp-key ifc-val)))))

(def field-mappings
  "All scalar IFC → LP field mappings, keyed by a unique IFC mapping id (e.g. :ifc-latauspiste-esikaapelointi).
  Covers all document schemas that use `:ifc-mapping` annotations (uusiRakennuskohde, rakennuspaikka, …).
  The document field path for each entry is derived at runtime by walking the target document schema and looking
  up the :ifc-mapping key — see `bimmer-to-lp/ifc-mapping-paths`."
  {;; IfcSite / FI_Kiinteistö — latauspiste
   :ifc-latauspiste-esikaapelointi   {:entity    :IfcSite
                                      :pset      :FI_Kiinteistö
                                      :property  :LatauspisteidenEsikaapelointi
                                      :transform boolean}
   :ifc-latauspiste-maara            {:entity    :IfcSite
                                      :pset      :FI_Kiinteistö
                                      :property  :LatauspisteidenMäärä
                                      :transform str}
   :ifc-latauspiste-muut-johtoreitit {:entity    :IfcSite
                                      :pset      :FI_Kiinteistö
                                      :property  :LatauspisteidenMuutJohtoreitit
                                      :transform boolean}
   :ifc-latauspiste-putkitus         {:entity    :IfcSite
                                      :pset      :FI_Kiinteistö
                                      :property  :LatauspisteidenPutkitus
                                      :transform boolean}

   ;; IfcBuilding / FI_Kohde — kaytto
   :ifc-kaytto-nimi             {:entity    :IfcBuilding
                                 :pset      :FI_Kohde
                                 :property  :Nimi
                                 :transform str}
   :ifc-kaytto-rakennusluokka   {:entity    :IfcBuilding
                                 :pset      :FI_Kohde
                                 :property  :PääasiallinenKäyttötarkoitus
                                 :transform #(get ifc-kayttotarkoitus->lp-option (some-> % ss/lower-case))}
   :ifc-kaytto-rakentaja-tyyppi {:entity    :IfcBuilding
                                 :pset      :FI_Kohde
                                 :property  :LiiketaloudellinenRakentaminen
                                 :transform boolean}

   ;; IfcBuilding / FI_Kohde — pysyva-rakennustunnus
   :ifc-pysyva-rakennustunnus {:entity    :IfcBuilding
                               :pset      :FI_Kohde
                               :property  :Rakennustunnus
                               :transform str}

   ;; IfcBuilding / FI_Kohde — rakennuksenOmistajat (repeating table)
   :ifc-omistajat-omistajalaji {:entity    :IfcBuilding
                                :pset      :FI_Kohde
                                :property  :Omistajalaji
                                :transform (mapping->ifc-lookup ryhti-mappings/omistajalaji)}

   ;; IfcBuilding / FI_Kohde — sijaintitiedot / osoite
   :ifc-osoite-katu             {:entity    :IfcBuilding
                                 :pset      :FI_Kohde
                                 :property  :Katuosoite
                                 :transform str}
   :ifc-osoite-postinumero      {:entity    :IfcBuilding
                                 :pset      :FI_Kohde
                                 :property  :Postinumero
                                 :transform str}
   :ifc-osoite-postitoimipaikka {:entity    :IfcBuilding
                                 :pset      :FI_Kohde
                                 :property  :Postitoimipaikka
                                 :transform str}

   ;; IfcBuilding / FI_Kohde — latauspiste
   :ifc-latauspiste-polkupyorat {:entity    :IfcBuilding
                                 :pset      :FI_Kohde
                                 :property  :PolkupyöräpaikkojenMäärä
                                 :transform str}

   ;; IfcBuilding / FI_Kohde — ulkokuorentiedot
   :ifc-ulkokuori-tilavuus           {:entity    :IfcBuilding
                                      :pset      :FI_Kohde
                                      :property  :RakennuksenTilavuus
                                      :transform str}
   :ifc-ulkokuori-kerrosluku         {:entity    :IfcBuilding
                                      :pset      :FI_Kohde
                                      :property  :RakennuksenKerrosluku
                                      :transform str}
   :ifc-ulkokuori-korkeus            {:entity    :IfcBuilding
                                      :pset      :FI_Kohde
                                      :property  :RakennuksenKorkeus
                                      :transform str}
   :ifc-ulkokuori-suhde-maan-pintaan {:entity    :IfcBuilding
                                      :pset      :FI_Kohde
                                      :property  :RakennuksenSuhdeMaanpintaan
                                      :transform (partial get {"Kokonaan pinnan yllä"              "kokonaan-pinnan-ylla"
                                                               "Olennaisesti kokonaan pinnan yllä" "olennaisesti-kokonaan-pinnan-ylla"
                                                               "Pinnan yllä ja alla"               "pinnan-ylla-ja-alla"
                                                               "Olennaisesti kokonaan pinnan alla" "olennaisesti-kokonaan-pinnan-alla"
                                                               "Kokonaan pinnan alla"              "kokonaan-pinnan-alla"})}

   :ifc-kaytto-valiaikainen {:entity    :IfcBuilding
                             :pset      :FI_Kohde
                             :property  :Väliaikainen
                             :transform boolean}

   ;; IfcBuilding / FI_Kohde — energiatiedot
   :ifc-energia-luokka            {:entity    :IfcBuilding
                                   :pset      :FI_Kohde
                                   :property  :Energiatehokkuusluokka
                                   :transform str}
   :ifc-energia-luokka-alaindeksi {:entity    :IfcBuilding
                                   :pset      :FI_Kohde
                                   :property  :EnergiatehokkuusluokanAlaindeksi
                                   :transform str}

   ;; IfcBuilding / FI_Kohde — kayttotiedot
   :ifc-kayttotiedot-kayttoika     {:entity    :IfcBuilding
                                    :pset      :FI_Kohde
                                    :property  :TavoitteellinenKäyttöikä
                                    :transform str}
   :ifc-kayttotiedot-kayttajamaara {:entity    :IfcBuilding
                                    :pset      :FI_Kohde
                                    :property  :SuunniteltuKäyttäjämäärä
                                    :transform str}

   ;; IfcBuilding / FI_Kohde — materiaalitiedot
   :ifc-materiaalit-rakentamistapa {:entity    :IfcBuilding
                                    :pset      :FI_Kohde
                                    :property  :Rakentamistapa
                                    :transform (partial get {"Elementti"      "elementti"
                                                             "Paikalla tehty" "paikalla-tehty"})}
   :ifc-materiaalit-paloluokka     {:entity    :IfcBuilding
                                    :pset      :FI_Kohde
                                    :property  :Paloluokka
                                    :transform str}

   ;; IfcBuilding / FI_Talotekniikka — sisaverkko
   :ifc-sisaverkko-valokuitu       {:entity    :IfcBuilding
                                    :pset      :FI_Talotekniikka
                                    :property  :OlemassaOlevaValokuituverkko
                                    :transform boolean}
   :ifc-sisaverkko-parikaapelointi {:entity    :IfcBuilding
                                    :pset      :FI_Talotekniikka
                                    :property  :Parikaapelointi
                                    :transform boolean}
   :ifc-sisaverkko-optinen         {:entity    :IfcBuilding
                                    :pset      :FI_Talotekniikka
                                    :property  :OptinenKaapelointi
                                    :transform boolean}
   :ifc-sisaverkko-antenni         {:entity    :IfcBuilding
                                    :pset      :FI_Talotekniikka
                                    :property  :Antenniverkko
                                    :transform boolean}

   ;; IfcBuilding / FI_Talotekniikka — tekniset-tiedot
   :ifc-tekniset-talousvesi {:entity    :IfcBuilding
                             :pset      :FI_Talotekniikka
                             :property  :TalousvedenLaji
                             :transform (partial get {"Verkostoliittymä"  "verkostoliittymat"
                                                      "Oma kaivo"         "oma-kaivo"
                                                      "Rengaskaivo"       "rengaskaivo"
                                                      "Porakaivo"         "porakaivo"
                                                      "Siivilaputkikaivo" "siivilaputkikaivo"
                                                      "Pintavesikaivo"    "pintavesikaivo"
                                                      "Kantovesi"         "kantovesi"
                                                      "Ei talousvettä"    "ei-talousvetta"})}
   :ifc-tekniset-jatevesi   {:entity    :IfcBuilding
                             :pset      :FI_Talotekniikka
                             :property  :JätevesienKäsittelytapa
                             :transform (mapping->ifc-lookup ryhti-mappings/jatevesienkasittely)}

   ;; IfcBuilding / FI_Talotekniikka — jaahdytystapa (repeating, singleton IfcBuilding → row :0)
   :ifc-jaahdytystapa-tyyppi {:entity    :IfcBuilding
                              :pset      :FI_Talotekniikka
                              :property  :Jäähdytystapa
                              :transform (partial get {"Ilmajäähdytteinen"             "ilmajaahdytteinen"
                                                       "Vesijäähdytteinen"             "vesijaahdytteinen"
                                                       "Ei kiinteää jäähdytyslaitetta" "ei-kiinteaa-jaahdytyslaitetta"})}

   ;; IfcBuilding / FI_Talotekniikka — ilmanvaihtotavan-laji (repeating, singleton IfcBuilding → row :0)
   :ifc-ilmanvaihto-tyyppi {:entity    :IfcBuilding
                            :pset      :FI_Talotekniikka
                            :property  :Ilmanvaihtotapa
                            :transform (partial get {"Painovoimainen ilmanvaihto"             "painovoimainen-ilmanvaihto"
                                                     "Koneellinen ilmanvaihto"                "koneellinen-ilmanvaihto"
                                                     "Koneellinen tulo- ja poistoilmanvaihto" "koneellinen-tulo-ja-poistoilmanvaihto"})}

   ;; IfcSpatialZone / FI_Huoneisto — huoneistot (repeating)
   :ifc-huoneisto-tyyppi                  {:entity    :IfcSpatialZone
                                           :pset      :FI_Huoneisto
                                           :property  :Käyttötarkoitus
                                           :transform (mapping->ifc-lookup ryhti-mappings/huoneistotyyppi)}
   :ifc-huoneisto-porras                  {:entity    :IfcSpatialZone
                                           :pset      :FI_Huoneisto
                                           :property  :Porraskirjain
                                           :transform str}
   :ifc-huoneisto-numero                  {:entity    :IfcSpatialZone
                                           :pset      :FI_Huoneisto
                                           :property  :Numero
                                           :transform str}
   :ifc-huoneisto-jakokirjain             {:entity    :IfcSpatialZone
                                           :pset      :FI_Huoneisto
                                           :property  :Jakokirjain
                                           :transform str}
   :ifc-huoneisto-huoneluku               {:entity    :IfcSpatialZone
                                           :pset      :FI_Huoneisto
                                           :property  :Huoneluku
                                           :transform str}
   :ifc-huoneisto-keittio                 {:entity    :IfcSpatialZone
                                           :pset      :FI_Huoneisto
                                           :property  :Keittiötyyppi
                                           :transform (mapping->ifc-lookup ryhti-mappings/keittiotyyppi)}
   :ifc-huoneisto-wc                      {:entity    :IfcSpatialZone
                                           :pset      :FI_Huoneisto
                                           :property  :VarusteWC
                                           :transform varuste}
   :ifc-huoneisto-suihku                  {:entity    :IfcSpatialZone
                                           :pset      :FI_Huoneisto
                                           :property  :VarusteSuihku
                                           :transform varuste}
   :ifc-huoneisto-sauna                   {:entity    :IfcSpatialZone
                                           :pset      :FI_Huoneisto
                                           :property  :VarusteSauna
                                           :transform varuste}
   :ifc-huoneisto-parveke                 {:entity    :IfcSpatialZone
                                           :pset      :FI_Huoneisto
                                           :property  :VarusteParveke
                                           :transform varuste}
   :ifc-huoneisto-lammin-vesi             {:entity    :IfcSpatialZone
                                           :pset      :FI_Huoneisto
                                           :property  :VarusteLämminVesi
                                           :transform varuste}
   :ifc-huoneisto-sijaintikerros          {:entity    :IfcSpatialZone
                                           :pset      :FI_Huoneisto
                                           :property  :Sijaintikerros
                                           :transform str}
   :ifc-huoneisto-sijaintikerros-tarkenne {:entity    :IfcSpatialZone
                                           :pset      :FI_Huoneisto
                                           :property  :SijaintikerroksenTarkenne
                                           :transform #(let [k (ifc->lp-key %)] (when (#{"kellari" "ullakko"} k) k))}
   :ifc-huoneisto-muutostapa              {:entity    :IfcSpatialZone
                                           :pset      :FI_Huoneisto
                                           :property  :MuutoksenLaji
                                           :transform (mapping->ifc-lookup ryhti-mappings/huoneiston-muutoksen-laji)}

   ;; IfcSpace / FI_VSS — vaestonsuoja (repeating)
   :ifc-vss-nimi         {:entity    :IfcSpace
                          :pset      :FI_VSS
                          :property  :Nimi
                          :transform str}
   :ifc-vss-ala          {:entity    :IfcSpace
                          :pset      :FI_VSS
                          :property  :VäestönsuojanAla
                          :transform str}
   :ifc-vss-kapasiteetti {:entity    :IfcSpace
                          :pset      :FI_VSS
                          :property  :VäestönsuojanPaikkamäärä
                          :transform str}
   :ifc-vss-luokka       {:entity    :IfcSpace
                          :pset      :FI_VSS
                          :property  :VäestönsuojanLuokka
                          :transform (mapping->ifc-lookup ryhti-mappings/vaestonsuoja-luokka)}
   :ifc-vss-yhteinen     {:entity    :IfcSpace
                          :pset      :FI_VSS
                          :property  :VäestönsuojaOnYhteinen
                          :transform boolean}

   ;; IfcTransportElement — hissit (repeating)
   :ifc-hissi-nimi            {:entity    :IfcTransportElement
                               :attribute :Name
                               :transform str}
   :ifc-hissi-palomieshissi   {:entity    :IfcTransportElement
                               :pset      :Pset_TransportElementElevator
                               :property  :FireFightingLift
                               :transform #(if % "yes" "no")}
   :ifc-hissi-laji            {:entity    :IfcTransportElement
                               :pset      :ePset_TransportElementElevator
                               :property  :ElevatorType
                               :transform (mapping->ifc-lookup ryhti-mappings/hissin-laji)}
   :ifc-hissi-henkilomaara    {:entity    :IfcTransportElement
                               :pset      :Pset_TransportElementCommon
                               :property  :CapacityPeople
                               :transform str}
   :ifc-hissi-kantavuus       {:entity    :IfcTransportElement
                               :pset      :Pset_TransportElementCommon
                               :property  :CapacityWeight
                               :transform str}
   :ifc-hissi-oviaukon-leveys {:entity    :IfcTransportElement
                               :pset      :ePset_TransportElementElevator
                               :property  :DoorOpeningWidth
                               :transform str}
   :ifc-hissi-sisaleveys      {:entity    :IfcTransportElement
                               :pset      :Pset_TransportElementElevator
                               :property  :ClearWidth
                               :transform str}
   :ifc-hissi-sisapituus      {:entity    :IfcTransportElement
                               :pset      :Pset_TransportElementElevator
                               :property  :ClearDepth
                               :transform str}

   ;; IfcDoor — sisaankaynti (repeating)
   :ifc-sisaankaynti-nimi           {:entity    :IfcDoor
                                     :attribute :Name
                                     :transform str}
   :ifc-sisaankaynti-esteeton       {:entity    :IfcDoor
                                     :pset      :Pset_DoorCommon
                                     :property  :HandicapAccessible
                                     :transform boolean}
   :ifc-sisaankaynti-hyokkausreitti {:entity    :IfcDoor
                                     :pset      :cPset_RegulatoryCondition
                                     :property  :FirefightingAccess
                                     :transform boolean}
   :ifc-sisaankaynti-tila-laji      {:entity    :IfcDoor
                                     :pset      :cPset_DoorRegulatory
                                     :property  :EntranceType
                                     :transform (mapping->ifc-lookup ryhti-mappings/sisaankaynnin-tyyppi)}

   ;; IfcSpatialZone / FI_Laajuustieto — huoneistot (repeating)
   :ifc-huoneisto-ala {:entity    :IfcSpatialZone
                       :pset      :FI_Laajuustieto
                       :property  :Huoneistoala
                       :transform str
                       :pset-opts {:replace {"Tyyppi" {"Huoneistoala" :area
                                                       "Kokonaisala"  :area}}}}

   ;; IfcSite / FI_Kiinteistö — rakennuspaikka
   :ifc-rakennuspaikka-kaavatilanne    {:entity    :IfcSite
                                        :pset      :FI_Kiinteistö
                                        :property  :Kaavatilanne
                                        :transform (validated-select-lookup-fn ["maakuntakaava"
                                                                                "oikeusvaikutteinen yleiskaava"
                                                                                "oikeusvaikutukseton yleiskaava"
                                                                                "asemakaava"
                                                                                "ranta-asemakaava"
                                                                                "ei kaavaa"])}
   :ifc-rakennuspaikka-hallintaperuste {:entity    :IfcSite
                                        :pset      :FI_Kiinteistö
                                        :property  :Hallintaperuste
                                        :transform (validated-select-lookup-fn ["oma" "vuokra" "muu oikeus" "ei tiedossa"])}})
(def row-mappings
  "IFC boolean-flag-driven repeating table rows.
  Each entry carries an `:ifc-mapping` key that identifies the target repeating table in
  the LP document schema (via `:ifc-mapping` annotation on the table element, resolved at
  runtime by `bimmer-to-lp/ifc-table-paths`). A row is included when the corresponding
  IFC property value is truthy."
  [;; verkostoliittymat (IfcBuilding / FI_Talotekniikka)
   {:ifc-mapping :ifc-tbl-verkostoliittymat
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :VerkostoliittymäTietoliikennekaapeli
    :row        {:verkostoliittymatyyppi "tietoliikennekaapeli" :liitetty true}}
   {:ifc-mapping :ifc-tbl-verkostoliittymat
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :VerkostoliittymäTalousvesi
    :row        {:verkostoliittymatyyppi "talousvesi" :liitetty true}}
   {:ifc-mapping :ifc-tbl-verkostoliittymat
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :VerkostoliittymäJätevesi
    :row        {:verkostoliittymatyyppi "jatevesi" :liitetty true}}
   {:ifc-mapping :ifc-tbl-verkostoliittymat
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :VerkostoliittymäSähkö
    :row        {:verkostoliittymatyyppi "sahko" :liitetty true}}
   {:ifc-mapping :ifc-tbl-verkostoliittymat
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :VerkostoliittymäKaukolämpö
    :row        {:verkostoliittymatyyppi "kaukolampo" :liitetty true}}
   {:ifc-mapping :ifc-tbl-verkostoliittymat
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :VerkostoliittymäKaasu
    :row        {:verkostoliittymatyyppi "kaasu" :liitetty true}}
   {:ifc-mapping :ifc-tbl-verkostoliittymat
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :VerkostoliittymäHulevesi
    :row        {:verkostoliittymatyyppi "hulevesi" :liitetty true}}
   {:ifc-mapping :ifc-tbl-verkostoliittymat
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :VerkostoliittymäKaukojäähdytys
    :row        {:verkostoliittymatyyppi "kaukojaahdytys" :liitetty true}}
   {:ifc-mapping :ifc-tbl-verkostoliittymat
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :VerkostoliittymäSammutusvesi
    :row        {:verkostoliittymatyyppi "sammutusvesi" :liitetty true}}

   ;; lammonlahde (IfcBuilding / FI_Talotekniikka)
   ;; Note: IFC has finer-grained options than LP. Several IFC properties map to the same LP
   ;; lammonlahdetyyppi value (e.g. both LämmitysenergianlähdeMaalämpö and
   ;; LämmitysenergianlähdeMaalämpöpumppu → "maalämpö"; Aurinkoenergia, Ilmalämpöpumppu,
   ;; Poistoilmalämpöpumppu, Ilma-vesilämpöpumppu and Muu all → "muu"). If multiple such
   ;; IFC properties are true simultaneously, multiple rows with the same type value will be
   ;; produced. This is intentional: each active IFC heat source becomes its own LP row.
   {:ifc-mapping :ifc-tbl-lammonlahde
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :LämmitysenergianlähdeKaukolämpö
    :row        {:lammonlahdetyyppi "kauko tai aluelämpö"}}
   {:ifc-mapping :ifc-tbl-lammonlahde
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :LämmitysenergianlähdeKevytPolttoöljy
    :row        {:lammonlahdetyyppi "kevyt polttoöljy"}}
   {:ifc-mapping :ifc-tbl-lammonlahde
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :LämmitysenergianlähdeRaskasPolttoöljy
    :row        {:lammonlahdetyyppi "raskas polttoöljy"}}
   {:ifc-mapping :ifc-tbl-lammonlahde
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :LämmitysenergianlähdeSähkö
    :row        {:lammonlahdetyyppi "sähkö"}}
   {:ifc-mapping :ifc-tbl-lammonlahde
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :LämmitysenergianlähdeKaasu
    :row        {:lammonlahdetyyppi "kaasu"}}
   {:ifc-mapping :ifc-tbl-lammonlahde
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :LämmitysenergianlähdeKivihiili
    :row        {:lammonlahdetyyppi "kiviihiili koksi tms"}}
   {:ifc-mapping :ifc-tbl-lammonlahde
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :LämmitysenergianlähdePuu
    :row        {:lammonlahdetyyppi "puu"}}
   {:ifc-mapping :ifc-tbl-lammonlahde
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :LämmitysenergianlähdeTurve
    :row        {:lammonlahdetyyppi "turve"}}
   {:ifc-mapping :ifc-tbl-lammonlahde
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :LämmitysenergianlähdeMaalämpö
    :row        {:lammonlahdetyyppi "maalämpö"}}
   {:ifc-mapping :ifc-tbl-lammonlahde
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :LämmitysenergianlähdeAurinkoenergia
    :row        {:lammonlahdetyyppi "muu"}}
   {:ifc-mapping :ifc-tbl-lammonlahde
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :LämmitysenergianlähdeMaalämpöpumppu
    :row        {:lammonlahdetyyppi "maalämpö"}}
   {:ifc-mapping :ifc-tbl-lammonlahde
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :LämmitysenergianlähdeIlmalämpöpumppu
    :row        {:lammonlahdetyyppi "muu"}}
   {:ifc-mapping :ifc-tbl-lammonlahde
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :LämmitysenergianlähdePoistoilmalämpöpumppu
    :row        {:lammonlahdetyyppi "muu"}}
   {:ifc-mapping :ifc-tbl-lammonlahde
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :LämmitysenergianlähdeIlma-vesilämpöpumppu
    :row        {:lammonlahdetyyppi "muu"}}
   {:ifc-mapping :ifc-tbl-lammonlahde
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :LämmitysenergianlähdeMuu
    :row        {:lammonlahdetyyppi "muu"}}

   ;; lammitystapa (IfcBuilding / FI_Talotekniikka)
   ;; LP options: vesikeskus, ilmakeskus, suora sähkö, uuni, ei lämmitystä, ei tiedossa
   {:ifc-mapping :ifc-tbl-lammitystapa
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :LämmitystapaVesikeskuslämmitys
    :row        {:lammitystapatyyppi "vesikeskus"}}
   {:ifc-mapping :ifc-tbl-lammitystapa
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :LämmitystapaIlmakeskuslämmitys
    :row        {:lammitystapatyyppi "ilmakeskus"}}
   {:ifc-mapping :ifc-tbl-lammitystapa
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :LämmitystapaSähkölämmitys
    :row        {:lammitystapatyyppi "suora sähkö"}}
   {:ifc-mapping :ifc-tbl-lammitystapa
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :LämmitystapaUuni-takka-kamiinalämmitys
    :row        {:lammitystapatyyppi "uuni"}}
   {:ifc-mapping :ifc-tbl-lammitystapa
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :LämmitystapaEiKiinteääLämmityslaitetta
    :row        {:lammitystapatyyppi "ei lämmitystä"}}
   ;; LP schema has no dedicated option for Aurinkolämmitys, Ilmalämpöpumppu or Muu;
   ;; they are mapped to "ei tiedossa" as the closest available fallback.
   {:ifc-mapping :ifc-tbl-lammitystapa
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :LämmitystapaAurinkolämmitys
    :row        {:lammitystapatyyppi "ei tiedossa"}}
   {:ifc-mapping :ifc-tbl-lammitystapa
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :LämmitystapaIlmalämpöpumppu
    :row        {:lammitystapatyyppi "ei tiedossa"}}
   {:ifc-mapping :ifc-tbl-lammitystapa
    :entity     :IfcBuilding
    :pset       :FI_Talotekniikka
    :property   :LämmitystapaMuu
    :row        {:lammitystapatyyppi "ei tiedossa"}}

   ;; julkisivu (IfcBuilding / FI_Kohde)
   ;; LP options: ei-tiedossa, betoni, tiili, metallilevy, kivi, puu, lasi, other
   {:ifc-mapping :ifc-tbl-julkisivu
    :entity     :IfcBuilding
    :pset       :FI_Kohde
    :property   :JulkisivumateriaaliBetoni
    :row        {:julkisivumateriaali "betoni"}}
   {:ifc-mapping :ifc-tbl-julkisivu
    :entity     :IfcBuilding
    :pset       :FI_Kohde
    :property   :JulkisivumateriaaliTiili
    :row        {:julkisivumateriaali "tiili"}}
   {:ifc-mapping :ifc-tbl-julkisivu
    :entity     :IfcBuilding
    :pset       :FI_Kohde
    :property   :JulkisivumateriaaliMetallilevy
    :row        {:julkisivumateriaali "metallilevy"}}
   {:ifc-mapping :ifc-tbl-julkisivu
    :entity     :IfcBuilding
    :pset       :FI_Kohde
    :property   :JulkisivumateriaaliKivi
    :row        {:julkisivumateriaali "kivi"}}
   {:ifc-mapping :ifc-tbl-julkisivu
    :entity     :IfcBuilding
    :pset       :FI_Kohde
    :property   :JulkisivumateriaaliPuu
    :row        {:julkisivumateriaali "puu"}}
   {:ifc-mapping :ifc-tbl-julkisivu
    :entity     :IfcBuilding
    :pset       :FI_Kohde
    :property   :JulkisivumateriaaliLasi
    :row        {:julkisivumateriaali "lasi"}}
   {:ifc-mapping :ifc-tbl-julkisivu
    :entity     :IfcBuilding
    :pset       :FI_Kohde
    :property   :JulkisivumateriaaliMuu
    :row        {:julkisivumateriaali "other"}}

   ;; kantavaRakennusaine (IfcBuilding / FI_Kohde)
   ;; LP options: ei-tiedossa, betoni, tiili, teräs, puu, other
   {:ifc-mapping :ifc-tbl-kantavarakennusaine
    :entity     :IfcBuilding
    :pset       :FI_Kohde
    :property   :RakenteidenRakennusaineBetoni
    :row        {:rakennusaine "betoni"}}
   {:ifc-mapping :ifc-tbl-kantavarakennusaine
    :entity     :IfcBuilding
    :pset       :FI_Kohde
    :property   :RakenteidenRakennusaineTiili
    :row        {:rakennusaine "tiili"}}
   {:ifc-mapping :ifc-tbl-kantavarakennusaine
    :entity     :IfcBuilding
    :pset       :FI_Kohde
    :property   :RakenteidenRakennusaineTeräs
    :row        {:rakennusaine "teräs"}}
   {:ifc-mapping :ifc-tbl-kantavarakennusaine
    :entity     :IfcBuilding
    :pset       :FI_Kohde
    :property   :RakenteidenRakennusainePuu
    :row        {:rakennusaine "puu"}}
   {:ifc-mapping :ifc-tbl-kantavarakennusaine
    :entity     :IfcBuilding
    :pset       :FI_Kohde
    :property   :RakenteidenRakennusaineMuu
    :row        {:rakennusaine "other"}}

   ;; varuste (IfcBuilding / FI_Kohde)
   ;; LP options: aurinkopaneeli, kaasu, sauna, vesijohto, lammin-vesi, hissi, uima-allas,
   ;;             jatevesiviemari, koneellinen-ilmastointi, sahko
   {:ifc-mapping :ifc-tbl-varuste
    :entity     :IfcBuilding
    :pset       :FI_Kohde
    :property   :RakennuksenVarusteSähkö
    :row        {:varustetyyppi "sahko"}}
   {:ifc-mapping :ifc-tbl-varuste
    :entity     :IfcBuilding
    :pset       :FI_Kohde
    :property   :RakennuksenVarusteKaasu
    :row        {:varustetyyppi "kaasu"}}
   {:ifc-mapping :ifc-tbl-varuste
    :entity     :IfcBuilding
    :pset       :FI_Kohde
    :property   :RakennuksenVarusteJätevesiviemäri
    :row        {:varustetyyppi "jatevesiviemari"}}
   {:ifc-mapping :ifc-tbl-varuste
    :entity     :IfcBuilding
    :pset       :FI_Kohde
    :property   :RakennuksenVarusteVesijohto
    :row        {:varustetyyppi "vesijohto"}}
   {:ifc-mapping :ifc-tbl-varuste
    :entity     :IfcBuilding
    :pset       :FI_Kohde
    :property   :RakennuksenVarusteLämminVesi
    :row        {:varustetyyppi "lammin-vesi"}}
   {:ifc-mapping :ifc-tbl-varuste
    :entity     :IfcBuilding
    :pset       :FI_Kohde
    :property   :RakennuksenVarusteKoneellinenIlmastointi
    :row        {:varustetyyppi "koneellinen-ilmastointi"}}
   {:ifc-mapping :ifc-tbl-varuste
    :entity     :IfcBuilding
    :pset       :FI_Kohde
    :property   :RakennuksenVarusteAurinkopaneeli
    :row        {:varustetyyppi "aurinkopaneeli"}}
   {:ifc-mapping :ifc-tbl-varuste
    :entity     :IfcBuilding
    :pset       :FI_Kohde
    :property   :RakennuksenVarusteHissi
    :row        {:varustetyyppi "hissi"}}
   {:ifc-mapping :ifc-tbl-varuste
    :entity     :IfcBuilding
    :pset       :FI_Kohde
    :property   :RakennuksenVarusteUima-allas
    :row        {:varustetyyppi "uima-allas"}}
   {:ifc-mapping :ifc-tbl-varuste
    :entity     :IfcBuilding
    :pset       :FI_Kohde
    :property   :RakennuksenVarusteSauna
    :row        {:varustetyyppi "sauna"}}])

(def ^:private ifc-space-laajuustieto-replace
  "The `:replace` pset-opts shared by all IfcSpace FI_Laajuustieto aggregate-mappings entries.
  Tells Bimmer to filter instances by their `Tyyppi` property value and return the numeric
  area measurement keyed by the matched type name — identical in structure to the
  IfcSpatialZone FI_Laajuustieto replace opts."
  {:replace {"Tyyppi" {"Kokonaisala"                     :area
                       "Kerrosala"                       :area
                       "Rakennusoikeudellinen kerrosala" :area
                       "Kellariala"                      :area
                       "Ullakkoala"                      :area
                       "Lämmitetty nettoala"             :area}}})

(def aggregate-mappings
  "IFC → LP area aggregate mappings.
  Each entry sums all numeric values of `:property-key` across every IfcSpace (or IfcSpatialZone)
  instance and writes the rounded result to the field identified by `:ifc-mapping`.
  The document field path is resolved at runtime via `bimmer-to-lp/ifc-mapping-paths`, the same
  schema-walk function used by `field-mappings`.
  Unlike `field-mappings`, which reads from the first entity instance only, these
  mappings aggregate across all instances of the given entity."
  [;; IfcSpace / FI_Laajuustieto — ulkokuorentiedot areas
   ;; All IfcSpace entries share the same :pset-opts so that merge-psets-queries
   ;; deduplicates them into a single {:name "FI_Laajuustieto" :replace {...}} entry.
   {:ifc-mapping  :ifc-agg-kokonaisala
    :entity       :IfcSpace
    :pset         :FI_Laajuustieto
    :property-key "Kokonaisala"
    :pset-opts    ifc-space-laajuustieto-replace}
   {:ifc-mapping  :ifc-agg-kerrosala
    :entity       :IfcSpace
    :pset         :FI_Laajuustieto
    :property-key "Kerrosala"
    :pset-opts    ifc-space-laajuustieto-replace}
   {:ifc-mapping  :ifc-agg-rakennusoikeudellinenkerrosala
    :entity       :IfcSpace
    :pset         :FI_Laajuustieto
    :property-key "Rakennusoikeudellinen kerrosala"
    :pset-opts    ifc-space-laajuustieto-replace}
   ;; IfcSpace / FI_Laajuustieto — sisatilat areas
   {:ifc-mapping  :ifc-agg-kellariala
    :entity       :IfcSpace
    :pset         :FI_Laajuustieto
    :property-key "Kellariala"
    :pset-opts    ifc-space-laajuustieto-replace}
   {:ifc-mapping  :ifc-agg-ullakkoala
    :entity       :IfcSpace
    :pset         :FI_Laajuustieto
    :property-key "Ullakkoala"
    :pset-opts    ifc-space-laajuustieto-replace}
   {:ifc-mapping  :ifc-agg-lammitetty-nettoala
    :entity       :IfcSpace
    :pset         :FI_Laajuustieto
    :property-key "Lämmitetty nettoala"
    :pset-opts    ifc-space-laajuustieto-replace}
   ;; IfcSpatialZone / FI_Laajuustieto — total huoneistoala (sum of per-apartment areas)
   {:ifc-mapping  :ifc-agg-huoneistoala
    :entity       :IfcSpatialZone
    :pset         :FI_Laajuustieto
    :property-key "Huoneistoala"}])

