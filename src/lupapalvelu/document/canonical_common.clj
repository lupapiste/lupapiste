(ns lupapalvelu.document.canonical-common
  (:require [clojure.string :as s]
            [clojure.walk :as walk]
            [sade.strings :as ss]
            [sade.util :refer :all]
            [sade.common-reader :as cr]
            [lupapalvelu.core :refer [now]]
            [lupapalvelu.i18n :refer [with-lang loc]]
            [cljts.geom :as geo]
            [cljts.io :as jts]))


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
  (when-not (and (string? v) (s/blank? v)) v))

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
   :ya-kayttolupa-harrastustoiminnan-jarjestaminen "muut yleiselle alueelle kohdistuvat tilan k\u00e4yt\u00f6t"
   :ya-kayttolupa-metsastys "muut yleiselle alueelle kohdistuvat tilan k\u00e4yt\u00f6t"
   :ya-kayttolupa-vesistoluvat "muut yleiselle alueelle kohdistuvat tilan k\u00e4yt\u00f6t"
   :ya-kayttolupa-terassit "muut yleiselle alueelle kohdistuvat tilan k\u00e4yt\u00f6t"
   :ya-kayttolupa-kioskit "muut yleiselle alueelle kohdistuvat tilan k\u00e4yt\u00f6t"
   :ya-kayttolupa-muu-kayttolupa "muu kaytt\u00f6lupa"
   :ya-kayttolupa-nostotyot "kadulta tapahtuvat nostot"
   :ya-kayttolupa-vaihtolavat "muu kaytt\u00f6lupa"
   :ya-kayttolupa-kattolumien-pudotustyot "muu kaytt\u00f6lupa"
   :ya-kayttolupa-muu-liikennealuetyo "muu kaytt\u00f6lupa"
   :ya-kayttolupa-talon-julkisivutyot "kadulle pystytett\u00e4v\u00e4t rakennustelineet"
   :ya-kayttolupa-talon-rakennustyot "kiinteist\u00f6n rakentamis- ja korjaamisty\u00f6t, joiden suorittamiseksi rajataan osa kadusta tai yleisest\u00e4 alueesta ty\u00f6maaksi (ei kaivut\u00f6it\u00e4)"
   :ya-kayttolupa-muu-tyomaakaytto "muut yleiselle alueelle kohdistuvat tilan k\u00e4yt\u00f6t"
   :ya-katulupa-vesi-ja-viemarityot "kaivu- tai katuty\u00f6lupa"
   :ya-katulupa-maalampotyot "kaivu- tai katuty\u00f6lupa"
   :ya-katulupa-kaukolampotyot "kaivu- tai katuty\u00f6lupa"
   :ya-katulupa-kaapelityot "kaivu- tai katuty\u00f6lupa"
   :ya-katulupa-kiinteiston-johto-kaapeli-ja-putkiliitynnat "kaivu- tai katuty\u00f6lupa"
   :ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen "pysyvien maanalaisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-maalampoputkien-sijoittaminen "pysyvien maanalaisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-kaukolampoputkien-sijoittaminen "pysyvien maanalaisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-sahko-data-ja-muiden-kaapelien-sijoittaminen "pysyvien maanalaisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-ilmajohtojen-sijoittaminen "pysyvien maanp\u00e4\u00e4llisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-muuntamoiden-sijoittaminen "pysyvien maanp\u00e4\u00e4llisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-jatekatoksien-sijoittaminen "pysyvien maanp\u00e4\u00e4llisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-leikkipaikan-tai-koiratarhan-sijoittaminen "pysyvien maanp\u00e4\u00e4llisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-muu-sijoituslupa "muu sijoituslupa"})

