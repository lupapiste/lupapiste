(ns lupapalvelu.document.yleiset-alueet-canonical
  (:require [lupapalvelu.core :refer [now]]
            [lupapalvelu.document.canonical-common :refer :all]
            [sade.util :refer :all]
            [clojure.walk :as walk]
            [sade.common-reader :as cr]
            [cljts.geom :as geo]
            [cljts.io :as jts]))

(defn- get-henkilo [henkilo]
  {:nimi {:etunimi (-> henkilo :henkilotiedot :etunimi :value)
          :sukunimi (-> henkilo :henkilotiedot :sukunimi :value)}
   :osoite {:osoitenimi {:teksti (-> henkilo :osoite :katu :value)}
            :postinumero (-> henkilo :osoite :postinumero :value)
            :postitoimipaikannimi (-> henkilo :osoite :postitoimipaikannimi :value)}
   :sahkopostiosoite (-> henkilo :yhteystiedot :email :value)
   :puhelin (-> henkilo :yhteystiedot :puhelin :value)
   :henkilotunnus (-> henkilo :henkilotiedot :hetu :value)})

(defn- get-henkilo-reduced [henkilo]
  (dissoc (get-henkilo henkilo) :osoite :henkilotunnus))

(defn- get-yritys [yritys]
  (merge
    {:nimi (-> yritys :yritysnimi :value)
     :liikeJaYhteisotunnus (-> yritys :liikeJaYhteisoTunnus :value)
     :postiosoitetieto {:Postiosoite {:osoitenimi {:teksti (-> yritys :osoite :katu :value)}
                                      :postinumero (-> yritys :osoite :postinumero :value)
                                      :postitoimipaikannimi (-> yritys :osoite :postitoimipaikannimi :value)}}}))

(defn- get-yritys-maksaja [yritys]
  (merge
    {:nimi (-> yritys :yritysnimi :value)
     :liikeJaYhteisotunnus (-> yritys :liikeJaYhteisoTunnus :value)
     :postiosoite {:osoitenimi {:teksti (-> yritys :osoite :katu :value)}
                   :postinumero (-> yritys :osoite :postinumero :value)
                   :postitoimipaikannimi (-> yritys :osoite :postitoimipaikannimi :value)}}))

(defn- get-hakija [hakija-doc]
  ;; Yritys-tyyppisella hakijalla tiedot jaetaan yritystietoon ja henkilotieto,
  ;; Henkilo-tyyppisella hakijalla kaikki kulkee henkilotiedon alla.
  (merge
    {:rooliKoodi "hakija"}
    (if (= (-> hakija-doc :_selected :value) "yritys")
      {:yritystieto  {:Yritys (get-yritys (:yritys hakija-doc))}
       :henkilotieto {:Henkilo (get-henkilo-reduced (-> hakija-doc :yritys :yhteyshenkilo))}}
      {:henkilotieto {:Henkilo (get-henkilo (:henkilo hakija-doc))}})))

(defn- get-vastuuhenkilo-osoitetieto [osoite]
  {:osoite {:osoitenimi {:teksti (-> osoite :katu :value)}
            :postinumero (-> osoite :postinumero :value)
            :postitoimipaikannimi (-> osoite :postitoimipaikannimi :value)}})

(defn- get-vastuuhenkilo [vastuuhenkilo type roolikoodi]
  (merge
    {:rooliKoodi roolikoodi}
    (if (= type :yritys)
      ;; yritys-tyyppinen vastuuhenkilo
      {:sukunimi (-> vastuuhenkilo :yritys :yhteyshenkilo :henkilotiedot :sukunimi :value)
       :etunimi (-> vastuuhenkilo :yritys :yhteyshenkilo :henkilotiedot :etunimi :value)
       :osoitetieto (get-vastuuhenkilo-osoitetieto (-> vastuuhenkilo :yritys :osoite))
       :puhelinnumero (-> vastuuhenkilo :yritys :yhteyshenkilo :yhteystiedot :puhelin :value)
       :sahkopostiosoite (-> vastuuhenkilo :yritys :yhteyshenkilo :yhteystiedot :email :value)}
      ;; henkilo-tyyppinen vastuuhenkilo
      {:sukunimi (-> vastuuhenkilo :henkilo :henkilotiedot :sukunimi :value)
       :etunimi (-> vastuuhenkilo :henkilo :henkilotiedot :etunimi :value)
       :osoitetieto (get-vastuuhenkilo-osoitetieto (-> vastuuhenkilo :henkilo :osoite))
       :puhelinnumero (-> vastuuhenkilo :henkilo :yhteystiedot :puhelin :value)
       :sahkopostiosoite (-> vastuuhenkilo :henkilo :yhteystiedot :email :value)})))

