(ns lupapalvelu.backing-system.krysp.review-reader
  (:require [taoensso.timbre :refer [error]]
            [lupapalvelu.backing-system.krysp.common-reader :as common]
            [lupapalvelu.tasks :as tasks]
            [net.cgrand.enlive-html :as enlive]
            [sade.common-reader :as cr]
            [sade.util :as util]
            [sade.xml :refer [select]]
            [schema.core :as sc]))

(sc/defschema MuuTunnus
  {:tunnus   sc/Str
   :sovellus sc/Str})

(sc/defschema Metatieto
  {(sc/optional-key :metatietoArvo) sc/Str
   (sc/optional-key :metatietoNimi) sc/Str})

(sc/defschema Liite
  {:kuvaus                                sc/Str
   :linkkiliitteeseen                     sc/Str
   (sc/optional-key :muokkausHetki)       sc/Str
   (sc/optional-key :tyyppi)              sc/Str
   (sc/optional-key :versionumero)        sc/Str
   (sc/optional-key :tekija)              sc/Any
   (sc/optional-key :metatietotieto)      [{:metatieto Metatieto}]
   (sc/optional-key :rakennustunnustieto) sc/Any})

(sc/defschema Huomautus
  {:kuvaus                          sc/Str
   (sc/optional-key :maaraAika)     sc/Str
   (sc/optional-key :toteamisHetki) sc/Str
   (sc/optional-key :toteaja)       sc/Str})

(sc/defschema KuntaGMLReview
  {:katselmuksenLaji                            (apply sc/enum tasks/task-types)
   :tarkastuksenTaiKatselmuksenNimi             sc/Str
   :vaadittuLupaehtonaKytkin                    sc/Bool
   (sc/optional-key :katselmuksenRakennustieto) [sc/Any]
   (sc/optional-key :huomautukset)              [{:huomautus Huomautus}]
   (sc/optional-key :osittainen)                (sc/enum "osittainen" "lopullinen")
   (sc/optional-key :pitoPvm)                   sc/Int
   (sc/optional-key :pitaja)                    sc/Str
   (sc/optional-key :lasnaolijat)               sc/Str
   (sc/optional-key :poikkeamat)                sc/Str
   (sc/optional-key :verottajanTvLlKytkin)      sc/Bool
   (sc/optional-key :tilanneKoodi)              sc/Str
   (sc/optional-key :paatos)                    sc/Str
   (sc/optional-key :paatoksenPerustelut)       sc/Str
   (sc/optional-key :katselmuksenTarkenne)      sc/Str
   (sc/optional-key :muuTunnustieto)            [{:MuuTunnus MuuTunnus}]
   (sc/optional-key :liitetieto)                {:Liite Liite}})


(sc/defn xml->reviews :- (sc/maybe [KuntaGMLReview])
  [xml & [strict?]]
  (let [xml-no-ns (cr/strip-xml-namespaces xml)
        asiat (enlive/select xml-no-ns common/case-elem-selector)]
    (when (not-empty asiat)
      (when (> (count asiat) 1)
        (error "Creating application from previous permit. More than one RakennusvalvontaAsia element were received in the xml message. Count:" (count asiat)))

      (let [asia (first asiat)
            selector (if strict?
                       [:RakennusvalvontaAsia :> :katselmustieto :Katselmus]
                       [:RakennusvalvontaAsia :katselmustieto :Katselmus])
            katselmukset (map cr/all-of (select asia selector))
            massage (fn [katselmus]
                      (-> katselmus
                          (util/ensure-sequential :muuTunnustieto)
                          (util/ensure-sequential :huomautukset)
                          (util/ensure-sequential :katselmuksenRakennustieto)
                          (cr/convert-keys-to-timestamps [:pitoPvm])
                          cr/convert-booleans
                          cr/cleanup))]
        (map massage katselmukset)))))
