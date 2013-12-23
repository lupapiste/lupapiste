(ns lupapalvelu.document.canonical-common
  (:require [clojure.string :as s]
            [clojure.walk :as walk]
            [sade.util :refer :all]
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
   :complement-needed "vireill\u00e4"
   :verdictGiven "p\u00e4\u00e4t\u00f6s toimitettu"
   :constructionStarted "rakennusty\u00f6t aloitettu"
   :closed "valmis"})

(def state-timestamps
  {:draft :created
   :open :opened
   :complement-needed :opened
   ; Application state in KRYSP will be "vireill\u00e4" -> use :opened date
   :submitted :opened
   ; Enables XML to be formed from sent applications
   :sent :opened
   :verdictGiven :opened
   :constructionStarted :opened
   :closed :closed})

(defn by-type [documents]
  (group-by (comp keyword :name :schema-info) documents))

(defn empty-strings-to-nil [v]
  (if (and (string? v) (s/blank? v)) nil v))

(defn documents-by-type-without-blanks
  "Converts blank strings to nils and groups documents by schema name"
  [{documents :documents}]
  (by-type (walk/postwalk empty-strings-to-nil documents)))

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
  {:ya-kayttolupa-tapahtumat "erilaiset messujen ja tapahtumien aikaiset alueiden k\u00e4yt\u00f6t"
   :ya-kayttolupa-mainostus-ja-viitoitus "mainoslaitteiden ja opasteviittojen sijoittaminen"
   :ya-kayttolupa-ya-kayttolupa-harrastustoiminnan-jarjestaminen "muut yleiselle alueelle kohdistuvat tilan k\u00e4yt\u00f6t"
   :ya-kayttolupa-metsastys "muut yleiselle alueelle kohdistuvat tilan k\u00e4yt\u00f6t"
   :ya-kayttolupa-vesistoluvat "muut yleiselle alueelle kohdistuvat tilan k\u00e4yt\u00f6t"
   :ya-kayttolupa-terassit "muut yleiselle alueelle kohdistuvat tilan k\u00e4yt\u00f6t"
   :ya-kayttolupa-kioskit "muut yleiselle alueelle kohdistuvat tilan k\u00e4yt\u00f6t"
   :ya-kayttolupa-muu-kayttolupa "muu kaytt\u00f6lupa"
   :ya-katulupa-vesi-ja-viemarityot "kaivu- tai katuty\u00f6lupa"
   :ya-katulupa-kaukolampotyot "kaivu- tai katuty\u00f6lupa"
   :ya-katulupa-kaapelityot "kaivu- tai katuty\u00f6lupa"
   :ya-katulupa-kiinteiston-johto-kaapeli-ja-putkiliitynnat "kaivu- tai katuty\u00f6lupa"
   :ya-katulupa-nostotyot "kadulta tapahtuvat nostot"
   :ya-katulupa-vaihtolavat "muu tyolupa"
   :ya-katulupa-kattolumien-pudotustyot "muu tyolupa"
   :ya-katulupa-muu-liikennealuetyo "muu tyolupa"
   :ya-katulupa-talon-julkisivutyot "kadulle pystytett\u00e4v\u00e4t rakennustelineet"
   :ya-katulupa-talon-rakennustyot "kiinteist\u00f6n rakentamis- ja korjaamisty\u00f6t, joiden suorittamiseksi rajataan osa kadusta tai yleisest\u00e4 alueesta ty\u00f6maaksi (ei kaivut\u00f6it\u00e4)"
   :ya-katulupa-muu-tyomaakaytto "muu tyolupa"
   :ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen "pysyvien maanalaisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-maalampoputkien-sijoittaminen "pysyvien maanalaisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-sahko-data-ja-muiden-kaapelien-sijoittaminen "pysyvien maanalaisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-ilmajohtojen-sijoittaminen "pysyvien maanp\u00e4\u00e4llisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-muuntamoiden-sijoittaminen "pysyvien maanp\u00e4\u00e4llisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-jatekatoksien-sijoittaminen "pysyvien maanp\u00e4\u00e4llisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-leikkipaikan-tai-koiratarhan-sijoittaminen "pysyvien maanp\u00e4\u00e4llisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-muu-sijoituslupa "muu sijoituslupa"})

(def ya-operation-type-to-schema-name-key
  {:ya-kayttolupa-tapahtumat :Kayttolupa
   :ya-kayttolupa-mainostus-ja-viitoitus :Kayttolupa
   :ya-kayttolupa-ya-kayttolupa-harrastustoiminnan-jarjestaminen :Kayttolupa
   :ya-kayttolupa-metsastys :Kayttolupa
   :ya-kayttolupa-vesistoluvat :Kayttolupa
   :ya-kayttolupa-terassit :Kayttolupa
   :ya-kayttolupa-kioskit :Kayttolupa
   :ya-kayttolupa-muu-kayttolupa :Kayttolupa
   :ya-katulupa-vesi-ja-viemarityot :Tyolupa
   :ya-katulupa-kaukolampotyot :Tyolupa
   :ya-katulupa-kaapelityot :Tyolupa
   :ya-katulupa-kiinteiston-johto-kaapeli-ja-putkiliitynnat :Tyolupa
   :ya-katulupa-nostotyot :Tyolupa
   :ya-katulupa-vaihtolavat :Tyolupa
   :ya-katulupa-kattolumien-pudotustyot :Tyolupa
   :ya-katulupa-muu-liikennealuetyo :Tyolupa
   :ya-katulupa-talon-julkisivutyot :Tyolupa
   :ya-katulupa-talon-rakennustyot :Tyolupa
   :ya-katulupa-muu-tyomaakaytto :Tyolupa
   :ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen :Sijoituslupa
   :ya-sijoituslupa-maalampoputkien-sijoittaminen :Sijoituslupa
   :ya-sijoituslupa-sahko-data-ja-muiden-kaapelien-sijoittaminen :Sijoituslupa
   :ya-sijoituslupa-ilmajohtojen-sijoittaminen :Sijoituslupa
   :ya-sijoituslupa-muuntamoiden-sijoittaminen :Sijoituslupa
   :ya-sijoituslupa-jatekatoksien-sijoittaminen :Sijoituslupa
   :ya-sijoituslupa-leikkipaikan-tai-koiratarhan-sijoittaminen :Sijoituslupa
   :ya-sijoituslupa-muu-sijoituslupa :Sijoituslupa})

(defn toimituksen-tiedot [{:keys [title municipality]} lang]
  {:aineistonnimi title
   :aineistotoimittaja "lupapiste@solita.fi"
   :tila toimituksenTiedot-tila
   :toimitusPvm (to-xml-date (now))
   :kuntakoodi municipality
   :kielitieto lang})

(defn- get-handler [{handler :authority}]
  (if (seq handler)
    {:henkilo {:nimi {:etunimi (:firstName handler) :sukunimi (:lastName handler)}}}
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
  (when (-> osoite :katu :value)  ;; required field in krysp (i.e. "osoitenimi")
    {:osoitenimi {:teksti (-> osoite :katu :value)}
     :postitoimipaikannimi (-> osoite :postitoimipaikannimi :value)
     :postinumero (-> osoite :postinumero :value)}))

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
  (let [yritys-type-osapuoli? (= (-> osapuoli :_selected :value) "yritys")
        henkilo        (if yritys-type-osapuoli?
                         (get-in osapuoli [:yritys :yhteyshenkilo])
                         (:henkilo osapuoli))]
    (when (-> henkilo :henkilotiedot :sukunimi :value)
      (let [kuntaRoolicode (get-kuntaRooliKoodi osapuoli party-type)
            omistajalaji   (muu-select-map
                             :muu (-> osapuoli :muu-omistajalaji :value)
                             :omistajalaji (-> osapuoli :omistajalaji :value))]
        (merge
          {:VRKrooliKoodi (kuntaRoolikoodi-to-vrkRooliKoodi kuntaRoolicode)
           :kuntaRooliKoodi kuntaRoolicode
           :turvakieltoKytkin (true? (-> henkilo :henkilotiedot :turvakieltoKytkin :value))
           :henkilo (merge
                      (get-name (:henkilotiedot henkilo))
                      (get-yhteystiedot-data (:yhteystiedot henkilo))
                      (when-not yritys-type-osapuoli?
                        {:henkilotunnus (-> (:henkilotiedot henkilo) :hetu :value)
                         :osoite (get-simple-osoite (:osoite henkilo))}))}
          (when yritys-type-osapuoli?
            {:yritys  (get-yritys-data (:yritys osapuoli))})
          (when omistajalaji {:omistajalaji omistajalaji}))))))

(defn get-parties-by-type [documents tag-name party-type doc-transformer]
  (for [doc (documents party-type)
        :let [osapuoli (:data doc)]
        :when (seq osapuoli)]
    {tag-name (doc-transformer osapuoli party-type)}))

(defn get-parties [documents]
  (filter #(seq (:Osapuoli %))
    (into
      (get-parties-by-type documents :Osapuoli :hakija get-osapuoli-data)
      (get-parties-by-type documents :Osapuoli :maksaja get-osapuoli-data))))

(defn get-suunnittelija-data [suunnittelija party-type]
  (when (-> suunnittelija :henkilotiedot :sukunimi :value)
    (let [kuntaRoolikoodi (get-kuntaRooliKoodi suunnittelija party-type)
          codes {:suunnittelijaRoolikoodi kuntaRoolikoodi ; Note the lower case 'koodi'
                 :VRKrooliKoodi (kuntaRoolikoodi-to-vrkRooliKoodi kuntaRoolikoodi)}
          patevyys (:patevyys suunnittelija)
          osoite (get-simple-osoite (:osoite suunnittelija))
          henkilo (merge (get-name (:henkilotiedot suunnittelija))
                    {:osoite osoite}
                    {:henkilotunnus (-> suunnittelija :henkilotiedot :hetu :value)}
                    (get-yhteystiedot-data (:yhteystiedot suunnittelija)))]
      (merge codes
        {:koulutus (-> patevyys :koulutus :value)
         :patevyysvaatimusluokka (-> patevyys :patevyysluokka :value)
         :valmistumisvuosi (-> patevyys :valmistumisvuosi :value)
         :kokemusvuodet (-> patevyys :kokemus :value)}
        (when (-> henkilo :nimi :sukunimi)
          {:henkilo henkilo})
        (when (-> suunnittelija :yritys :yritysnimi :value s/blank? not)
          {:yritys (merge
                     (get-simple-yritys (:yritys suunnittelija))
                     {:postiosoite osoite})})))))

(defn- get-designers [documents]
  (filter #(seq (:Suunnittelija %))
    (into
      (get-parties-by-type documents :Suunnittelija :paasuunnittelija get-suunnittelija-data)
      (get-parties-by-type documents :Suunnittelija :suunnittelija get-suunnittelija-data))))

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
    (merge foremans {:tyonjohtajaRooliKoodi (get-kuntaRooliKoodi tyonjohtaja :tyonjohtaja) ; Note the lower case 'koodi'
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

(defn get-viitelupatieto [link-permit-data]
  (when link-permit-data
    (->
      (if (= (:type link-permit-data) "lupapistetunnus")
        (lupatunnus (:id link-permit-data))
        {:LupaTunnus {:kuntalupatunnus (:id link-permit-data)}})
      (assoc-in [:LupaTunnus :viittaus] "edellinen rakennusvalvonta-asia"))))

