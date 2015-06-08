(ns lupapalvelu.document.yleiset-alueet-canonical
  (:require [lupapalvelu.document.canonical-common :refer :all]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [sade.util :as util]
            [sade.core :refer :all]
            [clojure.walk :as walk]))

(defn get-kasittelytieto [application]
  {:Kasittelytieto {:muutosHetki (util/to-xml-datetime (:modified application))
                    :hakemuksenTila (application-state-to-krysp-state (keyword (:state application)))
                    :asiatunnus (:id application)
                    :paivaysPvm (util/to-xml-date (state-timestamp application))
                    :kasittelija (if (domain/assigned? application)
                                   {:henkilotieto {:Henkilo {:nimi {:etunimi  (get-in application [:authority :firstName])
                                                                    :sukunimi (get-in application [:authority :lastName])}}}}
                                   empty-tag)}})

(defn- get-postiosoite [yritys]
  (let [teksti (util/assoc-when {} :teksti (-> yritys :osoite :katu))]
    (not-empty
      (util/assoc-when {}
        :osoitenimi teksti
        :postinumero (-> yritys :osoite :postinumero)
        :postitoimipaikannimi (-> yritys :osoite :postitoimipaikannimi)))))

(defn- get-yritys [yritys]
  (let [postiosoite (get-postiosoite yritys)
        yritys-basic (not-empty
                       (util/assoc-when {}
                         :nimi (-> yritys :yritysnimi)
                         :liikeJaYhteisotunnus (-> yritys :liikeJaYhteisoTunnus)))]
    (if postiosoite
      (merge
        yritys-basic
        {:postiosoite postiosoite
         :postiosoitetieto {:Postiosoite postiosoite}})
      yritys-basic)))

(defn- get-hakija [hakija-doc]
  ;; Yritys-tyyppisella hakijalla tiedot jaetaan yritystietoon ja henkilotieto,
  ;; Henkilo-tyyppisella hakijalla kaikki kulkee henkilotiedon alla.
  (let [hakija (not-empty
                 (if (= "yritys" (:_selected hakija-doc))
                   (let [yritys (util/deep-merge (:yritys (get-osapuoli-data hakija-doc :hakija ))
                                  (get-yritys (:yritys hakija-doc)))
                         henkilo (get-henkilo (-> hakija-doc :yritys :yhteyshenkilo))]
                     (when (and yritys henkilo)
                       {:Osapuoli {:yritystieto {:Yritys yritys}
                                   :henkilotieto {:Henkilo henkilo}}}))
                   (when-let [henkilo (get-henkilo (:henkilo hakija-doc))]
                     {:Osapuoli {:henkilotieto {:Henkilo henkilo}}})))]
    (when hakija
      (update-in hakija [:Osapuoli] merge {:rooliKoodi "hakija"}))))

(defn- get-vastuuhenkilo-osoitetieto [osoite]
  (let [osoitenimi (util/assoc-when {} :teksti (-> osoite :katu))
        osoite (not-empty
                 (util/assoc-when {}
                   :osoitenimi osoitenimi
                   :postinumero (-> osoite :postinumero)
                   :postitoimipaikannimi (-> osoite :postitoimipaikannimi)))]
    (when osoite {:osoite osoite})))

