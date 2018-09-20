(ns lupapalvelu.backing-system.krysp.building-reader
  (:require [taoensso.timbre :refer [trace debug info warn error]]
            [lupapalvelu.backing-system.krysp.common-reader :as common]
            [sade.common-reader :as cr]
            [sade.strings :as ss]
            [sade.validators :as v]
            [sade.xml :refer [get-text select select1 under has-text xml->edn parse-string]]
            [lupapalvelu.document.tools :as tools]
            [sade.util :as util]
            [lupapalvelu.document.schemas :as schema]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.building :as building]
            [lupapalvelu.mongo :as mongo]
            [sade.coordinate :as coordinate]))

(defn building-xml
  "Returns clojure.xml map or an empty map if the data could not be downloaded."
  ([server credentials property-id]
   (building-xml server credentials property-id false))
  ([server credentials property-id raw?]
   (let [url (common/wfs-krysp-url server common/building-type (common/property-equals common/rakennuksen-kiinteistotunnus property-id))
         options {:http-error :error.building-info.backend-system-not-responding, :connection-error :error.building-info.backend-system-not-responding}]
     (or (cr/get-xml url options credentials raw?) {}))))

(defn pysyva-rakennustunnus
  "Returns national building id or nil if the input was not valid"
  [^String s]
  {:pre [(or (nil? s) (string? s))]}
  (let [building-id (or (ss/trim s) "")]
    (when (v/rakennustunnus? building-id)
      building-id)))

