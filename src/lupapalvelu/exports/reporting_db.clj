(ns lupapalvelu.exports.reporting-db
  (:require [clojure.set :as set]
            [lupapalvelu.data-skeleton :as ds]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.rakennuslupa-canonical :refer [application-to-canonical katselmus-canonical]]
            [lupapalvelu.pate.verdict-canonical :refer [verdict-canonical]]
            [lupapalvelu.pate.verdict-common :as vc]
            [sade.core :refer :all]
            [sade.util :as util]))

(defn sequentialize [xs]
  (if xs
    (util/sequentialize xs)
    []))

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
   ;; Kiinteistötunnus

   :location-etrs-tm35fin (ds/access :location)
   :location-wgs84 (ds/access :location-wgs84)
   :address (ds/access :address)
   :stateChangeTs (ds/access :stateChangeTs) ;; Selite: Koska hakemuksen viimeisin tila on tullut voimaan Historysta etsitään
   :projectDescription (ds/access :projectDescription) ;; Selite: Hankkeen kuvaus korvaamaan tätä. Konversiossa huomioitava, rakennusvalvonta-asian kuvaus

   ;; Huoneisto
   :huoneidenLkm (ds/access :test) ;; Selite: Lupapisteessä huoneisto-käsite on huoneistomuutos
   :huoneistonLisaysMuutosPoistokoodi (ds/access :test)

   ;; Katselmus
   :reviews (ds/array-from :reviews
                           {:type           (ds/access :review-type) ;; KatselmuksenlajiSelite: Liittyy toimenpiteeseen
                            :date           (ds/access :review-date) ;; Pitopvm
                            :reviewer       (ds/access :review-reviewer)
                            :verottajanTvLl (ds/access :review-verottajanTvLl)})

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

   :statements (ds/access :statements)

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

   :uusiaAsuntoja (ds/access :test)
   :uusienAsuntojenHuoneistoalaLuvalla (ds/access :test)
   :uusienAsuntojenLkm (ds/access :test) ;; Selite: Per toimenpide
   :uusienAsuntojenLkmLuvalla (ds/access :test) ;; Selite: Summattuna rakennusten lukumäärä per hakemus

   ;; Rakennuspaikka
   :rakennettu (ds/access :test) ; rakennuspaikalla valmiiden rakennusten kerrosala yhteensä. Selite: Ennen Lupapisteen tuloa olevat mahdottomia, tulevaisuudessa Matti?
   :sallittu (ds/access :test) ; rakennuspaikan sallittu kerrosala kaavan mukaan. Selite: Tulossa rajapintaan, otetaan jatkossa mukaan. Ainakin uusille luville.

   ;; Rakentamistoimenpide
   :operations (ds/array-from
                :operations
                {:id (ds/access :operation-id)
                 :kuvaus (ds/access :operation-description)
                 :rakennus (ds/access :operation-building)
                 :rakennelma {:tiedot :foo}})

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
  [lang verdict]
  (-> (:Paatos (verdict-canonical lang
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

(def rakennusvalvonta-asia
  (ds/from-context [:canonical :Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia]))

(def osapuolet
  (ds/from-context [rakennusvalvonta-asia :osapuolettieto :Osapuolet]))

(defn ->statements [canonical-statements]
  (->> canonical-statements
       (map (comp :lausuntotieto :Lausunto))
       (remove nil?)
       (map :Lausunto)
       (mapv #(update % :puoltotieto :Puolto))))

(def- operation-types
  [:uusi :laajennus :uudelleenrakentaminen :purkaminen :muuMuutosTyo :kaupunkikuvaToimenpide])

(defn- dissoc-operation-types [operation]
  (apply dissoc operation operation-types))

(defn- operation-description [operation]
  (:kuvaus (util/find-first identity (map #(get operation %) operation-types))))

(defn- operation-building [context]
  (-> ((ds/from-context [:context :rakennustieto :Rakennus]) context)
      (update :omistajatieto (comp (partial mapv :Omistaja)
                                   sequentialize))
      (dissoc :alkuHetki :sijaintitieto :yksilointitieto)
      (set/rename-keys {:rakennuksenTiedot :tiedot :omistajatieto :omistajat})))

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

   :operations (ds/from-context [rakennusvalvonta-asia :toimenpidetieto sequentialize
                                 (partial mapv :Toimenpide)])
   :operation-id #(or ((ds/from-context [:context :rakennustieto :Rakennus :yksilointitieto]) %)
                      ((ds/from-context [:context :rakennelmatieto :Rakennelma :yksilointitieto]) %))
   :operation-description (ds/from-context [:context operation-description])
   ;; We know that :rakennustieto is not sequential, i.e. there's only one building per operation
   :operation-building operation-building

   :projectDescription (ds/from-context [:canonical :Rakennusvalvonta :rakennusvalvontaAsiatieto
                                         :RakennusvalvontaAsia :asianTiedot :Asiantiedot
                                         :rakennusvalvontaasianKuvaus])

   :parties (ds/from-context [osapuolet :osapuolitieto
                              sequentialize (partial mapv :Osapuoli)])
   :planners (ds/from-context [osapuolet :suunnittelijatieto
                               sequentialize (partial mapv :Suunnittelija)])

   :reviews (ds/from-context [:application :tasks (partial mapv #(katselmus-canonical application lang % nil))])
   :review-date (ds/from-context [get-katselmustieto :pitoPvm])
   :review-type (ds/from-context [get-katselmustieto :katselmuksenLaji])
   :review-reviewer (ds/from-context [get-katselmustieto :pitaja])
   :review-verottajanTvLl (ds/from-context [get-katselmustieto :verottajanTvLlKytkin] false)
   ;; TODO Propably not enough review data at the moment

   :state (ds/from-context [:application :state])
   :stateChangeTs (ds/from-context [:application :history (partial filterv :state) last :ts])
   :statements (ds/from-context [rakennusvalvonta-asia :lausuntotieto sequentialize
                                 ->statements])
   :permitType (ds/from-context [:application :permitType])

   :verdicts (fn [ctx]
               ((ds/from-context [:application vc/all-verdicts
                                  #(map (partial verdict-via-canonical (:lang ctx))
                                        %)])
                ctx))})

(defn ->reporting-result [application lang]
  ;; TODO check permit type, R or P (or others as well?)
  (let [application-canonical (application-to-canonical application lang)]
    (ds/build-with-skeleton reporting-app-skeleton
                            {:application application
                             :canonical application-canonical
                             :lang lang}
                            (reporting-app-accessors application lang))))