(def ya-operation-type-to-additional-usage-description
  {;; Muu kayttolupa
   :ya-kayttolupa-vaihtolavat                      "vaihtolavat"
   :ya-kayttolupa-kattolumien-pudotustyot          "kattolumien pudotusty\u00f6t"
   :ya-kayttolupa-muu-liikennealuetyo              "muu liikennealuety\u00f6"
   :ya-kayttolupa-muu-kayttolupa                   "muu k\u00e4ytt\u00f6lupa"
   ;; Muut yleiselle alueelle kohdistuvat tilan kaytot
   :ya-kayttolupa-harrastustoiminnan-jarjestaminen "harrastustoiminnan j\u00e4rjest\u00e4minen"
   :ya-kayttolupa-metsastys                        "mets\u00e4stys"
   :ya-kayttolupa-vesistoluvat                     "vesistoluvat"
   :ya-kayttolupa-terassit                         "terassit"
   :ya-kayttolupa-kioskit                          "kioskit"
   :ya-kayttolupa-muu-tyomaakaytto                 "muu ty\u00f6maak\u00e4ytt\u00f6"
   ;; Kaivu- tai katutyolupa
   :ya-katulupa-vesi-ja-viemarityot                "vesi-ja-viem\u00e4rity\u00f6t"
   :ya-katulupa-maalampotyot                     "maal\u00e4mp\u00f6ty\u00f6t"
   :ya-katulupa-kaukolampotyot                     "kaukol\u00e4mp\u00f6ty\u00f6t"
   :ya-katulupa-kaapelityot                        "kaapelity\u00f6t"
   :ya-katulupa-kiinteiston-johto-kaapeli-ja-putkiliitynnat      "kiinteist\u00f6n johto-, kaapeli- ja putkiliitynn\u00e4t"
   ;; Pysyvien maanalaisten rakenteiden sijoittaminen
   :ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen        "vesi- ja viem\u00e4rijohtojen sijoittaminen"
   :ya-sijoituslupa-maalampoputkien-sijoittaminen                "maal\u00e4mp\u00f6putkien sijoittaminen"
   :ya-sijoituslupa-kaukolampoputkien-sijoittaminen                "kaukol\u00e4mp\u00f6putkien sijoittaminen"
   :ya-sijoituslupa-sahko-data-ja-muiden-kaapelien-sijoittaminen "s\u00e4hk\u00f6-, data- ja muiden kaapelien sijoittaminen"
   ;; pysyvien maanpaallisten rakenteiden sijoittaminen
   :ya-sijoituslupa-ilmajohtojen-sijoittaminen                   "ilmajohtojen sijoittaminen"
   :ya-sijoituslupa-muuntamoiden-sijoittaminen                   "muuntamoiden sijoittaminen"
   :ya-sijoituslupa-jatekatoksien-sijoittaminen                  "j\u00e4tekatoksien sijoittaminen"
   :ya-sijoituslupa-leikkipaikan-tai-koiratarhan-sijoittaminen   "leikkipaikan tai koiratarhan sijoittaminen"})

(def ya-operation-type-to-schema-name-key
  {:ya-kayttolupa-tapahtumat                                     :Kayttolupa
   :ya-kayttolupa-mainostus-ja-viitoitus                         :Kayttolupa
   :ya-kayttolupa-harrastustoiminnan-jarjestaminen               :Kayttolupa
   :ya-kayttolupa-metsastys                                      :Kayttolupa
   :ya-kayttolupa-vesistoluvat                                   :Kayttolupa
   :ya-kayttolupa-terassit                                       :Kayttolupa
   :ya-kayttolupa-kioskit                                        :Kayttolupa
   :ya-kayttolupa-muu-kayttolupa                                 :Kayttolupa
   :ya-kayttolupa-nostotyot                                      :Kayttolupa
   :ya-kayttolupa-vaihtolavat                                    :Kayttolupa
   :ya-kayttolupa-kattolumien-pudotustyot                        :Kayttolupa
   :ya-kayttolupa-muu-liikennealuetyo                            :Kayttolupa
   :ya-kayttolupa-talon-julkisivutyot                            :Kayttolupa
   :ya-kayttolupa-talon-rakennustyot                             :Kayttolupa
   :ya-kayttolupa-muu-tyomaakaytto                               :Kayttolupa
   :ya-katulupa-vesi-ja-viemarityot                              :Tyolupa
   :ya-katulupa-maalampotyot                                     :Tyolupa
   :ya-katulupa-kaukolampotyot                                   :Tyolupa
   :ya-katulupa-kaapelityot                                      :Tyolupa
   :ya-katulupa-kiinteiston-johto-kaapeli-ja-putkiliitynnat      :Tyolupa
   :ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen        :Sijoituslupa
   :ya-sijoituslupa-maalampoputkien-sijoittaminen                :Sijoituslupa
   :ya-sijoituslupa-kaukolampoputkien-sijoittaminen              :Sijoituslupa
   :ya-sijoituslupa-sahko-data-ja-muiden-kaapelien-sijoittaminen :Sijoituslupa
   :ya-sijoituslupa-ilmajohtojen-sijoittaminen                   :Sijoituslupa
   :ya-sijoituslupa-muuntamoiden-sijoittaminen                   :Sijoituslupa
   :ya-sijoituslupa-jatekatoksien-sijoittaminen                  :Sijoituslupa
   :ya-sijoituslupa-leikkipaikan-tai-koiratarhan-sijoittaminen   :Sijoituslupa
   :ya-sijoituslupa-muu-sijoituslupa                             :Sijoituslupa})

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

