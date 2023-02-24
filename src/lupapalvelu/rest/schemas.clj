(ns lupapalvelu.rest.schemas
  (:require [ring.swagger.json-schema :as rjs]
            [schema.core :as sc]
            [sade.util :as util]
            [sade.schemas :as ssc]
            [lupapalvelu.rest.config :as config]))

(defn field [schema desc]
  (rjs/field schema {:description desc}))

(sc/defschema Asiointitunnus
  (field sc/Str "Hakemuksen asiointitunnus esim. LP-2016-000-90001"))

(sc/defschema Kuntalupatunnus
  (field sc/Str "Taustaj\u00e4rjestelm\u00e4ss\u00e4 olevan hakemuksen kuntalupatunnus"))

(sc/defschema AuthorizeApplicantsBoolean
  (field sc/Bool "Valtuutetaanko hakijaosapuolet Lupapisteen hakemukselle, jos t\u00e4m\u00e4n operaation seurauksena luodaan uusi hakemus. Oletus true, jos arvo puuttuu"))

(sc/defschema Kiinteistotunnus
  (field sc/Str "Kiinteist\u00f6tunnus"))

(sc/defschema SijaintiETRS
  (field [(sc/one sc/Num "lon")
          (sc/one sc/Num "lat")] "Rakennuspaikan sijainti (ETRS-TM35FIN), pituus- ja leveysasteet"))

