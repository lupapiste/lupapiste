(ns lupapalvelu.document.kiinteistotoimitus-canonical
  (:require [lupapalvelu.document.canonical-common :refer [empty-tag] :as canonical-common]
            [lupapalvelu.document.tools :as tools ]
            [lupapalvelu.permit :as permit]
            [sade.util :as util]))



#_(defn kiinteistotoimitus-canonical [application lang]
  (let [app (tools/unwrapped application)
        docs  (canonical-common/documents-without-blanks app)
        op-docs (canonical-common/schema-info-filter docs :op)
        op-name ((-> op-doc :schema-info :op :name keyword) {:tonttijako :Tonttijako
                                                             :asemakaava :Asemakaava
                                                             :ranta-asemakaava :RantaAsemakaava
                                                             :yleiskaava :Yleiskaava})
        parties (canonical-common/process-parties docs lang)
        [{{property :kiinteisto} :data}] (canonical-common/schema-info-filter docs :name "kiinteisto")]
    {:Kiinteistotoimitus
     {:todo
      {op-name
       {:toimituksenTiedottieto
        {:ToimituksenTiedot (canonical-common/toimituksen-tiedot application lang)}
        :hakemustieto
        {:Hakemus
         {:osapuolitieto parties
          :sijaintitieto (canonical-common/get-sijaintitieto application)
          :kohdekiinteisto (:propertyId application)
          :maaraAla (:maaraalaTunnus property)
          :tilatieto (application-state app)}
         }
        :toimituksenTila (toimituksen-tila app)
        :uusiKytkin (= op-age "uusi")
        :kuvaus op-desc}}}}))