(defn- get-tyomaasta-vastaava [tyomaasta-vastaava]
  (if (= (-> tyomaasta-vastaava :_selected :value) "yritys")
    ;; yritys-tyyppinen tyomaasta-vastaava, siirretaan yritysosa omaksi osapuolekseen
    {:osapuolitieto {:Osapuoli {:yritystieto {:Yritys (get-yritys (:yritys tyomaasta-vastaava))}
                                :rooliKoodi "ty\u00f6nsuorittaja"}}
     :vastuuhenkilotieto {:Vastuuhenkilo (get-vastuuhenkilo
                                           tyomaasta-vastaava
                                           :yritys
                                           "lupaehdoista/ty\u00f6maasta vastaava henkil\u00f6")}}
    ;; henkilo-tyyppinen tyomaasta-vastaava
    {:vastuuhenkilotieto {:Vastuuhenkilo (get-vastuuhenkilo
                                           tyomaasta-vastaava
                                           :henkilo
                                           "lupaehdoista/ty\u00f6maasta vastaava henkil\u00f6")}}))

(defn- get-maksaja [maksaja-doc]
  (merge
    (if (= (-> maksaja-doc :_selected :value) "yritys")
      ;; yritys-tyyppinen maksaja, siirretaan yritysosa omaksi osapuolekseen
      {:vastuuhenkilotieto {:Vastuuhenkilo (get-vastuuhenkilo               ;; vastuuhenkilotieto
                                             maksaja-doc
                                             :yritys
                                             "maksajan vastuuhenkil\u00f6")}
       :yritystieto {:Yritys (get-yritys-maksaja (:yritys maksaja-doc))}}    ;; maksajatieto
      ;; henkilo-tyyppinen maksaja
      {:henkilotieto {:Henkilo (get-henkilo (:henkilo maksaja-doc))}})       ;; maksajatieto
    {:laskuviite (-> maksaja-doc :laskuviite :value)}))

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

