(ns lupapalvelu.document.yleiset-alueet-canonical
  (:require [lupapalvelu.core :refer [now]]
            [lupapalvelu.document.canonical-common :refer :all]
            [sade.util :refer :all]
            [clojure.walk :as walk]
            [sade.common-reader :as cr]
            [cljts.geom :as geo]
            [cljts.io :as jts]))

(defn- get-henkilo [henkilo]
  (let [nimi (assoc-when {}
               :etunimi (-> henkilo :henkilotiedot :etunimi :value)
               :sukunimi (-> henkilo :henkilotiedot :sukunimi :value))
        teksti (assoc-when {} :teksti (-> henkilo :osoite :katu :value))
        osoite (assoc-when {}
                 :osoitenimi teksti
                 :postinumero (-> henkilo :osoite :postinumero :value)
                 :postitoimipaikannimi (-> henkilo :osoite :postitoimipaikannimi :value))]
    (not-empty
      (assoc-when {}
        :nimi nimi
        :osoite osoite
        :sahkopostiosoite (-> henkilo :yhteystiedot :email :value)
        :puhelin (-> henkilo :yhteystiedot :puhelin :value)
        :henkilotunnus (-> henkilo :henkilotiedot :hetu :value)))))

(defn- get-yritys [yritys]
  (let [teksti (assoc-when {} :teksti (-> yritys :osoite :katu :value))
        postiosoite (not-empty
                      (assoc-when {}
                        :osoitenimi teksti
                        :postinumero (-> yritys :osoite :postinumero :value)
                        :postitoimipaikannimi (-> yritys :osoite :postitoimipaikannimi :value)))]
    (not-empty
      (assoc-when {}
        :nimi (-> yritys :yritysnimi :value)
        :liikeJaYhteisotunnus (-> yritys :liikeJaYhteisoTunnus :value)
        :postiosoitetieto (when postiosoite {:Postiosoite postiosoite})))))

(defn- get-yritys-maksaja [yritys]
  (let [teksti (assoc-when {} :teksti (-> yritys :osoite :katu :value))
        postiosoite (assoc-when {}
                      :osoitenimi teksti
                      :postinumero (-> yritys :osoite :postinumero :value)
                      :postitoimipaikannimi (-> yritys :osoite :postitoimipaikannimi :value))]
    (not-empty
      (assoc-when {}
        :nimi (-> yritys :yritysnimi :value)
        :liikeJaYhteisotunnus (-> yritys :liikeJaYhteisoTunnus :value)
        :postiosoite postiosoite))))

(defn- get-hakija [hakija-doc]
  ;; Yritys-tyyppisella hakijalla tiedot jaetaan yritystietoon ja henkilotieto,
  ;; Henkilo-tyyppisella hakijalla kaikki kulkee henkilotiedon alla.
  (let [hakija (not-empty
                 (if (= (-> hakija-doc :_selected :value) "yritys")
                   (let [yritys (get-yritys (:yritys hakija-doc))
                         henkilo (get-henkilo (-> hakija-doc :yritys :yhteyshenkilo))]
                     (when (and yritys henkilo)
                       {:yritystieto {:Yritys yritys}
                        :henkilotieto {:Henkilo henkilo}}))
                   (when-let [henkilo (get-henkilo (:henkilo hakija-doc))]
                     {:henkilotieto {:Henkilo henkilo}})))]
    (when hakija
      (merge hakija {:rooliKoodi "hakija"}))))

(defn- get-vastuuhenkilo-osoitetieto [osoite]
  (let [osoitenimi (assoc-when {} :teksti (-> osoite :katu :value))
        osoite (not-empty
                 (assoc-when {}
                   :osoitenimi osoitenimi
                   :postinumero (-> osoite :postinumero :value)
                   :postitoimipaikannimi (-> osoite :postitoimipaikannimi :value)))]
    (when osoite {:osoite osoite})))

