(ns lupapalvelu.document.canonical-common
  (:require [taoensso.timbre :refer [warnf]]
            [clojure.walk :as walk]
            [cljts.io :as jts]
            [swiss.arrows :refer [-<>]]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as v]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.operations :as op]
            [lupapalvelu.statement :as statement]))


; Empty String will be rendered as empty XML element
(def empty-tag "")

; State of the content when it is send over KRYSP
; NOT the same as the state of the application!
(def toimituksenTiedot-tila "keskener\u00e4inen")

(def application-state-to-krysp-state
  {:submitted "vireill\u00e4"
   :sent "vireill\u00e4"
   :complementNeeded "odottaa asiakkaan toimenpiteit\u00e4"
   :verdictGiven "p\u00e4\u00e4t\u00f6s toimitettu"
   :foremanVerdictGiven "p\u00e4\u00e4t\u00f6s toimitettu"
   :constructionStarted "rakennusty\u00f6t aloitettu"
   :appealed "p\u00e4\u00e4t\u00f6ksest\u00e4 valitettu, valitusprosessin tulosta ei ole"
   :closed "valmis"})

(def ymp-application-state-to-krysp-state
  {:sent "1 Vireill\u00e4"
   :submitted "1 Vireill\u00e4"
   :complementNeeded "1 Vireill\u00e4"
   :verdictGiven "ei tiedossa"
   :constructionStarted "ei tiedossa"
   :appealed "12 P\u00e4\u00e4t\u00f6ksest\u00e4 valitettu"
   :closed "13 P\u00e4\u00e4t\u00f6s lainvoimainen"})