(defn get-simple-osoite [osoite]
  (when (-> osoite :katu)  ;; required field in krysp (i.e. "osoitenimi")
    {:osoitenimi {:teksti (-> osoite :katu)}
     :postitoimipaikannimi (-> osoite :postitoimipaikannimi)
     :postinumero (-> osoite :postinumero)}))

(defn- get-name [henkilotiedot]
  {:nimi {:etunimi (-> henkilotiedot :etunimi)
          :sukunimi (-> henkilotiedot :sukunimi)}})

(defn- get-yhteystiedot-data [yhteystiedot]
  {:sahkopostiosoite (-> yhteystiedot :email)
   :puhelin (-> yhteystiedot :puhelin)})

(defn- get-simple-yritys [yritys]
  {:nimi (-> yritys :yritysnimi)
   :liikeJaYhteisotunnus (-> yritys :liikeJaYhteisoTunnus)})

(defn- get-yritys-data [yritys]
  (let [yhteystiedot (get-in yritys [:yhteyshenkilo :yhteystiedot])]
    (merge (get-simple-yritys yritys)
           {:postiosoite (get-simple-osoite (:osoite yritys))
            :puhelin (-> yhteystiedot :puhelin)
            :sahkopostiosoite (-> yhteystiedot :email)})))

(def ^:private default-role "ei tiedossa")
(defn- get-kuntaRooliKoodi [party party-type]
  (if (contains? kuntaRoolikoodit party-type)
    (kuntaRoolikoodit party-type)
    (let [code (or (get-in party [:kuntaRoolikoodi])
                   ; Old applications have kuntaRoolikoodi under patevyys group (LUPA-771)
                   (get-in party [:patevyys :kuntaRoolikoodi])
                   default-role)]
      (if (s/blank? code) default-role code))))


(defn- get-roolikoodit [kuntaRoolikoodi]
  {:kuntaRooliKoodi kuntaRoolikoodi ; Note the upper case 'Koodi'
   :VRKrooliKoodi (kuntaRoolikoodi-to-vrkRooliKoodi kuntaRoolikoodi)})