(defn- get-vastuuhenkilo [vastuuhenkilo type roolikoodi]
  (let [content (not-empty
                  (if (= type "yritys")
                    ;; yritys-tyyppinen vastuuhenkilo
                    (assoc-when {}
                      :sukunimi (-> vastuuhenkilo :yritys :yhteyshenkilo :henkilotiedot :sukunimi :value)
                      :etunimi (-> vastuuhenkilo :yritys :yhteyshenkilo :henkilotiedot :etunimi :value)
                      :osoitetieto (get-vastuuhenkilo-osoitetieto (-> vastuuhenkilo :yritys :osoite))
                      :puhelinnumero (-> vastuuhenkilo :yritys :yhteyshenkilo :yhteystiedot :puhelin :value)
                      :sahkopostiosoite (-> vastuuhenkilo :yritys :yhteyshenkilo :yhteystiedot :email :value))
                    ;; henkilo-tyyppinen vastuuhenkilo
                    (assoc-when {}
                      :sukunimi (-> vastuuhenkilo :henkilo :henkilotiedot :sukunimi :value)
                      :etunimi (-> vastuuhenkilo :henkilo :henkilotiedot :etunimi :value)
                      :osoitetieto (get-vastuuhenkilo-osoitetieto (-> vastuuhenkilo :henkilo :osoite))
                      :puhelinnumero (-> vastuuhenkilo :henkilo :yhteystiedot :puhelin :value)
                      :sahkopostiosoite (-> vastuuhenkilo :henkilo :yhteystiedot :email :value))))]
    (when content
      (merge content {:rooliKoodi roolikoodi}))))

(defn- get-tyomaasta-vastaava [tyomaasta-vastaava]
  (let [type (-> tyomaasta-vastaava :_selected :value)
        vastuuhenkilo (get-vastuuhenkilo tyomaasta-vastaava type "lupaehdoista/ty\u00f6maasta vastaava henkil\u00f6")]
    (when vastuuhenkilo
      (merge
        {:vastuuhenkilotieto {:Vastuuhenkilo vastuuhenkilo}}
        (when (= "yritys" type)
          (when-let [yritys (get-yritys (:yritys tyomaasta-vastaava))]
            {:osapuolitieto {:Osapuoli {:yritystieto {:Yritys yritys}
                                        :rooliKoodi "ty\u00f6nsuorittaja"}}}))))))

(defn- get-maksaja [maksaja-doc]
  (let [maksaja-info (if (= (-> maksaja-doc :_selected :value) "yritys")
                       ;; yritys-tyyppinen maksaja, siirretaan yritysosa omaksi osapuolekseen
                       (let [vastuuhenkilo (get-vastuuhenkilo maksaja-doc "yritys" "maksajan vastuuhenkil\u00f6")
                             yritys-maksaja (get-yritys-maksaja (:yritys maksaja-doc))]
                         (when (and vastuuhenkilo yritys-maksaja)
                           {:vastuuhenkilotieto {:Vastuuhenkilo vastuuhenkilo}
                            :yritystieto {:Yritys yritys-maksaja}}))
                       ;; henkilo-tyyppinen maksaja
                       (when-let [henkilo (get-henkilo (:henkilo maksaja-doc))]
                         {:henkilotieto {:Henkilo henkilo}}))]
    (when maksaja-info
      (merge maksaja-info {:laskuviite (-> maksaja-doc :laskuviite :value)}))))

(defn- get-handler [application]
  (let [handler (:authority application)]
    (if (seq handler)
      {:henkilotieto {:Henkilo {:nimi {:etunimi  (:firstName handler)
                                       :sukunimi (:lastName handler)}}}}
      empty-tag)))

(defn- get-kasittelytieto [application]
  {:Kasittelytieto {:muutosHetki (to-xml-datetime (:modified application))
                    :hakemuksenTila (application-state-to-krysp-state (keyword (:state application)))
                    :asiatunnus (:id application)
                    :paivaysPvm (to-xml-date ((state-timestamps (keyword (:state application))) application))
                    :kasittelija (get-handler application)}})


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


(defn- get-sijaintitieto [application]
  (let [drawings (drawings-as-krysp (:drawings application))]
    (cons {:Sijainti {:osoite {:yksilointitieto (:id application)
                               :alkuHetki (to-xml-datetime (now))
                               :osoitenimi {:teksti (:address application)}}
                      :piste {:Point {:pos (str (:x (:location application)) " " (:y (:location application)))}}}}
      drawings)))

(defn- get-lisatietoja-sijoituskohteesta [data]
  (when-let [arvo (-> data :lisatietoja-sijoituskohteesta :value)]
    {:selitysteksti "Lis\u00e4tietoja sijoituskohteesta" :arvo arvo}))

(defn- get-sijoituksen-tarkoitus [data]
  (when-let [arvo (if (= "other" (:sijoituksen-tarkoitus data))
                    (-> data :muu-sijoituksen-tarkoitus :value)
                    (-> data :sijoituksen-tarkoitus :value))]
    {:selitysteksti "Sijoituksen tarkoitus" :arvo arvo}))

(defn- get-mainostus-alku-loppu-hetki [mainostus-viitoitus-tapahtuma]
  {:Toimintajakso {:alkuHetki (to-xml-datetime-from-string (-> mainostus-viitoitus-tapahtuma :mainostus-alkaa-pvm :value))
                   :loppuHetki (to-xml-datetime-from-string (-> mainostus-viitoitus-tapahtuma :mainostus-paattyy-pvm :value))}})