(defn last-history-timestamp [state application]
  (some #(when (= state (:state %)) (:ts %)) (reverse (application :history))))

(def- state-timestamp-fn
  {:submitted :submitted
   :sent :submitted ; Enables XML to be formed from sent applications
   :complementNeeded :complementNeeded
   :verdictGiven (fn [app] (->> (:verdicts app) (map :timestamp) sort first))
   :foremanVerdictGiven (fn [app] (->> (:verdicts app) (map :timestamp) sort first))
   :constructionStarted :started
   :closed :closed})

(defn state-timestamp [{state :state :as application}]
  ((or (state-timestamp-fn (keyword state))
       (partial last-history-timestamp state)) application))

(defn all-state-timestamps [application]
  (into {}
    (map
      (fn [state] [state (state-timestamp (assoc application :state state))])
      (keys state-timestamp-fn))))

(defn by-type [documents]
  (group-by (comp keyword :name :schema-info) documents))

(defn empty-strings-to-nil [v]
  (when-not (and (string? v) (ss/blank? v)) v))

(defn documents-by-type-without-blanks
  "Converts blank strings to nils and groups documents by schema name"
  [{documents :documents}]
  (by-type (walk/postwalk empty-strings-to-nil documents)))

(defn documents-without-blanks [{documents :documents}]
  (walk/postwalk empty-strings-to-nil documents))

;;;
;;; Statement
;;;

(defn statements-ids-with-status [lausuntotieto]
  (reduce
    (fn [r l]
      (if (get-in l [:Lausunto :lausuntotieto :Lausunto :puoltotieto :Puolto :puolto])
        (conj r (get-in l [:Lausunto :id]))
        r))
    #{} lausuntotieto))

(def- puolto-mapping {:ei-huomautettavaa "ei huomautettavaa"
                      :ehdollinen "ehdollinen"
                      :puollettu "puollettu"
                      :ei-puollettu "ei puollettu"
                      :ei-lausuntoa "ei lausuntoa"
                      :lausunto "lausunto"
                      :kielteinen "kielteinen"
                      :palautettu "palautettu"
                      :poydalle "p\u00f6yd\u00e4lle"})

(defn- get-statement [statement]
  (let [state    (keyword (:state statement))
        lausunto {:Lausunto
                  {:id (:id statement)
                   :viranomainen (get-in statement [:person :text])
                   :pyyntoPvm (util/to-xml-date (:requested statement))}}]
    (if-not (and (:status statement) (statement/post-given-states state))
      lausunto
      (assoc-in lausunto [:Lausunto :lausuntotieto] {:Lausunto
                                                     {:viranomainen (get-in statement [:person :text])
                                                      :lausunto (:text statement)
                                                      :lausuntoPvm (util/to-xml-date (:given statement))
                                                      :puoltotieto
                                                      {:Puolto
                                                       {:puolto ((keyword (:status statement)) puolto-mapping)}}}}))))

(defn get-statements [statements]
  ;Returing vector because this element to be Associative
  (mapv get-statement statements))

(defn muu-select-map
  "If 'sel-val' is \"other\" considers 'muu-key' and 'muu-val', else considers 'sel-key' and 'sel-val'.
   If value (either 'muu-val' or 'sel-val' is blank, return nil, else return map with
   considered key mapped to considered value."
  [muu-key muu-val sel-key sel-val]
  (let [muu (= "other" sel-val)
        k   (if muu muu-key sel-key)
        v   (if muu muu-val sel-val)]
    (when-not (ss/blank? v)
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
   :ya-katulupa-vesi-ja-viemarityot "vesihuoltoverkostoty\u00f6"
   :ya-katulupa-maalampotyot "muu"
   :ya-katulupa-kaukolampotyot "kaukol\u00e4mp\u00f6verkostoty\u00f6"
   :ya-katulupa-kaapelityot "tietoliikenneverkostoty\u00f6"
   :ya-katulupa-kiinteiston-johto-kaapeli-ja-putkiliitynnat "verkoston liitosty\u00f6"
   :ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen "pysyvien maanalaisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-maalampoputkien-sijoittaminen "pysyvien maanalaisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-kaukolampoputkien-sijoittaminen "pysyvien maanalaisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-sahko-data-ja-muiden-kaapelien-sijoittaminen "pysyvien maanalaisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-rakennuksen-tai-sen-osan-sijoittaminen "pysyvien maanalaisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-ilmajohtojen-sijoittaminen "pysyvien maanp\u00e4\u00e4llisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-muuntamoiden-sijoittaminen "pysyvien maanp\u00e4\u00e4llisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-jatekatoksien-sijoittaminen "pysyvien maanp\u00e4\u00e4llisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-leikkipaikan-tai-koiratarhan-sijoittaminen "pysyvien maanp\u00e4\u00e4llisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-rakennuksen-pelastuspaikan-sijoittaminen "pysyvien maanp\u00e4\u00e4llisten rakenteiden sijoittaminen"
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
   :ya-sijoituslupa-rakennuksen-tai-sen-osan-sijoittaminen       "rakennuksen tai sen osan sijoittaminen"
   ;; pysyvien maanpaallisten rakenteiden sijoittaminen
   :ya-sijoituslupa-ilmajohtojen-sijoittaminen                   "ilmajohtojen sijoittaminen"
   :ya-sijoituslupa-muuntamoiden-sijoittaminen                   "muuntamoiden sijoittaminen"
   :ya-sijoituslupa-jatekatoksien-sijoittaminen                  "j\u00e4tekatoksien sijoittaminen"
   :ya-sijoituslupa-leikkipaikan-tai-koiratarhan-sijoittaminen   "leikkipaikan tai koiratarhan sijoittaminen"
   :ya-sijoituslupa-rakennuksen-pelastuspaikan-sijoittaminen     "rakennuksen pelastuspaikan sijoittaminen"
   })

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
   :ya-sijoituslupa-rakennuksen-tai-sen-osan-sijoittaminen       :Sijoituslupa
   :ya-sijoituslupa-ilmajohtojen-sijoittaminen                   :Sijoituslupa
   :ya-sijoituslupa-muuntamoiden-sijoittaminen                   :Sijoituslupa
   :ya-sijoituslupa-jatekatoksien-sijoittaminen                  :Sijoituslupa
   :ya-sijoituslupa-leikkipaikan-tai-koiratarhan-sijoittaminen   :Sijoituslupa
   :ya-sijoituslupa-rakennuksen-pelastuspaikan-sijoittaminen     :Sijoituslupa
   :ya-sijoituslupa-muu-sijoituslupa                             :Sijoituslupa})

(defn toimituksen-tiedot [{:keys [title municipality]} lang]
  {:aineistonnimi title
   :aineistotoimittaja (env/value :technical-contact)
   :tila toimituksenTiedot-tila
   :toimitusPvm (util/to-xml-date (now))
   :kuntakoodi municipality
   :kielitieto lang})

(defn- get-handler [{handler :authority :as application}]
  (if (domain/assigned? application)
    {:henkilo {:nimi {:etunimi (:firstName handler) :sukunimi (:lastName handler)}}}
    empty-tag))

(defn get-state [application]
  (let [state-timestamps (-<> (all-state-timestamps application)
                           (dissoc :sent :closed) ; sent date will be returned from toimituksen-tiedot function, closed has no valid KRYSP enumeration
                           util/strip-nils
                           (sort-by second <>))]
    (mapv
      (fn [[state ts]]
        {:Tilamuutos
         {:tila (application-state-to-krysp-state state)
          :pvm (util/to-xml-date ts)
          :kasittelija (get-handler application)}})
      state-timestamps)))


(defn lupatunnus [{:keys [id submitted] :as application}]
  {:pre [id]}
  {:LupaTunnus
   (util/assoc-when
     {:muuTunnustieto {:MuuTunnus {:tunnus id, :sovellus "Lupapiste"}}}
     :saapumisPvm (util/to-xml-date submitted)
     :kuntalupatunnus (-> application :verdicts first :kuntalupatunnus))})

(def kuntaRoolikoodi-to-vrkRooliKoodi
  {"Rakennusvalvonta-asian hakija"  "hakija"
   "Ilmoituksen tekij\u00e4"        "hakija"
   "Hakijan asiamies"               "muu osapuoli"
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
   "rakennussuunnittelija"                          "rakennussuunnittelija"
   "kantavien rakenteiden suunnittelija"            "erityissuunnittelija"
   "pohjarakenteiden suunnittelija"                 "erityissuunnittelija"
   "ilmanvaihdon suunnittelija"                     "erityissuunnittelija"
   "kiinteist\u00F6n vesi- ja viem\u00e4r\u00F6intilaitteiston suunnittelija"  "erityissuunnittelija"
   "rakennusfysikaalinen suunnittelija"             "erityissuunnittelija"
   "kosteusvaurion korjausty\u00F6n suunnittelija"  "erityissuunnittelija"
   :rakennuspaikanomistaja          "rakennuspaikan omistaja"
   :lupapaatoksentoimittaminen      "lupap\u00e4\u00e4t\u00f6ksen toimittaminen"
   :naapuri                         "naapuri"
   :lisatietojenantaja              "lis\u00e4tietojen antaja"
   :muu                             "muu osapuoli"})

(def kuntaRoolikoodit
  {:paasuunnittelija       "p\u00e4\u00e4suunnittelija"
   :hakija-r               "Rakennusvalvonta-asian hakija"
   :hakija                 "Rakennusvalvonta-asian hakija"
   :ilmoittaja             "Ilmoituksen tekij\u00e4"
   :maksaja                "Rakennusvalvonta-asian laskun maksaja"
   :rakennuksenomistaja    "Rakennuksen omistaja"
   :hakijan-asiamies       "Hakijan asiamies"})

(defn assoc-country
  "Augments given (address) map with country and foreign address
  information (based on data), if needed.
  Note: KRYSP supports postal codes only in the Finnish format. Thus,
  the unsupported postal codes are removed from the canonical."
  [address data]
  (let [maa (or (:maa data) "FIN")
        address (assoc address
                       :valtioSuomeksi        (i18n/localize :fi (str "country." maa))
                       :valtioKansainvalinen  maa)]
    (if-not (= maa "FIN")
      (let [{:keys [katu postinumero postitoimipaikannimi]} data
            address (if (v/finnish-zip? postinumero)
                      address
                      (dissoc address :postinumero))]
        (assoc address
               :ulkomainenLahiosoite       katu
               :ulkomainenPostitoimipaikka postitoimipaikannimi))
      address)))

(defn get-simple-osoite
  [{:keys [katu postinumero postitoimipaikannimi] :as osoite}]
  (when katu  ;; required field in krysp (i.e. "osoitenimi")
    (assoc-country {:osoitenimi           {:teksti katu}
                    :postitoimipaikannimi postitoimipaikannimi
                    :postinumero          postinumero}
      osoite)))

(defn- get-name [henkilotiedot]
  {:nimi (select-keys henkilotiedot [:etunimi :sukunimi])})

(defn- get-yhteystiedot-data [yhteystiedot]
  {:sahkopostiosoite (-> yhteystiedot :email)
   :puhelin (-> yhteystiedot :puhelin)})

(defn- get-simple-yritys [{:keys [yritysnimi liikeJaYhteisoTunnus] :as yritys}]
  {:nimi yritysnimi, :liikeJaYhteisotunnus liikeJaYhteisoTunnus})

(defn get-verkkolaskutus [unwrapped-party-doc]
  (let [yritys (get-in unwrapped-party-doc [:data :yritys] unwrapped-party-doc)
        {:keys [ovtTunnus verkkolaskuTunnus valittajaTunnus]} (:verkkolaskutustieto yritys)]
    (when-not (and (ss/blank? ovtTunnus) (ss/blank? verkkolaskuTunnus) (ss/blank? valittajaTunnus))
      {:Verkkolaskutus {:ovtTunnus ovtTunnus
                        :verkkolaskuTunnus verkkolaskuTunnus
                        :valittajaTunnus valittajaTunnus}})))

(defn- get-yritys-data [{:keys [osoite yhteyshenkilo] :as yritys}]
  (let [yhteystiedot (:yhteystiedot yhteyshenkilo)
        postiosoite (get-simple-osoite osoite)
        yhteystiedot-canonical {:postiosoite postiosoite ; - 2.1.4
                                :postiosoitetieto {:postiosoite postiosoite} ; 2.1.5+
                                :puhelin (:puhelin yhteystiedot)
                                :sahkopostiosoite (:email yhteystiedot)}
        yritys-canonical (merge
                           (get-simple-yritys yritys)
                           yhteystiedot-canonical
                           {:vainsahkoinenAsiointiKytkin (-> yhteyshenkilo :kytkimet :vainsahkoinenAsiointiKytkin true?)})]
    (util/assoc-when yritys-canonical :verkkolaskutustieto (get-verkkolaskutus yritys))))

(def- default-role "ei tiedossa")
(defn- get-kuntaRooliKoodi [party party-type subtype]
  (if (contains? kuntaRoolikoodit party-type)
    (kuntaRoolikoodit party-type)
    (let [code (or (get-in party [:kuntaRoolikoodi])
                   ; Old applications have kuntaRoolikoodi under patevyys group (LUPA-771)
                   (get-in party [:patevyys :kuntaRoolikoodi])
                   (get kuntaRoolikoodit (keyword subtype) default-role))]
      (if (ss/blank? code) default-role code))))

(defn get-osapuoli-data
  ([osapuoli party-type]
    (get-osapuoli-data osapuoli party-type nil))
  ([osapuoli party-type subtype]
   (let [selected-value (or (-> osapuoli :_selected) (-> osapuoli first key))
         yritys-type-osapuoli? (= "yritys" selected-value)
         henkilo        (if yritys-type-osapuoli?
                          (get-in osapuoli [:yritys :yhteyshenkilo])
                          (:henkilo osapuoli))]
     (when (-> henkilo :henkilotiedot :sukunimi)
       (let [kuntaRoolicode (get-kuntaRooliKoodi osapuoli party-type subtype)
             omistajalaji   (muu-select-map
                              :muu (:muu-omistajalaji osapuoli)
                              :omistajalaji (:omistajalaji osapuoli))]
         (merge
           {:VRKrooliKoodi (kuntaRoolikoodi-to-vrkRooliKoodi kuntaRoolicode)
            :kuntaRooliKoodi kuntaRoolicode
            :turvakieltoKytkin (true? (-> henkilo :henkilotiedot :turvakieltoKytkin))
            ;; Only explicit check allows direct marketing
            :suoramarkkinointikieltoKytkin (-> henkilo :kytkimet :suoramarkkinointilupa true? not)
            :henkilo (merge
                       (get-name (:henkilotiedot henkilo))
                       (get-yhteystiedot-data (:yhteystiedot henkilo))
                       (when-not yritys-type-osapuoli?
                         {:henkilotunnus (get-in henkilo [:henkilotiedot :hetu])
                          :osoite (get-simple-osoite (:osoite henkilo))
                          :vainsahkoinenAsiointiKytkin (-> henkilo :kytkimet :vainsahkoinenAsiointiKytkin true?)}))}
           (when yritys-type-osapuoli?
             {:yritys (get-yritys-data (:yritys osapuoli))})
           (when omistajalaji {:omistajalaji omistajalaji})))))))

(defn get-parties-by-type [documents-by-type tag-name party-type doc-transformer]
  (for [doc (documents-by-type party-type)
        :let [osapuoli (:data doc)
              subtype  (get-in doc [:schema-info :subtype])]
        :when (seq osapuoli)]
    {tag-name (doc-transformer osapuoli party-type subtype)}))

(defn get-parties [{:keys [schema-version] :as app} documents]
  (let [hakija-schema-names (conj (schemas/get-hakija-schema-names schema-version) "hakijan-asiamies")
        hakija-key (some #(when (hakija-schema-names (name %)) %) (keys documents))
        applicant-schema-name (op/get-applicant-doc-schema-name app)]
    (when-not (= hakija-key (keyword applicant-schema-name))
      (warnf "%s is not correct applicant schema (%s) for app %s" hakija-key applicant-schema-name (:id app)))
    (filter #(seq (:Osapuoli %))
      (into
        (get-parties-by-type documents :Osapuoli hakija-key get-osapuoli-data)
        (into (get-parties-by-type documents :Osapuoli :maksaja get-osapuoli-data)
              (get-parties-by-type documents :Osapuoli :hakijan-asiamies get-osapuoli-data))))))

(defn get-suunnittelija-data [suunnittelija party-type & [subtype]]
  (when (-> suunnittelija :henkilotiedot :sukunimi)
    (let [kuntaRoolikoodi (get-kuntaRooliKoodi suunnittelija party-type subtype)
          codes {:suunnittelijaRoolikoodi kuntaRoolikoodi ; Note the lower case 'koodi'
                 :VRKrooliKoodi (kuntaRoolikoodi-to-vrkRooliKoodi kuntaRoolikoodi)}
          patevyys (:patevyys suunnittelija)
          koulutus (if (= "other" (:koulutusvalinta patevyys))
                     "muu"
                     (:koulutusvalinta patevyys))
          osoite (get-simple-osoite (:osoite suunnittelija))
          henkilo (merge (get-name (:henkilotiedot suunnittelija))
                    {:osoite osoite}
                    {:henkilotunnus (-> suunnittelija :henkilotiedot :hetu)}
                    (get-yhteystiedot-data (:yhteystiedot suunnittelija)))]
      (merge codes
        {:koulutus koulutus
         :vaadittuPatevyysluokka (:suunnittelutehtavanVaativuusluokka suunnittelija)
         :patevyysvaatimusluokka (:patevyysluokka patevyys)
         :valmistumisvuosi (:valmistumisvuosi patevyys)
         :FISEpatevyyskortti (:fise patevyys)
         :FISEkelpoisuus (:fiseKelpoisuus patevyys)
         :kokemusvuodet (:kokemus patevyys)}
        (when (-> henkilo :nimi :sukunimi)
          {:henkilo henkilo})
        (when (-> suunnittelija :yritys :yritysnimi ss/blank? not)
          {:yritys (merge
                     (get-simple-yritys (:yritys suunnittelija))
                     {:postiosoite osoite ; - 2.1.4
                      ; 2.1.5+
                      :postiosoitetieto {:postiosoite osoite}})})))))

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
                   (dissoc selections :muuMika)))]
    (if (-> selections :muuMika ss/blank? not)
      (str joined "," (-> selections :muuMika))
      joined)))