(defn- pos-xml->location-map [point-xml building-id]
  (try
    (let [source-projection (common/->source-projection point-xml [:Point])
          point-str (get-text point-xml :pos)
          coords (ss/split point-str #" ")]
      (when (and source-projection (= 2 (count coords)))
        {:location (coordinate/convert source-projection common/to-projection 3 coords)
         :location-wgs84 (coordinate/convert source-projection :WGS84 5 coords)}))
    (catch Exception e (error e "Coordinate conversion failed for building " building-id))))

(defn- ->list "a as list or nil. a -> [a], [b] -> [b]" [a] (when a (-> a list flatten)))

(defn- ->building-ids [id-container xml-no-ns]
  (let [national-id    (pysyva-rakennustunnus (get-text xml-no-ns id-container :valtakunnallinenNumero))
        local-short-id (-> (get-text xml-no-ns id-container :rakennusnro)
                           ss/trim (#(when-not (ss/blank? %) %)))
        local-id       (-> (get-text xml-no-ns id-container :kunnanSisainenPysyvaRakennusnumero)
                           ss/trim (#(when-not (ss/blank? %) %)))
        edn            (-> xml-no-ns (select [id-container]) first xml->edn id-container)
        building-index (get-text xml-no-ns id-container :jarjestysnumero)]
    (merge
      {:propertyId   (get-text xml-no-ns id-container :kiinttun)
       :buildingId   (first (remove ss/blank? [national-id local-short-id]))
       :nationalId   national-id
       :localId      local-id
       :localShortId local-short-id
       :index        building-index
       :usage        (or (get-text xml-no-ns :kayttotarkoitus) "")
       :area         (get-text xml-no-ns :kokonaisala)
       :created      (some->> (get-text xml-no-ns :alkuHetki)
                              cr/parse-datetime
                              (cr/unparse-datetime :year))
       :operationId  (some (fn [{{:keys [tunnus sovellus]} :MuuTunnus}]
                             (when (#{"toimenpideId" "Lupapiste" "Lupapistetunnus"} sovellus)
                               tunnus))
                           (->list (:muuTunnustieto edn)))
       :description  (or (:rakennuksenSelite edn) building-index)}
      (-> (select1 xml-no-ns :sijaintitieto :Sijainti :piste :Point)
          (pos-xml->location-map national-id)))))

(defn ->buildings-summary [xml]
  (let [xml-no-ns (cr/strip-xml-namespaces xml)]
    (distinct
      (concat
        (map (partial ->building-ids :rakennustunnus) (select xml-no-ns [:Rakennus]))
        (map (partial ->building-ids :tunnus) (select xml-no-ns [:Rakennelma]))))))


(defn building-info-list
  "Gets buildings info either from cache selection or fetches via WFS."
  [url credentials propertyId]
  (if-let [{buildings :buildings} (mongo/select-one :buildingCache {:propertyId propertyId})]
    buildings
    (let [kryspxml  (building-xml url credentials propertyId)
          buildings (->buildings-summary kryspxml)]
      (when (not-empty buildings)
        (try
          (mongo/insert :buildingCache
                        (mongo/with-mongo-meta {:propertyId propertyId :buildings buildings})
                        com.mongodb.WriteConcern/UNACKNOWLEDGED)
          (catch com.mongodb.DuplicateKeyException _)))
      buildings)))

(def ...notfound... nil)
(def ...notimplemented... nil)

(defn- ->rakennuksen-omistaja-legacy-version [omistaja]
  {:_selected "yritys"
   :yritys {:liikeJaYhteisoTunnus                     (get-text omistaja :tunnus)
            :osoite {:katu                            (get-text omistaja :osoitenimi :teksti)
                     :postinumero                     (get-text omistaja :postinumero)
                     :postitoimipaikannimi            (get-text omistaja :postitoimipaikannimi)}
            :yhteyshenkilo {:henkilotiedot {:etunimi  (get-text omistaja :henkilonnimi :etunimi)      ;; does-not-exist in test
                                            :sukunimi (get-text omistaja :henkilonnimi :sukunimi)     ;; does-not-exist in test
                                            :yhteystiedot {:email     ...notfound...
                                                           :puhelin   ...notfound...}}}
            :yritysnimi                               (get-text omistaja :nimi)}})

(defn ->rakennuksen-omistaja [omistaja]
  (cond
    (seq (select omistaja [:yritys]))
        (merge (common/->yritys omistaja) {:omistajalaji     (get-text omistaja :omistajalaji :omistajalaji)
                                           :muu-omistajalaji (get-text omistaja :omistajalaji :muu)})
    (seq (select omistaja [:henkilo]))
        (merge (common/->henkilo omistaja) {:omistajalaji     (get-text omistaja :omistajalaji :omistajalaji)
                                            :muu-omistajalaji (get-text omistaja :omistajalaji :muu)})
    :default (->rakennuksen-omistaja-legacy-version omistaja)))

(def polished  (comp cr/index-maps cr/cleanup cr/convert-booleans))

(def empty-building-ids {:valtakunnallinenNumero ""
                         :rakennusnro ""})

(defn get-non-zero-integer-text
  "Parses text as number, strips decimals and returns the result as
  string if non-zero"
  [xml key]
  (let [n (some->> (get-text xml key)
                   util/->double
                   int)]
    (when (and (integer? n)
               (not (zero? n)))
      (str n))))

(defn ->rakennuksen-tiedot
  ([xml building-id]
   (let [stripped  (cr/strip-xml-namespaces xml)
         rakennus  (or
                     (select1 stripped [:rakennustieto :> (under [:valtakunnallinenNumero (has-text building-id)])])
                     (select1 stripped [:rakennustieto :> (under [:rakennusnro (has-text building-id)])]))]
     (->rakennuksen-tiedot rakennus)))
  ([rakennus]
   (when rakennus
     (util/deep-merge
       (->
         {:body schema/rakennuksen-muuttaminen}
         (tools/create-unwrapped-data tools/default-values)
         ; Dissoc values that are not read
         (dissoc :buildingId :muutostyolaji :perusparannuskytkin :rakennustietojaEimuutetaKytkin)
         (util/dissoc-in [:huoneistot :0 :muutostapa]))
       (polished
         (util/assoc-when-pred
           {:muutostyolaji                          ...notimplemented...
            :valtakunnallinenNumero                 (pysyva-rakennustunnus (get-text rakennus :rakennustunnus :valtakunnallinenNumero))
            :kunnanSisainenPysyvaRakennusnumero     (get-text rakennus :rakennustunnus :kunnanSisainenPysyvaRakennusnumero)
            :rakennusnro                            (ss/trim (get-text rakennus :rakennustunnus :rakennusnro))
            :manuaalinen_rakennusnro                ""
            :jarjestysnumero                        (get-text rakennus :rakennustunnus :jarjestysnumero)
            :kiinttun                               (get-text rakennus :rakennustunnus :kiinttun)
            :verkostoliittymat                      (cr/all-of rakennus [:verkostoliittymat])

            :osoite {:kunta                         (get-text rakennus :osoite :kunta)
                     :lahiosoite                    (get-text rakennus :osoite :osoitenimi :teksti)
                     :osoitenumero                  (get-text rakennus :osoite :osoitenumero)
                     :osoitenumero2                 (get-text rakennus :osoite :osoitenumero2)
                     :jakokirjain                   (get-text rakennus :osoite :jakokirjain)
                     :jakokirjain2                  (get-text rakennus :osoite :jakokirjain2)
                     :porras                        (get-text rakennus :osoite :porras)
                     :huoneisto                     (get-text rakennus :osoite :huoneisto)
                     :postinumero                   (get-text rakennus :osoite :postinumero)
                     :postitoimipaikannimi          (get-text rakennus :osoite :postitoimipaikannimi)}
            :kaytto {:kayttotarkoitus               (get-text rakennus :kayttotarkoitus)
                     :rakentajaTyyppi               (get-text rakennus :rakentajatyyppi)}
            :luokitus {:energialuokka               (get-text rakennus :energialuokka)
                       :energiatehokkuusluku        (get-text rakennus :energiatehokkuusluku)
                       :energiatehokkuusluvunYksikko (get-text rakennus :energiatehokkuusluvunYksikko)
                       :paloluokka                  (get-text rakennus :paloluokka)}
            :mitat {:kellarinpinta-ala              (get-non-zero-integer-text rakennus :kellarinpinta-ala)
                    :kerrosala                      (get-non-zero-integer-text rakennus :kerrosala)
                    :rakennusoikeudellinenKerrosala (get-non-zero-integer-text rakennus :rakennusoikeudellinenKerrosala)
                    :kerrosluku                     (get-text rakennus :kerrosluku)
                    :kokonaisala                    (get-non-zero-integer-text rakennus :kokonaisala)
                    :tilavuus                       (get-non-zero-integer-text rakennus :tilavuus)}
            :rakenne {:julkisivu                    (get-text rakennus :julkisivumateriaali)
                      :kantavaRakennusaine          (get-text rakennus :rakennusaine)
                      :rakentamistapa               (get-text rakennus :rakentamistapa)}
            :lammitys {:lammitystapa                (get-text rakennus :lammitystapa)
                       :lammonlahde                 (get-text rakennus :polttoaine)}
            :varusteet                              (-> (cr/all-of rakennus :varusteet)
                                                        (dissoc :uima-altaita) ; key :uima-altaita has been removed from lupapiste
                                                        (merge {:liitettyJatevesijarjestelmaanKytkin (get-text rakennus :liitettyJatevesijarjestelmaanKytkin)}))}
           util/not-empty-or-nil?

           :rakennuksenOmistajat (->> (remove empty? (concat (select rakennus [:omistaja])
                                                           (select rakennus [:omistajatieto :Omistaja])))
                                      (map ->rakennuksen-omistaja))
           :huoneistot (->> (remove empty? (concat (select rakennus [:valmisHuoneisto])
                                                   (select rakennus [:asuinhuoneistot :> :huoneisto])))
                            (map (fn [huoneisto]
                                   {:huoneistonumero (get-text huoneisto :huoneistonumero)
                                    :jakokirjain     (get-text huoneisto :jakokirjain)
                                    :porras          (get-text huoneisto :porras)
                                    :huoneistoTyyppi (get-text huoneisto :huoneistonTyyppi)
                                    :huoneistoala    (ss/replace (get-text huoneisto :huoneistoala) "." ",")
                                    :huoneluku       (get-text huoneisto :huoneluku)
                                    :keittionTyyppi  (get-text huoneisto :keittionTyyppi)
                                    :WCKytkin                (get-text huoneisto :WCKytkin)
                                    :ammeTaiSuihkuKytkin     (get-text huoneisto :ammeTaiSuihkuKytkin)
                                    :lamminvesiKytkin        (get-text huoneisto :lamminvesiKytkin)
                                    :parvekeTaiTerassiKytkin (get-text huoneisto :parvekeTaiTerassiKytkin)
                                    :saunaKytkin             (get-text huoneisto :saunaKytkin)
                                    :muutostapa      (get-text huoneisto :muutostapa)}))
                            (sort-by (juxt :porras :huoneistonumero :jakokirjain)))))))))

(defn ->buildings [xml]
  (map ->rakennuksen-tiedot (-> xml cr/strip-xml-namespaces (select [:Rakennus]))))

(defn ->asian-tiedot [xml]
  (-> xml cr/strip-xml-namespaces (select [:asianTiedot :rakennusvalvontaasianKuvaus]) first (get-in [:content 0])))

(defn- ->rakennelman-tiedot [rakennelma]
  {:rakennusnro (ss/trim (get-text rakennelma :tunnus :rakennusnro))
   :rakennelman-kuvaus  (get-text rakennelma :kuvaus :kuvaus)})

(defn ->buildings-and-structures
  "Produces a building or structure for each (valid) construction operation in the application"
  [app-xml]
  (remove empty?
    (map (fn [xml]
           (let [rakennus (->rakennuksen-tiedot (-> xml (select [:Rakennus]) first))
                 rakennelma (->rakennelman-tiedot (-> xml (select [:Rakennelma]) first))]
             (when-not (empty? (or rakennus rakennelma))
               {:data (or rakennus rakennelma)
                :description (or (:rakennelman-kuvaus rakennelma)
                                 (-> (->buildings-summary xml) first :description))})))
          (-> app-xml cr/strip-xml-namespaces (select [:toimenpidetieto])))))

(defmethod permit/read-verdict-extras-xml :R [application xml] (->> (->buildings-summary xml) (building/building-updates application)))

(defn- parse-buildings
  "Convenience function for debugging KRYSP messages.
  Return buildings summary for the given file."
  [fname]
  (-> fname slurp (parse-string "utf-8") cr/strip-xml-namespaces ->buildings-summary))

(defn buildings-for-documents [xml]
  (->> (->buildings xml)
       (map (fn [b] {:data b}))))
