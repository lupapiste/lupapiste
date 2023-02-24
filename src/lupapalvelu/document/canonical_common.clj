(ns lupapalvelu.document.canonical-common
  (:require [cljts.io :as jts]
            [clojure.walk :as walk]
            [lupapalvelu.application-utils :as app-utils]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.operations :as op]
            [lupapalvelu.pate.verdict-interface :as vif]
            [lupapalvelu.statement-schemas :as statement-schemas]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.env :as env]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as v]
            [swiss.arrows :refer [-<>]]
            [taoensso.timbre :refer [warnf]])
  (:import [com.vividsolutions.jts.geom Coordinate Geometry]))


; Empty String will be rendered as empty XML element
(def empty-tag "")

; State of the content when it is send over KRYSP
; NOT the same as the state of the application!
(def toimituksenTiedot-tila "keskeneräinen")

(def application-state-to-krysp-state
  {:submitted           "vireillä"
   :sent                "vireillä"
   :complementNeeded    "odottaa asiakkaan toimenpiteitä"
   :verdictGiven        "päätös toimitettu"
   :foremanVerdictGiven "päätös toimitettu"
   :constructionStarted "rakennustyöt aloitettu"
   :appealed            "päätöksestä valitettu, valitusprosessin tulosta ei ole"
   :closed              "valmis"
   :finished            "valmis"
   :agreementPrepared   "odottaa asiakkaan toimenpiteitä"
   :agreementSigned     "valmis"
   :extinct             "lupa rauennut"
   :inUse               "osittainen loppukatselmus, yksi tai useampia luvan rakennuksista on käyttöönotettu"})

(def ymp-application-state-to-krysp-state
  {:sent "1 Vireillä"
   :submitted "1 Vireillä"
   :complementNeeded "1 Vireillä"
   :verdictGiven "ei tiedossa"
   :constructionStarted "ei tiedossa"
   :appealed "12 Päätöksestä valitettu"
   :closed "13 Päätös lainvoimainen"})

