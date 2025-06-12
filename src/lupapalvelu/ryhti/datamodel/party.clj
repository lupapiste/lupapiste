(ns lupapalvelu.ryhti.datamodel.party
  "Ryhti mappings for data models that match Lupapiste
  party documents, or something very similar (like building owners)."
  (:require [lupapalvelu.ryhti.api.mappings :as mappings]
            [lupapalvelu.ryhti.datamodel.common :refer [->iso-date apply-mapping]]
            [sade.date :as date]))

(defn person-like-base [{:keys [prefix required]}]
  (->> [{:ryhti       [:personalIdentityCode]
         :lupapiste   (into prefix [:henkilotiedot :hetu :value])
         :description "Henkilötunnus, 0..1"}

        ;; TODO: First check :not-finnish-hetu flag?
        {:ryhti       [:otherId]
         :lupapiste   (into prefix [:henkilotiedot :ulkomainenHenkilotunnus :value])
         :description "Muutunnus, 0..1"}

        ;; NOTE: E.g. for Planner firstName and familyName are 1..1
        {:ryhti       [:firstName]
         :lupapiste   (into prefix [:henkilotiedot :etunimi :value])
         :description "Etunimi, 0..1"}

        {:ryhti       [:familyName]
         :lupapiste   (into prefix [:henkilotiedot :sukunimi :value])
         :description "Sukunimi, 0..1"}]
       (mapv (fn [field]
               (if (contains? required (-> field :ryhti first))
                 (assoc field :required? true)
                 field)))))

(defn is-person [prefix]
  {:ryhti       [:isPerson]
   :lupapiste   (into prefix [:_selected :value])
   :->ryhti     (fn [selected _]
                  (= "henkilo" selected))
   :description "On henkilo, 1..1"})

(defn company-like-base [prefix]
  [{:ryhti       [:businessId]
    :lupapiste   (into prefix [:liikeJaYhteisoTunnus :value])
    :description "Yritys ja yhteisötunnus, 0..1"}

   {:ryhti       [:organizationName]
    :lupapiste   (into prefix [:yritysnimi :value])
    :description "Organisaation nimi, 0..1"}])

(declare contact-address-mapping)

(def operator-mapping
  ;; 1.63. Toimija
  (concat [;; No operatorKey?
           (is-person [:toimija])

           #_
           {:ryhti       [:nationalityCode]
            :lupapiste   []
            ;; TODO: Do we have this?
            :description ""}

           #_
           {:ryhti       [:deathDate]
            :lupapiste   []
            ;; NOTE: We definitely don't have this and don't want this
            :description ""}

           {:ryhti       [:operatorAddress]
            :lupapiste   [:toimija]
            :->ryhti     (fn [toimija context]
                           (let [osoite (if (= "henkilo" (:value (:_selected toimija)))
                                          (get-in toimija [:henkilo :osoite])
                                          (get-in toimija [:yritys :osoite]))]
                             [(apply-mapping contact-address-mapping
                                             (assoc context
                                                    :osoite osoite))]))
            :description "Toimijan yhteysosoite, 0..*"}

           #_
           {:ryhti       [:languageCode]
            :lupapiste   []
            ;; TODO: Do we want to use this?
            :description "Kielikoodi, 0..1"}]
          (person-like-base {:prefix [:toimija :henkilo]})
          (company-like-base [:toimija :yritys])))

