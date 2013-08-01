(ns lupapalvelu.document.rakennuslupa_canonical
  (:use [lupapalvelu.core :only [now]]
        [sade.strings]
        [clj-time.coerce :only [from-long]]
        [lupapalvelu.i18n :only [with-lang loc]])
  (:require [clojure.java.io :as io]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.string :as s]
            [clj-time.format :as timeformat]))

;; Macro to get values from
(defmacro value [m & path] `(-> ~m ~@path :value))

; Empty String will be rendered as empty XML element
(def empty-tag "")

; State of the content when it is send over KRYSP
; NOT the same as the state of the application!
(def toimituksenTiedot-tila "keskener\u00e4inen")

(def application-state-to-krysp-state
  {:draft "uusi lupa, ei k\u00e4sittelyss\u00e4"
   :open "vireill\u00e4"
   :sent "vireill\u00e4"
   :submitted "vireill\u00e4"
   :complement-needed "vireill\u00e4"})

(defn to-xml-date [timestamp]
  (let [d (from-long timestamp)]
    (timeformat/unparse (timeformat/formatter "YYYY-MM-dd") d)))

(defn to-xml-datetime [timestamp]
  (let [d (from-long timestamp)]
    (timeformat/unparse (timeformat/formatter "YYYY-MM-dd'T'HH:mm:ss") d)))

(defn to-xml-datetime-from-string [date-as-string]
  (let [d (timeformat/parse-local-date (timeformat/formatter "dd.MM.YYYY" ) date-as-string)]
    (timeformat/unparse-local-date (timeformat/formatter "YYYY-MM-dd") d)))

(defn by-type [documents]
  (group-by #(keyword (get-in % [:schema :info :name])) documents))

(defn- get-simple-osoite [osoite]
  {:osoitenimi {:teksti (-> osoite :katu :value)}
   :postitoimipaikannimi (-> osoite :postitoimipaikannimi :value)
   :postinumero (-> osoite :postinumero :value)})

(defn- get-yhteystiedot-data [yhteystiedot]
  {:sahkopostiosoite (-> yhteystiedot :email :value)
  :puhelin (-> yhteystiedot :puhelin :value)})

(defn- get-simple-yritys [yritys]
  {:nimi (-> yritys :yritysnimi :value)
   :liikeJaYhteisotunnus (-> yritys :liikeJaYhteisoTunnus :value)})

(defn- get-yritys-data [yritys]
  (let [yhteystiedot (get-in yritys [:yhteyshenkilo :yhteystiedot])]
    (merge (get-simple-yritys yritys)
           {:postiosoite (get-simple-osoite (:osoite yritys))
            :puhelin (-> yhteystiedot :puhelin :value)
            :sahkopostiosoite (-> yhteystiedot :email :value)})))

(defn- get-name [henkilotiedot]
  {:nimi {:etunimi (-> henkilotiedot :etunimi :value)
          :sukunimi (-> henkilotiedot :sukunimi :value)}})

(defn- get-henkilo-data [henkilo]
  (let [henkilotiedot (:henkilotiedot henkilo)
        yhteystiedot (:yhteystiedot henkilo)]
    (merge (get-name henkilotiedot)
      {:henkilotunnus (-> henkilotiedot :hetu :value)
       :osoite (get-simple-osoite (:osoite henkilo))}
     (get-yhteystiedot-data yhteystiedot))))

(defn- get-yhteyshenkilo-data [henkilo]
  (let [henkilotiedot (:henkilotiedot henkilo)
        yhteystiedot (:yhteystiedot henkilo)]
    (merge (get-name henkilotiedot)
     (get-yhteystiedot-data yhteystiedot))))

(defn- get-handler [application]
  (if-let [handler (:authority application)]
    {:henkilo {:nimi {:etunimi  (:firstName handler)
                      :sukunimi (:lastName handler)}}}
    empty-tag))

(def kuntaRoolikoodit
  {:paasuunnittelija "p\u00e4\u00e4suunnittelija"
   :hakija  "Rakennusvalvonta-asian hakija"
   :maksaja "Rakennusvalvonta-asian laskun maksaja"})

(defn- get-kuntaRooliKoodi [party party-type]
  (if (contains? kuntaRoolikoodit party-type)
    (kuntaRoolikoodit party-type)
    (get-in party [:kuntaRoolikoodi :value] "ei tiedossa")))

(def kuntaRoolikoodi-to-vrkRooliKoodi
  {"Rakennusvalvonta-asian hakija"  "hakija"
   "Rakennusvalvonta-asian laskun maksaja"  "maksaja"
   "p\u00e4\u00e4suunnittelija"     "p\u00e4\u00e4suunnittelija"
   "GEO-suunnittelija"              "erityissuunnittelija"
   "LVI-suunnittelija" `            "erityissuunnittelija"
   "RAK-rakennesuunnittelija"       "erityissuunnittelija"
   "ARK-rakennussuunnittelija"      "rakennussuunnittelija"
   "ei tiedossa"                    "ei tiedossa"

   ; TODO mappings for the rest
   :rakennuspaikanomistaja          "rakennuspaikan omistaja"
   :rakennuksenomistaja             "rakennuksen omistaja"
   :lupapaatoksentoimittaminen      "lupap\u00e4\u00e4t\u00f6ksen toimittaminen"
   :naapuri                         "naapuri"
   :lisatietojenantaja              "lis\u00e4tietojen antaja"
   :tyonjohtaja                     "ty\u00f6njohtaja"
   :muu                             "muu osapuoli"})

