(ns lupapalvelu.xml.krysp.vesihuolto-mapping
  (:require
    [lupapalvelu.xml.emit :refer [element-to-xml]]
    [lupapalvelu.xml.krysp.mapping-common :as mapping-common]
    [lupapalvelu.permit :as permit]))

; :xsi:schemaLocation (mapping-common/schemalocation "ymparisto/vesihuoltolaki" "2.1.1")

(def vesihuolto-to-krysp [{:tag :ymv:VesihuoltolakiType
                           :child
                           [{:tag :toimituksenTiedot :child mapping-common/toimituksenTiedot}
                            {:tag :vapatukset
                             :child [{:tag :Vapautus
                                      :child [{:tag :kasittelytietotieto :child [mapping-common/ymp-kasittelytieto-children]}
                                              {:tag :luvanTunnistetiedot
                                               :child [mapping-common/lupatunnus]}
                                              {:tag :lausuntotieto
                                               :child [mapping-common/lausunto_213]}
                                              {:tag :vapautusperuste}
                                              {:tag :vapautushakemustieto
                                               :child [{:tag :Vapautushakemus
                                                        :child [{:tag :hakija
                                                                 :child [mapping-common/henkilo-child-ns-yht]}
                                                                {:tag :kohde
                                                                 :child [{:tag :kiinteistorekisteritunnus}
                                                                         {:tag :kiinteistinRakennusTieto
                                                                          :child [{:tag :KiinteistinRakennus
                                                                                   :child [{:tag :kayttotarkoitustieto}
                                                                                           {:tag :kohteenVarustelutaso}
                                                                                           {:tag :haetaanVapautustaKytkin}]}]}]}
                                                                (mapping-common/sijaintitieto "yht")]}]}
                                              {:tag :liitetieto
                                               :child [{:tag :Liite :child mapping-common/liite-children_213}]}
                                              {:tag :asianKuvaus}]}]}

                            {:tag :liitetieto :child [{:tag :Liite :child mapping-common/liite-children_213}]}]}])