(defn- get-vastuuhenkilo [vastuuhenkilo type roolikoodi]
  (let [content (not-empty
                  (if (= type "yritys")
                    ;; yritys-tyyppinen vastuuhenkilo
                    (util/assoc-when {}
                      :sukunimi (-> vastuuhenkilo :yritys :yhteyshenkilo :henkilotiedot :sukunimi)
                      :etunimi (-> vastuuhenkilo :yritys :yhteyshenkilo :henkilotiedot :etunimi)
                      :osoitetieto (get-vastuuhenkilo-osoitetieto (-> vastuuhenkilo :yritys :osoite))
                      :puhelinnumero (-> vastuuhenkilo :yritys :yhteyshenkilo :yhteystiedot :puhelin)
                      :sahkopostiosoite (-> vastuuhenkilo :yritys :yhteyshenkilo :yhteystiedot :email))
                    ;; henkilo-tyyppinen vastuuhenkilo
                    (util/assoc-when {}
                      :sukunimi (-> vastuuhenkilo :henkilo :henkilotiedot :sukunimi)
                      :etunimi (-> vastuuhenkilo :henkilo :henkilotiedot :etunimi)
                      :osoitetieto (get-vastuuhenkilo-osoitetieto (-> vastuuhenkilo :henkilo :osoite))
                      :puhelinnumero (-> vastuuhenkilo :henkilo :yhteystiedot :puhelin)
                      :sahkopostiosoite (-> vastuuhenkilo :henkilo :yhteystiedot :email))))]
    (when content
      (merge content {:rooliKoodi roolikoodi}))))

(defn- get-tyomaasta-vastaava [tyomaasta-vastaava]
  (let [type (:_selected tyomaasta-vastaava)
        vastuuhenkilo (get-vastuuhenkilo tyomaasta-vastaava type "lupaehdoista/ty\u00f6maasta vastaava henkil\u00f6")]
    (when vastuuhenkilo
      (merge
        {:Vastuuhenkilo vastuuhenkilo}
        (when (= "yritys" type)
          (when-let [yritys (util/deep-merge (:yritys (get-osapuoli-data tyomaasta-vastaava :tyomaastaVastaava ))
                                  (get-yritys (:yritys tyomaasta-vastaava)))]
            {:Osapuoli {:yritystieto {:Yritys yritys}
                        :rooliKoodi "ty\u00f6nsuorittaja"}}))))))

(defn- get-yritys-and-henkilo [doc doc-type]
  (let [is-maksaja-doc (= "maksaja" doc-type)
        info (if (= (:_selected doc) "yritys")
               ;; yritys-tyyppinen hakija/maksaja, siirretaan yritysosa omaksi osapuolekseen
               (let [vastuuhenkilo-roolikoodi (if is-maksaja-doc "maksajan vastuuhenkil\u00f6" "hankkeen vastuuhenkil\u00f6")
                     vastuuhenkilo (get-vastuuhenkilo doc "yritys" vastuuhenkilo-roolikoodi)
                     yritys (util/deep-merge (:yritys (get-osapuoli-data doc :tyomaastaVastaava ))
                                        (get-yritys (:yritys doc)))]
                 (when (and vastuuhenkilo yritys)
                   {:Vastuuhenkilo vastuuhenkilo
                    :Osapuoli {:yritystieto {:Yritys yritys}}}))
               ;; henkilo-tyyppinen hakija/maksaja
               (when-let [henkilo (get-henkilo (:henkilo doc))]
                 {:Osapuoli {:henkilotieto {:Henkilo henkilo}}}))]
    (when info
      (update-in info [:Osapuoli] merge (if is-maksaja-doc
                                          {:laskuviite (-> doc :laskuviite)}
                                          {:rooliKoodi "hakija"})))))

(defn- get-mainostus-alku-loppu-hetki [mainostus-viitoitus-tapahtuma]
  {:Toimintajakso {:alkuHetki (util/to-xml-datetime-from-string (:tapahtuma-aika-alkaa-pvm mainostus-viitoitus-tapahtuma))
                   :loppuHetki (util/to-xml-datetime-from-string (:tapahtuma-aika-paattyy-pvm mainostus-viitoitus-tapahtuma))}})

(defn- get-mainostus-viitoitus-lisatiedot [mainostus-viitoitus-tapahtuma]
  [{:LupakohtainenLisatieto
    (when-let [arvo (:tapahtuman-nimi mainostus-viitoitus-tapahtuma)]
      {:selitysteksti "Tapahtuman nimi" :arvo arvo})}
   {:LupakohtainenLisatieto
    (when-let [arvo (:tapahtumapaikka mainostus-viitoitus-tapahtuma)]
      {:selitysteksti "Tapahtumapaikka" :arvo arvo})}
   {:LupakohtainenLisatieto
    (when-let [arvo (:haetaan-kausilupaa mainostus-viitoitus-tapahtuma)]
      {:selitysteksti "Haetaan kausilupaa" :arvo arvo})}])