(def ^:private configs-per-permit-name
  {:Kayttolupa                  {:hankkeen-kuvaus                            true
                                 :tyoaika                                    true}

   :Tyolupa                     {:hankkeen-kuvaus                            true
                                 :sijoitus-lisatiedot                        true
                                 :tyoaika                                    true
                                 :tyomaasta-vastaava                         true
                                 :hankkeen-kuvaus-with-sijoituksen-tarkoitus true
                                 :johtoselvitysviitetieto                    true}


   :Sijoituslupa                {:hankkeen-kuvaus                            true
                                 :dummy-alku-and-loppu-pvm                   true
                                 :sijoitus-lisatiedot                        true}

   :ya-kayttolupa-mainostus-ja-viitoitus {:hankkeen-kuvaus                   false
                                          :mainostus-viitoitus-tapahtuma-pvm true
                                          :mainostus-viitoitus-lisatiedot    true}})


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
        mainostus-viitoitus-tapahtuma-doc (or
                                            (-> documents-by-type :mainosten-tai-viitoitusten-sijoittaminen first :data)
                                            {})
        mainostus-viitoitus-tapahtuma-name (-> mainostus-viitoitus-tapahtuma-doc :_selected :value)
        mainostus-viitoitus-tapahtuma (mainostus-viitoitus-tapahtuma-doc (keyword mainostus-viitoitus-tapahtuma-name))
        alku-pvm (if (:dummy-alku-and-loppu-pvm config)
                   (to-xml-date (:submitted application))
                   (if (:mainostus-viitoitus-tapahtuma-pvm config)
                     (to-xml-date-from-string (-> mainostus-viitoitus-tapahtuma :tapahtuma-aika-alkaa-pvm :value))
                     (to-xml-date-from-string (-> tyoaika-doc :tyoaika-alkaa-pvm :value))))
        loppu-pvm (if (:dummy-alku-and-loppu-pvm config)
                    (to-xml-date (:modified application))
                    (if (:mainostus-viitoitus-tapahtuma-pvm config)
                      (to-xml-date-from-string (-> mainostus-viitoitus-tapahtuma :tapahtuma-aika-paattyy-pvm :value))
                      (to-xml-date-from-string (-> tyoaika-doc :tyoaika-paattyy-pvm :value))))
        maksaja (if (:dummy-maksaja config)
                  {:henkilotieto (:henkilotieto hakija) :laskuviite "0000000000"}
                  (get-maksaja (-> documents-by-type :yleiset-alueet-maksaja first :data)))
        tyomaasta-vastaava (when (:tyomaasta-vastaava config)
                             (get-tyomaasta-vastaava (-> documents-by-type :tyomaastaVastaava first :data)))
        ;; If tyomaasta-vastaava does not have :osapuolitieto, we filter the resulting nil out.
        osapuolitieto (into [] (filter :Osapuoli [{:Osapuoli hakija}
                                                  (:osapuolitieto tyomaasta-vastaava)]))
        ;; If tyomaasta-vastaava does not have :vastuuhenkilotieto, we filter the resulting nil out.
        vastuuhenkilotieto (when (or (:tyomaasta-vastaava config) (not (:dummy-maksaja config)))
                             (into [] (filter :Vastuuhenkilo [(:vastuuhenkilotieto tyomaasta-vastaava)
                                                              (:vastuuhenkilotieto maksaja)])))
        hankkeen-kuvaus (when (:hankkeen-kuvaus config)
                          (->
                            (or
                              (:yleiset-alueet-hankkeen-kuvaus-sijoituslupa documents-by-type)
                              (:yleiset-alueet-hankkeen-kuvaus-kayttolupa documents-by-type)
;                              (:yleiset-alueet-hankkeen-kuvaus-kaivulupa-with-sijoitusluvantunniste documents-by-type)
                              (:yleiset-alueet-hankkeen-kuvaus-kaivulupa documents-by-type))
                            first :data))

        lupaAsianKuvaus (when (:hankkeen-kuvaus config)
                          (-> hankkeen-kuvaus :kayttotarkoitus :value))

        lupakohtainenLisatietotieto (filter #(seq (:LupakohtainenLisatieto %))
                                      (if (:sijoitus-lisatiedot config)
                                        (if (:hankkeen-kuvaus-with-sijoituksen-tarkoitus config)
                                          (let [sijoituksen-tarkoitus-doc (-> documents-by-type :yleiset-alueet-hankkeen-kuvaus-kaivulupa first :data)]
                                            [{:LupakohtainenLisatieto (get-sijoituksen-tarkoitus sijoituksen-tarkoitus-doc)}])
                                          (let [sijoituksen-tarkoitus-doc (-> documents-by-type :sijoituslupa-sijoituksen-tarkoitus first :data)]
                                            [{:LupakohtainenLisatieto (get-sijoituksen-tarkoitus sijoituksen-tarkoitus-doc)}
                                             {:LupakohtainenLisatieto (get-lisatietoja-sijoituskohteesta sijoituksen-tarkoitus-doc)}]))
                                        (when (:mainostus-viitoitus-lisatiedot config)
                                          (get-mainostus-viitoitus-lisatiedot mainostus-viitoitus-tapahtuma))))

        sijoituslupaviitetieto-key (if (= permit-name-key :Sijoituslupa)
                                     :kaivuLuvanTunniste
                                     :sijoitusLuvanTunniste)
        sijoituslupaviitetieto (when (:hankkeen-kuvaus config)
                                 (when-let [tunniste (-> hankkeen-kuvaus sijoituslupaviitetieto-key :value)]
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
                                 :osapuolitieto osapuolitieto
                                 :vastuuhenkilotieto vastuuhenkilotieto
                                 :maksajatieto {:Maksaja (dissoc maksaja :vastuuhenkilotieto)}
                                 :lausuntotieto (get-statements (:statements application))
                                 :lupaAsianKuvaus lupaAsianKuvaus
                                 :lupakohtainenLisatietotieto lupakohtainenLisatietotieto
                                 :sijoituslupaviitetieto sijoituslupaviitetieto
                                 :kayttotarkoitus (ya-operation-type-to-usage-description operation-name-key)
                                 :johtoselvitysviitetieto johtoselvitysviitetieto}
                                (when (= "mainostus-tapahtuma-valinta" mainostus-viitoitus-tapahtuma-name)
                                  {:toimintajaksotieto (get-mainostus-alku-loppu-hetki mainostus-viitoitus-tapahtuma)})
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
        osapuolitieto (into [] (filter :Osapuoli [{:Osapuoli hakija}]))
        vastuuhenkilotieto (into [] (filter :Vastuuhenkilo [(:vastuuhenkilotieto maksaja)]))
        hankkeen-kuvaus (-> documents-by-type :hankkeen-kuvaus-jatkoaika first :data :kuvaus :value)
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
                              :maksajatieto {:Maksaja (dissoc maksaja :vastuuhenkilotieto)}
                              :lisaaikatieto {:Lisaaika {:alkuPvm alku-pvm
                                                         :loppuPvm loppu-pvm
                                                         :perustelu hankkeen-kuvaus}}
                              :kayttotarkoitus (ya-operation-type-to-usage-description operation-name-key)
                              :johtoselvitysviitetieto johtoselvitysviitetieto
                              }}}}))