(defn- get-roolikoodit [kuntaRoolikoodi]
  {:kuntaRooliKoodi kuntaRoolikoodi ; Note the upper case 'Koodi'
   :VRKrooliKoodi (kuntaRoolikoodi-to-vrkRooliKoodi kuntaRoolikoodi)})

(defn- muu-select-map
  "Palauttaa mapin jossa muu-key ja muu value jos muu valuen annettu.
   Jos ei paluttaa mapin jossa sel-key ja sel value annettu.
   Jos ei niin palauttaa nil"
  [muu-key muu-val sel-key sel-val]
  (if (s/blank? muu-val)
    (when sel-val
      {sel-key sel-val})
    {muu-key muu-val}))

(defn- get-osapuoli-data [osapuoli party-type]
  (let [henkilo        (:henkilo osapuoli)
        kuntaRoolicode (get-kuntaRooliKoodi osapuoli party-type)
        omistajalaji   (muu-select-map :muu
                         (-> osapuoli :muu-omistajalaji :value)
                         :omistajalaji
                         (-> osapuoli :omistajalaji :value))
        role-codes     {:VRKrooliKoodi (kuntaRoolikoodi-to-vrkRooliKoodi kuntaRoolicode)
                        :kuntaRooliKoodi kuntaRoolicode
                        :turvakieltoKytkin (true? (-> henkilo :turvakieltoKytkin :value))}
        codes          (if omistajalaji
                         (merge role-codes {:omistajalaji omistajalaji})
                         role-codes)]
    (if (= (-> osapuoli :_selected :value) "yritys")
      (merge codes
             {:yritys  (get-yritys-data (:yritys osapuoli))}
             {:henkilo (get-yhteyshenkilo-data (get-in osapuoli [:yritys :yhteyshenkilo]))})
      (merge codes {:henkilo (get-henkilo-data henkilo)}))))

(defn- get-suunnittelija-data [suunnittelija party-type]
  (let [kuntaRoolikoodi (get-kuntaRooliKoodi suunnittelija party-type)
        codes {:suunnittelijaRoolikoodi kuntaRoolikoodi ; Note the lower case 'koodi'
               :VRKrooliKoodi (kuntaRoolikoodi-to-vrkRooliKoodi kuntaRoolikoodi)}
        patevyys (:patevyys suunnittelija)
        henkilo (merge (get-name (:henkilotiedot suunnittelija))
                       {:osoite (get-simple-osoite (:osoite suunnittelija))}
                       (get-yhteystiedot-data (:yhteystiedot suunnittelija)))
        base-data (merge codes {:koulutus (-> patevyys :koulutus :value)
                                :patevyysvaatimusluokka (-> patevyys :patevyysluokka :value)
                                :henkilo henkilo})]
    (if (contains? suunnittelija :yritys)
      (assoc base-data :yritys (assoc
                                 (get-simple-yritys (:yritys suunnittelija))
                                 :postiosoite (get-simple-osoite (:osoite suunnittelija))
                                 ))
      base-data)))

(defn- get-parties-by-type [documents tag-name party-type doc-transformer]
  (for [doc (documents party-type)
        :let [osapuoli (:data doc)]
        :when (seq osapuoli)]
    {tag-name (doc-transformer osapuoli party-type)}))