(defn- get-construction-ready-info [application]
  {:kayttojaksotieto {:Kayttojakso {:alkuHetki (util/to-xml-datetime (:started application))
                                    :loppuHetki (util/to-xml-datetime (:closed application))}}
   :valmistumisilmoitusPvm (util/to-xml-date (now))})


;; Configs

(def- default-config {:hankkeen-kuvaus                                true
                               :tyoaika                                        true})

(def- kayttolupa-config-plus-tyomaastavastaava
  (merge default-config {:tyomaasta-vastaava                                   true}))

(def- configs-per-permit-name
  {:Kayttolupa                  default-config

   :Tyolupa                     (merge default-config
                                  {:tyomaasta-vastaava                         true
                                   :johtoselvitysviitetieto                    true})


   :Sijoituslupa                (merge default-config
                                  {:tyoaika                                    false
                                   :dummy-alku-and-loppu-pvm                   true})

   :ya-kayttolupa-nostotyot               kayttolupa-config-plus-tyomaastavastaava
   :ya-kayttolupa-vaihtolavat             kayttolupa-config-plus-tyomaastavastaava
   :ya-kayttolupa-kattolumien-pudotustyot kayttolupa-config-plus-tyomaastavastaava
   :ya-kayttolupa-muu-liikennealuetyo     kayttolupa-config-plus-tyomaastavastaava
   :ya-kayttolupa-talon-julkisivutyot     kayttolupa-config-plus-tyomaastavastaava
   :ya-kayttolupa-talon-rakennustyot      kayttolupa-config-plus-tyomaastavastaava
   :ya-kayttolupa-muu-tyomaakaytto        kayttolupa-config-plus-tyomaastavastaava

   :ya-kayttolupa-mainostus-ja-viitoitus {:mainostus-viitoitus-tapahtuma-pvm   true
                                          :mainostus-viitoitus-lisatiedot      true}})


