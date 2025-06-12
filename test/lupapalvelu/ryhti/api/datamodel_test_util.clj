(ns lupapalvelu.ryhti.api.datamodel-test-util
  (:require [lupapalvelu.rakennuslupa-canonical-util :as util]
            [sade.date :as date]))

(def property-id "21111111111111")

(def building-key (str (random-uuid)))
(def structure-key (str (random-uuid)))
(def etvra-key (str (random-uuid)))
(def building-object-location-data-key (str (random-uuid)))
(def admistrative-location-unit-key (str (random-uuid)))
(def rakennuskohde-address-key (str (random-uuid)))
(def charging-point-key (str (random-uuid)))

(def ryhti-uusi-rakennuskohde-doc
  (-> util/ryhti-uusi-rakennuskohde-doc
      (update :data assoc :ryhti {:buildingKey {:value building-key :whitelist-action "hidden"}
                                  :structureKey {:value structure-key :whitelist-action "hidden"}
                                  :areaToBeBuiltForSpecificActivitiesKey {:value etvra-key :whitelist-action "hidden"}})
      ;; Might be a mistake in the initial data?
      (update-in [:data :sijaintitiedot] dissoc :hallinnollinen-sijainti-yksikko)

      (assoc-in [:data :latauspiste :ryhti] {:chargingPointKey {:value charging-point-key}})
      (assoc-in [:data :sijaintitiedot :osoite]
                {:ryhti {:addressKey {:value rakennuskohde-address-key}}
                 :katu {:value "Testitie 1"}
                 :postinumero {:value "33800"}
                 :postitoimipaikannimi {:value "JÄRVENPÄÄ"}})
      (assoc-in [:data :sijaintitiedot :sijainti]
                {:ryhti {:buildingObjectLocationDataKey {:value building-object-location-data-key}}
                 :hallinnollinen-sijainti-yksikko {:ryhti {:administrativeLocationUnitKey {:value admistrative-location-unit-key}}
                                                   :kiinteistotunnus {:value property-id}}})))

(def hakija-key (str (random-uuid)))

(def hakija-henkilo
  (-> util/hakija-henkilo
      (assoc-in [:data :_selected :value] "henkilo")
      (update :data assoc :ryhti {:engagingPartyKey {:value hakija-key :whitelist-action "hidden"}})
      (assoc-in [:data :henkilo :osoite :maa :value] "FIN")))

(def suunnittelija1-key (str (random-uuid)))

(def suunnittelija
  (update util/suunnittelija2 :data assoc
          :ryhti {:plannerKey {:value suunnittelija1-key :whitelist-action "hidden"}}
          :vastuu {:vastuunAlkamisPvm {:value "2026-01-16"}}))

(def tyonjohtaja-key (str (random-uuid)))

(def tyonjohtaja
  (update util/tyonjohtaja-v3 :data assoc
          :ryhti {:foremanKey {:value tyonjohtaja-key :whitelist-action "hidden"}}))

(def building-site-key (str (random-uuid)))

(def rakennuspaikka
  (update util/rakennuspaikka :data assoc :ryhti {:buildingSiteKey {:value building-site-key :whitelist-action "hidden"}}))

(def permit-application-key (str (random-uuid)))
(def construction-project-key (str (random-uuid)))
(def construction-action-key (str (random-uuid)))
(def building-object-issue-key (str (random-uuid)))
(def decision-key (str (random-uuid)))
(def deviation-key (str (random-uuid)))
(def address-key (str (random-uuid)))
(def attachment-key (str (random-uuid)))
(def attachment-file-key (str (random-uuid)))

(def ryhti-uusi-rakennuskohde-app-with-etvra
  (let [;; NOTE: The app (and doc) operation can be this though this has ETVRA rakennuskohde
        op {:id          "kerrostalo-rivitalo-id"
            :name        "kerrostalo-rivitalo"
            :description ""
            :ryhti       {:constructionActionKey  construction-action-key
                          :buildingObjectIssueKey building-object-issue-key}}]
    (-> util/application-rakennuslupa
        (assoc :state "verdictGiven"
               :propertyId property-id
               :location [408048.0 6693225.0]
               :primaryOperation op
               :ryhti-data-2026  true
               :ryhti {:permitApplicationKey      permit-application-key
                       :constructionProjectKey    construction-project-key
                       :permanentPermitIdentifier "100000042-R"}
               :documents [(-> hakija-henkilo
                               (update-in [:data :henkilo :osoite] assoc :ryhti {:addressKey {:value address-key}}))
                           rakennuspaikka
                           (-> util/hankkeen-kuvaus
                               (assoc-in [:data :ryhti] {:callForDeviationKey {:value deviation-key}}))
                           suunnittelija
                           tyonjohtaja
                           (-> ryhti-uusi-rakennuskohde-doc
                               (assoc-in [:schema-info :op] op)
                               (update-in [:data :mitat] assoc :pintaAla {:value "1000"})
                               (update-in [:data :kaytto] (fn [kaytto]
                                                            (assoc kaytto :kohteen-laji               {:value "etvra"}
                                                                   :rakennusluokka             {:value nil}
                                                                   :erityista-toimintaa-tyyppi {:value "aurinkopaneelikentta"}))))]
               :pate-verdicts [{:ryhti     {:buildingPermitDecisionKey decision-key}
                                ;; Let's pretend the single attachment we have is also the verdict
                                :published {:attachment-id (-> util/application-rakennuslupa
                                                               :attachments
                                                               first
                                                               :id)}
                                :category  "r"
                                :template  {:giver "viranhaltija"}
                                :data      {:handler          "Pertti Päättäjä"
                                            :giver-first-name "Pertti"
                                            :giver-last-name  "Päättäjä"
                                            :verdict-date     (date/today)
                                            :k-lainvoimainen  (date/today)
                                            :k-kuulutus       (date/today)
                                            :k-aloitettava    (date/today)
                                            :k-voimassa       (date/today)
                                            :verdict-text     "Päätös"}}])
        (assoc-in [:attachments 0 :ryhti] {:attachmentDocumentKey attachment-key})
        (assoc-in [:attachments 0 :latestVersion :ryhti] {:fileKey attachment-file-key}))))