(defn get-parties [documents]
  (into
    (get-parties-by-type documents :Osapuoli :hakija get-osapuoli-data)
    (get-parties-by-type documents :Osapuoli :maksaja get-osapuoli-data)))

(defn- get-designers [documents]
  (into
    (get-parties-by-type documents :Suunnittelija :paasuunnittelija get-suunnittelija-data)
    (get-parties-by-type documents :Suunnittelija :suunnittelija get-suunnittelija-data)))

(def state-timestamps
  {:draft :created
   :open :opened
   :complement-needed :opened
   ; Application state in KRYSP will be "vireill\u00e4" -> use :opened date
   :submitted :opened})

(defn- get-state [application]
  (let [state (keyword (:state application))]
    {:Tilamuutos
     {:tila (application-state-to-krysp-state state)
      :pvm (to-xml-date ((state-timestamps state) application))
      :kasittelija (get-handler application)}}))

(defn- get-huoneisto-data [huoneistot]
  (for [huoneisto (vals huoneistot)
        :let [tyyppi (:huoneistonTyyppi huoneisto)
              varusteet (:varusteet huoneisto)
              huoneistonumero (-> huoneisto :huoneistoTunnus :huoneistonumero :value)
              huoneistoPorras (-> huoneisto :huoneistoTunnus :porras :value)
              jakokirjain (-> huoneisto :huoneistoTunnus :jakokirjain :value)]
        :when (seq huoneisto)]
    (merge {:huoneluku (-> tyyppi :huoneluku :value)
            :keittionTyyppi (-> huoneisto :keittionTyyppi :value)
            :huoneistoala (-> tyyppi :huoneistoala :value)
            :varusteet {:WCKytkin (true? (-> varusteet :WCKytkin :value))
                        :ammeTaiSuihkuKytkin (true? (-> varusteet :ammeTaiSuihkuKytkin :value))
                        :saunaKytkin (true? (-> varusteet :saunaKytkin :value))
                        :parvekeTaiTerassiKytkin (true? (-> varusteet  :parvekeTaiTerassiKytkin :value))
                        :lamminvesiKytkin (true? (-> varusteet :lamminvesiKytkin :value))}
            :huoneistonTyyppi (-> tyyppi :huoneistoTyyppi :value)}
           (when (numeric? huoneistonumero)
             {:huoneistotunnus
              (merge {:huoneistonumero (format "%03d" (read-string (remove-leading-zeros huoneistonumero)))}
                     (when (not-empty huoneistoPorras) {:porras (clojure.string/upper-case huoneistoPorras)})
                     (when (not-empty jakokirjain) {:jakokirjain (clojure.string/lower-case jakokirjain)}))}))))

(defn- get-rakennuksen-omistaja [omistaja]
  {:Omistaja (merge (get-osapuoli-data omistaja :rakennuksenomistaja))})

