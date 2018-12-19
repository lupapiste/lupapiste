(ns lupapalvelu.exports.reporting-db
  (:require [lupapalvelu.data-skeleton :as ds]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.rakennuslupa-canonical :refer [application-to-canonical katselmus-canonical]]
            [lupapalvelu.pate.verdict-canonical :refer [verdict-canonical]]
            [lupapalvelu.pate.verdict-common :as vc]
            [sade.util :as util]))

;; Ei suomeksi, noudattaa omaa tietomallia

;;

(def reporting-app-skeleton
  {;; Hakemus
   :id (ds/access :id)
   ;; Factassa asian pääavain, tulee itse asiassa päätöksien tietoihin
   ;; :lupanumero (ds/access :test) ;; TODO: Mistä? Kuntalupatunnus? Selite: Löytyy koko numero samasata kentästä: Luvan numero - Luvan vuosi - kaupunginosa - lupatyyppi
   ;; :luvanNumero (ds/access :test) ;; Oletus: lupanumerosta
   ;; :luvanVuosi (ds/access :test) ;; Oletus: lupanumerosta
   :araFunding (ds/access :araFunding) ;; TODO: Mistä? Ara-käsittelijä flägi boolean
   ;; :eiJulkistaLainaa (ds/access :test) ;; TODO: Mistä? Selite: Hankkeeseen liittyvä
   ;; :kaupunginosa (ds/access :test) ;; TODO: Mistä?
   ;; :korkotukivuokra-asunnotPitkaaik (ds/access :test) ;; TODO: Mistä? Selite: Johdettuna, ei tarpeellista jatkossa
   :state (ds/access :state) ;; Selite: Tilakone. Vierillä, käyttöönotettu... OMAT TERMIT esim submitted
   :permitType (ds/access :permitType) ;; Selite: Voidaan päätellä toimenpidetiedosta permitType

   :location-etrs-tm35fin (ds/access :location)
   :location-wgs84 (ds/access :location-wgs84)
   :address (ds/access :address)
   :stateChangeTs (ds/access :stateChangeTs) ;; Selite: Koska hakemuksen viimeisin tila on tullut voimaan Historysta etsitään
   :projectDescription (ds/access :projectDescription) ;; Selite: Hankkeen kuvaus korvaamaan tätä. Konversiossa huomioitava, rakennusvalvonta-asian kuvaus

   :uuttaHuoneistoalaa (ds/access :test) ;; Selite: Mahdollisuus myös negatiiviseen arvoon. Oleellinen tieto. Tulee rakennuksen tietona ja summataan hakemukselle
   :uuttaKerrosalaa (ds/access :test) ;; Selite: Mahdollisuus myös negatiiviseen arvoon. Oleellinen tieto. Kerrosalassa mukana yleiset tilat. Tulee rakennuksen tietona ja summataan hakemukselle

   ;; Huoneisto
   :huoneidenLkm (ds/access :test) ;; Selite: Lupapisteessä huoneisto-käsite on huoneistomuutos
   :huoneistonLisaysMuutosPoistokoodi (ds/access :test)

   ;; Katselmus
   :reviews (ds/array-from :reviews
                           {:type           (ds/access :review-type) ;; KatselmuksenlajiSelite: Liittyy toimenpiteeseen
                            :date           (ds/access :review-date) ;; Pitopvm
                            :reviewer       (ds/access :review-reviewer)
                            :verottajanTvLl (ds/access :review-verottajanTvLl)})

   ;; Kiinteistö
   :autopaikkavaatimusEnintaan (ds/access :test)
   :autopaikkavaatimusRakennettava (ds/access :test)
   :autopaikkavaatimusRakennettavaVahintaan (ds/access :test)
   :autopaikkojaKiinteistolla (ds/access :test)
   :autopaikkojaKiinteistonUlkopuolella (ds/access :test)
   :autopaikkojaRakennettavaYhteensa (ds/access :test)
   :autopaikkojaRakennettu (ds/access :test)
   :autopaikkojaRakennettuYhteensa (ds/access :test)
   :autopaikkojaVahintaan (ds/access :test)
   :kiinteistotunnus (ds/access :test) ;; Mukana mahdollinen hallintayksikkötunnus

   ;; Lasku -> taulukko?
   :laskurivinTaksankohdanSelite (ds/access :test) ;; Selite: Kehityksen alla oleva toiminnallisuus
   :laskunPvm (ds/access :test)
   :laskunSummaYhteensa (ds/access :test)
   :laskunViiteasiatunnus (ds/access :test)
   :laskutusvuosi (ds/access :test)
   :rivisumma (ds/access :test)

   ;; Taksa
   :taksanSelite (ds/access :test)
   :taksanVoimaantulopvm (ds/access :test) ;; Selite: Liittyy laskuun
   :taksankohta (ds/access :test)

   ;; Lausunto -> taulukko?
   ;; TODO: Lausunnon sisältö?
   :lausunnonAntajanNimi (ds/access :test)
   :lausunnonAntopvm (ds/access :test)

   ;; Osapuoli
   ;; Providing all data for now as it is unclear what is actually needed
   :parties (ds/array-from :parties
                           (ds/access :context))

   ;; Osapuoli: Suunnittelijat
   ;; Providing all data for now as it is unclear what is actually needed
   :planners (ds/array-from :planners
                            (ds/access :context))

   ;; Päätös
   :verdicts (ds/array-from :verdicts
                            (ds/access :context))

   ;; Rakennus
   ;; Raportoidaan se mitä sattuu löytymään
   :hankkeenRakennuksenHuoneistonAla (ds/access :test)
   :hankkeenRakennuksenKerrosala (ds/access :test)
   :hankkeenRakennuksenKokonaisala (ds/access :test)
   :hankkeenRakennuksenKayttotarkoitus (ds/access :test)
   :hankkeenRakennuksenLaajennuksenKerrosala (ds/access :test)
   :hankkeenRakennuksenLammitystapa (ds/access :test)
   :hankkeenRakennuksenPolttoaine (ds/access :test)
   :hankkeenRakennuksenRaukeamispvm (ds/access :test)
   :hankkeenRakennuksenTilavuus (ds/access :test)
   :hankkeenRakennuksenToimenpidenro (ds/access :test)
   :hankkeenRakennuksenToidenAloituspvm (ds/access :test)
   :hankkeenRakennuksenValmistumispvm (ds/access :test)

   :rakennuksenKerrosala (ds/access :test)
   :rakennuksenKoordinaatit (ds/access :test) ;; (KKJ ja GK) Selite: Löytyy joko WGS84 tai ETRS-formaatissa
   :rakennuksenKayttotarkoitus (ds/access :test)
   :rakennuksenPaaasiallinenHallinta (ds/access :test)
   :rakennuksenRakennelmanSelite (ds/access :test)
   :rakennusnumero (ds/access :test) ;; rakennusnumero(n viimeinen osa): Factassa kc_raknro eli vanhanmallinen
   :rakennustunnus (ds/access :test) ;; VTJ-PRT
   :uusiaAsuntoja (ds/access :test)
   :uusienAsuntojenHuoneistoalaLuvalla (ds/access :test)
   :uusienAsuntojenLkm (ds/access :test) ;; Selite: Per toimenpide
   :uusienAsuntojenLkmLuvalla (ds/access :test) ;; Selite: Summattuna rakennusten lukumäärä per hakemus

   ;; Rakennuspaikka
   :rakennettu (ds/access :test) ; rakennuspaikalla valmiiden rakennusten kerrosala yhteensä. Selite: Ennen Lupapisteen tuloa olevat mahdottomia, tulevaisuudessa Matti?
   :sallittu (ds/access :test) ; rakennuspaikan sallittu kerrosala kaavan mukaan. Selite: Tulossa rajapintaan, otetaan jatkossa mukaan. Ainakin uusille luville.

   ;; Rakentamistoimenpide
   :hankkeenRakennuksenMuutostyonLaji (ds/access :test)

   ;; Toimenpide
   :rakentamistoimenpiteenLaji (ds/access :test) ;; (rakennuksella) Selite: Yhdellä hakemuksella voi olla useampi toimenpide

   ;; Työnjohtajahakemus
   :työnjohtajanLaji (ds/access :test) ;; Työnjohtajasta oma hakemus viitelupana
   :työnjohtajanEmail (ds/access :test)

   ;; Muuta
   :aluejaonNimi (ds/access :test) ;; aluejaon (suuralueen) nimi
   ;; TODO Kaikkia kenttiä ei vielä listattu
   })

