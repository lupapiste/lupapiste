(ns lupapalvelu.opendata.schemas
  (:require [schema.core :as sc]
            [ring.swagger.json-schema :as rjs]
            [schema.core :as s]
            [sade.util :as util]))

(defn- field [schema desc]
  (rjs/field schema {:description desc}))

(sc/defschema Asiointitunnus
  (field sc/Str "Hakemuksen asiointitunnus esim. LP-2016-000-90001"))

(sc/defschema Kiinteistotunnus
  (field sc/Str "Kiinteistötunnus"))

(sc/defschema SijaintiETRS
  (field [(s/one sc/Num "lon")
          (s/one sc/Num "lat")] "Rakennuspaikan sijainti (ETRS-TM35FIN), pituus- ja leveysasteet"))

(sc/defschema Kuntakoodi
  (field sc/Str "Kuntakoodi"))

(sc/defschema DateString
  (field (s/constrained sc/Str (partial re-matches #"\d{4}-\d{2}-\d{2}"))
         "Päivämäärä muodossa yyyy-MM-dd, esim. 2016-05-23."))

(sc/defschema Osoite
  (field sc/Str "Rakennuspaikan katuosoite"))

(sc/defschema Toimenpide
              {(sc/optional-key :uusi) {:kuvaus sc/Str}
               (sc/optional-key :muuMuutosTyo) {:muutostyonLaji sc/Str
                                                :kuvaus sc/Str
                                                :perusparannusKytkin sc/Bool}
               (sc/optional-key :laajennus) {:kuvaus sc/Str
                                             :perusparannusKytkin sc/Bool
                                             :laajennuksentiedot sc/Any}
               (sc/optional-key :purkaminen) (util/map-keys sc/optional-key
                                               {:kuvaus sc/Str
                                                :purkamisenSyy sc/Str
                                                :poistumaPvm sc/Any})
               (sc/optional-key :kaupunkikuvaToimenpide) {:kuvaus sc/Str}
               (sc/optional-key :rakennuksenTiedot) (util/map-keys sc/optional-key
                                                      {:rakennustunnus sc/Str
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
                                                       :asuinhuoneistot sc/Any
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
