(ns lupapalvelu.document.yleiset-alueet-canonical
  (:require [lupapalvelu.document.canonical-common :refer :all]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [sade.util :as util]
            [sade.strings :as ss]
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
  (let [teksti (util/assoc-when-pred {} util/not-empty-or-nil? :teksti (-> yritys :osoite :katu))]
    (not-empty
      (util/assoc-when-pred {} util/not-empty-or-nil?
        :osoitenimi teksti
        :postinumero (-> yritys :osoite :postinumero)
        :postitoimipaikannimi (-> yritys :osoite :postitoimipaikannimi)))))

(defn- get-yritys [yritys]
  (let [postiosoite (get-simple-osoite (:osoite yritys));(get-postiosoite yritys)
        yritys-basic (not-empty
                       (util/assoc-when-pred {} util/not-empty-or-nil?
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
  (let [osoite (get-simple-osoite osoite)
        ;; osoitenimi (util/assoc-when-pred {} util/not-empty-or-nil? :teksti (-> osoite :katu))
        ;; osoite (not-empty
        ;;          (util/assoc-when-pred {} util/not-empty-or-nil?
        ;;            :osoitenimi osoitenimi
        ;;            :postinumero (-> osoite :postinumero)
        ;;            :postitoimipaikannimi (-> osoite :postitoimipaikannimi)))
        ]
    (when osoite {:osoite osoite})))

(defn- get-vastuuhenkilo [vastuuhenkilo type roolikoodi]
  (let [content (not-empty
                  (if (= type "yritys")
                    ;; yritys-tyyppinen vastuuhenkilo
                    (util/assoc-when-pred {} util/not-empty-or-nil?
                      :sukunimi (-> vastuuhenkilo :yritys :yhteyshenkilo :henkilotiedot :sukunimi)
                      :etunimi (-> vastuuhenkilo :yritys :yhteyshenkilo :henkilotiedot :etunimi)
                      :osoitetieto (get-vastuuhenkilo-osoitetieto (-> vastuuhenkilo :yritys :osoite))
                      :puhelinnumero (-> vastuuhenkilo :yritys :yhteyshenkilo :yhteystiedot :puhelin)
                      :sahkopostiosoite (-> vastuuhenkilo :yritys :yhteyshenkilo :yhteystiedot :email))
                    ;; henkilo-tyyppinen vastuuhenkilo
                    (util/assoc-when-pred {} util/not-empty-or-nil?
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

(def- default-config {:hankkeen-kuvaus true
                      :tyoaika         true})

(def- kayttolupa-config-plus-tyomaastavastaava
  (merge default-config {:tyomaasta-vastaava true}))

(def- configs-per-permit-name
  {:Kayttolupa                            default-config
   :Tyolupa                               (merge default-config
                                            {:tyomaasta-vastaava true :johtoselvitysviitetieto true :sijoituslupa-link true})
   :Sijoituslupa                          (merge default-config
                                            {:tyoaika false :dummy-alku-and-loppu-pvm true})
   :ya-kayttolupa-nostotyot               kayttolupa-config-plus-tyomaastavastaava
   :ya-kayttolupa-vaihtolavat             kayttolupa-config-plus-tyomaastavastaava
   :ya-kayttolupa-kattolumien-pudotustyot kayttolupa-config-plus-tyomaastavastaava
   :ya-kayttolupa-muu-liikennealuetyo     kayttolupa-config-plus-tyomaastavastaava
   :ya-kayttolupa-talon-julkisivutyot     kayttolupa-config-plus-tyomaastavastaava
   :ya-kayttolupa-talon-rakennustyot      kayttolupa-config-plus-tyomaastavastaava
   :ya-kayttolupa-muu-tyomaakaytto        kayttolupa-config-plus-tyomaastavastaava
   :ya-kayttolupa-mainostus-ja-viitoitus  {:mainostus-viitoitus-tapahtuma-pvm   true
                                           :mainostus-viitoitus-lisatiedot      true}})

(defn- get-luvan-tunniste-tiedot [application]
  (let [link-permits (map (fn [{id :id}] {:MuuTunnus {:tunnus id, :sovellus "Viitelupa"}})
                          (:linkPermitData application))
        base-id (lupatunnus application)]
    (update-in base-id [:LupaTunnus :muuTunnustieto] #(into (vector %) link-permits))))

(defn- add-kuntalupatunnus-link-in-luvan-tunniste-tiedot [base-with-link application]
  (let [kuntalupatunnus-link (some #(when (= (:type %) "kuntalupatunnus") {:kuntalupatunnus (:id %)})
                                   (:linkPermitData application))]
    (if kuntalupatunnus-link
      (update-in base-with-link [:LupaTunnus] #(into % kuntalupatunnus-link))
      base-with-link)))

(defn- get-main-viit-tapahtuma-info [documents-by-type]
  (let [;; If user has manually selected the mainostus/viitoitus type, the _selected key exists.
        ;; Otherwise the type is the first key in the map.
        main-viit-tapahtuma-doc (-> documents-by-type :mainosten-tai-viitoitusten-sijoittaminen first :data)
        main-viit-tapahtuma-name (or
                                   (-> main-viit-tapahtuma-doc :_selected keyword)
                                   (some-> main-viit-tapahtuma-doc first key))]
    (select-keys main-viit-tapahtuma-doc [main-viit-tapahtuma-name])))

;; Note: Agreed with Vianova 5.3.2014 that:
;;       Mainostuslupa's mainostusaika is put into alku-pvm and loppu-pvm, and tapahtuma-aika into toimintajaksotieto.
;;       On the contrary, Viitoituslupa's tapahtuma-aika is put into alku-pvm and loppu-pvm.
(defn- get-alku-loppu-pvm [application documents-by-type config]
  (let [main-viit-tapahtuma (-> documents-by-type get-main-viit-tapahtuma-info vals first)
        tyoaika-doc (when (:tyoaika config) (-> (util/some-key documents-by-type :tyoaika :tyo-aika-for-jatkoaika) first :data))
        alku-pvm (if (:dummy-alku-and-loppu-pvm config)
                   (util/to-xml-date (:submitted application))
                   (if (:mainostus-viitoitus-tapahtuma-pvm config)
                     (util/to-xml-date-from-string (util/some-key main-viit-tapahtuma :mainostus-alkaa-pvm :tapahtuma-aika-alkaa-pvm))
                     (util/to-xml-date-from-string (:tyoaika-alkaa-pvm tyoaika-doc))))
        loppu-pvm (if (:dummy-alku-and-loppu-pvm config)
                    (util/to-xml-date (:modified application))
                    (if (:mainostus-viitoitus-tapahtuma-pvm config)
                      (util/to-xml-date-from-string (util/some-key main-viit-tapahtuma :mainostus-paattyy-pvm :tapahtuma-aika-paattyy-pvm))
                      (util/to-xml-date-from-string (:tyoaika-paattyy-pvm tyoaika-doc))))]
    [alku-pvm loppu-pvm]))

(defn- get-canonical-body [application operation-name-key config documents-by-type]
  ;;
  ;; Sijoituslupa: Maksaja, alkuPvm and loppuPvm are not filled in the application, but are requested by schema
  ;;               -> Maksaja gets Hakija's henkilotieto, AlkuPvm/LoppuPvm both get application's "modified" date.
  ;;
  (let [[alku-pvm loppu-pvm] (get-alku-loppu-pvm application documents-by-type config)
        hakija (get-hakija (-> documents-by-type :hakija-ya first :data))
        maksaja (get-yritys-and-henkilo (-> documents-by-type :yleiset-alueet-maksaja first :data) "maksaja")
        maksajatieto-2-1-3 (get-maksajatiedot (-> documents-by-type :yleiset-alueet-maksaja first))
        maksajatieto (when maksaja {:Maksaja (util/deep-merge maksajatieto-2-1-3 (:Osapuoli maksaja))})
        tyomaasta-vastaava (when (:tyomaasta-vastaava config)
                             (get-tyomaasta-vastaava (-> documents-by-type :tyomaastaVastaava first :data)))
        ;; If tyomaasta-vastaava does not have :osapuolitieto, we filter out the resulting nil.
        osapuolitieto (vec (filter :Osapuoli [hakija
                                              tyomaasta-vastaava]))
        ;; If tyomaasta-vastaava does not have :vastuuhenkilotieto, we filter the resulting nil out.
        vastuuhenkilotieto (when (or (:tyomaasta-vastaava config) (not (:dummy-maksaja config)))
                             (vec (filter :Vastuuhenkilo [tyomaasta-vastaava
                                                          maksaja])))
        johtoselvitysviitetieto (when (:johtoselvitysviitetieto config)
                                  {:Johtoselvitysviite {:vaadittuKytkin false}})
        ]
    {:kasittelytietotieto (get-kasittelytieto application)
     :luvanTunnisteTiedot (get-luvan-tunniste-tiedot application)
     :alkuPvm alku-pvm
     :loppuPvm loppu-pvm
     :sijaintitieto (get-sijaintitieto application)
     :osapuolitieto osapuolitieto
     :vastuuhenkilotieto vastuuhenkilotieto
     :maksajatieto maksajatieto
     :kayttotarkoitus (ya-operation-type-to-usage-description operation-name-key)
     :johtoselvitysviitetieto johtoselvitysviitetieto}))

(defn application-to-canonical
  "Transforms application mongodb-document to canonical model."
  [application lang]
  (let [application (tools/unwrapped application)
        documents-by-type (documents-by-type-without-blanks application)
        hankkeen-kuvaus-key (util/find-first documents-by-type [:yleiset-alueet-hankkeen-kuvaus-sijoituslupa
                                                                :yleiset-alueet-hankkeen-kuvaus-kayttolupa
                                                                :yleiset-alueet-hankkeen-kuvaus-kaivulupa])
        operation-name-key (-> application :primaryOperation :name keyword)
        permit-name-key (ya-operation-type-to-schema-name-key operation-name-key)
        config (or (configs-per-permit-name operation-name-key) (configs-per-permit-name permit-name-key))

        [main-viit-tapahtuma-name main-viit-tapahtuma] (-> documents-by-type get-main-viit-tapahtuma-info seq first)
        hankkeen-kuvaus (when (:hankkeen-kuvaus config)
                          (-> (hankkeen-kuvaus-key documents-by-type) first :data))
        pinta-ala (when (:hankkeen-kuvaus config)
                    (:varattava-pinta-ala hankkeen-kuvaus))
        lupaAsianKuvaus (when (:hankkeen-kuvaus config)
                          (:kayttotarkoitus hankkeen-kuvaus))
        sijoituslupaviitetieto (when (:sijoituslupa-link config)
                                 (when-let [tunniste (link-permit-selector-value hankkeen-kuvaus (:linkPermitData application) [hankkeen-kuvaus-key :sijoitusLuvanTunniste])]
                                   {:Sijoituslupaviite {:vaadittuKytkin false
                                                        :tunniste tunniste}}))
        lupakohtainenLisatietotieto (filter #(seq (:LupakohtainenLisatieto %))
                                      (flatten
                                        (vector
                                          (when-let [erikoiskuvaus-operaatiosta (ya-operation-type-to-additional-usage-description operation-name-key)]
                                            {:LupakohtainenLisatieto {:selitysteksti "Lis\u00e4tietoja k\u00e4ytt\u00f6tarkoituksesta"
                                                                      :arvo erikoiskuvaus-operaatiosta}})
                                          (when (:mainostus-viitoitus-lisatiedot config)
                                            (get-mainostus-viitoitus-lisatiedot main-viit-tapahtuma)))))
        canonical-body (util/strip-nils
                         (merge
                           (get-canonical-body application operation-name-key config documents-by-type)
                           {:pintaala pinta-ala
                            :lausuntotieto (get-statements (:statements application))
                            :lupaAsianKuvaus lupaAsianKuvaus
                            :lupakohtainenLisatietotieto lupakohtainenLisatietotieto
                            :sijoituslupaviitetieto sijoituslupaviitetieto}
                           (when (and main-viit-tapahtuma (= "mainostus-tapahtuma-valinta" (name main-viit-tapahtuma-name)))
                             {:toimintajaksotieto (get-mainostus-alku-loppu-hetki main-viit-tapahtuma)})
                           (when (:closed application)
                             (get-construction-ready-info application))))]
    {:YleisetAlueet {:toimituksenTiedot (toimituksen-tiedot application lang)
                     :yleinenAlueAsiatieto {permit-name-key canonical-body}}}))


(defn jatkoaika-to-canonical
  "Transforms continuation period application mongodb-document to canonical model."
  [application lang]
  (let [application (tools/unwrapped application)
        documents-by-type (documents-by-type-without-blanks application)
        link-permit-data (-> application :linkPermitData first)
        ;; When operation is missing, setting kaivulupa as the operation (app created via op tree)
        operation-name-key (or (-> link-permit-data :operation keyword) :ya-katulupa-vesi-ja-viemarityot)
        permit-name-key (ya-operation-type-to-schema-name-key operation-name-key)
        config (or (configs-per-permit-name operation-name-key) (configs-per-permit-name permit-name-key))
        [alku-pvm loppu-pvm] (get-alku-loppu-pvm application documents-by-type config)
        hankkeen-kuvaus (-> documents-by-type :hankkeen-kuvaus-jatkoaika first :data :kuvaus)
        lisaaikatieto (when alku-pvm {:Lisaaika {:alkuPvm alku-pvm
                                                 :loppuPvm loppu-pvm
                                                 :perustelu hankkeen-kuvaus}})
        canonical-body (util/strip-nils
                         (merge
                           (get-canonical-body application operation-name-key config documents-by-type)
                           {:lisaaikatieto lisaaikatieto}))]

    {:YleisetAlueet {:toimituksenTiedot (toimituksen-tiedot application lang)
                     :yleinenAlueAsiatieto {permit-name-key
                                            (-> canonical-body
                                                (update :luvanTunnisteTiedot add-kuntalupatunnus-link-in-luvan-tunniste-tiedot application))}}}))


(defn- get-ya-katselmus [katselmus]
  (let [data (tools/unwrapped (:data katselmus))
        {:keys [katselmuksenLaji vaadittuLupaehtona rakennus]} data
        {:keys [pitoPvm pitaja lasnaolijat poikkeamat]} (:katselmus data)
        huomautukset (-> data :katselmus :huomautukset)
        task-id (:id katselmus)
        task-name (:taskname katselmus)]
    (util/strip-nils
      (merge
        (util/assoc-when-pred {} util/not-empty-or-nil?
          :pitoPvm (if (number? pitoPvm) (util/to-xml-date pitoPvm) (util/to-xml-date-from-string pitoPvm))
          :katselmuksenLaji katselmuksenLaji
          :vaadittuLupaehtonaKytkin (true? vaadittuLupaehtona)
          :lasnaolijat lasnaolijat
          :pitaja pitaja
          :poikkeamat poikkeamat
          :tarkastuksenTaiKatselmuksenNimi (ss/trim task-name))
        (when task-id
          {:muuTunnustieto {:MuuTunnus {:tunnus task-id :sovellus "Lupapiste"}}})
        (when (:kuvaus huomautukset)
          {:huomautustieto {:Huomautus (util/strip-nils (reduce-kv
                                                          (fn [m k v]
                                                            (if-not (ss/blank? v)
                                                              (assoc m k (util/to-xml-date-from-string v))
                                                              m))
                                                          (util/assoc-when-pred {} util/not-empty-or-nil?
                                                            :kuvaus (:kuvaus huomautukset)
                                                            :toteaja (:toteaja huomautukset))
                                                          (select-keys huomautukset [:maaraAika :toteamisHetki])))}})))))

(defn katselmus-canonical [application katselmus lang user]
  (let [application (tools/unwrapped application)
        documents-by-type (documents-by-type-without-blanks application)
        operation-name-key (-> application :primaryOperation :name keyword)
        permit-name-key (ya-operation-type-to-schema-name-key operation-name-key)
        config (or (configs-per-permit-name operation-name-key) (configs-per-permit-name permit-name-key))
        canonical-body (util/strip-nils
                         (merge
                           (get-canonical-body application operation-name-key config documents-by-type)
                           {:katselmustieto {:Katselmus (get-ya-katselmus katselmus)}}))]

    {:YleisetAlueet {:toimituksenTiedot (toimituksen-tiedot application lang)
                     :yleinenAlueAsiatieto {permit-name-key canonical-body}}}))