(defn- get-rakennus [toimenpide application {id :id created :created}]
  (let [{kuvaus   :toimenpiteenKuvaus
         kaytto   :kaytto
         mitat    :mitat
         rakenne  :rakenne
         lammitys :lammitys
         luokitus :luokitus
         huoneistot :huoneistot} toimenpide
        kantava-rakennus-aine-map (muu-select-map :muuRakennusaine (-> rakenne :muuRakennusaine :value)
                                                  :rakennusaine (-> rakenne :kantavaRakennusaine :value))
        lammonlahde-map (muu-select-map :muu
                                        (-> lammitys :muu-lammonlahde :value)
                                        :polttoaine
                                        (if (= "kiviihiili koksi tms" (-> lammitys :lammonlahde :value))
                                          (str (-> lammitys :lammonlahde :value) ".")
                                          (-> lammitys :lammonlahde :value)))
        julkisivu-map (muu-select-map :muuMateriaali (-> rakenne :muuMateriaali :value)
                                      :julkisivumateriaali (-> rakenne :julkisivu :value))
        lammitystapa (-> lammitys :lammitystapa :value)
        huoneistot {:huoneisto (get-huoneisto-data huoneistot)}]
    {:yksilointitieto id
     :alkuHetki (to-xml-datetime  created)
     :sijaintitieto {:Sijainti {:tyhja empty-tag}}
     :rakentajatyyppi (-> kaytto :rakentajaTyyppi :value)
     :omistajatieto (for [m (vals (:rakennuksenOmistajat toimenpide))] (get-rakennuksen-omistaja m))
     :rakennuksenTiedot (merge {:kayttotarkoitus (-> kaytto :kayttotarkoitus :value)
                                :tilavuus (-> mitat :tilavuus :value)
                                :kokonaisala (-> mitat :kokonaisala :value)
                                :kellarinpinta-ala (-> mitat :kellarinpinta-ala :value)
                                ;:BIM empty-tag
                                :kerrosluku (-> mitat :kerrosluku :value)
                                :kerrosala (-> mitat :kerrosala :value)
                                :rakentamistapa (-> rakenne :rakentamistapa :value)
                                :verkostoliittymat {:sahkoKytkin (true? (-> toimenpide :verkostoliittymat :sahkoKytkin :value))
                                                    :maakaasuKytkin (true? (-> toimenpide :verkostoliittymat :maakaasuKytkin :value))
                                                    :viemariKytkin (true? (-> toimenpide :verkostoliittymat :viemariKytkin :value))
                                                    :vesijohtoKytkin (true? (-> toimenpide :verkostoliittymat :vesijohtoKytkin :value))
                                                    :kaapeliKytkin (true? (-> toimenpide :verkostoliittymat :kaapeliKytkin :value))}
                                :energialuokka (-> luokitus :energialuokka :value)
                                :energiatehokkuusluku (-> luokitus :energiatehokkuusluku :value)
                                :energiatehokkuusluvunYksikko (-> luokitus :energiatehokkuusluvunYksikko :value)
                                :paloluokka (-> luokitus :paloluokka :value)
                                :lammitystapa (if (= lammitystapa "suorasahk\u00f6")
                                                "suora s\u00e4hk\u00f6"
                                                lammitystapa)
                                :varusteet {:sahkoKytkin (true? (-> toimenpide :varusteet :sahkoKytkin :value))
                                            :kaasuKytkin (true? (-> toimenpide :varusteet :kaasuKytkin :value))
                                            :viemariKytkin (true? (-> toimenpide :varusteet :sahkoKytkin :value))
                                            :vesijohtoKytkin (true? (-> toimenpide :varusteet :vesijohtoKytkin :value))
                                            :lamminvesiKytkin (true? (-> toimenpide :varusteet :lamminvesiKytkin :value))
                                            :aurinkopaneeliKytkin (true? (-> toimenpide :varusteet :aurinkopaneeliKytkin :value))
                                            :hissiKytkin (true? (-> toimenpide :varusteet :hissiKytkin :value))
                                            :koneellinenilmastointiKytkin (true? (-> toimenpide :varusteet :koneellinenilmastointiKytkin :value))
                                            :saunoja (-> toimenpide :varusteet :saunoja :value)
                                            :vaestonsuoja (-> toimenpide :varusteet :vaestonsuoja :value)}}
                               (when (-> toimenpide :rakennusnro :value)
                                   {:rakennustunnus {:jarjestysnumero (-> toimenpide :rakennusnro :value)
                                                    :kiinttun (:propertyId application)}})
                               (when kantava-rakennus-aine-map {:kantavaRakennusaine kantava-rakennus-aine-map})
                               (when lammonlahde-map {:lammonlahde lammonlahde-map})
                               (when julkisivu-map {:julkisivu julkisivu-map})
                               (when huoneistot (if (not-empty (:huoneisto huoneistot))
                                                  {:asuinhuoneistot huoneistot})
                                 ))}))

(defn- get-rakennus-data [toimenpide application doc]
  {:Rakennus (get-rakennus toimenpide application doc)})

(defn- get-rakenelma-data [application action]
  nil)

(defn- get-toimenpiteen-kuvaus [doc]
  ;Uses fi as default since krysp uses finnish in enumeration values
  {:kuvaus (with-lang "fi" (loc (str "operations." (-> doc :schema :info :op :name))))})

(defn get-uusi-toimenpide [doc application]
  (let [toimenpide (:data doc)]
    {:Toimenpide {:uusi (get-toimenpiteen-kuvaus doc)
                  :rakennustieto (get-rakennus-data toimenpide application doc)}
     :created (:created doc)}))