(def contact-address-mapping
  ;; 1.64. Yhteysosoite
  ;; NOTE: Kotimaiset osoitteet toimitetaan yhdessä merkkijonokentässä
  [
   {:ryhti       [:addressKey]
    :lupapiste   [:osoite :ryhti :addressKey :value]
    :->ryhti     (fn [v _] v)
    :description ""}

   #_
   {:ryhti       [:addressName]
    ;; TODO: Probably something else? Katuosoite probably on postalAddress field.
    :lupapiste   [:osoite :katu :value]
    :description "Osoitenimi, 0..1"}

   #_
   {:ryhti       [:numberPartOfAddressNumber]
    :lupapiste   []
    :description ""}

   #_
   {:ryhti       [:subdivisionLetterOfAddressNumber]
    :lupapiste   []
    :description ""}

   #_
   {:ryhti       [:apartmentIdentifierLetterSuffix]
    :lupapiste   []
    :description ""}

   #_
   {:ryhti       [:apartmentIdentifierNumber]
    :lupapiste   []
    :description ""}

   #_
   {:ryhti       [:apartmentIdentifierSubdivisionLetter]
    :lupapiste   []
    :description ""}

   #_
   {:ryhti       [:additionalAddressAttribute]
    :lupapiste   []
    :description ""}

   #_
   {:ryhti       [:c_O]
    :lupapiste   []
    :description ""}

   #_
   {:ryhti       [:poBoxAddress]
    :lupapiste   []
    :description ""}

   {:ryhti       [:postalCode]
    :lupapiste   [:osoite :postinumero :value]
    :description "Postinumero, 1"}

   #_
   {:ryhti       [:location]
    :lupapiste   []
    :description "Sijainti, 0"}

   {:ryhti       [:countryCode]
    :lupapiste   [:osoite :maa :value]
    :->ryhti     (fn [value _]
                   ;; TODO: Koodisto?
                   (case value
                     "FIN" "http://uri.suomi.fi/codelist/jhs/valtio_1_20120101/code/246"
                     nil))
    :description "Maakoodi, 0"}

   #_
   {:ryhti       [:foreignAddress]
    :lupapiste   []
    :description "Ulkomainen lähiosoite, 0..1"}

   #_
   {:ryhti       [:foreignPostalAddress]
    :lupapiste   []
    :description "Ulkomainen postitoimipaikka, 0..1"}

   #_
   {:ryhti       [:numberPartOfAddressNumber2]
    :lupapiste   []
    :description ""}

   #_
   {:ryhti       [:subdivisionLetterOfAddressNumber2]
    :lupapiste   []
    :description ""}

   #_
   {:ryhti       [:additionalForeignAddress]
    :lupapiste   []
    :description "Ulkomainen lähiosoite lisärivi, 0..1"}

   {:ryhti       [:postalAddress]
    :lupapiste   [:osoite :katu :value]
    :->ryhti     (fn [value _]
                   {:fin value})
    :description "Postiosoite, 0..1"}

   {:ryhti       [:contactAddressType]
    :lupapiste   [:osoite :maa :value]
    :->ryhti     (fn [maa _]
                   ;; TODO: Kotimainen/ulkomainen, postiosoite/yhteysosoite, tilapäivinen, vakinainen?
                   ;; The apply-mapping context might tell if this is postiosoite or yhteysosoite?
                   ;; Then we check countryCode = FIN for kotimainen/ulkomainen
                   (case maa
                     "FIN" "http://uri.suomi.fi/codelist/rytj/yhteysosoitteenLaji/code/01"
                     nil))
    :description "Yhteysosoitteen laji, 1..1"}

   {:ryhti       [:source]
    :lupapiste   []
    :->ryhti     (fn [_ _]
                   ;; TODO: Koodisto
                   ;; TODO: Value
                   ;; NOTE 05 wasn't accepted?
                   ;; Väestötietojärjestelmä
                   "http://uri.suomi.fi/codelist/rytj/tietolahde/code/01")
    :description "Tietolähde, 1..1"}

   ])

