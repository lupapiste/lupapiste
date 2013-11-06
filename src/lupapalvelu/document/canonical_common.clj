(ns lupapalvelu.document.canonical-common
  (:require [clj-time.format :as timeformat]
            [clj-time.coerce :as tc]
            [clojure.string :as s]
            [lupapalvelu.core :refer [now]]))


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

(def state-timestamps
  {:draft :created
   :open :opened
   :complement-needed :opened
   ; Application state in KRYSP will be "vireill\u00e4" -> use :opened date
   :submitted :opened
   ; Enables XML to be formed from sent applications
   :sent :opened})

(defn to-xml-date [timestamp]
  (let [dt (tc/from-long timestamp)]
    (if-not (nil? timestamp)
      (timeformat/unparse (timeformat/formatter "YYYY-MM-dd") dt))))

(defn to-xml-datetime [timestamp]
  (let [dt (tc/from-long timestamp)]
    (if-not (nil? timestamp)
      (timeformat/unparse (timeformat/formatter "YYYY-MM-dd'T'HH:mm:ss") dt))))

(defn to-xml-date-from-string [date-as-string]
  (let [d (timeformat/parse-local-date (timeformat/formatter "dd.MM.YYYY" ) date-as-string)]
    (timeformat/unparse-local-date (timeformat/formatter "YYYY-MM-dd") d)))

(defn to-xml-datetime-from-string [date-as-string]
  (let [d (timeformat/parse-local (timeformat/formatter "dd.MM.YYYY" ) date-as-string)]
    (timeformat/unparse-local-date (timeformat/formatter "YYYY-MM-dd'T'HH:mm:ss") d)))

(defn by-type [documents]
  (group-by (comp keyword :name :schema-info) documents))


(def ^:private puolto-mapping {:condition "ehdoilla"
                               :no "ei puolla"
                               :yes "puoltaa"})

(defn- get-statement [statement]
  (let [lausunto {:Lausunto
                  {:id (:id statement)
                   :viranomainen (get-in statement [:person :text])
                   :pyyntoPvm (to-xml-date (:requested statement))}}]
    (if-not (:status statement)
      lausunto
      (assoc-in lausunto [:Lausunto :lausuntotieto] {:Lausunto
                                                     {:viranomainen (get-in statement [:person :text])
                                                      :lausunto (:text statement)
                                                      :lausuntoPvm (to-xml-date (:given statement))
                                                      :puoltotieto
                                                      {:Puolto
                                                       {:puolto ((keyword (:status statement)) puolto-mapping)}}}}))))

(defn get-statements [statements]
  ;Returing vector because this element to be Associative
  (vec (map get-statement statements)))

(defn empty-strings-to-nil [v]
  (if (and (string? v) (s/blank? v)) nil v))