(defn last-history-timestamp [state application]
  (some #(when (= state (:state %)) (:ts %)) (reverse (application :history))))

(def- state-timestamp-fn
  {:submitted           :submitted
   :sent                :submitted ; Enables XML to be formed from sent applications
   :complementNeeded    :complementNeeded
   :verdictGiven        (fn [app] (->> (:verdicts app) (map :timestamp) sort first))
   :foremanVerdictGiven (fn [app] (->> (:verdicts app) (map :timestamp) sort first))
   :constructionStarted :started
   :agreementPrepared   :agreementPrepared
   :agreementSigned     :agreementSigned
   :finished            :finished
   :closed              :closed
   :inUse               :inUse})

(defn state-timestamp [{state :state :as application}]
  ((or (state-timestamp-fn (keyword state))
       (partial last-history-timestamp state)) application))

(defn all-state-timestamps [application]
  (into {}
    (map
      (fn [state] [state (state-timestamp (assoc application :state state))])
      (keys state-timestamp-fn))))

(defn empty-strings-to-nil [v]
  (when-not (and (string? v) (ss/blank? v)) v))

(defn positive-integer
  "Returns nil if value cannot be converted to postive integer, else returns value itself."
  [v]
  (when (pos? (util/->int v)) v))

(defn documents-without-blanks [{documents :documents}]
  (walk/postwalk empty-strings-to-nil documents))

(defn stripped-documents-by-type
  "Converts blank strings to nils and groups documents by schema name"
  [application]
  (->> application
       (documents-without-blanks)
       (map util/strip-empty-maps)
       (group-by (comp keyword :name :schema-info))))

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

;; See yht:LausuntoRvPType
(def- puolto-mapping {:ei-huomautettavaa "ei huomautettavaa"
                      :ehdollinen        "ehdollinen"
                      :puollettu         "puollettu"
                      :ei-puollettu      "ei puollettu"
                      :ei-lausuntoa      "ei lausuntoa"
                      :lausunto          "lausunto"
                      :kielteinen        "kielteinen"
                      :palautettu        "palautettu"
                      :poydalle          "pöydälle"
                      :ei-tiedossa       "ei tiedossa"})

(defn- get-statement [statement]
  (let [state    (keyword (:state statement))
        lausunto {:Lausunto
                  {:id (:id statement)
                   :viranomainen (get-in statement [:person :text])
                   :pyyntoPvm (date/xml-date (:requested statement))}}]
    (if-not (and (:status statement) (statement-schemas/post-given-states state))
      lausunto
      (assoc-in lausunto [:Lausunto :lausuntotieto] {:Lausunto
                                                     {:viranomainen (get-in statement [:person :text])
                                                      :lausunto (:text statement)
                                                      :lausuntoPvm (date/xml-date (:given statement))
                                                      :puoltotieto
                                                      {:Puolto
                                                       {:puolto ((keyword (:status statement)) puolto-mapping)}}}}))))

(defn get-statements [statements]
  ;Returing vector because this element to be Associative
  (mapv get-statement statements))

(defn muu-select-map
  "If 'sel-val' is \"other\" considers 'muu-key' and 'muu-val', else considers 'sel-key' and 'sel-val'.
   If value (either 'muu-val' or 'sel-val') is blank, return nil, else return map with
   considered key mapped to considered value."
  [muu-key muu-val sel-key sel-val]
  (let [muu (= "other" sel-val)
        k   (if muu muu-key sel-key)
        v   (if muu muu-val (ss/->plain-string sel-val))]
    (when-not (ss/blank? v)
      {k v})))

(defn get-avain-arvo-parit
  "Returns the free-form data that is not specified in the KuntaGML but
  has been agreed upon with the backing systems.
  For LPK-6231 this means some encumbrance property information for Facta"
  [documents-by-type]
  (let [pari (fn [avain arvo idx]
               {:AvainArvoPari
                {:avain (format "rasite_%s_%s" avain idx)
                 :arvo  arvo}})

        get-properties (fn [doc]
                         (let [type-kw         (or (some-> doc :data :tyyppi keyword)
                                                   :rakennusrasite)
                               list-properties (fn [prop-group]
                                                 ;; Combine the data relevant to forming avain-arvo-parit
                                                 ;; and discard the property group's internal index (`_`)
                                                 (map (fn [[_ property]]
                                                        (merge property
                                                               {:prop-group prop-group
                                                                :tarkenne   (name type-kw)}))
                                                      (-> doc :data type-kw prop-group)))]
                           (if (= type-kw :rakennusrasite)
                             (concat (list-properties :oikeutetutTontit)
                                     (list-properties :rasitetutTontit))
                             (list-properties :kohdeTontit))))

        props->a-a-parit (fn [idx {:keys [prop-group kiinteistotunnus tarkenne]}]
                           ;; Assign a new id `idx` referring to the order
                           ;; in which the properties appear on the application
                           [(pari "kiinteistotunnus" kiinteistotunnus idx)
                            (pari "laji"
                                  (get {:oikeutetutTontit "oikeutettu"
                                        :rasitetutTontit  "rasitettu"
                                        :kohdeTontit      "sopimus"}
                                       prop-group)
                                  idx)
                            (pari "tarkenne" tarkenne idx)])]
    (->> documents-by-type
         :rasite-tai-yhteisjarjestely
         (mapcat get-properties)
         (mapcat props->a-a-parit (range)))))

(def ya-operation-type-to-usage-description
  {:ya-kayttolupa-tapahtumat "erilaiset messujen ja tapahtumien aikaiset alueiden käytöt"
   :ya-kayttolupa-mainostus-ja-viitoitus "mainoslaitteiden ja opasteviittojen sijoittaminen"
   :ya-kayttolupa-harrastustoiminnan-jarjestaminen "muut yleiselle alueelle kohdistuvat tilan käytöt"
   :ya-kayttolupa-metsastys "muut yleiselle alueelle kohdistuvat tilan käytöt"
   :ya-kayttolupa-vesistoluvat "muut yleiselle alueelle kohdistuvat tilan käytöt"
   :ya-kayttolupa-terassit "muut yleiselle alueelle kohdistuvat tilan käytöt"
   :ya-kayttolupa-kioskit "muut yleiselle alueelle kohdistuvat tilan käytöt"
   :ya-kayttolupa-muu-kayttolupa "muu kayttölupa"
   :ya-kayttolupa-nostotyot "kadulta tapahtuvat nostot"
   :ya-kayttolupa-vaihtolavat "muu kayttölupa"
   :ya-kayttolupa-kattolumien-pudotustyot "muu kayttölupa"
   :ya-kayttolupa-talon-julkisivutyot "kadulle pystytettävät rakennustelineet"
   :ya-kayttolupa-talon-rakennustyot "kiinteistön rakentamis- ja korjaamistyöt, joiden suorittamiseksi rajataan osa kadusta tai yleisestä alueesta työmaaksi (ei kaivutöitä)"
   :ya-kayttolupa-muu-tyomaakaytto "muut yleiselle alueelle kohdistuvat tilan käytöt"
   :ya-katulupa-muu-liikennealuetyo "muu"
   :ya-katulupa-vesi-ja-viemarityot "vesihuoltoverkostotyö"
   :ya-katulupa-maalampotyot "muu"
   :ya-katulupa-kaukolampotyot "kaukolämpöverkostotyö"
   :ya-katulupa-kaapelityot "tietoliikenneverkostotyö"
   :ya-katulupa-kiinteiston-johto-kaapeli-ja-putkiliitynnat "verkoston liitostyö"
   :ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen "pysyvien maanalaisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-maalampoputkien-sijoittaminen "pysyvien maanalaisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-kaukolampoputkien-sijoittaminen "pysyvien maanalaisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-sahko-data-ja-muiden-kaapelien-sijoittaminen "pysyvien maanalaisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-rakennuksen-tai-sen-osan-sijoittaminen "pysyvien maanalaisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-ilmajohtojen-sijoittaminen "pysyvien maanpäällisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-muuntamoiden-sijoittaminen "pysyvien maanpäällisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-jatekatoksien-sijoittaminen "pysyvien maanpäällisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-leikkipaikan-tai-koiratarhan-sijoittaminen "pysyvien maanpäällisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-rakennuksen-pelastuspaikan-sijoittaminen "pysyvien maanpäällisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-muu-sijoituslupa "muu sijoituslupa"})

(def ya-operation-type-to-additional-usage-description
  {;; Muu kayttolupa
   :ya-kayttolupa-vaihtolavat                      "vaihtolavat"
   :ya-kayttolupa-kattolumien-pudotustyot          "kattolumien pudotustyöt"
   :ya-kayttolupa-muu-kayttolupa                   "muu käyttölupa"
   ;; Muut yleiselle alueelle kohdistuvat tilan kaytot
   :ya-kayttolupa-harrastustoiminnan-jarjestaminen "harrastustoiminnan järjestäminen"
   :ya-kayttolupa-metsastys                        "metsästys"
   :ya-kayttolupa-vesistoluvat                     "vesistoluvat"
   :ya-kayttolupa-terassit                         "terassit"
   :ya-kayttolupa-kioskit                          "kioskit"
   :ya-kayttolupa-muu-tyomaakaytto                 "muu työmaakäyttö"
   ;; Kaivu- tai katutyolupa
   :ya-katulupa-muu-liikennealuetyo                "muu liikennealuetyö"
   :ya-katulupa-vesi-ja-viemarityot                "vesi-ja-viemärityöt"
   :ya-katulupa-maalampotyot                     "maalämpötyöt"
   :ya-katulupa-kaukolampotyot                     "kaukolämpötyöt"
   :ya-katulupa-kaapelityot                        "kaapelityöt"
   :ya-katulupa-kiinteiston-johto-kaapeli-ja-putkiliitynnat      "kiinteistön johto-, kaapeli- ja putkiliitynnät"
   ;; Pysyvien maanalaisten rakenteiden sijoittaminen
   :ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen        "vesi- ja viemärijohtojen sijoittaminen"
   :ya-sijoituslupa-maalampoputkien-sijoittaminen                "maalämpöputkien sijoittaminen"
   :ya-sijoituslupa-kaukolampoputkien-sijoittaminen                "kaukolämpöputkien sijoittaminen"
   :ya-sijoituslupa-sahko-data-ja-muiden-kaapelien-sijoittaminen "sähkö-, data- ja muiden kaapelien sijoittaminen"
   :ya-sijoituslupa-rakennuksen-tai-sen-osan-sijoittaminen       "rakennuksen tai sen osan sijoittaminen"
   ;; pysyvien maanpaallisten rakenteiden sijoittaminen
   :ya-sijoituslupa-ilmajohtojen-sijoittaminen                   "ilmajohtojen sijoittaminen"
   :ya-sijoituslupa-muuntamoiden-sijoittaminen                   "muuntamoiden sijoittaminen"
   :ya-sijoituslupa-jatekatoksien-sijoittaminen                  "jätekatoksien sijoittaminen"
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
   :ya-kayttolupa-talon-julkisivutyot                            :Kayttolupa
   :ya-kayttolupa-talon-rakennustyot                             :Kayttolupa
   :ya-kayttolupa-muu-tyomaakaytto                               :Kayttolupa
   :ya-katulupa-muu-liikennealuetyo                              :Tyolupa
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

(defn toimituksen-tiedot [{:keys [title municipality address]} lang]
  {:aineistonnimi (or title address "Lupapiste KuntaGML")
   :aineistotoimittaja (env/value :aineistotoimittaja)
   :tila toimituksenTiedot-tila
   :toimitusPvm (date/xml-date (now))
   :kuntakoodi municipality
   :kielitieto lang})

(defn- get-general-handler [{handlers :handlers}]
  (util/find-first :general handlers))

(defn- get-handler [application]
  (if-let [general-handler (get-general-handler application)]
    {:henkilo {:nimi {:etunimi (:firstName general-handler) :sukunimi (:lastName general-handler)}}}
    empty-tag))

(defn get-state [application]
  (let [state-timestamps (-<> (all-state-timestamps application)
                           (dissoc :sent :closed :finished :agreementSigned) ; sent date will be returned from toimituksen-tiedot function, closed has no valid KRYSP enumeration
                           util/strip-nils
                           (sort-by second <>))]
    (mapv
      (fn [[state ts]]
        {:Tilamuutos
         {:tila (application-state-to-krysp-state state)
          :pvm (date/xml-date ts)
          :kasittelija (get-handler application)}})
      state-timestamps)))

(defn lupatunnus
  ([{:keys [id submitted] :as application}]
   (lupatunnus id submitted (vif/published-kuntalupatunnus application)))
  ([id submitted backend-id]
   {:pre [id]}
   {:LupaTunnus
    (util/assoc-when-pred {:muuTunnustieto {:MuuTunnus {:tunnus id, :sovellus "Lupapiste"}}}
      util/not-empty-or-nil?
      :saapumisPvm     (date/xml-date submitted)
      :kuntalupatunnus backend-id)}))

(defn lupatunnus-with-vrktunnus [application]
  (assoc-in (lupatunnus application)
            [:LupaTunnus :VRKLupatunnus]
            (app-utils/vrk-lupatunnus application)))

(defn get-avainsanaTieto [{:keys [tags]}]
  (->> (map :label tags)
       (remove nil?)
       (map (partial hash-map :Avainsana))))

(def kuntaRoolikoodi-to-vrkRooliKoodi
  {"Rakennusvalvonta-asian hakija"  "hakija"
   "Ilmoituksen tekijä"        "hakija"
   "Hakijan asiamies"               "muu osapuoli"
   "Rakennusvalvonta-asian laskun maksaja"  "maksaja"
   "pääsuunnittelija"     "pääsuunnittelija"
   "GEO-suunnittelija"              "erityissuunnittelija"
   "LVI-suunnittelija" `            "erityissuunnittelija"
   "RAK-rakennesuunnittelija"       "erityissuunnittelija"
   "ARK-rakennussuunnittelija"      "rakennussuunnittelija"
   "KVV-työnjohtaja"           "työnjohtaja"
   "IV-työnjohtaja"            "työnjohtaja"
   "erityisalojen työnjohtaja" "työnjohtaja"
   "vastaava työnjohtaja"      "työnjohtaja"
   "työnjohtaja"               "työnjohtaja"
   "ei tiedossa"                    "ei tiedossa"
   "Rakennuksen omistaja"           "rakennuksen omistaja"
   "rakennussuunnittelija"                          "rakennussuunnittelija"
   "kantavien rakenteiden suunnittelija"            "erityissuunnittelija"
   "pohjarakenteiden suunnittelija"                 "erityissuunnittelija"
   "ilmanvaihdon suunnittelija"                     "erityissuunnittelija"
   "kiinteistön vesi- ja viemäröintilaitteiston suunnittelija"  "erityissuunnittelija"
   "rakennusfysikaalinen suunnittelija"             "erityissuunnittelija"
   "kosteusvaurion korjaustyön suunnittelija"  "erityissuunnittelija"
   :rakennuspaikanomistaja          "rakennuspaikan omistaja"
   :lupapaatoksentoimittaminen      "lupapäätöksen toimittaminen"
   :naapuri                         "naapuri"
   :lisatietojenantaja              "lisätietojen antaja"
   :muu                             "muu osapuoli"})

(def kuntaRoolikoodit
  {:paasuunnittelija       "pääsuunnittelija"
   :hakija-r               "Rakennusvalvonta-asian hakija"
   :hakija                 "Rakennusvalvonta-asian hakija"
   :ilmoittaja             "Ilmoituksen tekijä"
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

(defn get-hetu [henkilotiedot]
  (if (:not-finnish-hetu henkilotiedot)
    {:ulkomainenHenkilotunnus (:ulkomainenHenkilotunnus henkilotiedot)}
    {:henkilotunnus (:hetu henkilotiedot)}))

(defn- get-simple-yritys [{:keys [yritysnimi liikeJaYhteisoTunnus]}]
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
    (util/assoc-when-pred yritys-canonical util/not-empty-or-nil? :verkkolaskutustieto (get-verkkolaskutus yritys))))

(def- default-role "ei tiedossa")
(defn get-kuntaRooliKoodi [party party-type subtype]
  (if (contains? kuntaRoolikoodit (keyword party-type))
    (kuntaRoolikoodit (keyword party-type))
    (let [code (or (get-in party [:kuntaRoolikoodi])
                   ; Old applications have kuntaRoolikoodi under patevyys group (LUPA-771)
                   (get-in party [:patevyys :kuntaRoolikoodi])
                   (get kuntaRoolikoodit (keyword subtype) default-role))]
      (cond (ss/blank? code) default-role
            (= "other" code) "muu"
            :else            code))))

(defn get-osapuoli-data
  ([osapuoli party-type]
    (get-osapuoli-data osapuoli party-type nil))
  ([osapuoli party-type subtype]
   (let [selected-value        (or (-> osapuoli :_selected) (-> osapuoli first key))
         yritys-type-osapuoli? (= "yritys" selected-value)
         henkilo               (if yritys-type-osapuoli?
                                 (get-in osapuoli [:yritys :yhteyshenkilo])
                                 (:henkilo osapuoli))]
     (when (-> henkilo :henkilotiedot :sukunimi ss/trim not-empty)
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
                         (merge
                           {:osoite (get-simple-osoite (:osoite henkilo))
                            :vainsahkoinenAsiointiKytkin (-> henkilo :kytkimet :vainsahkoinenAsiointiKytkin true?)}
                           (get-hetu (:henkilotiedot henkilo)))))}
           (when (not-empty (:laskuviite osapuoli))
             {:laskuviite (not-empty (:laskuviite osapuoli))})
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
    (let [kuntaroolikoodi (get-kuntaRooliKoodi suunnittelija party-type subtype)
          patevyys (:patevyys suunnittelija)
          koulutus (if (= "other" (:koulutusvalinta patevyys))
                     "muu"
                     (:koulutusvalinta patevyys))
          osoite (get-simple-osoite (:osoite suunnittelija))
          henkilo (merge (get-name (:henkilotiedot suunnittelija))
                    {:osoite osoite}
                    (get-yhteystiedot-data (:yhteystiedot suunnittelija))
                    (get-hetu (:henkilotiedot suunnittelija)))]
      (merge
        {:suunnittelijaRoolikoodi kuntaroolikoodi ; Note the lower case 'koodi'
         :VRKrooliKoodi (if (= kuntaroolikoodi "muu")
                          "erityissuunnittelija"
                          (kuntaRoolikoodi-to-vrkRooliKoodi kuntaroolikoodi))
         :koulutus koulutus
         :vaadittuPatevyysluokka (:suunnittelutehtavanVaativuusluokka suunnittelija)
         :patevyysvaatimusluokka (:patevyysluokka patevyys)
         :valmistumisvuosi (:valmistumisvuosi patevyys)
         :FISEpatevyyskortti (:fise patevyys)
         :FISEkelpoisuus (:fiseKelpoisuus patevyys)
         :kokemusvuodet (:kokemus patevyys)}
        (when (and (= kuntaroolikoodi "muu") (not-empty (:muuSuunnittelijaRooli suunnittelija)))
          {:muuSuunnittelijaRooli (:muuSuunnittelijaRooli suunnittelija)})
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
  (cond-> (->> (dissoc selections :muuMika :muuMikaValue)
               (reduce (fn [acc [k v]]
                         (cond-> acc
                           v (conj (name k))))
                       [])
               (ss/join ","))
    (and (-> selections :muuMika)
         (-> selections :muuMikaValue ss/not-blank?))
    (str "," (:muuMikaValue selections))))

(defn- get-sijaistustieto [{:keys [sijaistettavaHloEtunimi sijaistettavaHloSukunimi alkamisPvm paattymisPvm]} sijaistettavaRooli]
  (when (or sijaistettavaHloEtunimi sijaistettavaHloSukunimi)
    {:Sijaistus (util/assoc-when-pred {} util/not-empty-or-nil?
                  :sijaistettavaHlo (ss/trim (str sijaistettavaHloEtunimi " " sijaistettavaHloSukunimi))
                  :sijaistettavaRooli sijaistettavaRooli
                  :alkamisPvm (date/xml-date alkamisPvm)
                  :paattymisPvm (date/xml-date paattymisPvm))}))

(defn- get-sijaistettava-hlo-214 [{:keys [sijaistettavaHloEtunimi sijaistettavaHloSukunimi]}]
  (when (or sijaistettavaHloEtunimi sijaistettavaHloSukunimi)
    (ss/trim (str sijaistettavaHloEtunimi " " sijaistettavaHloSukunimi))))

(defn- get-vastattava-tyotieto [{tyotehtavat :vastattavatTyotehtavat} lang]
  (util/strip-nils
    (when (seq tyotehtavat)
      {:vastattavaTyotieto
       (keep (fn [[k v]]
               (when (and (cond-> v
                            (string? v) ss/not-blank?)
                          (not= :muuMika k)
                          (or (not= :muuMikaValue k)
                              (:muuMika tyotehtavat)))
                 {:VastattavaTyo
                  {:vastattavaTyo
                   (if (= k :muuMikaValue)
                     v
                     (let [loc-s (i18n/localize lang (str "osapuoli.tyonjohtaja.vastattavatTyotehtavat." (name k)))]
                       (assert (not (re-matches #"^\?\?\?.*" loc-s)))
                       loc-s))}}))
             tyotehtavat)})))

(defn- foreman-tasks-by-role [{role :kuntaRoolikoodi tasks :vastattavatTyotehtavat}]
  (let [role-tasks (case role
                     "KVV-työnjohtaja"
                     [:ulkopuolinenKvvTyo :sisapuolinenKvvTyo :muuMika :muuMikaValue]

                     "IV-työnjohtaja"
                     [:ivLaitoksenAsennustyo :ivLaitoksenKorjausJaMuutostyo :muuMika :muuMikaValue]

                     "erityisalojen työnjohtaja"
                     [:muuMika :muuMikaValue]

                     ("vastaava työnjohtaja" "työnjohtaja")
                     [:rakennuksenPurkaminen :uudisrakennustyoIlmanMaanrakennustoita
                      :maanrakennustyot :uudisrakennustyoMaanrakennustoineen
                      :rakennuksenMuutosJaKorjaustyo :linjasaneeraus :muuMika :muuMikaValue]

                     ;; ei tiedossa is the default
                     [])]
    (select-keys  tasks role-tasks)))


(defn get-tyonjohtaja-data [_ lang tyonjohtaja party-type & [subtype]]
  (let [foremans          (dissoc (get-suunnittelija-data tyonjohtaja party-type subtype)
                                  :suunnittelijaRoolikoodi :FISEpatevyyskortti :FISEkelpoisuus)
        patevyys          (:patevyys-tyonjohtaja tyonjohtaja)
        ;; The mappings in backing system providers' end make us pass "muu" when "muu koulutus" is selected.
        ;; Thus cannot use just this as koulutus:
        ;; (:koulutus (muu-select-map
        ;;              :koulutus (-> patevyys :koulutus)
        ;;              :koulutus (-> patevyys :koulutusvalinta)))
        koulutus          (if (= "other" (:koulutusvalinta patevyys))
                            "muu"
                            (:koulutusvalinta patevyys))
        {:keys [alkamisPvm paattymisPvm]
         :as   sijaistus} (:sijaistus tyonjohtaja)
        rooli             (get-kuntaRooliKoodi tyonjohtaja :tyonjohtaja nil)]
    (merge
      foremans
      {:tyonjohtajaRooliKoodi      rooli
       :vastattavatTyotehtavat     (concat-tyotehtavat-to-string (:vastattavatTyotehtavat tyonjohtaja))
       :koulutus                   koulutus
       :patevyysvaatimusluokka     (:patevyysvaatimusluokka patevyys)
       :vaadittuPatevyysluokka     (:patevyysvaatimusluokka patevyys)
       :valmistumisvuosi           (:valmistumisvuosi patevyys)
       :kokemusvuodet              (:kokemusvuodet patevyys)
       :valvottavienKohteidenMaara (:valvottavienKohteidenMaara patevyys)
       :tyonjohtajaHakemusKytkin   (= "hakemus" (:tyonjohtajaHakemusKytkin patevyys))
       :sijaistustieto             (get-sijaistustieto sijaistus rooli)}
      (get-vastattava-tyotieto tyonjohtaja lang)
      (util/strip-blanks {:alkamisPvm       (date/xml-date alkamisPvm)
                          :paattymisPvm     (date/xml-date paattymisPvm)
                          :sijaistettavaHlo (get-sijaistettava-hlo-214 sijaistus)}))))

(defn get-tyonjohtaja-v2-data [application lang tyonjohtaja party-type & [subtype]]
  (let [foremans          (dissoc (get-suunnittelija-data tyonjohtaja party-type subtype)
                                  :suunnittelijaRoolikoodi :FISEpatevyyskortti :FISEkelpoisuus)
        patevyys          (:patevyys-tyonjohtaja tyonjohtaja)
        koulutus          (if (= "other" (:koulutusvalinta patevyys))
                            "muu"
                            (:koulutusvalinta patevyys))
        {:keys [alkamisPvm paattymisPvm]
         :as   sijaistus} (:sijaistus tyonjohtaja)
        rooli             (get-kuntaRooliKoodi tyonjohtaja :tyonjohtaja nil)]
    (merge
      foremans
      {:tyonjohtajaRooliKoodi      rooli
       :vastattavatTyotehtavat     (concat-tyotehtavat-to-string (:vastattavatTyotehtavat tyonjohtaja))
       :koulutus                   koulutus
       :patevyysvaatimusluokka     (:patevyysvaatimusluokka tyonjohtaja)
       :vaadittuPatevyysluokka     (:patevyysvaatimusluokka tyonjohtaja)
       :valmistumisvuosi           (:valmistumisvuosi patevyys)
       :kokemusvuodet              (:kokemusvuodet patevyys)
       :valvottavienKohteidenMaara (:valvottavienKohteidenMaara patevyys)
       :tyonjohtajaHakemusKytkin   (= "tyonjohtaja-hakemus" (:permitSubtype application))
       :sijaistustieto             (get-sijaistustieto sijaistus rooli)
       :vainTamaHankeKytkin        (:tyonjohtajanHyvaksynta (:tyonjohtajanHyvaksynta tyonjohtaja))}
      (get-vastattava-tyotieto {:vastattavatTyotehtavat (foreman-tasks-by-role tyonjohtaja)} lang)
      (util/strip-blanks {:alkamisPvm       (date/xml-date alkamisPvm)
                          :paattymisPvm     (date/xml-date paattymisPvm)
                          :sijaistettavaHlo (get-sijaistettava-hlo-214 sijaistus)}))))

(defn- get-foremen [application documents-by-type lang]
  (if (contains? documents-by-type :tyonjohtaja)
    (get-parties-by-type documents-by-type :Tyonjohtaja :tyonjohtaja (partial get-tyonjohtaja-data application lang))
    (get-parties-by-type documents-by-type :Tyonjohtaja :tyonjohtaja-v2 (partial get-tyonjohtaja-v2-data application lang))))

(defn address->osoitetieto [{katu :street postinumero :zip postitoimipaikannimi :city}]
  (when-not (util/empty-or-nil? katu)
    (util/assoc-when-pred {} util/not-empty-or-nil?
                     :osoitenimi {:teksti katu}
                     :postinumero postinumero
                     :postitoimipaikannimi postitoimipaikannimi)))

(defn get-neighbor [{status :status property-id :propertyId}]
  (let [{state :state vetuma :vetuma message :message} (last status)
        neighbor (util/assoc-when-pred {} util/not-empty-or-nil?
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

(defn osapuolet [application documents-by-type lang]
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

(defn get-building-places
  "Prepares a canonical map of the application's buildings' properties for eventual translation into KRYSP XML.
  Encumbered properties are handled by `get-encumbrance-places` below"
  [docs application]
  (for [doc docs
        :let [rakennuspaikka (:data doc)
              kiinteisto (:kiinteisto rakennuspaikka)
              id (:id doc)
              kaavatilanne (-> rakennuspaikka :kaavatilanne)
              kaavanaste (change-value-to-when (-> rakennuspaikka :kaavanaste) "eiKaavaa" "ei kaavaa")
              rakennuspaikka-map {:Rakennuspaikka
                                  {:yksilointitieto id
                                   :alkuHetki (date/xml-datetime (now))
                                   :rakennuspaikanKiinteistotieto
                                   [{:RakennuspaikanKiinteisto
                                     {:kokotilaKytkin (ss/blank? (ss/trim (:maaraalaTunnus kiinteisto)))
                                      :hallintaperuste (:hallintaperuste rakennuspaikka )
                                      :kiinteistotieto
                                      {:Kiinteisto
                                       (merge {:tilannimi (:tilanNimi kiinteisto)
                                               :kiinteistotunnus (:propertyId application)
                                               :rantaKytkin (true? (:rantaKytkin kiinteisto))}
                                              (when-let [maara-ala (format-maara-alatunnus (:maaraalaTunnus kiinteisto))]
                                                {:maaraAlaTunnus maara-ala}))}}}]}}
              kaavatieto (merge
                           (when kaavanaste {:kaavanaste kaavanaste})
                           (when kaavatilanne
                             (merge
                               {:kaavatilanne kaavatilanne}
                               (when-not kaavanaste
                                 {:kaavanaste (or (kaavatilanne-to-kaavanaste-mapping kaavatilanne) "ei tiedossa")}))))]]
    (update-in rakennuspaikka-map [:Rakennuspaikka] merge kaavatieto)))

(defn get-canonical-property-list-row
  "Returns a canonical map of a property list's single row for translation into KRYSP XML KiinteistoTyp.
  Note that owners are not included as they are optional and the XSD schema is not well equipped for them
  (e.g. no support for owners that are companies)."
  [{:keys [kiinteistotunnus tilanNimi]}]
  {:kiinteisto {:kiinteistotunnus kiinteistotunnus
                :tilannimi        tilanNimi}})

(defn get-encumbrance-places
  "Prepares a canonical map of the application's encumbered properties for eventual translation into KRYSP XML.
  Note that the actual worked property is handled by `get-building-places` above"
  [docs _]
  (for [doc docs]
    (let [xml-now           (date/xml-datetime (now))
          document-id       (:id doc)
          ;; Whether this is a rakennusrasite or yhteisjarjestely document (selected on a radio button group by user)
          doc-type-kw       (or (-> doc :data :tyyppi keyword) :rakennusrasite)
          rasite?           (= :rakennusrasite doc-type-kw)
          get-properties    #(->> (get-in doc [:data doc-type-kw %])
                                  (vals)
                                  (map get-canonical-property-list-row))
          ;; Link is mandatory in schema, even if attachments go through SFTP
          ;; the link itself is injected in `lupapalvelu.backing-system.krysp.rakennuslupa-mapping/add-non-attachment-links`
          ;; using the placeholder below so if you change it make sure to modify the other as well
          link-to-descr-att {:kuvaus            "Rakennusrasitteen tai yhteisjärjestelyn kuvaus"
                             :linkkiliitteeseen :ENCUMBRANCE_DESCRIPTION_LINK_PLACEHOLDER}
          ;; Required fields inherited from XML type AbstractPaikkatietopalveluKohde
          base-map          {:yksilointitieto document-id
                             :alkuHetki xml-now}
          add-property-map  (fn [pred? key contents RakennusPaikanKiinteisto]
                              (cond-> RakennusPaikanKiinteisto
                                pred? (assoc key contents)))]
      (->> {}
           (add-property-map rasite?
                             :rakennusrasite
                             (merge base-map
                                    {:rasitteenSisalto    link-to-descr-att
                                     :rasitettuKiinteisto (get-properties :rasitetutTontit)}))
           (add-property-map (not rasite?)
                             :yhteisjarjestely
                             (merge base-map
                                    {:yhteisjarjestelynKuvaus link-to-descr-att
                                     :muutkiinteistot         {:kiinteisto (->> (get-properties :kohdeTontit)
                                                                                (map :kiinteisto))}}))
           (assoc {} :RakennuspaikanKiinteisto)
           (vector)
           ;; oikeutetutTontit does not exist in KuntaGML so add as generic properties (agreement with Facta)
           (concat (when rasite?
                     (some->> (get-properties :oikeutetutTontit)
                              (map (fn [{oikeutettuTontti :kiinteisto}]
                                     {:RakennuspaikanKiinteisto
                                      {:kiinteistotieto {:Kiinteisto oikeutettuTontti}}})))))
           (assoc-in {:Rakennuspaikka base-map}
                     [:Rakennuspaikka :rakennuspaikanKiinteistotieto])))))

(defn get-rakennuspaikkatieto
  "Gets the building location information for the canonical model.
  Depending on the application's operations, this could be a list of the affected buildings' locations,
  the encumbered locations or both.

  If both types are included, the maps must be merged at the rakennuspaikanKiinteisto level
  since only one rakennuspaikkatieto field is allowed in the XML.

  In fact, it seems that the building-places seq only ever contains at maximum one item?
  But to be safe it is handled here as if it could contain many more"
  [documents-by-type application]
  (let [encumbrance-places (get-encumbrance-places (:rasite-tai-yhteisjarjestely documents-by-type) application)
        building-places    (get-building-places (concat (:rakennuspaikka documents-by-type)
                                                        (:rakennuspaikka-ilman-ilmoitusta documents-by-type))
                                                application)]
    (cond
      (empty? building-places)    encumbrance-places
      (empty? encumbrance-places) building-places
      ;; Merge the encumbrance information to the first (and most likely only) property since it is guaranteed to exist
      :else                       (update (vec building-places)
                                          0
                                          (fn [building-property]
                                            (apply util/deep-merge-with
                                                   (fn [a b]
                                                     (if (sequential? a)
                                                       (concat a b)
                                                       b))
                                                   building-property
                                                   encumbrance-places))))))

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
  {kt-key {:muutosHetki (date/xml-datetime (:modified application))
           :hakemuksenTila (ymp-application-state-to-krysp-state (keyword (:state application)))
           :asiatunnus (:id application)
           :paivaysPvm (date/xml-date (state-timestamp application))
           :kasittelija (get-handler application)}})

(defn get-yhteystiedot [unwrapped-party-doc]
  (if (= (-> unwrapped-party-doc :data :_selected) "yritys")
    (let [yritys (-> unwrapped-party-doc :data :yritys)
          {:keys [yhteyshenkilo]} yritys
          {:keys [etunimi sukunimi]} (:henkilotiedot yhteyshenkilo)
          {:keys [puhelin email]} (:yhteystiedot yhteyshenkilo)
          yhteyshenkilon-nimi (ss/trim (str etunimi " " sukunimi))
          osoite (get-simple-osoite (:osoite yritys))]
      (not-empty
        (util/assoc-when-pred {} util/not-empty-or-nil?
          :yTunnus (:liikeJaYhteisoTunnus yritys)
          :yrityksenNimi (:yritysnimi yritys)
          :yhteyshenkilonNimi (when-not (ss/blank? yhteyshenkilon-nimi) yhteyshenkilon-nimi)
          :osoitetieto (when (seq osoite) {:Osoite osoite})
          :puhelinnumero puhelin
          :sahkopostiosoite email
          ;:yhdistysRekisterinumero (:yhdistysRekisterinumero yritys) ;;TODO CAN BE USED WHEN yhdistysRekisterinumero is added to schema and to database
          ;; Only explicit check allows direct marketing
          :suoramarkkinointikielto (-> yhteyshenkilo :kytkimet :suoramarkkinointilupa true? not)
)))
    (when-let [henkilo (-> unwrapped-party-doc :data :henkilo)]
      (let [{:keys [henkilotiedot yhteystiedot]} henkilo
            osoite                               (get-simple-osoite (:osoite henkilo))
            [hetu-key hetu-value]                (-> (:henkilotiedot henkilo) get-hetu vec first)]
        (not-empty
          (util/assoc-when-pred {} util/not-empty-or-nil?
            hetu-key hetu-value
            :sukunimi (:sukunimi henkilotiedot)
            :etunimi (:etunimi henkilotiedot)
            :osoitetieto (when (seq osoite) {:Osoite osoite})
            :puhelinnumero (:puhelin yhteystiedot)
            :sahkopostiosoite (:email yhteystiedot)
            ;; Only explicit check allows direct marketing
            :suoramarkkinointikielto (-> henkilo :kytkimet :suoramarkkinointilupa true? not)
            )))
      )))

(defn get-maksajatiedot [unwrapped-party-doc]
  (merge
    (get-yhteystiedot unwrapped-party-doc)
    (not-empty
      (util/assoc-when-pred {} util/not-empty-or-nil?
        :laskuviite (get-in unwrapped-party-doc [:data :laskuviite])
        :verkkolaskutustieto (get-verkkolaskutus unwrapped-party-doc)))))

(defn- get-pos [coordinates]
  {:pos (map (fn [^Coordinate coord] (str (-> coord .x) " " (-> coord .y))) coordinates)})

(defn- get-basic-drawing-info [drawing]  ;; krysp Yhteiset 2.1.5+
  (util/assoc-when-pred {} util/not-empty-or-nil?
    :nimi (:name drawing)
    :kuvaus (:desc drawing)
    :korkeusTaiSyvyys (str (:height drawing))
    :pintaAla (str (:area drawing))))

(defn- parse-krysp-geometry [drawing parser-fn]
  (try
    {:Sijainti
     (merge (parser-fn (-> drawing :geometry jts/read-wkt-str))
            (get-basic-drawing-info drawing))}
    (catch IllegalArgumentException e
      (warnf "Invalid geometry: %s, message is: %s" (:geometry drawing) (.getMessage e))
      nil)))

(defn- point-drawing [drawing]
  (letfn [(point-parser
            [^Geometry geometry]
            (let [cord (.getCoordinate geometry)]
              {:piste {:Point {:pos (str (-> cord .x) " " (-> cord .y))}}}))]
    (parse-krysp-geometry drawing point-parser)))

(defn- linestring-drawing [drawing]
  (letfn [(linestring-parser [^Geometry geometry]
            {:viiva {:LineString (get-pos (.getCoordinates geometry))}})]
    (parse-krysp-geometry drawing linestring-parser)))

(defn- polygon-drawing [drawing]
  (letfn [(polygon-parser [^Geometry geometry]
            {:alue {:Polygon {:exterior {:LinearRing (get-pos (.getCoordinates geometry))}}}})]
    (parse-krysp-geometry drawing polygon-parser)))

(defn- drawing-type? [t {:keys [^String geometry]}]
  (.startsWith geometry t))

(defn drawings-as-krysp [drawings]
   (remove nil?
           (concat (map point-drawing (filter (partial drawing-type? "POINT") drawings))
                   (map linestring-drawing (filter (partial drawing-type? "LINESTRING") drawings))
                   (map polygon-drawing (filter (partial drawing-type? "POLYGON") drawings)))))


(defn get-sijaintitieto [application]
  (let [app-location-info {:Sijainti {:osoite {:yksilointitieto (:id application)
                                               :alkuHetki (date/xml-datetime (now))
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
  (let [not-finnish-hetu? (-> henkilotiedot :not-finnish-hetu)]
    (merge (entry :turvakieltoKytkin henkilotiedot :turvakieltokytkin)
           {:vainsahkoinenAsiointiKytkin (-> kytkimet :vainsahkoinenAsiointiKytkin true?)}
           {:henkilotieto {:Henkilo
                           (merge
                             (->nimi henkilotiedot)
                             {:osoite (->postiosoite-type osoite)}
                             (entry :email yhteystiedot :sahkopostiosoite)
                             (entry :puhelin yhteystiedot)
                             (if not-finnish-hetu?
                               (entry :ulkomainenHenkilotunnus henkilotiedot :ulkomainenHenkilotunnus)
                               (entry :hetu henkilotiedot :henkilotunnus)))}})))

(defmethod osapuolitieto "yritys"
  [data]
  (let [company (:yritys data)
        {contact :yhteystiedot personal :henkilotiedot} (:yhteyshenkilo company)
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
                                         ;(entry :yhdistysRekisterinumero company :yhdistysRekisterinumero) ;;TODO CAN BE USED WHEN yhdistysRekisterinumero is added to schema and to database
                                         billing-information)}})))

(defn- process-party [lang {{role :subtype} :schema-info data :data}]
  {:Osapuoli (merge
              {:roolikoodi (ss/capitalize (name role))
               :asioimiskieli lang}
               (osapuolitieto data))})

(defn process-parties [docs lang]
  (map (partial process-party lang) (schema-info-filter docs :type "party")))

(defn simple-application-state [app]
  (let [enums {:submitted "Vireillä"
               :sent "Haettu"
               :complementNeeded "Vireillä"
               :closed "Päättynyt"}
        state (-> app :state keyword)
        date (date/xml-date (state app))
        {a-first :firstName a-last :lastName} (get-general-handler app)]
    {:Tila
     (util/strip-nils
       {:pvm            date
        :kasittelija    (and a-first a-last (format "%s %s" a-first a-last))
        :hakemuksenTila (state enums)})}))

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

(defn link-permit-selector-value [doc-data link-permit-data [schema-name & link-permit-selector-path]]
  (let [valid-operations (-> (schemas/get-in-schemas schema-name link-permit-selector-path)
                             :operationsPath
                             op/operations-in)]
    (or (:id (util/find-first (comp (set valid-operations) keyword :operation) link-permit-data))
        (get-in doc-data link-permit-selector-path))))

(defmulti application->canonical {:arglists '([application lang])}
  (fn [application _] (keyword (:permitType application))))

(defmulti description
  "Returns description (hankkeen kuvaus) from canonical"
  {:arglists '([application canonical])}
  (fn [application _] (keyword (:permitType application))))

(defmulti review->canonical {:arglists '([application review options])}
  (fn [application _ _] (keyword (:permitType application))))

(defmulti review-path {:arglists '([application])}
  (fn [application] (keyword (:permitType application))))