(defn get-osapuoli-data [osapuoli party-type]
  (let [selected-value (or (-> osapuoli :_selected) (-> osapuoli first key))
        yritys-type-osapuoli? (= "yritys" selected-value)
        henkilo        (if yritys-type-osapuoli?
                         (get-in osapuoli [:yritys :yhteyshenkilo])
                         (:henkilo osapuoli))]
    (when (-> henkilo :henkilotiedot :sukunimi)
      (let [kuntaRoolicode (get-kuntaRooliKoodi osapuoli party-type)
            omistajalaji   (muu-select-map
                             :muu (-> osapuoli :muu-omistajalaji)
                             :omistajalaji (-> osapuoli :omistajalaji))]
        (merge
          {:VRKrooliKoodi (kuntaRoolikoodi-to-vrkRooliKoodi kuntaRoolicode)
           :kuntaRooliKoodi kuntaRoolicode
           :turvakieltoKytkin (true? (-> henkilo :henkilotiedot :turvakieltoKytkin))
           :henkilo (merge
                      (get-name (:henkilotiedot henkilo))
                      (get-yhteystiedot-data (:yhteystiedot henkilo))
                      (when-not yritys-type-osapuoli?
                        {:henkilotunnus (-> (:henkilotiedot henkilo) :hetu)
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
  (when (-> suunnittelija :henkilotiedot :sukunimi)
    (let [kuntaRoolikoodi (get-kuntaRooliKoodi suunnittelija party-type)
          codes {:suunnittelijaRoolikoodi kuntaRoolikoodi ; Note the lower case 'koodi'
                 :VRKrooliKoodi (kuntaRoolikoodi-to-vrkRooliKoodi kuntaRoolikoodi)}
          patevyys (:patevyys suunnittelija)
          osoite (get-simple-osoite (:osoite suunnittelija))
          henkilo (merge (get-name (:henkilotiedot suunnittelija))
                    {:osoite osoite}
                    {:henkilotunnus (-> suunnittelija :henkilotiedot :hetu)}
                    (get-yhteystiedot-data (:yhteystiedot suunnittelija)))]
      (merge codes
        {:koulutus (-> patevyys :koulutus)
         :patevyysvaatimusluokka (-> patevyys :patevyysluokka)
         :valmistumisvuosi (-> patevyys :valmistumisvuosi)
         :kokemusvuodet (-> patevyys :kokemus)}
        (when (-> henkilo :nimi :sukunimi)
          {:henkilo henkilo})
        (when (-> suunnittelija :yritys :yritysnimi s/blank? not)
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
                     (if (true? v)
                       (conj r (name k))
                       r))
                   []
                   (-> (dissoc selections :muuMika))))]
    (if (-> selections :muuMika s/blank? not)
      (str joined "," (-> selections :muuMika))
      joined)))

(defn- get-sijaistustieto [sijaistukset sijaistettavaRooli]
  (mapv (fn [[_ {:keys [sijaistettavaHloEtunimi sijaistettavaHloSukunimi alkamisPvm paattymisPvm]}]]
          (if (not (or sijaistettavaHloEtunimi sijaistettavaHloSukunimi))
            {}
            {:Sijaistus (assoc-when {}
                                    :sijaistettavaHlo (s/trim (str sijaistettavaHloEtunimi " " sijaistettavaHloSukunimi))
                                    :sijaistettavaRooli sijaistettavaRooli
                                    :alkamisPvm (when-not (s/blank? alkamisPvm) (to-xml-date-from-string alkamisPvm))
                                    :paattymisPvm (when-not (s/blank? paattymisPvm) (to-xml-date-from-string paattymisPvm)))}))
        (sort sijaistukset)))

(defn- get-sijaistettava-hlo-214 [sijaistukset]
  (->>
    (sort sijaistukset)
    (map (fn [[_ {:keys [sijaistettavaHloEtunimi sijaistettavaHloSukunimi]}]]
           (when (or sijaistettavaHloEtunimi sijaistettavaHloSukunimi)
             (s/trim (str sijaistettavaHloEtunimi " " sijaistettavaHloSukunimi)))))
    (remove ss/blank?)
    (s/join ", ")))

(defn- get-vastattava-tyotieto [tyonjohtaja lang]
  (with-lang lang
    (let [sijaistukset (:sijaistukset tyonjohtaja)
          tyotehtavat  (:vastattavatTyotehtavat tyonjohtaja)
          tyotehtavat-canonical (when (seq tyotehtavat)
                                  (->>
                                    (sort tyotehtavat)
                                    (map
                                     (fn [[k v]] (when v
                                                   (if (= k :muuMika)
                                                     v
                                                     (let [loc-s (loc (str "tyonjohtaja.vastattavatTyotehtavat." (name k)))]
                                                       (assert (not (re-matches #"^\?\?\?.*" loc-s)))
                                                       loc-s)))))
                                    (remove nil?)
                                    (s/join ", ")))]
      (cr/strip-nils
        (if (seq sijaistukset)
          {:vastattavaTyotieto
           (map (fn [[_ {:keys [alkamisPvm paattymisPvm]}]]
                  {:VastattavaTyo
                   {:vastattavaTyo tyotehtavat-canonical
                    :alkamisPvm   (when-not (s/blank? alkamisPvm) (to-xml-date-from-string alkamisPvm))
                    :paattymisPvm (when-not (s/blank? paattymisPvm) (to-xml-date-from-string paattymisPvm))}})
             (sort sijaistukset))}
          (when-not (ss/blank? tyotehtavat-canonical)
            {:vastattavaTyotieto {:VastattavaTyo {:vastattavaTyo tyotehtavat-canonical}}}))))))

(defn get-tyonjohtaja-data [lang tyonjohtaja party-type]
  (let [foremans (dissoc (get-suunnittelija-data tyonjohtaja party-type) :suunnittelijaRoolikoodi)
        patevyys (:patevyys tyonjohtaja)
        sijaistukset (:sijaistukset tyonjohtaja)
        rooli    (get-kuntaRooliKoodi tyonjohtaja :tyonjohtaja)]
    (merge
      foremans
      {:tyonjohtajaRooliKoodi rooli
       :vastattavatTyotehtavat (concat-tyotehtavat-to-string (:vastattavatTyotehtavat tyonjohtaja))
       :patevyysvaatimusluokka (:patevyysvaatimusluokka patevyys)
       :valmistumisvuosi (:valmistumisvuosi patevyys)
       :kokemusvuodet (:kokemusvuodet patevyys)
       :valvottavienKohteidenMaara (:valvottavienKohteidenMaara patevyys)
       :tyonjohtajaHakemusKytkin (= "hakemus" (:tyonjohtajaHakemusKytkin patevyys))
       :sijaistustieto (get-sijaistustieto sijaistukset rooli)}
      (get-vastattava-tyotieto tyonjohtaja lang)
      (let [sijaistettava-hlo (get-sijaistettava-hlo-214 sijaistukset)]
        (when-not (ss/blank? sijaistettava-hlo)
          {:sijaistettavaHlo sijaistettava-hlo})))))

(defn- get-foremans [documents lang]
  (get-parties-by-type documents :Tyonjohtaja :tyonjohtaja (partial get-tyonjohtaja-data lang)))

(defn- get-neighbor [neighbor-name property-id]
  {:Naapuri {:henkilo neighbor-name
             :kiinteistotunnus property-id
             :hallintasuhde "Ei tiedossa"}})

(defn- get-neighbors [neighbors]
  (remove nil? (for [[_ neighbor] neighbors]
                   (let [status (last (:status neighbor))
                         propertyId (-> neighbor :neighbor :propertyId)]
                     (case (:state status)
                       "response-given-ok" (get-neighbor (str (-> status :vetuma :firstName) " " (-> status :vetuma :lastName)) propertyId)
                       "mark-done" (get-neighbor (-> neighbor :neighbor :owner :name) propertyId)
                       nil)))))

(defn osapuolet
  ([documents-by-types lang]
    (osapuolet documents-by-types nil))
  ([documents-by-types neighbors lang]
    {:Osapuolet
     {:osapuolitieto (get-parties documents-by-types)
      :suunnittelijatieto (get-designers documents-by-types)
      :tyonjohtajatieto (get-foremans documents-by-types lang)
      :naapuritieto (get-neighbors neighbors)}}))

(defn change-value-to-when [value to_compare new_val]
  (if (= value to_compare) new_val value))


(defn get-bulding-places [docs application]
  (for [doc docs
        :let [rakennuspaikka (:data doc)
              kiinteisto (:kiinteisto rakennuspaikka)
              id (:id doc)]]
    {:Rakennuspaikka
     {:yksilointitieto id
      :alkuHetki (to-xml-datetime (now))
      :kaavanaste (change-value-to-when (-> rakennuspaikka :kaavanaste) "eiKaavaa" "ei kaavaa")
      :rakennuspaikanKiinteistotieto {:RakennuspaikanKiinteisto
                                      {:kokotilaKytkin (s/blank? (-> kiinteisto :maaraalaTunnus))
                                       :hallintaperuste (-> rakennuspaikka :hallintaperuste)
                                       :kiinteistotieto {:Kiinteisto (merge {:tilannimi (-> kiinteisto :tilanNimi)
                                                                             :kiinteistotunnus (:propertyId application)
                                                                             :rantaKytkin (true? (-> kiinteisto :rantaKytkin))}
                                                         (when (-> kiinteisto :maaraalaTunnus)
                                                           {:maaraAlaTunnus (str "M" (-> kiinteisto :maaraalaTunnus))}))}}}}}))

(defn get-viitelupatieto [link-permit-data]
  (when link-permit-data
    (assoc-in
      (if (= (:type link-permit-data) "kuntalupatunnus")
        {:LupaTunnus {:kuntalupatunnus (:id link-permit-data)}}
        (lupatunnus (:id link-permit-data)))
      [:LupaTunnus :viittaus] "edellinen rakennusvalvonta-asia")))

(defn get-kasittelytieto-ymp [application kt-key]
  {kt-key {:muutosHetki (to-xml-datetime (:modified application))
           :hakemuksenTila (application-state-to-krysp-state (keyword (:state application)))
           :asiatunnus (:id application)
           :paivaysPvm (to-xml-date ((state-timestamps (keyword (:state application))) application))
           :kasittelija (let [handler (:authority application)]
                          (if (seq handler)
                            {:henkilo
                             {:nimi {:etunimi  (:firstName handler)
                                     :sukunimi (:lastName handler)}}}
                            empty-tag))}})

(defn get-henkilo [henkilo]
  (let [nimi (assoc-when {}
                         :etunimi (-> henkilo :henkilotiedot :etunimi)
                         :sukunimi (-> henkilo :henkilotiedot :sukunimi))
        teksti (assoc-when {} :teksti (-> henkilo :osoite :katu))
        osoite (assoc-when {}
                           :osoitenimi teksti
                           :postinumero (-> henkilo :osoite :postinumero)
                           :postitoimipaikannimi (-> henkilo :osoite :postitoimipaikannimi))]
    (not-empty
      (assoc-when {}
                  :nimi nimi
                  :osoite osoite
                  :sahkopostiosoite (-> henkilo :yhteystiedot :email)
                  :puhelin (-> henkilo :yhteystiedot :puhelin)
                   :henkilotunnus (-> henkilo :henkilotiedot :hetu)))))

(defn ->ymp-osapuoli [unwrapped-party-doc]
  (if (= (-> unwrapped-party-doc :data :_selected) "yritys")
    (let [yritys (-> unwrapped-party-doc :data :yritys)]
      {:nimi (-> yritys :yritysnimi)
       :postiosoite (get-simple-osoite (:osoite yritys))
       :yhteyshenkilo (get-henkilo (:yhteyshenkilo yritys))
       :liikeJaYhteisotunnus (:liikeJaYhteisoTunnus yritys)})
    (when-let [henkilo (-> unwrapped-party-doc :data :henkilo)]
      {:nimi "Yksityishenkil\u00f6"
       :postiosoite (get-simple-osoite (:osoite henkilo))
       :yhteyshenkilo (get-henkilo henkilo)})))

(defn- get-pos [coordinates]
  {:pos (map #(str (-> % .x) " " (-> % .y)) coordinates)})

(defn- point-drawing [drawing]
  (let  [geometry (:geometry drawing)
         p (jts/read-wkt-str geometry)
         cord (.getCoordinate p)]
    {:Sijainti
     {:piste {:Point {:pos (str (-> cord .x) " " (-> cord .y))}}}}))

(defn- linestring-drawing [drawing]
  (let  [geometry (:geometry drawing)
         ls (jts/read-wkt-str geometry)]
    {:Sijainti
     {:viiva {:LineString (get-pos (-> ls .getCoordinates))}}}))

(defn- polygon-drawing [drawing]
  (let  [geometry (:geometry drawing)
         polygon (jts/read-wkt-str geometry)]
    {:Sijainti
     {:alue {:Polygon {:exterior {:LinearRing (get-pos (-> polygon .getCoordinates))}}}}}))

(defn- drawing-type? [t drawing]
  (.startsWith (:geometry drawing) t))

(defn- drawings-as-krysp [drawings]
   (concat (map point-drawing (filter (partial drawing-type? "POINT") drawings))
           (map linestring-drawing (filter (partial drawing-type? "LINESTRING") drawings))
           (map polygon-drawing (filter (partial drawing-type? "POLYGON") drawings))))


(defn get-sijaintitieto [application]
  (let [drawings (drawings-as-krysp (:drawings application))]
    (cons {:Sijainti {:osoite {:yksilointitieto (:id application)
                               :alkuHetki (to-xml-datetime (now))
                               :osoitenimi {:teksti (:address application)}}
                      :piste {:Point {:pos (str (:x (:location application)) " " (:y (:location application)))}}}}
      drawings)))