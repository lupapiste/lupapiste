(ns lupapalvelu.opendata.schemas
  (:require [schema.core :as sc]
            [ring.swagger.json-schema :as rjs]
            [sade.util :as util]))

(defn- field [schema desc]
  (rjs/field schema {:description desc}))

(sc/defschema Asiointitunnus
  (field sc/Str "Hakemuksen asiointitunnus esim. LP-2016-000-90001"))

(sc/defschema Kiinteistotunnus
  (field sc/Str "Kiinteistötunnus"))

(sc/defschema SijaintiETRS
  (field [(sc/one sc/Num "lon")
          (sc/one sc/Num "lat")] "Rakennuspaikan sijainti (ETRS-TM35FIN), pituus- ja leveysasteet"))

(sc/defschema Kuntakoodi
  (field sc/Str "Kuntakoodi"))

(sc/defschema DateString
  (field (sc/constrained sc/Str (partial re-matches #"\d{4}-\d{2}-\d{2}"))
         "Päivämäärä muodossa yyyy-MM-dd, esim. 2016-05-23."))

(sc/defschema Osoite
  (field sc/Str "Rakennuspaikan katuosoite"))

(sc/defschema Huoneisto
  (field (util/map-keys sc/optional-key
                        {:muutostapa sc/Str
                         :huoneluku sc/Str
                         :keittionTyyppi sc/Str
                         :huoneistoala sc/Str
                         :varusteet sc/Any
                         :huoneistonTyyppi sc/Str
                         :huoneistotunnus sc/Any}) "Huoneiston tiedot"))

(sc/defschema Kayttotarkoitus
  (field (util/map-keys sc/optional-key {:pintaAla sc/Str
                                         :kayttotarkoitusKoodi sc/Str}) "Esittää pinta-alat käyttötarkoituksittain"))

(sc/defschema LaajennuksenTiedot
  (field (util/map-keys sc/optional-key
                        {:tilavuus sc/Str
                         :kerrosala sc/Str
                         :rakennusoikeudellinenKerrosala sc/Str
                         :kokonaisala sc/Str
                         :huoneistoala (sc/maybe [Kayttotarkoitus])}) "Laajennuksen tiedot"))

(sc/defschema Toimenpide
              {(sc/optional-key :uusi) {:kuvaus sc/Str}
               (sc/optional-key :muuMuutosTyo) {:kuvaus sc/Str
                                                :perusparannusKytkin sc/Bool
                                                (sc/optional-key :muutostyonLaji) sc/Str}
               (sc/optional-key :laajennus) {:kuvaus sc/Str
                                             :laajennuksentiedot LaajennuksenTiedot
                                             (sc/optional-key :perusparannusKytkin) sc/Bool}
               (sc/optional-key :purkaminen) {:kuvaus sc/Str
                                              (sc/optional-key :purkamisenSyy) sc/Str
                                              (sc/optional-key :poistumaPvm) DateString}
               (sc/optional-key :kaupunkikuvaToimenpide) {:kuvaus sc/Str}
               (sc/optional-key :rakennuksenTiedot) (util/map-keys sc/optional-key
                                                      {:rakennustunnus sc/Any
                                                       :kayttotarkoitus sc/Str
                                                       :tilavuus sc/Str
                                                       :kokonaisala sc/Str
                                                       :kellarinpinta-ala sc/Str
                                                       :kerrosluku sc/Any
                                                       :kerrosala sc/Any
                                                       :rakennusoikeudellinenKerrosala sc/Any
                                                       :rakentamistapa sc/Any
                                                       :kantavaRakennusaine sc/Any
                                                       :julkisivu sc/Any
                                                       :verkostoliittymat sc/Any
                                                       :energialuokka sc/Any
                                                       :energiatehokkuusluku sc/Any
                                                       :energiatehokkuusluvunYksikko sc/Any
                                                       :paloluokka sc/Any
                                                       :lammitystapa sc/Any
                                                       :lammonlahde sc/Any
                                                       :varusteet sc/Any
                                                       :jaahdytysmuoto sc/Any
                                                       :asuinhuoneistot {:huoneisto [Huoneisto]}
                                                       :liitettyJatevesijarjestelmaanKytkin sc/Bool})
               (sc/optional-key :rakennelmanTiedot) (util/map-keys sc/optional-key
                                                      {:yksilointitieto sc/Str
                                                       :alkuHetki sc/Any
                                                       :sijaintitieto sc/Any
                                                       :kuvaus sc/Str
                                                       :tunnus sc/Str
                                                       :kokonaisala sc/Str
                                                       :kayttotarkoitus sc/Str
                                                       :kiinttun Kiinteistotunnus})})

(sc/defschema ToimenpideTiedot
  (field [Toimenpide] "Hakemuksen toimenpiteiden tiedot"))

(sc/defschema HakemusTiedot
  {:asiointitunnus Asiointitunnus
   :kiinteistoTunnus Kiinteistotunnus
   :osoite Osoite
   :sijaintiETRS SijaintiETRS
   :kuntakoodi Kuntakoodi
   :saapumisPvm DateString
   :toimenpiteet ToimenpideTiedot})

(sc/defschema JulkinenHakemusData
              (field [HakemusTiedot] "Hakemusten tiedot"))

(sc/defschema OrganizationId
  (field sc/Str "Organisaation tunnus"))