(sc/defschema Kuntakoodi
  (field (sc/cond-pre sc/Num #"^\d{3}$") "Kuntakoodi esim. 091 (Helsinki)"))

(sc/defschema DateString
  (field (sc/constrained sc/Str (partial re-matches #"\d{4}-\d{2}-\d{2}"))
         "P\u00e4iv\u00e4m\u00e4\u00e4r\u00e4 muodossa yyyy-MM-dd, esim. 2016-05-23."))

(sc/defschema Osoite
  (field sc/Str "Rakennuspaikan katuosoite"))

(sc/defschema asiatyyppi
  (field sc/Str "Asiatyyppi"))

(sc/defschema Huoneisto
  (field (util/map-keys sc/optional-key
                        {:muutostapa sc/Str
                         :huoneluku sc/Str
                         :keittionTyyppi sc/Str
                         :huoneistoala sc/Str
                         :varusteet sc/Any
                         :huoneistonTyyppi sc/Str
                         :huoneistotunnus sc/Any
                         :valtakunnallinenHuoneistotunnus sc/Str}) "Huoneiston tiedot"))

(sc/defschema Kayttotarkoitus
  (field (util/map-keys sc/optional-key {:pintaAla sc/Str
                                         :kayttotarkoitusKoodi sc/Str}) "Esitt\u00e4\u00e4 pinta-alat k\u00e4ytt\u00f6tarkoituksittain"))

(sc/defschema LaajennuksenTiedot
  (field (util/map-keys sc/optional-key
                        {:tilavuus sc/Str
                         :kerrosala sc/Str
                         :rakennusoikeudellinenKerrosala sc/Str
                         :kokonaisala sc/Str
                         :huoneistoala (sc/maybe [Kayttotarkoitus])}) "Laajennuksen tiedot"))

(sc/defschema UusiRakennus
  {:kuvaus (field sc/Str "Uusi rakennus, toimenpiteen kuvaus")})

(sc/defschema Laajennus
  {:kuvaus (field sc/Str "Muu muutosty\u00f6, toimenpiteen kuvaus")
   :laajennuksentiedot LaajennuksenTiedot
   (sc/optional-key :perusparannusKytkin) (field sc/Bool "Perusparannusta (kyll\u00e4/ei)")})

(sc/defschema MuuMuutostyo
  {:kuvaus (field sc/Str "Muu muutosty\u00f6, toimenpiteen kuvaus")
   :perusparannusKytkin (field sc/Bool "Perusparannusta (kyll\u00e4/ei)")
   (sc/optional-key :rakennustietojaEimuutetaKytkin) (field sc/Bool "Rakennustietoja ei muuteta (kyll\u00e4/ei)")
   (sc/optional-key :muutostyonLaji) (field sc/Str "Muutosty\u00f6n laji")})

(sc/defschema Purkaminen
  {:kuvaus (field sc/Str "Muu muutosty\u00f6, toimenpiteen kuvaus")
   (sc/optional-key :purkamisenSyy) sc/Str
   (sc/optional-key :poistumaPvm) DateString})

(sc/defschema Toimenpide
              {(sc/optional-key :uusi)         UusiRakennus
               (sc/optional-key :laajennus)    Laajennus
               (sc/optional-key :purkaminen)   Purkaminen
               (sc/optional-key :muuMuutosTyo) MuuMuutostyo
               (sc/optional-key :kaupunkikuvaToimenpide) {:kuvaus sc/Str}
               (sc/optional-key :rakennuksenTiedot) (util/map-keys sc/optional-key
                                                      {:rakennustunnus sc/Any
                                                       :kayttotarkoitus sc/Str
                                                       :rakennusluokka sc/Str
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
                                                       :asuinhuoneistot {:huoneisto [Huoneisto]
                                                                         (sc/optional-key :asuntojenPintaala) sc/Any
                                                                         (sc/optional-key :asuntojenLkm) sc/Any}
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

(sc/defschema GMLPoint      {:Point {:pos sc/Str}})
(sc/defschema GMLPolygon    {:Polygon {:exterior {:LinearRing {:pos [sc/Str]}}}})
(sc/defschema GMLLineString {:LineString {:pos [sc/Str]}})

(sc/defschema YAKarttakuvio
  (field (util/map-keys sc/optional-key
                        {:piste  GMLPoint
                         :viiva  GMLLineString
                         :alue   GMLPolygon
                         :nimi   sc/Str
                         :kuvaus sc/Str
                         :korkeusTaiSyvyys sc/Str
                         :pintaAla (sc/cond-pre sc/Str sc/Int)}) ""))

(sc/defschema YleisenAlueenKayttolupa
  (field {:kayttotarkoitus sc/Str
          :sijainnit       [YAKarttakuvio]} "Yleisen alueen k\u00e4ytt\u00f6lupahakemuksen tiedot"))

(sc/defschema HakemusTiedot
  {:asiointitunnus Asiointitunnus
   :kiinteistoTunnus Kiinteistotunnus
   :osoite Osoite
   :sijaintiETRS SijaintiETRS
   :kuntakoodi Kuntakoodi
   :saapumisPvm DateString
   (sc/optional-key :asiatyyppi) asiatyyppi
   (sc/optional-key :toimenpiteet) ToimenpideTiedot
   (sc/optional-key :yleisenAlueenKayttolupa) YleisenAlueenKayttolupa})

(sc/defschema ApiResponse
  {:ok sc/Bool
   (sc/optional-key :text) sc/Keyword})

(sc/defschema JatetytHakemuksetResponse
  (assoc ApiResponse :data [HakemusTiedot]))

(sc/defschema IdFromPreviousPermitResponse
  (assoc ApiResponse (sc/optional-key :id) Asiointitunnus))

(sc/defschema YaMattiReviewResponse
  (assoc ApiResponse (sc/optional-key :status) sc/Num))

(sc/defschema OrganizationId
  (field sc/Str "Organisaation tunnus"))

(sc/defschema ApplicationId
  (field ssc/ApplicationId "LP-tunnus"))

(sc/defschema UUIDStr
  (field ssc/UUIDStr "UUID"))

(sc/defschema KatselmusJSONStr
  (field sc/Str "Katselmus-json"))

(sc/defschema OperationId
  (field ssc/ObjectIdStr "Toimenpiteen tunnus"))

(sc/defschema NationalBuildingId
  (field ssc/Rakennustunnus "Pysyv\u00e4 rakennustunnus"))

(sc/defschema Location
  (field ssc/Location "Sijaintipiste ETRS-TM35FIN koordinaatistossa"))

(sc/defschema Configuration
  (field config/Configuration "Konfiguraatioarvot"))

(sc/defschema Stairway
  (field sc/Str "Porras"))

(sc/defschema FlatNumber
  (field sc/Str "Huoneistonumero"))

(sc/defschema SplittingLetter
  (field sc/Str "Jakokirjain"))

(sc/defschema PermanenFlatNumber
  (field sc/Str "Pysyvä Huoneistotunnus"))

(sc/defschema ApartmentData
  (field {(sc/optional-key :stairway) Stairway
          :flatNumber FlatNumber
          (sc/optional-key :splittingLetter) SplittingLetter
          (sc/optional-key :permanentFlatNumber) PermanenFlatNumber} "Yksittäisen huoneiston tiedot"))

(sc/defschema ApartmentsData
  (field (sc/maybe [ApartmentData]) "Toimenpiteen/rakennuksen huoneistot"))