(defn- get-mainostus-viitoitus-lisatiedot [mainostus-viitoitus-tapahtuma]
  [{:LupakohtainenLisatieto
    (when-let [arvo (-> mainostus-viitoitus-tapahtuma :tapahtuman-nimi :value)]
      {:selitysteksti "Tapahtuman nimi" :arvo arvo})}
   {:LupakohtainenLisatieto
    (when-let [arvo (-> mainostus-viitoitus-tapahtuma :tapahtumapaikka :value)]
      {:selitysteksti "Tapahtumapaikka" :arvo arvo})}
   {:LupakohtainenLisatieto
    (when-let [arvo (-> mainostus-viitoitus-tapahtuma :haetaan-kausilupaa :value)]
      {:selitysteksti "Haetaan kausilupaa" :arvo arvo})}])

(defn- get-construction-ready-info [application]
  {:kayttojaksotieto {:Kayttojakso {:alkuHetki (to-xml-datetime (:started application))
                                    :loppuHetki (to-xml-datetime (:closed application))}}
   :valmistumisilmoitusPvm (to-xml-date (now))})


;; Configs

(def ^:private default-config {:hankkeen-kuvaus                                true
                               :tyoaika                                        true})

(def ^:private kayttolupa-config-plus-tyomaastavastaava
  (merge default-config {:tyomaasta-vastaava                                   true}))

(def ^:private configs-per-permit-name
  {:Kayttolupa                  default-config

   :Tyolupa                     (merge default-config
                                  {:sijoitus-lisatiedot                        true
                                   :tyomaasta-vastaava                         true
                                   :hankkeen-kuvaus-with-sijoituksen-tarkoitus true
                                   :johtoselvitysviitetieto                    true})


   :Sijoituslupa                (merge default-config
                                  {:tyoaika                                    false
                                   :dummy-alku-and-loppu-pvm                   true
                                   :sijoitus-lisatiedot                        true})

   :ya-kayttolupa-nostotyot               kayttolupa-config-plus-tyomaastavastaava
   :ya-kayttolupa-vaihtolavat             kayttolupa-config-plus-tyomaastavastaava
   :ya-kayttolupa-kattolumien-pudotustyot kayttolupa-config-plus-tyomaastavastaava
   :ya-kayttolupa-muu-liikennealuetyo     kayttolupa-config-plus-tyomaastavastaava
   :ya-kayttolupa-talon-julkisivutyot     kayttolupa-config-plus-tyomaastavastaava
   :ya-kayttolupa-talon-rakennustyot      kayttolupa-config-plus-tyomaastavastaava
   :ya-kayttolupa-muu-tyomaakaytto        kayttolupa-config-plus-tyomaastavastaava

   :ya-kayttolupa-mainostus-ja-viitoitus {:mainostus-viitoitus-tapahtuma-pvm   true
                                          :mainostus-viitoitus-lisatiedot      true}})