(def get-katselmustieto
  (ds/from-context [:context :Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :katselmustieto :Katselmus]))

(defn- verdict-via-canonical
  "Builds a representation of the given verdict for reporting db API
  using mostly the canonical representation."
  [verdict]
  (-> (:Paatos (verdict-canonical (:lang ctx)
                                  verdict))
      (assoc :kuntalupatunnus
             (vc/verdict-municipality-permit-id verdict))
      (update-in [:lupamaaraykset :maaraystieto]
                 (partial mapv (comp :sisalto :Maarays)))
      (update-in [:lupamaaraykset :vaadittuErityissuunnitelmatieto]
                 (partial mapv :VaadittuErityissuunnitelma))
      (update-in [:lupamaaraykset :vaadittuTyonjohtajatieto]
                 (partial mapv (comp :tyonjohtajaRooliKoodi :VaadittuTyonjohtaja)))
      (update-in [:lupamaaraykset :vaaditutKatselmukset]
                 (partial mapv :Katselmus))))

(defn reporting-app-accessors [application lang]
  {:test (constantly "foo")
   :id (ds/from-context [:application :id])
   :address (ds/from-context [:application :address])
   :araFunding (ds/from-context [:application #(domain/get-document-by-name % "hankkeen-kuvaus")
                                 :data tools/unwrapped :rahoitus]
                                false)
   :context (ds/from-context [:context])

   :location (ds/from-context [:application :location])
   :location-wgs84 (ds/from-context [:application :location-wgs84])
   :projectDescription (ds/from-context [:canonical :Rakennusvalvonta :rakennusvalvontaAsiatieto
                                         :RakennusvalvontaAsia :asianTiedot :Asiantiedot
                                         :rakennusvalvontaasianKuvaus])

   :parties (ds/from-context [:canonical :Rakennusvalvonta :rakennusvalvontaAsiatieto
                              :RakennusvalvontaAsia :osapuolettieto :Osapuolet :osapuolitieto
                              util/sequentialize (partial mapv :Osapuoli)])
   :planners (ds/from-context [:canonical :Rakennusvalvonta :rakennusvalvontaAsiatieto
                               :RakennusvalvontaAsia :osapuolettieto :Osapuolet :suunnittelijatieto
                               util/sequentialize (partial mapv :Suunnittelija)])

   :reviews (ds/from-context [:application :tasks (partial mapv #(katselmus-canonical application lang % nil))])
   :review-date (ds/from-context [get-katselmustieto :pitoPvm])
   :review-type (ds/from-context [get-katselmustieto :katselmuksenLaji])
   :review-reviewer (ds/from-context [get-katselmustieto :pitaja])
   :review-verottajanTvLl (ds/from-context [get-katselmustieto :verottajanTvLlKytkin] false)

   :state (ds/from-context [:application :state])
   :stateChangeTs (ds/from-context [:application :history (partial filterv :state) last :ts])
   :permitType (ds/from-context [:application :permitType])

   :verdicts (fn [ctx]
               ((ds/from-context [:application vc/all-verdicts
                                  (partial map verdict-via-canonical)])
                ctx))})

(defn ->reporting-result [application lang]
  ;; TODO check permit type, R or P (or others as well?)
  (let [application-canonical (application-to-canonical application lang)]
    (ds/build-with-skeleton reporting-app-skeleton
                            {:application application
                             :canonical application-canonical
                             :lang lang}
                            (reporting-app-accessors application lang))))
