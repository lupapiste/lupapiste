(ns lupapalvelu.backing-system.krysp.building-reader
  (:require [lupapalvelu.backing-system.krysp.common-reader :as common]
            [lupapalvelu.building :as building]
            [lupapalvelu.building-types :as building-types]
            [lupapalvelu.document.schemas :as schema]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.permit :as permit]
            [net.cgrand.enlive-html :as enlive]
            [sade.common-reader :as cr]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as v]
            [sade.xml :refer [get-text select select1 under has-text xml->edn parse-string]]))

(def ^:private rakennusluokka-map
  "Canonicalization support map. For example, '0939' -> '0939 muut kaivannaistoiminnan
  rakennukset'"
  (->> building-types/rakennuksen-rakennusluokka
       (map (fn [{s :name}]
              [(first (ss/split s #"\s+" 2)) s]))
       (into {})))

(defn fix-rakennusluokka
  "Some backing systems omit the textual part from the rakennusluokka information (e.g.,
  '1212' vs. '1212 kylmä- ja pakastevarastot'). The full format is the canonical version,
  since it is required by the KuntaGML schema. Returns either the canonical format or the
  trimmed argument (otherwise unchanged), if it cannot be canonicalized."
  [s]
  (when-let [s (ss/trim s)]
    (get rakennusluokka-map s s)))

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

(defn- ->list "a as list or nil. a -> [a], [b] -> [b]" [a] (when a (-> a list flatten)))

(defn read-huoneisto [rakennus]
  (->> (remove empty? (concat (select rakennus [:valmisHuoneisto])
                              (select rakennus [:asuinhuoneistot :> :huoneisto])))
       (map (fn [huoneisto]
              {:huoneistonumero (get-text huoneisto :huoneistotunnus :huoneistonumero)
               :jakokirjain     (get-text huoneisto :huoneistotunnus :jakokirjain)
               :porras          (get-text huoneisto :huoneistotunnus :porras)
               :huoneistoTyyppi (get-text huoneisto :huoneistonTyyppi)
               :huoneistoala    (ss/replace (get-text huoneisto :huoneistoala) "." ",")
               :huoneluku       (get-text huoneisto :huoneluku)
               :keittionTyyppi  (get-text huoneisto :keittionTyyppi)
               :WCKytkin                (get-text huoneisto :WCKytkin)
               :ammeTaiSuihkuKytkin     (get-text huoneisto :ammeTaiSuihkuKytkin)
               :lamminvesiKytkin        (get-text huoneisto :lamminvesiKytkin)
               :parvekeTaiTerassiKytkin (get-text huoneisto :parvekeTaiTerassiKytkin)
               :saunaKytkin             (get-text huoneisto :saunaKytkin)
               :muutostapa              (get-text huoneisto :muutostapa)
               :pysyvaHuoneistotunnus   (get-text huoneisto :valtakunnallinenHuoneistotunnus)}))
       (sort-by (juxt :porras :huoneistonumero :jakokirjain))))

(defn- ->building-ids
  "Buiding or structure information. Nil if `loppuHetki` is before the current time."
  [id-container xml-no-ns]
  (when-not (some-> (get-text xml-no-ns :loppuHetki)
                    date/zoned-date-time
                    (.isBefore (date/now)))
    (let [national-id    (pysyva-rakennustunnus (get-text xml-no-ns id-container :valtakunnallinenNumero))
          local-short-id (-> (get-text xml-no-ns id-container :rakennusnro)
                             ss/trim (#(when-not (ss/blank? %) %)))
          local-id       (-> (get-text xml-no-ns id-container :kunnanSisainenPysyvaRakennusnumero)
                             ss/trim (#(when-not (ss/blank? %) %)))
          edn            (-> xml-no-ns (select [id-container]) first xml->edn id-container)
          building-index (get-text xml-no-ns id-container :jarjestysnumero)]
     (merge
       {:propertyId    (get-text xml-no-ns id-container :kiinttun)
        :buildingId    (first (remove ss/blank? [national-id local-short-id]))
        :nationalId    national-id
        :localId       local-id
        :localShortId  local-short-id
        :index         building-index
        :usage         (or (get-text xml-no-ns :kayttotarkoitus) "")
        :building-type (fix-rakennusluokka (get-text xml-no-ns :rakennusluokka))
        :area          (get-text xml-no-ns :kokonaisala)
        :created       (some->> (get-text xml-no-ns :alkuHetki)
                                (date/zoned-date-time)
                                (.getYear)
                                str) ;; String for legacy reasons
        :operationId   (some (fn [{{:keys [tunnus sovellus]} :MuuTunnus}]
                               (when (#{"toimenpideId" "Lupapiste" "Lupapistetunnus"} sovellus)
                                 tunnus))
                             (->list (:muuTunnustieto edn)))
        :description   (or (:rakennuksenSelite edn) building-index)
        :apartments    (read-huoneisto xml-no-ns)}
       (-> (select1 xml-no-ns :sijaintitieto :Sijainti)
           common/point->location-map)))))

(defn- ->Rakennus
  "Returns a list of Rakennus elements that exist according to `Olotila` and `Kaytossaolo` elements."
  [xml-no-ns]
  (let [under-valmis-rakennus   (set (select xml-no-ns [:ValmisRakennus :Rakennus]))
        outside-valmis-rakennus (remove under-valmis-rakennus (select xml-no-ns :Rakennus))]
    (->> (select xml-no-ns [:ValmisRakennus])
         (remove #(select1 % [:olotila (enlive/text-pred #{"purettu"})]))
         (map #(select1 % [:Rakennus]))
         (concat outside-valmis-rakennus))))


(defn- property-location-fallback
  "Some 'buildings' like large sewer systems don't have a location of their own
  See LPK-5463 and TT-18896"
  [property-location {:keys [location-wgs84 location] :as building-summary}]
  (if (or (nil? location-wgs84)
          (nil? location))
    (merge building-summary property-location)
    building-summary))


(defn ->buildings-summary [xml]
  (let [xml-no-ns         (cr/strip-xml-namespaces xml)
        building-elements (->Rakennus xml-no-ns)
        property-location (-> (select xml-no-ns [:Rakennuspaikka :Sijainti])
                              first
                              common/point->location-map)]
    (->> (concat
           (map (partial ->building-ids :rakennustunnus) building-elements)
           (map (partial ->building-ids :tunnus) (select xml-no-ns [:Rakennelma])))
         (remove nil?)
         distinct
         (map (partial property-location-fallback property-location)))))


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

(defn ->rakennuksen-omistaja
  "Owner data. If the owner is a person, their information is only included if
  `include-personal-owner-info?` is true."
  ([omistaja {:keys [include-personal-owner-info?]}]
   (cond
     (seq (select omistaja [:yritys]))
     (merge (common/->yritys omistaja) {:omistajalaji     (get-text omistaja :omistajalaji :omistajalaji)
                                        :muu-omistajalaji (get-text omistaja :omistajalaji :muu)})

     (seq (select omistaja [:henkilo]))
     (merge (common/->henkilo (if include-personal-owner-info? omistaja {}))
            {:omistajalaji     (get-text omistaja :omistajalaji :omistajalaji)
             :muu-omistajalaji (get-text omistaja :omistajalaji :muu)})

     :else (->rakennuksen-omistaja-legacy-version omistaja)))
  ([omistaja]
   (->rakennuksen-omistaja omistaja false)))

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
  ([rakennus]
   (->rakennuksen-tiedot rakennus nil))
  ([rakennus options]
   (when rakennus
     (util/deep-merge
       (->
         {:body schema/rakennuksen-muuttaminen}
         (tools/create-unwrapped-data tools/default-values)
         ; Dissoc values that are not read and the default one apartment
         (dissoc :buildingId :muutostyolaji :perusparannuskytkin :rakennustietojaEimuutetaKytkin
                 :huoneistot))
       (polished
         (util/assoc-when-pred
           (let [voimassa-pvm (date/zoned-date-time (get-text rakennus :tilapainenRakennusvoimassaPvm))]
             {:muutostyolaji                      ...notimplemented...
              :valtakunnallinenNumero             (pysyva-rakennustunnus (get-text rakennus :rakennustunnus :valtakunnallinenNumero))
              :kunnanSisainenPysyvaRakennusnumero (get-text rakennus :rakennustunnus :kunnanSisainenPysyvaRakennusnumero)
              :rakennusnro                        (ss/trim (get-text rakennus :rakennustunnus :rakennusnro))
              :manuaalinen_rakennusnro            ""
              :jarjestysnumero                    (get-text rakennus :rakennustunnus :jarjestysnumero)
              :kiinttun                           (get-text rakennus :rakennustunnus :kiinttun)
              :verkostoliittymat                  (cr/all-of rakennus [:verkostoliittymat])

              :osoite                             {:kunta                (get-text rakennus :osoite :kunta)
                                                   :lahiosoite           (get-text rakennus :osoite :osoitenimi :teksti)
                                                   :osoitenumero         (get-text rakennus :osoite :osoitenumero)
                                                   :osoitenumero2        (get-text rakennus :osoite :osoitenumero2)
                                                   :jakokirjain          (get-text rakennus :osoite :jakokirjain)
                                                   :jakokirjain2         (get-text rakennus :osoite :jakokirjain2)
                                                   :porras               (get-text rakennus :osoite :porras)
                                                   :huoneisto            (get-text rakennus :osoite :huoneisto)
                                                   :postinumero          (get-text rakennus :osoite :postinumero)
                                                   :postitoimipaikannimi (get-text rakennus :osoite :postitoimipaikannimi)}
              :kaytto
              {:kayttotarkoitus               (get-text rakennus :kayttotarkoitus)
               :rakentajaTyyppi               (get-text rakennus :rakentajatyyppi)
               :rakennusluokka                (fix-rakennusluokka (get-text rakennus :rakennusluokka))
               :tilapainenRakennusKytkin      (or (get-text rakennus :tilapainenRakennusKytkin) ;; Is set to true if tilapainenRakennusvoimassaPvm has date value
                                                  (boolean voimassa-pvm))
               :tilapainenRakennusvoimassaPvm (date/finnish-date voimassa-pvm :zero-pad)}
              :luokitus                           {:energialuokka                (get-text rakennus :energialuokka)
                                                   :energiatehokkuusluku         (get-text rakennus :energiatehokkuusluku)
                                                   :energiatehokkuusluvunYksikko (get-text rakennus :energiatehokkuusluvunYksikko)
                                                   :paloluokka                   (get-text rakennus :paloluokka)}
              :mitat                              {:kellarinpinta-ala              (get-non-zero-integer-text rakennus :kellarinpinta-ala)
                                                   :kerrosala                      (get-non-zero-integer-text rakennus :kerrosala)
                                                   :rakennusoikeudellinenKerrosala (get-non-zero-integer-text rakennus :rakennusoikeudellinenKerrosala)
                                                   :kerrosluku                     (get-text rakennus :kerrosluku)
                                                   :kokonaisala                    (get-non-zero-integer-text rakennus :kokonaisala)
                                                   :tilavuus                       (get-non-zero-integer-text rakennus :tilavuus)}
              :rakenne                            {:julkisivu           (or (get-text rakennus :julkisivumateriaali)
                                                                            "other") ;; If :julkisivumateriaali is empty, use 'other' so that docgen uses value in muuMateriaali
                                                   :kantavaRakennusaine (get-text rakennus :rakennusaine)
                                                   :rakentamistapa      (get-text rakennus :rakentamistapa)
                                                   :muuMateriaali       (get-text rakennus :muuMateriaali)}
              :lammitys                           {:lammitystapa (get-text rakennus :lammitystapa)
                                                   :lammonlahde  (get-text rakennus :polttoaine)}
              :varusteet                          (-> (cr/all-of rakennus :varusteet)
                                                      (dissoc :uima-altaita) ; key :uima-altaita has been removed from lupapiste
                                                      (merge {:liitettyJatevesijarjestelmaanKytkin (get-text rakennus :liitettyJatevesijarjestelmaanKytkin)
                                                              :kokoontumistilanHenkilomaara        ""}))})
           util/not-empty-or-nil?

           :rakennuksenOmistajat (->> (remove empty? (concat (select rakennus [:omistaja])
                                                             (select rakennus [:omistajatieto :Omistaja])))
                                      (map #(->rakennuksen-omistaja % options))
                                      distinct)
           :huoneistot (read-huoneisto rakennus)))))))

(defn ->rakennuksen-tiedot-by-id
  ([xml building-id options]
   (let [stripped (cr/strip-xml-namespaces xml)
         rakennus (or
                    (select1 stripped [:rakennustieto :> (under [:valtakunnallinenNumero (has-text building-id)])])
                    (select1 stripped [:rakennustieto :> (under [:rakennusnro (has-text building-id)])]))]
     (->rakennuksen-tiedot rakennus options)))
  ([xml building-id]
   (->rakennuksen-tiedot-by-id xml building-id nil)))

(defn ->building-document-data [xml]
  (map ->rakennuksen-tiedot (-> xml cr/strip-xml-namespaces (select [:Rakennus]))))

(defn ->asian-tiedot [xml]
  (let [xml-no-ns (cr/strip-xml-namespaces xml)
        elements (or (select xml-no-ns [:asianTiedot :rakennusvalvontaasianKuvaus])
                     (select xml-no-ns [:asianTiedot :poikkeamisasianKuvaus]))]
    (-> elements first (get-in [:content 0]))))

(defn ->rakennuspaikkatieto [xml]
  (-> xml
      cr/strip-xml-namespaces
      (select [:rakennusvalvontaAsiatieto :RakennusvalvontaAsia :rakennuspaikkatieto :Rakennuspaikka])
      ((partial map xml->edn))
      first))

(defn- ->rakennelman-tiedot [rakennelma]
  (let [voimassa-pvm (date/zoned-date-time (get-text rakennelma :tilapainenRakennelmavoimassaPvm))]
    {:rakennusnro                     (ss/trim (get-text rakennelma :tunnus :rakennusnro))
     :rakennelman-kuvaus              (get-text rakennelma :kuvaus :kuvaus)
     :kokoontumistilanHenkilomaara    ""
     :tilapainenRakennelmaKytkin      (or (get-text rakennelma :tilapainenRakennelmaKytkin)
                                          (boolean voimassa-pvm))
     :tilapainenRakennelmavoimassaPvm (date/finnish-date voimassa-pvm :zero-pad)}))

(defn ->kuntagml-toimenpide [xml]
  (let [toimenpide?  #(select1 xml [:Toimenpide %])
        [tag fields] (cond
                       (toimenpide? :uusi)                   [:uusi]
                       (toimenpide? :purkaminen)             [:purkaminen]
                       (toimenpide? :kaupunkikuvaToimenpide) [:kaupunkikuvaToimenpide]
                       (toimenpide? :laajennus)              [:laajennus [:BASIC]]

                       (toimenpide? :uudelleenrakentaminen)
                       [:uudelleenrakentaminen [:BASIC :WORK]]

                       (toimenpide? :muuMuutosTyo)
                       [:muuMuutosTyo [:BASIC :WORK :NO-CHANGE]])
        get-value    (fn [field] (get-text xml [:Toimenpide tag field]))
        get-boolean  (fn [field]
                       (let [v (get-value field)]
                         (when (some? v)
                           (= v "true"))))]
    (when tag
      (->> fields
           (map (fn [f]
                  (case f
                    :BASIC     [:perusparannuskytkin (get-boolean :perusparannusKytkin)]
                    :WORK      [:muutostyolaji (get-value :muutostyonLaji)]
                    :NO-CHANGE [:rakennustietojaEimuutetaKytkin
                                (get-boolean :rakennustietojaEimuutetaKytkin)])))
           (remove (comp nil? second))
           (cons [:toimenpide (name tag)])
           (into {})
           not-empty))))

(defn ->buildings-and-structures
  "Produces a building or structure for each (valid) construction operation in the application"
  ([app-xml {:keys [include-kuntagml-toimenpide?] :as options}]
   (remove empty?
           (map (fn [xml]
                  (let [rakennus   (->rakennuksen-tiedot (-> xml (select [:Rakennus]) first)
                                                         options)
                        rakennelma (->rakennelman-tiedot (-> xml (select [:Rakennelma]) first))]
                    (when-not (empty? (or rakennus rakennelma))
                      (util/assoc-when {:data        (or rakennus rakennelma)
                                        :description (or (:rakennelman-kuvaus rakennelma)
                                                         (-> (->buildings-summary xml)
                                                             first :description))}
                                       :kuntagml-toimenpide (when include-kuntagml-toimenpide?
                                                              (->kuntagml-toimenpide xml))
                             ))))
                (-> app-xml cr/strip-xml-namespaces (select [:toimenpidetieto])))))
  ([app-xml]
   (->buildings-and-structures app-xml nil)))

(defmethod permit/read-verdict-extras-xml :R [application xml & [timestamp]]
  (->> xml
       (->buildings-summary)
       (building/building-updates application timestamp)))

(defn- parse-buildings
  "Convenience function for debugging KRYSP messages.
  Return buildings summary for the given file."
  [fname]
  (-> fname slurp (parse-string "utf-8") cr/strip-xml-namespaces ->buildings-summary))

(defn buildings-for-documents [xml]
  (->> (->building-document-data xml)
       (map (fn [b] {:data b}))))
