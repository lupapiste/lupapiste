(ns lupapalvelu.document.yleiset-alueet-canonical
  (:require [lupapalvelu.document.canonical-common :refer :all]
            [lupapalvelu.document.tools :as tools]
            [lupapalvelu.domain :as domain]
            [sade.core :refer :all]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]))

(defn- get-handler [{:keys [handlers]}]
  (if-let [general-handler (util/find-first :general handlers)]
    {:henkilotieto {:Henkilo {:nimi {:etunimi (:firstName general-handler) :sukunimi (:lastName general-handler)}}}}
    empty-tag))

(defn get-kasittelytieto [application]
  {:Kasittelytieto {:muutosHetki (date/xml-datetime (:modified application))
                    :hakemuksenTila (application-state-to-krysp-state (keyword (:state application)))
                    :asiatunnus (:id application)
                    :paivaysPvm (date/xml-date (state-timestamp application))
                    :kasittelija (get-handler application)}})

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

(defn- get-henkilo [henkilo]
  (let [nimi                  (util/assoc-when-pred {} util/not-empty-or-nil?
                                                    :etunimi (-> henkilo :henkilotiedot :etunimi)
                                                    :sukunimi (-> henkilo :henkilotiedot :sukunimi))
        osoite                (get-simple-osoite (:osoite henkilo))
        [hetu-key hetu-value] (-> (:henkilotiedot henkilo) get-hetu vec first)]
    (not-empty
      (util/assoc-when-pred {} util/not-empty-or-nil?
                            :nimi nimi
                            :osoite osoite
                            :sahkopostiosoite (-> henkilo :yhteystiedot :email)
                            :puhelin (-> henkilo :yhteystiedot :puhelin)
                            hetu-key hetu-value
                            ))))

(defn- get-hakija
  "Either applicant (hakija) or agent (hakijan asiamies)."
  [{:keys [data schema-info]}]
  (when-let [hakija (not-empty
                      (if (= "yritys" (:_selected data))
                        (let [yhteyshenkilo (-> data :yritys :yhteyshenkilo)
                              yritys  (util/deep-merge (:yritys (get-osapuoli-data data :hakija))
                                                       (get-yritys (:yritys data)))
                              henkilo (get-henkilo yhteyshenkilo)]
                          (when (and yritys henkilo)
                            {:Osapuoli
                             {:yritystieto                   {:Yritys yritys}
                              :henkilotieto                  {:Henkilo henkilo}
                              :suoramarkkinointikieltoKytkin (some-> yhteyshenkilo :kytkimet :suoramarkkinointilupa true? not)}}))
                        (when-let [henkilo (get-henkilo (:henkilo data))]
                          {:Osapuoli
                           {:henkilotieto                  {:Henkilo henkilo}
                            :suoramarkkinointikieltoKytkin (some-> henkilo :kytkimet :suoramarkkinointilupa true? not)}})))]
    (assoc-in hakija [:Osapuoli :rooliKoodi]  (case (some-> schema-info :subtype keyword)
                                                :hakija           "hakija"
                                                :hakijan-asiamies "yhteyshenkilö"))))

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
    (when (and (:sukunimi content) (:etunimi content))
      (merge content {:rooliKoodi roolikoodi}))))

(defn- get-tyomaasta-vastaava [tyomaasta-vastaava]
  (let [type (:_selected tyomaasta-vastaava)
        vastuuhenkilo (get-vastuuhenkilo tyomaasta-vastaava type "lupaehdoista/ty\u00f6maasta vastaava henkil\u00f6")]
    (when vastuuhenkilo
      (merge
        {:Vastuuhenkilo vastuuhenkilo}
        (when (= "yritys" type)
          (let [yritys (util/deep-merge
                         (:yritys (get-osapuoli-data tyomaasta-vastaava :tyomaastaVastaava))
                         (get-yritys (:yritys tyomaasta-vastaava)))]
            (when (and (:nimi yritys)
                       (:liikeJaYhteisotunnus yritys)) ; has some "sane" data
              {:Osapuoli {:yritystieto {:Yritys yritys}
                          :rooliKoodi  "ty\u00f6nsuorittaja"}})))))))