(defn- get-rakennuksen-muuttaminen-toimenpide [rakennuksen-muuttaminen-doc application]
  (let [toimenpide (:data rakennuksen-muuttaminen-doc)]
    {:Toimenpide {:muuMuutosTyo (conj (get-toimenpiteen-kuvaus rakennuksen-muuttaminen-doc)
                                      {:perusparannusKytkin (-> rakennuksen-muuttaminen-doc :data :perusparannuskytkin :value)}
                                      {:muutostyonLaji (-> rakennuksen-muuttaminen-doc :data :muutostyolaji :value)})
                  :rakennustieto (get-rakennus-data toimenpide application rakennuksen-muuttaminen-doc)}
     :created (:created rakennuksen-muuttaminen-doc)}))

(defn- get-rakennuksen-laajentaminen-toimenpide [laajentaminen-doc application]
  (let [toimenpide (:data laajentaminen-doc)
        mitat (-> toimenpide :laajennuksen-tiedot :mitat )]
    {:Toimenpide {:laajennus (conj (get-toimenpiteen-kuvaus laajentaminen-doc)
                                   {:perusparannusKytkin (-> laajentaminen-doc :data :laajennuksen-tiedot :perusparannuskytkin :value)}
                                   {:laajennuksentiedot {:tilavuus (-> mitat :tilavuus :value)
                                                         :kerrosala (-> mitat :tilavuus :value)
                                                         :kokonaisala (-> mitat :tilavuus :value)
                                                         :huoneistoala (for [huoneistoala (vals (:huoneistoala mitat))]
                                                                         {:pintaAla (-> huoneistoala :pintaAla :value)
                                                                          :kayttotarkoitusKoodi (-> huoneistoala :kayttotarkoitusKoodi :value)})}})
                  :rakennustieto (get-rakennus-data toimenpide application laajentaminen-doc)}
     :created (:created laajentaminen-doc)}))

(defn- get-purku-toimenpide [purku-doc application]
  (let [toimenpide (:data purku-doc)]
    {:Toimenpide {:purkaminen (conj (get-toimenpiteen-kuvaus purku-doc)
                                   {:purkamisenSyy (-> toimenpide :poistumanSyy :value)}
                                   {:poistumaPvm (to-xml-datetime-from-string (-> toimenpide :poistumanAjankohta :value))})
                  :rakennustieto (get-rakennus-data toimenpide application purku-doc)}
     :created (:created purku-doc)}))

(defn get-kaupunkikuvatoimenpide [kaupunkikuvatoimenpide-doc application]
  (let [toimenpide (:data kaupunkikuvatoimenpide-doc)]
    {:Toimenpide {:kaupunkikuvaToimenpide (get-toimenpiteen-kuvaus kaupunkikuvatoimenpide-doc)
                  :rakennelmatieto {:Rakennelma {:yksilointitieto (:id kaupunkikuvatoimenpide-doc)
                                                 :alkuHetki (to-xml-datetime (:created kaupunkikuvatoimenpide-doc))
                                                 :sijaintitieto {:Sijainti {:tyhja empty-tag}}
                                                 :kokonaisala (-> toimenpide :kokonaisala :value)
                                                 :kuvaus {:kuvaus (-> toimenpide :kuvaus :value)}}}}
     :created (:created kaupunkikuvatoimenpide-doc)}))


