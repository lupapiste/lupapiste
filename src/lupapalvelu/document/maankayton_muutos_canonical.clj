(ns lupapalvelu.document.maankayton-muutos-canonical
  (:require [lupapalvelu.document.asianhallinta-canonical :as ah-canonical]
            [lupapalvelu.document.canonical-common :as canonical-common]
            [lupapalvelu.document.tools :as tools]))

(defn- toimituksen-tila [app]
  (let [state (-> app :state keyword)
        state-name (state {:sent "Hakemus"
                           :submitted "Hakemus"})]
    ;; TODO: add more states.
    (or state-name "Hakemus")))

(def op->elem {:tonttijako :Tonttijako
               :asemakaava :Asemakaava
               :ranta-asemakaava :RantaAsemakaava
               :yleiskaava :Yleiskaava})

(defn maankayton-muutos-canonical [application lang]
  (let [app          (tools/unwrapped application)
        docs         (canonical-common/documents-without-blanks app)
        [op-doc]     (canonical-common/schema-info-filter docs :op)
        op-name      (->> op-doc :schema-info :op :name keyword (get op->elem))
        op-age       (-> op-doc :data :uusiKytkin)
        op-desc      (-> op-doc :data :kuvaus)
        parties      (canonical-common/process-parties docs lang)
        property     (-> docs (canonical-common/schema-info-filter :name "kiinteisto") :data :kiinteisto)
        link-permits (:linkPermitData application)]
    {:Maankaytonmuutos
     {:maankayttomuutosTieto
      {op-name
       {:toimituksenTiedottieto
        {:ToimituksenTiedot (canonical-common/toimituksen-tiedot application lang)}
        :hakemustieto
        {:Hakemus
         {:osapuolitieto   parties
          :sijaintitieto   (canonical-common/get-sijaintitieto application)
          :kohdekiinteisto (:propertyId application)
          :maaraAla        (canonical-common/maaraalatunnus app property)
          :tilatieto       (canonical-common/simple-application-state app)}}
        :toimituksenTila (toimituksen-tila app)
        :uusiKytkin      (= op-age "uusi")
        :kuvaus          op-desc
        :toimenpiteet    (ah-canonical/get-toimenpiteet application lang)
        :kohteet         (canonical-common/kohteet application)
        :viitelupatieto  (map canonical-common/get-viitelupatieto link-permits)
        :lausuntotieto   (-> application :statements canonical-common/get-statements)
        ; liitetieto is added during mapping
        }}}}))


(defmethod canonical-common/application->canonical :MM [application lang]
  (maankayton-muutos-canonical application lang))