(def engaging-party-mapping
  ;; 1.20. Hankkeeseen ryhtyvä
  ;; Lupapiste: hakija tai hakija-r dokumentti
  (concat [{:ryhti     [:engagingPartyKey]
            :lupapiste [:hakija :data :ryhti :engagingPartyKey :value]}

           (is-person [:hakija :data])

           {:ryhti       [:applicantContactOperator]
            :lupapiste   [:hakija]
            :->ryhti     (fn [hakija context]
                           (apply-mapping operator-mapping (assoc context :toimija (:data hakija))))
            :description ""}

           #_
           {:ryhti       [:languageCode]
            :lupapiste   []
            :description ""}

           #_
           {:ryhti       [:nationalityCode]
            :lupapiste   []
            :description ""}

           #_
           {:ryhti       [:deathDate]
            :lupapiste   []
            :description ""}]
          (person-like-base {:prefix [:hakija :data :henkilo]})
          (company-like-base [:hakija :data :yritys])))

(def planner-mapping
  ;; 1.17. Suunnittelija
  ;; Lupapiste: Suunnittelija tai Pääsuunnittelija dokumentti
  (into [{:ryhti     [:plannerKey]
          :lupapiste [:suunnittelija :data :ryhti :plannerKey :value]}

         {:ryhti       [:plannerRole]
          :lupapiste   [:suunnittelija :data :kuntaRoolikoodi :value]
          :->ryhti     (fn [roolikoodi context]
                         (get mappings/suunnittelijanrooli
                              (if (= "paasuunnittelija" (:name (:schema-info (:suunnittelija context))))
                                "pääsuunnittelija"
                                roolikoodi)))
          :description "SuunnittelijanRooli, 1"}

         {:ryhti       [:plannerCompetence]
          :lupapiste   [:suunnittelija :data :patevyys :patevyysluokka :value]
          :->ryhti     (fn [patevyysluokka _]
                         (get mappings/vaativuusluokat (keyword patevyysluokka)))
          :description "Suunnittelijan pätevyys, 1"}

         ;; NOTE: Has to be in the future currently?
         ;; Error message says should be the date of the save operation OR EARLIER.
         {:ryhti       [:responsibilityStartDate]
          :lupapiste   [:suunnittelija :data :vastuu :vastuunAlkamisPvm :value]
          :->ryhti     ->iso-date
          :description "Vastuun alkamis pvm, 1"}

         {:ryhti       [:responsibilityEndDate]
          :lupapiste   [:suunnittelija :data :vastuu :vastuunPaattymisPvm :value]
          :->ryhti     ->iso-date
          :description "Vastuun päättymis pvm 0..1"}

         ;; NOTE: Refet vastaaviin hankkeisiin?
         #_
         {:ryhti       [:buildingReference]
          :lupapiste   []
          :description "RakennusViite, 0..*"}

         #_
         {:ryhti       [:structureReference]
          :lupapiste   []
          :description "RakennelmaViite, 0..*"}

         #_
         {:ryhti       [:specificAreaReference]
          :lupapiste   []
          :description "ErityinenAlueViite, 0..*"}

         {:ryhti       [:requiredCompetence]
          :lupapiste   [:suunnittelija :data :suunnittelutehtavanVaativuusluokka :value]
          :->ryhti     (fn [patevyysluokka _]
                         (get mappings/vaativuusluokat (keyword patevyysluokka)))
          :description "Tehtävän vaativuus, 1..1"}]

        (person-like-base {:prefix   [:suunnittelija :data]
                           :required #{:firstName :familyName}})))

(def foreman-mapping
  ;; 1.18. Työnjohtaja
  (into [{:ryhti     [:foremanKey]
          :lupapiste [:tyonjohtaja :data :ryhti :foremanKey :value]
          :->ryhti   (fn [v _context] v)}

         {:ryhti       [:foremanCompetence]
          ;; FIXME: Same as requiredCompetence? What is the difference?
          ;; Probably one is requirement for role and another is the competence of the person,
          ;; but we don't have two fields?
          :lupapiste   [:tyonjohtaja :data :patevyysvaatimusluokka :value]
          :->ryhti     (fn [patevyysvaatimusluokka _context]
                         (get mappings/vaativuusluokat (keyword patevyysvaatimusluokka)))
          :description "Vaativuus- ja pätevyysluokka. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/vaativuusluokat\">http://uri.suomi.fi/codelist/rytj/vaativuusluokat</a>"}

         {:ryhti       [:foremanRole]
          :lupapiste   [:tyonjohtaja :data :kuntaRoolikoodi :value]
          :->ryhti     (fn [roolikoodi _context]
                         (get mappings/tyonjohtajan-rooli roolikoodi))
          :description "Työnjohtajan rooli. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/tyonjohtajanrooli\">http://uri.suomi.fi/codelist/rytj/tyonjohtajanrooli</a>"}

         {:ryhti       [:responsibilityStartDate]
          :lupapiste   nil
          ;; TODO: Do we have the field for this?
          :->ryhti     (fn [_v _context]
                         (->iso-date (date/today)))
          :description "VastuunAlkamisPvm, 1"}

         {:ryhti       [:responsibilityEndDate]
          :lupapiste   nil
          ;; TODO:
          :->ryhti     (fn [_v _context] nil)
          :description "VastuunPäättymisPvm, 0..1"}

         {:ryhti       [:buildingReference]
          :lupapiste   nil
          :->ryhti     (fn [_v _context] [])
          :description "0..*"}

         {:ryhti       [:structureReference]
          :lupapiste   nil
          :->ryhti     (fn [_v _context] [])
          :description "0..*"}

         {:ryhti       [:specificAreaReference]
          :lupapiste   nil
          :->ryhti     (fn [_v _context] [])
          :description "0..*"}

         {:ryhti       [:requiredCompetence]
          :lupapiste   [:tyonjohtaja :data :patevyysvaatimusluokka :value]
          :required?   true
          :->ryhti     (fn [patevyysvaatimusluokka _context]
                         (get mappings/vaativuusluokat (keyword patevyysvaatimusluokka)))
          :description "Vaativuus- ja pätevyysluokka. Käytetään koodiston URI arvoa <a href=\"http://uri.suomi.fi/codelist/rytj/vaativuusluokat\">http://uri.suomi.fi/codelist/rytj/vaativuusluokat</a>"}]
        (person-like-base {:prefix [:tyonjohtaja :data]})))

;; TODO: Separate mapping for henkilö / yritys etc. and select before apply-mapping call?
(def building-object-owner
  ;; 1.62. RakennuskohteenOmistaja
  ;; Omistaja
  [{:ryhti     [:buildingObjectOwnerKey]
    :lupapiste [:omistaja :ryhti :buildingObjectOwnerKey :value]}

   {:ryhti       [:ownerOperator]
    :lupapiste   [:omistaja]
    :->ryhti     (fn [omistaja context]
                   (apply-mapping operator-mapping (assoc context :toimija omistaja)))
    :description "Toimija, 1"}

   ;; TODO: OmistajatiedonLähde?

   {:ryhti       [:differentOwner]
    :lupapiste   nil
    :->ryhti     (fn [_ _]
                   false)
    :description "Omistaja poikkeaa, 1. Tosi jos rakennuksella on eri omistaja kuin kiinteistöllä."}

   #_
   {:ryhti       [:tenureStatus]
    :lupapiste   nil
    :description "Hallinta oikeuden peruste, 0..1. Peruste miksi rakennuksen omistaja on eri kuin kiinteistöllä, esim. vuokrakiinteistö."}

   #_
   {:ryhti       [:ownerContactOperator]
    :lupapiste   nil
    :->ryhti     (fn [_ context]
                   (apply-mapping operator-mapping (assoc context :toimija x)))
    :description "Omistaja yhteys, 0..1"}

   {:ryhti       [:ownerType]
    :lupapiste   [:omistaja :omistajalaji :value]
    :->ryhti     (fn [omistajalaji _]
                   (get mappings/omistajalaji omistajalaji))
    :default     (get mappings/omistajalaji "ei tiedossa")
    :description "Omistajalaji, 1"}
   ])