(defn- get-luvan-tunniste-tiedot [application]
  (let [base-id (update-in (lupatunnus application) [:LupaTunnus :muuTunnustieto] vector)
        link-permits (map (fn [{id :id}] {:MuuTunnus {:tunnus id, :sovellus "Viitelupa"}}) (:linkPermitData application))
        base-with-link (update-in base-id [:LupaTunnus :muuTunnustieto] #(into % link-permits))
        kuntalupatunnus-link (some #(if (= (:type %) "kuntalupatunnus") {:kuntalupatunnus (:id %)}) (:linkPermitData application))]
    (if kuntalupatunnus-link
      (update-in base-with-link [:LupaTunnus] #(into % kuntalupatunnus-link))
      base-with-link)))

(defn- permits [application]
  ;;
  ;; Sijoituslupa: Maksaja, alkuPvm and loppuPvm are not filled in the application, but are requested by schema
  ;;               -> Maksaja gets Hakija's henkilotieto, AlkuPvm/LoppuPvm both get application's "modified" date.
  ;;
  (let [application (tools/unwrapped application)
        documents-by-type (documents-by-type-without-blanks application)

        operation-name-key (-> application :primaryOperation :name keyword)
        permit-name-key (ya-operation-type-to-schema-name-key operation-name-key)

        config (or (configs-per-permit-name operation-name-key) (configs-per-permit-name permit-name-key))

;        hakija (get-yritys-and-henkilo (-> documents-by-type :hakija-ya first :data) "hakija")
        hakija (get-hakija (-> documents-by-type :hakija-ya first :data))

        tyoaika-doc (when (:tyoaika config)
                      (-> documents-by-type :tyoaika first :data))

        main-viit-tapahtuma-doc (-> documents-by-type :mainosten-tai-viitoitusten-sijoittaminen first :data)
        ;; If user has manually selected the mainostus/viitoitus type, the _selected key exists.
        ;; Otherwise the type is the first key in the map.
        main-viit-tapahtuma-name (when main-viit-tapahtuma-doc
                                   (or
                                     (-> main-viit-tapahtuma-doc :_selected keyword)
                                     (-> main-viit-tapahtuma-doc first key)))
        main-viit-tapahtuma (when main-viit-tapahtuma-doc
                             (main-viit-tapahtuma-doc main-viit-tapahtuma-name))

        ;; Note: Agreed with Vianova 5.3.2014 that:
        ;;       Mainostuslupa's mainostusaika is put into alku-pvm and loppu-pvm, and tapahtuma-aika into toimintajaksotieto.
        ;;       On the contrary, Viitoituslupa's tapahtuma-aika is put into alku-pvm and loppu-pvm.
        alku-pvm (if (:dummy-alku-and-loppu-pvm config)
                   (util/to-xml-date (:submitted application))
                   (if (:mainostus-viitoitus-tapahtuma-pvm config)
                     (or
                       (util/to-xml-date-from-string (-> main-viit-tapahtuma :mainostus-alkaa-pvm))
                       (util/to-xml-date-from-string (-> main-viit-tapahtuma :tapahtuma-aika-alkaa-pvm)))
                     (util/to-xml-date-from-string (-> tyoaika-doc :tyoaika-alkaa-pvm))))
        loppu-pvm (if (:dummy-alku-and-loppu-pvm config)
                    (util/to-xml-date (:modified application))
                    (if (:mainostus-viitoitus-tapahtuma-pvm config)
                      (or
                        (util/to-xml-date-from-string (-> main-viit-tapahtuma :mainostus-paattyy-pvm))
                        (util/to-xml-date-from-string (-> main-viit-tapahtuma :tapahtuma-aika-paattyy-pvm)))
                      (util/to-xml-date-from-string (-> tyoaika-doc :tyoaika-paattyy-pvm))))
        maksaja (get-yritys-and-henkilo (-> documents-by-type :yleiset-alueet-maksaja first :data) "maksaja")
        maksajatieto-2-1-3 (get-maksajatiedot (-> documents-by-type :yleiset-alueet-maksaja first))
        maksajatieto (when maksaja {:Maksaja (util/deep-merge maksajatieto-2-1-3 (:Osapuoli maksaja))})
        tyomaasta-vastaava (when (:tyomaasta-vastaava config)
                             (get-tyomaasta-vastaava (-> documents-by-type :tyomaastaVastaava first :data)))
        ;; If tyomaasta-vastaava does not have :osapuolitieto, we filter the resulting nil out.
        osapuolitieto (vec (filter :Osapuoli [hakija
                                              tyomaasta-vastaava]))
        ;; If tyomaasta-vastaava does not have :vastuuhenkilotieto, we filter the resulting nil out.
        vastuuhenkilotieto (when (or (:tyomaasta-vastaava config) (not (:dummy-maksaja config)))
                             (vec (filter :Vastuuhenkilo [;hakija
                                                          tyomaasta-vastaava
                                                          maksaja])))
        hankkeen-kuvaus (when (:hankkeen-kuvaus config)
                          (->
                            (or
                              (:yleiset-alueet-hankkeen-kuvaus-sijoituslupa documents-by-type)
                              (:yleiset-alueet-hankkeen-kuvaus-kayttolupa documents-by-type)
                              (:yleiset-alueet-hankkeen-kuvaus-kaivulupa documents-by-type))
                            first :data))

        lupaAsianKuvaus (when (:hankkeen-kuvaus config)
                          (-> hankkeen-kuvaus :kayttotarkoitus))

        pinta-ala (when (:hankkeen-kuvaus config)
                    (-> hankkeen-kuvaus :varattava-pinta-ala))

        lupakohtainenLisatietotieto (filter #(seq (:LupakohtainenLisatieto %))
                                      (flatten
                                        (vector
                                          (when-let [erikoiskuvaus-operaatiosta (ya-operation-type-to-additional-usage-description operation-name-key)]
                                            {:LupakohtainenLisatieto {:selitysteksti "Lis\u00e4tietoja k\u00e4ytt\u00f6tarkoituksesta"
                                                                      :arvo erikoiskuvaus-operaatiosta}})
                                          (when (:mainostus-viitoitus-lisatiedot config)
                                            (get-mainostus-viitoitus-lisatiedot main-viit-tapahtuma)))))

        sijoituslupaviitetieto (when (:hankkeen-kuvaus config)
                                 (when-let [tunniste (-> hankkeen-kuvaus :sijoitusLuvanTunniste)]
                                   {:Sijoituslupaviite {:vaadittuKytkin false
                                                        :tunniste tunniste}}))

        johtoselvitysviitetieto (when (:johtoselvitysviitetieto config)
                                  {:Johtoselvitysviite {:vaadittuKytkin false}})

        body {permit-name-key (merge
                                {:kasittelytietotieto (get-kasittelytieto application)
                                 :luvanTunnisteTiedot (get-luvan-tunniste-tiedot application)
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
                                (when (and main-viit-tapahtuma (= "mainostus-tapahtuma-valinta" (name main-viit-tapahtuma-name)))
                                  {:toimintajaksotieto (get-mainostus-alku-loppu-hetki main-viit-tapahtuma)})
                                (when (:closed application)
                                  (get-construction-ready-info application)))}]
    (util/strip-nils body)))

(defn application-to-canonical
  "Transforms application mongodb-document to canonical model."
  [application lang]
  {:YleisetAlueet {:toimituksenTiedot (toimituksen-tiedot application lang)
                   :yleinenAlueAsiatieto (permits application)}})


(defn jatkoaika-to-canonical [application lang]
  "Transforms continuation period application mongodb-document to canonical model."
  [application lang]
  (let [application (tools/unwrapped application)
        documents-by-type (documents-by-type-without-blanks application)

        link-permit-data (-> application :linkPermitData first)

        ;; When operation is missing, setting kaivulupa as the operation (app created via op tree)
        operation-name-key (or (-> link-permit-data :operation keyword) :ya-katulupa-vesi-ja-viemarityot)
        permit-name-key (ya-operation-type-to-schema-name-key operation-name-key)

        config (or (configs-per-permit-name operation-name-key) (configs-per-permit-name permit-name-key))

;        hakija (get-yritys-and-henkilo (-> documents-by-type :hakija-ya first :data) "hakija")
        hakija (get-hakija (-> documents-by-type :hakija-ya first :data))

        tyoaika-doc (-> documents-by-type :tyo-aika-for-jatkoaika first :data)
        alku-pvm (if-let [tyoaika-alkaa-value (-> tyoaika-doc :tyoaika-alkaa-pvm)]
                   (util/to-xml-date-from-string tyoaika-alkaa-value)
                   (util/to-xml-date (:submitted application)))
        loppu-pvm (util/to-xml-date-from-string (-> tyoaika-doc :tyoaika-paattyy-pvm))
        maksaja (get-yritys-and-henkilo (-> documents-by-type :yleiset-alueet-maksaja first :data) "maksaja")
        maksajatieto-2-1-3 (get-maksajatiedot (-> documents-by-type :yleiset-alueet-maksaja first))
        maksajatieto (when maksaja {:Maksaja (util/deep-merge maksajatieto-2-1-3 (:Osapuoli maksaja))})
        osapuolitieto (vec (filter :Osapuoli [hakija]))
        vastuuhenkilotieto (vec (filter :Vastuuhenkilo [;hakija
                                                        maksaja]))
        hankkeen-kuvaus (-> documents-by-type :hankkeen-kuvaus-jatkoaika first :data :kuvaus)
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
                              :luvanTunnisteTiedot (get-luvan-tunniste-tiedot application)
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