(defn- permits [application]
  ;;
  ;; Sijoituslupa: Maksaja, alkuPvm and loppuPvm are not filled in the application, but are requested by schema
  ;;               -> Maksaja gets Hakija's henkilotieto, AlkuPvm/LoppuPvm both get application's "modified" date.
  ;;
  (let [documents-by-type (documents-by-type-without-blanks application)
        operation-name-key (-> application :operations first :name keyword)
        permit-name-key (ya-operation-type-to-schema-name-key operation-name-key)

        config (or (configs-per-permit-name operation-name-key) (configs-per-permit-name permit-name-key))

        hakija (get-hakija (-> documents-by-type :hakija-ya first :data))
        tyoaika-doc (when (:tyoaika config)
                      (-> documents-by-type :tyoaika first :data))

        main-viit-tapahtuma-doc (-> documents-by-type :mainosten-tai-viitoitusten-sijoittaminen first :data)
        ;; If user has manually selected the mainostus/viitoitus type, the _selected key exists.
        ;; Otherwise the type is the first key in the map.
        main-viit-tapahtuma-name (when main-viit-tapahtuma-doc
                                   (or
                                     (-> main-viit-tapahtuma-doc :_selected :value)
                                     (-> main-viit-tapahtuma-doc first key)))
        main-viit-tapahtuma (when main-viit-tapahtuma-doc
                             (main-viit-tapahtuma-doc (keyword main-viit-tapahtuma-name)))

        alku-pvm (if (:dummy-alku-and-loppu-pvm config)
                   (to-xml-date (:submitted application))
                   (if (:mainostus-viitoitus-tapahtuma-pvm config)
                     (to-xml-date-from-string (-> main-viit-tapahtuma :tapahtuma-aika-alkaa-pvm :value))
                     (to-xml-date-from-string (-> tyoaika-doc :tyoaika-alkaa-pvm :value))))
        loppu-pvm (if (:dummy-alku-and-loppu-pvm config)
                    (to-xml-date (:modified application))
                    (if (:mainostus-viitoitus-tapahtuma-pvm config)
                      (to-xml-date-from-string (-> main-viit-tapahtuma :tapahtuma-aika-paattyy-pvm :value))
                      (to-xml-date-from-string (-> tyoaika-doc :tyoaika-paattyy-pvm :value))))
        maksaja (if (:dummy-maksaja config)
                  {:henkilotieto (:henkilotieto hakija) :laskuviite "0000000000"}
                  (get-maksaja (-> documents-by-type :yleiset-alueet-maksaja first :data)))
        maksajatieto (when maksaja {:Maksaja (dissoc maksaja :vastuuhenkilotieto)})
        tyomaasta-vastaava (when (:tyomaasta-vastaava config)
                             (get-tyomaasta-vastaava (-> documents-by-type :tyomaastaVastaava first :data)))
        ;; If tyomaasta-vastaava does not have :osapuolitieto, we filter the resulting nil out.
        osapuolitieto (vec (filter :Osapuoli [{:Osapuoli hakija}
                                              (:osapuolitieto tyomaasta-vastaava)]))
        ;; If tyomaasta-vastaava does not have :vastuuhenkilotieto, we filter the resulting nil out.
        vastuuhenkilotieto (when (or (:tyomaasta-vastaava config) (not (:dummy-maksaja config)))
                             (vec (filter :Vastuuhenkilo [(:vastuuhenkilotieto tyomaasta-vastaava)
                                                          (:vastuuhenkilotieto maksaja)])))
        hankkeen-kuvaus (when (:hankkeen-kuvaus config)
                          (->
                            (or
                              (:yleiset-alueet-hankkeen-kuvaus-sijoituslupa documents-by-type)
                              (:yleiset-alueet-hankkeen-kuvaus-kayttolupa documents-by-type)
                              (:yleiset-alueet-hankkeen-kuvaus-kaivulupa documents-by-type))
                            first :data))

        lupaAsianKuvaus (when (:hankkeen-kuvaus config)
                          (-> hankkeen-kuvaus :kayttotarkoitus :value))

        pinta-ala (when (:hankkeen-kuvaus config)
                    (-> hankkeen-kuvaus :varattava-pinta-ala :value))

        lupakohtainenLisatietotieto (filter #(seq (:LupakohtainenLisatieto %))
                                      (flatten
                                        (vector
                                          (when-let [erikoiskuvaus-operaatiosta (ya-operation-type-to-additional-usage-description operation-name-key)]
                                            {:LupakohtainenLisatieto {:selitysteksti "Lis\u00e4tietoja k\u00e4ytt\u00f6tarkoituksesta"
                                                                      :arvo erikoiskuvaus-operaatiosta}})
                                          (when (:sijoitus-lisatiedot config)
                                            (if (:hankkeen-kuvaus-with-sijoituksen-tarkoitus config)
                                              (let [sijoituksen-tarkoitus-doc (-> documents-by-type :yleiset-alueet-hankkeen-kuvaus-kaivulupa first :data)]
                                                [{:LupakohtainenLisatieto (get-sijoituksen-tarkoitus sijoituksen-tarkoitus-doc)}])
                                              (let [sijoituksen-tarkoitus-doc (-> documents-by-type :sijoituslupa-sijoituksen-tarkoitus first :data)]
                                                [{:LupakohtainenLisatieto (get-sijoituksen-tarkoitus sijoituksen-tarkoitus-doc)}
                                                 {:LupakohtainenLisatieto (get-lisatietoja-sijoituskohteesta sijoituksen-tarkoitus-doc)}])))
                                          (when (:mainostus-viitoitus-lisatiedot config)
                                            (get-mainostus-viitoitus-lisatiedot main-viit-tapahtuma)))))

        sijoituslupaviitetieto (when (:hankkeen-kuvaus config)
                                 (when-let [tunniste (-> hankkeen-kuvaus :sijoitusLuvanTunniste :value)]
                                   {:Sijoituslupaviite {:vaadittuKytkin false
                                                        :tunniste tunniste}}))

        johtoselvitysviitetieto (when (:johtoselvitysviitetieto config)
                                  {:Johtoselvitysviite {:vaadittuKytkin false
                                                        ;:tunniste "..."
                                                        }})

        body {permit-name-key (merge
                                {:kasittelytietotieto (get-kasittelytieto application)
                                 :luvanTunnisteTiedot (lupatunnus (:id application))
                                 :alkuPvm alku-pvm
                                 :loppuPvm loppu-pvm
                                 :sijaintitieto (get-sijaintitieto application)
                                 :pintaala pinta-ala
                                 :osapuolitieto osapuolitieto
                                 :vastuuhenkilotieto vastuuhenkilotieto
                                 :maksajatieto maksajatieto
                                 :lausuntotieto (get-statements (:statements application))
                                 :lupaAsianKuvaus lupaAsianKuvaus
                                 :lupakohtainenLisatietotieto lupakohtainenLisatietotieto
                                 :sijoituslupaviitetieto sijoituslupaviitetieto
                                 :kayttotarkoitus (ya-operation-type-to-usage-description operation-name-key)
                                 :johtoselvitysviitetieto johtoselvitysviitetieto}
                                (when (= "mainostus-tapahtuma-valinta" main-viit-tapahtuma-name)
                                  {:toimintajaksotieto (get-mainostus-alku-loppu-hetki main-viit-tapahtuma)})
                                (when (:closed application)
                                  (get-construction-ready-info application)))}]
    (cr/strip-nils body)))

