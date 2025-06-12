(ns lupapalvelu.ryhti.datamodel.application
  (:require [lupapalvelu.ryhti.api.malli-schema.generated :as gen]
            [lupapalvelu.ryhti.datamodel.common :refer [->iso-date apply-mapping]]
            [lupapalvelu.states :as states]))

(def ^{:schema gen/CallForDeviation} deviation-mapping
  ;; 1.9. MääräyksestäPoikkeaminen
  [{:ryhti     [:callForDeviationKey]
    :lupapiste [:poikkeamat-ryhti :callForDeviationKey :value]
    :->ryhti   (fn [v _] v)}
   {:ryhti     [:deviationType]
    :lupapiste nil
    :->ryhti   (fn [_ _]
                 ;; TODO: Koodisto?
                 ;; TODO: Miten saadaan ylipäätään poikkeaminen merkkijono jaettua eri poikkeamiin ja tyypit näille?
                 ;; Luultavasti tarvii uudistaa schema tältäosin.
                 "http://uri.suomi.fi/codelist/rytj/PoikkeamisenLaji/code/13")}
   {:ryhti     [:contentOfDeviation :fin]
    :lupapiste [:poikkeamat]}])

(def ^:private common-mapping
  [{:ryhti       [:lifeCycleState]
    :lupapiste   [:application :state]
    :->ryhti     (fn [state _]
                   (let [state (keyword state)]
                     (cond
                       ;; Peruutettu
                       ;; TODO: Should other states be considered cancelled?
                       (= :canceled state)
                       "http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/code/3"
                       ;; Jätetty
                       (contains? states/post-submitted-states state)
                       "http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/code/2"
                       ;; Luonnos
                       :else
                       "http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/code/1")))
    :description "Elinkaaritila, pakollinen, 1"}

   {:ryhti       [:callForDeviation]
    :lupapiste   [:documents-by-subtype :hankkeen-kuvaus]
    :->ryhti     (fn [docs context]
                   (when (< 1 (count docs))
                     (throw (ex-info "Multiple hankkeen-kuvaus documents found!" {:docs docs})))
                   (let [data       (-> docs first :data)
                         poikkeamat (-> data :poikkeamat :value)
                         ryhti      (-> data :ryhti)]
                     (when (seq poikkeamat)
                       [(apply-mapping deviation-mapping (assoc context
                                                                :poikkeamat poikkeamat
                                                                :poikkeamat-ryhti ryhti))])))
    :description "haettuPoikkeaminen, 0..*. Lupapisteessä yksi merkkijono."}

   {:ryhti       [:applicationContent]
    :lupapiste   [:documents-by-subtype :hankkeen-kuvaus]
    :->ryhti     (fn [docs _]
                   (when (< 1 (count docs))
                     (throw (ex-info "Multiple hankkeen-kuvaus documents found!" {:docs docs})))
                   (-> docs first :data :kuvaus :value))
    :description "Hakemuksen sisältö"}

   {:ryhti       [:dateOfReception]
    :lupapiste   [:application :submitted]
    :nilable?    true
    :->ryhti     ->iso-date
    :description "Vastaanottoaika"}])

(def ^{:schema gen/BuildingPermitApplication} building-permit-application-mapping
  ;; 1.5. Rakentamislupahakemus
  (into common-mapping
        [{:ryhti     [:permitApplicationKey]
          :lupapiste [:application :ryhti :permitApplicationKey]}

         {:ryhti       [:buildingPermitApplicationType]
          :lupapiste   [:application :permitSubtype]
          :->ryhti     (fn [subtype _]
                         (case subtype
                           ;; "tyonjohtaja-ilmoitus"
                           ;; "tyonjohtaja-hakemus"
                           ;; "tyolupa"
                           ;; "suunnittelutarveratkaisu"
                           ;; "sijoituslupa"
                           ;; "sijoitussopimus"
                           ;; "poikkeamislupa"
                           ;; "kayttolupa"
                           "muutoslupa" "http://uri.suomi.fi/codelist/rytj/HakemuksenTyyppi/code/05"
                           "http://uri.suomi.fi/codelist/rytj/HakemuksenTyyppi/code/01"))
          :description "Rakentamislupahakemuksen tyyppi, pääteltävissä (pää)toimenpiteestä"}

         {:ryhti       [:permitApplicationType]
          :lupapiste   [:application :permitSubtype]
          :->ryhti     (fn [subtype _]
                         (case subtype
                           ;; "tyonjohtaja-ilmoitus" nil
                           ;; "tyonjohtaja-hakemus"  nil
                           ;; "tyolupa"
                           ;; "suunnittelutarveratkaisu"
                           ;; "sijoitussopimus"
                           "sijoituslupa" "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/02"
                           ;; "poikkeamislupa" ""
                           ;; "muutoslupa" ""
                           ;; "kayttolupa"

                           ;; Toteuttamislupa
                           "http://uri.suomi.fi/codelist/rytj/LuvanSisalto/code/03"))
          :description "Lupatyyppi, pääteltävissä siitä onko hanke sijoittamis-/toteuttamislupa"}]))

(def ^{:schema gen/DeviationPermitApplication} deviation-permit-application-mapping
  ;; 3.2. deviationPermitApplication - Poikkeamislupahakemus
  (into common-mapping
        [{:ryhti     [:deviationPermitApplicationKey]
          ;; we use the same `:permitApplicationKey` for different app types..
          :lupapiste [:application :ryhti :permitApplicationKey]}

         {:ryhti     [:constructionDesign]
          :lupapiste []
          :->ryhti   (fn [_ _]
                       ;; TODO
                       [])}

         ;; :buildingInformationModel

         ]))

;; TODO:
;; 2.2. landscapeWorkPermitApplication - Maisematyölupahakemus
