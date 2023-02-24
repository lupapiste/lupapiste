(ns lupapalvelu.document.maankayton-muutos-canonical
  (:require [lupapalvelu.document.canonical-common :refer [empty-tag] :as canonical-common]
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
  (let [app (tools/unwrapped application)
        docs  (canonical-common/documents-without-blanks app)
        [op-doc] (canonical-common/schema-info-filter docs :op)
        op-name (get op->elem (-> op-doc :schema-info :op :name keyword))
        {op-age :uusiKytkin op-desc :kuvaus} (:data op-doc)
        parties (canonical-common/process-parties docs lang)
        [{{property :kiinteisto} :data}] (canonical-common/schema-info-filter docs :name "kiinteisto")]
    {:Maankaytonmuutos
     {:maankayttomuutosTieto
      {op-name
       {:toimituksenTiedottieto
        {:ToimituksenTiedot (canonical-common/toimituksen-tiedot application lang)}
        :hakemustieto
        {:Hakemus
         {:osapuolitieto parties
          :sijaintitieto (canonical-common/get-sijaintitieto application)
          :kohdekiinteisto (:propertyId application)
          :maaraAla (canonical-common/maaraalatunnus app property)
          :tilatieto (canonical-common/simple-application-state app)}
         }
        :toimituksenTila (toimituksen-tila app)
        :uusiKytkin (= op-age "uusi")
        :kuvaus op-desc}}}}))


(defmethod canonical-common/application->canonical :MM [application lang]
  (maankayton-muutos-canonical application lang))
