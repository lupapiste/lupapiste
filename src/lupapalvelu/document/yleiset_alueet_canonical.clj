(ns lupapalvelu.document.yleiset-alueet-canonical
  (:require [lupapalvelu.core :refer [now]]
            [lupapalvelu.document.canonical-common :refer :all]
            [sade.util :as util]
            [clojure.walk :as walk]
            [sade.common-reader :as cr]))

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
  (if-let [handler (:authority application)]
    {:henkilotieto {:Henkilo {:nimi {:etunimi  (:firstName handler)
                                     :sukunimi (:lastName handler)}}}}
    empty-tag))

(defn- get-kasittelytieto [application]
  {:Kasittelytieto {:muutosHetki (to-xml-datetime (:modified application))
                    :hakemuksenTila ((keyword (:state application)) application-state-to-krysp-state)
                    :asiatunnus (:id application)
                    :paivaysPvm (to-xml-date ((state-timestamps (keyword (:state application))) application))
                    :kasittelija (get-handler application)}})

(defn- get-sijaintitieto [application]
  {:Sijainti {:osoite {:yksilointitieto (:id application)
                       :alkuHetki (to-xml-datetime (now))
                       :osoitenimi {:teksti (:address application)}}
              :piste {:Point {:pos (str (:x (:location application)) " " (:y (:location application)))}}}})

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

(defn- permits [application]
  ;;
  ;; Sijoituslupa: Maksaja, alkuPvm and loppuPvm are not filled in the application, but are requested by schema
  ;;               -> Maksaja gets Hakija's henkilotieto, AlkuPvm/LoppuPvm both get application's "modified" date.

  (let [documents-by-type (by-type (:documents application))
        operation-name-key (-> application :operations first :name keyword)
        permit-name-key (operation-name-key ya-operation-type-to-schema-name-key)

        default-config {:tyomaasta-vastaava true
                        :tyoaika true
                        :hankkeen-kuvaus true}

        configs-per-permit-name {:Tyolupa      (-> default-config
                                                 (merge {:sijoitus-lisatiedot true
                                                         :hankkeen-kuvaus-with-sijoituksen-tarkoitus true
                                                         :johtoselvitysviitetieto true}))

                                 :Kayttolupa   (dissoc default-config :tyomaasta-vastaava)

                                 :Sijoituslupa (-> default-config
                                                 (dissoc :tyomaasta-vastaava)
                                                 (dissoc :tyoaika)
                                                 (merge {:dummy-alku-pvm true
                                                         :sijoitus-lisatiedot true}))

                                 :ya-kayttolupa-mainostus-ja-viitoitus (-> default-config
                                                                         (dissoc :tyomaasta-vastaava)
                                                                         (dissoc :tyoaika)
                                                                         (dissoc :hankkeen-kuvaus)
                                                                         (merge {:mainostus-viitoitus-tapahtuma-pvm true
                                                                                 :mainostus-viitoitus-lisatiedot true}))}

        config (or (operation-name-key configs-per-permit-name) (permit-name-key configs-per-permit-name))

        hakija (get-hakija (-> documents-by-type :hakija-ya first :data))
        tyoaika-doc (when (:tyoaika config)
                      (-> documents-by-type :tyoaika first :data))
        mainostus-viitoitus-tapahtuma-doc (or
                                            (-> documents-by-type :mainosten-tai-viitoitusten-sijoittaminen first :data)
                                            {})
        mainostus-viitoitus-tapahtuma-name (-> mainostus-viitoitus-tapahtuma-doc :_selected :value)
        mainostus-viitoitus-tapahtuma (mainostus-viitoitus-tapahtuma-doc (keyword mainostus-viitoitus-tapahtuma-name))
        alku-pvm (if (:dummy-alku-pvm config)
                   (to-xml-date (:modified application))
                   (if (:mainostus-viitoitus-tapahtuma-pvm config)
                     (to-xml-date-from-string (-> mainostus-viitoitus-tapahtuma :tapahtuma-aika-alkaa-pvm :value))
                     (to-xml-date-from-string (-> tyoaika-doc :tyoaika-alkaa-pvm :value))))
        loppu-pvm (if (:dummy-alku-pvm config)
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
        hankkeen-kuvaus-key (case permit-name-key
                              :Sijoituslupa :yleiset-alueet-hankkeen-kuvaus-sijoituslupa
                              :Kayttolupa :yleiset-alueet-hankkeen-kuvaus-kayttolupa
                              :yleiset-alueet-hankkeen-kuvaus-kaivulupa)
        hankkeen-kuvaus (when (:hankkeen-kuvaus config)
                          (-> documents-by-type hankkeen-kuvaus-key first :data))
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
                                 {:Sijoituslupaviite {:vaadittuKytkin false
                                                      :tunniste (-> hankkeen-kuvaus sijoituslupaviitetieto-key :value)}})

        johtoselvitysviitetieto (when (:johtoselvitysviitetieto config)
                                  {:Johtoselvitysviite {:vaadittuKytkin false
                                                        ;:tunniste "..."
                                                        }})

        body {permit-name-key {:kasittelytietotieto (get-kasittelytieto application)
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
                               :kayttotarkoitus (operation-name-key ya-operation-type-to-usage-description)
                               :johtoselvitysviitetieto johtoselvitysviitetieto}}

        body (if (= "mainostus-tapahtuma-valinta" mainostus-viitoitus-tapahtuma-name)
               (assoc-in body [permit-name-key :toimintajaksotieto]
                 (get-mainostus-alku-loppu-hetki mainostus-viitoitus-tapahtuma))
               body)]

    (cr/strip-nils body)))

(defn application-to-canonical
  "Transforms application mongodb-document to canonical model."
  [application lang]
  (let [app (assoc application :documents
              (clojure.walk/postwalk empty-strings-to-nil (:documents application)))]
    {:YleisetAlueet {:toimituksenTiedot (toimituksen-tiedot app lang)
                     :yleinenAlueAsiatieto (permits app)}}))