(defn- get-sijaistustieto [{:keys [sijaistettavaHloEtunimi sijaistettavaHloSukunimi alkamisPvm paattymisPvm] :as sijaistus} sijaistettavaRooli]
  (when (or sijaistettavaHloEtunimi sijaistettavaHloSukunimi)
    {:Sijaistus (util/assoc-when {}
                  :sijaistettavaHlo (ss/trim (str sijaistettavaHloEtunimi " " sijaistettavaHloSukunimi))
                  :sijaistettavaRooli sijaistettavaRooli
                  :alkamisPvm (when-not (ss/blank? alkamisPvm) (util/to-xml-date-from-string alkamisPvm))
                  :paattymisPvm (when-not (ss/blank? paattymisPvm) (util/to-xml-date-from-string paattymisPvm)))}))

(defn- get-sijaistettava-hlo-214 [{:keys [sijaistettavaHloEtunimi sijaistettavaHloSukunimi] :as sijaistus}]
  (when (or sijaistettavaHloEtunimi sijaistettavaHloSukunimi)
    (ss/trim (str sijaistettavaHloEtunimi " " sijaistettavaHloSukunimi))))

(defn- get-vastattava-tyotieto [{tyotehtavat :vastattavatTyotehtavat} lang]
  (util/strip-nils
    (when (seq tyotehtavat)
      {:vastattavaTyotieto
       (remove nil?
         (map (fn [[k v]]
                (when v
                  {:VastattavaTyo
                   {:vastattavaTyo
                    (if (= k :muuMika)
                      v
                      (let [loc-s (i18n/localize lang (str "osapuoli.tyonjohtaja.vastattavatTyotehtavat." (name k)))]
                        (assert (not (re-matches #"^\?\?\?.*" loc-s)))
                        loc-s))}}))
              tyotehtavat))})))

(defn- foreman-tasks-by-role [{role :kuntaRoolikoodi tasks :vastattavatTyotehtavat}]
  (let [role-tasks {"KVV-ty\u00F6njohtaja"           [:ulkopuolinenKvvTyo :sisapuolinenKvvTyo :muuMika]
                    "IV-ty\u00F6njohtaja"            [:ivLaitoksenAsennustyo :ivLaitoksenKorjausJaMuutostyo :muuMika]
                    "erityisalojen ty\u00F6njohtaja" [:muuMika]
                    "vastaava ty\u00F6njohtaja"      [:rakennuksenPurkaminen
                                                      :uudisrakennustyoIlmanMaanrakennustoita
                                                      :maanrakennustyot
                                                      :uudisrakennustyoMaanrakennustoineen
                                                      :rakennuksenMuutosJaKorjaustyo
                                                      :linjasaneeraus
                                                      :muuMika]
                    "ei tiedossa"                    []}]
    (select-keys  tasks (get role-tasks role))))


(defn get-tyonjohtaja-data [application lang tyonjohtaja party-type & [subtype]]
  (let [foremans (dissoc (get-suunnittelija-data tyonjohtaja party-type subtype) :suunnittelijaRoolikoodi :FISEpatevyyskortti :FISEkelpoisuus)
        patevyys (:patevyys-tyonjohtaja tyonjohtaja)
        ;; The mappings in backing system providers' end make us pass "muu" when "muu koulutus" is selected.
        ;; Thus cannot use just this as koulutus:
        ;; (:koulutus (muu-select-map
        ;;              :koulutus (-> patevyys :koulutus)
        ;;              :koulutus (-> patevyys :koulutusvalinta)))
        koulutus (if (= "other" (:koulutusvalinta patevyys))
                   "muu"
                   (:koulutusvalinta patevyys))
        {:keys [alkamisPvm paattymisPvm] :as sijaistus} (:sijaistus tyonjohtaja)
        rooli    (get-kuntaRooliKoodi tyonjohtaja :tyonjohtaja nil)]
    (merge
      foremans
      {:tyonjohtajaRooliKoodi rooli
       :vastattavatTyotehtavat (concat-tyotehtavat-to-string (:vastattavatTyotehtavat tyonjohtaja))
       :koulutus koulutus
        :patevyysvaatimusluokka (:patevyysvaatimusluokka patevyys)
       :vaadittuPatevyysluokka (:patevyysvaatimusluokka patevyys)
       :valmistumisvuosi (:valmistumisvuosi patevyys)
       :kokemusvuodet (:kokemusvuodet patevyys)
       :valvottavienKohteidenMaara (:valvottavienKohteidenMaara patevyys)
       :tyonjohtajaHakemusKytkin (= "hakemus" (:tyonjohtajaHakemusKytkin patevyys))
       :sijaistustieto (get-sijaistustieto sijaistus rooli)}
      (when-not (ss/blank? alkamisPvm) {:alkamisPvm (util/to-xml-date-from-string alkamisPvm)})
      (when-not (ss/blank? paattymisPvm) {:paattymisPvm (util/to-xml-date-from-string paattymisPvm)})
      (get-vastattava-tyotieto tyonjohtaja lang)
      (let [sijaistettava-hlo (get-sijaistettava-hlo-214 sijaistus)]
        (when-not (ss/blank? sijaistettava-hlo)
          {:sijaistettavaHlo sijaistettava-hlo})))))

(defn get-tyonjohtaja-v2-data [application lang tyonjohtaja party-type & [subtype]]
  (let [foremans (dissoc (get-suunnittelija-data tyonjohtaja party-type subtype) :suunnittelijaRoolikoodi :FISEpatevyyskortti :FISEkelpoisuus)
        patevyys (:patevyys-tyonjohtaja tyonjohtaja)
        koulutus (if (= "other" (:koulutusvalinta patevyys))
                   "muu"
                   (:koulutusvalinta patevyys))
        {:keys [alkamisPvm paattymisPvm] :as sijaistus} (:sijaistus tyonjohtaja)
        rooli    (get-kuntaRooliKoodi tyonjohtaja :tyonjohtaja nil)]
    (merge
      foremans
      {:tyonjohtajaRooliKoodi rooli
       :vastattavatTyotehtavat (concat-tyotehtavat-to-string (:vastattavatTyotehtavat tyonjohtaja))
       :koulutus koulutus
       :patevyysvaatimusluokka (:patevyysvaatimusluokka tyonjohtaja)
       :vaadittuPatevyysluokka (:patevyysvaatimusluokka tyonjohtaja)
       :valmistumisvuosi (:valmistumisvuosi patevyys)
       :kokemusvuodet (:kokemusvuodet patevyys)
       :valvottavienKohteidenMaara (:valvottavienKohteidenMaara patevyys)
       :tyonjohtajaHakemusKytkin (= "tyonjohtaja-hakemus" (:permitSubtype application))
       :sijaistustieto (get-sijaistustieto sijaistus rooli)
       :vainTamaHankeKytkin (:tyonjohtajanHyvaksynta (:tyonjohtajanHyvaksynta tyonjohtaja))}
      (when-not (ss/blank? alkamisPvm) {:alkamisPvm (util/to-xml-date-from-string alkamisPvm)})
      (when-not (ss/blank? paattymisPvm) {:paattymisPvm (util/to-xml-date-from-string paattymisPvm)})
      (get-vastattava-tyotieto {:vastattavatTyotehtavat (foreman-tasks-by-role tyonjohtaja)} lang)
      (let [sijaistettava-hlo (get-sijaistettava-hlo-214 sijaistus)]
        (when-not (ss/blank? sijaistettava-hlo)
          {:sijaistettavaHlo sijaistettava-hlo})))))

(defn- get-foremen [application documents-by-type lang]
  (if (contains? documents-by-type :tyonjohtaja)
    (get-parties-by-type documents-by-type :Tyonjohtaja :tyonjohtaja (partial get-tyonjohtaja-data application lang))
    (get-parties-by-type documents-by-type :Tyonjohtaja :tyonjohtaja-v2 (partial get-tyonjohtaja-v2-data application lang))))

(defn address->osoitetieto [{katu :street postinumero :zip postitoimipaikannimi :city :as address}]
  (when-not (util/empty-or-nil? katu)
    (util/assoc-when {}
                     :osoitenimi {:teksti katu}
                     :postinumero postinumero
                     :postitoimipaikannimi postitoimipaikannimi)))

(defn get-neighbor [{status :status property-id :propertyId :as neighbor}]
  (let [{state :state vetuma :vetuma message :message} (last status)
        neighbor (util/assoc-when {}
                                  :henkilo (str (:firstName vetuma) " " (:lastName vetuma))
                                  :osoite (address->osoitetieto vetuma)
                                  :kiinteistotunnus property-id
                                  :hallintasuhde "Ei tiedossa"
                                  :huomautus message)]
    (case state
      "response-given-comments" {:Naapuri (assoc neighbor :huomautettavaaKytkin true)}
      "response-given-ok"       {:Naapuri (assoc neighbor :huomautettavaaKytkin false)}
      nil)))

(defn get-neighbors [neighbors]
  (->> (map get-neighbor neighbors)
       (remove nil?)))

(defn osapuolet [{neighbors :neighbors :as application} documents-by-type lang]
  {:pre [(map? documents-by-type) (string? lang)]}
  {:Osapuolet
   {:osapuolitieto (get-parties application documents-by-type)
    :suunnittelijatieto (get-designers documents-by-type)
    :tyonjohtajatieto (get-foremen application documents-by-type lang)
    ;:naapuritieto (get-neighbors neighbors);LPK-215
    }})

(defn change-value-to-when [value to_compare new_val]
  (if (= value to_compare) new_val value))



(def kaavatilanne-to-kaavanaste-mapping
  {"oikeusvaikutteinen yleiskaava" "yleis"
   "asemakaava" "asema"
   "ranta-asemakaava" "ranta"
   "ei kaavaa" "ei kaavaa"})

(defn format-maara-alatunnus
  "Given a valid parameter, returns M + zero padded id"
  [tunnus]
  (when-let [digits (and (string? tunnus) (second (re-find v/maara-alatunnus-pattern (ss/trim tunnus))))]
    (str \M (ss/zero-pad 4 digits))))

(defn get-bulding-places [docs application]
  (for [doc docs
        :let [rakennuspaikka (:data doc)
              kiinteisto (:kiinteisto rakennuspaikka)
              id (:id doc)
              kaavatilanne (-> rakennuspaikka :kaavatilanne)
              kaavanaste (change-value-to-when (-> rakennuspaikka :kaavanaste) "eiKaavaa" "ei kaavaa")
              rakennuspaikka-map {:Rakennuspaikka
                                  {:yksilointitieto id
                                   :alkuHetki (util/to-xml-datetime (now))
                                   :rakennuspaikanKiinteistotieto
                                   {:RakennuspaikanKiinteisto
                                    {:kokotilaKytkin (ss/blank? (ss/trim (:maaraalaTunnus kiinteisto)))
                                     :hallintaperuste (:hallintaperuste rakennuspaikka )
                                     :kiinteistotieto
                                     {:Kiinteisto
                                      (merge {:tilannimi (:tilanNimi kiinteisto)
                                              :kiinteistotunnus (:propertyId application)
                                              :rantaKytkin (true? (:rantaKytkin kiinteisto))}
                                        (when-let [maara-ala (format-maara-alatunnus (:maaraalaTunnus kiinteisto))]
                                          {:maaraAlaTunnus maara-ala}))}}}}}
              kaavatieto (merge
                           (when kaavanaste {:kaavanaste kaavanaste})
                           (when kaavatilanne
                             (merge
                               {:kaavatilanne kaavatilanne}
                               (when-not kaavanaste
                                 {:kaavanaste (or (kaavatilanne-to-kaavanaste-mapping kaavatilanne) "ei tiedossa")}))))]]
    (update-in rakennuspaikka-map [:Rakennuspaikka] merge kaavatieto)))

;; TODO lupatunnus type is always kuntalupatunnus?
(defn get-viitelupatieto [link-permit-data]
  (when link-permit-data
    (let [lupapiste-id (:lupapisteId link-permit-data)
          lupatunnus-map (if (= (:type link-permit-data) "kuntalupatunnus")
                           {:LupaTunnus {:kuntalupatunnus (:id link-permit-data)}}
                           (lupatunnus link-permit-data))
          lupatunnus-map (assoc-in lupatunnus-map
                           [:LupaTunnus :viittaus] "edellinen rakennusvalvonta-asia")
          muu-tunnustieto {:MuuTunnus {:tunnus lupapiste-id, :sovellus "Lupapiste"}}
          lupatunnus-map  (if (nil? lupapiste-id)
                            lupatunnus-map
                            (assoc-in lupatunnus-map
                              [:LupaTunnus :muuTunnustieto]
                              muu-tunnustieto))]
      lupatunnus-map)))

(defn get-kasittelytieto-ymp [application kt-key]
  {kt-key {:muutosHetki (util/to-xml-datetime (:modified application))
           :hakemuksenTila (ymp-application-state-to-krysp-state (keyword (:state application)))
           :asiatunnus (:id application)
           :paivaysPvm (util/to-xml-date (state-timestamp application))
           :kasittelija (if (domain/assigned? application)
                          {:henkilo
                           {:nimi {:etunimi  (get-in application [:authority :firstName])
                                   :sukunimi (get-in application [:authority :lastName])}}}
                          empty-tag)}})

(defn get-henkilo [henkilo]
  (let [nimi   (util/assoc-when {}
                                :etunimi (-> henkilo :henkilotiedot :etunimi)
                                :sukunimi (-> henkilo :henkilotiedot :sukunimi))
        osoite (get-simple-osoite (:osoite henkilo))]
    (not-empty
      (util/assoc-when {}
                  :nimi nimi
                  :osoite osoite
                  :sahkopostiosoite (-> henkilo :yhteystiedot :email)
                  :puhelin (-> henkilo :yhteystiedot :puhelin)
                   :henkilotunnus (-> henkilo :henkilotiedot :hetu)))))

(defn get-yhteystiedot [unwrapped-party-doc]
  (if (= (-> unwrapped-party-doc :data :_selected) "yritys")
    (let [yritys (-> unwrapped-party-doc :data :yritys)
          {:keys [yhteyshenkilo osoite]} yritys
          {:keys [etunimi sukunimi]} (:henkilotiedot yhteyshenkilo)
          {:keys [puhelin email]} (:yhteystiedot yhteyshenkilo)
          yhteyshenkilon-nimi (ss/trim (str etunimi " " sukunimi))
          osoite (get-simple-osoite (:osoite yritys))]
      (not-empty
        (util/assoc-when {}
          :yTunnus (:liikeJaYhteisoTunnus yritys)
          :yrityksenNimi (:yritysnimi yritys)
          :yhteyshenkilonNimi (when-not (ss/blank? yhteyshenkilon-nimi) yhteyshenkilon-nimi)
          :osoitetieto (when (seq osoite) {:Osoite osoite})
          :puhelinnumero puhelin
          :sahkopostiosoite email)))
    (when-let [henkilo (-> unwrapped-party-doc :data :henkilo)]
      (let [{:keys [henkilotiedot yhteystiedot]} henkilo
            osoite (get-simple-osoite (:osoite henkilo))]
        (not-empty
          (util/assoc-when {}
            :henkilotunnus (:hetu henkilotiedot)
            :sukunimi (:sukunimi henkilotiedot)
            :etunimi (:etunimi henkilotiedot)
            :osoitetieto (when (seq osoite) {:Osoite osoite})
            :puhelinnumero (:puhelin yhteystiedot)
            :sahkopostiosoite (:email yhteystiedot))))
      )))

(defn get-maksajatiedot [unwrapped-party-doc]
  (merge
    (get-yhteystiedot unwrapped-party-doc)
    (not-empty
      (util/assoc-when {}
        :laskuviite (get-in unwrapped-party-doc [:data :laskuviite])
        :verkkolaskutustieto (get-verkkolaskutus unwrapped-party-doc)))))

(defn- get-pos [coordinates]
  {:pos (map #(str (-> % .x) " " (-> % .y)) coordinates)})

(defn- get-basic-drawing-info [drawing]  ;; krysp Yhteiset 2.1.5+
  (util/assoc-when {}
    :nimi (:name drawing)
    :kuvaus (:desc drawing)
    :korkeusTaiSyvyys (:height drawing)
    :pintaAla (:area drawing)))

(defn- point-drawing [drawing]
  (let [p (-> drawing :geometry jts/read-wkt-str)
        cord (.getCoordinate p)]
    {:Sijainti
     (merge {:piste {:Point {:pos (str (-> cord .x) " " (-> cord .y))}}}
       (get-basic-drawing-info drawing))}))

(defn- linestring-drawing [drawing]
  (let [ls (-> drawing :geometry jts/read-wkt-str)]
    {:Sijainti
     (merge {:viiva {:LineString (get-pos (-> ls .getCoordinates))}}
       (get-basic-drawing-info drawing))}))

(defn- polygon-drawing [drawing]
  (let [polygon (-> drawing :geometry jts/read-wkt-str)]
    {:Sijainti
     (merge {:alue {:Polygon {:exterior {:LinearRing (get-pos (-> polygon .getCoordinates))}}}}
       (get-basic-drawing-info drawing))}))

(defn- drawing-type? [t drawing]
  (.startsWith (:geometry drawing) t))

(defn- drawings-as-krysp [drawings]
   (concat (map point-drawing (filter (partial drawing-type? "POINT") drawings))
           (map linestring-drawing (filter (partial drawing-type? "LINESTRING") drawings))
           (map polygon-drawing (filter (partial drawing-type? "POLYGON") drawings))))


(defn get-sijaintitieto [application]
  (let [app-location-info {:Sijainti {:osoite {:yksilointitieto (:id application)
                                               :alkuHetki (util/to-xml-datetime (now))
                                               :osoitenimi {:teksti (:address application)}}
                                      :piste {:Point {:pos (str (first (:location application)) " " (second (:location application)))}}}}
        drawings (drawings-as-krysp (:drawings application))]
    (cons app-location-info drawings)))

;;
;; The following definitions are used by maankayton-muutos and kiinteistotoimitus.
;; Those two schemas (at least) have somewhat more peculiar structure than the more
;; legacy operations.
;;

(defn entry [entry-key m & [force-key]]
  (let [k (or force-key entry-key)
        v (entry-key m)]
    (when-not (nil? v)
      {k v})))

(defn- str-get-in [m ks]
  ;; return strings for keywords
  (let [safe-name #(if (keyword? %)
                     (name %)
                     %)]
    (safe-name (get-in m ks))))

(defn schema-info-filter
  ([docs prop]
   (filter #(get-in % [:schema-info prop]) docs))
  ([docs prop value]
   (let [values (if (coll? value)
                  (set value)
                  #{value})]
     (filter #(contains? values (str-get-in % [:schema-info prop])) docs))))

(defn ->postiosoite-type [address]
  (assoc-country (merge {:osoitenimi (entry :katu address :teksti)}
         (entry :postinumero address)
                        (entry :postitoimipaikannimi address))
                 address))

(defn- ->nimi [personal]
  {:nimi (select-keys personal [:etunimi :sukunimi])})

(defmulti osapuolitieto :_selected)

(defmethod osapuolitieto "henkilo"
  [{{:keys [yhteystiedot henkilotiedot osoite kytkimet]} :henkilo}]
  (merge (entry :turvakieltoKytkin henkilotiedot :turvakieltokytkin)
         {:vainsahkoinenAsiointiKytkin (-> kytkimet :vainsahkoinenAsiointiKytkin true?)}
         {:henkilotieto {:Henkilo
                         (merge
                          (->nimi henkilotiedot)
                          {:osoite (->postiosoite-type osoite)}
                           (entry :email yhteystiedot :sahkopostiosoite)
                           (entry :puhelin yhteystiedot)
                           (entry :hetu henkilotiedot :henkilotunnus))}}))

(defmethod osapuolitieto "yritys"
  [data]
  (let [company (:yritys data)
        {contact :yhteystiedot personal :henkilotiedot kytkimet :kytkimet} (:yhteyshenkilo company)
        billing (get-verkkolaskutus {:data data})
        billing-information (when billing
                              {:verkkolaskutustieto billing})]
    (merge (entry :turvakieltoKytkin personal :turvakieltokytkin)
           {:vainsahkoinenAsiointiKytkin (true? (-> company :yhteyshenkilo :kytkimet :vainsahkoinenAsiointiKytkin))
            :henkilotieto {:Henkilo (->nimi personal)}
            :yritystieto {:Yritys (merge (entry :yritysnimi company :nimi)
                                         (entry :liikeJaYhteisoTunnus company :liikeJaYhteisotunnus )
                                         {:postiosoitetieto {:postiosoite (->postiosoite-type (:osoite company))}}
                                         (entry :puhelin contact)
                                         (entry :email contact :sahkopostiosoite)
                                         billing-information)}})))

(defn- process-party [lang {{role :subtype} :schema-info data :data}]
  {:Osapuoli (merge
              {:roolikoodi (ss/capitalize (name role))
               :asioimiskieli lang}
               (osapuolitieto data))})

(defn process-parties [docs lang]
  (map (partial process-party lang) (schema-info-filter docs :type "party")))

(defn application-state [app]
  (let [enums {:submitted "Vireill\u00e4"
               :sent "Haettu"
               :closed "P\u00e4\u00e4ttynyt"}
        state (-> app :state keyword)
        date (util/to-xml-date (state app))
        {a-first :firstName a-last :lastName} (:authority app)]
    {:Tila
     {:pvm date
      :kasittelija (format "%s %s" a-first a-last)
      :hakemuksenTila (state enums)}}))

(defn- mat-helper [property property-id]
  (when-let [mat (format-maara-alatunnus (:maaraalaTunnus property))]
    (str property-id mat)))

(defn maaraalatunnus
  "Returns maaraalatunnus in the correct format if the id is available
  in the property, otherwise nil. For the primary application the
  application must be provided."
  [property & [app]]
  (if app
    (mat-helper property (:propertyId app))
    (mat-helper property (:kiinteistoTunnus property))))