(defn- get-yritys-and-henkilo [doc doc-type]
  (let [is-maksaja-doc (= "maksaja" doc-type)
        info (if (= (:_selected doc) "yritys")
               ;; yritys-tyyppinen hakija/maksaja, siirretaan yritysosa omaksi osapuolekseen
               (let [vastuuhenkilo-roolikoodi (if is-maksaja-doc "maksajan vastuuhenkil\u00f6" "hankkeen vastuuhenkil\u00f6")
                     vastuuhenkilo (get-vastuuhenkilo doc "yritys" vastuuhenkilo-roolikoodi)
                     yritys (util/deep-merge
                              (:yritys (get-osapuoli-data doc :tyomaastaVastaava))
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
  {:Toimintajakso {:alkuHetki (date/xml-datetime (:tapahtuma-aika-alkaa-pvm mainostus-viitoitus-tapahtuma))
                   :loppuHetki(date/xml-datetime (:tapahtuma-aika-paattyy-pvm mainostus-viitoitus-tapahtuma))}})

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

(defn- get-construction-period-info [{:keys [started closed]}]
  (cond-> {:kayttojaksotieto {:Kayttojakso {:alkuHetki (date/xml-datetime started)}}}
      closed (assoc :valmistumisilmoitusPvm (date/xml-date (now)))
      closed (assoc-in [:kayttojaksotieto :Kayttojakso :loppuHetki] (date/xml-datetime closed))))


;; Configs

(def- default-config {:hankkeen-kuvaus true
                      :tyoaika         true})

(def- configs-per-permit-name
  {:Kayttolupa                            default-config
   :Tyolupa                               (merge default-config
                                                 {:johtoselvitysviitetieto true :sijoituslupa-link true})
   :Sijoituslupa                          (merge default-config
                                                 {:tyoaika false :dummy-alku-and-loppu-pvm true})
   :ya-kayttolupa-nostotyot               default-config
   :ya-kayttolupa-vaihtolavat             default-config
   :ya-kayttolupa-kattolumien-pudotustyot default-config
   :ya-kayttolupa-talon-julkisivutyot     default-config
   :ya-kayttolupa-talon-rakennustyot      default-config
   :ya-kayttolupa-muu-tyomaakaytto        default-config
   :ya-kayttolupa-mainostus-ja-viitoitus  {:mainostus-viitoitus-tapahtuma-pvm true
                                           :mainostus-viitoitus-lisatiedot    true}})

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
(defn- get-alku-loppu-pvm
  "Returns [alku-pvm loppu-pvm]"
  [application documents-by-type {:keys [tyoaika dummy-alku-and-loppu-pvm
                                         mainostus-viitoitus-tapahtuma-pvm]}]
  (cond dummy-alku-and-loppu-pvm
        [(date/xml-date (:submitted application)) (date/xml-date (:modified application))]

        tyoaika
        (let [tyoaika-doc (-> (util/some-key documents-by-type :tyoaika :tyo-aika-for-jatkoaika)
                              first :data)]
          [(or (date/xml-date (:tyoaika-alkaa-ms tyoaika-doc))
               (date/xml-date (:tyoaika-alkaa-pvm tyoaika-doc)))
           (if (:voimassa-toistaiseksi tyoaika-doc)
             "2999-01-01" ; Value is required by KRYSP to both exist and be in date format
             (or (date/xml-date (:tyoaika-paattyy-ms tyoaika-doc))
                 (date/xml-date (:tyoaika-paattyy-pvm tyoaika-doc))))])

        mainostus-viitoitus-tapahtuma-pvm
        (let [main-viit-tapahtuma (-> documents-by-type get-main-viit-tapahtuma-info vals first)]
          [(date/xml-date (util/some-key main-viit-tapahtuma
                                         :mainostus-alkaa-pvm
                                         :tapahtuma-aika-alkaa-pvm))
           (date/xml-date (util/some-key main-viit-tapahtuma
                                         :mainostus-paattyy-pvm
                                         :tapahtuma-aika-paattyy-pvm))])))

(defn- get-canonical-body [application operation-name-key config documents-by-type]
  ;;
  ;; Sijoituslupa: Maksaja, alkuPvm and loppuPvm are not filled in the application, but are requested by schema
  ;;               -> Maksaja gets Hakija's henkilotieto, AlkuPvm/LoppuPvm both get application's "modified" date.
  ;;
  (let [[alku-pvm loppu-pvm] (get-alku-loppu-pvm application documents-by-type config)
        hakijat (->> (select-keys documents-by-type [:hakija-ya :hakijan-asiamies])
                     vals
                     (apply concat)
                     (mapv get-hakija))
        maksaja (get-yritys-and-henkilo (-> documents-by-type :yleiset-alueet-maksaja first :data) "maksaja")
        maksajatieto-2-1-3 (get-maksajatiedot (-> documents-by-type :yleiset-alueet-maksaja first))
        maksajatieto (when maksaja {:Maksaja (util/deep-merge maksajatieto-2-1-3 (:Osapuoli maksaja))})
        tyomaasta-vastaava (some-> (domain/get-document-by-subtype application :tyomaasta-vastaava)
                                   :data
                                   get-tyomaasta-vastaava )
        ;; If tyomaasta-vastaava does not have :osapuolitieto, we filter out the resulting nil.
        osapuolitieto (filterv :Osapuoli (conj hakijat tyomaasta-vastaava))
        ;; If tyomaasta-vastaava does not have :vastuuhenkilotieto, we filter the resulting nil out.
        vastuuhenkilotieto (filterv :Vastuuhenkilo [tyomaasta-vastaava maksaja])
        johtoselvitysviitetieto (when (:johtoselvitysviitetieto config)
                                  {:Johtoselvitysviite {:vaadittuKytkin false}})]
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
        documents-by-type (stripped-documents-by-type application)
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
                                 (when-let [tunniste (link-permit-selector-value hankkeen-kuvaus
                                                                                 (:linkPermitData application)
                                                                                 [hankkeen-kuvaus-key :sijoitusLuvanTunniste])]
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
                           (when (:started application)
                             (get-construction-period-info application))))]
    {:YleisetAlueet {:toimituksenTiedot (toimituksen-tiedot application lang)
                     :yleinenAlueAsiatieto {permit-name-key canonical-body}}}))


(defn jatkoaika-to-canonical
  "Transforms continuation period application mongodb-document to canonical model."
  [application lang]
  (let [application          (tools/unwrapped application)
        documents-by-type    (stripped-documents-by-type application)
        link-permit-data     (-> application :linkPermitData first)
        ;; When operation is missing, setting kaivulupa as the operation (app created via op tree)
        operation-name-key   (or (-> link-permit-data :operation keyword) :ya-katulupa-vesi-ja-viemarityot)
        permit-name-key      (ya-operation-type-to-schema-name-key operation-name-key)
        config               (assoc (or (configs-per-permit-name operation-name-key)
                                        (configs-per-permit-name permit-name-key))
                                    :tyoaika true)
        [alku-pvm loppu-pvm] (get-alku-loppu-pvm application documents-by-type config)
        hankkeen-kuvaus      (-> documents-by-type :hankkeen-kuvaus-jatkoaika first :data :kuvaus)
        lisaaikatieto        (when alku-pvm {:Lisaaika {:alkuPvm   (date/xml-date alku-pvm)
                                                        :loppuPvm  (date/xml-date loppu-pvm)
                                                        :perustelu hankkeen-kuvaus}})
        canonical-body       (util/strip-nils
                               (merge
                                 (get-canonical-body application operation-name-key config documents-by-type)
                                 {:lisaaikatieto lisaaikatieto}))]

    {:YleisetAlueet {:toimituksenTiedot    (toimituksen-tiedot application lang)
                     :yleinenAlueAsiatieto {permit-name-key
                                            (-> canonical-body
                                                (update :luvanTunnisteTiedot add-kuntalupatunnus-link-in-luvan-tunniste-tiedot application))}}}))

(defmethod application->canonical :YA [application lang]
  (if (util/=as-kw :ya-jatkoaika (-> application :primaryOperation :name))
    (jatkoaika-to-canonical application lang)
    (application-to-canonical application lang)))

(defn- get-ya-katselmus-muu-tunnustieto [katselmus]
  (->> [{:tunnus (:id katselmus) :sovellus "Lupapiste"}
        {:tunnus (:muuTunnus katselmus) :sovellus (:muuTunnusSovellus katselmus)}]
       (filter :tunnus)
       (map (partial hash-map :MuuTunnus))))

(defn- get-ya-katselmus [katselmus]
  (let [data (tools/unwrapped (:data katselmus))
        {:keys [katselmuksenLaji vaadittuLupaehtona]} data
        {:keys [pitoPvm pitaja lasnaolijat poikkeamat]} (:katselmus data)
        huomautukset (-> data :katselmus :huomautukset)
        task-name (:taskname katselmus)]
    (util/strip-nils
      (merge
        (util/assoc-when-pred
          {} util/not-empty-or-nil?
          :pitoPvm (date/xml-date pitoPvm)
          :katselmuksenLaji katselmuksenLaji
          :vaadittuLupaehtonaKytkin (true? vaadittuLupaehtona)
          :lasnaolijat lasnaolijat
          :pitaja pitaja
          :poikkeamat poikkeamat
          :tarkastuksenTaiKatselmuksenNimi (ss/trim task-name)
          :muuTunnustieto (get-ya-katselmus-muu-tunnustieto katselmus))
        (when (:kuvaus huomautukset)
          {:huomautustieto {:Huomautus (util/strip-nils (reduce-kv
                                                          (fn [m k v]
                                                            (if-not (ss/blank? v)
                                                              (assoc m k (date/xml-date v))
                                                              m))
                                                          (util/assoc-when-pred {} util/not-empty-or-nil?
                                                            :kuvaus (:kuvaus huomautukset)
                                                            :toteaja (:toteaja huomautukset))
                                                          (select-keys huomautukset [:maaraAika :toteamisHetki])))}})))))

(defn katselmus-canonical [application katselmus lang]
  (let [application (tools/unwrapped application)
        documents-by-type (stripped-documents-by-type application)
        operation-name-key (-> application :primaryOperation :name keyword)
        permit-name-key (ya-operation-type-to-schema-name-key operation-name-key)
        config (or (configs-per-permit-name operation-name-key)
                   (configs-per-permit-name permit-name-key))
        canonical-body (util/strip-nils
                         (merge
                           (get-canonical-body application operation-name-key config documents-by-type)
                           {:katselmustieto {:Katselmus (get-ya-katselmus katselmus)}}))]

    {:YleisetAlueet {:toimituksenTiedot (toimituksen-tiedot application lang)
                     :yleinenAlueAsiatieto {permit-name-key canonical-body}}}))

(defmethod review->canonical :YA [application katselmus {:keys [lang]}]
  (katselmus-canonical application katselmus lang))

(defmethod review-path :YA [{:keys [primaryOperation]}]
  (when-let [permit-name-key (get ya-operation-type-to-schema-name-key (keyword (:name primaryOperation)))]
    [:YleisetAlueet :yleinenAlueAsiatieto permit-name-key :katselmustieto :Katselmus]))

(defmethod description :YA [{:keys [primaryOperation]} canonical]
  (when-let [permit-name-key (get ya-operation-type-to-schema-name-key (keyword (:name primaryOperation)))]
    (get-in canonical [:YleisetAlueet :yleinenAlueAsiatieto permit-name-key :lupaAsianKuvaus])))