(defn application-to-canonical
  "Transforms application mongodb-document to canonical model."
  [application lang]
  {:YleisetAlueet {:toimituksenTiedot (toimituksen-tiedot application lang)
                   :yleinenAlueAsiatieto (permits application)}})


(defn jatkoaika-to-canonical [application lang]
  "Transforms continuation period application mongodb-document to canonical model."
  [application lang]
  (let [documents-by-type (documents-by-type-without-blanks application)

        link-permit-data (-> application :linkPermitData first)
        ;; When operation is missing, setting kaivulupa as the operation (app created via op tree)
        operation-name-key (or (-> link-permit-data :operation keyword) :ya-katulupa-vesi-ja-viemarityot)
        permit-name-key (ya-operation-type-to-schema-name-key operation-name-key)

        config (or (configs-per-permit-name operation-name-key) (configs-per-permit-name permit-name-key))

        hakija (get-hakija (-> documents-by-type :hakija-ya first :data))
        tyoaika-doc (-> documents-by-type :tyo-aika-for-jatkoaika first :data)
        alku-pvm (if-let [tyoaika-alkaa-value (-> tyoaika-doc :tyoaika-alkaa-pvm :value)]
                   (to-xml-date-from-string tyoaika-alkaa-value)
                   (to-xml-date (:submitted application)))
        loppu-pvm (to-xml-date-from-string (-> tyoaika-doc :tyoaika-paattyy-pvm :value))
        maksaja (get-maksaja (-> documents-by-type :yleiset-alueet-maksaja first :data))
        maksajatieto (when maksaja {:Maksaja (dissoc maksaja :vastuuhenkilotieto)})
        osapuolitieto (vec (filter :Osapuoli [{:Osapuoli hakija}]))
        vastuuhenkilotieto (vec (filter :Vastuuhenkilo [(:vastuuhenkilotieto maksaja)]))
        hankkeen-kuvaus (-> documents-by-type :hankkeen-kuvaus-jatkoaika first :data :kuvaus :value)
        lisaaikatieto (when alku-pvm loppu-pvm hankkeen-kuvaus
                        {:Lisaaika {:alkuPvm alku-pvm
                                    :loppuPvm loppu-pvm
                                    :perustelu hankkeen-kuvaus}})
        johtoselvitysviitetieto (when (:johtoselvitysviitetieto config)
                                  {:Johtoselvitysviite {:vaadittuKytkin false
                                                        ;:tunniste "..."
                                                        }})]
    {:YleisetAlueet
     {:toimituksenTiedot (toimituksen-tiedot application lang)
      :yleinenAlueAsiatieto {permit-name-key
                             {:kasittelytietotieto (get-kasittelytieto application)
                              :luvanTunnisteTiedot (get-viitelupatieto link-permit-data)
                              :alkuPvm alku-pvm
                              :loppuPvm loppu-pvm
                              :sijaintitieto (get-sijaintitieto application)
                              :osapuolitieto osapuolitieto
                              :vastuuhenkilotieto vastuuhenkilotieto
                              :maksajatieto maksajatieto
                              :lisaaikatieto lisaaikatieto
                              :kayttotarkoitus (ya-operation-type-to-usage-description operation-name-key)
                              :johtoselvitysviitetieto johtoselvitysviitetieto
                              }}}}))