(defn muu-select-map
  "If 'sel-val' is \"other\" considers 'muu-key' and 'muu-val', else considers 'sel-key' and 'sel-val'.
   If value (either 'muu-val' or 'sel-val' is blank, return nil, else return map with
   considered key mapped to considered value."
  [muu-key muu-val sel-key sel-val]
  (let [muu (= "other" sel-val)
        k   (if muu muu-key sel-key)
        v   (if muu muu-val sel-val)]
    (when-not (s/blank? v)
      {k v})))

(def ya-operation-type-to-usage-description
  {:ya-kaivuulupa "kaivu- tai katuty\u00f6lupa"
   :ya-kayttolupa-tyomaasuojat-ja-muut-rakennelmat "ty\u00f6maasuojien ja muiden rakennelmien sijoittaminen yleiselle alueelle"
   :ya-kayttolupa-mainostus-ja-viitoitus "mainoslaitteiden ja opasteviittojen sijoittaminen"
   :ya-kayttolupa-muut-yleisten-alueiden-tilojen-kaytot "muut yleiselle alueelle kohdistuvat tilan k\u00e4yt\u00f6t"
   :ya-kayttolupa-messujen-ja-tapahtumien-alueiden-kaytot "erilaiset messujen ja tapahtumien aikaiset alueiden k\u00e4yt\u00f6t"
   :ya-kayttolupa-kadulta-tapahtuvat-nostot "kadulta tapahtuvat nostot"
   :ya-kayttolupa-kiinteistojen-tyot-jotka-varaavat-yleisen-alueen-tyomaaksi "kadulle pystytett\u00e4v\u00e4t rakennustelineet"
   :ya-kayttolupa-rakennustelineet-kadulla "kiinteist\u00f6n rakentamis- ja korjaamisty\u00f6t, joiden suorittamiseksi rajataan osa kadusta tai yleisest\u00e4 alueesta ty\u00f6maaksi (ei kaivut\u00f6it\u00e4)"
   :ya-kayttolupa-muu-kayttolupa "muu kaytt\u00f6lupa"
   :ya-sijoituslupa-pysyvien-maanalaisten-rakenteiden-sijoittaminen "pysyvien maanalaisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-pysyvien-maanpaallisten-rakenteiden-sijoittaminen "pysyvien maanp\u00e4\u00e4llisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-muu-sijoituslupa "muu sijoituslupa"})

(def ya-operation-type-to-schema-name-key
  {:ya-kaivuulupa :Tyolupa
   :ya-kayttolupa-tyomaasuojat-ja-muut-rakennelmat :Kayttolupa
   :ya-kayttolupa-mainostus-ja-viitoitus :Kayttolupa
   :ya-kayttolupa-muut-yleisten-alueiden-tilojen-kaytot :Kayttolupa
   :ya-kayttolupa-messujen-ja-tapahtumien-alueiden-kaytot :Kayttolupa
   :ya-kayttolupa-kadulta-tapahtuvat-nostot :Kayttolupa
   :ya-kayttolupa-kiinteistojen-tyot-jotka-varaavat-yleisen-alueen-tyomaaksi :Kayttolupa
   :ya-kayttolupa-rakennustelineet-kadulla :Kayttolupa
   :ya-kayttolupa-muu-kayttolupa :Kayttolupa
   :ya-sijoituslupa-pysyvien-maanalaisten-rakenteiden-sijoittaminen :Sijoituslupa
   :ya-sijoituslupa-pysyvien-maanpaallisten-rakenteiden-sijoittaminen :Sijoituslupa
   :ya-sijoituslupa-muu-sijoituslupa :Sijoituslupa})

(defn toimituksen-tiedot [application lang]
  {:aineistonnimi (:title application)
   :aineistotoimittaja "lupapiste@solita.fi"
   :tila toimituksenTiedot-tila
   :toimitusPvm (to-xml-date (now))
   :kuntakoodi (:municipality application)
   :kielitieto lang})

(defn- get-handler [application]
  (if-let [handler (:authority application)]
    {:henkilo {:nimi {:etunimi  (:firstName handler)
                      :sukunimi (:lastName handler)}}}
    empty-tag))


(defn get-state [application]
  (let [state (keyword (:state application))]
    {:Tilamuutos
     {:tila (application-state-to-krysp-state state)
      :pvm (to-xml-date ((state-timestamps state) application))
      :kasittelija (get-handler application)}}))


(defn lupatunnus [id]
  {:LupaTunnus
   {:muuTunnustieto {:MuuTunnus {:tunnus id
                                 :sovellus "Lupapiste"}}}})

(def kuntaRoolikoodi-to-vrkRooliKoodi
  {"Rakennusvalvonta-asian hakija"  "hakija"
   "Rakennusvalvonta-asian laskun maksaja"  "maksaja"
   "p\u00e4\u00e4suunnittelija"     "p\u00e4\u00e4suunnittelija"
   "GEO-suunnittelija"              "erityissuunnittelija"
   "LVI-suunnittelija" `            "erityissuunnittelija"
   "RAK-rakennesuunnittelija"       "erityissuunnittelija"
   "ARK-rakennussuunnittelija"      "rakennussuunnittelija"
   "KVV-ty\u00F6njohtaja"           "ty\u00f6njohtaja"
   "IV-ty\u00F6njohtaja"            "ty\u00f6njohtaja"
   "erityisalojen ty\u00F6njohtaja" "ty\u00f6njohtaja"
   "vastaava ty\u00F6njohtaja"      "ty\u00f6njohtaja"
   "ty\u00F6njohtaja"               "ty\u00f6njohtaja"
   "ei tiedossa"                    "ei tiedossa"
   "Rakennuksen omistaja"           "rakennuksen omistaja"

   ; TODO mappings for the rest
   :rakennuspaikanomistaja          "rakennuspaikan omistaja"
   :lupapaatoksentoimittaminen      "lupap\u00e4\u00e4t\u00f6ksen toimittaminen"
   :naapuri                         "naapuri"
   :lisatietojenantaja              "lis\u00e4tietojen antaja"
   :muu                             "muu osapuoli"})

(def kuntaRoolikoodit
  {:paasuunnittelija       "p\u00e4\u00e4suunnittelija"
   :hakija                 "Rakennusvalvonta-asian hakija"
   :maksaja                "Rakennusvalvonta-asian laskun maksaja"
   :rakennuksenomistaja    "Rakennuksen omistaja"})

(defn- get-simple-osoite [osoite]
  {:osoitenimi {:teksti (-> osoite :katu :value)}
   :postitoimipaikannimi (-> osoite :postitoimipaikannimi :value)
   :postinumero (-> osoite :postinumero :value)})

(defn- get-name [henkilotiedot]
  {:nimi {:etunimi (-> henkilotiedot :etunimi :value)
          :sukunimi (-> henkilotiedot :sukunimi :value)}})

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


(def ^:private default-role "ei tiedossa")
(defn- get-kuntaRooliKoodi [party party-type]
  (if (contains? kuntaRoolikoodit party-type)
    (kuntaRoolikoodit party-type)
    (let [code (or (get-in party [:kuntaRoolikoodi :value])
                   ; Old applications have kuntaRoolikoodi under patevyys group (LUPA-771)
                   (get-in party [:patevyys :kuntaRoolikoodi :value])
                   default-role)]
      (if (s/blank? code) default-role code))))


(defn- get-roolikoodit [kuntaRoolikoodi]
  {:kuntaRooliKoodi kuntaRoolikoodi ; Note the upper case 'Koodi'
   :VRKrooliKoodi (kuntaRoolikoodi-to-vrkRooliKoodi kuntaRoolikoodi)})

(defn get-osapuoli-data [osapuoli party-type]
  (let [henkilo        (if (= (-> osapuoli :_selected :value) "yritys")
                         (get-in osapuoli [:yritys :yhteyshenkilo])
                         (:henkilo osapuoli))
        kuntaRoolicode (get-kuntaRooliKoodi osapuoli party-type)
        omistajalaji   (muu-select-map
                         :muu (-> osapuoli :muu-omistajalaji :value)
                         :omistajalaji (-> osapuoli :omistajalaji :value))
        role-codes     {:VRKrooliKoodi (kuntaRoolikoodi-to-vrkRooliKoodi kuntaRoolicode)
                        :kuntaRooliKoodi kuntaRoolicode
                        :turvakieltoKytkin (true? (-> henkilo :henkilotiedot :turvakieltoKytkin :value))}
        codes          (if omistajalaji
                         (merge role-codes {:omistajalaji omistajalaji})
                         role-codes)]
    (if (= (-> osapuoli :_selected :value) "yritys")
      (merge codes
             {:yritys  (get-yritys-data (:yritys osapuoli))}
             {:henkilo (get-yhteyshenkilo-data henkilo)})
      (merge codes {:henkilo (get-henkilo-data henkilo)}))))

(defn get-parties-by-type [documents tag-name party-type doc-transformer]
  (for [doc (documents party-type)
        :let [osapuoli (:data doc)]
        :when (seq osapuoli)]
    {tag-name (doc-transformer osapuoli party-type)}))


(defn get-parties [documents]
  (into
    (get-parties-by-type documents :Osapuoli :hakija get-osapuoli-data)
    (get-parties-by-type documents :Osapuoli :maksaja get-osapuoli-data)))

(defn get-suunnittelija-data [suunnittelija party-type]
  (let [kuntaRoolikoodi (get-kuntaRooliKoodi suunnittelija party-type)
        codes {:suunnittelijaRoolikoodi kuntaRoolikoodi ; Note the lower case 'koodi'
               :VRKrooliKoodi (kuntaRoolikoodi-to-vrkRooliKoodi kuntaRoolikoodi)}
        patevyys (:patevyys suunnittelija)
        osoite (get-simple-osoite (:osoite suunnittelija))
        henkilo (merge (get-name (:henkilotiedot suunnittelija))
                       {:osoite osoite}
                       {:henkilotunnus (-> suunnittelija :henkilotiedot :hetu :value)}
                       (get-yhteystiedot-data (:yhteystiedot suunnittelija)))
        base-data (merge codes {:koulutus (-> patevyys :koulutus :value)
                                :patevyysvaatimusluokka (-> patevyys :patevyysluokka :value)
                                :henkilo henkilo})]
    (if (contains? suunnittelija :yritys)
      (assoc base-data :yritys (assoc
                                 (get-simple-yritys (:yritys suunnittelija))
                                 :postiosoite osoite))
      base-data)))

(defn- get-designers [documents]
  (into
    (get-parties-by-type documents :Suunnittelija :paasuunnittelija get-suunnittelija-data)
    (get-parties-by-type documents :Suunnittelija :suunnittelija get-suunnittelija-data)))

(defn- concat-tyotehtavat-to-string [selections]
  (let [joined (clojure.string/join ","
                 (reduce
                   (fn [r [k v]]
                     (if (true? (:value v))
                       (conj r (name k))
                       r))
                   []
                   (-> (dissoc selections :muuMika))))]
    (if (-> selections :muuMika :value s/blank? not)
      (str joined "," (-> selections :muuMika :value))
      joined)))

(defn get-tyonjohtaja-data [tyonjohtaja party-type]
  (let [foremans (-> (get-suunnittelija-data tyonjohtaja party-type) (dissoc :suunnittelijaRoolikoodi))
        patevyys (:patevyys tyonjohtaja)]
    (conj foremans {:tyonjohtajaRooliKoodi (get-kuntaRooliKoodi tyonjohtaja :tyonjohtaja) ; Note the lower case 'koodi'
                    :vastattavatTyotehtavat (concat-tyotehtavat-to-string (:vastattavatTyotehtavat tyonjohtaja))
                    :koulutus (-> patevyys :koulutus :value)
                    :patevyysvaatimusluokka (-> patevyys :patevyysvaatimusluokka :value)
                    :valmistumisvuosi (-> patevyys :valmistumisvuosi :value)
                    :kokemusvuodet (-> patevyys :kokemusvuodet :value)
                    :valvottavienKohteidenMaara (-> patevyys :valvottavienKohteidenMaara :value)
                    :tyonjohtajaHakemusKytkin (true? (= "hakemus" (-> patevyys :tyonjohtajaHakemusKytkin :value)))})))

(defn get-foremans [documents]
  (get-parties-by-type documents :Tyonjohtaja :tyonjohtaja get-tyonjohtaja-data))

(defn osapuolet [documents-by-types]
  {:Osapuolet
   {:osapuolitieto (get-parties documents-by-types)
    :suunnittelijatieto (get-designers documents-by-types)
    :tyonjohtajatieto (get-foremans documents-by-types)}})

(defn change-value-to-when [value to_compare new_val]
  (if (= value to_compare) new_val
    value))


(defn get-bulding-places [docs application]
  (for [doc docs
        :let [rakennuspaikka (:data doc)
              kiinteisto (:kiinteisto rakennuspaikka)
              id (:id doc)]]
    {:Rakennuspaikka
     {:yksilointitieto id
      :alkuHetki (to-xml-datetime (now))
      :kaavanaste (change-value-to-when (-> rakennuspaikka :kaavanaste :value) "eiKaavaa" "ei kaavaa")
      :rakennuspaikanKiinteistotieto {:RakennuspaikanKiinteisto
                                      {:kokotilaKytkin (s/blank? (-> kiinteisto :maaraalaTunnus :value))
                                       :hallintaperuste (-> rakennuspaikka :hallintaperuste :value)
                                       :kiinteistotieto {:Kiinteisto (merge {:tilannimi (-> kiinteisto :tilanNimi :value)
                                                                             :kiinteistotunnus (:propertyId application)
                                                                             :rantaKytkin (true? (-> kiinteisto :rantaKytkin :value))}
                                                         (when (-> kiinteisto :maaraalaTunnus :value)
                                                           {:maaraAlaTunnus (str "M" (-> kiinteisto :maaraalaTunnus :value))}))}}}}}))