(defn- get-operations [documents application]
  (let [toimenpiteet (filter not-empty (concat (map #(get-uusi-toimenpide % application) (:uusiRakennus documents))
                                               (map #(get-rakennuksen-muuttaminen-toimenpide % application) (:rakennuksen-muuttaminen documents))
                                               (map #(get-rakennuksen-laajentaminen-toimenpide % application) (:rakennuksen-laajentaminen documents))
                                               (map #(get-purku-toimenpide % application) (:purku documents))
                                               (map #(get-kaupunkikuvatoimenpide % application) (:kaupunkikuvatoimenpide documents))))]
    (not-empty (sort-by :created toimenpiteet))))

(defn- get-lisatiedot [documents lang]
  (let [lisatiedot (:data (first documents))]
    {:Lisatiedot {:suoramarkkinointikieltoKytkin (true? (-> lisatiedot :suoramarkkinointikielto :value))
                  :asioimiskieli (if (= lang "se")
                                   "ruotsi"
                                   "suomi")}}))

(defn- get-asian-tiedot [documents maisematyo_documents]
  (let [asian-tiedot (:data (first documents))
        maisematyo_kuvaukset (for [maismatyo_doc maisematyo_documents]
                               (str "\n\n"  (:kuvaus (get-toimenpiteen-kuvaus maismatyo_doc)) ":" (-> maismatyo_doc :data :kuvaus :value )))]
    {:Asiantiedot {:vahainenPoikkeaminen (or (-> asian-tiedot :poikkeamat :value) empty-tag)
                   :rakennusvalvontaasianKuvaus (str (-> asian-tiedot :kuvaus :value) (apply str maisematyo_kuvaukset))}}))

(defn- change-value-to-when [value to_compare new_val]
  (if (= value to_compare) new_val
    value))

(defn- get-bulding-places [documents application]
  (for [doc (:rakennuspaikka documents)
        :let [rakennuspaikka (:data doc)
              kiinteisto (:kiinteisto rakennuspaikka)
              id (:id doc)
              created (:created doc)]]
    {:Rakennuspaikka
     {:yksilointitieto id
      :alkuHetki (to-xml-datetime created)
      :kaavanaste (change-value-to-when (-> rakennuspaikka :kaavanaste :value) "eiKaavaa" "ei kaavaa")
      :rakennuspaikanKiinteistotieto {:RakennuspaikanKiinteisto
                                      {:kokotilaKytkin (s/blank? (-> kiinteisto :maaraalaTunnus :value))
                                       :hallintaperuste (-> rakennuspaikka :hallintaperuste :value)
                                       :kiinteistotieto {:Kiinteisto {:tilannimi (-> kiinteisto :tilanNimi :value)
                                                                                    :kiinteistotunnus (:propertyId application)
                                                                                    :maaraAlaTunnus (-> kiinteisto :maaraalaTunnus :value)}}}}}}))

(defn- get-kayttotapaus [documents toimenpiteet]
  (if (and (contains? documents :maisematyo) (empty? toimenpiteet))
      "Uusi maisematy\u00f6hakemus"
      "Uusi hakemus"))

(def puolto-mapping {:condition "ehdoilla"
                     :no "ei puolla"
                     :yes "puoltaa"})

(defn- get-statement [statement]
  {:Lausunto {:id (:id statement)
              :pyydetty {:viranomainen (get-in statement [:person :text])
                         :pyyntoPvm (to-xml-date (:requested statement))}
              :lausunto {:viranomainen (get-in statement [:person :name])
                         :lausuntoPvm (to-xml-date (:given statement))
                         :lausunto {:lausunto (:text statement)}
                         :puoltotieto (if (nil? (:status statement))
                                        {:puolto "ei tiedossa"}
                                        {:puolto ((keyword (:status statement)) puolto-mapping)})}}})

(defn- get-statements [statements]
  ;Returing vector because this element to be Associative
  (vec (map get-statement statements)))

(defn application-to-canonical
  "Transforms application mongodb-document to canonical model."
  [application lang]
  (let [documents (by-type (:documents application))
        toimenpiteet (get-operations documents application)
        canonical {:Rakennusvalvonta
                   {:toimituksenTiedot
                    {:aineistonnimi (:title application)
                     :aineistotoimittaja "lupapiste@solita.fi"
                     :tila toimituksenTiedot-tila
                     :toimitusPvm (to-xml-date (now))
                     :kuntakoodi (:municipality application)
                     :kielitieto ""}
                    :rakennusvalvontaAsiatieto
                    {:RakennusvalvontaAsia
                     {:kasittelynTilatieto (get-state application)
                      :luvanTunnisteTiedot
                      {:LupaTunnus
                       {:muuTunnustieto {:MuuTunnus {:tunnus (:id application)
                                                     :sovellus "Lupapiste"}}}}
                      :osapuolettieto
                      {:Osapuolet
                       {:osapuolitieto (get-parties documents)
                        :suunnittelijatieto (get-designers documents)}}
                      :rakennuspaikkatieto (get-bulding-places documents application)
                      :lausuntotieto (get-statements (:statements application))
                      :lisatiedot (get-lisatiedot (:lisatiedot documents) lang)
                      :kayttotapaus (get-kayttotapaus documents toimenpiteet)
                      :asianTiedot (get-asian-tiedot (:hankkeen-kuvaus documents) (:maisematyo documents))}
                     }}}]
    (assoc-in canonical [:Rakennusvalvonta :rakennusvalvontaAsiatieto :RakennusvalvontaAsia :toimenpidetieto] toimenpiteet)))
